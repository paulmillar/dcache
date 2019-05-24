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

import com.google.common.annotations.VisibleForTesting;

import javax.annotation.concurrent.GuardedBy;

import java.io.PrintWriter;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;

import dmg.cells.nucleus.CellInfoProvider;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * A LoadObserver that calculates the load of this pool by considering the
 * average number of active IO threads over a "history period", scaled by an
 * maximum number of concurrent requests.  The idea is that a block device
 * should be able to handle a certain number of near continous requests while
 * working at peak performance.  For a single drive, this value is (likely) 1
 * while for RAID systems, this value is likely the number of active spindles
 * in the RAID cluster.  A more accurate value should be obtained by running
 * a calibration program, observing the total bandwidth delivered with
 * different numbers of IO threads.
 */
public class IoThreadLoadObserver implements ThreadIoMonitor, LoadObserver,
        CellInfoProvider
{
    @VisibleForTesting
    static final Duration PURGE_PAUSE_DURATION = Duration.of(1, ChronoUnit.SECONDS);

    /**
     * An instance of time when the number of concurrent IO threads changed.
     * Instances of this class record when the thread changed and the updated
     * thread count.
     */
    private static class ThreadCountChange
    {
        private final Instant when;
        private final int updatedThreadCount;

        public ThreadCountChange(Instant when, int newCount)
        {
            this.when = when;
            this.updatedThreadCount = newCount;
        }

        public boolean isBefore(Instant other)
        {
            return when.isBefore(other);
        }

        /**
         * Return a new ThreadCountChange with the same number of threads but
         * with the supplied instance.
         * @param when The time when the new change happened.
         * @return The new ThreadCountChange
         */
        public ThreadCountChange startingAt(Instant when)
        {
            return new ThreadCountChange(when, updatedThreadCount);
        }

        public LoadAccumulator addToLoad(LoadAccumulator load, ThreadCountChange previousChange)
        {
            Duration elapsed = Duration.between(previousChange.when, when);
            return load.add(elapsed, previousChange.updatedThreadCount);
        }

        public LoadAccumulator addToLoad(LoadAccumulator load, Instant whenTo)
        {
            Duration elapsed = Duration.between(when, whenTo);
            return load.add(elapsed, updatedThreadCount);
        }
    }

    /**
     * A class that holds the load, as being calculated while walking the
     * history.
     */
    private static class LoadAccumulator
    {
        private Duration duration = Duration.ZERO;
        private double load = 0;

        private double inFractionalSeconds(Duration duration)
        {
            return duration.getSeconds()
                    + ((double)duration.getNano()) / ChronoUnit.SECONDS.getDuration().toNanos();
        }

        public LoadAccumulator add(Duration elapsed, int threads)
        {
            duration = duration.plus(elapsed);
            load += threads * inFractionalSeconds(elapsed);
            return this;
        }

        public double get(Duration expectedDuration)
        {
            Duration unaccountedFor = expectedDuration.minus(duration);
            if (!unaccountedFor.isNegative() && !unaccountedFor.isZero()) {
                add(unaccountedFor, 0);
            }
            return load;
        }
    }

    private final Duration historyDuration;
    private final Clock clock;
    private final List<ThreadCountChange> history = new LinkedList<>();

    private int concurrentThreads;
    private Instant nextPurgeHistory;
    private int maxConcurrentThreads = 1;

    public IoThreadLoadObserver(Duration historyDuration)
    {
        this(historyDuration, Clock.systemDefaultZone());
    }

    IoThreadLoadObserver(Duration historyDuration, Clock clock)
    {
        this.historyDuration = historyDuration;
        this.clock = clock;
        nextPurgeHistory = nextPurge(Instant.now(clock));
    }

    /**
     * The maximum number of concurrent IO threads that the storage system
     * can cope with.  This is used to scale the reported IO load so that 1.0
     * indicates the maximum number of concurrent requests the storage device
     * can cope with, without seeing significant performance degradation.
     * @param value The maximum number of simultaneous IO requests without degradation.
     */
    public synchronized void setMaxConcurrentThreads(int value)
    {
        checkArgument(value > 0, "max concurrent threads must be positive");
        maxConcurrentThreads = value;
    }

    @Override
    public synchronized void threadIoStarted()
    {
        updateCount(1);
    }

    @Override
    public synchronized void threadIoFinished()
    {
        updateCount(-1);
    }

    @GuardedBy("this")
    private void updateCount(int delta)
    {
        Instant now = Instant.now(clock);

        if (now.isAfter(nextPurgeHistory)) {
            Instant historyStart = now.minus(historyDuration);
            purgeOldData(historyStart);
            nextPurgeHistory = nextPurge(now);
        }

        concurrentThreads += delta;
        if (concurrentThreads < 0) {
            concurrentThreads = 0;
            throw new IllegalArgumentException("Attempt to record IO thread stopping that didn't start.");
        }

        ThreadCountChange change = new ThreadCountChange(now, concurrentThreads);
        history.add(change);
    }

    private static Instant nextPurge(Instant now)
    {
        return now.plus(PURGE_PAUSE_DURATION);
    }

    @GuardedBy("this")
    private void purgeOldData(Instant historyStart)
    {
        // If we have a single change, always keep it.  We need (at most) one
        // change that happened before our current history period to know the
        // thread count at the start of our history period.
        if (history.size() < 2) {
            return;
        }

        // Scan through history.  We want (at most) one change that happened
        // before our current history period.
        ListIterator<ThreadCountChange> itr = history.listIterator(1);
        while (itr.hasNext()) {
            ThreadCountChange change = itr.next();
            if (change.isBefore(historyStart)) {
                itr.previous();
                itr.previous();
                itr.remove();
                itr.next();
            } else {
                // ThreadCountChange objects are recorded in time-order.  Once
                // we are within our history period, there are no further
                // changes to consider.
                break;
            }
        }
    }

    @Override
    public synchronized double currentLoad()
    {
        if (history.isEmpty()) {
            return 0d;
        }

        Instant now = Instant.now(clock);
        Instant historyStart = now.minus(historyDuration);

        purgeOldData(historyStart);

        Iterator<ThreadCountChange> itr = history.iterator();

        ThreadCountChange firstChange = itr.next();
        ThreadCountChange previousChange = firstChange.isBefore(historyStart)
                ? firstChange.startingAt(historyStart)
                : firstChange;

        LoadAccumulator load = new LoadAccumulator();
        while (itr.hasNext()) {
            ThreadCountChange thisChange = itr.next();

            load = thisChange.addToLoad(load, previousChange);

            previousChange = thisChange;
        }

        load = previousChange.addToLoad(load, now);

        return load.get(historyDuration) / maxConcurrentThreads;
    }

    @Override
    public void getInfo(PrintWriter pw)
    {
        pw.println("IO load: " + (currentLoad()*100) + "%");
    }
}
