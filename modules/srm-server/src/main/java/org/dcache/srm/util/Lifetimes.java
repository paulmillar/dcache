package org.dcache.srm.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

import org.dcache.srm.SRMInvalidRequestException;
import org.dcache.util.TimeUtils;
import org.dcache.util.TimeUtils.TimeUnitFormat;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 *  Utility methods for handling lifetime of requests.
 */
public class Lifetimes
{
    private static final Logger LOGGER = LoggerFactory.getLogger(Lifetimes.class);

    /**
     * Calculate the lifetime of this request.
     * @param requestedLifetime the requested lifetime in seconds, or null if absent from request
     * @param maximumLifetime the maximum allowed lifetime in milliseconds.
     * @return the lifetime of this request in milliseconds
     * @throws SRMInvalidRequestException
     */
    public static long calculateLifetime(Integer requestedLifetime, long maximumLifetime)
            throws SRMInvalidRequestException
    {
        return calculateLifetime(requestedLifetime, 0, 0, maximumLifetime);
    }

    /**
     * Calculate the lifetime of this request.
     * @param requestedLifetime the requested lifetime in seconds, or null if absent from request
     * @param size the size of the file or zero if no value is supplied
     * @param minimumBandwidth the expected minimal available bandwidth in MiB/s
     * @param maximumLifetime the maximum allowed lifetime in milliseconds.
     * @return the lifetime of this request in milliseconds
     * @throws SRMInvalidRequestException
     */
    public static long calculateLifetime(Integer requestedLifetime, long size,
            long minimumBandwidth, long maximumLifetime) throws SRMInvalidRequestException
    {
        long lifetimeInSeconds = (requestedLifetime != null) ? requestedLifetime : 0;

        if (lifetimeInSeconds < 0) {
            /* [ SRM 2.2, 5.2.1 ]
             * m) If input parameter desiredTotalRequestTime is 0 (zero), each file request
             *    must be tried at least once. Negative value must be invalid.
             */
            throw new SRMInvalidRequestException("Negative desiredTotalRequestTime is invalid.");
        } else if (lifetimeInSeconds == 0) {
            // Revisit: Behaviour doesn't match the SRM spec
            return maximumLifetime;
        } else {
            long lifetime = TimeUnit.SECONDS.toMillis(lifetimeInSeconds);
            lifetime = updateLifetime(lifetime, size, minimumBandwidth, maximumLifetime);
            return (lifetime > maximumLifetime) ? maximumLifetime : lifetime;
        }
    }

    /**
     * Provide an updated request lifetime, taking into account the file's size
     * and minimum bandwidth.
     * @param requestedLifetime requested lifetime in milliseconds
     * @param size the size of the file
     * @param minimumBandwidth the expected minimal available bandwidth in MiB/s
     * @param maximumLifetime the configured maximum, in milliseconds
     * @return an expected duration for the transfer, in milliseconds
     */
    public static long updateLifetime(long requestedLifetime, long size, long minimumBandwidth, long maximumLifetime)
    {
        long lifetime = requestedLifetime;

        if (size > 0 && minimumBandwidth > 0) {
            long estimatedDuration = SECONDS.toMillis((size/minimumBandwidth) / 1048576L);
            long cappedDuration = estimatedDuration < maximumLifetime ? estimatedDuration : maximumLifetime;
            if (requestedLifetime < cappedDuration) {
                lifetime = cappedDuration;
                LOGGER.info("Requested lifetime of {} for {} bytes too short; adjusting to {}",
                        TimeUtils.duration(requestedLifetime, MILLISECONDS, TimeUnitFormat.SHORT),
                        size,
                        TimeUtils.duration(lifetime, MILLISECONDS, TimeUnitFormat.SHORT));
            }
        }

        return lifetime;
    }
}
