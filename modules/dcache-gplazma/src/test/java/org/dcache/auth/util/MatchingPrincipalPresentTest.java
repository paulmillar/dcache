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

import org.dcache.auth.UidPrincipal;
import org.dcache.auth.UserNamePrincipal;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import org.junit.Test;

public class MatchingPrincipalPresentTest {

    @Test
    public void shouldInitiallyAllFalse() {
        var pred = new HasMatching(p -> p instanceof UidPrincipal);

        var checker = pred.start();

        assertThat(checker.result(), equalTo(false));
        assertThat(checker.isFinal(), equalTo(false));
    }

    @Test
    public void shouldAllFalseAfterNonMatching() {
        var pred = new HasMatching(p -> p instanceof UidPrincipal);
        var checker = pred.start();

        checker.accept(new UserNamePrincipal("paul"));

        assertThat(checker.result(), equalTo(false));
        assertThat(checker.isFinal(), equalTo(false));
    }

    @Test
    public void shouldAllTrueAfterMatching() {
        var pred = new HasMatching(p -> p instanceof UidPrincipal);
        var checker = pred.start();

        checker.accept(new UidPrincipal(1000));

        assertThat(checker.result(), equalTo(true));
        assertThat(checker.isFinal(), equalTo(true));
    }

    @Test
    public void shouldAllTrueAfterNonMatchingThenMatching() {
        var pred = new HasMatching(p -> p instanceof UidPrincipal);
        var checker = pred.start();

        checker.accept(new UserNamePrincipal("paul"));
        checker.accept(new UidPrincipal(1000));

        assertThat(checker.result(), equalTo(true));
        assertThat(checker.isFinal(), equalTo(true));
    }

    @Test
    public void shouldAllTrueAfterMatchingThenNonMatching() {
        var pred = new HasMatching(p -> p instanceof UidPrincipal);
        var checker = pred.start();

        checker.accept(new UidPrincipal(1000));
        checker.accept(new UserNamePrincipal("paul"));

        assertThat(checker.result(), equalTo(true));
        assertThat(checker.isFinal(), equalTo(true));
    }
}