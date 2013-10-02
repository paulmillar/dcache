package org.dcache.xrootd;

import org.jboss.netty.handler.execution.ChannelEventRunnable;

import java.util.concurrent.Executor;

import dmg.cells.nucleus.CDC;

/**
 * An instance of this class wraps some other Executor and ensures the CDC
 * is preserved.  This class is netty-specific as it handles
 * ChannelEventRunnable differently.
 */
public class CDCExecutorDecorator implements Executor
{
    private final Executor _inner;

    public CDCExecutorDecorator(Executor inner)
    {
        _inner = inner;
    }

    @Override
    public void execute(final Runnable command)
    {
        Runnable wrapped;

        // The OrderedMemoryAwareThreadPoolExecutor maintains strict sequence
        // ordering only for Runnables that are subclasses of
        // ChannelEventRunnable.  All other Runnable objects are scheduled
        // without constraints, which can break IdleStateHandler.
        if (command instanceof ChannelEventRunnable) {
            wrapped = buildWrap((ChannelEventRunnable)command);
        } else {
            wrapped = buildWrap(command);
        }

        _inner.execute(wrapped);
    }

    private Runnable buildWrap(final ChannelEventRunnable command)
    {
        return new ChannelEventRunnable(command.getContext(),
                command.getEvent(), this) {
            private final CDC _cdc = new CDC();

            @Override
            protected void doRun()
            {
                try (CDC ignored = _cdc.restore()) {
                    command.run();
                }
            }
        };
    }

    private Runnable buildWrap(final Runnable command)
    {
        return new Runnable() {
            private final CDC _cdc = new CDC();

            @Override
            public void run()
            {
                try (CDC ignored = _cdc.restore()) {
                    command.run();
                }
            }
        };
    }
}
