package org.dcache.junit;

/**
 * A JUnit category for any test or test-suite that relies on the passage of
 * time.  One example is a test that checks that an expiring item is removed
 * within a tolerable period.
 */
public interface AgainstTheClock extends PerformanceCritical
{

}
