package org.dcache.util;

import com.google.common.base.Strings;
import com.google.common.primitives.Ints;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.dcache.util.ByteUnit.BYTES;
import static org.dcache.util.ByteUnit.Type.DECIMAL;
import static org.dcache.util.ByteUnits.isoSymbol;

/**
 * Utility class to output a formatted table.
 *
 * Automatically determines column width, supports various data types, left and
 * right adjusted column, and column headers.
 */
public class ColumnWriter
{
    private final List<String> headers = new ArrayList<>();
    private final List<Integer> spaces = new ArrayList<>();
    private final List<Column> columns = new ArrayList<>();
    private final List<Row> rows = new ArrayList<>();
    private boolean abbrev;
    private boolean headersAffectRowWidth;

    public enum DateStyle {
         /** ISO 8601. */
        ISO,

        /** As with GNU coreutils 'ls -l'. */
        LS
    }

    public ColumnWriter()
    {
        spaces.add(0);
    }

    private void addColumn(Column column)
    {
        columns.add(column);
        if (headers.size() < columns.size()) {
            headers.add(null);
        }
        spaces.add(0);
    }

    public ColumnWriter left(String name)
    {
        addColumn(new LeftColumn(name));
        return this;
    }

    public ColumnWriter centre(String name)
    {
        addColumn(new CentreColumn(name));
        return this;
    }

    public ColumnWriter right(String name)
    {
        addColumn(new RightColumn(name));
        return this;
    }

    public ColumnWriter bytes(String name)
    {
        addColumn(new ByteColumn(name, ByteUnit.Type.DECIMAL));
        return this;
    }

    public ColumnWriter bytes(String name, Optional<ByteUnit> units, ByteUnit.Type displayUnits)
    {
        Column c = units.isPresent()
                ? new ByteColumn(name, units.get())
                : new ByteColumn(name, displayUnits);
        addColumn(c);
        return this;
    }

    public ColumnWriter bytes(String name, ByteUnit units)
    {
        addColumn(new ByteColumn(name, units));
        return this;
    }

    public ColumnWriter fixed(String value)
    {
        addColumn(new FixedColumn(value));
        return this;
    }

    public ColumnWriter space()
    {
        int last = spaces.size() - 1;
        spaces.set(last, spaces.get(last) + 1);
        return this;
    }

    // This is disabled by default since existing code depends on headers
    // not affecting column with to implement a poor man's grouped heading.
    public ColumnWriter headersInColumns()
    {
        headersAffectRowWidth = true;
        return this;
    }

    public ColumnWriter abbreviateBytes(boolean abbrev)
    {
        this.abbrev = abbrev;
        return this;
    }

    public TabulatedRow row()
    {
        TabulatedRow row = new TabulatedRow();
        rows.add(row);
        return row;
    }

    public void row(String value)
    {
        rows.add(new LiteralRow(value));
    }

    @Override
    public String toString()
    {
        return toString("\n");
    }

    public String toString(String endOfLine)
    {
        if (rows.isEmpty()) {
            return "";
        }
        List<Integer> widths = calculateWidths();
        List<Integer> spaces = new ArrayList<>(this.spaces);

        StringWriter result = new StringWriter();
        try (PrintWriter out = new NoTrailingWhitespacePrintWriter(result)) {
            String header = renderHeader(spaces, widths);
            if (!header.isEmpty()) {
                out.print(header);
                out.print(endOfLine);
            }
            for (Row row: rows) {
                row.render(columns, spaces, widths, out, endOfLine);
            }
        }
        return result.toString();
    }

    private List<Integer> calculateWidths()
    {
        int columnCount = columns.size();
        int[] widths = new int[columnCount];
        if (headersAffectRowWidth) {
            for (int i = 0; i < columnCount; i++) {
                String header = headers.get(i);
                widths[i] = header == null ? 0 : header.length();
            }
        }
        for (Row row : rows) {
            for (int i = 0; i < columnCount; i++) {
                widths[i] = Math.max(widths[i], row.width(columns.get(i)));
            }
        }
        return Ints.asList(widths);
    }

    private String renderHeader(List<Integer> spaces, List<Integer> widths)
    {
        int columnCount = columns.size();
        StringBuilder line = new StringBuilder();
        int columnEnd = 0;
        for (int i = 0; i < columnCount; i++) {
            String header = headers.get(i);
            if (header != null) {
                int headerStart;
                headerStart = columnEnd + spaces.get(i)
                        + columns.get(i).headerIndentation(header.length(), widths.get(i));
                if (!headersAffectRowWidth && line.length() >= headerStart) {
                    int newHeaderStart = (line.length() > 0) ? line.length() + 1 : 0;
                    spaces.set(i, spaces.get(i) + newHeaderStart - headerStart);
                    headerStart = newHeaderStart;
                }
                for (int c = line.length(); c < headerStart; c++) {
                    line.append(' ');
                }
                line.append(header);
            }
            columnEnd = columnEnd + spaces.get(i) + widths.get(i);
        }
        return line.toString();
    }

    public ColumnWriter date(String name)
    {
        addColumn(new DateColumn(name));
        return this;
    }

    public ColumnWriter date(String name, DateStyle style)
    {
        addColumn(new DateColumn(name, style));
        return this;
    }

    public ColumnWriter header(String text)
    {
        headers.add(text);
        return this;
    }

    private interface Column
    {
        String name();
        int headerIndentation(int headerWidth, int columnWidth);
        int width(Object value);
        void render(Object value, int actualWidth, PrintWriter writer);
    }

    private abstract static class AbstractColumn implements Column
    {
        protected final String name;

        public AbstractColumn(String name)
        {
            this.name = name;
        }

        @Override
        public String name()
        {
            return name;
        }
    }

    private abstract static class RegularColumn extends AbstractColumn
    {
        private RegularColumn(String name)
        {
            super(name);
        }

        @Override
        public int width(Object value)
        {
            return Objects.toString(value, "").length();
        }
    }

    private static class LeftColumn extends RegularColumn
    {
        private LeftColumn(String name)
        {
            super(name);
        }

        @Override
        public int headerIndentation(int headerWidth, int columnWidth)
        {
            return 0;
        }

        @Override
        public void render(Object value, int actualWidth, PrintWriter out)
        {
            out.append(Strings.padEnd(Objects.toString(value, ""), actualWidth, ' '));
        }
    }

    private static class CentreColumn extends RegularColumn
    {
        private CentreColumn(String name)
        {
            super(name);
        }

        @Override
        public int headerIndentation(int headerWidth, int columnWidth)
        {
            return Math.max(columnWidth - headerWidth, 0)/2;
        }

        @Override
        public void render(Object value, int actualWidth, PrintWriter out)
        {
            String text = Objects.toString(value, "");
            out.append(Strings.padEnd(Strings.padStart(text, (actualWidth + text.length())/2, ' '), actualWidth, ' '));
         }
    }

    private static class RightColumn extends RegularColumn
    {
        private RightColumn(String name)
        {
            super(name);
        }

        @Override
        public int headerIndentation(int headerWidth, int columnWidth)
        {
            return Math.max(columnWidth - headerWidth, 0);
        }

        @Override
        public void render(Object value, int actualWidth, PrintWriter out)
        {
            out.append(Strings.padStart(Objects.toString(value, ""), actualWidth, ' '));
        }
    }

    /**
     * Right adjusted column for byte quantities. Supports a narrow column mode in
     * which quantities are rounded to fit in three characters plus a trailing
     * SI prefix.
     */
    private class ByteColumn extends AbstractColumn
    {
        private final Optional<ByteUnit> unit;
        private final ByteUnit.Type displayUnits;

        public ByteColumn(String name, ByteUnit.Type displayUnits)
        {
            super(name);
            this.unit = Optional.empty();
            this.displayUnits = displayUnits;
        }

        public ByteColumn(String name, ByteUnit unit)
        {
            super(name);
            this.unit = Optional.of(unit);
            this.displayUnits = ByteUnit.Type.DECIMAL; // FIXME: this shouldn't be needed.
        }

        @Override
        public int headerIndentation(int headerWidth, int columnWidth)
        {
            return Math.max(columnWidth - headerWidth, 0);
        }

        @Override
        public int width(Object rawValue)
        {
            if (rawValue == null) {
                return 0;
            }

            long value = (long)rawValue;

            if (abbrev) {
                return displayUnits.unitsOf(value) == BYTES ? 4 : 5;
            } else if (unit.isPresent()) {
                return render(value, unit.get()).length();
            } else {
                return String.format("%d", value).length();
            }
        }

        private String render(long value, ByteUnit units)
        {
            if (units == BYTES) {
                return String.format("%3d", value);
            } else {
                double tmp = units.convert((double) value, BYTES);
                if (tmp >= 0 && tmp < 9.95) {
                    return String.format("%.1f", tmp);
                } else {
                    return String.format("%.0f", tmp);
                }
            }
        }

        private void render(long value, int actualWidth, PrintWriter out)
        {
            ByteUnit units = displayUnits.unitsOf(value);
            String symbol = isoSymbol().of(units);
            String numerical = render(value, units);

            int padding = actualWidth - numerical.length() - symbol.length();
            while (padding-- > 0) {
                out.append(' ');
            }
            out.append(numerical).append(symbol);
        }

        private void render(long value, int actualWidth, PrintWriter out, ByteUnit units)
        {
            String renderedValue = render(value, units);
            out.format("%" + actualWidth + 's', renderedValue);
        }

        @Override
        public void render(Object o, int actualWidth, PrintWriter out)
        {
            if (o == null) {
                while (actualWidth-- > 0) {
                    out.append(' ');
                }
            } else {
                long value = (long) o;
                if (abbrev) {
                    render(value, actualWidth, out);
                } else if (unit.isPresent()) {
                    render(value, actualWidth, out, unit.get());
                } else {
                    out.format("%" + actualWidth + 'd', value);
                }
            }
        }
    }

    private static class FixedColumn implements Column
    {
        private final String value;

        public FixedColumn(String value)
        {
            this.value = value;
        }

        @Override
        public int headerIndentation(int headerWidth, int columnWidth)
        {
            return 0;
        }

        @Override
        public String name()
        {
            return null;
        }

        @Override
        public int width(Object o)
        {
            return value.length();
        }

        @Override
        public void render(Object o, int actualWidth, PrintWriter out)
        {
            out.append(Strings.padEnd(value, actualWidth, ' '));
        }
    }

    private static class DateColumn extends AbstractColumn
    {
        public static final String ISO_FORMAT = "%1$tF %1$tT";
        public static final String LS_YEAR_FORMAT = "%1$tb %1$2te  %1$tY";
        public static final String LS_NO_YEAR_FORMAT = "%1$tb %1$2te %1$tR";
        public static final int WIDTH_OF_ISO_FORMAT = 19;
        public static final int WIDTH_OF_LS_FORMAT = 12;

        public final DateStyle style;
        private final long sixMonthsInPast;
        private final long oneHourInFuture;

        public DateColumn(String name)
        {
            this(name, DateStyle.ISO);
        }

        public DateColumn(String name, DateStyle style)
        {
            super(name);
            this.style = style;

            oneHourInFuture = System.currentTimeMillis() + TimeUnit.HOURS.toMillis(1);
            Calendar calendar = Calendar.getInstance();
            calendar.add(Calendar.MONTH, -6);
            sixMonthsInPast = calendar.getTimeInMillis();
        }

        @Override
        public int headerIndentation(int headerWidth, int columnWidth)
        {
            return Math.max(columnWidth - headerWidth, 0);
        }

        @Override
        public int width(Object value)
        {
            switch (style) {
            case ISO:
                return WIDTH_OF_ISO_FORMAT;
            case LS:
                return WIDTH_OF_LS_FORMAT;
            default:
                throw new RuntimeException("Unknown style: " + style);
            }
        }

        private String getFormat(Object value)
        {
            switch (style) {
            case ISO:
                return ISO_FORMAT;
            case LS:
                Date when = (Date) value;
                if (when.getTime() < sixMonthsInPast || when.getTime() > oneHourInFuture) {
                    return LS_YEAR_FORMAT;
                } else {
                    return LS_NO_YEAR_FORMAT;
                }
            default:
                throw new RuntimeException("Unknown style: " + style);
            }
        }

        @Override
        public void render(Object value, int actualWidth, PrintWriter out)
        {
            if (value == null) {
                while (actualWidth-- > 0) {
                    out.append(' ');
                }
            } else {
                out.format(getFormat(value), value);
            }
        }
    }

    private interface Row
    {
        int width(Column column);

        void render(List<Column> columns, List<Integer> spaces, List<Integer> widths,
                PrintWriter out, String endOfLine);
    }

    public static class TabulatedRow implements Row
    {
        private final Map<String, Object> values = new HashMap<>();
        private final Map<String, String> fills = new HashMap<>();

        public TabulatedRow value(String column, Object value)
        {
            values.put(column, value);
            return this;
        }

        public TabulatedRow fill(String column, String value)
        {
            fills.put(column, value);
            return this;
        }

        @Override
        public int width(Column column)
        {
            return column.width(values.get(column.name()));
        }

        @Override
        public void render(List<Column> columns, List<Integer> spaces,
                List<Integer> widths, PrintWriter out, String endOfLine)
        {
            int size = columns.size();
            for (int i = 0; i < size; i++) {
                for (int c = spaces.get(i); c > 0; c--) {
                    out.append(' ');
                }
                Column column = columns.get(i);
                Object value = values.get(column.name());
                int width = widths.get(i);
                if (value == null) {
                    String fill = fills.get(column.name());
                    if (fill != null) {
                        int count = (width + width % fill.length())/fill.length();
                        value = Strings.repeat(fill, count).subSequence(0, width);
                    }
                }
                column.render(value, width, out);
            }
            out.print(endOfLine);
        }
    }

    private static class LiteralRow implements Row
    {
        private final String value;

        private LiteralRow(String value)
        {
            this.value = value;
        }

        @Override
        public int width(Column column)
        {
            return 0;
        }

        @Override
        public void render(List<Column> columns, List<Integer> spaces, List<Integer> widths, PrintWriter out, String endOfLine)
        {
            out.print(value);
            out.print(endOfLine);
        }
    }
}
