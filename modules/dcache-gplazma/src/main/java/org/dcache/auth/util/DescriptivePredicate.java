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

import java.util.function.Predicate;

import static java.util.Objects.requireNonNull;

/**
 * A class that wraps some predicate and adds a meaningful {@literal toString} method.
 */
public class DescriptivePredicate<T> implements Predicate<T> {
    private final Predicate<T> predicate;
    private final String description;

    public static <T> DescriptivePredicateBuilder<T> decorate(Predicate<T> predicate) {
        return new DescriptivePredicateBuilder(predicate);
    }

    /**
     * A simple builder pattern class to make this class' use more intuitive.
     */
    public static class DescriptivePredicateBuilder<T> {
        private final Predicate<T> predicate;
        public DescriptivePredicateBuilder(Predicate<T> predicate) {
            this.predicate = requireNonNull(predicate);
        }

        public DescriptivePredicate<T> withDescription(String description) {
            return new DescriptivePredicate<T>(description, predicate);
        }
    }

    private DescriptivePredicate(String description, Predicate<T> predicate) {
        this.predicate = requireNonNull(predicate);
        this.description = requireNonNull(description);
    }

    @Override
    public boolean test(T t) {
        return predicate.test(t);
    }

    @Override
    public String toString() {
        return description;
    }
}
