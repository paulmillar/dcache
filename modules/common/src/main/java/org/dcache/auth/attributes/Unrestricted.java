package org.dcache.auth.attributes;

import diskCacheV111.util.FsPath;

/**
 * Do not restrict the user's activity.
 */
public class Unrestricted implements Restriction
{
    private static final long serialVersionUID = 1L;

    protected Unrestricted()
    {
    }

    @Override
    public boolean alwaysRestricted(Activity activity)
    {
        return false;
    }

    @Override
    public boolean isRestricted(Activity activity, FsPath path)
    {
        return false;
    }

    @Override
    public int hashCode()
    {
        return Unrestricted.class.hashCode();
    }

    @Override
    public boolean equals(Object other)
    {
        return other instanceof Unrestricted;
    }

    @Override
    public boolean isSubsumedBy(Restriction other)
    {
        return true;
    }

    @Override
    public String toString()
    {
        return "Unrestricted";
    }
}
