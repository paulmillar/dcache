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

import java.io.IOException;
import java.nio.file.OpenOption;
import java.util.Set;

import org.dcache.pool.repository.ForwardingReplicaRecord;
import org.dcache.pool.repository.ReplicaRecord;
import org.dcache.pool.repository.RepositoryChannel;

import static java.util.Objects.requireNonNull;

/**
 * A ReplicaRecord that wraps some other ReplicaRecord and wraps all channels
 * to monitor thread IO usage.
 */
public class LoadMonitoringReplicaRecord extends ForwardingReplicaRecord
{
    private final ReplicaRecord inner;
    private final ThreadIoMonitor monitor;

    public LoadMonitoringReplicaRecord(ReplicaRecord inner, ThreadIoMonitor monitor)
    {
        this.inner = requireNonNull(inner);
        this.monitor = requireNonNull(monitor);
    }

    @Override
    protected ReplicaRecord delegate()
    {
        return inner;
    }

    @Override
    public RepositoryChannel openChannel(Set<? extends OpenOption> mode)
            throws IOException
    {
        RepositoryChannel channel = super.openChannel(mode);
        return new LoadMonitoringRepositoryChannel(channel, monitor);
    }
}
