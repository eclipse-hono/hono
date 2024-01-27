/*******************************************************************************
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
 *******************************************************************************/

package org.eclipse.hono.adapter.mqtt.impl;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import static com.google.common.truth.Truth.assertThat;

import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.eclipse.hono.adapter.MapperEndpoint;
import org.eclipse.hono.adapter.mqtt.MqttContext;
import org.eclipse.hono.adapter.mqtt.MqttProtocolAdapterProperties;
import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.ServerErrorException;
import org.eclipse.hono.client.command.Command;
import org.eclipse.hono.service.auth.DeviceUser;
import org.eclipse.hono.test.TracingMockSupport;
import org.eclipse.hono.test.VertxMockSupport;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.RegistrationAssertion;
import org.eclipse.hono.util.TelemetryConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;

import io.netty.handler.codec.mqtt.MqttQoS;
import io.opentracing.Span;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpHeaders;
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

        span = TracingMockSupport.mockSpan();
    }

    /**
     * Verifies that the result returned by the mapping service contains the
     * original payload and target address if no downstream mapper has been defined for
     * the gateway.
     *
     * @param ctx The helper to use for running tests on vert.x.
     */
    @Test
    public void testMapMessageSucceedsIfNoMapperIsSet(final VertxTestContext ctx) {

        config.setMapperEndpoints(Map.of("mapper", MapperEndpoint.from("host", 1234, "/uri", false)));
        final MqttPublishMessage message = newMessage(MqttQoS.AT_LEAST_ONCE, TelemetryConstants.TELEMETRY_ENDPOINT);
        final String deviceId = "gateway";
        final MqttContext context = newContext(message, span, new DeviceUser(TEST_TENANT_ID, deviceId));

        messageMapping.mapDownstreamMessage(context, TEST_TENANT_ID, new RegistrationAssertion(deviceId))
            .onComplete(ctx.succeeding(mappedMessage -> {
                ctx.verify(() -> {
                    assertThat(mappedMessage.getTargetDeviceId()).isEqualTo(deviceId);
                    assertThat(mappedMessage.getPayload()).isEqualTo(message.payload());
                    assertThat(mappedMessage.getAdditionalProperties()).isEmpty();
                    verify(mapperWebClient, never()).post(anyInt(), anyString(), anyString());
                });
                ctx.completeNow();
            }));
    }

    /**
     * Verifies that the result returned by the mapping service contains the
     * original payload and target address if no downstream mapper endpoint has been configured
     * for the adapter.
     *
     * @param ctx The helper to use for running tests on vert.x.
     */
    @Test
    public void testMapMessageSucceedsIfNoMapperEndpointIsConfigured(final VertxTestContext ctx) {

        final MqttPublishMessage message = newMessage(MqttQoS.AT_LEAST_ONCE, TelemetryConstants.TELEMETRY_ENDPOINT);
        final String deviceId = "gateway";
        final MqttContext context = newContext(message, span, new DeviceUser(TEST_TENANT_ID, deviceId));

        final RegistrationAssertion assertion = new RegistrationAssertion(deviceId).setDownstreamMessageMapper("mapper");
        messageMapping.mapDownstreamMessage(context, TEST_TENANT_ID, assertion)
            .onComplete(ctx.succeeding(mappedMessage -> {
                ctx.verify(() -> {
                    assertThat(mappedMessage.getTargetDeviceId()).isEqualTo(deviceId);
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
        final String newDeviceId = "new-device";

        final HttpRequest<Buffer> httpRequest = mock(HttpRequest.class, withSettings().defaultAnswer(RETURNS_SELF));

        final MultiMap responseHeaders = MultiMap.caseInsensitiveMultiMap();
        responseHeaders.add(MessageHelper.APP_PROPERTY_DEVICE_ID, newDeviceId);
        responseHeaders.add("foo", "bar");
        final Buffer responseBody = Buffer.buffer("changed");

        final HttpResponse<Buffer> httpResponse = mock(HttpResponse.class);
        when(httpResponse.headers()).thenReturn(responseHeaders);
        when(httpResponse.bodyAsBuffer()).thenReturn(responseBody);
        when(httpResponse.statusCode()).thenReturn(HttpURLConnection.HTTP_OK);

        when(mapperWebClient.post(anyInt(), anyString(), anyString())).thenReturn(httpRequest);

        final String topic = String.format(
                "%s/?content-type=%s",
                TelemetryConstants.TELEMETRY_ENDPOINT,
                URLEncoder.encode("text/plain", StandardCharsets.UTF_8));

        final MqttPublishMessage message = newMessage(MqttQoS.AT_LEAST_ONCE, topic);
        final MqttContext context = newContext(message, span, new DeviceUser(TEST_TENANT_ID, "gateway"));

        final RegistrationAssertion assertion = new RegistrationAssertion("gateway").setDownstreamMessageMapper("mapper");
        messageMapping.mapDownstreamMessage(context, TEST_TENANT_ID, assertion)
            .onComplete(ctx.succeeding(mappedMessage -> {
                ctx.verify(() -> {
                    assertThat(mappedMessage.getTargetDeviceId()).isEqualTo("new-device");
                    assertThat(mappedMessage.getPayload()).isEqualTo(responseBody);
                    assertThat(mappedMessage.getAdditionalProperties()).doesNotContainKey(MessageHelper.APP_PROPERTY_DEVICE_ID);
                    assertThat(mappedMessage.getAdditionalProperties()).containsEntry("foo", "bar");
                });
                ctx.completeNow();
            }));

        final ArgumentCaptor<Handler<AsyncResult<HttpResponse<Buffer>>>> handleCaptor = VertxMockSupport.argumentCaptorHandler();
        verify(httpRequest).sendBuffer(any(Buffer.class), handleCaptor.capture());
        handleCaptor.getValue().handle(Future.succeededFuture(httpResponse));

        final ArgumentCaptor<MultiMap> headersCaptor = ArgumentCaptor.forClass(MultiMap.class);
        verify(httpRequest).putHeaders(headersCaptor.capture());
        final MultiMap addedHeaders = headersCaptor.getValue();

        assertThat(addedHeaders.contains(MessageHelper.APP_PROPERTY_ORIG_ADDRESS, topic, false)).isTrue();
        assertThat(addedHeaders.contains(HttpHeaders.CONTENT_TYPE.toString(), "text/plain", false)).isTrue();
    }

    /**
     * Verifies that the downstream mapper returns a failed future with a ServerErrorException if the downstream mapper has been configured
     * for an adapter but the remote service returns a 403 status code indicating that the device payload cannot be mapped.
     *
     * @param ctx   The Vert.x test context.
     */
    @Test
    @SuppressWarnings("unchecked")
    public void testMappingFailsForWhenPayloadCannotMapped(final VertxTestContext ctx) {

        config.setMapperEndpoints(Map.of("mapper", MapperEndpoint.from("host", 1234, "/uri", false)));

        final HttpRequest<Buffer> httpRequest = mock(HttpRequest.class, withSettings().defaultAnswer(RETURNS_SELF));

        final HttpResponse<Buffer> httpResponse = mock(HttpResponse.class);
        when(httpResponse.statusCode()).thenReturn(HttpURLConnection.HTTP_FORBIDDEN);

        when(mapperWebClient.post(anyInt(), anyString(), anyString())).thenReturn(httpRequest);
        final MqttPublishMessage message = newMessage(MqttQoS.AT_LEAST_ONCE, "mqtt-topic");
        final MqttContext context = newContext(message, span, new DeviceUser(TEST_TENANT_ID, "gateway"));

        final RegistrationAssertion assertion = new RegistrationAssertion("gateway").setDownstreamMessageMapper("mapper");
        messageMapping.mapDownstreamMessage(context, TEST_TENANT_ID, assertion)
            .onComplete(ctx.failing(t -> {
                ctx.verify(() -> {
                    assertThat(t).isInstanceOf(ServerErrorException.class);
                    assertThat((((ServerErrorException) t).getErrorCode())).isEqualTo(HttpURLConnection.HTTP_UNAVAILABLE);
                });
                ctx.completeNow();
            }));

        final ArgumentCaptor<Handler<AsyncResult<HttpResponse<Buffer>>>> handlerCaptor = VertxMockSupport.argumentCaptorHandler();
        verify(httpRequest).sendBuffer(any(Buffer.class), handlerCaptor.capture());
        handlerCaptor.getValue().handle(Future.succeededFuture(httpResponse));
    }

    private static MqttContext newContext(final MqttPublishMessage message, final Span span, final DeviceUser authenticatedDevice) {
        final MqttContext mqttContext = MqttContext.fromPublishPacket(message, mock(MqttEndpoint.class), span, authenticatedDevice);
        return mqttContext;
    }

    private static MqttPublishMessage newMessage(
            final MqttQoS qosLevel,
            final String topic) {
        return newMessage(qosLevel, topic, Buffer.buffer("test"));
    }

    private static MqttPublishMessage newMessage(
            final MqttQoS qosLevel,
            final String topic,
            final Buffer payload) {

        final MqttPublishMessage message = mock(MqttPublishMessage.class);
        when(message.qosLevel()).thenReturn(qosLevel);
        when(message.payload()).thenReturn(payload);
        when(message.topicName()).thenReturn(topic);
        return message;
    }

    /**
     * Verifies that the result returned by the mapping service contains the
     * original payload and target address if no upstream mapper has been defined for
     * the gateway.
     *
     * @param ctx The helper to use for running tests on vert.x.
     */
    @Test
    public void testMapCommandSucceedsIfNoMapperIsSet(final VertxTestContext ctx) {
        config.setMapperEndpoints(Map.of("mapper", MapperEndpoint.from("host", 1234, "/uri", false)));
        final Command command = mock(Command.class);
        final Buffer payload = Buffer.buffer("payload");
        when(command.getPayload()).thenReturn(payload);

        messageMapping.mapUpstreamMessage(new RegistrationAssertion("gateway"), command)
            .onComplete(ctx.succeeding(mappedBuffer -> {
                ctx.verify(() -> {
                    assertThat(mappedBuffer).isEqualTo(payload);
                    verify(mapperWebClient, never()).post(anyInt(), anyString(), anyString());
                });
                ctx.completeNow();
            }));
    }

    /**
     * Verifies that the result returned by the mapping service contains the
     * original payload and target address if no upstream mapper endpoint has been configured
     * for the adapter.
     *
     * @param ctx The helper to use for running tests on vert.x.
     */
    @Test
    public void testMapCommandSucceedsIfNoMapperEndpointIsConfigured(final VertxTestContext ctx) {
        final RegistrationAssertion assertion = new RegistrationAssertion("gateway").setUpstreamMessageMapper("mapper");
        final Command command = mock(Command.class);
        final Buffer payload = Buffer.buffer("payload");
        when(command.getPayload()).thenReturn(payload);

        messageMapping.mapUpstreamMessage(assertion, command)
            .onComplete(ctx.succeeding(mappedBuffer -> {
                ctx.verify(() -> {
                    assertThat(mappedBuffer).isEqualTo(payload);
                    verify(mapperWebClient, never()).post(anyInt(), anyString(), anyString());
                });
                ctx.completeNow();
            }));
    }

    /**
     * Verifies that the result returned by the upstream mapping service contains the
     * mapped payload.
     *
     * @param ctx The helper to use for running tests on vert.x.
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testMapCommandSucceeds(final VertxTestContext ctx) {

        config.setMapperEndpoints(Map.of("mapper", MapperEndpoint.from("host", 1234, "/uri", false)));
        final HttpRequest<Buffer> httpRequest = mock(HttpRequest.class, withSettings().defaultAnswer(RETURNS_SELF));

        final Buffer payload = Buffer.buffer("payload");
        final Buffer responseBody = Buffer.buffer("changed");

        final HttpResponse<Buffer> httpResponse = mock(HttpResponse.class);
        when(httpResponse.bodyAsBuffer()).thenReturn(responseBody);
        when(httpResponse.statusCode()).thenReturn(HttpURLConnection.HTTP_OK);

        when(mapperWebClient.post(anyInt(), anyString(), anyString())).thenReturn(httpRequest);

        final Command command = mock(Command.class);
        when(command.getPayload()).thenReturn(payload);

        final RegistrationAssertion assertion = new RegistrationAssertion("gateway").setUpstreamMessageMapper("mapper");
        messageMapping.mapUpstreamMessage(assertion, command)
            .onComplete(ctx.succeeding(mappedBuffer -> {
                ctx.verify(() -> {
                    assertThat(mappedBuffer).isEqualTo(responseBody);
                    verify(mapperWebClient, times(1)).post(anyInt(), anyString(), anyString());
                });
                ctx.completeNow();
            }));

        final ArgumentCaptor<Handler<AsyncResult<HttpResponse<Buffer>>>> handleCaptor = VertxMockSupport.argumentCaptorHandler();
        verify(httpRequest).sendBuffer(any(Buffer.class), handleCaptor.capture());
        handleCaptor.getValue().handle(Future.succeededFuture(httpResponse));
    }

    /**
     * Verifies that the upstream mapper returns a failed future with a ClientErrorException if the upstream mapper has been configured
     * for an adapter but the remote service returns a 403 status code indicating that the device payload cannot be mapped.
     *
     * @param ctx   The Vert.x test context.
     */
    @Test
    @SuppressWarnings("unchecked")
    public void testMappingCommandFailsForWhenPayloadCannotMapped(final VertxTestContext ctx) {

        config.setMapperEndpoints(Map.of("mapper", MapperEndpoint.from("host", 1234, "/uri", false)));
        final HttpRequest<Buffer> httpRequest = mock(HttpRequest.class, withSettings().defaultAnswer(RETURNS_SELF));

        final Buffer payload = Buffer.buffer("payload");

        final HttpResponse<Buffer> httpResponse = mock(HttpResponse.class);
        when(httpResponse.statusCode()).thenReturn(HttpURLConnection.HTTP_FORBIDDEN);

        when(mapperWebClient.post(anyInt(), anyString(), anyString())).thenReturn(httpRequest);

        final Command command = mock(Command.class);
        when(command.getPayload()).thenReturn(payload);

        final RegistrationAssertion assertion = new RegistrationAssertion("gateway").setUpstreamMessageMapper("mapper");
        messageMapping.mapUpstreamMessage(assertion, command)
            .onComplete(ctx.failing(t -> {
                ctx.verify(() -> {
                    assertThat(t).isInstanceOf(ClientErrorException.class);
                    assertThat((((ClientErrorException) t).getErrorCode())).isEqualTo(HttpURLConnection.HTTP_FORBIDDEN);
                });
                ctx.completeNow();
            }));

        final ArgumentCaptor<Handler<AsyncResult<HttpResponse<Buffer>>>> handleCaptor = VertxMockSupport.argumentCaptorHandler();
        verify(httpRequest).sendBuffer(any(Buffer.class), handleCaptor.capture());
        handleCaptor.getValue().handle(Future.succeededFuture(httpResponse));
    }

    /**
     * Verifies that the upstream mapper returns a failed future with a ServerErrorException if the upstream mapper has been configured
     * for an adapter but the remote service cannot be reached should return a 503.
     *
     * @param ctx   The Vert.x test context.
     */
    @Test
    @SuppressWarnings("unchecked")
    public void testMappingCommandFailsForWhenMapperCannotBeReached(final VertxTestContext ctx) {

        config.setMapperEndpoints(Map.of("mapper", MapperEndpoint.from("host", 1234, "/uri", false)));
        final HttpRequest<Buffer> httpRequest = mock(HttpRequest.class, withSettings().defaultAnswer(RETURNS_SELF));

        final Buffer payload = Buffer.buffer("payload");

        when(mapperWebClient.post(anyInt(), anyString(), anyString())).thenReturn(httpRequest);

        final Command command = mock(Command.class);
        when(command.getPayload()).thenReturn(payload);

        final RegistrationAssertion assertion = new RegistrationAssertion("gateway").setUpstreamMessageMapper("mapper");
        messageMapping.mapUpstreamMessage(assertion, command)
                .onComplete(ctx.failing(t -> {
                    ctx.verify(() -> {
                        assertThat(t).isInstanceOf(ServerErrorException.class);
                        assertThat((((ServerErrorException) t).getErrorCode())).isEqualTo(HttpURLConnection.HTTP_UNAVAILABLE);
                    });
                    ctx.completeNow();
                }));

        final ArgumentCaptor<Handler<AsyncResult<HttpResponse<Buffer>>>> handleCaptor = VertxMockSupport.argumentCaptorHandler();
        verify(httpRequest).sendBuffer(any(Buffer.class), handleCaptor.capture());
        handleCaptor.getValue().handle(Future.failedFuture(new RuntimeException("something went wrong")));
    }
}
