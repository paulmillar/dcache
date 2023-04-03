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
 * result.  At a high level, this is broadly similar to {@code Predicate<? extends Collection<T>>};
 * however, moving the iteration outside of the predicate allows for various optimisation.
 */
public interface StatefulPredicate<T> {

    /**
     * A class that implements this interface provides a way to accept multiple items.
     * @param <T>
     */
    public interface Checker<T> {
        /**
         * Accept a new item.
         * @param item
         */
        void accept(T item);

        /**
         * Whether the value returned by {@link #result() } can change.  Returning {@literal true}
         * indicates that subsequent calls to {@link #accept(java.lang.Object) } will not alter
         * this value.
         */
        default boolean isFinal() {
            return false;
        }

        /**
         * The result of this predicate with the information presented so far.  There may be
         * subsequent calls to {@link #accept(java.lang.Object) }
         * @return
         */
        boolean result();
    }

    /**
     * Start a new checking process.
     * @return
     */
    Checker<T> start();
}
