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
package org.dcache.auth.watches;

import java.util.function.Predicate;
import org.dcache.auth.util.HasMatching;

/**
 * A class that provides the same matching semantics as CredentialPredicate but composition and
 * negation is delegated to the inner HasMatching predicate.  This class also has some utility
 * static methods simplify switching between the two composition semantics.
 */
public class InnerComposeCredentialPredicate extends CredentialPredicate {

    public static InnerComposeCredentialPredicate composeOnInner(Predicate<LoginResultObservation> other) {
        assert other instanceof CredentialPredicate;

        return new InnerComposeCredentialPredicate(innerPredicateOf(other));
    }

    public static CredentialPredicate composeOnOuter(Predicate<LoginResultObservation> other) {
        assert other instanceof CredentialPredicate;

        return new CredentialPredicate(innerPredicateOf(other));
    }

    private static HasMatching<Object> innerPredicateOf(Predicate<LoginResultObservation> other) {
        CredentialPredicate otherCredentialPredicate = (CredentialPredicate) other;
        return (HasMatching<Object>)otherCredentialPredicate.predicate;
    }

    public InnerComposeCredentialPredicate(HasMatching<Object> predicate) {
        super(predicate);
    }

    private HasMatching<Object> innerPredicate() {
        return (HasMatching<Object>) predicate;
    }

    @Override
    public Predicate<LoginResultObservation> and(Predicate<? super LoginResultObservation> other) {
        assert other instanceof InnerComposeCredentialPredicate;

        HasMatching<Object> otherPredicate = ((InnerComposeCredentialPredicate)other).innerPredicate();
        var combined = innerPredicate().and(otherPredicate);
        return new InnerComposeCredentialPredicate(combined);
    }

    @Override
    public Predicate<LoginResultObservation> or(Predicate<? super LoginResultObservation> other) {
        assert other instanceof InnerComposeCredentialPredicate;

        HasMatching<Object> otherPredicate = ((InnerComposeCredentialPredicate)other).innerPredicate();
        var combined = innerPredicate().or(otherPredicate);
        return new InnerComposeCredentialPredicate(combined);
    }

    @Override
    public Predicate<LoginResultObservation> negate() {
        return new InnerComposeCredentialPredicate(innerPredicate().negate());
    }
}
