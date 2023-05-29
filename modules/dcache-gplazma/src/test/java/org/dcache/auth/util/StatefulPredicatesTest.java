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

import org.dcache.auth.GidPrincipal;
import org.dcache.auth.UidPrincipal;
import org.dcache.auth.UserNamePrincipal;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import org.junit.Test;

public class StatefulPredicatesTest {

    @Test
    public void shouldInitialiseNegate() {
        var pred = new HasMatching(p -> p instanceof UidPrincipal);
        var neg = StatefulPredicates.negate(pred);

        var checker = neg.start();

        assertThat(checker.result(), equalTo(true));
        assertThat(checker.isFinal(), equalTo(false));
    }

    @Test
    public void shouldTrueFalseForNegateWithNonMatching() {
        var pred = new HasMatching(p -> p instanceof UidPrincipal);
        var neg = StatefulPredicates.negate(pred);
        var checker = neg.start();

        checker.accept(new UserNamePrincipal("paul"));

        assertThat(checker.result(), equalTo(true));
        assertThat(checker.isFinal(), equalTo(false));
    }

    @Test
    public void shouldFalseTrueForNegateWithMatching() {
        var pred = new HasMatching(p -> p instanceof UidPrincipal);
        var neg = StatefulPredicates.negate(pred);
        var checker = neg.start();

        checker.accept(new UidPrincipal(1000));

        assertThat(checker.result(), equalTo(false));
        assertThat(checker.isFinal(), equalTo(true));
    }

    public void shouldFalseTrueForNegateWithNonMatchingMatching() {
        var pred = new HasMatching(p -> p instanceof UidPrincipal);
        var neg = StatefulPredicates.negate(pred);
        var checker = neg.start();
        checker.accept(new UserNamePrincipal("paul"));

        checker.accept(new UidPrincipal(1000));

        assertThat(checker.result(), equalTo(false));
        assertThat(checker.isFinal(), equalTo(true));
    }

    @Test
    public void shouldFalseTrueForNegateWithMatchingNonMatching() {
        var pred = new HasMatching(p -> p instanceof UidPrincipal);
        var neg = StatefulPredicates.negate(pred);
        var checker = neg.start();

        checker.accept(new UidPrincipal(1000));
        checker.accept(new UserNamePrincipal("paul"));

        assertThat(checker.result(), equalTo(false));
        assertThat(checker.isFinal(), equalTo(true));
    }

    @Test
    public void shouldWorkForAndInitial() {
        var pred1 = new HasMatching(p -> p instanceof UidPrincipal);
        var pred2 = new HasMatching(p -> p instanceof GidPrincipal);
        var and = StatefulPredicates.and(pred1, pred2);
        var checker = and.start();

        assertThat(checker.result(), equalTo(false));
        assertThat(checker.isFinal(), equalTo(false));
    }

    @Test
    public void shouldWorkForAndAfterFirstNonMatch() {
        var pred1 = new HasMatching(p -> p instanceof UidPrincipal);
        var pred2 = new HasMatching(p -> p instanceof GidPrincipal);
        var and = StatefulPredicates.and(pred1, pred2);
        var checker = and.start();

        checker.accept(new UserNamePrincipal("paul"));

        assertThat(checker.result(), equalTo(false));
        assertThat(checker.isFinal(), equalTo(false));
    }

    @Test
    public void shouldWorkForAndAfterFirstMatch() {
        var pred1 = new HasMatching(p -> p instanceof UidPrincipal);
        var pred2 = new HasMatching(p -> p instanceof GidPrincipal);
        var and = StatefulPredicates.and(pred1, pred2);
        var checker = and.start();

        checker.accept(new UidPrincipal(1000));

        assertThat(checker.result(), equalTo(false));
        assertThat(checker.isFinal(), equalTo(false));
    }

    @Test
    public void shouldWorkForAndAfterSecondMatch() {
        var pred1 = new HasMatching(p -> p instanceof UidPrincipal);
        var pred2 = new HasMatching(p -> p instanceof GidPrincipal);
        var and = StatefulPredicates.and(pred1, pred2);
        var checker = and.start();

        checker.accept(new GidPrincipal(1000, false));

        assertThat(checker.result(), equalTo(false));
        assertThat(checker.isFinal(), equalTo(false));
    }

    @Test
    public void shouldWorkForAndAfterBothMatch() {
        var pred1 = new HasMatching(p -> p instanceof UidPrincipal);
        var pred2 = new HasMatching(p -> p instanceof GidPrincipal);
        var and = StatefulPredicates.and(pred1, pred2);
        var checker = and.start();

        checker.accept(new UidPrincipal(1000));
        checker.accept(new GidPrincipal(1000, false));

        assertThat(checker.result(), equalTo(true));
        assertThat(checker.isFinal(), equalTo(true));
    }

    @Test
    public void shouldWorkForOrInitial() {
        var pred1 = new HasMatching(p -> p instanceof UidPrincipal);
        var pred2 = new HasMatching(p -> p instanceof GidPrincipal);
        var or = StatefulPredicates.or(pred1, pred2);
        var checker = or.start();

        assertThat(checker.result(), equalTo(false));
        assertThat(checker.isFinal(), equalTo(false));
    }

    @Test
    public void shouldWorkForOrAfterNonMatch() {
        var pred1 = new HasMatching(p -> p instanceof UidPrincipal);
        var pred2 = new HasMatching(p -> p instanceof GidPrincipal);
        var or = StatefulPredicates.or(pred1, pred2);
        var checker = or.start();

        checker.accept(new UserNamePrincipal("paul"));

        assertThat(checker.result(), equalTo(false));
        assertThat(checker.isFinal(), equalTo(false));
    }

    @Test
    public void shouldWorkForOrFirstInnerMatches() {
        var pred1 = new HasMatching(p -> p instanceof UidPrincipal);
        var pred2 = new HasMatching(p -> p instanceof GidPrincipal);
        var or = StatefulPredicates.or(pred1, pred2);
        var checker = or.start();
        checker.accept(new UserNamePrincipal("paul"));

        checker.accept(new UidPrincipal(1000));

        assertThat(checker.result(), equalTo(true));
        assertThat(checker.isFinal(), equalTo(true));
    }

    @Test
    public void shouldWorkForOrSecondInnerMatches() {
        var pred1 = new HasMatching(p -> p instanceof UidPrincipal);
        var pred2 = new HasMatching(p -> p instanceof GidPrincipal);
        var or = StatefulPredicates.or(pred1, pred2);
        var checker = or.start();
        checker.accept(new UserNamePrincipal("paul"));

        checker.accept(new GidPrincipal(1000, true));

        assertThat(checker.result(), equalTo(true));
        assertThat(checker.isFinal(), equalTo(true));
    }
}