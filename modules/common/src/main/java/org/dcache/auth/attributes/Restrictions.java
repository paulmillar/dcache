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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

import diskCacheV111.util.FsPath;

import static org.dcache.auth.attributes.Activity.*;
import static java.util.Arrays.asList;

/**
 * Class containing utility methods.
 */
public class Restrictions
{
    private static final Restriction UNRESTRICTED = OnlyAllowedActivity.restrictNone();
    private static final Restriction DENY_ALL = OnlyAllowedActivity.restrictAll();
    private static final Restriction READ_ONLY = OnlyAllowedActivity.restrict(DELETE, MANAGE, UPDATE_METADATA, UPLOAD);

    private Restrictions()
    {
        // prevent instantiating.
    }

    public static Restriction denyAll()
    {
        return DENY_ALL;
    }

    public static Restriction none()
    {
        return UNRESTRICTED;
    }

    public static Restriction readOnly()
    {
        return READ_ONLY;
    }

    /**
     * Compose multiple restrictions where each Restriction can veto an
     * operation.
     */
    public static Restriction concat(Restriction... restrictions)
    {
        switch (restrictions.length) {
        case 0:
            return UNRESTRICTED;

        case 1:
            return restrictions[0];

        case 2:
            if (restrictions [0].isSubsumedBy(restrictions [1])) {
                return restrictions [1];
            }
            if (restrictions [1].isSubsumedBy(restrictions [0])) {
                return restrictions [0];
            }

            // Fall through for default behaviour

        default:
            return concat(asList(restrictions));
        }
    }

    /**
     * Compose multiple Restrictions where each Restriction can veto an
     * operation.
     */
    public static Restriction concat(Iterable<Restriction> restrictions)
    {
        CompositeRestrictionBuilder composite = new CompositeRestrictionBuilder();

        for (Restriction r : restrictions) {
            if (r instanceof CompositeRestriction) {
                for (Restriction inner : ((CompositeRestriction) r).restrictions) {
                    composite.with(inner);
                }
            } else {
                composite.with(r);
            }
        }

        return composite.build();
    }

    /**
     * Build an optimised composition of restrictions.
     */
    private static class CompositeRestrictionBuilder
    {
        private final List<Restriction> restrictions = new ArrayList<>();

        private void with(Restriction newRestriction)
        {
            Iterator<Restriction> i = restrictions.iterator();
            while (i.hasNext()) {
                Restriction existing = i.next();

                if (newRestriction.isSubsumedBy(existing)) {
                    return;
                }

                if (existing.isSubsumedBy(newRestriction)) {
                    i.remove();
                }
            }

            restrictions.add(newRestriction);
        }

        private Restriction build()
        {
            switch (restrictions.size()) {
            case 0:
                return UNRESTRICTED;

            case 1:
                return restrictions.get(0);

            default:
                return new CompositeRestriction(restrictions);
            }
        }
    }

    /**
     * A composite of multiple restrictions where any restriction can veto
     * an activity.
     */
    private static class CompositeRestriction implements Restriction
    {
        private final List<Restriction> restrictions;

        public CompositeRestriction(List<Restriction> restrictions)
        {
            this.restrictions = restrictions;
        }

        @Override
        public boolean isRestricted(Activity activity, FsPath path)
        {
            for (Restriction r : restrictions) {
                if (r.isRestricted(activity, path)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public boolean equals(Object other)
        {
            if (!(other instanceof CompositeRestriction)) {
                return false;
            }

            CompositeRestriction r = (CompositeRestriction) other;
            return restrictions.equals(r.restrictions);
        }

        @Override
        public int hashCode()
        {
            return Objects.hashCode(restrictions);
        }

        @Override
        public boolean isSubsumedBy(Restriction other)
        {
            if (other instanceof CompositeRestriction) {
                return isSubsumedBy((CompositeRestriction) other);
            }

            for (Restriction r : restrictions) {
                if (!r.isSubsumedBy(other)) {
                    return false;
                }
            }
            return true;
        }

        private boolean isSubsumedBy(CompositeRestriction other)
        {
            for (Restriction r : restrictions) {
                if (!other.subsumes(r)) {
                    return false;
                }
            }
            return true;
        }

        private boolean subsumes(Restriction other)
        {
            for (Restriction r : restrictions) {
                if (other.isSubsumedBy(r)) {
                    return true;
                }
            }
            return false;
        }
    }
}
