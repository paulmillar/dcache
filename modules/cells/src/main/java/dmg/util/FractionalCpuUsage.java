package dmg.util;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * Represents a fractions of CPU time within some time-period.  Models CPU usage
 * logically as combined = user + system, with 'combined', 'user' and 'system'
 * as integers >= 0.
 *
 * A computer's CPU may have multiple cores while a single thread can utilise
 * only one core at any time.  Therefore a thread may be using 100% of the
 * available CPU, yet the computer will not be utilising 100% of available
 * resources.
 *
 * The model here looks at the CPU usage of the system as a whole; therefore
 * on a n-core machine, a thread can use, at most, 100/n percent of the
 * available CPU.
 *
 * Returned usage numbers are from  [0..1] with 'user' less than or equal to
 * 'combined'.
 */
public class FractionalCpuUsage
{
    private final double _combinedCpuUsage;
    private final double _userCpuUsage;
    private final long _quantum;

    /**
     *  quantum is the duration in which the CpuUsage was consumed.  The value
     *  is in nanoseconds.  The number of cores is that of the machine that
     *  the JVM is running that creates this object.
     */
    public FractionalCpuUsage(CpuUsage usage, long quantum)
    {
        this(usage.getCombined(), usage.getUser(), quantum,
                Runtime.getRuntime().availableProcessors());
    }

    /**
     * Values are all in nanoseconds.
     */
    public FractionalCpuUsage(long totalCpuUsage, long userCpuUsage,
            long quantum, int cores)
    {
        /* Since max CPU usage for quantum in NUMBER_OF_CORES*quantum, we
         * normalise the usage by averaging over the number or cores.  This
         * means that on a multi-core machine, a single thread will never
         * consume 100%
         */
        totalCpuUsage /= cores;
        userCpuUsage /= cores;

        checkArgument(totalCpuUsage <= quantum, "total (" + totalCpuUsage +
                ") usage exceeds quantum (" + quantum + ")");
        checkArgument(userCpuUsage <= quantum, "user usage exceeds quantum");
        checkArgument(userCpuUsage <= totalCpuUsage, "user usage (" +
                userCpuUsage + ") exceeds total (" + totalCpuUsage + ")");
        _quantum = quantum;
        _combinedCpuUsage = totalCpuUsage / (double)quantum;
        _userCpuUsage = userCpuUsage / (double)quantum;
    }


    /**
     * Fraction of time in quantum where CPU was active.
     * @return number [0..1]
     */
    public double getCombinedCpuUsage()
    {
        return _combinedCpuUsage;
    }


    /**
     * Fraction of time in quantum where CPU was active with user activity.
     * @return number [0..1]
     */
    public double getUserCpuUsage()
    {
        return _userCpuUsage;
    }


    /**
     * Fraction of time in quantum where CPU was active with system activity.
     * @return number [0..1]
     */
    public double getSystemCpuUsage()
    {
        return _combinedCpuUsage - _userCpuUsage;
    }


    /**
     * Length of quantum in nanoseconds.
     */
    public long getQuantum()
    {
        return _quantum;
    }
}
