/*******************************************************************************
 * Copyright (c) 2016, 2019 Contributors to the Eclipse Foundation
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

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.adapter.http.HttpProtocolAdapterProperties;
import org.eclipse.hono.adapter.lora.LoraConstants;
import org.eclipse.hono.adapter.lora.LoraMessageType;
import org.eclipse.hono.adapter.lora.impl.LoraProtocolAdapter;
import org.eclipse.hono.adapter.lora.providers.LoraProvider;
import org.eclipse.hono.adapter.lora.providers.LoraProviderMalformedPayloadException;
import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.service.auth.DeviceUser;
import org.eclipse.hono.service.http.HttpUtils;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;

/**
 * Verifies behavior of {@link LoraProtocolAdapter}.
 */
public class LoraProtocolAdapterTest {

    private static final String TEST_TENANT_ID = "myTenant";
    private static final String TEST_GATEWAY_ID = "myLoraGateway";
    private static final String TEST_DEVICE_ID = "myLoraDevice";
    private static final String TEST_PAYLOAD = "bumxlux";
    private static final String TEST_PROVIDER = "bumlux";

    private LoraProtocolAdapter adapter;

    /**
     * Sets up the fixture.
     */
    @Before
    public void setUp() {
        this.adapter = spy(LoraProtocolAdapter.class);

        doAnswer(answer -> {
            final RoutingContext ctx = answer.getArgument(0);
            ctx.response().setStatusCode(HttpResponseStatus.ACCEPTED.code());
            return null;
        }).when(this.adapter).uploadTelemetryMessage(any(), any(), any(), any(), any());

        when(this.adapter.getConfig()).thenReturn(new HttpProtocolAdapterProperties());
    }

    /**
     * Verifies that an uplink message is routed to a provider correctly.
     */
    @Test
    public void handleProviderRouteSuccessfullyForUplinkMessage() {
        final LoraProvider providerMock = getLoraProviderMock();
        final RoutingContext routingContextMock = getRoutingContextMock();

        adapter.handleProviderRoute(routingContextMock, providerMock);

        verify(routingContextMock).put(LoraConstants.APP_PROPERTY_ORIG_LORA_PROVIDER, TEST_PROVIDER);

        verify(adapter).uploadTelemetryMessage(any(), eq(TEST_TENANT_ID), eq(TEST_DEVICE_ID),
                argThat(buffer -> buffer.toJsonObject().getString("payload").equals(TEST_PAYLOAD)),
                eq(HttpUtils.CONTENT_TYPE_JSON));

        verify(routingContextMock.response()).setStatusCode(HttpResponseStatus.ACCEPTED.code());
    }

    /**
     * Verifies that an options request is routed to a provider correctly.
     */
    @Test
    public void handleProviderRouteSuccessfullyForOptionsRequest() {
        final RoutingContext routingContextMock = getRoutingContextMock();

        adapter.handleOptionsRoute(routingContextMock);

        verify(routingContextMock.response()).setStatusCode(HttpResponseStatus.OK.code());
    }

    /**
     * Verifies that the provider route discards join messages.
     */
    @Test
    public void handleProviderRouteDiscardsJoinMessages() {
        final LoraProvider providerMock = getLoraProviderMock();
        when(providerMock.extractMessageType(any())).thenReturn(LoraMessageType.JOIN);
        final RoutingContext routingContextMock = getRoutingContextMock();

        adapter.handleProviderRoute(routingContextMock, providerMock);

        verify(routingContextMock).put(LoraConstants.APP_PROPERTY_ORIG_LORA_PROVIDER, TEST_PROVIDER);
        verify(adapter, never()).uploadTelemetryMessage(any(), any(), any(), any(), any());
        verify(routingContextMock.response()).setStatusCode(HttpResponseStatus.ACCEPTED.code());
    }

    /**
     * Verifies that the provider route discards downlink messages.
     */
    @Test
    public void handleProviderRouteDiscardsDownlinkMessages() {
        final LoraProvider providerMock = getLoraProviderMock();
        when(providerMock.extractMessageType(any())).thenReturn(LoraMessageType.DOWNLINK);
        final RoutingContext routingContextMock = getRoutingContextMock();

        adapter.handleProviderRoute(routingContextMock, providerMock);

        verify(routingContextMock).put(LoraConstants.APP_PROPERTY_ORIG_LORA_PROVIDER, TEST_PROVIDER);
        verify(adapter, never()).uploadTelemetryMessage(any(), any(), any(), any(), any());
        verify(routingContextMock.response()).setStatusCode(HttpResponseStatus.ACCEPTED.code());
    }

    /**
     * Verifies that the provider route discards other messages.
     */
    @Test
    public void handleProviderRouteDiscardsOtherMessages() {
        final LoraProvider providerMock = getLoraProviderMock();
        when(providerMock.extractMessageType(any())).thenReturn(LoraMessageType.UNKNOWN);
        final RoutingContext routingContextMock = getRoutingContextMock();

        adapter.handleProviderRoute(routingContextMock, providerMock);

        verify(routingContextMock).put(LoraConstants.APP_PROPERTY_ORIG_LORA_PROVIDER, TEST_PROVIDER);
        verify(adapter, never()).uploadTelemetryMessage(any(), any(), any(), any(), any());
        verify(routingContextMock.response()).setStatusCode(HttpResponseStatus.ACCEPTED.code());
    }

    /**
     * Verifies that the provider route rejects invalid gateway credentials with unauthorized.
     */
    @Test
    public void handleProviderRouteCausesUnauthorizedForInvalidGatewayCredentials() {
        final LoraProvider providerMock = getLoraProviderMock();
        final RoutingContext routingContextMock = getRoutingContextMock();
        when(routingContextMock.user()).thenReturn(null);

        adapter.handleProviderRoute(routingContextMock, providerMock);

        verify(routingContextMock).put(LoraConstants.APP_PROPERTY_ORIG_LORA_PROVIDER, TEST_PROVIDER);
        verify(adapter, never()).uploadTelemetryMessage(any(), any(), any(), any(), any());
        verifyUnauthorized(routingContextMock);
    }

    /**
     * Verifies that the provider route rejects a missing device id with bad request.
     */
    @Test
    public void handleProviderRouteCausesBadRequestForMissingDeviceId() {
        final LoraProvider providerMock = getLoraProviderMock();
        when(providerMock.extractDeviceId(any())).thenReturn(null);
        final RoutingContext routingContextMock = getRoutingContextMock();

        adapter.handleProviderRoute(routingContextMock, providerMock);

        verify(routingContextMock).put(LoraConstants.APP_PROPERTY_ORIG_LORA_PROVIDER, TEST_PROVIDER);
        verify(adapter, never()).uploadTelemetryMessage(any(), any(), any(), any(), any());
        verifyBadRequest(routingContextMock);
    }

    /**
     * Verifies that the provider route rejects a missing payload with bad request.
     */
    @Test
    public void handleProviderRouteCausesBadRequestForMissingPayload() {
        final LoraProvider providerMock = getLoraProviderMock();
        when(providerMock.extractPayloadEncodedInBase64(any())).thenReturn(null);
        final RoutingContext routingContextMock = getRoutingContextMock();

        adapter.handleProviderRoute(routingContextMock, providerMock);

        verify(routingContextMock).put(LoraConstants.APP_PROPERTY_ORIG_LORA_PROVIDER, TEST_PROVIDER);
        verify(adapter, never()).uploadTelemetryMessage(any(), any(), any(), any(), any());
        verifyBadRequest(routingContextMock);
    }

    /**
     * Verifies that the provider route rejects an invalid payload with bad request.
     */
    @Test
    public void handleProviderRouteCausesBadRequestForInvalidPayload() {
        final LoraProvider providerMock = getLoraProviderMock();
        when(providerMock.extractPayloadEncodedInBase64(any())).thenThrow(ClassCastException.class);
        final RoutingContext routingContextMock = getRoutingContextMock();

        adapter.handleProviderRoute(routingContextMock, providerMock);
        verify(routingContextMock).put(LoraConstants.APP_PROPERTY_ORIG_LORA_PROVIDER, TEST_PROVIDER);
        verify(adapter, never()).uploadTelemetryMessage(any(), any(), any(), any(), any());
        verifyBadRequest(routingContextMock);
    }

    /**
     * Verifies that the provider route rejects a malformed payload with bad request.
     */
    @Test
    public void handleProviderRouteCausesBadRequestInCaseOfPayloadTransformationException() {
        final LoraProvider providerMock = getLoraProviderMock();
        when(providerMock.extractPayloadEncodedInBase64(any())).thenThrow(LoraProviderMalformedPayloadException.class);
        final RoutingContext routingContextMock = getRoutingContextMock();

        adapter.handleProviderRoute(routingContextMock, providerMock);

        verify(routingContextMock).put(LoraConstants.APP_PROPERTY_ORIG_LORA_PROVIDER, TEST_PROVIDER);
        verify(adapter, never()).uploadTelemetryMessage(any(), any(), any(), any(), any());
        verifyBadRequest(routingContextMock);
    }

    /**
     * Verifies that the provider name is added to the message when using customized downstream message.
     */
    @Test
    public void customizeDownstreamMessageAddsProviderNameToMessage() {
        final RoutingContext routingContextMock = getRoutingContextMock();
        final Message messageMock = mock(Message.class);

        adapter.customizeDownstreamMessage(messageMock, routingContextMock);

        verify(messageMock).setApplicationProperties(argThat(properties -> TEST_PROVIDER
                .equals(properties.getValue().get(LoraConstants.APP_PROPERTY_ORIG_LORA_PROVIDER))));
    }

    private LoraProvider getLoraProviderMock() {
        final LoraProvider provider = mock(LoraProvider.class);
        when(provider.getProviderName()).thenReturn(TEST_PROVIDER);
        when(provider.pathPrefix()).thenReturn("/bumlux");
        when(provider.extractMessageType(any())).thenReturn(LoraMessageType.UPLINK);
        when(provider.extractDeviceId(any())).thenReturn(TEST_DEVICE_ID);
        when(provider.extractPayloadEncodedInBase64(any())).thenReturn(TEST_PAYLOAD);

        return provider;
    }

    private RoutingContext getRoutingContextMock() {
        final RoutingContext context = mock(RoutingContext.class);
        when(context.user()).thenReturn(new DeviceUser(TEST_TENANT_ID, TEST_GATEWAY_ID));
        when(context.response()).thenReturn(mock(HttpServerResponse.class));
        when(context.get(LoraConstants.APP_PROPERTY_ORIG_LORA_PROVIDER)).thenReturn(TEST_PROVIDER);

        return context;
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
