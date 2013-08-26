package dmg.cells.nucleus;

import org.junit.Before;
import org.junit.Test;

import static org.hamcrest.Matchers.is;

import static org.junit.Assert.assertThat;

public class CDCTests
{
    @Before
    public void setup()
    {
        CDC.clear();
        CDC.resetDiagnose();
    }

    @Test
    public void shouldHaveDiagnoseOffByDefault()
    {
        assertThat(CDC.isDiagnoseEnabled(), is(false));
    }

    @Test
    public void shouldBeAbleToEnableDiagnose()
    {
        CDC.setDiagnoseEnabled(true);
        assertThat(CDC.isDiagnoseEnabled(), is(true));
    }

    @Test
    public void shouldBeAbleToDisableDiagnose()
    {
        CDC.setDiagnoseEnabled(true);
        CDC.setDiagnoseEnabled(false);
        assertThat(CDC.isDiagnoseEnabled(), is(false));
    }

    @Test
    public void shouldResetDiagnose()
    {
        try (CDC ignored = new CDC()) {
            CDC.setDiagnoseEnabled(true);
        }

        assertThat(CDC.isDiagnoseEnabled(), is(false));
    }

    @Test
    public void shouldBeIndependentOfOtherThreadsEnabling() throws InterruptedException
    {
        Thread t = new Thread(new Runnable() {
            @Override
            public void run()
            {
                CDC.setDiagnoseEnabled(true);
            }
        });

        t.start();
        t.join();

        assertThat(CDC.isDiagnoseEnabled(), is(false));
    }


    @Test
    public void shouldInheritValue() throws InterruptedException
    {
        CDC.setDiagnoseEnabled(true);

        final CDC captured = new CDC();

        Thread t = new Thread(new Runnable() {
            @Override
            public void run()
            {
                try (CDC ignored = captured.restore()) {
                    assertThat(CDC.isDiagnoseEnabled(), is(true));
                }
            }
        });

        t.start();
        t.join();
    }

    @Test
    public void shouldInfluenceValue() throws InterruptedException
    {
        final CDC captured = new CDC();

        Thread t = new Thread(new Runnable() {
            @Override
            public void run()
            {
                try (CDC ignored = captured.restore()) {
                    CDC.setDiagnoseEnabled(true);
                }
            }
        });

        t.start();
        t.join();

        assertThat(CDC.isDiagnoseEnabled(), is(true));
    }
}
