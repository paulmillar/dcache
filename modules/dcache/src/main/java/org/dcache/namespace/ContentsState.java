package org.dcache.namespace;

/**
 *  The different states of a file's content.
 */
public enum ContentsState
{
    /**
     * The contents of the file is subject to change.
     */
    BEING_WRITTEN,

    /**
     * The contents of the file will not change.
     */
    IMMUTABLE,

    /**
     * The contents of the file have been irretrievably lost.
     */
    LOST;
}
