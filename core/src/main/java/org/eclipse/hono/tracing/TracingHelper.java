/**
 * Copyright (c) 2018 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 1.0 which is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * SPDX-License-Identifier: EPL-1.0
 */

package org.eclipse.hono.tracing;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import io.opentracing.Span;
import io.opentracing.tag.BooleanTag;
import io.opentracing.tag.StringTag;
import io.opentracing.tag.Tags;

/**
 * A helper class providing utility methods for interacting with the
 * OpenTracing API.
 *
 */
public final class TracingHelper {

    /**
     * The name of the field to use for adding an exception to an OpenTracing span's log.
     */
    public static final String LOG_FIELD_ERROR_OBJECT = "error.object";
    /**
     * The name of the field to use for adding an event to an OpenTracing span's log.
     */
    public static final String LOG_FIELD_EVENT = "event";
    /**
     * The name of the field to use for adding an explanatory message to an OpenTracing span's log.
     */
    public static final String LOG_FIELD_MESSAGE = "message";

    /**
     * An OpenTracing tag indicating if a client (device) has been authenticated.
     */
    public static final BooleanTag TAG_AUTHENTICATED = new BooleanTag("authenticated");
    /**
     * An OpenTracing tag that contains the QoS that a device has used for publishing
     * a message.
     */
    public static final StringTag TAG_QOS = new StringTag("qos");
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
        log.put(LOG_FIELD_EVENT, Tags.ERROR.getKey());
        if (error != null) {
            log.put(LOG_FIELD_ERROR_OBJECT, error);
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
            logError(span, Collections.singletonMap(LOG_FIELD_MESSAGE, message));
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
     *               as the value.
     */
    public static void logError(final Span span, final Map<String, ?> items) {
        if (span != null) {
            Tags.ERROR.set(span, Boolean.TRUE);
            if (items != null) {
                span.log(items);
                span.log(Tags.ERROR.getKey());
            }
        }
    }

}
