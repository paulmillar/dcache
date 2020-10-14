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

import java.net.URI;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static java.util.Objects.requireNonNull;

/**
 * This class identifies whether a transfer is somehow special.  Special
 * transfers are identified by it matches any of a list of rules.  Each rule
 * identifies special transfers by any combination of a local (absolute) path
 * pattern, a remote server's hostname pattern, a remote (absolute) path
 * pattern.
 */
public class RemoteTransferMatcher
{
    private enum MatchResult {
        NO_MATCH,
        NON_FINAL_MATCH,
        FINAL_MATCH
    }

    /**
     * An individual rule that selects a subset of URLs.  Rules may match
     * indefinitely or may provide a limit number of matches.
     */
    private static class Rule
    {
        private final Glob localPath;
        private final Glob host;
        private final Glob remotePath;
        private final int count;
        private int hits = 0;

        Rule(int count, Glob localPath, Glob remoteHost, Glob remotePath)
        {
            checkArgument(count >= 0);
            checkArgument(localPath != Glob.ANY || remoteHost != Glob.ANY || remotePath != Glob.ANY);
            this.localPath = requireNonNull(localPath);
            this.host = requireNonNull(remoteHost);
            this.remotePath = requireNonNull(remotePath);
            this.count = count;
        }

        @Override
        public String toString()
        {
            StringBuilder sb = new StringBuilder();

            if (count > 0) {
                sb.append(count).append(" transfer");
                if (count > 1) {
                    sb.append('s');
                }
            } else {
                sb.append("All transfers");
            }

            sb.append(" where");

            if (localPath != Glob.ANY) {
                sb.append(" dCache path matches \"").append(localPath).append('\"');
            }

            if (host != Glob.ANY) {
                sb.append(" remote host matches \"").append(host).append('\"');
            }

            if (remotePath != Glob.ANY) {
                sb.append(" remote path matches \"").append(remotePath).append('\"');
            }

            sb.append(", ");

            switch (hits) {
            case 0:
                sb.append("no hits");
                break;
            case 1:
                sb.append("1 hit");
                break;
            default:
                sb.append(hits).append(" hits");
                break;
            }

            return sb.toString();
        }

        public synchronized MatchResult isMatch(String localPath, String host, String remotePath)
        {
            checkState(count == 0 || hits < count);

            if (this.localPath.matches(localPath)
                    && this.host.matches(host)
                    && this.remotePath.matches(remotePath)) {
                hits++;
                return count > 0 && hits == count
                        ? MatchResult.FINAL_MATCH
                        : MatchResult.NON_FINAL_MATCH;
            }

            return MatchResult.NO_MATCH;
        }
    }

    private final List<Rule> rules = new ArrayList<>();

    public synchronized boolean matches(String localPath, URI location)
    {
        String host = location.getHost();
        String remotePath = location.getPath();
        if (host == null || remotePath == null) {
            return false;
        }

        Iterator<Rule> r = rules.iterator();
        while (r.hasNext()) {
            Rule rule = r.next();

            MatchResult result = rule.isMatch(localPath, host, remotePath);

            switch (result) {
            case FINAL_MATCH:
                r.remove();
                // FALL THROUGH
            case NON_FINAL_MATCH:
                return true;
            }
        }

        return false;
    }

    /**
     * Add a new matching rule.  Rules may continue to match indefinitely or
     * may be limited to a fixed number of matches before being removed
     * automatically.
     * @param count the number of times the rule should match. Zero indicates
     * the rule is never removed.
     * @param localPath The glob pattern for the file's absolute path within
     * dCache.
     * @param host The glob pattern for the remote server's hostname.
     * @param remotePath The glob patter for the file's absolute path on the
     * remote server.
     */
    public synchronized void addRule(int count, Optional<Glob> localPath,
            Optional<Glob> host, Optional<Glob> remotePath)
    {
        checkArgument(localPath.isPresent() || host.isPresent() || remotePath.isPresent());
        rules.add(new Rule(count, localPath.orElse(Glob.ANY),
                host.orElse(Glob.ANY), remotePath.orElse(Glob.ANY)));
    }

    /**
     * Remove a rule with the specified index.
     * @param index the ID of the rule to remove.
     */
    public synchronized void removeRule(int index)
    {
        rules.remove(index);
    }

    /**
     * Provide a list of all rules.
     */
    public synchronized List<String> listRules()
    {
        return rules.stream().map(Object::toString).collect(Collectors.toList());
    }
}
