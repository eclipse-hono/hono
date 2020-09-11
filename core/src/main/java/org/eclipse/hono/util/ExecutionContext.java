/**
 * Copyright (c) 2018, 2020 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package org.eclipse.hono.util;

import io.opentracing.Span;
import io.opentracing.SpanContext;

/**
 * A container for information relevant for processing a message sent by a device.
 * <p>
 * Provides means to store arbitrary key/value pairs.
 */
public interface ExecutionContext {

    /**
     * Gets the value for a key.
     *
     * @param <T> The type of the value.
     * @param key The key to get the value for.
     * @return The value or {@code null} if the key is unknown.
     */
    <T> T get(String key);

    /**
     * Gets the value for a key.
     *
     * @param <T> The type of the value.
     * @param key The key to get the value for.
     * @param defaultValue The value to return if the key is unknown.
     * @return The value.
     */
    <T> T get(String key, T defaultValue);

    /**
     * Sets a value for a key.
     *
     * @param key The key.
     * @param value The value.
     */
    void put(String key, Object value);

    /**
     * Gets the <em>OpenTracing</em> context that is used to
     * track the processing of this context.
     *
     * @return The context or {@code null} if no tracing context is set.
     */
    SpanContext getTracingContext();

    /**
     * Gets the <em>OpenTracing</em> root span that is used to
     * track the processing of this context.
     *
     * @return The span or {@code null} if no span is set.
     */
    Span getTracingSpan();

}
