/*******************************************************************************
 * Copyright (c) 2016, 2018 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/

package org.eclipse.hono.tracing;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import io.opentracing.Span;
import io.opentracing.log.Fields;
import io.opentracing.tag.BooleanTag;
import io.opentracing.tag.IntTag;
import io.opentracing.tag.StringTag;
import io.opentracing.tag.Tags;

/**
 * A helper class providing utility methods for interacting with the
 * OpenTracing API.
 *
 */
public final class TracingHelper {

    /**
     * An OpenTracing tag indicating if a client (device) has been authenticated.
     */
    public static final BooleanTag TAG_AUTHENTICATED = new BooleanTag("authenticated");
    /**
     * An OpenTracing tag that is used to indicate if the result of an operation
     * has been taken from a local cache.
     */
    public static final BooleanTag TAG_CACHE_HIT = new BooleanTag("cache_hit");
    /**
     * An OpenTracing tag that contains the (transport protocol specific) identifier of a
     * client connecting to a server. This could be the MQTT <em>client identifier</em> or the
     * AMQP 1.0 <em>container name</em>.
     */
    public static final StringTag TAG_CLIENT_ID = new StringTag("client_id");
    /**
     * An OpenTracing tag that contains the identifier used to correlate a response
     * with a request message.
     */
    public static final StringTag TAG_CORRELATION_ID = new StringTag("message_bus.correlation_id");
    /**
     * An OpenTracing tag that contains the number of available credits for a sender link.
     */
    public static final IntTag TAG_CREDIT = new IntTag("message_bus.credit");
    /**
     * An OpenTracing tag that contains the identifier of a (request) message.
     */
    public static final StringTag TAG_MESSAGE_ID = new StringTag("message_bus.message_id");
    /**
     * An OpenTracing tag that contains the QoS that a device has used for publishing
     * a message.
     */
    public static final StringTag TAG_QOS = new StringTag("qos");
    /**
     * An OpenTracing tag that indicates the remote delivery state of an AMQP 1.0
     * message transfer.
     */
    public static final StringTag TAG_REMOTE_STATE = new StringTag("message_bus.remote_state");
    /**
     * An OpenTracing tag indicating if a client's connection is secured using TLS.
     */
    public static final BooleanTag TAG_TLS = new BooleanTag("tls");

    private TracingHelper() {
        // prevent instantiation
    }

    /**
     * Marks an <em>OpenTracing</em> span as erroneous and logs an exception.
     * <p>
     * This method does <em>not</em> finish the span.
     * 
     * @param span The span to mark.
     * @param error The exception that has occurred.
     * @throws NullPointerException if error is {@code null}.
     */
    public static void logError(final Span span, final Throwable error) {
        if (span != null) {
            logError(span, getErrorLogItems(error));
        }
    }

    /**
     * Creates a set of items to log for an error.
     * 
     * @param error The error.
     * @return The items to log.
     */
    public static Map<String, Object> getErrorLogItems(final Throwable error) {
        final Map<String, Object> log = new HashMap<>(2);
        log.put(Fields.EVENT, Tags.ERROR.getKey());
        if (error != null) {
            log.put(Fields.ERROR_OBJECT, error);
        }
        return log;
    }

    /**
     * Marks an <em>OpenTracing</em> span as erroneous and logs a message.
     * <p>
     * This method does <em>not</em> finish the span.
     * 
     * @param span The span to mark.
     * @param message The message to log on the span.
     * @throws NullPointerException if message is {@code null}.
     */
    public static void logError(final Span span, final String message) {
        if (span != null) {
            Objects.requireNonNull(message);
            logError(span, Collections.singletonMap(Fields.MESSAGE, message));
        }
    }

    /**
     * Marks an <em>OpenTracing</em> span as erroneous and logs several items.
     * <p>
     * This method does <em>not</em> finish the span.
     * 
     * @param span The span to finish.
     * @param items The items to log on the span. Note that this method will
     *               also log an item using {@code event} as key and {@code error}
     *               as the value if the items do not contain such an entry already.
     */
    public static void logError(final Span span, final Map<String, ?> items) {
        if (span != null) {
            Tags.ERROR.set(span, Boolean.TRUE);
            if (items != null) {
                span.log(items);
                final Object event = items.get(Fields.EVENT);
                if (event == null || !Tags.ERROR.getKey().equals(event)) {
                    span.log(Tags.ERROR.getKey());
                }
            }
        }
    }
}
