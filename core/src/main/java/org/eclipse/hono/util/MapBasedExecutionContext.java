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
import java.util.Optional;

import io.opentracing.SpanContext;

/**
 * An execution context that stores properties in a {@code Map}.
 *
 */
public abstract class MapBasedExecutionContext implements ExecutionContext {

    private Map<String, Object> data;
    private SpanContext spanContext;

    @Override
    public final <T> T get(final String key) {
        return get(key, null);
    }

    @Override
    @SuppressWarnings("unchecked")
    public final <T> T get(final String key, final T defaultValue) {
        return Optional.ofNullable(getData().get(key)).map(value -> {
            return (T) value;
        }).orElse(defaultValue);
    }

    @Override
    public final void put(final String key, final Object value) {
        getData().put(key, value);
    }


    @Override
    public SpanContext getTracingContext() {
        return spanContext;
    }

    @Override
    public void setTracingContext(final SpanContext spanContext) {
        this.spanContext = spanContext;
    }

    private Map<String, Object> getData() {
        return Optional.ofNullable(data).orElseGet(() -> {
            data = new HashMap<>();
            return data;
        });
    }
}
