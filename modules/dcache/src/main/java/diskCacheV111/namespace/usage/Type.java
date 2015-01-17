package diskCacheV111.namespace.usage;

/**
 * The kind of storage being accounted for.
 */
public enum Type {
    /**
     * Accounting for disk usage for files stored with ONLINE
     * latency.  This does not include any disk capacity used to store
     * NEARLINE files; either storing files to be written to tape or
     * files staged back from tape.  The physical usage is grouped by
     * pool.
     */
    ONLINE,

    /**
     * Accounting for nearline (e.g., tape) usage.  This does not
     * includes any online usage; for example, to store files before
     * they are written to tape or capacity used once a file is brought
     * from back on disk.  Physical usage assumes the tape system
     * employs no compression and no adjustment is made for any "holes"
     * from deleted files.  The physical usage is grouped by tertiary
     * instance.
     */
    NEARLINE,

    /**
     * Accounting for disk usage of NEARLINE files that have been
     * stored on tertiary medium.  This is typically data brought
     * back to satisfy client read requests.  The physical usage is
     * grouped by pool.
     */
    STAGED,

    /**
     * Accounting for disk usage of NEARLINE files that have not
     * yet been written to tape.  These are files that should be
     * stored on some tertiary medium but, for whatever reason, have
     * not yet been written.  The physical usage is grouped by pool.
     */
    FLUSHABLE,
}

