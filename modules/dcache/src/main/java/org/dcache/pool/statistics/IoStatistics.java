/* dCache - http://www.dcache.org/
 *
 * Copyright (C) 2017 Deutsches Elektronen-Synchrotron
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
package org.dcache.pool.statistics;

import static org.dcache.util.Strings.toThreeSigFig;

import org.dcache.util.Describable;
import org.dcache.util.DescriptionReceiver;

/**
 * An immutable snapshot of statistics describing the channel usage since it
 * was created.  Statistics of both read and write activity are provided;
 * although, in many cases, a channel is exclusively used in one direction.
 */
public class IoStatistics implements Describable
{
    private final DirectedIoStatistics reads;
    private final DirectedIoStatistics writes;

    public IoStatistics(DirectedIoStatistics reads, DirectedIoStatistics writes)
    {
        this.reads = reads;
        this.writes = writes;
    }

    public DirectedIoStatistics reads()
    {
        return reads;
    }

    public DirectedIoStatistics writes()
    {
        return writes;
    }

    public boolean hasReads()
    {
        return reads.statistics().requestedBytes().getN() > 0;
    }

    public boolean hasWrites()
    {
        return writes.statistics().requestedBytes().getN() > 0;
    }

    private static String ratioDescription(long reads, long writes)
    {
        if (reads <= writes) {
            return toThreeSigFig(writes / (double)reads, 1000)
                    + " writes for every read (on average)";
        } else {
            return toThreeSigFig(reads / (double)writes, 1000)
                    + " reads for every write (on average)";
        }
    }

    @Override
    public void describeTo(DescriptionReceiver receiver)
    {
        long readCount = reads.statistics().requestedBytes().getN();
        long writeCount = writes.statistics().requestedBytes().getN();

        if (hasReads() && hasWrites()) {
            long totalCount = readCount + writeCount;

            receiver.accept("Request ratio", ratioDescription(readCount, writeCount));

            DescriptionReceiver readReceiver = receiver.acceptComplex("Read statistics");
            readReceiver.accept("Request", readCount, totalCount, "all requests");
            reads.describeTo(readReceiver);

            DescriptionReceiver writeReceiver = receiver.acceptComplex("Write statistics");
            writeReceiver.accept("Requests", writeCount, totalCount, "all requests");
            writes.describeTo(writeReceiver);
        } else if (hasReads()) {
            receiver.accept("Requests", readCount);
            reads.describeTo(receiver);
        } else if (hasWrites()) {
            receiver.accept("Requests", writeCount);
            writes.describeTo(receiver);
        }
    }
}
