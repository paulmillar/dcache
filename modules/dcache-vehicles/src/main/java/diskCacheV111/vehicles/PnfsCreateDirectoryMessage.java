package diskCacheV111.vehicles;

import java.util.Set;

import org.dcache.namespace.FileAttribute;
import org.dcache.vehicles.FileAttributes;

public class PnfsCreateDirectoryMessage extends PnfsCreateEntryMessage
{
    private static final long serialVersionUID = 2081981117629353921L;

    public PnfsCreateDirectoryMessage(String path)
    {
        super(path);
    }

    public PnfsCreateDirectoryMessage(String path, FileAttributes attributes)
    {
        super(path, attributes);
    }

    public PnfsCreateDirectoryMessage(String path, FileAttributes assignAttributes,
            Set<FileAttribute> queryAttributes)
    {
        super(path, assignAttributes, queryAttributes);
    }
}
