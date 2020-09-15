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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import java.util.Map;

import org.eclipse.hono.adapter.mqtt.MqttContext;
import org.eclipse.hono.adapter.mqtt.MqttProtocolAdapterProperties;
import org.eclipse.hono.auth.Device;
import org.eclipse.hono.config.MapperEndpoint;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.RegistrationConstants;
import org.eclipse.hono.util.ResourceIdentifier;
import org.eclipse.hono.util.TelemetryConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;

import io.netty.handler.codec.mqtt.MqttQoS;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
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

    private MqttProtocolAdapterProperties config;
    private WebClient mapperWebClient;
    private HttpBasedMessageMapping messageMapping;
    private Span span;

    /**
     * Sets up the fixture.
     */
    @BeforeEach
    public void setUp() {
        mapperWebClient = mock(WebClient.class);
        config = new MqttProtocolAdapterProperties();
        messageMapping = new HttpBasedMessageMapping(mapperWebClient, config);

        span = mock(Span.class);
        final SpanContext spanContext = mock(SpanContext.class);
        when(span.context()).thenReturn(spanContext);
    }

    /**
     * Verifies that the result returned by the mapping service contains the
     * original payload and target address if no mapper has been defined for
     * the gateway.
     *
     * @param ctx The helper to use for running tests on vert.x.
     */
    @Test
    public void testMapMessageSucceedsIfNoMapperIsSet(final VertxTestContext ctx) {

        config.setMapperEndpoints(Map.of("mapper", MapperEndpoint.from("host", 1234, "/uri", false)));
        final ResourceIdentifier targetAddress = ResourceIdentifier.from(TelemetryConstants.TELEMETRY_ENDPOINT, TEST_TENANT_ID, "gateway");
        final MqttPublishMessage message = newMessage(MqttQoS.AT_LEAST_ONCE, TelemetryConstants.TELEMETRY_ENDPOINT);
        final MqttContext context = newContext(message, span, new Device(TEST_TENANT_ID, "gateway"));

        messageMapping.mapMessage(context, targetAddress, new JsonObject())
            .onComplete(ctx.succeeding(mappedMessage -> {
                ctx.verify(() -> {
                    assertThat(mappedMessage.getTargetAddress()).isEqualTo(targetAddress);
                    assertThat(mappedMessage.getPayload()).isEqualTo(message.payload());
                    assertThat(mappedMessage.getAdditionalProperties()).isEmpty();
                    verify(mapperWebClient, never()).post(anyInt(), anyString(), anyString());
                });
                ctx.completeNow();
            }));
    }

    /**
     * Verifies that the result returned by the mapping service contains the
     * original payload and target address if no mapper endpoint has been configured
     * for the adapter.
     *
     * @param ctx The helper to use for running tests on vert.x.
     */
    @Test
    public void testMapMessageSucceedsIfNoMapperEndpointIsConfigured(final VertxTestContext ctx) {

        final ResourceIdentifier targetAddress = ResourceIdentifier.from(TelemetryConstants.TELEMETRY_ENDPOINT, TEST_TENANT_ID, "gateway");
        final MqttPublishMessage message = newMessage(MqttQoS.AT_LEAST_ONCE, TelemetryConstants.TELEMETRY_ENDPOINT);
        final MqttContext context = newContext(message, span, new Device(TEST_TENANT_ID, "gateway"));

        messageMapping.mapMessage(context, targetAddress, new JsonObject().put(RegistrationConstants.FIELD_MAPPER, "mapper"))
            .onComplete(ctx.succeeding(mappedMessage -> {
                ctx.verify(() -> {
                    assertThat(mappedMessage.getTargetAddress()).isEqualTo(targetAddress);
                    assertThat(mappedMessage.getPayload()).isEqualTo(message.payload());
                    assertThat(mappedMessage.getAdditionalProperties()).isEmpty();
                    verify(mapperWebClient, never()).post(anyInt(), anyString(), anyString());
                });
                ctx.completeNow();
            }));
    }

    /**
     * Verifies that the result returned by the mapping service contains the
     * mapped payload, device ID and additional properties.
     *
     * @param ctx The helper to use for running tests on vert.x.
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testMapMessageSucceeds(final VertxTestContext ctx) {

        config.setMapperEndpoints(Map.of("mapper", MapperEndpoint.from("host", 1234, "/uri", false)));
        final ResourceIdentifier targetAddress = ResourceIdentifier.from(TelemetryConstants.TELEMETRY_ENDPOINT, TEST_TENANT_ID, "gateway");
        final String newDeviceId = "new-device";

        final HttpRequest<Buffer> httpRequest = mock(HttpRequest.class, withSettings().defaultAnswer(RETURNS_SELF));

        final MultiMap responseHeaders = MultiMap.caseInsensitiveMultiMap();
        responseHeaders.add(MessageHelper.APP_PROPERTY_DEVICE_ID, newDeviceId);
        responseHeaders.add("foo", "bar");
        final Buffer responseBody = Buffer.buffer("changed");

        final HttpResponse<Buffer> httpResponse = mock(HttpResponse.class);
        when(httpResponse.headers()).thenReturn(responseHeaders);
        when(httpResponse.bodyAsBuffer()).thenReturn(responseBody);
        when(httpResponse.statusCode()).thenReturn(200);

        when(mapperWebClient.post(anyInt(), anyString(), anyString())).thenReturn(httpRequest);

        final MqttPublishMessage message = newMessage(MqttQoS.AT_LEAST_ONCE, TelemetryConstants.TELEMETRY_ENDPOINT);
        final MqttContext context = newContext(message, span, new Device(TEST_TENANT_ID, "gateway"));

        messageMapping.mapMessage(context, targetAddress, new JsonObject().put(RegistrationConstants.FIELD_MAPPER, "mapper"))
            .onComplete(ctx.succeeding(mappedMessage -> {
                ctx.verify(() -> {
                    assertThat(mappedMessage.getTargetAddress().getResourceId()).isEqualTo("new-device");
                    assertThat(mappedMessage.getPayload()).isEqualTo(responseBody);
                    assertThat(mappedMessage.getAdditionalProperties()).doesNotContainKey(MessageHelper.APP_PROPERTY_DEVICE_ID);
                    assertThat(mappedMessage.getAdditionalProperties()).containsEntry("foo", "bar");
                });
                ctx.completeNow();
            }));

        final ArgumentCaptor<Handler<AsyncResult<HttpResponse<Buffer>>>> captor = ArgumentCaptor.forClass(Handler.class);
        verify(httpRequest).sendBuffer(any(Buffer.class), captor.capture());
        captor.getValue().handle(Future.succeededFuture(httpResponse));
    }

    private static MqttContext newContext(final MqttPublishMessage message, final Span span, final Device authenticatedDevice) {
        return MqttContext.fromPublishPacket(message, mock(MqttEndpoint.class), span, authenticatedDevice);
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
}
