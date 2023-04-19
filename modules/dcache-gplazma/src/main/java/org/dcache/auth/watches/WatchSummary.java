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
import static java.util.Objects.requireNonNull;
import java.util.Optional;

/**
 * A class that provides a summary of the current status of some Watch.
 */
public class WatchSummary {
    private final Optional<Instant> oldestObservation;
    private final Optional<Instant> newestObservation;
    private final int observationCount;

    public WatchSummary(Optional<Instant> oldestObservation, Optional<Instant> newestObservation,
            int observationCount) {
        this.oldestObservation = requireNonNull(oldestObservation);
        this.newestObservation = requireNonNull(newestObservation);
        this.observationCount = observationCount;
    }

    public Optional<Instant> oldestObservation() {
        return oldestObservation;
    }

    public Optional<Instant> newestObservation() {
        return newestObservation;
    }

    public int observationCount() {
        return observationCount;
    }
}
