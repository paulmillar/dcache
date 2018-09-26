/* dCache - http://www.dcache.org/
 *
 * Copyright (C) 2018 Deutsches Elektronen-Synchrotron
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
package diskCacheV111.vehicles;


import java.net.InetSocketAddress;
import java.util.Collection;

import diskCacheV111.util.PnfsId;

/**
 * A progress report of a transfer a file with some remote storage.
 */
public class RemoteTransferJobInfo extends IoJobInfo
{
    private final Collection<InetSocketAddress> _currentConnections;

    public RemoteTransferJobInfo(long submitTime, long startTime, String state,
            int id, String clientName, long clientId, PnfsId pnfsId,
            long bytesTransferred, long transferTime, long lastTransferred,
            Collection<InetSocketAddress> connections)
    {
        super(submitTime, startTime, state, id, clientName, clientId, pnfsId,
                bytesTransferred, transferTime, lastTransferred);
        _currentConnections = connections;
    }


    public Collection<InetSocketAddress> getCurrentConnections()
    {
        return _currentConnections;
    }
}
