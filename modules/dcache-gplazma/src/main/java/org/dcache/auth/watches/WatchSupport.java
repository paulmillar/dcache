/* dCache - http://www.dcache.org/
 *
 * Copyright (C) 2023 Deutsches Elektronen-Synchrotron
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.dcache.auth.watches;

import dmg.cells.nucleus.CellCommandListener;
import dmg.util.CommandException;
import dmg.util.command.Argument;
import dmg.util.command.Command;
import dmg.util.command.Option;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.dcache.gplazma.LoginObserver;
import org.dcache.gplazma.monitor.LoginResult;
import org.dcache.util.ColumnWriter;
import org.dcache.util.TimeUtils;
import org.parboiled.Parboiled;
import org.parboiled.parserunners.ReportingParseRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static dmg.util.CommandException.checkCommand;
import static java.util.Objects.requireNonNull;

/**
 * Support for watching login results.
 */
public class WatchSupport implements LoginObserver, CellCommandListener{

    private static final Logger LOGGER = LoggerFactory.getLogger(WatchSupport.class);
    private static final WatchExpressionParser PARSER = Parboiled.createParser(WatchExpressionParser.class);

    private final Map<String,Watch> watches = new ConcurrentHashMap<>(); // REVISIT, should Map's key be Integer ?
    private final Executor executor;

    private volatile boolean isWatchingSuspended;

    private int nextId = 1;

    public WatchSupport(Executor executor) {
        this.executor = requireNonNull(executor);
    }

    @Command(name = "watch ls", hint = "list watches",
          description = "Provide information about the currently configured login watches.")
    public class WatchLsCommand implements Callable<String> {

        @Argument(usage="Watch ID", required=false)
        private String id;

        private String listWatches() {
            ColumnWriter writer = new ColumnWriter().headersInColumns()
                    .header("ID").left("id").space()
                    .header("Count").right("count").space()
                    .header("State").right("state").space()
                    .header("Oldest").left("oldest").space()
                    .header("Newest").left("newest").space()
                    .header("Description").left("description");
            for (Map.Entry<String,Watch> entry : watches.entrySet()) {
                Watch thisWatch = entry.getValue();
                WatchSummary summary = thisWatch.summarise();
                writer.row()
                    .value("id", entry.getKey())
                    .value("count", summary.observationCount() + "/" + summary.capacity())
                    .value("state", summary.isPaused() ? "PAUSED" : "ACTIVE")
                    .value("oldest", summary.oldestObservation().map(TimeUtils::relativeTimestamp).orElse("-"))
                    .value("newest", summary.newestObservation().map(TimeUtils::relativeTimestamp).orElse("-"))
                    .value("description", thisWatch.describe());
            }
            return writer.toString();
        }

        private String listWatch(String id) throws CommandException {
            Watch watch = watches.get(id);
            checkCommand(watch != null, "Unknown watch with ID %s", id);

            return watch.list().stream()
                .map(LoginResultObservation::print)
                .collect(Collectors.joining("\n"));
        }

        @Override
        public String call() throws CommandException {
            return id == null ? listWatches() : listWatch(id);
        }
    }

    @Command(name = "watch add", hint = "create a new watch",
          description = "Instruct gPlazma to watch for logins that match specific criteria.")
    public class WatchAddCommand implements Callable<String> {

        @Argument(usage="An expression that describes which login results are of interest.  A login "
            + "result that matches the expression is considered interesting, while those that do "
            + "not match are ignored by the watch.\n"
            + "\n"
            + "The expression is one or more predicates combined with simple logic: conjunction "
            + "(\"AND\" or \"&&\") or disjunction (\"OR\" or \"||\"), with optional negation "
            + "(\"NOT\" or \"!\").  Parentheses may be used to control priority.\n"
            + "\n"
            + "Example of expressions:\n"
            + "\n"
            + "    dn      Match logins with any X.509 distinguished name\n"
            + "            principal.\n"
            + "    NOT dn  Match logins with no X.509 distinguished name\n"
            + "            principal.\n"
            + "    !dn     Same as previous example, but using symbols.\n"
            + "    dn AND email\n"
            + "            Match logins with both an X.509 distinguished\n"
            + "            name principal and an email address principal.\n"
            + "    dn && email\n"
            + "            Same as previous example, but using symbols.\n"
            + "    groupname~foo-* OR groupname~bar-*\n"
            + "            Match logins with either a groupname that\n"
            + "            starts \"foo-\" or a groupname that starts\n"
            + "            \"bar-\".\n"
            + "    groupname~foo-* || groupname~bar-*\n"
            + "            Same as previous example, but using symbols.\n"
            + "    username:paul && !(groupname~foo-* || groupname~bar-*)\n"
            + "            Match logins with a username principal \"paul\"\n"
            + "            and without a groupname starting \"foo-\" or a\n"
            + "            groupname starting \"bar-\".\n"
            + "\n"
            + "Predicates are either principal predicates or ...\n"
            + "\n"
            + "PRINCIPAL PREDICATES\n"
            + "\n"
            + "A principal predicate matches if there is a matching principal supplied by the door, "
            + "or in the set of principals after the Auth phase completes, or in the set of "
            + "principals after the mapping phase completes.\n"
            + "\n"
            + "Principal predicates have the form \"<type>\" or \"<type><pattern>\".  The first "
            + "form matches any principal of the given type; the second form matches a predicate "
            + "of the given type with a value that matches the <pattern>.\n"
            + "\n"
            + "<type> is one of \"dn\" (X.509 Distinguished Name), \"sub\" (OIDC 'sub' claim), "
            + "\"email\" (email address), \"groupname\" (group name), \"username\" (username), "
            + "\"uid\" (numeric uid), \"gid\" (numeric gid).  NB. \"username\" matches the user's "
            + "actual username; it does not match the client-supplied username when authenticating "
            + "via username and password.\n"
            + "\n"
            + "<pattern> is \":VALUE\" to check the predicate's value completely matches VALUE, "
            + "\"~GLOB\" to check the predicate's value matches the glob pattern GLOB, "
            + "\"/REGEXP/\" to check the predicate's value matches the regular expression REGEXP.\n"
            + "\n"
            + "Glob patterns must match the entire predicate value.  Certain characters have "
            + "special meaning: a \"*\" matches zero-or-more arbitrary characters, \"?\" matches "
            + "exactly one arbitrary character, \"{A,B}\" curly braces contain a comma-separated "
            + "list of glob sub-patterns where (at least) one of the sub-patterns must match.\n"
            + "\n"
            + "Regular expressions use Java's built-in support, which is largely PCRE but with "
            + "some additional features.  The following URL provides details:\n"
            + "\n"
            + "https://docs.oracle.com/en/java/javase/11/docs/api/java.base/java/util/regex/Pattern.html\n"
            + "\n"
            + "For exact match and glob patterns, the value may be unquoted (e.g., username:paul, "
            + "username~paul*), single-quoted (e.g., username:'paul', username~'paul*') or "
            + "double-quoted (e.g., username:\"paul\", username~\"paul*\").  Unquoted values cannot "
            + "contain spaces.  Single-quoted values cannot contain single-quote characters.  "
            + "Double-quote values may contain any characters; however, backslash and double-quote "
            + "characters must be backslash-escaped.  The following escape sequences match "
            + "single characters: \\t tab, \\b backspace, \\n new line, \\r carriage return, "
            + "\\f form feed, \\' single quotemark, \\\" double quotemark, \\\\ a backslash.\n"
            + "\n"
            + "Example principal predicates:\n"
            + "\n"
            + "    dn      Test for the presence of an X.509\n"
            + "            distinguished name principal.\n"
            + "    dn~'*/CN=Paul Millar'\n"
            + "            Test for the presence of an X.509\n"
            + "            distinguished name principal that ends \"/CN=Paul Millar\".\n"
            + "    username:paul\n"
            + "            Test for a username matching \"paul\".\n"
            + "    groupname~it-*\n"
            + "            Test for a groupname that starts with \"it-\"\n"
            + "    email/^[pn]\\d*@.*\\.(net|org)$/\n"
            + "            Test for an email address with a local-part\n"
            + "            starting with p or n and has zero-or-more digits,\n"
            + "            and the domain name ending \".net\" or \".org\".\n"
        )
        private String expression;

        @Option(name="capacity", usage="The maximum number of observations retained in-memory for"
            + " later examination.  See the -when-full-discard option.", metaVar="COUNT")
        private int capacity=5;

        // TODO: add support for sending reports to destinations; e.g., log file, Kafka, ...

        @Option(name="description", usage="Some meaningful label used to describe this watch.  If"
            + " not specified then the predicate is used.")
        private String userDescription;

        @Option(name="when-full-discard", values={"INCOMING", "OLDEST"},
            usage="Describe which login result to discard when the watch's in-memory capacity is "
                + "full.\n"
                + "\n"
                + "OLDEST    The watch will continue to record new matching\n"
                + "          logins even when full.  The oldest login is\n"
                + "          discarded to make sufficient space.  The watch\n"
                + "          will always show the most recent matching\n"
                + "          logins.  This is sometimes called a circular\n"
                + "          buffer.\n"
                + "\n"
                + "INCOMING  The watch will record new logins only if there\n"
                + "          is available capacity.  Once the watch's\n"
                + "          in-memory storage is exhaused then all new\n"
                + "          matching logins are discarded.\n")
        private Watch.DiscardWhenFull whenFull = Watch.DiscardWhenFull.OLDEST;

        @Override
        public String call() throws Exception {
            Predicate<LoginResultObservation> p = parseExpression();
            String id = "WATCH-" + nextId++;
            String description = Optional.ofNullable(userDescription).orElse(expression);
            Watch watch = new LoginResultPredicateWatch(p, description, capacity, whenFull);
            watches.put(id, watch);
            return id + " added.";
        }

        private Predicate<LoginResultObservation> parseExpression() throws CommandException {
            ReportingParseRunner<Predicate<LoginResultObservation>> runner = new ReportingParseRunner(PARSER.input());
            var result = runner.run(expression);

            if (!result.isSuccess()) {
                var errors = result.getParseErrors().stream()
                    .map(Object::toString)
                    .collect(Collectors.joining("\n\n"));
                throw new CommandException("Parsing of \"" + expression + "\" failed with errors:\n"
                    + errors);
            }

            return result.getTopStackValue();
        }
    }

    @Command(name="watch pause", hint = "stop accepting new logins",
        description="Temporarily stop a watch from matching login activity.  The watch will no"
            + " longer record login activity, even if a login request matches the watch's"
            + " predicate.  The watch may be reactivated using the \"watch resume\" command.  This"
            + " command is idempotent: pausing a watch that is already paused has no effect.")
    public class WatchPauseCommand implements Callable<String> {
        @Argument(usage="Watch ID")
        private String id;

        @Override
        public String call() throws CommandException {
            Watch watch = watches.get(id);
            checkCommand(watch != null, "Unknown watch with ID %s", id);
            watch.pause();
            return "";
        }
    }

    @Command(name="watch resume", hint = "allow a watch to be triggered",
        description="A watch that was previously paused is now allowed to record matching login"
            + " activity.  This command is idempotent: resuming a watch that is not paused has no"
            + " effect.")
    public class WatchResumeCommand implements Callable<String> {
        @Argument(usage="Watch ID")
        private String id;

        @Override
        public String call() throws CommandException {
            Watch watch = watches.get(id);
            checkCommand(watch != null, "Unknown watch with ID %s", id);
            watch.resume();
            return "";
        }
    }

    @Command(name = "watch rm", hint = "remove a watch",
          description = "Instruct gPlazma no longer to watch for certain logins.")
    public class WatchRmCommand implements Callable<String> {
        @Argument(usage="Watch ID")
        private String id;

        @Override
        public String call() throws CommandException {
            checkCommand(watches.remove(id) != null, "Unknown watch with ID %s", id);
            return "";
        }
    }

    @Command(name = "watch reset", hint = "clear a watch's results",
          description = "Remove the history associated with a watch.  If the watch was paused then"
              + " it is also be restarted.")
    public class WatchResetCommand implements Callable<String> {
        @Argument(usage="Watch ID")
        private String id;

        @Override
        public String call() throws CommandException {
            Watch watch = watches.get(id);
            checkCommand(watch != null, "Unknown watch with ID %s", id);
            watch.reset();
            return "";
        }
    }

    @Override
    public synchronized void accept(LoginResult result) {
        LoginResultObservation o = new LoginResultObservation(result);
        try {
            for (Watch watch : watches.values()) {
                executor.execute(() -> watch.accept(o));
            }

            if (isWatchingSuspended) {
                isWatchingSuspended = false;
                LOGGER.warn("Resuming login watching.");
            }
        } catch (RejectedExecutionException e) {
            if (!isWatchingSuspended) {
                isWatchingSuspended = true;
                LOGGER.warn("Suspending login watching: excessive login activity.  "
                    + "Watches may have incomplete information.");
            }
        }
    }
}
