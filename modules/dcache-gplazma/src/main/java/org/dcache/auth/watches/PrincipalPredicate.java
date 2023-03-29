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
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import org.dcache.gplazma.monitor.LoginResult;

/**
 * A LoginResultObservation Predicate that tests predicates on each principal of a set of
 * principals.  This is done in three contexts: the door-supplied principals are checked against
 * the door predicate, the principals available after the auth phase are checked against the auth
 * predicate, and those available after the map phase are checked against the map predicate.
 * <p>
 * PrincipalPredicate returns true if at least one of the three sets of principals contains a
 * principals that matches the corresponding predicate.
 */
public class PrincipalPredicate implements Predicate<LoginResultObservation> {
    private final Optional<Predicate<Principal>> fromDoor;
    private final Optional<Predicate<Principal>> afterAuth;
    private final Optional<Predicate<Principal>> afterMap;

    public static PrincipalPredicate anyPredicateMatches(Predicate<Principal> predicate) {
        return new PrincipalPredicate(predicate, predicate, predicate);
    }

    public static PrincipalPredicate doorPredicateMatches(Predicate<Principal> predicate) {
        return new PrincipalPredicate(predicate, null, null);
    }

    public static PrincipalPredicate authPredicateMatches(Predicate<Principal> predicate) {
        return new PrincipalPredicate(null, predicate, null);
    }

    public static PrincipalPredicate mapPredicateMatches(Predicate<Principal> predicate) {
        return new PrincipalPredicate(null, null, predicate);
    }

    /**
     * Check for matching predicates.  This predicate returns true if a principal in the list of
     * door-supplied principals match the {@literal fromDoor} predicate, or if a principal in the
     * list of auth-phase-supplied principals match the {@literal afterAuth} predicate, or if a
     * principal in the list of map-phase-supplied principals match the {@literal afterMap}
     * predicate.  A {@literal null} argument is a special case that is equivalent to the
     * {@literal p -> Boolean.FALSE} predicate.
     */
    private PrincipalPredicate(@Nullable Predicate<Principal> fromDoor,
            @Nullable Predicate<Principal> afterAuth,
            @Nullable Predicate<Principal> afterMap) {
        this.fromDoor = Optional.ofNullable(fromDoor);
        this.afterAuth = Optional.ofNullable(afterAuth);
        this.afterMap = Optional.ofNullable(afterMap);
    }

    @Override
    public Predicate<LoginResultObservation> and(Predicate<? super LoginResultObservation> other) {
        if (other instanceof PrincipalPredicate) {
            PrincipalPredicate o = (PrincipalPredicate) other;
            Predicate<Principal> combinedFromDoor = fromDoor
                .map(p1 -> o.fromDoor.map(p2 -> p1.and(p2)).orElse(null))
                .orElse(null);
            Predicate<Principal> combinedAfterAuth = afterAuth
                .map(p1 -> o.afterAuth.map(p2 -> p1.and(p2)).orElse(null))
                .orElse(null);
            Predicate<Principal> combinedAfterMap = afterMap
                .map(p1 -> o.afterMap.map(p2 -> p1.and(p2)).orElse(null))
                .orElse(null);
            return new PrincipalPredicate(combinedFromDoor, combinedAfterAuth, combinedAfterMap);
        }

        return o -> this.test(o) && other.test(o);
    }

    @Override
    public Predicate<LoginResultObservation> or(Predicate<? super LoginResultObservation> other) {
        if (!(other instanceof PrincipalPredicate)) {
            return o -> this.test(o) || other.test(o);
        }

        PrincipalPredicate o = (PrincipalPredicate) other;
        Predicate<Principal> combinedFromDoor = fromDoor
            .map(p1 -> o.fromDoor.map(p2 -> p1.or(p2)).orElse(p1))
            .orElse(o.fromDoor.orElse(null));
        Predicate<Principal> combinedAfterAuth = afterAuth
            .map(p1 -> o.afterAuth.map(p2 -> p1.or(p2)).orElse(p1))
            .orElse(o.afterAuth.orElse(null));
        Predicate<Principal> combinedAfterMap = afterMap
            .map(p1 -> o.afterMap.map(p2 -> p1.or(p2)).orElse(p1))
            .orElse(o.afterMap.orElse(null));
        return new PrincipalPredicate(combinedFromDoor, combinedAfterAuth, combinedAfterMap);
    }

    @Override
    public boolean test(LoginResultObservation observation) {
        LoginResult result = observation.getResult();

        return matching(result.getAuthPhase(), LoginResult.SetDiff::getBefore, fromDoor) ||
                matching(result.getAuthPhase(), LoginResult.SetDiff::getAfter, afterAuth) ||
                matching(result.getMapPhase(), LoginResult.SetDiff::getAfter, afterMap);
    }

    private boolean matching(LoginResult.PhaseResult phase, Function<LoginResult.SetDiff<Principal>,Set> selection,
            Optional<Predicate<Principal>> predicate) {
        return phase.hasHappened() && predicate.map(p -> {
                var principalsDiff = phase.getPrincipals();
                var principals = selection.apply(principalsDiff);
                return principals.stream().anyMatch(p);
            }).orElse(Boolean.FALSE);
    }
}
