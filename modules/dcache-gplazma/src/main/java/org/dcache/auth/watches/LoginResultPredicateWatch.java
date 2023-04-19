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

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * A concrete implementation of Watch that uses a Predicate to select "interesting" LoginResults.
 */
public class LoginResultPredicateWatch implements Watch {

    private final List<LoginResultObservation> results = new ArrayList<>();
    private final String description;
    private final Predicate<LoginResultObservation> predicate;

    public LoginResultPredicateWatch(Predicate<LoginResultObservation> predicate, String description) {
        this.description = Objects.requireNonNull(description);
        this.predicate = Objects.requireNonNull(predicate);
    }

    @Override
    public List<LoginResultObservation> list() {
        return List.copyOf(results);
    }

    @Override
    public String describe() {
        return description;
    }

    @Override
    public WatchSummary summarise() {
        Instant oldest = null;
        Instant newest = null;
        for (LoginResultObservation result : results) {
            Instant whenObserved = result.getWhenObserved();
            if (oldest == null || whenObserved.isBefore(oldest)) {
                oldest = whenObserved;
            }
            if (newest == null || whenObserved.isAfter(newest)) {
                newest = whenObserved;
            }
        }
        return new WatchSummary(Optional.ofNullable(oldest), Optional.ofNullable(newest),
            results.size());
    }

    @Override
    public void accept(LoginResultObservation observation) {
        if (predicate.test(observation)) {
            results.add(observation);
        }
    }

    @Override
    public void reset() {
        results.clear();
    }
}
