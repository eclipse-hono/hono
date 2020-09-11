/*******************************************************************************
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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

import java.util.Map;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.tag.Tag;

/**
 * A {@link Span} implementation whose purpose it is to keep a {@link SpanContext} and provide it via the
 * {@link #context()} method. All the other methods are no-ops.
 *
 */
public class SpanContextHolder implements Span {

    private final SpanContext spanContext;

    /**
     * Creates a new SpanContextHolder.
     * @param spanContext The span context to be returned by the {@link #context()} method.
     */
    public SpanContextHolder(final SpanContext spanContext) {
        this.spanContext = spanContext;
    }

    @Override
    public SpanContext context() {
        return spanContext;
    }

    // -----------------

    @Override
    public Span setTag(final String key, final String value) {
        return this;
    }

    @Override
    public Span setTag(final String key, final boolean value) {
        return this;
    }

    @Override
    public Span setTag(final String key, final Number value) {
        return this;
    }

    @Override
    public <T> Span setTag(final Tag<T> tag, final T value) {
        return this;
    }

    @Override
    public Span log(final Map<String, ?> fields) {
        return this;
    }

    @Override
    public Span log(final long timestampMicroseconds, final Map<String, ?> fields) {
        return this;
    }

    @Override
    public Span log(final String event) {
        return this;
    }

    @Override
    public Span log(final long timestampMicroseconds, final String event) {
        return this;
    }

    @Override
    public Span setBaggageItem(final String key, final String value) {
        return this;
    }

    @Override
    public String getBaggageItem(final String key) {
        return null;
    }

    @Override
    public Span setOperationName(final String operationName) {
        return this;
    }

    @Override
    public void finish() {
        // do nothing
    }

    @Override
    public void finish(final long finishMicros) {
        // do nothing
    }
}
