/*
 * dCache - http://www.dcache.org/
 *
 * Copyright (C) 2018 Deutsches Elektronen-Synchrotron
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
package org.dcache.restful.events.spi;


import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;

import javax.validation.constraints.NotNull;

import java.util.function.BiConsumer;

/**
 * A class that can provide a stream of events.  An object that implements
 * this interface represents a source of events that are thematically related.
 * <p>
 * Each EventStream object has a name that uniquely identifies the kind of
 * events that are generated.
 * <p>
 * This stream of events may be subdivided by specifying a domain-specific
 * selector: some JSON entity that describes which events are of interest.
 * <p>
 * Implementations provide JSON Schema that describes the semantics of the
 * selector and the format of the event information.  This is to allow
 * clients (and users) to introspect the EventStream's behaviour.
 * <p>
 * It is optional whether an EventStream allows modification of existing
 * selected events.  If so, this is achieved by issuing a select with the
 * selector identifying some existing resource.  The result is a SelectionResult
 * where {@link SelectionResult#getStatus()} returns {@literal MERGED}.
 */
public interface EventStream
{
    /**
     * A SelectedEventStream may be closed by something other than an explicit
     * client request or the channel expiring; for example, a subscription
     * could be limited to a finite number of events or finite lifetime.
     * Closing a subscription is achieved by sending the
     * EventStream.CLOSE_STREAM event.  This will trigger a call to the
     * corresponding {@link SelectedEventStream#close} method before
     * the receiver.accept call returns.
     */
    public static final JsonNode CLOSE_STREAM = new TextNode("CLOSE STREAM");

    /**
     * The event type generated by this EventStream.  Each EventStream
     * instances must have unique event type.
     */
    @NotNull
    String eventType();

    /**
     * Provide a short (typically single sentence) description of the
     * generated events.
     */
    @NotNull
    String description();

    /**
     * The JSON Schema of the selector.  The selector is a client-supplied
     * filter the describes which events are of interest.  This method provides
     * the JSON schema of valid selectors.
     */
    ObjectNode selectorSchema();

    /**
     * The JSON schema of events.  In general, an SSE event's 'data' field is
     * a JSON object.  That JSON object contains some metadata in addition to
     * the 'event' field.  This method provides the JSON schema of the value of
     * the 'event' field.
     */
    ObjectNode eventSchema();

    /**
     * Make provision for supplying selected events to the receiver.  The
     * {@literal receiver} argument must not block when receiving an event.
     * @param receiver the recipient of a selection-id and JSON event data.
     * The selection-id must correspond to some
     * {@link SelectedEventStream#getId()} value and the JSON event data must
     * adhere to {@link #eventSchema()}.
     * @param selector a filter describing which events are of interest.  The
     * format is plugin-specific and described by {@link #selectorSchema()}.
     * @return the result of processing the subscription request
     */
    @NotNull
    SelectionResult select(String channelId, @NotNull BiConsumer<String,JsonNode> receiver,
            @NotNull JsonNode selector);
}
