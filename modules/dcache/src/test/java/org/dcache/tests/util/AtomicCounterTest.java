package org.dcache.tests.util;

import org.junit.Before;
import org.junit.Test;

import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.dcache.commons.util.AtomicCounter;

import static org.junit.Assert.*;

import org.dcache.commons.util.AtomicCounter;
import org.dcache.junit.AgainstTheClock;
import org.junit.experimental.categories.Category;

public class AtomicCounterTest
{
    private AtomicCounter counter;

    @Before
    public void setup()
    {
        counter = new AtomicCounter();
    }

    @Test
    public void startsAtZero()
    {
        assertEquals(0, counter.get());
    }

    @Test
    public void incrementByOne()
    {
        counter.increment();
        assertEquals(1, counter.get());
    }

    @Test
    public void incrementIsThreadSafe()
        throws InterruptedException
    {
        final int THREADS = 5;
        final int ITERATIONS = 100000;
        ExecutorService executor = Executors.newCachedThreadPool();

        for (int i = 0; i < THREADS; i++) {
            executor.execute(new Runnable() {
                    @Override
                    public void run() {
                        for (int i = 0; i < ITERATIONS; i++) {
                            counter.increment();
                        }
                    }
                });
        }

        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);

        assertEquals(THREADS * ITERATIONS, counter.get());
    }

    @Test
    public void awaitReturnsImmediately()
        throws InterruptedException
    {
        assertTrue(counter.awaitChangeUntil(1, new Date(System.currentTimeMillis() + 2000)));
    }

    @Test
    public void awaitTimesOutInFuture()
        throws InterruptedException
    {
        assertFalse(counter.awaitChangeUntil(0, new Date(System.currentTimeMillis() + 100)));
    }

    @Test
    public void awaitTimesOutImmediately()
        throws InterruptedException
    {
        assertFalse(counter.awaitChangeUntil(0, new Date(System.currentTimeMillis() - 100)));
    }

    @Category(AgainstTheClock.class)
    @Test(expected=InterruptedException.class)
    public void awaitIsInterruptible()
        throws InterruptedException
    {
        ExecutorService executor = Executors.newCachedThreadPool();
        try {
            final Thread thread = Thread.currentThread();
            executor.execute(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            Thread.sleep(100);
                            thread.interrupt();
                        } catch (InterruptedException e) {
                            fail("Test was interrupted");
                        }
                    }
                });
            counter.awaitChangeUntil(0, new Date(System.currentTimeMillis() + 200));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    public void incrementWakensAwait()
        throws InterruptedException
    {
        ExecutorService executor = Executors.newCachedThreadPool();
        try {
            executor.execute(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            Thread.sleep(100);
                            counter.increment();
                        } catch (InterruptedException e) {
                            fail("Test was interrupted");
                        }
                    }
                });
            assertTrue(counter.awaitChangeUntil(0, new Date(System.currentTimeMillis() + 200)));
        } finally {
            executor.shutdownNow();
        }
    }
}
