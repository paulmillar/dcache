/* dCache - http://www.dcache.org/
 *
 * Copyright (C) 2015 Deutsches Elektronen-Synchrotron
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
package org.dcache.gplazma.plugins;

/**
 * A gPlazma plugin that is LifecycleAware is notified twice during its
 * lifetime.  The first notification is {@code #start}, which is called
 * exactly once some time after creation and before the first user-triggered
 * activity.  The second notification is {@code #stop} is called exactly once
 * after the last user-triggered activity.
 * <p>
 * The intended use is that any gPlazma plugin can easily
 */
public interface LifecycleAware
{
    /**
     * This method is called exactly once before the first user activity.
     */
    void start();

    /**
     * This method is called exactly once after the last user activity.
     */
    void stop();
}
