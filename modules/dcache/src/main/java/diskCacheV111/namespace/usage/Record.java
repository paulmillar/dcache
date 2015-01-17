package diskCacheV111.namespace.usage;

import com.google.common.collect.ImmutableMap;

import java.io.Serializable;
import java.util.Map;

/**
 * Some amount of storage capacity usage caused by one or more files.
 * Logical usage is the amount found by the sum of all file sizes.  Physical
 * usage is the amount of some underlying capacity that is used to store these
 * files.
 */
public class Record implements Serializable
{
    private static final long serialVersionUID = 1L;

    private final long _logicalUsage;
    private final long _fileCount;
    private final ImmutableMap<String,Long> _physicalUsage;

    public Record(long logicalUsage, long fileCount, Map<String,Long> physicalUsage)
    {
        _logicalUsage = logicalUsage;
        _fileCount = fileCount;
        _physicalUsage = ImmutableMap.copyOf(physicalUsage);
    }

    public long getLogicalUsage()
    {
        return _logicalUsage;
    }

    public long getFileCount()
    {
        return _fileCount;
    }

    public ImmutableMap<String,Long> getPhysicalUsage()
    {
        return _physicalUsage;
    }
}
