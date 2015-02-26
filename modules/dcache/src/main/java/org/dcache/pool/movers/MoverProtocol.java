package org.dcache.pool.movers;

import diskCacheV111.vehicles.ProtocolInfo;

import org.dcache.pool.repository.Allocator;
import org.dcache.pool.repository.RepositoryChannel;
import org.dcache.pool.repository.TransferMonitor;
import org.dcache.vehicles.FileAttributes;

public interface MoverProtocol
{

    /**
     * @param allocator Space allocator. May be null for a read-only
     * transfer.
     */
    public void runIO(FileAttributes fileAttributes,
                      RepositoryChannel diskFile,
                      ProtocolInfo protocol,
                      Allocator    allocator,
                      IoMode         access,
                      TransferMonitor monitor)
        throws Exception;

    /**
     * Get number of bytes transfered. The number of bytes may exceed
     * total file size if client does some seek requests in between.
     *
     * @return number of bytes
     */
    public long getBytesTransferred();

    /**
     * Get time of last transfer.
     *
     * @return last access time in milliseconds.
     */
    public long getLastTransferred();
}
