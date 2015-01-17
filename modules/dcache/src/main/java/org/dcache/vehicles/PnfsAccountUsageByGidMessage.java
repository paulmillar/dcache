package org.dcache.vehicles;

import java.util.Map;

import diskCacheV111.namespace.usage.Usage;
import diskCacheV111.vehicles.Message;

/**
 * This message allows querying of usage information by GID.
 */
public class PnfsAccountUsageByGidMessage extends Message
{
    private Map<Long,Usage> _usage;

    public void setUsage(Map<Long,Usage> usage)
    {
        _usage = usage;
    }

    public Map<Long,Usage> getUsage()
    {
        return _usage;
    }
}
