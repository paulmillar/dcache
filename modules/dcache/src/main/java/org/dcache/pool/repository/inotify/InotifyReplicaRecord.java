/* dCache - http://www.dcache.org/
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
package org.dcache.pool.repository.inotify;

import org.apache.curator.shaded.com.google.common.base.Splitter;
import org.springframework.web.util.UriUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.OpenOption;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import diskCacheV111.namespace.EventReceiver;
import diskCacheV111.util.CacheException;
import diskCacheV111.util.PnfsId;

import org.dcache.namespace.FileAttribute;
import org.dcache.pool.repository.FileAttributesAware;
import org.dcache.pool.repository.ForwardingReplicaRecord;
import org.dcache.pool.repository.ReplicaRecord;
import org.dcache.pool.repository.ReplicaState;
import org.dcache.pool.repository.RepositoryChannel;
import org.dcache.pool.repository.inotify.InotifyChannel.Link;
import org.dcache.vehicles.FileAttributes;

/**
 * Wrap some existing ReplicaRecord and add support for optionally wrapping
 * a RepositoryChannel with an InotifyChannel.
 */
public class InotifyReplicaRecord extends ForwardingReplicaRecord
        implements FileAttributesAware
{
    private final ReplicaRecord inner;
    private final EventReceiver receiver;

    private Duration suppressDuration = Duration.ZERO;
    private Collection<Link> links = Collections.emptyList();

    public enum OpenFlags implements OpenOption
    {
        /**
         * Specifying this flag results in the ReplicaRecord being wrapped by
         * a InotifyChannel, which monitors client activity and generates
         * inotify events if a client is monitoring that file or its parent
         * directory.
         */
        ENABLE_INOTIFY_MONITORING;
    }


    public InotifyReplicaRecord(ReplicaRecord inner, EventReceiver receiver,
            PnfsId target)
    {
        this.inner = inner;
        this.receiver = receiver;
    }

    /**
     * Update the suppression period for all subsequently opened
     * RepositoryChannel. Any already opened channels are not affected.
     */
    public void setSuppressDuration(Duration duration)
    {
        suppressDuration = duration;
    }

    private synchronized void updateLinks(String serialisedLinks)
    {
        List<Link> updatedLinks = new ArrayList<>();
        for (String serialisedLink : Splitter.on('#').split(serialisedLinks)) {
            List<String> items = Splitter.on(' ').limit(2).splitToList(serialisedLink);
            PnfsId parent = new PnfsId(items.get(0));
            String name = UriUtils.decode(items.get(1), StandardCharsets.UTF_8);
            updatedLinks.add(new Link(parent, name));
        }
        this.links = updatedLinks;
    }

    @Override
    protected ReplicaRecord delegate()
    {
        return inner;
    }

    @Override
    public void accept(FileAttributes attributes)
    {
        if (attributes.isDefined(FileAttribute.STORAGEINFO)) {
            String serialisedLinks = attributes.getStorageInfo().getMap().get("links");

            if (serialisedLinks != null) {
                updateLinks(serialisedLinks);
            }
        }
    }

    @Override
    public RepositoryChannel openChannel(Set<? extends OpenOption> mode)
            throws IOException
    {
        boolean inotifyRequested = mode.contains(OpenFlags.ENABLE_INOTIFY_MONITORING);

        if (!inotifyRequested) {
            return super.openChannel(mode);
        }

        Collection<Link> currentLinks;
        synchronized (this) {
            currentLinks = this.links;
        }

        Set<? extends OpenOption> innerMode = new HashSet<>(mode);
        innerMode.remove(OpenFlags.ENABLE_INOTIFY_MONITORING);
        RepositoryChannel innerChannel = super.openChannel(innerMode);

        if (currentLinks.isEmpty()) {
            return innerChannel;
        }

        boolean openForWrite = mode.contains(StandardOpenOption.WRITE);

        InotifyChannel channel = new InotifyChannel(innerChannel, receiver,
                getPnfsId(), currentLinks, openForWrite);
        channel.setSuppressDuration(suppressDuration);
        channel.sendOpenEvent();
        return channel;
    }

    @Override
    public <T> T update(Update<T> update) throws CacheException
    {
        return super.update(r -> update.apply(
                new UpdatableRecord()
                {
                    @Override
                    public boolean setSticky(String owner, long validTill,
                                             boolean overwrite) throws CacheException
                    {
                        return r.setSticky(owner, validTill, overwrite);
                    }

                    @Override
                    public Void setState(ReplicaState state) throws CacheException
                    {
                        return r.setState(state);
                    }

                    @Override
                    public Void setFileAttributes(FileAttributes attributes)
                            throws CacheException
                    {
                        FileAttributes forInner;

                        // Avoid storing the 'links' value
                        if (attributes.isDefined(FileAttribute.STORAGEINFO)) {
                            forInner = attributes.clone();
                            forInner.getStorageInfo().setKey("links", null);
                        } else {
                            forInner = attributes;
                        }

                        return r.setFileAttributes(forInner);
                    }

                    @Override
                    public FileAttributes getFileAttributes() throws CacheException
                    {
                        return r.getFileAttributes();
                    }

                    @Override
                    public ReplicaState getState()
                    {
                        return r.getState();
                    }

                    @Override
                    public int getLinkCount()
                    {
                        return r.getLinkCount();
                    }
                }));
    }
}
