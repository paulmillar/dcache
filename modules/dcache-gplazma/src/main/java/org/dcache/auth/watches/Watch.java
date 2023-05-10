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

import java.util.List;
import java.util.function.Consumer;

/**
 * A watch represents the ability to accept incoming LoginResult objects, retain some subset
 * of them, and later allow querying of the results.
 */
public interface Watch extends Consumer<LoginResultObservation> {

    /**
     * Which login result to discard when a watch is full and there is another matching login.
     */
    public enum DiscardWhenFull {
        INCOMING,
        OLDEST
    }

    /**
     * Provide a snapshot of the list of LoginResult observations.  Subsequent calls to
     * {@link #accept} should not affect the returned List.
     * @return the retained LoginResults.
     */
    List<LoginResultObservation> list();

    /**
     * Provide a short, single-line description of this watch.
     * @return Description about this watch
     */
    String describe();

    /**
     * Provide a summary description of the current status of this watch.
     */
    WatchSummary summarise();

    /**
     * Clear the stored results.
     */
    void reset();

    /**
     * Stop matching login observations.
     */
    void pause();

    /**
     * Resume matching login observations.
     */
    void resume();
}
