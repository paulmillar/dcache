package org.dcache.pool.repository;

import java.io.Serializable;
import java.util.concurrent.TimeUnit;

/**
 * Summary of a file transfer.  The time is split into three phases:
 * between the start of the transfer and the first operation
 * ({@literal WAITING}); time spend before {@link RepositoryChannel#close}
 * ({@literal ACTIVE}) and time spent until statistics are gathered
 * ({@literal CLOSED}).  The time while ACTIVE is further split
 * into time taken to complete local operations {@literal PROCESSING} and
 * time spent waiting for client activity {@literal PENDING}.  Therefore,
 * duration = WAITING + ACTIVE + CLOSED and ACTIVE = PENDING + PROCESSING.
 */
public class TransferStatistics implements Serializable
{
    private final long _waiting;
    private final long _pending;
    private final long _closed;
    private final long _processing;

    public TransferStatistics(long waiting, long pending, long processing, long closed)
    {
        _waiting = waiting;
        _pending = pending;
        _closed = closed;
        _processing = processing;
    }

    public long getTransferTime(TimeUnit unit)
    {
        return unit.convert(_waiting + _pending + _processing,
                TimeUnit.MILLISECONDS);
    }

    public long getWaiting(TimeUnit unit)
    {
        return unit.convert(_waiting, TimeUnit.MILLISECONDS);
    }

    public long getActive(TimeUnit unit)
    {
        return unit.convert(_pending+_processing, TimeUnit.MILLISECONDS);
    }

    public long getPending(TimeUnit unit)
    {
        return _pending;
    }

    public long getProcessing(TimeUnit unit)
    {
        return _processing;
    }

    public long getClosed(TimeUnit unit)
    {
        return _closed;
    }
}
