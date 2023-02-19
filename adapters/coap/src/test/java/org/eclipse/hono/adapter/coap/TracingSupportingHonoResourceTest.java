/**
 * Copyright (c) 2020, 2023 Contributors to the Eclipse Foundation
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
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.stream.Stream;

import org.eclipse.californium.core.coap.CoAP;
import org.eclipse.californium.core.coap.CoAP.Code;
import org.eclipse.californium.core.coap.Request;
import org.eclipse.californium.core.coap.Response;
import org.eclipse.californium.core.network.Endpoint;
import org.eclipse.californium.core.network.Exchange;
import org.eclipse.californium.core.network.Exchange.Origin;
import org.eclipse.californium.core.server.resources.CoapExchange;
import org.eclipse.hono.client.registry.TenantClient;
import org.eclipse.hono.service.auth.DeviceUser;
import org.eclipse.hono.util.TenantObject;
import org.eclipse.hono.util.TenantTracingConfig;
import org.eclipse.hono.util.TracingSamplingMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import io.opentracing.References;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.Tracer.SpanBuilder;
import io.opentracing.tag.Tags;
import io.vertx.core.Future;

/**
 * Tests verifying behavior of {@code TracingSupportingHonoResource}.
 *
 */
public class TracingSupportingHonoResourceTest {

    private static final String TENANT_ID = "tenant";
    private static final String DEVICE_ID = "4711";
    private static final String AUTHENTICATED_DEVICE_ID = "4712";
    private static final String AUTH_ID = "authId";
    private static final String SPAN_NAME = "test";

    private Tracer tracer;
    private Span span;
    private SpanBuilder spanBuilder;
    private TracingSupportingHonoResource resource;
    private TenantClient tenantClient;
    private Endpoint endpoint;
    private CoapProtocolAdapter adapter;

    private static Stream<Code> supportedRequestCodes() {
        return Stream.of(Code.POST, Code.PUT);
    }

    private static Stream<Code> unsupportedRequestCodes() {
        return Stream.of(Code.CUSTOM_30, Code.DELETE, Code.FETCH, Code.GET, Code.IPATCH, Code.PATCH);
    }

    /**
     * Sets up the fixture.
     */
    @BeforeEach
    public void setUp() {
        span = mock(Span.class);
        spanBuilder = mock(SpanBuilder.class, RETURNS_SELF);
        when(spanBuilder.start()).thenReturn(span);
        tracer = mock(Tracer.class);
        when(tracer.buildSpan(anyString())).thenReturn(spanBuilder);

        tenantClient = mock(TenantClient.class);
        when(tenantClient.get(anyString(), (SpanContext) any())).thenAnswer(invocation -> {
            return Future.succeededFuture(TenantObject.from(invocation.getArgument(0), true));
        });

        endpoint = mock(Endpoint.class);
        adapter = mock(CoapProtocolAdapter.class);
        when(adapter.getTenantClient()).thenReturn(tenantClient);
        when(adapter.getTypeName()).thenReturn("adapter");

        resource = new TracingSupportingHonoResource(adapter, tracer, "test") {

            @Override
            protected String getSpanName(final Exchange exchange) {
                return SPAN_NAME;
            }

            @Override
            protected Future<CoapContext> createCoapContextForPost(final CoapExchange coapExchange, final Span span) {
                return Future.succeededFuture(createCoapContext(coapExchange, span));
            }

            @Override
            protected Future<CoapContext> createCoapContextForPut(final CoapExchange coapExchange, final Span span) {
                return Future.succeededFuture(createCoapContext(coapExchange, span));
            }

            private CoapContext createCoapContext(final CoapExchange coapExchange, final Span span) {
                return CoapContext.fromRequest(coapExchange, new DeviceUser(TENANT_ID, DEVICE_ID),
                        new DeviceUser(TENANT_ID, AUTHENTICATED_DEVICE_ID), AUTH_ID, span);
            }
        };
    }

    private Exchange newExchange(final Request request) {
        final Object identity = "dummy";
        final Exchange exchange = new Exchange(request, identity, Origin.REMOTE, (cmd) -> {
            cmd.run();
        });
        exchange.setEndpoint(endpoint);
        doAnswer(invocation -> {
            exchange.execute(() -> {
                exchange.setResponse(invocation.getArgument(1));
            });
            return null;
        }).when(endpoint).sendResponse(eq(exchange), any(Response.class));
        return exchange;
    }

    /**
     * Verifies that the resource sets the trace sampling priority on the newly created Span if the CoAP request
     * belongs to a tenant for which a specific sampling priority is configured.
     */
    @Test
    public void testApplyTenantTraceSamplingPriority() {

        final TenantObject tenantObject = TenantObject.from(TENANT_ID, true);
        final TenantTracingConfig tracingConfig = new TenantTracingConfig();
        tracingConfig.setSamplingMode(TracingSamplingMode.NONE);
        tenantObject.setTracingConfig(tracingConfig);
        when(tenantClient.get(anyString(), (SpanContext) any())).thenReturn(Future.succeededFuture(tenantObject));

        final Request request = new Request(Code.POST);
        final Exchange exchange = newExchange(request);
        resource.handleRequest(exchange);

        verify(tracer).buildSpan(eq(SPAN_NAME));
        verify(spanBuilder).withTag(eq(Tags.SPAN_KIND.getKey()), eq(Tags.SPAN_KIND_SERVER));
        verify(spanBuilder).addReference(eq(References.CHILD_OF), isNull());
        // verify sampling prio has been set to 0 (corresponding to TracingSamplingMode.NONE)
        verify(span).setTag(eq(Tags.SAMPLING_PRIORITY.getKey()), eq(0));
        verify(span).setBaggageItem(eq(Tags.SAMPLING_PRIORITY.getKey()), eq("0"));
    }

    /**
     * Verifies that the resource sets the trace sampling priority on the newly created Span if the CoAP request
     * belongs to a tenant and an auth-id for which a specific sampling priority is configured.
     */
    @Test
    public void testApplyTenantTraceSamplingPrioritySetForAuthId() {

        final TenantObject tenantObject = TenantObject.from(TENANT_ID, true);
        final TenantTracingConfig tracingConfig = new TenantTracingConfig();
        tracingConfig.setSamplingModePerAuthId(Map.of(AUTH_ID, TracingSamplingMode.ALL));
        tenantObject.setTracingConfig(tracingConfig);
        when(tenantClient.get(anyString(), (SpanContext) any())).thenReturn(Future.succeededFuture(tenantObject));

        final Request request = new Request(Code.POST);
        final Exchange exchange = newExchange(request);
        resource.handleRequest(exchange);

        verify(tracer).buildSpan(eq(SPAN_NAME));
        verify(spanBuilder).withTag(eq(Tags.SPAN_KIND.getKey()), eq(Tags.SPAN_KIND_SERVER));
        verify(spanBuilder).addReference(eq(References.CHILD_OF), isNull());
        // verify sampling prio has been set to 1 (corresponding to TracingSamplingMode.ALL)
        verify(span).setTag(eq(Tags.SAMPLING_PRIORITY.getKey()), eq(1));
        verify(span).setBaggageItem(eq(Tags.SAMPLING_PRIORITY.getKey()), eq("1"));
    }

    /**
     * Verifies that the resource returns a 4.05 for request codes other than PUT or POST.
     *
     * @param requestCode The CoAP request code to verify.
     */
    @ParameterizedTest
    @MethodSource("unsupportedRequestCodes")
    public void testUnsupportedRequestCodesResultInMethodNotAllowed(final Code requestCode) {

        final Request request = new Request(requestCode);
        final Exchange exchange = newExchange(request);
        resource.handleRequest(exchange);
        verify(endpoint).sendResponse(eq(exchange), argThat(response -> response.getCode() == CoAP.ResponseCode.METHOD_NOT_ALLOWED));
        verify(span).setTag(eq(CoapConstants.TAG_COAP_RESPONSE_CODE.getKey()), eq("4.05"));
    }

    /**
     * Verifies that the resource returns a 5.01 for request codes PUT and POST.
     *
     * @param requestCode The CoAP request code to verify.
     */
    @ParameterizedTest
    @MethodSource("supportedRequestCodes")
    public void testDefaultHandlersResultInNotImplemented(final Code requestCode) {

        final Request request = new Request(requestCode);
        final Exchange exchange = newExchange(request);
        resource.handleRequest(exchange);
        verify(endpoint).sendResponse(eq(exchange), argThat(response -> response.getCode() == CoAP.ResponseCode.NOT_IMPLEMENTED));
        verify(span).setTag(eq(CoapConstants.TAG_COAP_RESPONSE_CODE.getKey()), eq("5.01"));
    }
}
