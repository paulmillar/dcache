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

import org.apache.commons.math3.stat.descriptive.StatisticalSummary;

import javax.annotation.Nullable;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * A class implementing this interface may be presented to objects that
 * implement the Describable interface.  The object will receive information
 * about the current state of that object.
 * <p>
 * The information model is based on JSON (specifically, a JSON Object), simply
 * because this seems to be "good enough" for most scenarios without being
 * overly complicated.  However the data may be serialised in any format.
 */
public interface DescriptionReceiver
{
    interface ListDescriptionReceiver
    {
        /**
         * Add a simple value as a list item.
         */
        void accept(Object item);

        /**
         * Add a complex item as a list item.
         */
        DescriptionReceiver acceptComplex();

        /**
         * Add a list as a list item.
         */
        ListDescriptionReceiver acceptList();
    }

    /**
     * Add an additional, simple key-value pair.
     */
    void accept(String label, Object value);

    /**
     * Add an additional key-value pair that might be absent.  An absent value
     * is represented by {@literal null}.  If the value is absent then receiver
     * is free to completely ignore this key-value pair or to use a place-holder
     * value.
     * @param <T>
     * @param label
     * @param value
     * @param type
     */
    <T extends Object> void optionallyAccept(String label, @Nullable T value, Class<T> type);

    /**
     * Add an additional key-value pair that might be empty.  The absent value
     * is represented by {@literal Optional.empty()}.  If the value is absent
     * then receiver is free to completely ignore this key-value pair or to
     * use a place-holder value.
     * @param <T>
     * @param label
     * @param value
     * @param type
     */
    <T extends Object> void optionallyAccept(String label, Optional<T> value, Class<T> type);

    /**
     * Accept an integer value that is some fraction of some other (related)
     * value.
     */
    void accept(String label, long value, long relatedValue, String relationship);

    /**
     * Add an additional, key-value pair where the value is a file or
     * capacity size.
     */
    void acceptSize(String label, long value);

    /**
     * Add an additional, key-value pair where the value is a file or
     * capacity size, in relation to some other size.
     */
    void acceptSize(String label, long value, long relatedValue, String relationship);

    /**
     * Add information about statistics of bandwidth usage.
     */
    void acceptBandwidth(String label, StatisticalSummary stats);

    /**
     * Add information about statistics of duration.
     */
    void acceptDuration(String label, StatisticalSummary stats, TimeUnit units);

    /**
     * Add information about statistics of file or request sizes.
     */
    void acceptSize(String label, StatisticalSummary stats);

    /**
     * Add information about statistics where the values are integers.
     */
    void acceptInteger(String label, StatisticalSummary stats);

    /**
     * Add a complex value, associated with the label.  The returned object
     * is used to add key-value pairs associated with this complex value.
     */
    DescriptionReceiver acceptComplex(String label);

    /**
     * Add a list value, associated with the label.  The returned object
     * is used to add list items associated with this list value.
     */
    ListDescriptionReceiver acceptList(String label);
}
