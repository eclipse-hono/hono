/**
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.mockito.Mockito;

import io.opentracing.Span;
import io.opentracing.Tracer;

/**
 * Helper class that provides mocks for objects that are needed by other tests.
 *
 */
public final class HonoUnitTestHelper {

    private HonoUnitTestHelper() {
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
    public static Tracer.SpanBuilder mockSpanBuilder(final Span spanToCreate) {
        final Tracer.SpanBuilder spanBuilder = mock(Tracer.SpanBuilder.class, Mockito.RETURNS_SMART_NULLS);
        when(spanBuilder.addReference(anyString(), any())).thenReturn(spanBuilder);
        when(spanBuilder.withTag(anyString(), anyBoolean())).thenReturn(spanBuilder);
        when(spanBuilder.withTag(anyString(), anyString())).thenReturn(spanBuilder);
        when(spanBuilder.withTag(anyString(), (Number) any())).thenReturn(spanBuilder);
        when(spanBuilder.ignoreActiveSpan()).thenReturn(spanBuilder);
        when(spanBuilder.start()).thenReturn(spanToCreate);
        return spanBuilder;
    }
}
