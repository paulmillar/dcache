package org.dcache.pool.repository;

/**
 * A class that implements this interface provides statistics about a
 * transfer.
 */
public interface TransferMonitor
{

    /**
     * Gather statistics about this transfer.
     */
    public TransferStatistics getStatistics();
}
