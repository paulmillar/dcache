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

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Arrays.asList;
import java.util.List;
import static java.util.Objects.requireNonNull;
import java.util.stream.Collectors;

/**
 * A collection of useful methods for operating with StatefulePredicates.
 */
public class StatefulPredicates {

    /**
     * An abstract class that provides a framework for supporting somehow combining multiple
     * StatefulPredicate objects.
     */
    public static abstract class Combined<T> implements StatefulPredicate<T> {
        protected abstract class CombinedChecker<T> implements Checker<T> {
            protected final List<Checker<T>> checkers = innerPredicates.stream()
                      .map(StatefulPredicate::start)
                      .collect(Collectors.toList());
            private boolean cachedResult;
            private boolean cachedResultValid;

            @Override
            public void accept(T item) {
                cachedResultValid = false;
                checkers.forEach(p -> p.accept(item));
            }

            @Override
            public boolean result() {
                if (cachedResultValid) {
                    return cachedResult;
                }
                cachedResult = calculateResult();
                cachedResultValid = true;
                return cachedResult;
            }

            protected abstract boolean calculateResult();

            @Override
            public boolean isFinal() {
                return result() == true;
            }
        }

        protected final List<StatefulPredicate<T>> innerPredicates;

        public Combined(StatefulPredicate<T>... predicate) {
            checkArgument(predicate.length > 0);
            this.innerPredicates = asList(predicate);
        }
    }

    // Provide the logical OR of supplied arguments.
    public static class Disjunction<T> extends Combined<T> {
        private class DisjunctionChecker<T> extends CombinedChecker<T> {
            @Override
            protected boolean calculateResult() {
                return checkers.stream().anyMatch(StatefulPredicate.Checker::result);
            }
        }

        public Disjunction(StatefulPredicate<T>... predicate) {
            super(predicate);
        }

        public DisjunctionChecker start() {
            return new DisjunctionChecker();
        }
    }

    /**
     * Provide the logical AND of the supplied arguments.
     */
    public static class Conjunction<T> extends Combined<T> {
        private class ConjunctionChecker<T> extends CombinedChecker<T> {
            @Override
            protected boolean calculateResult() {
                return checkers.stream().allMatch(StatefulPredicate.Checker::result);
            }
        }

        public Conjunction(StatefulPredicate<T>... predicate) {
            super(predicate);
        }

        @Override
        public ConjunctionChecker<T> start() {
            return new ConjunctionChecker();
        }
    }

    public static class Negation<T> implements StatefulPredicate<T> {
        private class NegationChecker<T> implements StatefulPredicate.Checker<T> {
            private final StatefulPredicate.Checker<T> innerChecker;

            NegationChecker(StatefulPredicate.Checker<T> checker) {
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

        public NegationChecker<T> start() {
            return new NegationChecker(predicate.start());
        }
    }

    /**
     * The logical NOT of a predicate.
     */
    public static <T> StatefulPredicate<T> negate(StatefulPredicate<T> predicate) {
        return new Negation<>(predicate);
    }

    /**
     * The logical AND of the supplied predicates.
     */
    public static <T> StatefulPredicate<T> and(StatefulPredicate<T>... predicates) {
        return new Conjunction<>(predicates);
    }

    /**
     * The logical OR of the supplied predicates.
     */
    public static <T> StatefulPredicate<T> or(StatefulPredicate<T>... predicates) {
        return new Disjunction<>(predicates);
    }
}
