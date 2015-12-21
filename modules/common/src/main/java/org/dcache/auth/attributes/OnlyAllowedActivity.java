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
package org.dcache.auth.attributes;


import java.util.EnumSet;

import diskCacheV111.util.FsPath;

/**
 * A Restriction that allows a user to perform only activity from the
 * supplied set of activities.
 */
public class OnlyAllowedActivity implements Restriction
{
    private static final long serialVersionUID = 1L;

    private final EnumSet<Activity> denied;

    protected OnlyAllowedActivity(EnumSet<Activity> allowed)
    {
        denied = EnumSet.complementOf(allowed);
    }

    @Override
    public boolean isRestricted(Activity activity, FsPath path)
    {
        return denied.contains(activity);
    }

    @Override
    public int hashCode()
    {
        return denied.hashCode();
    }

    @Override
    public boolean equals(Object other)
    {
        return other instanceof OnlyAllowedActivity && denied.equals(((OnlyAllowedActivity) other).denied);
    }

    @Override
    public boolean isSubsumedBy(Restriction other)
    {
        if (!(other instanceof OnlyAllowedActivity)) {
            return false;
        }

        EnumSet<Activity> otherDenied = ((OnlyAllowedActivity) other).denied;

        return otherDenied.containsAll(denied);
    }

    @Override
    public String toString()
    {
        if (denied.isEmpty()) {
            return "Unrestricted";
        }
        if (denied.size() == Activity.values().length) {
            return "FullyRestricted";
        }
        return "Restrict" + denied.toString();
    }
}
