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

import com.google.common.collect.ImmutableList;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import diskCacheV111.util.FsPath;

import static java.util.Arrays.asList;

/**
 * Class containing utility methods.
 */
public class Restrictions
{
    private static final Restriction UNRESTRICTED = new Unrestricted();
    private static final Restriction DENY_ALL = new DenyAll();
    private static final Restriction READ_ONLY = new ReadOnly();

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
            return concat(asList(restrictions));

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
            composite.with(r);
        }

        return composite.build();
    }

    /**
     * Build an optimised composition of restrictions.
     */
    private static class CompositeRestrictionBuilder
    {
        private List<Restriction> restrictions = new ArrayList<>();

        private void with(Restriction r)
        {
            if (r instanceof Unrestricted) {
                return;
            }

            if (r instanceof DenyAll) {
                restrictions = ImmutableList.of(DENY_ALL);
                return;
            }

            if (restrictions.size() == 1 && restrictions.get(0) == DENY_ALL) {
                return;
            }

            if (r instanceof CompositeRestriction) {
                restrictions.addAll(((CompositeRestriction)r).restrictions);
            } else {
                restrictions.add(r);
            }
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

        private boolean subsumes(Restriction r)
        {
            for (Restriction o : restrictions) {
                if (r.isSubsumedBy(o)) {
                    return true;
                }
            }
            return false;
        }
    }
}
