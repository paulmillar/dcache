package diskCacheV111.namespace;

import com.google.common.collect.Range;

import javax.security.auth.Subject;

import java.util.List;
import java.util.Set;

import diskCacheV111.util.AccessLatency;
import diskCacheV111.util.CacheException;
import diskCacheV111.util.FsPath;
import diskCacheV111.util.PnfsId;
import diskCacheV111.util.RetentionPolicy;

import org.dcache.auth.attributes.Restriction;
import org.dcache.namespace.CreateOption;
import org.dcache.namespace.FileAttribute;
import org.dcache.namespace.FileType;
import org.dcache.namespace.ListHandler;
import org.dcache.util.ChecksumType;
import org.dcache.util.Glob;
import org.dcache.vehicles.FileAttributes;

public class AbstractNameSpaceProvider
    implements NameSpaceProvider
{
    @Override
    public FileAttributes createFile(Subject subject, Restriction restriction, String path, int uid, int gid, int mode,
                                     Set<FileAttribute> requestedAttributes)
        throws CacheException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public PnfsId createDirectory(Subject subject, Restriction restriction, String path, int uid, int gid, int mode)
            throws CacheException {
        throw new UnsupportedOperationException();
    }

    @Override
    public PnfsId createSymLink(Subject subject, Restriction restriction, String path, String dest, int uid, int gid)
            throws CacheException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void deleteEntry(Subject subject, Restriction restriction, Set<FileType> allowed, PnfsId pnfsId)
        throws CacheException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public PnfsId deleteEntry(Subject subject, Restriction restriction, Set<FileType> allowed, String path)
        throws CacheException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void deleteEntry(Subject subject, Restriction restriction, Set<FileType> allowed, PnfsId pnfsId, String path) throws CacheException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void rename(Subject subject, Restriction restriction, PnfsId pnfsId, String source, String destination, boolean overwrite)
        throws CacheException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public String pnfsidToPath(Subject subject, Restriction restriction, PnfsId pnfsId)
        throws CacheException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public PnfsId pathToPnfsid(Subject subject, Restriction restriction, String path, boolean followLinks)
        throws CacheException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public PnfsId getParentOf(Subject subject, Restriction restriction, PnfsId pnfsId)
        throws CacheException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void removeFileAttribute(Subject subject, Restriction restriction, PnfsId pnfsId, String attribute)
        throws CacheException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void removeChecksum(Subject subject, Restriction restriction, PnfsId pnfsId, ChecksumType type)
        throws CacheException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void addCacheLocation(Subject subject, Restriction restriction, PnfsId pnfsId, String cacheLocation)
        throws CacheException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<String> getCacheLocation(Subject subject, Restriction restriction, PnfsId pnfsId)
        throws CacheException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void clearCacheLocation(Subject subject, Restriction restriction, PnfsId pnfsId, String cacheLocation, boolean removeIfLast)
        throws CacheException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public FileAttributes getFileAttributes(Subject subject, Restriction restriction, PnfsId pnfsId,
                                            Set<FileAttribute> attr)
        throws CacheException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public FileAttributes setFileAttributes(Subject subject, Restriction restriction, PnfsId pnfsId,
            FileAttributes attr, Set<FileAttribute> acquire) throws CacheException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void list(Subject subject, Restriction restriction, String path, Glob glob, Range<Integer> range,
                     Set<FileAttribute> attrs, ListHandler handler)
        throws CacheException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public FsPath createUploadPath(Subject subject, Restriction restriction, FsPath path, FsPath rootPath,
                                   Long size, AccessLatency al, RetentionPolicy rp, String spaceToken,
                                   Set<CreateOption> options) throws CacheException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public PnfsId commitUpload(Subject subject, Restriction restriction, FsPath uploadPath, FsPath pnfsPath, Set<CreateOption> options)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void cancelUpload(Subject subject, Restriction restriction, FsPath uploadPath, FsPath path) throws CacheException
    {
        throw new UnsupportedOperationException();
    }
}
