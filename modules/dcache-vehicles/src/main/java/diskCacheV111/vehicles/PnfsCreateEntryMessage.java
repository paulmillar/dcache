package diskCacheV111.vehicles;

import com.google.common.collect.Sets;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

import org.dcache.namespace.FileAttribute;
import org.dcache.vehicles.FileAttributes;
import org.dcache.vehicles.PnfsGetFileAttributes;

import static java.util.Objects.requireNonNull;
import static org.dcache.namespace.FileAttribute.*;

public class PnfsCreateEntryMessage extends PnfsGetFileAttributes
{
    private final String _path;
    private final FileAttributes _assignAttributes;

    private static final long serialVersionUID = -8197311585737333341L;

    public PnfsCreateEntryMessage(String path) {
        this(path, new FileAttributes());
    }

    public PnfsCreateEntryMessage(String path, FileAttributes assignAttributes) {
        this(path, assignAttributes, Collections.emptySet());
    }

    public PnfsCreateEntryMessage(String path, FileAttributes assignAttributes,
            Set<FileAttribute> queryAttributes) {
        super(path, EnumSet.copyOf(Sets.union(queryAttributes,
                EnumSet.of(OWNER, OWNER_GROUP, MODE, TYPE, SIZE,
                        CREATION_TIME, ACCESS_TIME, MODIFICATION_TIME,
                        PNFSID, STORAGEINFO, STORAGECLASS, CACHECLASS, HSM,
                        ACCESS_LATENCY, RETENTION_POLICY))));
        _path = path;
        _assignAttributes = requireNonNull(assignAttributes);
    }


    public String getPath(){
	return _path;
    }

    /**
     * Attributes for the newly created entry.
     */
    public FileAttributes getAssignAttributes()
    {
        return _assignAttributes;
    }

    @Override
    public boolean invalidates(Message message)
    {
        return genericInvalidatesForPnfsMessage(message);
    }

    @Override
    public boolean fold(Message message)
    {
        return false;
    }
}
