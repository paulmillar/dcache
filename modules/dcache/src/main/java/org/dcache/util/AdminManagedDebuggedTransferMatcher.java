/* dCache - http://www.dcache.org/
 *
 * Copyright (C) 2020 Deutsches Elektronen-Synchrotron
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
package org.dcache.util;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;

import dmg.cells.nucleus.CellCommandListener;
import dmg.util.CommandException;
import dmg.util.command.Argument;
import dmg.util.command.Command;

import static dmg.util.CommandException.checkCommand;

/**
 * A version of RemoteTransferMatcher that is augmented with a standard set of
 * admin commands to manage the rules.
 */
public class AdminManagedDebuggedTransferMatcher extends RemoteTransferMatcher
        implements CellCommandListener
{
    @Command(name="httptpc debug ls",
            hint="list rules for enabled HTTP-TPC transfer debugging",
            description="Show the list of debugging rules.  Each entry shows"
                    + " the rule ID and a description of the rule.  The rule ID"
                    + " is used to remove a specific rule with the 'httptpc"
                    + " debug rm' command.   The description provides the"
                    + " number of times the rule will match before being"
                    + " automatically removed (if at all), an explanation of"
                    + " which transfers are selected for extra debugging and"
                    + " the number of transfers this rule has enabled"
                    + " debugging.\n"
                    + "\n"
                    + "Further details on the rules and debugging are available"
                    + " in the 'httptpc debug add' command help.")
    public class ListRules implements Callable<String>
    {
        @Override
        public String call()
        {
            ColumnWriter writer = new ColumnWriter()
                .header("ID").left("index").space()
                .header("DESCRIPTION").left("description");

            List<String> rules = listRules();
            for (int i = 0; i < rules.size(); i++) {
                writer.row().value("index", i+1).value("description", rules.get(i));
            }

            return writer.toString();
        }
    }

    @Command(name="httptpc debug add",
            hint="add a new rule for HTTP-TPC debugging",
            description = "Add an extra rule that describes for which HTTP-TPC "
                    + "transfers dCache should log additional information.\n"
                    + "\n"
                    + "Each rule optionally matches on the file's absoluate "
                    + "path within dCache (local-path), the remote server "
                    + "(host) and the absolute path of the file on that remote "
                    + "server (remote-path).  A rule must specify least one of "
                    + "these three criteria.\n"
                    + "\n"
                    + "All of the three criteria may contain wildcards.  A '?' "
                    + "represents any single character and '*' represents zero "
                    + "or more arbitrary characters.  Multiple alternative "
                    + "matching values may be written as a comma-separated list "
                    + "within a set of curly-braces.\n"
                    + "\n"
                    + "The pattern must match the entire text; for example, a "
                    + "pattern matching local-path must match the file's "
                    + "absolute path within dCache.  This means that local-path "
                    + "and remote-path must start with either '/' or a "
                    + "wildcard.\n"
                    + "\n"
                    + "A rule may be auto-removing; that is, the rule is "
                    + "removed after it matches a specific number of times.  "
                    + "This is controlled by the 'count' option.")
    public class AddRule implements Callable<String>
    {
        @dmg.util.command.Option(name="host", usage="A pattern for selecting "
                + "transfers by the remote server's hostname, as supplied by "
                + "the HTTP-TPC transfer initiating client.  This is typically "
                + "the machine's fully qualified domain name (FQDN).")
        private Glob host;

        @dmg.util.command.Option(name="local-path", usage="A pattern for "
                + "selecting transfers by the file's absolute path within "
                + "dCache.  The pattern must match the entire path.")
        private Glob localPath;

        @dmg.util.command.Option(name="remote-path", usage="A pattern for "
                + "selecting transfers by the file's absolute path on the "
                + "remote server.  The pattern must match the entire path.")
        private Glob remotePath;

        @dmg.util.command.Option(name="count", usage="Specify that the rule is "
                + "removed after it matches the specified number of times.  If "
                + "the value is zero then the rule is never removed.  A "
                + "negative value is not valid.")
        private int count;

        @Override
        public String call() throws CommandException
        {
            checkCommand(isNonTrivial(host) || isNonTrivial(localPath)
                    || isNonTrivial(remotePath), "Must specify at least one of "
                            + "host, local-path, or remote-path");
            checkCommand(isAbsolutePathMatchable(localPath), "The local-path "
                    + "pattern must match an absolute path.");
            checkCommand(isAbsolutePathMatchable(remotePath), "The remote-path "
                    + "pattern must match an absolute path.");
            checkCommand(count >= 0, "count must be zero or a positive number.");

            addRule(count, Optional.ofNullable(localPath),
                    Optional.ofNullable(host), Optional.ofNullable(remotePath));

            return "";
        }

        private boolean isNonTrivial(Glob glob)
        {
            if (glob == null) {
                return false;
            }

            String pattern = glob.toString();
            return !pattern.isEmpty() && !pattern.equals("*");
        }

        private boolean isAbsolutePathMatchable(Glob glob)
        {
            if (glob == null) {
                return true;
            }

            String pattern = glob.toString();
            if (pattern.isEmpty()) {
                return false;
            }

            char firstCharacter = pattern.charAt(0);
            return firstCharacter == '/' || firstCharacter == '*';
        }
    }

    @Command(name="httptpc debug rm",
            hint="remove a rule for HTTP-TPC debugging",
            description = "Remove an existing debugging rule.  Rules select "
                    + "for which HTTP-TPC transfers dCache should log "
                    + "additional information.  Removing a rule means HTTP-TPC "
                    + "transfers that would match no longer trigger additional "
                    + "logging information.\n"
                    + "\n"
                    + "Each rule has an associated ID: a numerical number.  "
                    + "This ID is needed when removing a rule.  The 'httptpc "
                    + "debug ls' command lists all rules along with their "
                    + "ID.\n"
                    + "\n"
                    + "A summary of that rule, including the number of times "
                    + "that rule triggered additional logging, is returned "
                    + "when a rule is removed.")
    public class RemoveRule implements Callable<String>
    {
        @Argument(usage="The numerical ID of the rule to remove.")
        private int id;

        @Override
        public String call() throws CommandException
        {
            checkCommand(id >= 1, "Index is too small.");

            List<String> rules = listRules();

            checkCommand(!rules.isEmpty(), "No rules to remove");
            checkCommand(id <= listRules().size(),
                    "Index is larger than maximum value (%d)", rules.size()+1);

            String rule = rules.get(id-1);

            removeRule(id-1);

            return "Removed rule '" + rule + "'";
        }
    }

}
