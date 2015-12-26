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

import com.google.common.collect.Range;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Required;

import java.util.EnumSet;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import diskCacheV111.util.CacheException;
import diskCacheV111.util.FsPath;

import org.dcache.namespace.ACLPermissionHandler;
import org.dcache.namespace.ChainedPermissionHandler;
import org.dcache.namespace.FileAttribute;
import org.dcache.namespace.PermissionHandler;
import org.dcache.namespace.PosixPermissionHandler;
import org.dcache.util.Transfer;
import org.dcache.util.list.DirectoryEntry;
import org.dcache.util.list.DirectoryStream;
import org.dcache.util.list.ListDirectoryHandler;
import org.dcache.vehicles.FileAttributes;

import static org.dcache.acl.enums.AccessType.ACCESS_ALLOWED;
import static org.dcache.namespace.FileAttribute.SIZE;
import static org.dcache.namespace.FileAttribute.TYPE;

/**
 * Class to handle queries to the namespace when discovering all files
 * that are to become part of a zip file.
 */
public class DirectoryListEngine
{
    private static final Logger LOG = LoggerFactory.getLogger(DirectoryListEngine.class);
    private static final EnumSet<FileAttribute> LOCALLY_REQUIRED_ATTRIBUTES =
            EnumSet.of(TYPE, SIZE);

    private final PermissionHandler _pdp = new ChainedPermissionHandler(
            new ACLPermissionHandler(),
            new PosixPermissionHandler());
    private final Set<FileAttribute> _requiredAttributes;

    private ListDirectoryHandler _handler;

    public DirectoryListEngine()
    {
        _requiredAttributes = EnumSet.copyOf(LOCALLY_REQUIRED_ATTRIBUTES);
        _requiredAttributes.addAll(_pdp.getRequiredAttributes());
        _requiredAttributes.addAll(Transfer.REQUIRED_ATTRIBUTES);
        _requiredAttributes.addAll(ZipRequestHandler.REQUIRED_ATTRIBUTES);
    }

    @Required
    public void setListHandler(ListDirectoryHandler handler)
    {
        _handler = handler;
    }


    public void startListing(ZipRequest request) throws InterruptedException, CacheException
    {
        DirectoryStream stream = list(request, request.getPath());
        request.submitDirectoryScan(() -> {
                    handleDirectoryStream(request, request.getPath(),
                            stream.iterator());
                });
    }

    private DirectoryStream list(ZipRequest request, FsPath path)
            throws InterruptedException, CacheException
    {
        LOG.trace("start listing activity: {}", path);
        return _handler.list(request.getSubject(), path, null, Range.all(),
                _requiredAttributes);
    }


    private void handleDirectoryStream(ZipRequest request, FsPath directoryPath,
            Iterator<DirectoryEntry> entries)
    {
        LOG.trace("listing directory: {}", directoryPath);
        while (entries.hasNext()) {
            DirectoryEntry entry = entries.next();
            FileAttributes attributes = entry.getFileAttributes();
            switch (attributes.getFileType()) {
            case REGULAR:
                if (_pdp.canReadFile(request.getSubject(), attributes) == ACCESS_ALLOWED) {
                    FsPath path = new FsPath(directoryPath, entry.getName());
                    if (!request.offerFile(path, attributes)) {
                        LOG.debug("file addition blocked: {}", directoryPath);
                        request.schedule(() -> {
                                    attemptToAddFile(request, directoryPath,
                                            entries, path, attributes);
                                }, 10, TimeUnit.SECONDS);
                        return;
                    }
                }
                break;

            case DIR:
                FsPath path = new FsPath(directoryPath, entry.getName());
                request.addDirectory(path);
                break;

            case LINK:
                // REVISIT: how to handle sym-links.
                break;

            case SPECIAL:
                break;
            }
        }

        FsPath next = null;
        try {
            while ((next = request.takeDirectory()) != null && next != ZipRequest.DIRECTORY_SENTINEL) {
                try {
                    DirectoryStream stream = list(request, next);
                    FsPath subdir = next;
                    request.submitDirectoryScan(() -> {
                        handleDirectoryStream(request, subdir, stream.iterator());
                    });
                    break;
                } catch (CacheException e) {
                    request.addProblem("failed to read directory " + next + ": " + e.getMessage());
                }
            }
        } catch (InterruptedException e) {
            request.cancel();
        }

        request.directoryListingFinished();
    }


    private void attemptToAddFile(ZipRequest request, FsPath directoryPath,
            Iterator<DirectoryEntry> entries, FsPath file, FileAttributes attributes)
    {
        LOG.trace("attempting to add file: {}", file);

        if (request.offerFile(file, attributes)) {
            handleDirectoryStream(request, directoryPath, entries);
        } else {
            request.schedule(() -> {
                attemptToAddFile(request, directoryPath, entries, file, attributes);
            }, 10, TimeUnit.SECONDS);
        }
    }
}
