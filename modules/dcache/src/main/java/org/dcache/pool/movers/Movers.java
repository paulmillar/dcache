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
package org.dcache.pool.movers;

import java.time.Duration;
import java.time.Instant;

import org.dcache.util.DescriptionReceiver;

/**
 * Utility methods for working with Mover.
 */
public class Movers
{
    /** Provide a standard description of a mover. */
    public static void describeMover(DescriptionReceiver receiver, Mover mover)
    {
        receiver.accept("PNFSID", mover.getFileAttributes().getPnfsId());
        receiver.acceptSize("Bytes transferred", mover.getBytesTransferred());

        DescriptionReceiver door = receiver.acceptComplex("Door");
        door.accept("address", mover.getPathToDoor().getDestinationAddress());
        door.accept("transfer id", mover.getClientId());

        DescriptionReceiver transfer = receiver.acceptComplex("Transfer");
        transfer.accept("Duration", Duration.ofMillis(mover.getTransferTime()));
        long lastTransferred = mover.getLastTransferred();
        if (lastTransferred != 0) {
            transfer.accept("Last operation", Instant.ofEpochMilli(lastTransferred));
        }
    }
}
