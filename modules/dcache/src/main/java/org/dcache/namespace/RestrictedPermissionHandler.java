/* dCache - http://www.dcache.org/
 *
 * Copyright (C) 2015 Deutsches Elektronen-Synchrotron
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
package org.dcache.namespace;

import javax.annotation.Nonnull;
import javax.security.auth.Subject;

import java.util.Set;

import diskCacheV111.util.FsPath;

import org.dcache.acl.enums.AccessType;
import org.dcache.auth.attributes.Activity;
import org.dcache.auth.attributes.Restriction;
import org.dcache.vehicles.FileAttributes;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * This class provides a wrapper around any PermissionHandler to support
 * enforcing a Restriction.  The methods are deliberately similar to
 * PermissionHandler.
 */
public class RestrictedPermissionHandler
{
    private final PermissionHandler _handler;

    public RestrictedPermissionHandler(PermissionHandler handler)
    {
        _handler = checkNotNull(handler);
    }

    public PermissionHandler getInner()
    {
        return _handler;
    }

    /**
     * Returns the set of attributes required to make policy
     * decisions. When calling any of the other methods, one or more
     * FileAttributes objects most be provided containing the
     * attributes specified by the set returned by the
     * getRequiredAttributes method.
     */
    public Set<FileAttribute> getRequiredAttributes()
    {
        return _handler.getRequiredAttributes();
    }

    /**
     * checks whether the user can read file
     *
     * @param subject
     *            identifies the subject that is trying to access a resource
     * @param restriction
     *            possible limitations to allowed user's activity
     * @param attr
     *            the attributes of the file to read
     *
     * @return Returns the access type granted
     */
    @Nonnull
    public AccessType canReadFile(Subject subject,
                                   Restriction restriction,
                                   FsPath path,
                                   FileAttributes attr)
    {
        if (restriction.isRestricted(Activity.DOWNLOAD, path)) {
            return AccessType.ACCESS_DENIED;
        }

        return _handler.canReadFile(subject, attr);
    }

    /**
     * checks whether the user can write file
     *
     * @param subject
     *            identifies the subject that is trying to access a resource
     * @param restriction
     *            possible limitations to allowed user's activity
     * @param attr
     *            the attributes of the file to write
     *
     * @return Returns the access type granted
     */
    @Nonnull
    public AccessType canWriteFile(Subject subject,
                                    Restriction restriction,
                                    FsPath path,
                                    FileAttributes attr)
    {
        if (restriction.isRestricted(Activity.UPLOAD, path.getParent())) {
            return AccessType.ACCESS_DENIED;
        }

        return _handler.canWriteFile(subject, attr);
    }


    /**
     * checks whether the user can create a sub-directory in a directory
     *
     * @param subject
     *            identifies the subject that is trying to access a resource
     * @param restriction
     *            possible limitations to allowed user's activity
     * @param parentAttr
     *            the attributes of the directory in which to create a
     *            sub-directory
     *
     * @return Returns the access type granted
     */
    @Nonnull
    public AccessType canCreateSubDir(Subject subject,
                                       Restriction restriction,
                                       FsPath path,
                                       FileAttributes parentAttr)
    {
        if (restriction.isRestricted(Activity.MANAGE, path.getParent())) {
            return AccessType.ACCESS_DENIED;
        }

        return _handler.canCreateSubDir(subject, parentAttr);
    }


    /**
     * checks whether the user can create a file in a directory
     *
     * @param subject
     *            identifies the subject that is trying to access a resource
     * @param restriction
     *            possible limitations to allowed user's activity
     * @param parentAttr
     *            the attributes of the directory in which to create a
     *            file
     *
     * @return Returns the access type granted
     */
    @Nonnull
    public AccessType canCreateFile(Subject subject,
                                     Restriction restriction,
                                     FsPath path,
                                     FileAttributes parentAttr)
    {
        if (restriction.isRestricted(Activity.UPLOAD, path.getParent())) {
            return AccessType.ACCESS_DENIED;
        }

        return _handler.canCreateFile(subject, parentAttr);
    }

    /**
     * checks whether the user can delete file
     *
     * @param subject
     *            identifies the subject that is trying to access a resource
     * @param restriction
     *            possible limitations to allowed user's activity
     * @param parentAttr
     *            Attributes of directory containing the file to delete
     * @param childAttr
     *            Attributes of the file to be deleted
     *
     * @return Returns the access type granted
     */
    @Nonnull
    public AccessType canDeleteFile(Subject subject,
                                     Restriction restriction,
                                     FsPath path,
                                     FileAttributes parentAttr,
                                     FileAttributes childAttr)
    {
        if (restriction.isRestricted(Activity.DELETE, path)) {
            return AccessType.ACCESS_DENIED;
        }

        return _handler.canDeleteFile(subject, parentAttr, childAttr);
    }

    /**
     * checks whether the user can delete directory
     *
     * @param subject
     *            identifies the subject that is trying to access a resource
     * @param restriction
     *            possible limitations to allowed user's activity
     * @param parentAttr
     *            Attributes of directory containing the directory to delete
     * @param childAttr
     *            Attributes of the directory to be deleted
     *
     * @return Returns the access type granted
     */
    @Nonnull
    public AccessType canDeleteDir(Subject subject,
                             Restriction restriction,
                             FsPath path,
                             FileAttributes parentAttr,
                             FileAttributes childAttr)
    {
        if (restriction.isRestricted(Activity.DELETE, path)) {
            return AccessType.ACCESS_DENIED;
        }

        return _handler.canDeleteDir(subject, parentAttr, childAttr);
    }

    /**
     * checks whether the user can rename a file
     *
     * @param subject
     *            identifies the subject that is trying to access a resource
     * @param restriction
     *            possible limitations to allowed user's activity
     * @param existingParentAttr
     *            Attributes of directory containing the file to rename
     * @param newParentAttr
     *            Attributes of the new parent directory
     * @param isDirectory
     *            True if and only if the entry to rename is a directory
     *
     * @return Returns the access type granted
     */
    @Nonnull
    public AccessType canRename(Subject subject,
                                 Restriction restriction,
                                 FsPath existingPath,
                                 FsPath newParentPath,
                                 FileAttributes existingParentAttr,
                                 FileAttributes newParentAttr,
                                 boolean isDirectory)
    {
        if (restriction.isRestricted(Activity.MANAGE, existingPath.getParent()) ||
                restriction.isRestricted(Activity.MANAGE, newParentPath)) {
            return AccessType.ACCESS_DENIED;
        }

        return _handler.canRename(subject, existingParentAttr, newParentAttr, isDirectory);
    }

    /**
     * checks whether the user can list directory
     *
     * @param subject
     *            identifies the subject that is trying to access a resource
     * @param restriction
     *            possible limitations to allowed user's activity
     * @param attr
     *            Attributes of the directory to list
     *
     * @return Returns the access type granted
     */
    @Nonnull
    public AccessType canListDir(Subject subject,
                                  Restriction restriction,
                                  FsPath path,
                                  FileAttributes attr)
    {
        if (restriction.isRestricted(Activity.LIST, path)) {
            return AccessType.ACCESS_DENIED;
        }

        return _handler.canListDir(subject, attr);
    }

    /**
     * checks whether the user can lookup an entry in a directory
     *
     * @param subject
     *            identifies the subject that is trying to access a resource
     * @param restriction
     *            possible limitations to allowed user's activity
     * @param attr
     *            Attributes of the directory in which to lookup an entry
     *
     * @return Returns the access type granted
     */
    @Nonnull
    public AccessType canLookup(Subject subject,
                                 Restriction restriction,
                                 FsPath path,
                                 FileAttributes attr)
    {
        if (restriction.isRestricted(Activity.READ_METADATA, path)) {
            return AccessType.ACCESS_DENIED;
        }

        return _handler.canLookup(subject, attr);
    }

    /**
     * checks whether the user can set attributes of a file/directory
     *
     * @param subject
     *            identifies the subject that is trying to access a resource
     * @param restriction
     *            possible limitations to allowed user's activity
     * @param attr
     *            Attributes of the file for which to modify an attribute
     * @param attributes
     *            Attributes to modify
     *
     * @return Returns the access type granted
     */
    @Nonnull
    public AccessType canSetAttributes(Subject subject,
                                        Restriction restriction,
                                        FsPath path,
                                        FileAttributes attr,
                                        Set<FileAttribute> attributes)
    {
        if (restriction.isRestricted(Activity.UPDATE_METADATA, path)) {
            return AccessType.ACCESS_DENIED;
        }

        return _handler.canSetAttributes(subject, attr, attributes);
    }

    /**
     * checks whether the user can get attributes of a file/directory
     *
     * @param subject
     *            identifies the subject that is trying to access a resource
     * @param restriction
     *            possible limitations to allowed user's activity
     * @param attr
     *            Attributes of the file for which to modify an attribute
     * @param attributes
     *            Attributes to retrieve
     *
     * @return Returns the access type granted
     */
    @Nonnull
    public AccessType canGetAttributes(Subject subject,
                                 Restriction restriction,
                                 FsPath path,
                                 FileAttributes attr,
                                 Set<FileAttribute> attributes)
    {
        if (restriction.isRestricted(Activity.READ_METADATA, path)) {
            return AccessType.ACCESS_DENIED;
        }

        return _handler.canGetAttributes(subject, attr, attributes);
    }
}
