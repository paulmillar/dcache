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
package org.dcache.pool.repository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

import diskCacheV111.util.PnfsId;

import org.dcache.util.Strings;

/**
 * A variation of Account that does file-based accounting.  This is
 * particularly useful in detecting places where a file's capacity usage goes
 * negative, potentially resulting in the overall pool account going negative.
 */
public class FileTrackingAccount extends Account
{
    private static final Logger LOGGER = LoggerFactory.getLogger(FileTrackingAccount.class);

    private static class FileSpaceUsage
    {
        private final long used;
        private final long precious;
        private final long removable;

        public FileSpaceUsage(PnfsId id)
        {
            this(0L, 0L, 0L);
        }

        private FileSpaceUsage(long used, long precious, long removable)
        {
            this.used = used;
            this.precious = precious;
            this.removable = removable;
        }

        private void checkCapacity(PnfsId id, long newValue, long delta, String name)
        {
            if (newValue < 0) {
                LOGGER.warn("{} currently has {}; \u0394 {} capacity of {} \u21D2 {}",
                        id, this, name, Strings.describeSize(delta),
                        Strings.describeSize(newValue),
                        new FileSizeMismatchException("Negative " + name + " capacity detected"));
            }
        }

        public FileSpaceUsage withAdjustUsed(PnfsId id, long delta)
        {
            long newUsed = used + delta;
            checkCapacity(id, newUsed, delta, "used");
            return new FileSpaceUsage(newUsed, precious, removable);
        }

        public FileSpaceUsage withAdjustPrecious(PnfsId id, long delta)
        {
            long newPrecious = precious + delta;
            checkCapacity(id, newPrecious, delta, "precious");
            return new FileSpaceUsage(used, newPrecious, removable);
        }

        public FileSpaceUsage withAdjustRemovable(PnfsId id, long delta)
        {
            long newRemovable = removable + delta;
            checkCapacity(id, newRemovable, delta, "removable");
            return new FileSpaceUsage(used, precious, newRemovable);
        }

        public boolean isEmpty()
        {
            return used <= 0 && precious <= 0 && removable <= 0;
        }

        @Override
        public String toString()
        {
            return "[used " + Strings.describeSize(used)
                    + ", precious " + Strings.describeSize(precious)
                    + ", removable " + Strings.describeSize(removable) + "]";
        }
    }

    private final Map<PnfsId,FileSpaceUsage> _fileSizes = new HashMap<>();

    public FileTrackingAccount()
    {
        LOGGER.warn("File-tracking pool account activated!");
    }

    private void storeFileUsage(PnfsId id, FileSpaceUsage newUsage)
    {
        if (newUsage.isEmpty()) {
            _fileSizes.remove(id);
        } else {
            _fileSizes.put(id, newUsage);
        }
    }

    private void adjustFileUsed(PnfsId id, long delta)
    {
        FileSpaceUsage oldUsage = _fileSizes.computeIfAbsent(id, FileSpaceUsage::new);
        FileSpaceUsage newUsage = oldUsage.withAdjustUsed(id, delta);
        storeFileUsage(id, newUsage);
    }

    private void adjustFileRemovable(PnfsId id, long delta)
    {
        FileSpaceUsage oldUsage = _fileSizes.computeIfAbsent(id, FileSpaceUsage::new);
        FileSpaceUsage newUsage = oldUsage.withAdjustRemovable(id, delta);
        storeFileUsage(id, newUsage);
    }

    private void adjustFilePrecious(PnfsId id, long delta)
    {
        FileSpaceUsage oldUsage = _fileSizes.computeIfAbsent(id, FileSpaceUsage::new);
        FileSpaceUsage newUsage = oldUsage.withAdjustPrecious(id, delta);
        storeFileUsage(id, newUsage);
    }


    @Override
    public synchronized void free(PnfsId id, long space)
    {
        super.free(id, space);
        adjustFileUsed(id, -space);
    }

    @Override
    public synchronized boolean allocateNow(PnfsId id, long request)
             throws InterruptedException
    {
        boolean result = super.allocateNow(id, request);
        adjustFileUsed(id, request);
        return result;
    }

    @Override
    public synchronized void allocate(PnfsId id, long request)
             throws InterruptedException
    {
        super.allocate(id, request);
        adjustFileUsed(id, request);
    }

    @Override
    public synchronized void growTotalAndUsed(PnfsId id, long delta)
    {
        super.growTotalAndUsed(id, delta);
        adjustFileUsed(id, delta);
    }

    @Override
    public synchronized void adjustRemovable(PnfsId id, long delta)
    {
        super.adjustRemovable(id, delta);
        adjustFileRemovable(id, delta);
    }

    @Override
    public synchronized void adjustPrecious(PnfsId id, long delta)
    {
        super.adjustPrecious(id, delta);
        adjustFilePrecious(id, delta);
    }
}
