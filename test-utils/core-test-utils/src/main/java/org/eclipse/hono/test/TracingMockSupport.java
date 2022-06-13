/**
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
 */

package org.eclipse.hono.test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.mockito.Mockito;

import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.Tracer.SpanBuilder;

/**
 * Mocks for OpenTracing objects.
 */
public final class TracingMockSupport {

    private TracingMockSupport() {
    }

    /**
     * Creates a mocked OpenTracing Span.
     *
     * @return The span.
     */
    public static Span mockSpan() {
        final SpanContext spanContext = mock(SpanContext.class);
        final Span span = mock(Span.class, Mockito.RETURNS_SELF);
        when(span.context()).thenReturn(spanContext);
        return span;
    }

    /**
     * Creates a mocked OpenTracing Tracer that will use a SpanBuilder that
     * creates the given Span.
     *
     * @param spanToBuild The object that the <em>start</em> method of the
     *                    SpanBuilder should produce that the tracer returns
     *                    for its <em>buildSpan</em> method.
     * @return The tracer.
     */
    public static Tracer mockTracer(final Span spanToBuild) {
        return mockTracer(mockSpanBuilder(spanToBuild));
    }

    /**
     * Creates a mocked OpenTracing Tracer that will use a given SpanBuilder.
     *
     * @param spanBuilder The object that the <em>buildSpan</em> method of the
     *                    returned tracer should return.
     * @return The tracer.
     */
    public static Tracer mockTracer(final SpanBuilder spanBuilder) {
        final Tracer tracer = mock(Tracer.class);
        when(tracer.buildSpan(anyString())).thenReturn(spanBuilder);
        final Scope spanScope = mock(Scope.class);
        when(tracer.activateSpan(any(Span.class))).thenReturn(spanScope);
        return tracer;
    }

    /**
     * Creates a mocked OpenTracing SpanBuilder for creating a given Span.
     * <p>
     * All invocations on the mock are stubbed to return the builder by default.
     *
     * @param spanToCreate The object that the <em>start</em> method of the
     *                     returned builder should produce.
     * @return The builder.
     */
    public static SpanBuilder mockSpanBuilder(final Span spanToCreate) {
        final SpanBuilder spanBuilder = mock(SpanBuilder.class, Mockito.RETURNS_SELF);
        when(spanBuilder.start()).thenReturn(spanToCreate);
        return spanBuilder;
    }
}
