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

package org.eclipse.hono.adapter.mqtt.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.eclipse.hono.adapter.mqtt.MqttContext;
import org.eclipse.hono.adapter.mqtt.MqttProtocolAdapterProperties;
import org.eclipse.hono.auth.Device;
import org.eclipse.hono.config.MapperEndpoint;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.EventConstants;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.ResourceIdentifier;
import org.eclipse.hono.util.TelemetryConstants;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import io.netty.handler.codec.mqtt.MqttQoS;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.impl.headers.VertxHttpHeaders;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.mqtt.MqttEndpoint;
import io.vertx.mqtt.messages.MqttPublishMessage;

/**
 * Verifies behavior of {@link HttpBasedMessageMapping}.
 */
@ExtendWith(VertxExtension.class)
public class HttpBasedMessageMappingTest {
    /**
     * A tenant identifier used for testing.
     */
    private static final String TEST_TENANT_ID = Constants.DEFAULT_TENANT;
    /**
     * A device used for testing.
     */
    private static final String TEST_DEVICE = "test-device";

    private MqttProtocolAdapterProperties config;
    private WebClient mapperWebClient;

    private static MqttContext newContext(final MqttPublishMessage message, final Device authenticatedDevice) {
        return MqttContext.fromPublishPacket(message, mock(MqttEndpoint.class), authenticatedDevice);
    }

    private static MqttPublishMessage newMessage(final MqttQoS qosLevel, final String topic) {
        return newMessage(qosLevel, topic, Buffer.buffer("test"));
    }

    private static MqttPublishMessage newMessage(final MqttQoS qosLevel, final String topic, final Buffer payload) {
        final MqttPublishMessage message = mock(MqttPublishMessage.class);
        when(message.qosLevel()).thenReturn(qosLevel);
        when(message.topicName()).thenReturn(topic);
        when(message.payload()).thenReturn(payload);
        return message;
    }

    private void givenAnAdapterWithMapper() {
        mapperWebClient = mock(WebClient.class);
        config = new MqttProtocolAdapterProperties();
    }

    /**
     * Verifies the updated payload and deviceId is overwritten in the messageMapper.
     *
     * @param ctx The helper to use for running tests on vert.x.
     */
    @Test
    public void testMapperShouldUpdatePayloadAndDeviceId(final VertxTestContext ctx) {
        givenAnAdapterWithMapper();
        final HttpBasedMessageMapping messageMapping = new HttpBasedMessageMapping(mapperWebClient, config);

        final ResourceIdentifier targetAddress = ResourceIdentifier.from(TelemetryConstants.TELEMETRY_ENDPOINT, TEST_TENANT_ID, TEST_DEVICE);
        final HttpRequest httpRequest = mock(HttpRequest.class);
        when(mapperWebClient.post(any(int.class), any(String.class), any(String.class))).thenReturn(httpRequest);
        when(httpRequest.putHeaders(any(MultiMap.class))).thenReturn(httpRequest);
        when(httpRequest.ssl(any(Boolean.class))).thenReturn(httpRequest);
        final AsyncResult asyncResult = mock(AsyncResult.class);
        when(asyncResult.succeeded()).thenReturn(true);
        final HttpResponse httpResponse = mock(HttpResponse.class);
        when(asyncResult.result()).thenReturn(httpResponse);
        final VertxHttpHeaders headers = new VertxHttpHeaders();
        final String newDeviceId = "new-device";
        headers.add(MessageHelper.APP_PROPERTY_DEVICE_ID, newDeviceId);
        when(httpResponse.headers()).thenReturn(headers);
        final Buffer changedBuffer = Buffer.buffer("changed");
        when(httpResponse.bodyAsBuffer()).thenReturn(changedBuffer);
        when(httpResponse.statusCode()).thenReturn(200);

        final MqttPublishMessage message = newMessage(MqttQoS.AT_LEAST_ONCE, EventConstants.EVENT_ENDPOINT);
        final MqttContext context = newContext(message, null);
        final MapperEndpoint mapperEndpoint = MapperEndpoint.from("host", 1234, "/uri", false);
        messageMapping.mapMessageRequest(context, targetAddress, message, mapperEndpoint, headers).onComplete(ctx.succeeding(mappedMessage -> {
            ctx.verify(() -> assertThat(mappedMessage.getResource().getResourceId()).isEqualTo("new-device"));
            ctx.verify(() -> assertThat(mappedMessage.getMessage().payload()).isEqualTo(changedBuffer));
        }));

        final ArgumentCaptor<Handler<AsyncResult<HttpResponse<Buffer>>>> captor =
                ArgumentCaptor.forClass(Handler.class);
        Mockito.verify(httpRequest).sendBuffer(any(Buffer.class), captor.capture());
        final Handler<AsyncResult<HttpResponse<Buffer>>> handler = captor.getValue();

        handler.handle(asyncResult);
        ctx.completeNow();
    }

}
