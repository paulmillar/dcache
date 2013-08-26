package org.dcache.util;

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
    public void shouldHaveNewValue()
    {
        SDC.put("key", "value");

        assertThat(SDC.get("key"), is(equalTo("value")));
    }

    @Test
    public void shouldBeAbleToRemoveKey()
    {
        SDC.put("key", "value");
        SDC.remove("key");

        assertThat(SDC.get("key"), is(nullValue()));
    }

    @Test
    public void shouldRemoveWhenUpdatingWithNullValue()
    {
        SDC.put("key", "value");
        SDC.put("key", null);

        assertThat(SDC.get("key"), is(nullValue()));
    }

    @Test
    public void shouldHaveUpdatedValue()
    {
        SDC.put("key", "value");
        SDC.put("key", "new-value");
        assertThat(SDC.get("key"), is(equalTo("new-value")));
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
    public void shouldThrowExceptionWhenEnactTwice()
    {
        SDC captured = new SDC();
        captured.enact();
        captured.enact();
    }

    @Test
    public void shouldHaveSameValueInAnotherThreadWhenShared()
            throws InterruptedException
    {
        SDC.put("key", "value");
        final SDC captured = new SDC();

        run( new Runnable() {
            @Override
            public void run()
            {
                captured.enact();
                assertThat(SDC.get("key"), is(equalTo("value")));
            }
        });
    }

    @Test
    public void shouldHaveSameValueInAnotherThreadWhenSharedAfterCapture()
            throws InterruptedException
    {
        final SDC captured = new SDC();
        SDC.put("key", "value");

        run( new Runnable() {
            @Override
            public void run()
            {
                captured.enact();
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
                captured.enact();
                SDC.put("key", "value");
            }
        });

        assertThat(SDC.get("key"), is(equalTo("value")));
    }


    @Test
    public void shouldSeeUpdateValueAfterRollback()
            throws InterruptedException
    {
        final SDC captured1 = new SDC();
        final SDC captured2 = new SDC();

        run(new Runnable() {
            @Override
            public void run()
            {
                captured1.enact();
                SDC.put("key", "value");
            }
        });

        captured2.rollback();

        assertThat(SDC.get("key"), is(equalTo("value")));
    }

    @Test
    public void should()
            throws InterruptedException
    {
        final SDC captured = new SDC();

        run(new Runnable() {
            @Override
            public void run()
            {
                captured.enact();
                SDC.put("key", "value");
            }
        });

        assertThat(SDC.get("key"), is(equalTo("value")));
    }

    private static void run(Runnable task) throws InterruptedException
    {
        Thread t = new Thread(task);
        t.start();
        t.join();
    }
}
