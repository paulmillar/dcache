package org.dcache.auth.attributes;

import diskCacheV111.util.FsPath;

/**
 * Disallow all activity: the user is not allowed to do anything.
 */
public class DenyAll implements Restriction
{
    private static final long serialVersionUID = 1L;

    protected DenyAll()
    {
    }

    @Override
    public boolean alwaysRestricted(Activity activity)
    {
        return true;
    }

    @Override
    public boolean isRestricted(Activity activity, FsPath path)
    {
        return true;
    }

    @Override
    public int hashCode()
    {
        return DenyAll.class.hashCode();
    }

    @Override
    public boolean equals(Object other)
    {
        return other instanceof DenyAll;
    }

    @Override
    public boolean isSubsumedBy(Restriction other)
    {
        return other instanceof DenyAll;
    }

    @Override
    public String toString()
    {
        return "DenyAllRestriction";
    }
}
