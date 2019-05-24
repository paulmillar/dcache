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

import org.junit.Before;
import org.junit.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.*;

public class IoThreadLoadObserverTest
{
    private static final Duration HISTORY_DURATION = Duration.of(1, ChronoUnit.SECONDS);

    IoThreadLoadObserver observer;
    SkippingClock clock;

    private static class SkippingClock extends Clock
    {
        private Instant now;
        private ZoneId zone;

        public SkippingClock()
        {
            this(Instant.now(), ZoneId.systemDefault());
        }

        public SkippingClock(Instant now, ZoneId zone)
        {
            this.now = now;
            this.zone = zone;
        }

        @Override
        public ZoneId getZone()
        {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId zone)
        {
            return new SkippingClock(now, zone);
        }

        @Override
        public Instant instant()
        {
            return now;
        }

        public void skip(Duration duration)
        {
            now = now.plus(duration);
        }
    }

    @Before
    public void setup()
    {
        clock = new SkippingClock();
        observer = new IoThreadLoadObserver(HISTORY_DURATION, clock);
    }

    @Test(expected=IllegalArgumentException.class)
    public void shouldThrowExceptionWhenDecreasingThreadCountIncorrectly()
    {
        observer.threadIoFinished();
    }

    @Test
    public void shouldGiveZeroLoadInitially()
    {
        assertThat(observer.currentLoad(), is(equalTo(0.0d)));
    }

    @Test
    public void shouldGiveZeroLoadAfterHistory()
    {
        clock.skip(HISTORY_DURATION);

        assertThat(observer.currentLoad(), is(equalTo(0.0d)));
    }

    @Test
    public void shouldGiveExpectedLoadAfterHistoryWithSingleIoThread()
    {
        observer.threadIoStarted();

        clock.skip(HISTORY_DURATION);

        assertThat(observer.currentLoad(), is(equalTo(1.0d)));
    }

    @Test
    public void shouldGiveExpectedLoadWithHalfHistory()
    {
        observer.threadIoStarted();

        clock.skip(HISTORY_DURATION.dividedBy(2));

        assertThat(observer.currentLoad(), is(equalTo(0.5d)));
    }

    @Test
    public void shouldGiveExpectedLoadWithThreadIoFinishedHalfwayThroughHistory()
    {
        observer.threadIoStarted();

        clock.skip(HISTORY_DURATION.dividedBy(2));

        observer.threadIoFinished();

        clock.skip(HISTORY_DURATION.dividedBy(2));

        assertThat(observer.currentLoad(), is(equalTo(0.5d)));
    }

    @Test
    public void shouldGiveExpectedLoadWithIoThreadStartedHalfwayThroughHistory()
    {
        clock.skip(HISTORY_DURATION.dividedBy(2));

        observer.threadIoStarted();

        clock.skip(HISTORY_DURATION.dividedBy(2));

        assertThat(observer.currentLoad(), is(equalTo(0.5d)));
    }

    @Test
    public void shouldGiveExpectedLoadWithTwoIoThreadRuns()
    {
        observer.threadIoStarted();

        clock.skip(HISTORY_DURATION.dividedBy(4));

        observer.threadIoFinished();

        clock.skip(HISTORY_DURATION.dividedBy(4));

        observer.threadIoStarted();

        clock.skip(HISTORY_DURATION.dividedBy(4));

        observer.threadIoFinished();

        clock.skip(HISTORY_DURATION.dividedBy(4));

        assertThat(observer.currentLoad(), is(equalTo(0.5d)));
    }

    @Test
    public void shouldGiveZeroLoadWithExpiredIoThreadRun()
    {
        observer.threadIoStarted();

        clock.skip(HISTORY_DURATION.dividedBy(4));

        observer.threadIoFinished();

        clock.skip(HISTORY_DURATION);

        assertThat(observer.currentLoad(), is(equalTo(0d)));
    }

    @Test
    public void shouldGiveZeroLoadWithTwoIoThreadRun()
    {
        observer.threadIoStarted(); // now one IO thread

        clock.skip(HISTORY_DURATION.dividedBy(4));

        observer.threadIoStarted(); // now two IO threads

        clock.skip(HISTORY_DURATION.dividedBy(4));

        observer.threadIoFinished(); // now one IO thread

        clock.skip(HISTORY_DURATION.dividedBy(4));

        observer.threadIoStarted(); // now two IO threads

        clock.skip(HISTORY_DURATION.dividedBy(4));

        assertThat(observer.currentLoad(), is(equalTo(1.5d)));
    }

    @Test
    public void shouldGiveCorrectAnswerAfterTriggeringPurge()
    {
        observer.threadIoStarted();

        clock.skip(Duration.of(5, ChronoUnit.MILLIS));

        observer.threadIoFinished();

        clock.skip(Duration.of(5, ChronoUnit.MILLIS));

        observer.threadIoStarted();

        clock.skip(Duration.of(5, ChronoUnit.SECONDS));

        observer.threadIoFinished();

        clock.skip(IoThreadLoadObserver.PURGE_PAUSE_DURATION);

        observer.threadIoStarted(); // should trigger a history purge

        clock.skip(HISTORY_DURATION.dividedBy(4));

        observer.threadIoFinished();

        clock.skip(HISTORY_DURATION.dividedBy(4));

        assertThat(observer.currentLoad(), is(equalTo(0.25d)));
    }
}
