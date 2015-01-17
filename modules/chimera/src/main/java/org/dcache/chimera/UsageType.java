package org.dcache.chimera;

/**
 * A partitioning of all the storage resources of which dCache is aware.
 */
public enum UsageType
{
    /**
     * usage of disk resources to store disk-only files.
     */
    ONLINE,

    /**
     * usage of tape resources to store tape files.
     */
    NEARLINE,

    /**
     * usage of disk resources to store files already stored on tape.
     */
    STAGED,

    /**
     * usage of disk resources to store files destined for tape but that
     * are not yet written there.
     */
    FLUSHABLE,
}
