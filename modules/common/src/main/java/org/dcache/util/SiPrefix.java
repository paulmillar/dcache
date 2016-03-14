/* dCache - http://www.dcache.org/
 *
 * Copyright (C) 2016 Deutsches Elektronen-Synchrotron
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
package org.dcache.util;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.dcache.util.SiPrefix.Prefix.NONE;
import static org.dcache.util.SiPrefix.Type.BINARY;
import static org.dcache.util.SiPrefix.Type.DECIMAL;

/**
 * This class contains utility methods that handle SI unit prefixes that denote
 * scales.
 */
public class SiPrefix
{
    /**
     * Prefixes may be grouped by whether they follow decimal ordering or
     * binary.
     */
    public enum Type
    {
        /**
         * Prefixes that are base-10 (k, M, G, T, ...).  These are the
         * traditional prefix from scientific usage.
         */
        DECIMAL,

        /**
         * Prefix that are base-2 (ki, Mi, Gi, Ti, ...).  These are the
         * prefixes often used within computing.
         */
        BINARY
    };

    public enum Prefix
    {
        NONE(null, "") {
            @Override
            public long toNone(long d)
            {
                return d;
            }

            @Override
            public long toKilo(long d)
            {
                return d / 1_000;
            }

            @Override
            public long toKibi(long d)
            {
                return d >> 10;
            }

            @Override
            public long toMega(long d)
            {
                return d / 1_000_000;
            }

            @Override
            public long toMebi(long d)
            {
                return d >> 20;
            }

            @Override
            public long toGiga(long d)
            {
                return d / 1_000_000_000L;
            }

            @Override
            public long toGibi(long d)
            {
                return d >> 30;
            }

            @Override
            public long toTera(long d)
            {
                return d / 1_000_000_000_000L;
            }

            @Override
            public long toTebi(long d)
            {
                return d >> 40;
            }

            @Override
            public long toPeta(long d)
            {
                return d / 1_000_000_000_000_000L;
            }

            @Override
            public long toPebi(long d)
            {
                return d >> 50;
            }

            @Override
            public long convert(long value, Prefix prefix)
            {
                return prefix.toNone(value);
            }
        },

        KILO(DECIMAL, "k") {
            @Override
            public long toNone(long d)
            {
                return d * 1_000L;
            }

            @Override
            public long toKilo(long d)
            {
                return d;
            }

            @Override
            public long toKibi(long d)
            {
                return (d * 1_000L) >> 10;
            }

            @Override
            public long toMega(long d)
            {
                return d / 1_000L;
            }

            @Override
            public long toMebi(long d)
            {
                return (d * 1_000L) >> 20;
            }

            @Override
            public long toGiga(long d)
            {
                return d / 1_000_000L;
            }

            @Override
            public long toGibi(long d)
            {
                return (d * 1_000L) >> 30;
            }

            @Override
            public long toTera(long d)
            {
                return d / 1_000_000_000L;
            }

            @Override
            public long toTebi(long d)
            {
                return (d * 1_000L) >> 40;
            }

            @Override
            public long toPeta(long d)
            {
                return d / 1_000_000_000_000L;
            }

            @Override
            public long toPebi(long d)
            {
                return (d * 1_000L) >> 50;
            }

            @Override
            public long convert(long value, Prefix prefix)
            {
                return prefix.toKilo(value);
            }
        },

        KIBI(BINARY, "ki") {
            @Override
            public long toNone(long d)
            {
                return d << 10;
            }

            @Override
            public long toKilo(long d)
            {
                return (d << 10) / 1_000;
            }

            @Override
            public long toKibi(long d)
            {
                return d;
            }

            @Override
            public long toMega(long d)
            {
                return (d << 10) / 1_000_000L;
            }

            @Override
            public long toMebi(long d)
            {
                return d >> 10;
            }

            @Override
            public long toGiga(long d)
            {
                return (d << 10) / 1_000_000_000L;
            }

            @Override
            public long toGibi(long d)
            {
                return d >> 20;
            }

            @Override
            public long toTera(long d)
            {
                return (d << 10) / 1_000_000_000_000L;
            }

            @Override
            public long toTebi(long d)
            {
                return d >> 30;
            }

            @Override
            public long toPeta(long d)
            {
                return (d << 10) / 1_000_000_000_000_000L;
            }

            @Override
            public long toPebi(long d)
            {
                return d >> 40;
            }

            @Override
            public long convert(long value, Prefix prefix)
            {
                return prefix.toKibi(value);
            }
        },

        MEGA(DECIMAL, "M") {
            @Override
            public long toNone(long d)
            {
                return d * 1_000_000L;
            }

            @Override
            public long toKilo(long d)
            {
                return d * 1_000;
            }

            @Override
            public long toKibi(long d)
            {
                return (d * 1_000_000) >> 10;
            }

            @Override
            public long toMega(long d)
            {
                return d;
            }

            @Override
            public long toMebi(long d)
            {
                return (d * 1_000_000L) >> 20;
            }

            @Override
            public long toGiga(long d)
            {
                return d / 1_000L;
            }

            @Override
            public long toGibi(long d)
            {
                return (d * 1_000_000L) >> 30;
            }

            @Override
            public long toTera(long d)
            {
                return d / 1_000_000L;
            }

            @Override
            public long toTebi(long d)
            {
                return (d * 1_000_000L) >> 40;
            }

            @Override
            public long toPeta(long d)
            {
                return d / 1_000_000_000L;
            }

            @Override
            public long toPebi(long d)
            {
                return (d * 1_000_000L) >> 50;
            }

            @Override
            public long convert(long value, Prefix prefix)
            {
                return prefix.toMega(value);
            }
        },

        MEBI(BINARY, "Mi") {
            @Override
            public long toNone(long d)
            {
                return d << 20;
            }

            @Override
            public long toKilo(long d)
            {
                return (d << 20) / 1_000L;
            }

            @Override
            public long toKibi(long d)
            {
                return d << 10;
            }

            @Override
            public long toMega(long d)
            {
                return (d << 20) / 1_000_000L;
            }

            @Override
            public long toMebi(long d)
            {
                return d;
            }

            @Override
            public long toGiga(long d)
            {
                return (d << 20) / 1_000_000_000L;
            }

            @Override
            public long toGibi(long d)
            {
                return d >> 10;
            }

            @Override
            public long toTera(long d)
            {
                return (d << 20) / 1_000_000_000_000L;
            }

            @Override
            public long toTebi(long d)
            {
                return d >> 20;
            }

            @Override
            public long toPeta(long d)
            {
                return (d << 20) / 1_000_000_000_000_000L;
            }

            @Override
            public long toPebi(long d)
            {
                return d >> 30;
            }

            @Override
            public long convert(long value, Prefix prefix)
            {
                return prefix.toMebi(value);
            }
        },

        GIGA(DECIMAL, "G") {
            @Override
            public long toNone(long d)
            {
                return d * 1_000_000_000L;
            }

            @Override
            public long toKilo(long d)
            {
                return d * 1_000_000;
            }

            @Override
            public long toKibi(long d)
            {
                return (d * 1_000_000_000L) >> 10;
            }

            @Override
            public long toMega(long d)
            {
                return d / 1_000L;
            }

            @Override
            public long toMebi(long d)
            {
                return (d * 1_000_000_000L) >> 20;
            }

            @Override
            public long toGiga(long d)
            {
                return d;
            }

            @Override
            public long toGibi(long d)
            {
                return (d * 1_000_000_000L) >> 30;
            }

            @Override
            public long toTera(long d)
            {
                return d / 1_000L;
            }

            @Override
            public long toTebi(long d)
            {
                return (d * 1_000_000_000L) >> 40;
            }

            @Override
            public long toPeta(long d)
            {
                return d / 1_000_000L;
            }

            @Override
            public long toPebi(long d)
            {
                return (d * 1_000_000_000L) >> 50;
            }

            @Override
            public long convert(long value, Prefix prefix)
            {
                return prefix.toGiga(value);
            }
        },

        GIBI(BINARY, "Gi") {
            @Override
            public long toNone(long d)
            {
                return d << 30;
            }

            @Override
            public long toKilo(long d)
            {
                return (d << 30) / 1_000;
            }

            @Override
            public long toKibi(long d)
            {
                return d << 20;
            }

            @Override
            public long toMega(long d)
            {
                return (d << 30) / 1_000_000L;
            }

            @Override
            public long toMebi(long d)
            {
                return d << 10;
            }

            @Override
            public long toGiga(long d)
            {
                return (d << 30) / 1_000_000_000L;
            }

            @Override
            public long toGibi(long d)
            {
                return d;
            }

            @Override
            public long toTera(long d)
            {
                return (d << 30) / 1_000_000_000_000L;
            }

            @Override
            public long toTebi(long d)
            {
                return d >> 10;
            }

            @Override
            public long toPeta(long d)
            {
                return (d << 30) / 1_000_000_000_000_000L;
            }

            @Override
            public long toPebi(long d)
            {
                return d >> 20;
            }

            @Override
            public long convert(long value, Prefix prefix)
            {
                return prefix.toGibi(value);
            }
        },

        TERA(DECIMAL, "T") {
            @Override
            public long toNone(long d)
            {
                return d * 1_000_000_000_000L;
            }

            @Override
            public long toKilo(long d)
            {
                return d * 1_000_000_000L;
            }

            @Override
            public long toKibi(long d)
            {
                return (d * 1_000_000_000_000L) >> 10;
            }

            @Override
            public long toMega(long d)
            {
                return d * 1_000_000L;
            }

            @Override
            public long toMebi(long d)
            {
                return (d * 1_000_000_000_000L) >> 20;
            }

            @Override
            public long toGiga(long d)
            {
                return d * 1_000L;
            }

            @Override
            public long toGibi(long d)
            {
                return (d * 1_000_000_000_000L) >> 30;
            }

            @Override
            public long toTera(long d)
            {
                return d;
            }

            @Override
            public long toTebi(long d)
            {
                return (d * 1_000_000_000_000L) >> 40;
            }

            @Override
            public long toPeta(long d)
            {
                return d * 1_000L;
            }

            @Override
            public long toPebi(long d)
            {
                return (d * 1_000_000_000_000L) >> 50;
            }

            @Override
            public long convert(long value, Prefix prefix)
            {
                return prefix.toTera(value);
            }
        },

        TEBI(BINARY, "Ti") {
            @Override
            public long toNone(long d)
            {
                return d << 40;
            }

            @Override
            public long toKilo(long d)
            {
                return (d << 40) / 1_000;
            }

            @Override
            public long toKibi(long d)
            {
                return d << 30;
            }

            @Override
            public long toMega(long d)
            {
                return (d << 40) / 1_000_000L;
            }

            @Override
            public long toMebi(long d)
            {
                return d << 20;
            }

            @Override
            public long toGiga(long d)
            {
                return (d << 40) / 1_000_000_000L;
            }

            @Override
            public long toGibi(long d)
            {
                return d << 10;
            }

            @Override
            public long toTera(long d)
            {
                return (d << 40) / 1_000_000_000_000L;
            }

            @Override
            public long toTebi(long d)
            {
                return d;
            }

            @Override
            public long toPeta(long d)
            {
                return (d << 40) / 1_000_000_000_000_000L;
            }

            @Override
            public long toPebi(long d)
            {
                return d >> 10;
            }

            @Override
            public long convert(long value, Prefix prefix)
            {
                return prefix.toTebi(value);
            }
        },

        PETA(DECIMAL, "P") {
            @Override
            public long toNone(long d)
            {
                return d * 1_000_000_000_000_000L;
            }

            @Override
            public long toKilo(long d)
            {
                return d * 1_000_000_000_000L;
            }

            @Override
            public long toKibi(long d)
            {
                return (d * 1_000_000_000_000_000L) >> 10;
            }

            @Override
            public long toMega(long d)
            {
                return d * 1_000_000_000L;
            }

            @Override
            public long toMebi(long d)
            {
                return (d * 1_000_000_000_000_000L) >> 20;
            }

            @Override
            public long toGiga(long d)
            {
                return d * 1_000_000L;
            }

            @Override
            public long toGibi(long d)
            {
                return (d * 1_000_000_000_000_000L) >> 30;
            }

            @Override
            public long toTera(long d)
            {
                return d * 1_000L;
            }

            @Override
            public long toTebi(long d)
            {
                return (d * 1_000_000_000_000_000L) >> 40;
            }

            @Override
            public long toPeta(long d)
            {
                return d;
            }

            @Override
            public long toPebi(long d)
            {
                return (d * 1_000_000_000_000_000L) >> 50;
            }

            @Override
            public long convert(long value, Prefix prefix)
            {
                return prefix.toPeta(value);
            }
        },

        PEBI(BINARY, "Pi") {
            @Override
            public long toNone(long d)
            {
                return d << 50;
            }

            @Override
            public long toKilo(long d)
            {
                return (d << 50) / 1_000L;
            }

            @Override
            public long toKibi(long d)
            {
                return d << 40;
            }

            @Override
            public long toMega(long d)
            {
                return (d << 50) / 1_000_000L;
            }

            @Override
            public long toMebi(long d)
            {
                return d << 30;
            }

            @Override
            public long toGiga(long d)
            {
                return (d << 50) / 1_000_000_000L;
            }

            @Override
            public long toGibi(long d)
            {
                return d << 20;
            }

            @Override
            public long toTera(long d)
            {
                return (d << 50) / 1_000_000_000_000L;
            }

            @Override
            public long toTebi(long d)
            {
                return d << 10;
            }

            @Override
            public long toPeta(long d)
            {
                return (d << 50) / 1_000_000_000_000_000L;
            }

            @Override
            public long toPebi(long d)
            {
                return d;
            }

            @Override
            public long convert(long value, Prefix prefix)
            {
                return prefix.toPebi(value);
            }
        };

        private final Type type;
        private final String label;

        Prefix(Type type, String label)
        {
            this.type = type;
            this.label = label;
        }

        /**
         * Converts the given value in the given prefix to this prefix.
         * Conversions from smaller to larger prefix truncate, so lose
         * precision. For example, converting 999 KILO to MEGA results in 0.
         * Conversions from larger to smaller granularities with arguments that
         * would numerically overflow saturate to Long.MIN_VALUE if negative or
         * Long.MAX_VALUE if positive.
         * For example, to convert 10 PEBI to MEBI, use: Prefix.MEBI.convert(10L, Prefix.PEBI)
         * @param sourceValue the value in some other prefix
         * @param sourcePrefix the prefix of sourceValue
         * @return the value in this prefix.
         */
        public long convert(long sourceValue, Prefix sourcePrefix)
        {
            throw new UnsupportedOperationException();
        }

        public long toNone(long d)
        {
            throw new UnsupportedOperationException();
        }

        public long toKilo(long d)
        {
            throw new UnsupportedOperationException();
        }

        public long toKibi(long d)
        {
            throw new UnsupportedOperationException();
        }

        public long toMega(long d)
        {
            throw new UnsupportedOperationException();
        }

        public long toMebi(long d)
        {
            throw new UnsupportedOperationException();
        }

        public long toGiga(long d)
        {
            throw new UnsupportedOperationException();
        }

        public long toGibi(long d)
        {
            throw new UnsupportedOperationException();
        }

        public long toTera(long d)
        {
            throw new UnsupportedOperationException();
        }

        public long toTebi(long d)
        {
            throw new UnsupportedOperationException();
        }

        public long toPeta(long d)
        {
            throw new UnsupportedOperationException();
        }

        public long toPebi(long d)
        {
            throw new UnsupportedOperationException();
        }
    }

    /**
     * Create a string that describes some quantity using SI prefixes.  The
     * returned value is a single number with a prefix and units (e.g.,
     * "301 MiB").  The result may be only approximately the supplied value,
     * there can be loss of information.  The format is
     * {@literal <integer> <space> [<prefix>]<units>}.
     * @param size the value
     * @param type the kind of prefix to use
     * @param units the units.
     * @return a String describing the size, using
     */
    public static String singleValue(long size, Type type, String units)
    {
        return appendSimpleSize(new StringBuilder(), size, type, units).toString();
    }

    /**
     * Append a string to the StringBuilder that describes some quantity using
     * SI prefixes.  The appended text is a single number with a prefix and
     * units (e.g., "301 MiB").  The result may be only approximately the
     * supplied value, there can be loss of information.  The format is
     * {@literal <integer> <space> [<prefix>]<units>}.
     * @param sb the StringBuilder to which the description is appended
     * @param size the value
     * @param type the kind of prefix to use
     * @param units the units.
     * @return the supplied StringBuilder.
     */
    public static StringBuilder appendSimpleSize(StringBuilder sb, long size, Type type, String units)
    {
        checkNotNull(type);
        checkNotNull(units);

        Prefix desiredPrefix = NONE;
        for (Prefix prefix : Prefix.values()) {
            if (prefix.type == type) {
                if (prefix.convert(size, NONE) >= 2) {
                    desiredPrefix = prefix;
                } else {
                    break;
                }
            }
        }

        return sb.append(desiredPrefix.convert(size, NONE))
                .append(' ')
                .append(desiredPrefix.label)
                .append(units);
    }
}
