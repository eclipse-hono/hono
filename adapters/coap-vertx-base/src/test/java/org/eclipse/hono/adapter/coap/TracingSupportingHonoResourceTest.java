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


package org.eclipse.hono.adapter.coap;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.Executor;

import org.eclipse.californium.core.coap.CoAP.Code;
import org.eclipse.californium.core.coap.CoAP.ResponseCode;
import org.eclipse.californium.core.coap.Request;
import org.eclipse.californium.core.network.Exchange;
import org.eclipse.californium.core.network.Exchange.Origin;
import org.eclipse.californium.core.server.resources.CoapExchange;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.opentracing.References;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.Tracer.SpanBuilder;
import io.opentracing.propagation.Format;
import io.opentracing.tag.Tags;
import io.vertx.core.Future;


/**
 * Tests verifying behavior of {@code TracingSupportingHonoResource}.
 *
 */
public class TracingSupportingHonoResourceTest {

    private Tracer tracer;
    private SpanBuilder spanBuilder;
    private TracingSupportingHonoResource resource;

    /**
     * Sets up the fixture.
     */
    @BeforeEach
    public void setUp() {
        final Span span = mock(Span.class);
        spanBuilder = mock(SpanBuilder.class, RETURNS_SELF);
        when(spanBuilder.start()).thenReturn(span);
        tracer = mock(Tracer.class);
        when(tracer.buildSpan(anyString())).thenReturn(spanBuilder);
    }

    /**
     * Verifies that the resource extracts the trace context from a CoAP request.
     */
    @Test
    public void testExtractTraceContext() {

        final SpanContext extractedContext = mock(SpanContext.class);
        when(tracer.extract(eq(Format.Builtin.BINARY_EXTRACT), any(CoapOptionInjectExtractAdapter.class))).thenReturn(extractedContext);

        resource = new TracingSupportingHonoResource(tracer, "test", "adapter") {
            /**
             * {@inheritDoc}
             */
            @Override
            protected Future<ResponseCode> handlePost(final CoapExchange exchange, final Span currentSpan) {
                return Future.succeededFuture(ResponseCode.CHANGED);
            }
        };

        final Request request = new Request(Code.POST);
        final Exchange exchange = new Exchange(request, Origin.REMOTE, mock(Executor.class));
        resource.handleRequest(exchange);

        verify(tracer).buildSpan(eq(Code.POST.toString()));
        verify(spanBuilder).withTag(eq(Tags.SPAN_KIND.getKey()), eq(Tags.SPAN_KIND_SERVER.toString()));
        verify(spanBuilder).addReference(eq(References.CHILD_OF), eq(extractedContext));
    }
}
