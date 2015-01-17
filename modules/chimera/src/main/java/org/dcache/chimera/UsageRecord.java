package org.dcache.chimera;

import java.util.HashMap;
import java.util.Map;

/**
 * Records a specific kind usage (e.g., ONLINE) for some group of files
 * (e.g., files with owned gid 0).
 */
public class UsageRecord
{
    private final Map<String,Long> _physicalUsed = new HashMap<>();
    private long _logicalUsed;
    private long _fileCount;

    public long getLogicalUsed()
    {
        return _logicalUsed;
    }

    public void setLogicalUsed(long usage)
    {
        _logicalUsed = usage;
    }

    public Map<String,Long> getPhysicalUsed()
    {
        return _physicalUsed;
    }

    public void incrementPhysicalUsed(String type, long usage)
    {
        long current = _physicalUsed.computeIfAbsent(type, k -> 0L);
        _physicalUsed.put(type, current + usage);
    }

    /**
     * The number of online files.
     */
    public long getFileCount()
    {
        return _fileCount;
    }

    /**
     * The number of online files.
     */
    public void setFileCount(long count)
    {
        _fileCount = count;
    }
}
