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

import java.util.List;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Arrays.asList;
import static java.util.Objects.requireNonNull;

/**
 * A collection of useful methods for operating with StatefulePredicates.
 */
public class StatefulPredicates {

    /**
     * An abstract class that provides a framework for supporting somehow combining multiple
     * StatefulPredicate objects.
     */
    public static abstract class Combined<T> implements StatefulPredicate<T> {
        protected static abstract class CombinedChecker<T> implements Checker<T> {
            protected final List<Checker<T>> checkers;

            protected CombinedChecker(List<StatefulPredicate<T>> predicates) {
                checkers = predicates.stream()
                      .map(StatefulPredicate::start)
                      .collect(Collectors.toList());
            }

            @Override
            public void accept(T item) {
                checkers.forEach(p -> p.accept(item));
            }

            @Override
            public boolean isFinal() {
                return checkers.stream().allMatch(Checker::isFinal);
            }
        }

        protected final List<StatefulPredicate<T>> innerPredicates;

        public Combined(StatefulPredicate<T>... predicate) {
            checkArgument(predicate.length > 0);
            this.innerPredicates = asList(predicate);
        }
    }

    /**
     * Provide the logical OR of the supplied StatefulPredicate arguments.
     */
    public static class Disjunction<T> extends Combined<T> {
        public static class DisjunctionChecker<T> extends CombinedChecker<T> {
            @Override
            public boolean result() {
                return checkers.stream().anyMatch(Checker<T>::result);
            }

            protected DisjunctionChecker(List<StatefulPredicate<T>> predicates) {
                super(predicates);
            }

            @Override
            public boolean isFinal() {
                return checkers.stream().anyMatch(c -> c.result() && c.isFinal())
                    || super.isFinal();
            }
        }

        public Disjunction(StatefulPredicate<T>... predicate) {
            super(predicate);
        }

        public DisjunctionChecker start() {
            return new DisjunctionChecker(innerPredicates);
        }
    }

    /**
     * Provide the logical AND of the supplied StatefulPredicate arguments.
     */
    public static class Conjunction<T> extends Combined<T> {
        public static class ConjunctionChecker<T> extends CombinedChecker<T> {
            @Override
            public boolean result() {
                return checkers.stream().allMatch(Checker<T>::result);
            }

            protected ConjunctionChecker(List<StatefulPredicate<T>> predicates) {
                super(predicates);
            }


            @Override
            public boolean isFinal() {
                return checkers.stream().anyMatch(c -> !c.result() && c.isFinal())
                    || super.isFinal();
            }
        }

        public Conjunction(StatefulPredicate<T>... predicate) {
            super(predicate);
        }

        @Override
        public ConjunctionChecker<T> start() {
            return new ConjunctionChecker(innerPredicates);
        }
    }

    /**
     * Return the logical NOT of the supplied StatefulPredicate argument.
     */
    public static class Negation<T> implements StatefulPredicate<T> {
        private class NegationChecker<T> implements Checker<T> {
            private final Checker<T> innerChecker;

            NegationChecker(Checker<T> checker) {
                innerChecker = requireNonNull(checker);
            }

            @Override
            public void accept(T item) {
                innerChecker.accept(item);
            }

            @Override
            public boolean isFinal() {
                return innerChecker.isFinal();
            }

            @Override
            public boolean result() {
                return !innerChecker.result();
            }
        }

        private final StatefulPredicate<T> predicate;

        public Negation(StatefulPredicate<T> predicate) {
            this.predicate = requireNonNull(predicate);
        }

        @Override
        public NegationChecker<T> start() {
            return new NegationChecker(predicate.start());
        }
    }

    /**
     * A simple utility method that returns the logical NOT of the supplied StatefulPredicate
     * argument.
     */
    public static <T> StatefulPredicate<T> negate(StatefulPredicate<T> predicate) {
        return new Negation<>(predicate);
    }

    /**
     * A simple utility method that returns the logical AND of the supplied StatefulPredicate
     * arguments.
     */
    public static <T> StatefulPredicate<T> and(StatefulPredicate<T>... predicates) {
        return new Conjunction<>(predicates);
    }

    /**
     * A simple utility method that returns the logical OR of the supplied StatefulPredicate
     * arguments.
     */
    public static <T> StatefulPredicate<T> or(StatefulPredicate<T>... predicates) {
        return new Disjunction<>(predicates);
    }
}
