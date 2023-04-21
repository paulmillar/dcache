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

import com.google.common.collect.EvictingQueue;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * A concrete implementation of Watch that uses a Predicate to select "interesting" LoginResults.
 */
public class LoginResultPredicateWatch implements Watch {

    private final EvictingQueue<LoginResultObservation> results;
    private final String description;
    private final Predicate<LoginResultObservation> predicate;

    private boolean isPaused;
    private Instant newestObservation;

    public LoginResultPredicateWatch(Predicate<LoginResultObservation> predicate, String description,
            int capacity) {
        this.description = Objects.requireNonNull(description);
        this.predicate = Objects.requireNonNull(predicate);
        results = EvictingQueue.create(capacity);
    }

    @Override
    public synchronized List<LoginResultObservation> list() {
        return List.copyOf(results);
    }

    @Override
    public String describe() {
        return description;
    }

    @Override
    public synchronized WatchSummary summarise() {
        Optional<Instant> oldest = Optional.ofNullable(results.peek())
            .map(LoginResultObservation::getWhenObserved);
        return new WatchSummary(oldest, Optional.ofNullable(newestObservation), results.size(),
            results.size() + results.remainingCapacity(), isPaused);
    }

    @Override
    public synchronized void accept(LoginResultObservation observation) {
        if (!isPaused && predicate.test(observation)) {
            results.add(observation);
            newestObservation = observation.getWhenObserved();
        }
    }

    @Override
    public synchronized void reset() {
        results.clear();
    }

    @Override
    public synchronized void pause() {
        isPaused = true;
    }

    @Override
    public synchronized void resume() {
        isPaused = false;
    }
}
