/* dCache - http://www.dcache.org/
 *
 * Copyright (C) 2020 Deutsches Elektronen-Synchrotron
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

import com.google.common.collect.ImmutableMap;
import org.apache.commons.math3.stat.descriptive.StatisticalSummary;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.SocketAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Build a multi-line String description suitable for including in the log
 * output.
 */
public class LogOutputDescriptionBuilder implements DescriptionBuilder<String>
{
    private static final String INDENTATION = "    ";

    private static final Map<Class,String> PLACEHOLDERS = ImmutableMap.of(
            Instant.class, "never",
            Duration.class, "none");

    private enum ItemType { SIMPLE, COMPLEX, LIST };

    private class ListReceiver implements ListDescriptionReceiver
    {
        private final PrintWriter pw;
        private ItemType lastItem;

        private ListReceiver(PrintWriter pw)
        {
            this.pw = pw;
        }

        @Override
        public void accept(Object value)
        {
            lastItem = ItemType.SIMPLE;
            pw.println(asPrintableString(value));
        }

        @Override
        public DescriptionReceiver acceptComplex()
        {
            if (lastItem == ItemType.COMPLEX) {
                pw.println("---");
            }
            lastItem = ItemType.COMPLEX;
            return new LogOutputDescriptionBuilder(new LineIndentingPrintWriter(pw, INDENTATION));
        }

        @Override
        public ListDescriptionReceiver acceptList()
        {
            if (lastItem == ItemType.LIST) {
                pw.println("---");
            }
            lastItem = ItemType.LIST;
            return new ListReceiver(new LineIndentingPrintWriter(pw, INDENTATION));
        }
    }


    private final StringWriter sw;
    private final PrintWriter pw;
    private final String initial;

    public LogOutputDescriptionBuilder(String initial)
    {
        sw = new StringWriter();
        pw = new LineIndentingPrintWriter(sw, INDENTATION);
        this.initial = initial;
    }

    public LogOutputDescriptionBuilder(PrintWriter pw)
    {
        initial = null;
        sw = null;
        this.pw = pw;
    }

    @Override
    public Optional<String> build()
    {
        String info = sw.toString();
        if (info.isBlank()) {
            return Optional.empty();
        }

        String report = initial + ":\n"
                + info.substring(0, info.length()-1); // Strip final '\n'

        return Optional.of(report);
    }

    @Override
    public void accept(String label, Object value)
    {
        print(label, asPrintableString(value));
    }

    @Override
    public <T extends Object> void optionallyAccept(String label, T value,
            Class<T> type)
    {
        Object expandedValue = value == null
                ? PLACEHOLDERS.getOrDefault(type, "unknown")
                : value;

        accept(label, expandedValue);
    }

    @Override
    public <T extends Object> void optionallyAccept(String label,
            Optional<T> value, Class<T> type)
    {
        Object expandedValue = value.isEmpty()
                ? PLACEHOLDERS.getOrDefault(type, "unknown")
                : value.get();

        accept(label, expandedValue);
    }

    @Override
    public void accept(String label, long value, long relatedValue,
            String relationship)
    {
        print(label, Long.toString(value) + " " + describeAsPercent(value, relatedValue, relationship));
    }

    @Override
    public void acceptSize(String label, long size)
    {
        print(label, Strings.describeSize(size));
    }

    @Override
    public void acceptBandwidth(String label, StatisticalSummary stats)
    {
        print(label, Strings.describeBandwidth(stats));
    }

    @Override
    public void acceptDuration(String label, StatisticalSummary stats, TimeUnit units)
    {
        print(label, TimeUtils.describeDuration(stats, units));
    }

    @Override
    public void acceptSize(String label, StatisticalSummary stats)
    {
        print(label, Strings.describeSize(stats));
    }

    @Override
    public void acceptInteger(String label, StatisticalSummary stats)
    {
        print(label, Strings.describeInteger(stats));
    }

    @Override
    public void acceptSize(String label, long value, long relatedValue,
            String relationship)
    {
        print(label, Strings.describeSize(value) + " " + describeAsPercent(value, relatedValue, relationship));
    }

    @Override
    public LogOutputDescriptionBuilder acceptComplex(String label)
    {
        pw.println(label + ":");
        return new LogOutputDescriptionBuilder(new LineIndentingPrintWriter(pw, INDENTATION));
    }

    @Override
    public ListDescriptionReceiver acceptList(String label)
    {
        pw.println(label + ":");
        return new ListReceiver(new LineIndentingPrintWriter(pw, INDENTATION));
    }

    private void print(String key, CharSequence value)
    {
        pw.println(key + ": " + value);
    }

    private CharSequence asPrintableString(Object value)
    {
        if (value instanceof SocketAddress) {
            return Strings.describe((SocketAddress)value);
        } else if (value instanceof Instant) {
            return TimeUtils.relativeTimestamp((Instant)value);
        } else if (value instanceof Duration) {
            return TimeUtils.describe((Duration)value).orElse("none");
        } else {
            return String.valueOf(value);
        }
    }

    private String describeAsPercent(long value, long relatedValue, String relationship)
    {
        double fraction = value / (double)relatedValue;
        return "(" + Strings.toThreeSigFig(100 * fraction, 1000) + "% of " + relationship + ")";
    }
}
