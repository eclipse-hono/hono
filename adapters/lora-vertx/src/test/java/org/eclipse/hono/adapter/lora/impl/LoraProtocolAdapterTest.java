/*******************************************************************************
 * Copyright (c) 2019, 2020 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.adapter.lora.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.eclipse.hono.adapter.lora.LoraConstants;
import org.eclipse.hono.adapter.lora.LoraMessage;
import org.eclipse.hono.adapter.lora.LoraMessageType;
import org.eclipse.hono.adapter.lora.LoraMetaData;
import org.eclipse.hono.adapter.lora.LoraProtocolAdapterProperties;
import org.eclipse.hono.adapter.lora.UplinkLoraMessage;
import org.eclipse.hono.adapter.lora.providers.LoraProvider;
import org.eclipse.hono.adapter.lora.providers.LoraProviderMalformedPayloadException;
import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.service.auth.DeviceUser;
import org.eclipse.hono.service.http.HttpContext;
import org.eclipse.hono.service.http.TracingHandler;
import org.eclipse.hono.service.test.ProtocolAdapterTestSupport;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.QoS;
import org.eclipse.hono.util.RegistrationAssertion;
import org.eclipse.hono.util.TenantObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.Tracer.SpanBuilder;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

/**
 * Verifies behavior of {@link LoraProtocolAdapter}.
 */
public class LoraProtocolAdapterTest extends ProtocolAdapterTestSupport<LoraProtocolAdapterProperties, LoraProtocolAdapter> {

    private static final int TEST_FUNCTION_PORT = 2;
    private static final String TEST_TENANT_ID = "myTenant";
    private static final String TEST_GATEWAY_ID = "myLoraGateway";
    private static final String TEST_DEVICE_ID = "0102030405060708";
    private static final byte[] TEST_PAYLOAD = "bumxlux".getBytes(StandardCharsets.UTF_8);
    private static final String TEST_PROVIDER = "bumlux";

    private Tracer tracer;
    private Span currentSpan;

    /**
     * Sets up the fixture.
     */
    @BeforeEach
    public void setUp() {

        this.properties = givenDefaultConfigurationProperties();
        createClientFactories();
        prepareClients();

        currentSpan = mock(Span.class);
        when(currentSpan.context()).thenReturn(mock(SpanContext.class));

        final SpanBuilder spanBuilder = mock(SpanBuilder.class, withSettings().defaultAnswer(RETURNS_SELF));
        when(spanBuilder.start()).thenReturn(currentSpan);

        tracer = mock(Tracer.class);
        when(tracer.buildSpan(anyString())).thenReturn(spanBuilder);

        adapter = new LoraProtocolAdapter();
        adapter.setConfig(properties);
        adapter.setTracer(tracer);
        setServiceClients(adapter);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected LoraProtocolAdapterProperties givenDefaultConfigurationProperties() {
        return new LoraProtocolAdapterProperties();
    }

    /**
     * Verifies that an uplink message is routed to a provider correctly.
     */
    @Test
    public void handleProviderRouteSuccessfullyForUplinkMessage() {

        givenATelemetrySenderForAnyTenant();

        final LoraProvider providerMock = getLoraProviderMock();
        final HttpContext httpContext = newHttpContext();
        final HttpServerRequest request = mock(HttpServerRequest.class);
        when(request.getHeader(eq(Constants.HEADER_QOS_LEVEL))).thenReturn(null);
        when(httpContext.request()).thenReturn(request);

        adapter.handleProviderRoute(httpContext, providerMock);

        verify(httpContext.getRoutingContext()).put(LoraConstants.APP_PROPERTY_ORIG_LORA_PROVIDER, TEST_PROVIDER);

        @SuppressWarnings("unchecked")
        final ArgumentCaptor<Map<String, Object>> props = ArgumentCaptor.forClass(Map.class);
        verify(telemetrySender).sendTelemetry(
                any(TenantObject.class),
                any(RegistrationAssertion.class),
                any(QoS.class),
                eq(LoraConstants.CONTENT_TYPE_LORA_BASE + TEST_PROVIDER),
                eq(Buffer.buffer(TEST_PAYLOAD)),
                props.capture(),
                any());
        assertThat(props.getValue()).contains(Map.entry(LoraConstants.APP_PROPERTY_FUNCTION_PORT, TEST_FUNCTION_PORT));
        final String metaData = (String) props.getValue().get(LoraConstants.APP_PROPERTY_META_DATA);
        assertThat(metaData).isNotNull();
        final JsonObject metaDataJson = new JsonObject(metaData);
        assertThat(metaDataJson.getInteger(LoraConstants.APP_PROPERTY_FUNCTION_PORT)).isEqualTo(TEST_FUNCTION_PORT);
        verify(httpContext.getRoutingContext().response()).setStatusCode(HttpResponseStatus.ACCEPTED.code());
        verify(currentSpan).finish();
    }

    /**
     * Verifies that an options request is routed to a provider correctly.
     */
    @Test
    public void handleProviderRouteSuccessfullyForOptionsRequest() {
        final HttpContext httpContext = newHttpContext();

        adapter.handleOptionsRoute(httpContext.getRoutingContext());

        verify(httpContext.getRoutingContext().response()).setStatusCode(HttpResponseStatus.OK.code());
        verify(currentSpan).finish();
    }

    /**
     * Verifies that the provider route discards join messages.
     */
    @Test
    public void handleProviderRouteDiscardsJoinMessages() {

        givenATelemetrySenderForAnyTenant();

        final LoraMessage message = mock(LoraMessage.class);
        when(message.getType()).thenReturn(LoraMessageType.JOIN);
        final LoraProvider providerMock = getLoraProviderMock(message);
        final HttpContext httpContext = newHttpContext();

        adapter.handleProviderRoute(httpContext, providerMock);

        verify(httpContext.getRoutingContext()).put(LoraConstants.APP_PROPERTY_ORIG_LORA_PROVIDER, TEST_PROVIDER);
        assertNoTelemetryMessageHasBeenSentDownstream();
        verify(httpContext.getRoutingContext().response()).setStatusCode(HttpResponseStatus.ACCEPTED.code());
        verify(currentSpan).finish();
    }

    /**
     * Verifies that the provider route discards downlink messages.
     */
    @Test
    public void handleProviderRouteDiscardsDownlinkMessages() {

        givenATelemetrySenderForAnyTenant();

        final LoraMessage message = mock(LoraMessage.class);
        when(message.getType()).thenReturn(LoraMessageType.DOWNLINK);
        final LoraProvider providerMock = getLoraProviderMock(message);
        final HttpContext httpContext = newHttpContext();

        adapter.handleProviderRoute(httpContext, providerMock);

        verify(httpContext.getRoutingContext()).put(LoraConstants.APP_PROPERTY_ORIG_LORA_PROVIDER, TEST_PROVIDER);
        assertNoTelemetryMessageHasBeenSentDownstream();
        verify(httpContext.response()).setStatusCode(HttpResponseStatus.ACCEPTED.code());
        verify(currentSpan).finish();
    }

    /**
     * Verifies that the provider route discards other messages.
     */
    @Test
    public void handleProviderRouteDiscardsOtherMessages() {

        givenATelemetrySenderForAnyTenant();

        final LoraMessage message = mock(LoraMessage.class);
        when(message.getType()).thenReturn(LoraMessageType.UNKNOWN);
        final LoraProvider providerMock = getLoraProviderMock(message);
        final HttpContext httpContext = newHttpContext();

        adapter.handleProviderRoute(httpContext, providerMock);

        verify(httpContext.getRoutingContext()).put(LoraConstants.APP_PROPERTY_ORIG_LORA_PROVIDER, TEST_PROVIDER);
        assertNoTelemetryMessageHasBeenSentDownstream();
        verify(httpContext.response()).setStatusCode(HttpResponseStatus.ACCEPTED.code());
        verify(currentSpan).finish();
    }

    /**
     * Verifies that the provider route rejects invalid gateway credentials with unauthorized.
     */
    @Test
    public void handleProviderRouteCausesUnauthorizedForInvalidGatewayCredentials() {

        givenATelemetrySenderForAnyTenant();

        final LoraProvider providerMock = getLoraProviderMock();
        final HttpContext httpContext = newHttpContext();
        when(httpContext.getRoutingContext().user()).thenReturn(null);

        adapter.handleProviderRoute(httpContext, providerMock);

        verify(httpContext.getRoutingContext()).put(LoraConstants.APP_PROPERTY_ORIG_LORA_PROVIDER, TEST_PROVIDER);
        assertNoTelemetryMessageHasBeenSentDownstream();
        verifyUnauthorized(httpContext.getRoutingContext());
        verify(currentSpan).finish();
    }

    /**
     * Verifies that the provider route rejects a request if the request body cannot
     * be parsed.
     */
    @Test
    public void handleProviderRouteCausesBadRequestForFailureToParseBody() {

        givenATelemetrySenderForAnyTenant();

        final LoraProvider providerMock = getLoraProviderMock();
        when(providerMock.getMessage(any(RoutingContext.class))).thenThrow(new LoraProviderMalformedPayloadException("no device ID"));
        final HttpContext httpContext = newHttpContext();

        adapter.handleProviderRoute(httpContext, providerMock);

        verify(httpContext.getRoutingContext()).put(LoraConstants.APP_PROPERTY_ORIG_LORA_PROVIDER, TEST_PROVIDER);
        assertNoTelemetryMessageHasBeenSentDownstream();
        verifyBadRequest(httpContext.getRoutingContext());
        verify(currentSpan).finish();
    }

    /**
     * Verifies that the provider name is added to the message when using customized downstream message.
     */
    @Test
    public void customizeDownstreamMessageAddsProviderNameToMessage() {

        final HttpContext httpContext = newHttpContext();
        final Map<String, Object> props = new HashMap<>();

        adapter.customizeDownstreamMessageProperties(props, httpContext);

        assertThat(props).contains(Map.entry(LoraConstants.APP_PROPERTY_ORIG_LORA_PROVIDER, TEST_PROVIDER));
    }

    private LoraProvider getLoraProviderMock() {
        final UplinkLoraMessage message = new UplinkLoraMessage(TEST_DEVICE_ID);
        message.setPayload(Buffer.buffer(TEST_PAYLOAD));
        return getLoraProviderMock(message);
    }

    private LoraProvider getLoraProviderMock(final LoraMessage message) {
        final LoraProvider provider = mock(LoraProvider.class);
        when(provider.getProviderName()).thenReturn(TEST_PROVIDER);
        when(provider.pathPrefixes()).thenReturn(Set.of("/bumlux"));
        when(provider.getMessage(any(RoutingContext.class))).thenReturn(message);

        return provider;
    }

    private HttpContext newHttpContext() {

        final LoraMetaData metaData = new LoraMetaData();
        metaData.setFunctionPort(TEST_FUNCTION_PORT);

        final HttpServerRequest request = mock(HttpServerRequest.class);
        when(request.uri()).thenReturn("/lora");

        final RoutingContext context = mock(RoutingContext.class);
        when(context.getBody()).thenReturn(Buffer.buffer());
        when(context.user()).thenReturn(new DeviceUser(TEST_TENANT_ID, TEST_GATEWAY_ID));
        when(context.request()).thenReturn(request);
        when(context.response()).thenReturn(mock(HttpServerResponse.class));
        when(context.get(LoraConstants.APP_PROPERTY_ORIG_LORA_PROVIDER)).thenReturn(TEST_PROVIDER);
        when(context.get(LoraConstants.APP_PROPERTY_META_DATA)).thenReturn(metaData);
        final Span parentSpan = mock(Span.class);
        when(parentSpan.context()).thenReturn(mock(SpanContext.class));
        when(context.get(TracingHandler.CURRENT_SPAN)).thenReturn(parentSpan);

        return HttpContext.from(context);
    }

    private void verifyUnauthorized(final RoutingContext routingContextMock) {
        verifyErrorCode(routingContextMock, HttpResponseStatus.UNAUTHORIZED);
    }

    private void verifyBadRequest(final RoutingContext routingContextMock) {
        verifyErrorCode(routingContextMock, HttpResponseStatus.BAD_REQUEST);
    }

    private void verifyErrorCode(final RoutingContext routingContextMock, final HttpResponseStatus expectedStatusCode) {
        final ArgumentCaptor<ClientErrorException> failCaptor = ArgumentCaptor.forClass(ClientErrorException.class);
        verify(routingContextMock).fail(failCaptor.capture());
        assertEquals(expectedStatusCode.code(), failCaptor.getValue().getErrorCode());
    }
}
