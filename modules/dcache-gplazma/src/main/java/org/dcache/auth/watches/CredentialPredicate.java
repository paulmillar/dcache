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

import static java.util.Objects.requireNonNull;
import java.util.function.Predicate;
import org.dcache.auth.util.StatefulPredicate;
import org.dcache.auth.util.StatefulPredicates;
import org.dcache.gplazma.monitor.LoginResult;

/**
 * A LoginResultObservation Predicate that tests whether the supplied StatefulPredicate is satisfied
 * by the credentials supplied by the door.
 * <p>
 * The default methods for combining two predicates (and, or) are overridden so that, if the
 * other predicate is also a CredentialPredicate then the two are combined so that there is only a
 * single pass over the set of credentials.  The negation method is updated to support this even
 * if a test is negated.
 * <p>
 * Note that (currently) this optimisation is lost if the CredentialPredicate is combined with
 * another predicate.
 */
public class CredentialPredicate implements Predicate<LoginResultObservation> {
    protected final StatefulPredicate<Object> predicate;

    public CredentialPredicate(StatefulPredicate<Object> predicate) {
        this.predicate = requireNonNull(predicate);
    }

    @Override
    public Predicate<LoginResultObservation> and(Predicate<? super LoginResultObservation> other) {
        requireNonNull(other);

        if (other instanceof CredentialPredicate) {
            CredentialPredicate otherPredicate = (CredentialPredicate) other;
            StatefulPredicate<Object> combined = StatefulPredicates.and(this.predicate, otherPredicate.predicate);
            return new CredentialPredicate(combined);
        }

        return o -> this.test(o) && other.test(o);
    }

    @Override
    public Predicate<LoginResultObservation> or(Predicate<? super LoginResultObservation> other) {
        requireNonNull(other);

        if (other instanceof CredentialPredicate) {
            CredentialPredicate otherPredicate = (CredentialPredicate) other;
            StatefulPredicate<Object> combined = StatefulPredicates.or(this.predicate, otherPredicate.predicate);
            return new CredentialPredicate(combined);
        }

        return o -> this.test(o) || other.test(o);
    }

    @Override
    public Predicate<LoginResultObservation> negate() {
        StatefulPredicate<Object> negated = StatefulPredicates.negate(predicate);
        return new CredentialPredicate(negated);
    }

    @Override
    public boolean test(LoginResultObservation observation) {
        LoginResult result = observation.getResult();

        StatefulPredicate.Checker<Object> checker = predicate.start();

        result.getAuthPhase().getPublicCredentials().stream().forEach(checker::accept);
        if (!checker.isFinal()) {
            result.getAuthPhase().getPrivateCredentials().stream().forEach(checker::accept);
        }

        return checker.result();
    }
}
