package org.dcache.util;

import com.google.common.base.Throwables;
import org.junit.Before;
import org.junit.Test;

import static org.hamcrest.Matchers.*;

import static org.junit.Assert.assertThat;

public class SDCTests
{
    @Before
    public void setup()
    {
        SDC.reset();
    }

    @Test
    public void shouldBeInitiallyNull()
    {
        assertThat(SDC.get("key"), is(nullValue()));
    }

    @Test
    public void shouldSeeNewValue()
    {
        SDC.put("key", "value");

        assertThat(SDC.get("key"), is(equalTo("value")));
    }

    @Test
    public void shouldSeeNullAfterRemoving()
    {
        SDC.put("key", "value");
        SDC.remove("key");

        assertThat(SDC.get("key"), is(nullValue()));
    }

    @Test
    public void shouldSeeNullAfterUpdatingWithNullValue()
    {
        SDC.put("key", "value");
        SDC.put("key", null);

        assertThat(SDC.get("key"), is(nullValue()));
    }

    @Test
    public void shouldSeeUpdatedValue()
    {
        SDC.put("key", "value");
        SDC.put("key", "new-value");
        assertThat(SDC.get("key"), is(equalTo("new-value")));
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
        SDC captured = new SDC();
        captured.rollback();
        captured.rollback();
    }

    @Test(expected=IllegalStateException.class)
    public void shouldThrowExceptionWhenAdoptedTwice() throws InterruptedException
    {
        SDC captured = new SDC();
        captured.adopt();
        captured.adopt();
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
                adopt(captured);
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
                adopt(captured);
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
                adopt(captured);
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
                adopt(captured);
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
                adopt(captured);
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
                adopt(captured);
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
                adopt(captured);
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
                adopt(captured);
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
                adopt(captured);
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
                adopt(captured);
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
                adopt(captured);
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
                adopt(captureForSharing);
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
                adopt(captureForSharing);

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
                adopt(capture1);
                assertThat(SDC.get("key"), is("value"));
            }
        });

        run(new Runnable() {
            @Override
            public void run()
            {
                adopt(capture2);
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
                adopt(capture1);
                SDC.put("key", "new-value");
            }
        });

        run(new Runnable() {
            @Override
            public void run()
            {
                adopt(capture2);
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
                adopt(capture1);
                assertThat(SDC.get("key"), is("value"));
            }
        });

        run(new Runnable() {
            @Override
            public void run()
            {
                adopt(capture2);
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
                adopt(capture1);
                assertThat(SDC.get("key"), is("value"));
            }
        });

        run(new Runnable() {
            @Override
            public void run()
            {
                adopt(capture2);
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
                adopt(capture1);
                SDC.put("key", "new-value");
            }
        });

        run(new Runnable() {
            @Override
            public void run()
            {
                adopt(capture2);
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
                adopt(capture1);
                assertThat(SDC.get("key"), is("value"));
            }
        });

        run(new Runnable() {
            @Override
            public void run()
            {
                adopt(capture2);
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
                adopt(capture1);
                assertThat(SDC.get("key"), is("value"));
            }
        });

        run(new Runnable() {
            @Override
            public void run()
            {
                adopt(capture2);
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
                adopt(capture1);
                SDC.put("key", "new-value");
            }
        });

        run(new Runnable() {
            @Override
            public void run()
            {
                adopt(capture2);
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
                adopt(capture1);
                assertThat(SDC.get("key"), is("value"));
            }
        });

        run(new Runnable() {
            @Override
            public void run()
            {
                adopt(capture2);
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

        capture.rollback(); // prevent being reported as a bug
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

        capture.rollback(); // prevent being reported as a bug
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

    @Test
    public void shouldSeeOriginalValueWhenRemoveFromCaptured()
    {
        SDC.put("key", "value");

        SDC capture = new SDC();

        capture.localRemove("key");

        assertThat(SDC.get("key"), is("value"));

        capture.rollback(); // prevent being reported as a bug
    }

    @Test
    public void shouldSeeNullWhenRemoveFromCapturedAfterAdopt()
            throws InterruptedException
    {
        SDC.put("key", "value");

        SDC capture = new SDC();

        capture.localRemove("key");

        capture.adopt();

        assertThat(SDC.get("key"), is(nullValue()));
    }

    private static void run(Runnable task) throws InterruptedException
    {
        Thread t = new Thread(task);
        t.start();
        t.join();
    }

    private static void adopt(SDC captured)
    {
        try {
            captured.adopt();
        } catch (InterruptedException e) {
            Throwables.propagate(e);
        }
    }
}
