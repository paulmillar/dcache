package org.dcache.junit;

/**
 * A JUnit category for any test or test-suite that provides a measurement
 * of how fast is some operation.  These tests are often compared against
 * previous runs to check for performance regressions.  The test must be run
 * on the same class of hardware for such comparisons to be meaningful.
 */
public interface Benchmark extends PerformanceCritical
{

}
