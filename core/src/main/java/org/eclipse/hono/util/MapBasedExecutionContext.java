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
package org.eclipse.hono.util;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import io.opentracing.Span;
import io.opentracing.SpanContext;

/**
 * An execution context that stores properties in a {@code Map}.
 *
 */
public abstract class MapBasedExecutionContext implements ExecutionContext {

    private Map<String, Object> data;
    private final Span span;

    /**
     * Creates a new MapBasedExecutionContext instance.
     *
     * @param span The <em>OpenTracing</em> root span that is used to track the processing of this context.
     * @throws NullPointerException If span is {@code null}.
     */
    public MapBasedExecutionContext(final Span span) {
        this.span = Objects.requireNonNull(span);
    }

    @Override
    public final <T> T get(final String key) {
        return get(key, null);
    }

    @Override
    @SuppressWarnings("unchecked")
    public final <T> T get(final String key, final T defaultValue) {
        return Optional.ofNullable(getData().get(key))
                .map(value -> (T) value)
                .orElse(defaultValue);
    }

    @Override
    public final void put(final String key, final Object value) {
        getData().put(key, value);
    }

    /**
     * Gets the <em>OpenTracing</em> root span that is used to
     * track the processing of this context.
     *
     * @return The span.
     */
    @Override
    public final Span getTracingSpan() {
        return span;
    }

    @Override
    public final SpanContext getTracingContext() {
        return span.context();
    }

    private Map<String, Object> getData() {
        return Optional.ofNullable(data).orElseGet(() -> {
            data = new HashMap<>();
            return data;
        });
    }
}
