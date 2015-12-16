package org.dcache.auth.attributes;


import diskCacheV111.util.FsPath;

/**
 * A Restriction that allows a user to read content in dCache but not to
 * modify it.
 */
public class ReadOnly implements Restriction
{
    private static final long serialVersionUID = 1L;

    protected ReadOnly()
    {
    }

    @Override
    public boolean isRestricted(Activity activity, FsPath path)
    {
        return activity.isModifying();
    }

    @Override
    public int hashCode()
    {
        return ReadOnly.class.hashCode();
    }

    @Override
    public boolean equals(Object other)
    {
        return other instanceof ReadOnly;
    }

    @Override
    public boolean isSubsumedBy(Restriction other)
    {
        return other instanceof ReadOnly || other instanceof DenyAll;
    }

    @Override
    public String toString()
    {
        return "ReadOnly";
    }
}
