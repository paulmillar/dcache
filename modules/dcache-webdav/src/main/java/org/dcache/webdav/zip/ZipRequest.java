/* dCache - http://www.dcache.org/
 *
 * Copyright (C) 2015 Deutsches Elektronen-Synchrotron
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
package org.dcache.webdav.zip;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.security.auth.Subject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import diskCacheV111.util.FsPath;

import dmg.cells.nucleus.CDC;

import org.dcache.util.FireAndForgetTask;
import org.dcache.util.RedirectedTransfer;
import org.dcache.vehicles.FileAttributes;

/**
 * Encapsulates a request to create a zip file.
 */
public class ZipRequest
{
    private static final Logger LOG = LoggerFactory.getLogger(ZipRequestFactory.class);
    public static final FsPath DIRECTORY_SENTINEL = new FsPath();
    public static final FileEntry FILE_SENTINEL = new FileEntry();

    private static final int MAXIMUM_PENDING_FILES = 1_000;

    private final Subject _subject;
    private final boolean _isRecursive;
    private final FsPath _path;
    private final BlockingDeque<FileEntry> _files = new LinkedBlockingDeque<>(MAXIMUM_PENDING_FILES);
    private final BlockingDeque<FsPath> _directories = new LinkedBlockingDeque<>();
    private final BlockingDeque<RedirectedTransfer<String>> _transfers = new LinkedBlockingDeque<>();
    private final List<String> _problems = new ArrayList<>();

    private final AtomicInteger _pendingDirectory = new AtomicInteger();
    private final ScheduledExecutorService _executor;

    private List<Future> _futures = Collections.emptyList();
    private boolean _isCancelled;
    private boolean _finalTransferEnqueued;

    public static class FileEntry
    {
        private final FileAttributes _attributes;
        private final FsPath _path;

        public FileEntry()
        {
            _path = null;
            _attributes = null;
        }

        public FileEntry(FsPath path, FileAttributes attributes)
        {
            _path = path;
            _attributes = attributes;
        }

        public FsPath getPath()
        {
            return _path;
        }

        public FileAttributes getAttributes()
        {
            return _attributes;
        }
    }

    interface TransferStartScheduler
    {
        void scheduleNextStartTransfer(Runnable task);
    }

    ZipRequest(Subject subject, boolean isRecursive, FsPath path, ScheduledExecutorService executor)
    {
        _subject = subject;
        _isRecursive = isRecursive;
        _path = path;
        _executor = executor;
    }

    public boolean isRecursive()
    {
        return _isRecursive;
    }

    public FsPath getPath()
    {
        return _path;
    }

    public Subject getSubject()
    {
        return _subject;
    }

    public boolean offerFile(FsPath file, FileAttributes attributes)
    {
        return _files.offerLast(new FileEntry(file, attributes));
    }

    public void addDirectory(FsPath file)
    {
        if (_isRecursive) {
            _directories.add(file);
        }
    }

    public void addProblem(String description)
    {
        System.out.println("addProblem: " + description);
        _problems.add(description);
    }

    public void closeDirectoryListing()
    {
        if (!_directories.offerFirst(DIRECTORY_SENTINEL)) {
            LOG.debug("Capacity limits inserting directory sentinel");
        }
    }

    public void closeFileListing()
    {
        if (!_files.offerFirst(FILE_SENTINEL)) {
            LOG.debug("Capacity limits inserting file sentinel");
        }
    }

    public FileEntry pollFile()
    {
        return _files.pollFirst();
    }

    public FileEntry pollFile(long duration, TimeUnit units)
            throws InterruptedException
    {
        return _files.poll(duration, units);
    }

    public FsPath takeDirectory() throws InterruptedException
    {
        return _directories.pollFirst();
    }

    public void submitDirectoryScan(Runnable task)
    {
        _pendingDirectory.incrementAndGet();
        submit(task);
    }

    public Future submit(Runnable innerTask)
    {
        Runnable task = new FireAndForgetTask(innerTask);
        CDC cdc = new CDC();

        Future future = _executor.submit(() -> {
            try (CDC ignored = cdc.restore()) {
                task.run();
            }
        });

        addFuture(future);

        return future;
    }

    public Future schedule(Runnable innerTask, long delay, TimeUnit unit)
    {
        Runnable task = new FireAndForgetTask(innerTask);
        CDC cdc = new CDC();

        Future future = _executor.schedule(() -> {
            try (CDC ignored = cdc.restore()) {
                task.run();
            }
        }, delay, unit);

        addFuture(future);

        return future;
    }

    private synchronized void addFuture(Future future)
    {
        if (_isCancelled) {
            LOG.error("Activity scheduled after ZIP file creation cancelled.");
            future.cancel(true);
        } else {
            _futures = Stream.concat(_futures.stream(), Stream.of(future))
                    .filter(f -> {return !f.isDone();})
                    .collect(Collectors.toList());
        }
    }

    public void directoryListingFinished()
    {
        if (_pendingDirectory.decrementAndGet() == 0) {
            _files.add(FILE_SENTINEL);
        }
    }

    public synchronized void cancel()
    {
        if (!_isCancelled) {
            _files.offerFirst(FILE_SENTINEL);
            _directories.offerFirst(DIRECTORY_SENTINEL);
            _futures.forEach(f -> f.cancel(true));
            _futures.clear();
            _isCancelled = true;
        }
    }

    public boolean offerTransfer(RedirectedTransfer<String> transfer)
    {
        synchronized (_transfers) {
            if (_finalTransferEnqueued) {
                LOG.error("enqueued transfer after final notification");
                return true;
            }

            boolean successful = _transfers.offerLast(transfer);
            if (successful) {
                _transfers.notifyAll();
            }
            return successful;
        }
    }

    public RedirectedTransfer<String> takeTransfer() throws InterruptedException
    {
        RedirectedTransfer<String> transfer;

        synchronized (_transfers) {
            transfer = _transfers.pollFirst();

            if (transfer == null && !_finalTransferEnqueued) {
                _transfers.wait();
                transfer = _transfers.pollFirst();
            }
        }

        return transfer;
    }

    public void noFurtherTransfers()
    {
        synchronized(_transfers) {
            _finalTransferEnqueued = true;
            _transfers.notifyAll();
        }
    }

    public void scheduleNextStartTransfer(Runnable task)
    {
        // FIXME refactor this into separate class.
        schedule(task, 100, TimeUnit.MILLISECONDS);
    }
}
