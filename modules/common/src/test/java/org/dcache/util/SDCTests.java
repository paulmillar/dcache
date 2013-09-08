package org.dcache.util;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.google.common.base.Throwables;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.hamcrest.Matchers.*;

import static org.junit.Assert.assertThat;
import static org.slf4j.Logger.ROOT_LOGGER_NAME;
import static org.slf4j.LoggerFactory.getLogger;

public class SDCTests
{
    final ExecutorService _executor = Executors.newSingleThreadExecutor();

    @Before
    public void setup()
    {
        setLoggingLevel(Level.INFO);
        SDC.reset();
    }

    @Test
    public void shouldBeInitiallyNull()
    {
        assertThat(SDC.get("key"), is(nullValue()));
        assertThat(SDC.isSet("key"), is(equalTo(false)));
    }

    @Test
    public void shouldSeeNewValue()
    {
        SDC.put("key", "value");

        assertThat(SDC.get("key"), is(equalTo("value")));
        assertThat(SDC.isSet("key"), is(equalTo(true)));
    }

    @Test
    public void shouldSeeNullAfterRemoving()
    {
        SDC.put("key", "value");
        SDC.remove("key");

        assertThat(SDC.get("key"), is(nullValue()));
        assertThat(SDC.isSet("key"), is(equalTo(false)));
    }

    @Test
    public void shouldSeeNullAfterUpdatingWithNullValue()
    {
        SDC.put("key", "value");
        SDC.put("key", null);

        assertThat(SDC.get("key"), is(nullValue()));
        assertThat(SDC.isSet("key"), is(equalTo(false)));
    }

    @Test
    public void shouldSeeUpdatedValue()
    {
        SDC.put("key", "value");
        SDC.put("key", "new-value");
        assertThat(SDC.get("key"), is(equalTo("new-value")));
        assertThat(SDC.isSet("key"), is(equalTo(true)));
    }

    @Test
    public void shouldBeIndependentFromOtherThreadInitialValue() throws InterruptedException
    {
        SDC.put("key", "value");

        run(new Runnable() {
            @Override
            public void run()
            {
                assertThat(SDC.get("key"), is(nullValue()));
            }
        });
    }

    @Test
    public void shouldBeIndependentFromOtherThreadsUpdates() throws InterruptedException
    {
        SDC.put("key", "value");

        run(new Runnable() {
            @Override
            public void run()
            {
                SDC.put("key", "new-value");
            }
        });

        assertThat(SDC.get("key"), is("value"));
    }

    @Test
    public void shouldBeAbleToRollback()
    {
        SDC.put("key", "value");
        SDC captured = new SDC();
        SDC.put("key", "new-value");
        captured.rollback();
        assertThat(SDC.get("key"), is(equalTo("value")));
    }

    @Test(expected=IllegalStateException.class)
    public void shouldThrowExceptionWhenRollbackTwice()
    {
        setLoggingLevel(Level.OFF);
        SDC captured = new SDC();
        captured.rollback();
        captured.rollback();
    }

    @Test
    public void shouldSupportCallingAdoptTwice() throws InterruptedException
    {
        SDC.put("key", "value");
        SDC captured = new SDC();

        SDC.reset();
        captured.adopt();
        assertThat(SDC.get("key"), is(equalTo("value")));

        SDC.reset();
        captured.adopt();
        assertThat(SDC.get("key"), is(equalTo("value")));
    }

    @Test
    public void shouldSeeSameValueInAnotherThreadWhenShared()
            throws InterruptedException
    {
        SDC.put("key", "value");

        final SDC captured = new SDC();

        run( new Runnable() {
            @Override
            public void run()
            {
                captured.adopt();
                assertThat(SDC.get("key"), is(equalTo("value")));
            }
        });
    }

    @Test
    public void shouldSeeNullAfterAdopt()
            throws InterruptedException
    {
        final SDC captured = new SDC();

        run( new Runnable() {
            @Override
            public void run()
            {
                SDC.put("key", "value");
                captured.adopt();
                assertThat(SDC.get("key"), is(nullValue()));
            }
        });
    }

    @Test
    public void shouldSeeSameValueInAnotherThreadWhenSharedAndUpdatedAfterCapture()
            throws InterruptedException
    {
        final SDC captured = new SDC();

        SDC.put("key", "value");

        run( new Runnable() {
            @Override
            public void run()
            {
                captured.adopt();
                assertThat(SDC.get("key"), is(equalTo("value")));
            }
        });
    }

    @Test
    public void shouldSeeNewValueWhenAnotherThreadUpdatesSharedContext()
            throws InterruptedException
    {
        final SDC captured = new SDC();

        run( new Runnable() {
            @Override
            public void run()
            {
                captured.adopt();
                SDC.put("key", "value");
            }
        });

        assertThat(SDC.get("key"), is(equalTo("value")));
    }

    @Test
    public void shouldSeeNullWhenAnotherThreadPutsNullFromSharedContext()
            throws InterruptedException
    {
        final SDC captured = new SDC();

        run( new Runnable() {
            @Override
            public void run()
            {
                captured.adopt();
                SDC.put("key", null);
            }
        });

        assertThat(SDC.get("key"), is(nullValue()));
    }

    @Test
    public void shouldSeeUpdatedValueWhenAnotherThreadUpdatesSharedContextUpdateAfterCature()
            throws InterruptedException
    {
        final SDC captured = new SDC();

        SDC.put("key", "value");

        run( new Runnable() {
            @Override
            public void run()
            {
                captured.adopt();
                SDC.put("key", "new-value");
            }
        });

        assertThat(SDC.get("key"), is(equalTo("new-value")));
    }


    @Test
    public void shouldSeeNullWhenAnotherThreadRemovesFromSharedContextUpdateAfterCature()
            throws InterruptedException
    {
        final SDC captured = new SDC();

        SDC.put("key", "value");

        run( new Runnable() {
            @Override
            public void run()
            {
                captured.adopt();
                SDC.remove("key");
            }
        });

        assertThat(SDC.get("key"), is(nullValue()));
    }


    @Test
    public void shouldSeeNullWhenAnotherThreadPutsNullIntoSharedContextUpdateAfterCature()
            throws InterruptedException
    {
        final SDC captured = new SDC();

        SDC.put("key", "value");

        run( new Runnable() {
            @Override
            public void run()
            {
                captured.adopt();
                SDC.put("key", null);
            }
        });

        assertThat(SDC.get("key"), is(nullValue()));
    }


    @Test
    public void shouldSeeUpdatedValueWhenAnotherThreadUpdatesSharedContext()
            throws InterruptedException
    {
        SDC.put("key", "value");

        final SDC captured = new SDC();

        run( new Runnable() {
            @Override
            public void run()
            {
                captured.adopt();
                SDC.put("key", "new-value");
            }
        });

        assertThat(SDC.get("key"), is(equalTo("new-value")));
    }

    @Test
    public void shouldSeeNullWhenAnotherThreadRemovesFromSharedContext()
            throws InterruptedException
    {
        SDC.put("key", "value");

        final SDC captured = new SDC();

        run( new Runnable() {
            @Override
            public void run()
            {
                captured.adopt();
                SDC.remove("key");
            }
        });

        assertThat(SDC.get("key"), is(nullValue()));
    }

    @Test
    public void shouldSeeNullWhenAnotherThreadPutsNullIntoSharedContext()
            throws InterruptedException
    {
        SDC.put("key", "value");

        final SDC captured = new SDC();

        run( new Runnable() {
            @Override
            public void run()
            {
                captured.adopt();
                SDC.put("key", null);
            }
        });

        assertThat(SDC.get("key"), is(nullValue()));
    }


    @Test
    public void shouldSeeUpdateValueAfterRollback()
            throws InterruptedException
    {
        SDC.put("key", "value");

        final SDC captureForSharing = new SDC();

        final SDC captureForRollingBack = new SDC();

        SDC.put("key", "value to be rolled back");

        run(new Runnable() {
            @Override
            public void run()
            {
                captureForSharing.adopt();
                SDC.put("key", "new-value");
            }
        });

        captureForRollingBack.rollback();

        assertThat(SDC.get("key"), is(equalTo("new-value")));
    }

    @Test
    public void shouldSeeUpdateValueAfterRollbackBeforeSharing()
            throws InterruptedException
    {
        SDC.put("key", "value");

        final SDC captureForRollingBack = new SDC();

        final SDC captureForSharing = new SDC();

        SDC.put("key", "value to be rolled back");

        run(new Runnable() {
            @Override
            public void run()
            {
                captureForSharing.adopt();
                SDC.put("key", "new-value");
            }
        });

        captureForRollingBack.rollback();

        assertThat(SDC.get("key"), is(equalTo("new-value")));
    }

    @Test
    public void shouldSeeUpdateValueInThreadThatSharesTemporally()
            throws InterruptedException
    {
        SDC.put("key", "value");

        final SDC captureForSharing = new SDC();

        run(new Runnable() {
            @Override
            public void run()
            {
                SDC captureForRollingBack = new SDC();
                captureForSharing.adopt();

                SDC.put("key", "new-value");

                captureForRollingBack.rollback();
            }
        });

        assertThat(SDC.get("key"), is(equalTo("new-value")));
    }

    @Test
    public void shouldAllSeeSharedValueAfterTripleShare() throws InterruptedException
    {
        SDC.put("key", "value");

        final SDC capture1 = new SDC();
        final SDC capture2 = new SDC();

        run(new Runnable() {
            @Override
            public void run()
            {
                capture1.adopt();
                assertThat(SDC.get("key"), is("value"));
            }
        });

        run(new Runnable() {
            @Override
            public void run()
            {
                capture2.adopt();
                assertThat(SDC.get("key"), is("value"));
            }
        });

        assertThat(SDC.get("key"), is("value"));
    }

    @Test
    public void shouldAllSeeSharedValueAfterTripleShareFirstUpdates() throws InterruptedException
    {
        SDC.put("key", "value");

        final SDC capture1 = new SDC();
        final SDC capture2 = new SDC();

        run(new Runnable() {
            @Override
            public void run()
            {
                capture1.adopt();
                SDC.put("key", "new-value");
            }
        });

        run(new Runnable() {
            @Override
            public void run()
            {
                capture2.adopt();
                assertThat(SDC.get("key"), is("new-value"));
            }
        });

        assertThat(SDC.get("key"), is("new-value"));
    }

    @Test
    public void shouldAllSeeSharedValueAfterTripleShareSecondUpdates() throws InterruptedException
    {
        SDC.put("key", "value");

        final SDC capture1 = new SDC();
        final SDC capture2 = new SDC();

        run(new Runnable() {
            @Override
            public void run()
            {
                capture1.adopt();
                assertThat(SDC.get("key"), is("value"));
            }
        });

        run(new Runnable() {
            @Override
            public void run()
            {
                capture2.adopt();
                SDC.put("key", "new-value");
            }
        });

        assertThat(SDC.get("key"), is("new-value"));
    }

    @Test
    public void shouldAllSeeSharedValueAfterTripleShare2() throws InterruptedException
    {
        final SDC capture1 = new SDC();

        SDC.put("key", "value");

        final SDC capture2 = new SDC();

        run(new Runnable() {
            @Override
            public void run()
            {
                capture1.adopt();
                assertThat(SDC.get("key"), is("value"));
            }
        });

        run(new Runnable() {
            @Override
            public void run()
            {
                capture2.adopt();
                assertThat(SDC.get("key"), is("value"));
            }
        });

        assertThat(SDC.get("key"), is("value"));
    }

    @Test
    public void shouldAllSeeSharedValueAfterTripleShare2FirstUpdates() throws InterruptedException
    {
        final SDC capture1 = new SDC();

        SDC.put("key", "value");

        final SDC capture2 = new SDC();

        run(new Runnable() {
            @Override
            public void run()
            {
                capture1.adopt();
                SDC.put("key", "new-value");
            }
        });

        run(new Runnable() {
            @Override
            public void run()
            {
                capture2.adopt();
                assertThat(SDC.get("key"), is("new-value"));
            }
        });

        assertThat(SDC.get("key"), is("new-value"));
    }

    @Test
    public void shouldAllSeeSharedValueAfterTripleShare2SecondUpdates() throws InterruptedException
    {
        final SDC capture1 = new SDC();

        SDC.put("key", "value");

        final SDC capture2 = new SDC();

        run(new Runnable() {
            @Override
            public void run()
            {
                capture1.adopt();
                assertThat(SDC.get("key"), is("value"));
            }
        });

        run(new Runnable() {
            @Override
            public void run()
            {
                capture2.adopt();
                SDC.put("key", "new-value");
            }
        });

        assertThat(SDC.get("key"), is("new-value"));
    }

    @Test
    public void shouldAllSeeSharedValueAfterTripleShare3() throws InterruptedException
    {
        final SDC capture1 = new SDC();
        final SDC capture2 = new SDC();

        SDC.put("key", "value");

        run(new Runnable() {
            @Override
            public void run()
            {
                capture1.adopt();
                assertThat(SDC.get("key"), is("value"));
            }
        });

        run(new Runnable() {
            @Override
            public void run()
            {
                capture2.adopt();
                assertThat(SDC.get("key"), is("value"));
            }
        });

        assertThat(SDC.get("key"), is("value"));
    }

    @Test
    public void shouldAllSeeSharedValueAfterTripleShare3FirstUpdates() throws InterruptedException
    {
        final SDC capture1 = new SDC();
        final SDC capture2 = new SDC();

        SDC.put("key", "value");

        run(new Runnable() {
            @Override
            public void run()
            {
                capture1.adopt();
                SDC.put("key", "new-value");
            }
        });

        run(new Runnable() {
            @Override
            public void run()
            {
                capture2.adopt();
                assertThat(SDC.get("key"), is("new-value"));
            }
        });

        assertThat(SDC.get("key"), is("new-value"));
    }

    @Test
    public void shouldAllSeeSharedValueAfterTripleShare3SecondUpdates() throws InterruptedException
    {
        final SDC capture1 = new SDC();
        final SDC capture2 = new SDC();

        SDC.put("key", "value");

        run(new Runnable() {
            @Override
            public void run()
            {
                capture1.adopt();
                assertThat(SDC.get("key"), is("value"));
            }
        });

        run(new Runnable() {
            @Override
            public void run()
            {
                capture2.adopt();
                SDC.put("key", "new-value");
            }
        });

        assertThat(SDC.get("key"), is("new-value"));
    }

    @Test
    public void shouldNotSeeCaptureUpdatedValue()
    {
        SDC.put("key", "value");

        SDC capture = new SDC();

        capture.localPut("key", "new-value");

        assertThat(SDC.get("key"), is("value"));
    }

    @Test
    public void shouldNotSeeCaptureUpdatedValueAfterRollback()
    {
        SDC.put("key", "value");

        SDC capture = new SDC();

        capture.localPut("key", "new-value");

        capture.rollback();

        assertThat(SDC.get("key"), is("value"));
    }

    @Test
    public void shouldSeeCaptureUpdatedValueAfterAdopt()
            throws InterruptedException
    {
        SDC.put("key", "value");

        SDC capture = new SDC();

        capture.localPut("key", "new-value");

        capture.adopt();

        assertThat(SDC.get("key"), is("new-value"));
    }

    @Test
    public void shouldSeeOriginalValueWhenPutNullIntoCaptured()
    {
        SDC.put("key", "value");

        SDC capture = new SDC();

        capture.localPut("key", null);

        assertThat(SDC.get("key"), is("value"));
    }

    @Test
    public void shouldSeeNullWhenPutNullIntoCapturedAfterAdopt()
            throws InterruptedException
    {
        SDC.put("key", "value");

        SDC capture = new SDC();

        capture.localPut("key", null);

        capture.adopt();

        assertThat(SDC.get("key"), is(nullValue()));
    }


    /**
     * This test is potentially dodgy.  It relies on the behaviour of gc and
     * runFinalization.
     */
    @Test
    public void shouldSeeCaptureRemoved() throws InterruptedException
    {
        assertThat(SDC.countActiveCaptures(), is(0));

        SDC.put("key", "value");

        assertThat(SDC.countActiveCaptures(), is(0));


        final SDC capture = new SDC();

        assertThat(SDC.countActiveCaptures(), is(1));

        run(new Runnable(){
            @Override
            public void run()
            {
                assertThat(SDC.countActiveCaptures(), is(0));
                capture.adopt();
                assertThat(SDC.countActiveCaptures(), is(0));
                new SDC();
                assertThat(SDC.countActiveCaptures(), is(1));
                SDC.put("key", "new-value");
                assertThat(SDC.countActiveCaptures(), is(1));
            }
        });
        assertThat(SDC.countActiveCaptures(), is(1));
        assertThat(SDC.get("key"), is("new-value"));

        /*
         * This part is dodgy.  System.gc and System.runFinalization provide
         * the JVM with hints: it can ignore both calls.  The Thread.yield is
         * to allow the finalizing thread a chance to continue, assuming it will
         * finish the finalize method before returning here.
         *
         * In practise, this seems to work reliably for OpenJDK on my laptop.
         */
        System.gc();
        System.runFinalization();
        // There is a race between this thread counting the number of shared
        // contexts and the finalizing thread removing the context.  Normally
        // Thread.yield is sufficient to allow the finalizing thread to win the
        // race, but we give it a head start to make this more likely.
        Thread.sleep(10);
        Thread.yield();
        Thread.sleep(10);

        //  The following test is potentially dodgy; in practice, it seems fine.
        assertThat(SDC.countActiveCaptures(), is(0));
        assertThat(SDC.get("key"), is("new-value"));
    }

    /**
     * Run some task in another thread.  The calling thread waits until
     * the task is finished.  The SDC is reset before running the task.  Any
     * AssertionError in the other thread is propagated.
     */
    private void run(final Runnable task) throws InterruptedException
    {
        Future f = _executor.submit(new Runnable(){
            @Override
            public void run()
            {
                SDC.reset(); // Provide a clean environment for task
                task.run();
                SDC.reset(); // Remove reference to the current capture
            }
        }, null);
        try {
            f.get();
        } catch (ExecutionException e) {
            Throwables.propagate(e.getCause());
        }
    }

    public static void setLoggingLevel(Level level)
    {
        Logger root = (ch.qos.logback.classic.Logger) getLogger(ROOT_LOGGER_NAME);
        root.setLevel(level);
    }
}
