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
import static dmg.util.CommandException.checkCommand;
import dmg.util.command.Argument;
import dmg.util.command.Command;
import dmg.util.command.Option;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.dcache.gplazma.LoginObserver;
import org.dcache.gplazma.monitor.LoginResult;
import org.dcache.gplazma.monitor.LoginResultPrinter;
import org.dcache.util.ColumnWriter;
import org.dcache.util.TimeUtils;
import org.parboiled.Parboiled;
import org.parboiled.parserunners.ReportingParseRunner;

/**
 * Support for watching login results.
 */
public class WatchSupport implements LoginObserver, CellCommandListener{

    private static final WatchExpressionParser PARSER = Parboiled.createParser(WatchExpressionParser.class);

    private final Map<String,Watch> watches = new ConcurrentHashMap<>(); // REVISIT, should Map's key be Integer ?

    private int nextId = 1;

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

        @Argument(usage="Describe which login results are of interest.")
        private String predicate;

        @Option(name="capacity", usage="The maximum number of observations retained for later"
            + " examination.", metaVar="COUNT")
        private int capacity=5;

        @Option(name="target", usage="Where to send reports.")
        private String[] targets;

        @Option(name="description", usage="Some meaningful label used to describe this watch.  If"
            + " not specified then the predicate is used.")
        private String userDescription;

        @Option(name="pause-when-full", usage="When specified, the watch will automatically become"
            + " paused once it becomes full, preventing the loss of information.  A pause-when-full"
            + " watch that is full may be manually resumed; if so, then any subsequent matches"
            + " will result in the oldest observation being evicted.  If the watch is reset then"
            + " the watch will pause if it becomes full again.")
        private boolean pauseWhenFull;

        @Override
        public String call() throws Exception {
            Predicate<LoginResultObservation> p = parseExpression();
            String id = "WATCH-" + nextId++;
            String description = Optional.ofNullable(userDescription).orElse(predicate);
            Watch watch = new LoginResultPredicateWatch(p, description, capacity, pauseWhenFull);
            watches.put(id, watch);
            return id + " added.";
        }

        private Predicate<LoginResultObservation> parseExpression() throws CommandException {
            ReportingParseRunner<Predicate<LoginResultObservation>> runner = new ReportingParseRunner(PARSER.input());
            var result = runner.run(predicate);

            if (!result.isSuccess()) {
                var errors = result.getParseErrors().stream()
                    .map(Object::toString)
                    .collect(Collectors.joining("\n\n"));
                throw new CommandException("Parsing of \"" + predicate + "\" failed with errors:\n"
                    + errors);
            }

            return result.getTopStackValue();
        }
    }

    @Command(name="watch pause", hint = "watch is no longer triggered",
        description="Temporarily stop a watch from matching login activity.  The watch will no"
            + " longer record login activity, even if a login request matches the watch's"
            + " predicate.  This block may be reversed using the \"watch resume\" command.  This"
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
          description = "Remove the history associated with a watch.  If the watch will "
              + " pause-when-full and is full then the reset command will return the watch to"
              + " an unpaused state, otherwise the watch's state is not affected.")
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
    public void accept(LoginResult result) {
        LoginResultObservation o = new LoginResultObservation(result);
        watches.forEach((id,w) -> w.accept(o));
    }
}
