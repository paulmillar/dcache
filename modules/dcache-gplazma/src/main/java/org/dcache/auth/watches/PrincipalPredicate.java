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

import java.security.Principal;
import static java.util.Objects.requireNonNull;
import java.util.function.Predicate;
import org.dcache.auth.util.StatefulPredicate;
import org.dcache.auth.util.StatefulPredicate.Checker;
import org.dcache.auth.util.StatefulPredicates;
import org.dcache.gplazma.monitor.LoginResult;
import org.dcache.gplazma.monitor.LoginResult.AuthPhaseResult;
import org.dcache.gplazma.monitor.LoginResult.MapPhaseResult;

/**
 * A LoginResultObservation Predicate that tests whether the supplied StatefulPredicate is satisfied
 * by the combination of the principals from the door, from the auth phase, and from the map phase.
 * <p>
 * The default methods for combining two predicates (and, or) are overridden so that, if the
 * other predicate is also a PrincipalPredicate then the two are combined so that there is only a
 * single pass over the set of principals.  The negation method is updated to support this even
 * if a test is negated.
 * <p>
 * Note that (currently) this optimisation is lost if the PrincipalPredicate is combined with
 * another predicate.
 */
public class PrincipalPredicate implements Predicate<LoginResultObservation> {

    private final StatefulPredicate<Principal> predicate;

    public PrincipalPredicate(StatefulPredicate<Principal> predicate) {
        this.predicate = requireNonNull(predicate);
    }

    @Override
    public Predicate<LoginResultObservation> and(Predicate<? super LoginResultObservation> other) {
        requireNonNull(other);

        if (other instanceof PrincipalPredicate) {
            PrincipalPredicate otherPredicate = (PrincipalPredicate) other;
            StatefulPredicate<Principal> combined = StatefulPredicates.and(this.predicate, otherPredicate.predicate);
            return new PrincipalPredicate(combined);
        }

        return o -> this.test(o) && other.test(o);
    }

    @Override
    public Predicate<LoginResultObservation> negate() {
        StatefulPredicate<Principal> negated = StatefulPredicates.negate(predicate);
        return new PrincipalPredicate(negated);
    }

    @Override
    public Predicate<LoginResultObservation> or(Predicate<? super LoginResultObservation> other) {
        requireNonNull(other);

        if (other instanceof PrincipalPredicate) {
            PrincipalPredicate otherPredicate = (PrincipalPredicate) other;
            StatefulPredicate<Principal> combined = StatefulPredicates.or(this.predicate, otherPredicate.predicate);
            return new PrincipalPredicate(combined);
        }

        return o -> this.test(o) || other.test(o);
    }

    @Override
    public boolean test(LoginResultObservation observation) {
        LoginResult result = observation.getResult();

        Checker<Principal> checker = predicate.start();

        AuthPhaseResult auth = result.getAuthPhase();
        if (auth.hasHappened()) {
            auth.getPrincipals().getBefore().stream().forEach(checker::accept);
            if (!checker.isFinal()) {
                auth.getPrincipals().getAfter().stream().forEach(checker::accept);
            }
        }

        MapPhaseResult map = result.getMapPhase();
        if (map.hasHappened() && !checker.isFinal()) {
            map.getPrincipals().getAfter().stream().forEach(checker::accept);
        }

        return checker.result();
    }
}
