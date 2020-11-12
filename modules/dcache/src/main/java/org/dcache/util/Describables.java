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

import dmg.cells.nucleus.CDC;

/**
 * Utility methods for handling classes that implement the Describable
 * interface.
 */
public class Describables
{
    /**
     * Utility method to build a suitable log output if CDC inspection
     * is enabled.
     * @param targets the objects to describe.
     * @return the string to log, if there is something to log.
     */
    public static Optional<String> inspectionOf(Object... targets)
    {
        if (!CDC.isInspectEnabled()) {
            return Optional.empty();
        }

        String initial = CDC.getInspectReason() + "; details follow";
        LogOutputDescriptionBuilder builder = new LogOutputDescriptionBuilder(initial);

        return describeTo(builder, targets).build();
    }

    /**
     * Visit each of the supplied targets in sequence with the visitor if the
     * target implements Describable.  Any target that does not implement
     * Describable is silently ignored.
     * @param <T> the type of visitor.
     * @param receiver the instance of the visitor type.
     * @param targets the targets to visit.
     * @return the first argument.
     */
    public static <T extends DescriptionReceiver> T describeTo(T receiver,
            Object... targets)
    {
        for (Object target : targets) {
            if (target instanceof Describable) {
                ((Describable)target).describeTo(receiver);
            }
        }
        return receiver;
    }
}