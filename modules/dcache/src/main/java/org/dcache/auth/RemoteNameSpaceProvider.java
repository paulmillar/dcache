package org.dcache.auth;

import com.google.common.collect.Range;

import javax.security.auth.Subject;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import diskCacheV111.namespace.NameSpaceProvider;
import diskCacheV111.util.AccessLatency;
import diskCacheV111.util.CacheException;
import diskCacheV111.util.FsPath;
import diskCacheV111.util.PnfsHandler;
import diskCacheV111.util.PnfsId;
import diskCacheV111.util.RetentionPolicy;
import diskCacheV111.util.TimeoutCacheException;
import diskCacheV111.vehicles.PnfsCancelUpload;
import diskCacheV111.vehicles.PnfsClearCacheLocationMessage;
import diskCacheV111.vehicles.PnfsCommitUpload;
import diskCacheV111.vehicles.PnfsCreateEntryMessage;
import diskCacheV111.vehicles.PnfsCreateUploadPath;
import diskCacheV111.vehicles.PnfsFlagMessage;

import org.dcache.auth.attributes.Restriction;
import org.dcache.auth.attributes.Restrictions;
import org.dcache.namespace.CreateOption;
import org.dcache.namespace.FileAttribute;
import org.dcache.namespace.FileType;
import org.dcache.namespace.ListHandler;
import org.dcache.util.ChecksumType;
import org.dcache.util.Glob;
import org.dcache.util.list.DirectoryEntry;
import org.dcache.util.list.DirectoryStream;
import org.dcache.util.list.ListDirectoryHandler;
import org.dcache.vehicles.FileAttributes;

import static com.google.common.base.Preconditions.checkNotNull;
import static diskCacheV111.vehicles.PnfsFlagMessage.FlagOperation.REMOVE;

/**
 * The RemoteNameSpaceProvider uses the PnfsManager client stub to provide
 * an implementation of the NameSpaceProvider interface.  This implementation
 * is thread-safe.
 */
public class RemoteNameSpaceProvider implements NameSpaceProvider
{
    private final PnfsHandler _pnfs;
    private final ListDirectoryHandler _handler;


    public RemoteNameSpaceProvider(PnfsHandler pnfsHandler,
            ListDirectoryHandler listHandler)
    {
        _pnfs = pnfsHandler;
        _handler = listHandler;
    }

    public RemoteNameSpaceProvider(PnfsHandler pnfsHandler)
    {
        this(pnfsHandler, new ListDirectoryHandler(pnfsHandler));
    }

    @Override
    public FileAttributes createFile(Subject subject, Restriction restriction, String path, int uid, int gid, int mode,
                                     Set<FileAttribute> requestedAttributes)
            throws CacheException
    {
        PnfsHandler pnfs = new PnfsHandler(_pnfs, subject, restriction);
        PnfsCreateEntryMessage returnMsg =
                pnfs.request(new PnfsCreateEntryMessage(path, uid, gid, mode, requestedAttributes));
        return returnMsg.getFileAttributes();
    }

    @Override
    public PnfsId createDirectory(Subject subject, Restriction restriction, String path, int uid, int gid, int mode)
            throws CacheException {
        PnfsHandler pnfs = new PnfsHandler(_pnfs, subject, restriction);

        PnfsCreateEntryMessage returnMsg = pnfs.createPnfsDirectory(path, uid, gid, mode);

        return returnMsg.getPnfsId();
    }

    @Override
    public PnfsId createSymLink(Subject subject, Restriction restriction, String path, String dest,
            int uid, int gid) throws CacheException
    {
        PnfsHandler pnfs = new PnfsHandler(_pnfs, subject, restriction);

        PnfsCreateEntryMessage returnMsg = pnfs.createSymLink(path, dest, uid, gid);

        return returnMsg.getPnfsId();
    }

    @Override
    public void deleteEntry(Subject subject, Restriction restriction, Set<FileType> allowed, PnfsId id) throws CacheException
    {
        PnfsHandler pnfs = new PnfsHandler(_pnfs, subject, restriction);
        pnfs.deletePnfsEntry(id, null, allowed);
    }

    @Override
    public PnfsId deleteEntry(Subject subject, Restriction restriction, Set<FileType> allowed, String path) throws CacheException
    {
        PnfsHandler pnfs = new PnfsHandler(_pnfs, subject, restriction);
        return pnfs.deletePnfsEntry(path, allowed);
    }

    @Override
    public void deleteEntry(Subject subject, Restriction restriction, Set<FileType> allowed, PnfsId pnfsId, String path) throws CacheException
    {
        PnfsHandler pnfs = new PnfsHandler(_pnfs, subject, restriction);
        pnfs.deletePnfsEntry(pnfsId, path, allowed);
    }

    @Override
    public void rename(Subject subject, Restriction restriction, PnfsId id, String sourcePath, String newName, boolean overwrite) throws CacheException
    {
        PnfsHandler pnfs = new PnfsHandler(_pnfs, subject, restriction);
        pnfs.renameEntry(id, sourcePath, newName, overwrite);
    }

    @Override
    public String pnfsidToPath(Subject subject, Restriction restriction, PnfsId id) throws CacheException
    {
        PnfsHandler pnfs = new PnfsHandler(_pnfs, subject, restriction);
        return pnfs.getPathByPnfsId(id).toString();
    }

    @Override
    public PnfsId pathToPnfsid(Subject subject, Restriction restriction, String path,
            boolean followLinks) throws CacheException
    {
        PnfsHandler pnfs = new PnfsHandler(_pnfs, subject, restriction);
        return pnfs.getPnfsIdByPath(path, followLinks);
    }

    @Override
    public PnfsId getParentOf(Subject subject, Restriction restriction, PnfsId id) throws CacheException
    {
        PnfsHandler pnfs = new PnfsHandler(_pnfs, subject, restriction);
        return pnfs.getParentOf(id);
    }

    @Override
    public void removeFileAttribute(Subject subject, Restriction restriction, PnfsId id,
            String attribute) throws CacheException
    {
        PnfsHandler pnfs = new PnfsHandler(_pnfs, subject, restriction);
        pnfs.notify(new PnfsFlagMessage(id, attribute, REMOVE));
    }


    @Override
    public void removeChecksum(Subject subject, Restriction restriction, PnfsId id, ChecksumType type)
            throws CacheException
    {
        PnfsHandler pnfs = new PnfsHandler(_pnfs, subject, restriction);
        pnfs.removeChecksum(id, type);
    }

    @Override
    public void addCacheLocation(Subject subject, Restriction restriction, PnfsId id, String pool)
            throws CacheException
    {
        PnfsHandler pnfs = new PnfsHandler(_pnfs, subject, restriction);
        pnfs.addCacheLocation(id, pool);
    }

    @Override
    public List<String> getCacheLocation(Subject subject, Restriction restriction, PnfsId id)
            throws CacheException
    {
        PnfsHandler pnfs = new PnfsHandler(_pnfs, subject, restriction);
        return pnfs.getCacheLocations(id);
    }

    @Override
    public void clearCacheLocation(Subject subject, Restriction restriction, PnfsId id, String pool,
            boolean removeIfLast) throws CacheException
    {
        PnfsHandler pnfs = new PnfsHandler(_pnfs, subject, restriction);
        pnfs.request(new PnfsClearCacheLocationMessage(id, pool, removeIfLast));
    }

    @Override
    public FileAttributes getFileAttributes(Subject subject, Restriction restriction, PnfsId id,
            Set<FileAttribute> attr) throws CacheException
    {
        PnfsHandler pnfs = new PnfsHandler(_pnfs, subject, restriction);
        return pnfs.getFileAttributes(id, attr);
    }

    @Override
    public FileAttributes setFileAttributes(Subject subject, Restriction restriction, PnfsId id,
            FileAttributes attr, Set<FileAttribute> acquire) throws CacheException
    {
        PnfsHandler pnfs = new PnfsHandler(_pnfs, subject, restriction);
        return pnfs.setFileAttributes(id, attr, acquire);
    }

    @Override
    public void list(Subject subject, Restriction restriction, String path, Glob glob,
            Range<Integer> range, Set<FileAttribute> attrs, ListHandler handler)
            throws CacheException
    {
        try (DirectoryStream stream = _handler.list(subject, restriction,
                new FsPath(path), glob, range, attrs)) {
            for (DirectoryEntry entry : stream) {
                handler.addEntry(entry.getName(), entry.getFileAttributes());
            }
        } catch (InterruptedException e) {
            throw new TimeoutCacheException(e.getMessage());
        }
    }

    @Override
    public FsPath createUploadPath(Subject subject, Restriction restriction, FsPath path, FsPath rootPath,
                                   Long size, AccessLatency al, RetentionPolicy rp, String spaceToken,
                                   Set<CreateOption> options)
            throws CacheException
    {
        PnfsCreateUploadPath msg = new PnfsCreateUploadPath(subject, restriction, path, rootPath,
                                                            size, al, rp, spaceToken, options);
        return _pnfs.request(msg).getUploadPath();
    }

    @Override
    public PnfsId commitUpload(Subject subject, Restriction restriction, FsPath uploadPath, FsPath pnfsPath, Set<CreateOption> options)
            throws CacheException
    {
        PnfsCommitUpload msg = new PnfsCommitUpload(subject,
                                                    restriction,
                                                    uploadPath,
                                                    pnfsPath,
                                                    options,
                                                    EnumSet.noneOf(FileAttribute.class));
        return _pnfs.request(msg).getPnfsId();
    }

    @Override
    public void cancelUpload(Subject subject, Restriction restriction, FsPath uploadPath, FsPath path) throws CacheException
    {
        _pnfs.request(new PnfsCancelUpload(subject, restriction, uploadPath, path));
    }
}
