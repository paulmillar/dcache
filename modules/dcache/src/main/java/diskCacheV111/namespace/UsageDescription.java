/*
 * dCache - http://www.dcache.org/
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
package diskCacheV111.namespace;

import java.time.Instant;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.BiPredicate;

/**
 * Description of dCache usage, as recorded in the namespace.
 */
public class UsageDescription
{
    @FunctionalInterface
    interface BiLongPredicate
    {
        boolean test(long first, long second);
    }

    private final long fileCount;
    private final long directoryCount;
    private final long symLinkCount;
    private final long specialCount;
    private final long usage;
    private final Optional<Instant> oldestFile;
    private final Optional<Instant> newestFile;
    private final OptionalLong smallestFile;
    private final OptionalLong largestFile;
    private final Optional<Instant> leastRecentlyAccessedFile;
    private final Optional<Instant> mostRecentlyAccessedFile;

    public UsageDescription(long fileCount, long directoryCount,
            long symLinkCount, long specialCount, long usage,
            Optional<Instant> oldestFile, Optional<Instant> newestFile,
            Optional<Instant> leastRecentlyAccessedFile, Optional<Instant> mostRecentlyAccessedFile,
            OptionalLong smallestFile, OptionalLong largestFile)
    {
        this.fileCount = fileCount;
        this.directoryCount = directoryCount;
        this.symLinkCount = symLinkCount;
        this.specialCount = specialCount;
        this.usage = usage;
        this.oldestFile = oldestFile;
        this.newestFile = newestFile;
        this.leastRecentlyAccessedFile = leastRecentlyAccessedFile;
        this.mostRecentlyAccessedFile = mostRecentlyAccessedFile;
        this.smallestFile = smallestFile;
        this.largestFile = largestFile;
    }

    private static Optional<Instant> combine(Optional<Instant> first,
            Optional<Instant> second, BiPredicate<Instant,Instant> comparison)
    {
        if (!first.isPresent()) {
            return second;
        }

        if (!second.isPresent()) {
            return first;
        }

        return comparison.test(first.get(), second.get()) ? first : second;
    }

    private static OptionalLong combine(OptionalLong first, OptionalLong second,
            BiLongPredicate comparison)
    {
        if (!first.isPresent()) {
            return second;
        }

        if (!second.isPresent()) {
            return first;
        }

        return comparison.test(first.getAsLong(), second.getAsLong()) ? first : second;
    }

    /**
     * Aggregate this record with another record.
     * @param other the other usage record to include
     * @return a record that combines this usage and the other usage.
     */
    public UsageDescription combine(UsageDescription other)
    {
        return new UsageDescription(
                fileCount + other.fileCount,
                directoryCount + other.directoryCount,
                symLinkCount + other.symLinkCount,
                specialCount + other.specialCount,
                usage + other.usage,
                combine(oldestFile, other.oldestFile, (a,b) -> a.isBefore(b)),
                combine(newestFile, other.newestFile, (a,b) -> a.isAfter(b)),
                combine(leastRecentlyAccessedFile, other.leastRecentlyAccessedFile, (a,b) -> a.isBefore(b)),
                combine(mostRecentlyAccessedFile, other.mostRecentlyAccessedFile, (a,b) -> a.isAfter(b)),
                combine(smallestFile, other.smallestFile, (a,b) -> a < b),
                combine(largestFile, other.largestFile, (a,b) -> a > b));
    }

    public long fileCount()
    {
        return fileCount;
    }

    public long directoryCount()
    {
        return directoryCount;
    }

    public long symLinkCount()
    {
        return symLinkCount;
    }

    public long specialCount()
    {
        return specialCount;
    }

    /**
     * Total number of bytes consumed by files.
     */
    public long usage()
    {
        return usage;
    }

    public Optional<Instant> oldestFile()
    {
        return oldestFile;
    }

    public Optional<Instant> newestFile()
    {
        return newestFile;
    }

    public Optional<Instant> leastRecentlyAccessedFile()
    {
        return leastRecentlyAccessedFile;
    }

    public Optional<Instant> mostRecentlyAccessedFile()
    {
        return mostRecentlyAccessedFile;
    }

    public OptionalLong smallestFile()
    {
        return smallestFile;
    }

    public OptionalLong largestFile()
    {
        return largestFile;
    }
}
