/*
 * dCache - http://www.dcache.org/
 *
 * Copyright (C) 2019 Deutsches Elektronen-Synchrotron
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
package org.dcache.pool.load;

/**
 * A class that implements this interface keeps track of IO thread activity.
 * Implementations must take care that they do not block these calls.
 */
public interface ThreadIoMonitor
{
    /**
     * A thread has started an IO operation. It is guaranteed that
     * {@link #ioThreadFinish} is subsequently called.
     */
    void threadIoStarted();

    /**
     * A thread has finished an IO operation.
     */
    void threadIoFinished();
}
