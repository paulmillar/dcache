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

/**
 * A StatefulPredicate is something that accepts zero or more items and then is asked for the
 * result.  There is an expectation that there is some state that might (or might not) be updated
 * when presented with information.
 * <p>
 * A class that implements StatefulPredicate represents the predicate in general, the algorithm.
 * The process for calculating an actual result of a predicate requires creating an object that
 * carries the state: that object's class implements the Checker interface.
 * <p>
 * The StatefulPredicate class is required to be thread-safe; however, the class implementing
 * Checker has no requirements to be thread-safe.
 * <p>
 * Functionally, this interface is somewhat similar to {@code Predicate<? extends Collection<T>>},
 * except this approach allows for combining predicates without repeated iterations and it doesn't
 * require building a Collection of items.  This interface is also similar to
 * {@literal java.util.stream.Collector} but is simpler to implement.
 */
 public interface StatefulPredicate<T> {

    /**
     * A class that implements this interface provides a way to accept multiple items.
     * @param <T> The kind of items this Checker accepts.
     */
    public interface Checker<T> {
        /**
         * Update the internal state based on a new item.
         */
        void accept(T item);

        /**
         * Whether the value returned by {@link #result() } can change.  Returning {@literal true}
         * indicates that subsequent calls to {@link #accept(java.lang.Object) } will not alter
         * the value returned by {@literal result}.
         */
        default boolean isFinal() {
            return false;
        }

        /**
         * The result of this predicate with the information presented so far.  There may be
         * subsequent calls to {@link #accept(java.lang.Object) }
         */
        boolean result();
    }

    /**
     * Start a new checking process.
     */
    Checker<T> start();
}
