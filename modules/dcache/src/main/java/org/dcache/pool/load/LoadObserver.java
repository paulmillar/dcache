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
 * A LoadObserver is any class that somehow measures how hard the storage device
 * is working.  The value is some non-negative floating point number, with zero
 * indicating that there is currently no IO operations.  In general, there is
 * some nominal maximum activity level that the storage device should cope with
 * without being overloaded.  In such cases, the measured activity should be
 * 1.0.  Therefore, any value between 0 and 1.0 indicates the storage device is
 * active without receiving more load than expected, and a value larger than 1.0
 * indicates the pool is receiving more load than it can cope with.
 */
public interface LoadObserver
{
    /**
     * Provide a measure of the current IO load.  A value of 0 indicates no
     * activity. A value of 1 indicates the storage device is receiving as much
     * load as it can cope with.  Values greater than 1 indicate there is
     * currently more requests than the storage device can cope with.
     * @return a measure of the current IO load.
     */
    double currentLoad();
}
