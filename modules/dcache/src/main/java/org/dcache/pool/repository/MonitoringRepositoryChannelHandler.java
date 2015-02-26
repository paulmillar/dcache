package org.dcache.pool.repository;

import java.io.Serializable;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A MonitoringChannel wraps some other RepositoryChannel instance and
 * monitors what percentage of time is spent processing requests.
 */
public class MonitoringRepositoryChannelHandler implements InvocationHandler
{
    private final RepositoryChannel _delegate;

    private final long _created = System.currentTimeMillis();
    private final AtomicLong _timeProcessing = new AtomicLong();

    private volatile long _activeSince;
    private volatile long _closedSince;

    public static RepositoryChannel addMonitoringTo(RepositoryChannel inner)
    {
        MonitoringRepositoryChannelHandler handler = new MonitoringRepositoryChannelHandler(inner);

        return (RepositoryChannel) Proxy.newProxyInstance(
                            MonitoringRepositoryChannelHandler.class.getClassLoader(),
                            new Class[] {RepositoryChannel.class, TransferMonitor.class},
                            handler);
    }

    MonitoringRepositoryChannelHandler(RepositoryChannel delegate)
    {
        _delegate = delegate;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable
    {
        if (method.getName().equals("getStatistics")) {
            return getStatistics();
        }

        if (_activeSince == 0L) {
            _activeSince = System.currentTimeMillis();
        }

        long start = System.currentTimeMillis();
        try {
            return method.invoke(_delegate, args);
        } catch (InvocationTargetException e) {
            throw e.getTargetException();
        } finally {
            long end = System.currentTimeMillis();
            if (method.getName().equals("close") && _closedSince == 0L) {
                _closedSince = end;
            }
            _timeProcessing.addAndGet(end-start);
        }
    }

    public synchronized TransferStatistics getStatistics()
    {
        long closed = 0L;
        long active = 0L;
        long waiting;

        long now = System.currentTimeMillis();
        if (_activeSince == 0L) {
            waiting = now - _created;
        } else {
            waiting = _activeSince - _created;

            if (_closedSince == 0L) {
                active = now - _activeSince;
            } else {
                active = _closedSince - _activeSince;
                closed = now - _closedSince;
            }
        }

        return new TransferStatistics(waiting, active, closed, _timeProcessing.get());
    }
}
