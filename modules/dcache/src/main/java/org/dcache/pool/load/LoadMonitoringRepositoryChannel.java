/*
 * dCache - http://www.dcache.org/
 *
 * Copyright (C) 2019 Deutsches Elektronen-Synchrotron
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.dcache.pool.load;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.SyncFailedException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.channels.WritableByteChannel;

import org.dcache.pool.repository.ForwardingRepositoryChannel;
import org.dcache.pool.repository.RepositoryChannel;

import static java.util.Objects.requireNonNull;

/**
 * A RepositoryChannel that wraps some other RepositoryChannel and reports IO
 * activty to some ThreadIoMonitor.
 */
public class LoadMonitoringRepositoryChannel extends ForwardingRepositoryChannel
{
    private static final Logger LOGGER = LoggerFactory.getLogger(LoadMonitoringRepositoryChannel.class);

    private final RepositoryChannel inner;
    private final ThreadIoMonitor monitor;

    public LoadMonitoringRepositoryChannel(RepositoryChannel inner,
            ThreadIoMonitor monitor)
    {
        this.inner = requireNonNull(inner);
        this.monitor = requireNonNull(monitor);
    }

    @Override
    protected RepositoryChannel delegate()
    {
        return inner;
    }

    private void threadIoStarted()
    {
        try {
            monitor.threadIoStarted();
        } catch (RuntimeException e) {
            LOGGER.error("Bug detected, please report this to <support@dcache.org>", e);
        }
    }

    private void threadIoFinished()
    {
        try {
            monitor.threadIoFinished();
        } catch (RuntimeException e) {
            LOGGER.error("Bug detected, please report this to <support@dcache.org>", e);
        }
    }

    @Override
    public int write(ByteBuffer buffer, long position) throws IOException
    {
        threadIoStarted();
        try {
            return super.write(buffer, position);
        } finally {
            threadIoFinished();
        }
    }

    @Override
    public int read(ByteBuffer buffer, long position) throws IOException
    {
        threadIoStarted();
        try {
            return super.read(buffer, position);
        } finally {
            threadIoFinished();
        }
    }

    @Override
    public void sync() throws SyncFailedException, IOException
    {
        threadIoStarted();
        try {
            delegate().sync();
        } finally {
            threadIoFinished();
        }
    }

    @Override
    public long transferTo(long position, long count, WritableByteChannel target)
            throws IOException
    {
        threadIoStarted();
        try {
            return super.transferTo(position, count, target);
        } finally {
            threadIoFinished();
        }
    }

    @Override
    public long transferFrom(ReadableByteChannel src, long position, long count)
            throws IOException
    {
        threadIoStarted();
        try {
            return super.transferFrom(src, position, count);
        } finally {
            threadIoFinished();
        }
    }

    @Override
    public int read(ByteBuffer dst) throws IOException
    {
        threadIoStarted();
        try {
            return super.read(dst);
        } finally {
            threadIoFinished();
        }
    }

    @Override
    public int write(ByteBuffer src) throws IOException
    {
        threadIoStarted();
        try {
            return super.write(src);
        } finally {
            threadIoFinished();
        }
    }

    @Override
    public long position() throws IOException
    {
        threadIoStarted();
        try {
            return super.position();
        } finally {
            threadIoFinished();
        }
    }

    @Override
    public SeekableByteChannel position(long newPosition) throws IOException
    {
        threadIoStarted();
        try {
            return super.position(newPosition);
        } finally {
            threadIoFinished();
        }
    }

    @Override
    public long size() throws IOException
    {
        threadIoStarted();
        try {
            return super.size();
        } finally {
            threadIoFinished();
        }
    }

    @Override
    public SeekableByteChannel truncate(long size) throws IOException
    {
        threadIoStarted();
        try {
            return super.truncate(size);
        } finally {
            threadIoFinished();
        }
    }

    @Override
    public long write(ByteBuffer[] srcs, int offset, int length)
            throws IOException
    {
        threadIoStarted();
        try {
            return super.write(srcs, offset, length);
        } finally {
            threadIoFinished();
        }
    }

    @Override
    public long write(ByteBuffer[] srcs) throws IOException
    {
        threadIoStarted();
        try {
            return super.write(srcs);
        } finally {
            threadIoFinished();
        }
    }

    @Override
    public long read(ByteBuffer[] dsts, int offset, int length)
            throws IOException
    {
        threadIoStarted();
        try {
            return super.read(dsts, offset, length);
        } finally {
            threadIoFinished();
        }
    }

    @Override
    public long read(ByteBuffer[] dsts) throws IOException
    {
        threadIoStarted();
        try {
            return super.read(dsts);
        } finally {
            threadIoFinished();
        }
    }

    @Override
    public boolean isOpen()
    {
        threadIoStarted();
        try {
            return super.isOpen();
        } finally {
            threadIoFinished();
        }
    }

    @Override
    public void close() throws IOException
    {
        threadIoStarted();
        try {
            super.close();
        } finally {
            threadIoFinished();
        }
    }
}
