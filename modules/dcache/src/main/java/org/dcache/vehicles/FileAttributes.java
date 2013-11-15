package org.dcache.vehicles;

import com.google.common.base.Objects;

import java.io.Serializable;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import diskCacheV111.util.AccessLatency;
import diskCacheV111.util.PnfsId;
import diskCacheV111.util.RetentionPolicy;
import diskCacheV111.vehicles.StorageInfo;

import org.dcache.acl.ACL;
import org.dcache.namespace.FileAttribute;
import org.dcache.namespace.FileType;
import org.dcache.util.Checksum;

import static org.dcache.namespace.FileAttribute.*;

/**
 * <code>FileAttributes</code> encapsulates attributes about a logical file.
 *
 * The attributes represented by an instance of this class belong to
 * a logical file as seen by a client or user of dCache. That is,
 * FileAttributes represent the information about a file stored, or that
 * should be stored, in the name space or other central components.
 *
 * Besides their location, the class does not represent any properties
 * of physical replicas on a pool. Eg the size or checksum stored in a
 * FileAttributes instance represents the expected file size and expected
 * checksum of the file. An broken replica may have a different size or a
 * different checksum.
 *
 * The distinction between the logical and physical instance is relevant when
 * considering response types to pool query messages: These should NOT return
 * attributes of a replica using FileAttributes, except when those represent
 * cached information from the name space or other central components.
 *
 * Not all attributes may be defined. Attempts to read undefined attributes
 * will throw IllegalStateException.
 *
 * @since 1.9.5
 */
public class FileAttributes implements Serializable {


    private static final long serialVersionUID = -3689129805631724432L;

    /**
     * Set of attributes which have been set.
     */
    private Set<FileAttribute> _definedAttributes =
        EnumSet.noneOf(FileAttribute.class);

    /**
     * NFSv4 Access control list.
     */
    private ACL _acl;

    /**
     * file's size
     */
    private long _size;

    /**
     * file's attribute change time
     */
    private long _ctime;

    /**
     * file's creation time
     */
    private long _creationTime;

    /**
     * file's last access time
     */
    private long _atime;

    /**
     * file's last modification time
     */
    private long _mtime;

    /**
     * file's known checksums
     */
    private Set<Checksum> _checksums;

    /**
     * file's owner's id
     */
    private int _owner;

    /**
     * file's group id
     */
    private int _group;

    /**
     * POSIX.1 file mode
     */
    private int _mode;

    /**
     * file's access latency ( e.g. ONLINE/NEARLINE )
     */
    private AccessLatency _accessLatency;

    /**
     * file's retention policy ( e.g. CUSTODIAL/REPLICA )
     */
    private RetentionPolicy _retentionPolicy;

    /**
     * type of the file ( e.g. REG, DIR, LINK, SPECIAL )
     */
    private FileType _fileType;

    /**
     * File locations within dCache.
     */
    private Collection<String> _locations;

    /**
     * Key value map of flags associated with the file.
     */
    private Map<String, String> _flags;

    /**
     * The unique PNFS ID of a file.
     */
    private PnfsId _pnfsId;

    /**
     * The storage info of a file.
     */
    private StorageInfo _storageInfo;

    /** Throws IllegalStateException if attribute is not defined. */
    private void guard(FileAttribute attribute)
        throws IllegalStateException
    {
        if (!_definedAttributes.contains(attribute)) {
            throw new IllegalStateException("Attribute is not defined: " +
                    attribute);
        }
    }

    private void define(FileAttribute attribute)
    {
        _definedAttributes.add(attribute);
    }

    public boolean isUndefined(FileAttribute attribute)
    {
        return !_definedAttributes.contains(attribute);
    }

    public boolean isDefined(FileAttribute attribute)
    {
        return _definedAttributes.contains(attribute);
    }

    public boolean isDefined(Set<FileAttribute> attributes)
    {
        return _definedAttributes.containsAll(attributes);
    }

    /**
     * Get the set of available attributes. The set may have zero or
     * more entries.
     * @return set of defined attribute.
     */
    public Set<FileAttribute> getDefinedAttributes() {
        return _definedAttributes;
    }

    public AccessLatency getAccessLatency() {
        guard(ACCESS_LATENCY);
        return _accessLatency;
    }

    public long getAccessTime()
    {
        guard(ACCESS_TIME);
        return _atime;
    }

    public ACL getAcl()
    {
        guard(ACL);
        return _acl;
    }

    public Set<Checksum> getChecksums() {
        guard(CHECKSUM);
        return _checksums;
    }

    /**
     * Get {@link FileType} corresponding to the file.
     * @return file type
     */
    public FileType getFileType() {
        guard(TYPE);
        return _fileType;
    }

    /**
     * Get group id to which file belongs to.
     * @return group id
     */
    public int getGroup() {
        guard(OWNER_GROUP);
        return _group;
    }

    public int getMode() {
        guard(MODE);
        return _mode;
    }

    /**
     * Get file's attribute change time.
     *
     * @return time in milliseconds since 1 of January 1970 00:00.00
     */
    public long getChangeTime() {
        guard(CHANGE_TIME);
        return _ctime;
    }

    /**
     * Get file's creation time.
     * @return time in milliseconds since 1 of January 1970 00:00.00
     */
    public long getCreationTime() {
        guard(CREATION_TIME);
        return _creationTime;
    }

    /**
     * Get file's last modification time.
     * @return time in milliseconds since 1 of January 1970 00:00.00
     */
    public long getModificationTime() {
        guard(MODIFICATION_TIME);
        return _mtime;
    }

    /**
     * Get owner id to whom file belongs to.
     * @return owner id
     */
    public int getOwner() {
        guard(OWNER);
        return _owner;
    }

    public RetentionPolicy getRetentionPolicy() {
        guard(RETENTION_POLICY);
        return _retentionPolicy;
    }

    public long getSize() {
        guard(SIZE);
        return _size;
    }

    public PnfsId getPnfsId()
    {
        guard(PNFSID);
        return _pnfsId;
    }

    public StorageInfo getStorageInfo()
    {
        guard(STORAGEINFO);
        return _storageInfo;
    }

    public void setAccessTime(long atime)
    {
        define(ACCESS_TIME);
        _atime = atime;
    }

    public void setAccessLatency(AccessLatency accessLatency) {
        define(ACCESS_LATENCY);
        _accessLatency = accessLatency;
    }

    public void setAcl(ACL acl)
    {
        define(ACL);
        _acl = acl;
    }

    public void setChecksums(Set<Checksum> checksums) {
        define(CHECKSUM);
        _checksums = checksums;
    }

    public void setFileType(FileType fileType) {
        define(TYPE);
        _fileType = fileType;
    }

    public void setGroup(int group) {
        define(OWNER_GROUP);
        _group = group;
    }

    public void setMode(int mode) {
        define(MODE);
        _mode = mode;
    }

    public void setChangeTime(long ctime) {
        define(CHANGE_TIME);
        _ctime = ctime;
    }

    public void setCreationTime(long creationTime) {
        define(CREATION_TIME);
        _creationTime = creationTime;
    }

    public void setModificationTime(long mtime) {
        define(MODIFICATION_TIME);
        _mtime = mtime;
    }

    public void setOwner(int owner) {
        define(OWNER);
        _owner = owner;
    }

    public void setRetentionPolicy(RetentionPolicy retentionPolicy) {
        define(RETENTION_POLICY);
        _retentionPolicy = retentionPolicy;
    }

    public void setSize(long size) {
        define(SIZE);
        _size = size;
    }

    public void setLocations(Collection<String> pools) {
        define(LOCATIONS);
        _locations = pools;
    }

    public Collection<String> getLocations() {
        guard(LOCATIONS);
        return _locations;
    }

    public Map<String, String> getFlags() {
        guard(FLAGS);
        return _flags;
    }

    public void setFlags(Map<String, String> flags) {
        define(FLAGS);
        _flags = flags;
    }

    public void setPnfsId(PnfsId pnfsId)
    {
        define(PNFSID);
        _pnfsId = pnfsId;
    }

    public void setStorageInfo(StorageInfo storageInfo)
    {
        define(STORAGEINFO);
        _storageInfo = storageInfo;
    }

    @Override
    public String toString()
    {
        return Objects.toStringHelper(this)
                .add("defined", _definedAttributes)
                .add("acl", _acl)
                .add("size", _size)
                .add("ctime", _ctime)
                .add("creationTime", _creationTime)
                .add("atime", _atime)
                .add("mtime", _mtime)
                .add("checksums", _checksums)
                .add("owner", _owner)
                .add("group", _group)
                .add("mode", _mode)
                .add("accessLatency", _accessLatency)
                .add("retentionPolicy", _retentionPolicy)
                .add("fileType", _fileType)
                .add("locations", _locations)
                .add("flags", _flags)
                .add("pnfsId", _pnfsId)
                .add("storageInfo", _storageInfo)
                .omitNullValues()
                .toString();
    }
}
