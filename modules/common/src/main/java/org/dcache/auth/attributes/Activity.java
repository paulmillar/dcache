package org.dcache.auth.attributes;

/**
 * The Activity class provides a very course-grain set of actions that
 * a user may attempt.  It is intended to be used when making course-grain
 * authorisation decisions, with the target being objects in the namespace.
 */
public enum Activity
{
    /**
     * List the contents of a directory.  For each directory item,
     * if {@code #READ_METADATA} is denied then that directory item is
     * excluded from the list.
     * <p>
     * This Activity is undefined against a non-directory item.
     */
    LIST(false),

    /**
     * Read the contents of the target.  For files, this represents the
     * attempts to read the contents of that file.  For sym-link, this
     * represents the ability to know the target of the sym-link; at least one
     * additional DOWNLOAD permission is needed to read the actual content.
     * <p>
     * This Activity is undefined against a directory.
     */
    DOWNLOAD(false),

    /**
     * Manage existing content.  The target of this action is always a
     * directory.  This represents renaming an item, moving an item into another
     * directory, or create a new (sub-)directory.  Unlike CREATE+DELETE, MANAGE
     * does not allow arbitrary changes to existing content.  A rename that
     * overwrites some existing content is allowed only if a corresponding
     * DELETE of the target is allowed.
     * <p>
     * This Activity is undefined against a non-directory item.
     */
    MANAGE(true),

    /**
     * Create a new file or symbolic link within dCache.  Note that creating
     * new directory requires MANAGE.  The target is the parent directory of
     * the new item.
     * <p>
     * This Activity is undefined against a non-directory item.
     */
    UPLOAD(true),

    /**
     * Remove the target.  The target can be any directory item: file, symbolic
     * link or directory.  Note that if a user has MANAGE and limited DELETE
     * then they can DELETE all items they can MANAGE.
     */
    DELETE(true),

    /**
     * Read any metadata associated with the target.  For a directory, this
     * also represents attempts for that user to change directory.
     */
    READ_METADATA(false),

    /**
     * Update metadata about the target.  This includes modifying a file's
     * permissions, ownership, ACL, etc.
     */
    UPDATE_METADATA(true);

    private final boolean _isModifying;

    Activity(boolean isModifying)
    {
        _isModifying = isModifying;
    }

    public boolean isModifying()
    {
        return _isModifying;
    }
}
