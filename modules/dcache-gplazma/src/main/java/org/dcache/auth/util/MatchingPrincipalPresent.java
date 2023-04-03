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
package org.dcache.auth.util;

import java.security.Principal;
import static java.util.Objects.requireNonNull;
import java.util.function.Predicate;

/**
 * A simple StatefulPredicate that checks whether there is any principal that matches the supplied
 * predicate.
 */
public class MatchingPrincipalPresent implements StatefulPredicate<Principal> {
    private class MatchingPrincipalPresentChecker implements StatefulPredicate.Checker<Principal> {
        private boolean matchFound;

        @Override
        public void accept(Principal item) {
            if (!matchFound && predicate.test(item)) {
                matchFound = true;
            }
        }

        @Override
        public boolean isFinal() {
            return matchFound;
        }

        @Override
        public boolean result() {
            return matchFound;
        }
    }

    private final Predicate<Principal> predicate;

    public MatchingPrincipalPresent(Predicate<Principal> predicate) {
        this.predicate = requireNonNull(predicate);
    }

    public StatefulPredicate.Checker<Principal> start() {
        return new MatchingPrincipalPresentChecker();
    }
}
