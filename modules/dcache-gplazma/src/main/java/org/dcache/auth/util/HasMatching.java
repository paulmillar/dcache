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

import static java.util.Objects.requireNonNull;
import java.util.function.Predicate;

/**
 * A simple StatefulPredicate that checks whether there is any of the items match the supplied
 * predicate.
 */
public class HasMatching<T> implements StatefulPredicate<T> {
    private class HasMatchingChecker implements StatefulPredicate.Checker<T> {
        private boolean matchFound;

        @Override
        public void accept(T item) {
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

    private final Predicate<T> predicate;

    public HasMatching(Predicate<T> predicate) {
        this.predicate = requireNonNull(predicate);
    }

    public StatefulPredicate.Checker<T> start() {
        return new HasMatchingChecker();
    }

    public String toString() {
        return "Test presence that matches " + predicate.toString();
    }
}
