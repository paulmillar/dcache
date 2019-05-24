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

import java.nio.file.OpenOption;
import java.util.Set;

import diskCacheV111.util.CacheException;
import diskCacheV111.util.PnfsId;

import org.dcache.pool.repository.DuplicateEntryException;
import org.dcache.pool.repository.ForwardingReplicaStore;
import org.dcache.pool.repository.ReplicaRecord;
import org.dcache.pool.repository.ReplicaStore;

/**
 * A ReplicaStore that wraps some other ReplicaStore and instruments all
 * ReplicaRecords to monitor thread IO usage.
 */
public class LoadMonitoringReplicaStore extends ForwardingReplicaStore
{
    private final ReplicaStore inner;
    private final ThreadIoMonitor monitor;

    public LoadMonitoringReplicaStore(ReplicaStore inner, ThreadIoMonitor monitor)
    {
        this.inner = inner;
        this.monitor = monitor;
    }

    @Override
    protected ReplicaStore delegate()
    {
        return inner;
    }

    @Override
    public ReplicaRecord get(PnfsId id) throws CacheException
    {
        ReplicaRecord inner = super.get(id);
        return inner == null ? null : new LoadMonitoringReplicaRecord(inner, monitor);
    }

    @Override
    public ReplicaRecord create(PnfsId id, Set<? extends OpenOption> flags)
            throws DuplicateEntryException, CacheException
    {
        ReplicaRecord inner = super.create(id, flags);
        return new LoadMonitoringReplicaRecord(inner, monitor);
    }
}
