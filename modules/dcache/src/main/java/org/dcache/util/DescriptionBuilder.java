/* dCache - http://www.dcache.org/
 *
 * Copyright (C) 2020 Deutsches Elektronen-Synchrotron
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
package org.dcache.util;

import java.util.Optional;

/**
 * A class that implements this interface can visit one or more
 * Describable objects to build a concrete representation of that information.
 */
public interface DescriptionBuilder<T> extends DescriptionReceiver
{
    /**
     * Build the resulting information using information collected by visiting
     * one or more the describable objects.
     * @return some representation of the collected information.
     */
    Optional<T> build();
}
