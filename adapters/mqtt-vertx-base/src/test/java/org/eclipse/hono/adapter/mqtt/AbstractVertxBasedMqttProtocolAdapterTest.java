/*******************************************************************************
 * Copyright (c) 2016, 2021 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.adapter.mqtt;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static com.google.common.truth.Truth.assertThat;

import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.OptionalInt;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

import javax.net.ssl.SSLSession;

import org.eclipse.hono.adapter.auth.device.AuthHandler;
import org.eclipse.hono.adapter.resourcelimits.ResourceLimitChecks;
import org.eclipse.hono.adapter.test.ProtocolAdapterTestSupport;
import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.ServerErrorException;
import org.eclipse.hono.client.command.CommandConsumer;
import org.eclipse.hono.client.command.CommandResponse;
import org.eclipse.hono.client.command.CommandResponseSender;
import org.eclipse.hono.client.command.Commands;
import org.eclipse.hono.service.auth.DeviceUser;
import org.eclipse.hono.service.http.HttpUtils;
import org.eclipse.hono.service.metric.MetricsTags;
import org.eclipse.hono.service.metric.MetricsTags.ConnectionAttemptOutcome;
import org.eclipse.hono.service.metric.MetricsTags.EndpointType;
import org.eclipse.hono.test.TracingMockSupport;
import org.eclipse.hono.test.VertxMockSupport;
import org.eclipse.hono.util.Adapter;
import org.eclipse.hono.util.CommandConstants;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.MessagingType;
import org.eclipse.hono.util.QoS;
import org.eclipse.hono.util.RegistrationAssertion;
import org.eclipse.hono.util.ResourceIdentifier;
import org.eclipse.hono.util.TenantObject;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;

import io.netty.handler.codec.mqtt.MqttConnectReturnCode;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.mqtt.MqttAuth;
import io.vertx.mqtt.MqttEndpoint;
import io.vertx.mqtt.MqttServer;
import io.vertx.mqtt.MqttTopicSubscription;
import io.vertx.mqtt.messages.MqttPublishMessage;
import io.vertx.mqtt.messages.MqttSubscribeMessage;

/**
 * Verifies behavior of {@link AbstractVertxBasedMqttProtocolAdapter}.
 *
 */
@ExtendWith(VertxExtension.class)
@Timeout(value = 10, timeUnit = TimeUnit.SECONDS)
public class AbstractVertxBasedMqttProtocolAdapterTest extends
    ProtocolAdapterTestSupport<MqttProtocolAdapterProperties, AbstractVertxBasedMqttProtocolAdapter<MqttProtocolAdapterProperties>> {

    private static final String ADAPTER_TYPE = "mqtt";

    private static Vertx vertx;

    private AuthHandler<MqttConnectContext> authHandler;
    private ResourceLimitChecks resourceLimitChecks;
    private MqttAdapterMetrics metrics;
    private Context context;
    private Span span;
    private MqttServer server;

    /**
     * Initializes vert.x.
     */
    @BeforeAll
    public static void init() {
        vertx = Vertx.vertx();
    }

    /**
     * Creates clients for the needed micro services and sets the configuration to enable the insecure port.
     */
    @BeforeEach
    @SuppressWarnings("unchecked")
    public void setup() {

        context = VertxMockSupport.mockContext(vertx);

        span = TracingMockSupport.mockSpan();

        metrics = mock(MqttAdapterMetrics.class);

        this.properties = givenDefaultConfigurationProperties();
        createClients();
        prepareClients();

        authHandler = mock(AuthHandler.class);
        resourceLimitChecks = mock(ResourceLimitChecks.class);
        when(resourceLimitChecks.isConnectionLimitReached(any(TenantObject.class), any(SpanContext.class)))
                .thenReturn(Future.succeededFuture(Boolean.FALSE));
        when(resourceLimitChecks.isConnectionDurationLimitReached(any(TenantObject.class), any(SpanContext.class)))
                .thenReturn(Future.succeededFuture(Boolean.FALSE));
        when(resourceLimitChecks.isMessageLimitReached(any(TenantObject.class), anyLong(), any(SpanContext.class)))
                .thenReturn(Future.succeededFuture(Boolean.FALSE));
    }

    /**
     * Cleans up fixture.
     *
     * @param ctx The vert.x test context.
     */
    @AfterAll
    public static void shutDown(final VertxTestContext ctx) {
        vertx.close(ctx.succeedingThenComplete());
        vertx = null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected MqttProtocolAdapterProperties givenDefaultConfigurationProperties() {
        properties = new MqttProtocolAdapterProperties();
        properties.setInsecurePortEnabled(true);
        properties.setMaxConnections(Integer.MAX_VALUE);

        return properties;
    }

    private static MqttContext newMqttContext(final MqttPublishMessage message, final MqttEndpoint endpoint, final Span span) {
        return MqttContext.fromPublishPacket(message, endpoint, span);
    }

    private static MqttEndpoint mockEndpoint() {
        final MqttEndpoint endpoint = mock(MqttEndpoint.class);
        when(endpoint.clientIdentifier()).thenReturn("test-device");
        when(endpoint.subscribeHandler(VertxMockSupport.anyHandler())).thenReturn(endpoint);
        when(endpoint.unsubscribeHandler(VertxMockSupport.anyHandler())).thenReturn(endpoint);
        return endpoint;
    }

    /**
     * Verifies that an MQTT server is bound to the insecure port during startup and connections to required services
     * have been established.
     *
     * @param ctx The helper to use for running async tests on vertx.
     */
    @Test
    public void testStartup(final VertxTestContext ctx) {

        givenAnAdapter(properties);

        final Promise<Void> startupTracker = Promise.promise();
        adapter.start(startupTracker);
        startupTracker.future().onComplete(ctx.succeeding(s -> {
            ctx.verify(() -> {
                verify(server).listen(VertxMockSupport.anyHandler());
                verify(server).endpointHandler(VertxMockSupport.anyHandler());
            });
            ctx.completeNow();
        }));
    }

    // TODO: startup fail test

    /**
     * Verifies that a connection attempt from a device is refused if the adapter is not connected to all of the
     * services it depends on.
     */
    @Test
    public void testEndpointHandlerFailsWithoutDownstreamConnections() {

        // GIVEN an adapter that is not connected to
        // all of its required services
        givenAnAdapter(properties);
        when(tenantClient.get(anyString(), any())).thenReturn(Future.failedFuture(new ServerErrorException(HttpURLConnection.HTTP_UNAVAILABLE)));
        when(authHandler.authenticateDevice(any(MqttConnectContext.class)))
            .thenReturn(Future.succeededFuture(new DeviceUser(Constants.DEFAULT_TENANT, "4711")));

        // WHEN a client tries to connect
        final MqttEndpoint endpoint = getMqttEndpointAuthenticated();
        adapter.handleEndpointConnection(endpoint);

        // THEN the connection request is rejected
        verify(endpoint).reject(MqttConnectReturnCode.CONNECTION_REFUSED_SERVER_UNAVAILABLE);
    }

    /**
     * Verifies that an adapter that is configured to not require devices to authenticate, accepts connections from
     * devices not providing any credentials.
     */
    @Test
    public void testEndpointHandlerAcceptsUnauthenticatedDevices() {

        // GIVEN an adapter that does not require devices to authenticate
        properties.setAuthenticationRequired(false);
        givenAnAdapter(properties);

        // WHEN a device connects without providing credentials
        final MqttEndpoint endpoint = mockEndpoint();
        adapter.handleEndpointConnection(endpoint);

        // THEN the connection is established
        verify(endpoint).accept(false);
        verify(metrics).reportConnectionAttempt(ConnectionAttemptOutcome.SUCCEEDED, null, null);
    }

    /**
     * Verifies that an adapter rejects a connection attempt from a device that belongs to a tenant for which the
     * adapter is disabled.
     */
    @Test
    public void testEndpointHandlerRejectsDeviceOfDisabledTenant() {

        // GIVEN an adapter
        givenAnAdapter(properties);
        // which is disabled for tenant "my-tenant"
        final TenantObject myTenantConfig = TenantObject.from("my-tenant", true);
        myTenantConfig.addAdapter(new Adapter(ADAPTER_TYPE).setEnabled(Boolean.FALSE));
        when(tenantClient.get(eq("my-tenant"), (SpanContext) any())).thenReturn(Future.succeededFuture(myTenantConfig));
        when(authHandler.authenticateDevice(any(MqttConnectContext.class)))
                .thenReturn(Future.succeededFuture(new DeviceUser("my-tenant", "4711")));

        // WHEN a device of "my-tenant" tries to connect
        final MqttEndpoint endpoint = mockEndpoint();
        adapter.handleEndpointConnection(endpoint);

        // THEN the connection is not established
        verify(endpoint).reject(MqttConnectReturnCode.CONNECTION_REFUSED_NOT_AUTHORIZED);
        verify(metrics).reportConnectionAttempt(
                ConnectionAttemptOutcome.ADAPTER_DISABLED,
                "my-tenant",
                null);
    }

    /**
     * Verifies that an adapter that is configured to require devices to authenticate rejects connections from devices
     * which do not provide proper credentials.
     */
    @Test
    public void testEndpointHandlerRejectsUnauthenticatedDevices() {

        // GIVEN an adapter that does require devices to authenticate
        givenAnAdapter(properties);
        when(authHandler.authenticateDevice(any(MqttConnectContext.class))).thenReturn(
                Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_UNAUTHORIZED)));

        // WHEN a device connects without providing proper credentials
        final MqttEndpoint endpoint = mockEndpoint();
        adapter.handleEndpointConnection(endpoint);
        adapter.handleEndpointConnection(endpoint);
        adapter.handleEndpointConnection(endpoint);

        // THEN the connection is refused
        verify(authHandler, times(3)).authenticateDevice(any(MqttConnectContext.class));
        verify(endpoint, times(3)).reject(MqttConnectReturnCode.CONNECTION_REFUSED_BAD_USER_NAME_OR_PASSWORD);
    }

    /**
     * Verifies that an adapter retrieves credentials on record for a device connecting to the adapter.
     */
    @Test
    public void testEndpointHandlerTriesToAuthenticateDevice() {

        // GIVEN an adapter requiring devices to authenticate endpoint
        properties.setAuthenticationRequired(true);
        givenAnAdapter(properties);

        // WHEN a device tries to establish a connection
        when(authHandler.authenticateDevice(any(MqttConnectContext.class))).thenReturn(
                Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_UNAUTHORIZED)));
        final MqttEndpoint endpoint = getMqttEndpointAuthenticated();
        adapter.handleEndpointConnection(endpoint);

        // THEN the adapter has tried to authenticate the device
        verify(authHandler).authenticateDevice(any(MqttConnectContext.class));
    }

    /**
     * Verifies that an adapter rejects connections when the adapter's connection limit is exceeded.
     *
     * @param ctx The helper to use for running async tests on vertx.
     */
    @Test
    public void testEndpointHandlerRejectsConnectionsExceedingAdapterLimit(final VertxTestContext ctx) {

        // GIVEN an adapter with a connection limit of 1
        properties.setMaxConnections(1);
        givenAnAdapter(properties);

        final Promise<Void> startupTracker = Promise.promise();
        adapter.start(startupTracker);
        startupTracker.future().onComplete(ctx.succeeding(s -> {

            // which already has 1 connection open
            when(metrics.getNumberOfConnections()).thenReturn(1);

            // WHEN a device tries to establish a connection
            final MqttEndpoint endpoint = getMqttEndpointAuthenticated();
            adapter.handleEndpointConnection(endpoint);

            // THEN the connection is not established
            ctx.verify(() -> {
                verify(endpoint).reject(MqttConnectReturnCode.CONNECTION_REFUSED_SERVER_UNAVAILABLE);
                verify(metrics).reportConnectionAttempt(
                        ConnectionAttemptOutcome.ADAPTER_CONNECTIONS_EXCEEDED,
                        null,
                        "BUMLUX_CIPHER");
            });
            ctx.completeNow();
        }));
    }

    /**
     * Verifies that on successful authentication the adapter sets appropriate message and close handlers on the client
     * endpoint.
     */
    @Test
    public void testAuthenticatedMqttAdapterCreatesMessageHandlersForAuthenticatedDevices() {

        // GIVEN an adapter
        givenAnAdapter(properties);
        when(authHandler.authenticateDevice(any(MqttConnectContext.class)))
                .thenReturn(Future.succeededFuture(new DeviceUser(Constants.DEFAULT_TENANT, "4711")));

        // WHEN a device tries to connect with valid credentials
        final MqttEndpoint endpoint = getMqttEndpointAuthenticated();
        adapter.handleEndpointConnection(endpoint);

        // THEN the device's logical ID is successfully established and corresponding handlers
        // are registered
        verify(authHandler).authenticateDevice(any(MqttConnectContext.class));
        verify(endpoint).accept(false);
        verify(endpoint).publishHandler(VertxMockSupport.anyHandler());
        verify(endpoint, times(2)).closeHandler(VertxMockSupport.anyHandler());
        verify(metrics).reportConnectionAttempt(
                ConnectionAttemptOutcome.SUCCEEDED,
                Constants.DEFAULT_TENANT,
                "BUMLUX_CIPHER");
    }

    /**
     * Verifies that unregistered devices with valid credentials cannot establish connection.
     */
    @Test
    public void testAuthenticatedMqttAdapterRejectsConnectionForNonExistingDevice() {

        // GIVEN an adapter
        givenAnAdapter(properties);
        // which is connected to a Credentials service that has credentials on record for device 9999
        when(authHandler.authenticateDevice(any(MqttConnectContext.class)))
                .thenReturn(Future.succeededFuture(new DeviceUser(Constants.DEFAULT_TENANT, "9999")));
        // but for which no registration information is available
        when(registrationClient.assertRegistration(anyString(), eq("9999"), (String) any(), (SpanContext) any()))
                .thenReturn(Future.failedFuture(new ClientErrorException(
                        HttpURLConnection.HTTP_NOT_FOUND, "device unknown or disabled")));

        // WHEN a device tries to connect with valid credentials
        final MqttEndpoint endpoint = getMqttEndpointAuthenticated();
        adapter.handleEndpointConnection(endpoint);

        // THEN the device's credentials are verified successfully
        verify(authHandler).authenticateDevice(any(MqttConnectContext.class));
        // but the connection is refused
        verify(endpoint).reject(MqttConnectReturnCode.CONNECTION_REFUSED_NOT_AUTHORIZED);
        verify(metrics).reportConnectionAttempt(
                ConnectionAttemptOutcome.REGISTRATION_ASSERTION_FAILURE,
                Constants.DEFAULT_TENANT,
                "BUMLUX_CIPHER");
    }

    /**
     * Verifies that the adapter registers message handlers on client connections when device authentication is
     * disabled.
     */
    @Test
    public void testUnauthenticatedMqttAdapterCreatesMessageHandlersForAllDevices() {

        // GIVEN an adapter that does not require devices to authenticate
        properties.setAuthenticationRequired(false);
        givenAnAdapter(properties);

        // WHEN a device connects that does not provide any credentials
        final MqttEndpoint endpoint = mockEndpoint();
        adapter.handleEndpointConnection(endpoint);

        // THEN the connection is established and handlers are registered
        verify(authHandler, never()).authenticateDevice(any(MqttConnectContext.class));
        verify(endpoint).publishHandler(VertxMockSupport.anyHandler());
        verify(endpoint, times(2)).closeHandler(VertxMockSupport.anyHandler());
        verify(endpoint).accept(false);
    }

    /**
     * Verifies that the adapter discards messages that contain a malformed topic.
     */
    @Test
    public void testHandlePublishedMessageFailsForMalformedTopic() {

        // GIVEN an adapter
        givenAnAdapter(properties);

        // WHEN a device publishes a message with a malformed topic
        final MqttEndpoint endpoint = mockEndpoint();
        final Buffer payload = Buffer.buffer("hello");
        final MqttPublishMessage msg = mock(MqttPublishMessage.class);
        when(msg.qosLevel()).thenReturn(MqttQoS.AT_LEAST_ONCE);
        when(msg.topicName()).thenReturn(null);
        when(msg.payload()).thenReturn(payload);
        final var mqttDeviceEndpoint = adapter.getMqttDeviceEndpoint(endpoint, null, OptionalInt.empty());
        mqttDeviceEndpoint.handlePublishedMessage(msg);

        // THEN the message is not processed
        verify(metrics, never()).reportTelemetry(
                any(MetricsTags.EndpointType.class),
                anyString(),
                any(),
                eq(MetricsTags.ProcessingOutcome.FORWARDED),
                any(MetricsTags.QoS.class),
                anyInt(),
                any());
        // and no PUBACK is sent to the device
        verify(endpoint, never()).publishAcknowledge(anyInt());
    }

    /**
     * Verifies that the adapter does not forward a message published by a device if the topic is empty and closes the
     * connection to the device.
     */
    @Test
    public void testUploadTelemetryMessageFailsForEmptyTopic() {

        // GIVEN an adapter
        properties.setAuthenticationRequired(false);
        givenAnAdapter(properties);
        givenATelemetrySenderForAnyTenant();

        final MqttEndpoint endpoint = mockEndpoint();
        when(endpoint.isConnected()).thenReturn(Boolean.TRUE);
        adapter.handleEndpointConnection(endpoint);
        final ArgumentCaptor<Handler<MqttPublishMessage>> messageHandler = VertxMockSupport.argumentCaptorHandler();
        verify(endpoint).publishHandler(messageHandler.capture());

        // WHEN a device publishes a message that has an empty topic
        final MqttPublishMessage msg = mock(MqttPublishMessage.class);
        when(msg.topicName()).thenReturn("");
        when(msg.qosLevel()).thenReturn(MqttQoS.AT_MOST_ONCE);

        messageHandler.getValue().handle(msg);

        // THEN the device gets disconnected
        verify(endpoint).close();
        // and the message is not forwarded downstream
        assertNoTelemetryMessageHasBeenSentDownstream();
        // and the message has not been reported as processed
        verify(metrics, never()).reportTelemetry(
                any(MetricsTags.EndpointType.class),
                anyString(),
                any(),
                eq(MetricsTags.ProcessingOutcome.FORWARDED),
                any(MetricsTags.QoS.class),
                anyInt(),
                any());
    }

    /**
     * Verifies that the adapter does not forward a message published by a device if the topic is not set and closes the
     * connection to the device.
     */
    @Test
    public void testUploadTelemetryMessageFailsForMissingTopic() {

        // GIVEN an adapter
        properties.setAuthenticationRequired(false);
        givenAnAdapter(properties);
        givenATelemetrySenderForAnyTenant();

        final MqttEndpoint endpoint = mockEndpoint();
        when(endpoint.isConnected()).thenReturn(Boolean.TRUE);
        adapter.handleEndpointConnection(endpoint);
        final ArgumentCaptor<Handler<MqttPublishMessage>> messageHandler = VertxMockSupport.argumentCaptorHandler();
        verify(endpoint).publishHandler(messageHandler.capture());

        // WHEN a device publishes a message that has no topic
        final MqttPublishMessage msg = mock(MqttPublishMessage.class);
        when(msg.topicName()).thenReturn(null);
        when(msg.qosLevel()).thenReturn(MqttQoS.AT_MOST_ONCE);

        messageHandler.getValue().handle(msg);

        // THEN the device gets disconnected
        verify(endpoint).close();
        // and the message is not forwarded downstream
        assertNoTelemetryMessageHasBeenSentDownstream();
        // and the message has not been reported as processed
        verify(metrics, never()).reportTelemetry(
                any(MetricsTags.EndpointType.class),
                anyString(),
                any(),
                eq(MetricsTags.ProcessingOutcome.FORWARDED),
                any(MetricsTags.QoS.class),
                anyInt(),
                any());
    }

    /**
     * Verifies that the adapter does not forward a message published by a device if the device's registration status
     * cannot be asserted.
     */
    @Test
    public void testUploadTelemetryMessageFailsForUnknownDevice() {

        // GIVEN an adapter
        properties.setAuthenticationRequired(false);
        givenAnAdapter(properties);
        givenATelemetrySenderForAnyTenant();

        // WHEN an unknown device publishes a telemetry message
        when(registrationClient.assertRegistration(anyString(), eq("unknown"), any(), any())).thenReturn(
                Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_NOT_FOUND)));

        final MqttEndpoint endpoint = mockEndpoint();
        when(endpoint.isConnected()).thenReturn(Boolean.TRUE);
        adapter.handleEndpointConnection(endpoint);
        final ArgumentCaptor<Handler<MqttPublishMessage>> messageHandler = VertxMockSupport.argumentCaptorHandler();
        verify(endpoint).publishHandler(messageHandler.capture());

        final MqttPublishMessage msg = mock(MqttPublishMessage.class);
        when(msg.topicName()).thenReturn("t/my-tenant/unknown");
        when(msg.qosLevel()).thenReturn(MqttQoS.AT_MOST_ONCE);
        when(msg.payload()).thenReturn(Buffer.buffer("hello"));
        messageHandler.getValue().handle(msg);

        // THEN the message has not been sent downstream
        assertNoTelemetryMessageHasBeenSentDownstream();
        // and the message has not been reported as processed
        verify(metrics, never()).reportTelemetry(
                any(MetricsTags.EndpointType.class),
                anyString(),
                any(),
                eq(MetricsTags.ProcessingOutcome.FORWARDED),
                any(MetricsTags.QoS.class),
                anyInt(),
                any());
        // and the connection is closed
        verify(endpoint).close();
    }

    /**
     * Verifies that the adapter does not forward a message published by a device if the device belongs to a tenant for
     * which the adapter has been disabled and that the adapter closes the connection to the device.
     */
    @Test
    public void testUploadTelemetryMessageFailsForDisabledTenant() {

        // GIVEN an adapter
        properties.setAuthenticationRequired(false);
        givenAnAdapter(properties);
        givenATelemetrySenderForAnyTenant();
        // which is disabled for tenant "my-tenant"
        final TenantObject myTenantConfig = TenantObject.from("my-tenant", true);
        myTenantConfig.addAdapter(new Adapter(ADAPTER_TYPE).setEnabled(Boolean.FALSE));
        when(tenantClient.get(eq("my-tenant"), (SpanContext) any())).thenReturn(Future.succeededFuture(myTenantConfig));

        // WHEN a device of "my-tenant" publishes a telemetry message
        final MqttEndpoint endpoint = mockEndpoint();
        when(endpoint.isConnected()).thenReturn(Boolean.TRUE);
        adapter.handleEndpointConnection(endpoint);
        final ArgumentCaptor<Handler<MqttPublishMessage>> messageHandler = VertxMockSupport.argumentCaptorHandler();
        verify(endpoint).publishHandler(messageHandler.capture());

        final MqttPublishMessage msg = mock(MqttPublishMessage.class);
        when(msg.topicName()).thenReturn("t/my-tenant/the-device");
        when(msg.qosLevel()).thenReturn(MqttQoS.AT_LEAST_ONCE);
        when(msg.payload()).thenReturn(Buffer.buffer("hello"));
        messageHandler.getValue().handle(msg);

        // THEN the message has not been sent downstream
        assertNoTelemetryMessageHasBeenSentDownstream();
        verify(metrics, never()).reportTelemetry(
                any(MetricsTags.EndpointType.class),
                anyString(),
                any(),
                eq(MetricsTags.ProcessingOutcome.FORWARDED),
                any(MetricsTags.QoS.class),
                anyInt(),
                any());
        // and the connection to the client has been closed
        verify(endpoint).close();
    }

    /**
     * Verifies that the adapter waits for an event being settled and accepted by a downstream peer before sending a
     * PUBACK package to the device.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testUploadEventMessageSendsPubAckOnSuccess(final VertxTestContext ctx) {

        // GIVEN an adapter with a downstream event consumer
        givenAnAdapter(properties);
        final Promise<Void> outcome = Promise.promise();
        givenAnEventSenderForAnyTenant(outcome);
        testUploadQoS1MessageSendsPubAckOnSuccess(
                outcome,
                EndpointType.EVENT,
                (adapter, mqttContext) -> {
                    adapter.uploadEventMessage(mqttContext, "my-tenant", "4712", mqttContext.message().payload())
                            .onComplete(ctx.succeedingThenComplete());
                });
    }

    /**
     * Verifies that the adapter waits for a QoS 1 telemetry message being settled and accepted by a downstream peer
     * before sending a PUBACK package to the device.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testUploadTelemetryMessageSendsPubAckOnSuccess(final VertxTestContext ctx) {

        // GIVEN an adapter with a downstream telemetry consumer
        givenAnAdapter(properties);
        final Promise<Void> outcome = Promise.promise();
        givenATelemetrySenderForAnyTenant(outcome);

        testUploadQoS1MessageSendsPubAckOnSuccess(
                outcome,
                EndpointType.TELEMETRY,
                (adapter, mqttContext) -> {
                    // WHEN forwarding a telemetry message that has been published with QoS 1
                    adapter.uploadTelemetryMessage(mqttContext, "my-tenant", "4712", mqttContext.message().payload())
                            .onComplete(ctx.succeedingThenComplete());
                });
    }

    private void testUploadQoS1MessageSendsPubAckOnSuccess(
            final Promise<Void> outcome,
            final EndpointType type,
            final BiConsumer<AbstractVertxBasedMqttProtocolAdapter<?>, MqttContext> upload) {

        // WHEN a device publishes a message using QoS 1
        final MqttEndpoint endpoint = mockEndpoint();
        when(endpoint.isConnected()).thenReturn(Boolean.TRUE);
        final Buffer payload = Buffer.buffer("some payload");
        final MqttPublishMessage messageFromDevice = mock(MqttPublishMessage.class);
        when(messageFromDevice.qosLevel()).thenReturn(MqttQoS.AT_LEAST_ONCE);
        when(messageFromDevice.messageId()).thenReturn(5555555);
        when(messageFromDevice.payload()).thenReturn(payload);
        when(messageFromDevice.topicName()).thenReturn(String.format("%s/my-tenant/4712", type.getCanonicalName()));
        final MqttContext context = newMqttContext(messageFromDevice, endpoint, span);
        upload.accept(adapter, context);

        // THEN the device does not receive a PUBACK
        verify(endpoint, never()).publishAcknowledge(anyInt());
        // and the message has not been reported as forwarded
        verify(metrics, never()).reportTelemetry(
                any(MetricsTags.EndpointType.class),
                anyString(),
                any(),
                eq(MetricsTags.ProcessingOutcome.FORWARDED),
                any(MetricsTags.QoS.class),
                anyInt(),
                any());

        // until the message has been settled and accepted
        outcome.complete();
        verify(endpoint).publishAcknowledge(5555555);
        verify(metrics).reportTelemetry(
                eq(type),
                eq("my-tenant"),
                any(),
                eq(MetricsTags.ProcessingOutcome.FORWARDED),
                eq(MetricsTags.QoS.AT_LEAST_ONCE),
                eq(payload.length()),
                any());
    }

    /**
     * Verifies that the adapter accepts a command response message with an empty body.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testUploadEmptyCommandResponseSucceeds(final VertxTestContext ctx) {

        // GIVEN an adapter with a command response consumer
        final CommandResponseSender sender = givenACommandResponseSenderForAnyTenant();

        // WHEN forwarding a command response that has been published
        givenAnAdapter(properties);

        final MqttEndpoint endpoint = mockEndpoint();
        when(endpoint.isConnected()).thenReturn(Boolean.TRUE);
        final MqttPublishMessage messageFromDevice = mock(MqttPublishMessage.class);
        when(messageFromDevice.qosLevel()).thenReturn(MqttQoS.AT_MOST_ONCE);
        when(messageFromDevice.messageId()).thenReturn(5555555);
        when(messageFromDevice.topicName()).thenReturn("command/my-tenant/4712/res/1010f8ab0b53-bd96-4d99-9d9c-56b868474a6a/200");

        // ... with an empty payload
        when(messageFromDevice.payload()).thenReturn(null);
        final ResourceIdentifier address = ResourceIdentifier
                .fromString("command/my-tenant/4712/res/1010f8ab0b53-bd96-4d99-9d9c-56b868474a6a/200");
        adapter.uploadCommandResponseMessage(newMqttContext(messageFromDevice, endpoint, span), address)
                .onComplete(ctx.succeeding(result -> {
                    ctx.verify(() -> {
                        verify(sender).sendCommandResponse(
                                any(TenantObject.class),
                                any(RegistrationAssertion.class),
                                any(CommandResponse.class),
                                any());
                        // then it is forwarded successfully
                        verify(metrics).reportCommand(
                                eq(MetricsTags.Direction.RESPONSE),
                                eq("my-tenant"),
                                any(TenantObject.class),
                                eq(MetricsTags.ProcessingOutcome.FORWARDED),
                                eq(0),
                                any());
                        ctx.completeNow();
                    });
                }));
    }

    /**
     * Verifies that the adapter does not send a PUBACK packet to the device if an event message has not been accepted
     * by the peer.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testOnUnauthenticatedMessageDoesNotSendPubAckOnFailure(final VertxTestContext ctx) {

        // GIVEN an adapter with a downstream event consumer
        givenAnAdapter(properties);
        final Promise<Void> outcome = Promise.promise();
        givenAnEventSenderForAnyTenant(outcome);

        // WHEN a device publishes an event
        final Buffer payload = Buffer.buffer("some payload");
        final MqttEndpoint endpoint = mockEndpoint();
        when(endpoint.isConnected()).thenReturn(Boolean.TRUE);
        final MqttPublishMessage messageFromDevice = mock(MqttPublishMessage.class);
        when(messageFromDevice.qosLevel()).thenReturn(MqttQoS.AT_LEAST_ONCE);
        when(messageFromDevice.messageId()).thenReturn(5555555);
        when(messageFromDevice.topicName()).thenReturn("e/my-tenant/4712");
        final MqttContext context = newMqttContext(messageFromDevice, endpoint, span);

        adapter.uploadEventMessage(context, "my-tenant", "4712", payload)
            .onSuccess(ok -> ctx.failNow("should not have succeeded sending message downstream"));
        assertEventHasBeenSentDownstream("my-tenant", "4712", null);
        // and the peer rejects the message
        outcome.fail(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST));

        ctx.verify(() -> {
            // THEN the device has not received a PUBACK
            verify(endpoint, never()).publishAcknowledge(anyInt());
            // and the message has not been reported as forwarded
            verify(metrics, never()).reportTelemetry(
                    any(MetricsTags.EndpointType.class),
                    anyString(),
                    any(),
                    eq(MetricsTags.ProcessingOutcome.FORWARDED),
                    any(MetricsTags.QoS.class),
                    anyInt(),
                    any());
        });
        ctx.completeNow();

    }

    /**
     * Verifies that the adapter includes a message annotation in a downstream message if the device publishes a message
     * with its <em>retain</em> flag set.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testUploadTelemetryMessageIncludesRetainAnnotation(final VertxTestContext ctx) {

        // GIVEN an adapter with a downstream telemetry consumer
        givenAnAdapter(properties);
        givenATelemetrySenderForAnyTenant();

        // WHEN a device publishes a message with its retain flag set
        final MqttEndpoint endpoint = mockEndpoint();
        when(endpoint.isConnected()).thenReturn(Boolean.TRUE);
        final Buffer payload = Buffer.buffer("hello");
        final MqttPublishMessage messageFromDevice = mock(MqttPublishMessage.class);
        when(messageFromDevice.qosLevel()).thenReturn(MqttQoS.AT_LEAST_ONCE);
        when(messageFromDevice.messageId()).thenReturn(5555555);
        when(messageFromDevice.topicName()).thenReturn("t/my-tenant/4712");
        when(messageFromDevice.isRetain()).thenReturn(Boolean.TRUE);
        when(messageFromDevice.payload()).thenReturn(payload);
        final MqttContext context = newMqttContext(messageFromDevice, endpoint, span);

        adapter.uploadTelemetryMessage(context, "my-tenant", "4712", payload)
            .onComplete(ctx.succeeding(ok -> {

                ctx.verify(() -> {
                    // THEN the device has received a PUBACK
                    verify(endpoint).publishAcknowledge(5555555);
                    // and the message has been sent downstream
                    // including the "retain" annotation
                    verify(telemetrySender).sendTelemetry(
                            argThat(tenant -> tenant.getTenantId().equals("my-tenant")),
                            argThat(assertion -> assertion.getDeviceId().equals("4712")),
                            eq(QoS.AT_LEAST_ONCE),
                            any(),
                            any(),
                            argThat(props -> props.get(MessageHelper.ANNOTATION_X_OPT_RETAIN).equals(Boolean.TRUE)),
                            any());
                    verify(metrics).reportTelemetry(
                            eq(MetricsTags.EndpointType.TELEMETRY),
                            eq("my-tenant"),
                            any(),
                            eq(MetricsTags.ProcessingOutcome.FORWARDED),
                            eq(MetricsTags.QoS.AT_LEAST_ONCE),
                            eq(payload.length()),
                            any());
                });
                ctx.completeNow();
            }));
    }

    /**
     * Verifies that the adapter creates a command consumer that is checked periodically for a subscription with Qos 0.
     * When the connection to the device is closed, check if a ttd event is sent and the command consumer is closed.
     */
    @Test
    public void testOnSubscribeWithQos0RegistersAndClosesConnection() {
        testOnSubscribeRegistersAndClosesConnection(MqttQoS.AT_MOST_ONCE);
    }

    /**
     * Verifies that the adapter creates a command consumer that is checked periodically for a subscription with Qos 1
     * and check the command consumer is closed and a ttd event is sent when the connection to the device is closed.
     */
    @Test
    public void testOnSubscribeWithQos1RegistersAndClosesConnection() {
        testOnSubscribeRegistersAndClosesConnection(MqttQoS.AT_LEAST_ONCE);
    }

    private void testOnSubscribeRegistersAndClosesConnection(final MqttQoS qos) {

        // GIVEN a device connected to an adapter
        givenAnAdapter(properties);
        givenAnEventSenderForAnyTenant();
        final MqttEndpoint endpoint = mockEndpoint();
        when(endpoint.keepAliveTimeSeconds()).thenReturn(10); // 10 seconds

        // WHEN a device subscribes to commands
        final CommandConsumer commandConsumer = mock(CommandConsumer.class);
        when(commandConsumer.close(any())).thenReturn(Future.succeededFuture());
        when(commandConsumerFactory.createCommandConsumer(eq("tenant"), eq("deviceId"), VertxMockSupport.anyHandler(), any(), any()))
                        .thenReturn(Future.succeededFuture(commandConsumer));
        final List<MqttTopicSubscription> subscriptions = Collections.singletonList(
                newMockTopicSubscription(getCommandSubscriptionTopic("tenant", "deviceId"), qos));
        final MqttSubscribeMessage msg = mock(MqttSubscribeMessage.class);
        when(msg.messageId()).thenReturn(15);
        when(msg.topicSubscriptions()).thenReturn(subscriptions);

        final var mqttDeviceEndpoint = adapter.getMqttDeviceEndpoint(endpoint, null, OptionalInt.empty());
        endpoint.closeHandler(handler -> mqttDeviceEndpoint.onClose());
        mqttDeviceEndpoint.onSubscribe(msg);

        // THEN the adapter creates a command consumer that is checked periodically
        verify(commandConsumerFactory).createCommandConsumer(eq("tenant"), eq("deviceId"), VertxMockSupport.anyHandler(), any(), any());
        // and the adapter registers a hook on the connection to the device
        final ArgumentCaptor<Handler<Void>> closeHookCaptor = VertxMockSupport.argumentCaptorHandler();
        verify(endpoint).closeHandler(closeHookCaptor.capture());
        // which closes the command consumer when the device disconnects
        closeHookCaptor.getValue().handle(null);
        verify(commandConsumer).close(any());
        // and sends an empty notification downstream with TTD 0
        assertEmptyNotificationHasBeenSentDownstream("tenant", "deviceId", 0);
    }

    /**
     * Verifies that the adapter doesn't send a 'disconnectedTtdEvent' on connection loss
     * when removal of the command consumer mapping entry fails (which would be the case
     * when another command consumer mapping had been registered in the mean time, meaning
     * the device has already reconnected).
     */
    @Test
    public void testAdapterSkipsTtdEventOnCmdConnectionCloseIfRemoveConsumerFails() {

        // GIVEN a device connected to an adapter
        givenAnAdapter(properties);
        givenAnEventSenderForAnyTenant();
        final MqttEndpoint endpoint = mockEndpoint();
        when(endpoint.isConnected()).thenReturn(true);
        when(endpoint.keepAliveTimeSeconds()).thenReturn(10); // 10 seconds

        // WHEN a device subscribes to commands
        final CommandConsumer commandConsumer = mock(CommandConsumer.class);
        when(commandConsumer.close(any())).thenReturn(Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_PRECON_FAILED)));
        when(commandConsumerFactory.createCommandConsumer(eq("tenant"), eq("deviceId"), VertxMockSupport.anyHandler(), any(), any()))
                .thenReturn(Future.succeededFuture(commandConsumer));
        final List<MqttTopicSubscription> subscriptions = Collections.singletonList(
                newMockTopicSubscription(getCommandSubscriptionTopic("tenant", "deviceId"), MqttQoS.AT_MOST_ONCE));
        final MqttSubscribeMessage msg = mock(MqttSubscribeMessage.class);
        when(msg.messageId()).thenReturn(15);
        when(msg.topicSubscriptions()).thenReturn(subscriptions);

        final var mqttDeviceEndpoint = adapter.getMqttDeviceEndpoint(endpoint, null, OptionalInt.empty());
        endpoint.closeHandler(handler -> mqttDeviceEndpoint.onClose());
        mqttDeviceEndpoint.onSubscribe(msg);

        // THEN the adapter creates a command consumer that is checked periodically
        verify(commandConsumerFactory).createCommandConsumer(eq("tenant"), eq("deviceId"), VertxMockSupport.anyHandler(), any(), any());
        // and the adapter registers a hook on the connection to the device
        final ArgumentCaptor<Handler<Void>> closeHookCaptor = VertxMockSupport.argumentCaptorHandler();
        verify(endpoint).closeHandler(closeHookCaptor.capture());
        // which closes the command consumer when the device disconnects
        closeHookCaptor.getValue().handle(null);
        when(endpoint.isConnected()).thenReturn(false);
        verify(commandConsumer).close(any());
        // and since closing the command consumer fails with a precon-failed exception
        // there is only one notification sent during consumer creation,
        assertEmptyNotificationHasBeenSentDownstream("tenant", "deviceId", -1);
        // no 'disconnectedTtdEvent' event with TTD = 0
        assertEmptyNotificationHasNotBeenSentDownstream("tenant", "deviceId", 0);
    }

    /**
     * Verifies that the adapter includes a status code for each topic filter in its SUBACK packet.
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testOnSubscribeIncludesStatusCodeForEachFilter() {

        // GIVEN a device connected to an adapter
        givenAnAdapter(properties);
        givenAnEventSenderForAnyTenant();
        final MqttEndpoint endpoint = mockEndpoint();
        when(endpoint.isConnected()).thenReturn(true);

        // WHEN a device sends a SUBSCRIBE packet for several unsupported filters
        final List<MqttTopicSubscription> subscriptions = new ArrayList<>();
        subscriptions.add(newMockTopicSubscription("unsupported/#", MqttQoS.AT_LEAST_ONCE));
        subscriptions.add(newMockTopicSubscription("bumlux/+/+/#", MqttQoS.AT_MOST_ONCE));
        subscriptions.add(newMockTopicSubscription("bumlux/+/+/#", MqttQoS.AT_MOST_ONCE));
        // and for subscribing to commands
        final CommandConsumer commandConsumer = mock(CommandConsumer.class);
        when(commandConsumer.close(any())).thenReturn(Future.succeededFuture());
        when(commandConsumerFactory.createCommandConsumer(eq("tenant-1"), eq("device-A"), VertxMockSupport.anyHandler(), any(), any()))
                        .thenReturn(Future.succeededFuture(commandConsumer));

        // command and error subscription for same device, checking:
        // - a command subscription is OK and shall pass
        // - an error subscription with unacceptable QoS level MqttQoS.EXACTLY_ONCE - shall fail
        // - error subscription (later, hence with priority) won't override the command subscription and
        //   command subscription will pass
        subscriptions.add(
                newMockTopicSubscription(getCommandSubscriptionTopic("tenant-1", "device-A"), MqttQoS.AT_MOST_ONCE));
        subscriptions.add(
                newMockTopicSubscription(getErrorSubscriptionTopic("tenant-1", "device-A"), MqttQoS.EXACTLY_ONCE));

        // command and error subscription for same device, checking:
        // - a command subscription with unacceptable QoS level MqttQoS.EXACTLY_ONCE - shall fail
        // - an error subscription shall be accepted with its QoS MqttQoS.AT_MOST_ONCE
        // - error subscription (later, hence with priority) won't override the command subscription and
        //   command subscription will fail
        subscriptions.add(
                newMockTopicSubscription(getCommandSubscriptionTopic("tenant-1", "device-B"), MqttQoS.EXACTLY_ONCE));
        subscriptions.add(
                newMockTopicSubscription(getErrorSubscriptionTopic("tenant-1", "device-B"), MqttQoS.AT_MOST_ONCE));

        final MqttSubscribeMessage msg = mock(MqttSubscribeMessage.class);
        when(msg.messageId()).thenReturn(15);
        when(msg.topicSubscriptions()).thenReturn(subscriptions);

        final var mqttDeviceEndpoint = adapter.getMqttDeviceEndpoint(endpoint, null, OptionalInt.empty());
        mqttDeviceEndpoint.onSubscribe(msg);

        // THEN the adapter sends a SUBACK packet to the device
        // which contains a failure status code for each unsupported filter
        final ArgumentCaptor<List<MqttQoS>> codeCaptor = ArgumentCaptor.forClass(List.class);
        verify(endpoint).subscribeAcknowledge(eq(15), codeCaptor.capture());
        assertThat(codeCaptor.getValue()).hasSize(subscriptions.size());
        assertThat(codeCaptor.getValue().get(0)).isEqualTo(MqttQoS.FAILURE);
        assertThat(codeCaptor.getValue().get(1)).isEqualTo(MqttQoS.FAILURE);
        assertThat(codeCaptor.getValue().get(2)).isEqualTo(MqttQoS.FAILURE);
        assertThat(codeCaptor.getValue().get(3)).isEqualTo(MqttQoS.AT_MOST_ONCE);
        assertThat(codeCaptor.getValue().get(4)).isEqualTo(MqttQoS.FAILURE);
        assertThat(codeCaptor.getValue().get(5)).isEqualTo(MqttQoS.FAILURE);
        assertThat(codeCaptor.getValue().get(6)).isEqualTo(MqttQoS.AT_MOST_ONCE);
        // and sends an empty notification downstream with TTD -1
        assertEmptyNotificationHasBeenSentDownstream("tenant-1", "device-A", -1);
    }

    private static MqttTopicSubscription newMockTopicSubscription(final String filter, final MqttQoS qos) {
        final MqttTopicSubscription result = mock(MqttTopicSubscription.class);
        when(result.qualityOfService()).thenReturn(qos);
        when(result.topicName()).thenReturn(filter);
        return result;
    }

    /**
     *
     * Verifies that the adapter will accept uploading messages to standard as well as shortened topic names.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testUploadMessageSupportsShortAndLongEndpointNames(final VertxTestContext ctx) {

        // GIVEN an adapter with downstream telemetry & event consumers
        givenAnAdapter(properties);
        givenATelemetrySenderForAnyTenant();
        givenAnEventSenderForAnyTenant();

        // WHEN a device publishes events and telemetry messages
        final MqttEndpoint endpoint = mockEndpoint();
        when(endpoint.isConnected()).thenReturn(Boolean.TRUE);
        final Buffer payload = Buffer.buffer("some payload");
        final MqttPublishMessage messageFromDevice = mock(MqttPublishMessage.class);
        when(messageFromDevice.qosLevel()).thenReturn(MqttQoS.AT_LEAST_ONCE);
        when(messageFromDevice.messageId()).thenReturn(5555555);
        when(messageFromDevice.payload()).thenReturn(payload);

        ResourceIdentifier resourceId = ResourceIdentifier.from("telemetry", "my-tenant", "4712");
        when(messageFromDevice.topicName()).thenReturn(resourceId.toString());
        adapter.uploadMessage(
                newMqttContext(messageFromDevice, endpoint, span),
                resourceId,
                messageFromDevice).onFailure(ctx::failNow);

        resourceId = ResourceIdentifier.from("event", "my-tenant", "4712");
        when(messageFromDevice.topicName()).thenReturn(resourceId.toString());
        adapter.uploadMessage(
                newMqttContext(messageFromDevice, endpoint, span),
                resourceId,
                messageFromDevice).onFailure(ctx::failNow);

        resourceId = ResourceIdentifier.from("t", "my-tenant", "4712");
        when(messageFromDevice.topicName()).thenReturn(resourceId.toString());
        adapter.uploadMessage(
                newMqttContext(messageFromDevice, endpoint, span),
                resourceId,
                messageFromDevice).onFailure(ctx::failNow);

        resourceId = ResourceIdentifier.from("e", "my-tenant", "4712");
        when(messageFromDevice.topicName()).thenReturn(resourceId.toString());
        adapter.uploadMessage(
                newMqttContext(messageFromDevice, endpoint, span),
                resourceId,
                messageFromDevice).onFailure(ctx::failNow);

        resourceId = ResourceIdentifier.from("unknown", "my-tenant", "4712");
        when(messageFromDevice.topicName()).thenReturn(resourceId.toString());
        adapter.uploadMessage(
                newMqttContext(messageFromDevice, endpoint, span),
                resourceId,
                messageFromDevice).onComplete(ctx.failing(t -> {
                    ctx.verify(() -> assertThat(t).isInstanceOf(ClientErrorException.class));
                }));
        ctx.completeNow();
    }

    /**
     * Verifies the connection metrics for authenticated connections.
     * <p>
     * This test should check if the metrics receive a call to increment and decrement when a connection is being
     * established and then closed.
     */
    @Test
    public void testConnectionMetrics() {

        givenAnAdapter(properties);

        when(authHandler.authenticateDevice(any(MqttConnectContext.class)))
                .thenReturn(Future.succeededFuture(new DeviceUser("DEFAULT_TENANT", "4711")));

        final MqttEndpoint endpoint = getMqttEndpointAuthenticated();
        adapter.handleEndpointConnection(endpoint);

        verify(metrics).incrementConnections("DEFAULT_TENANT");
        final ArgumentCaptor<Handler<Void>> closeHandlerCaptor = VertxMockSupport.argumentCaptorHandler();
        verify(endpoint, times(2)).closeHandler(closeHandlerCaptor.capture());
        closeHandlerCaptor.getValue().handle(null);
        verify(metrics).decrementConnections("DEFAULT_TENANT");
    }

    /**
     * Verifies the connection metrics for unauthenticated connections.
     * <p>
     * This test should check if the metrics receive a call to increment and decrement when a connection is being
     * established and then closed.
     */
    @Test
    public void testUnauthenticatedConnectionMetrics() {

        properties.setAuthenticationRequired(false);
        givenAnAdapter(properties);

        final MqttEndpoint endpoint = mockEndpoint();
        adapter.handleEndpointConnection(endpoint);

        // THEN the adapter does not try to authenticate the device
        verify(authHandler, never()).authenticateDevice(any(MqttConnectContext.class));
        // and increments the number of unauthenticated connections
        verify(metrics).incrementUnauthenticatedConnections();
        // and when the device closes the connection
        final ArgumentCaptor<Handler<Void>> closeHandlerCaptor = VertxMockSupport.argumentCaptorHandler();
        verify(endpoint, times(2)).closeHandler(closeHandlerCaptor.capture());
        closeHandlerCaptor.getValue().handle(null);
        // the number of unauthenticated connections is decremented again
        verify(metrics).decrementUnauthenticatedConnections();
    }

    /**
     * Verifies that the connection is rejected due to the limit exceeded.
     */
    @Test
    public void testConnectionsLimitExceeded() {

        // GIVEN an adapter requiring devices to authenticate endpoint
        properties.setAuthenticationRequired(true);
        givenAnAdapter(properties);

        // WHEN a device tries to establish a connection
        when(authHandler.authenticateDevice(any(MqttConnectContext.class)))
                .thenReturn(Future.succeededFuture(new DeviceUser(Constants.DEFAULT_TENANT, "4711")));
        when(resourceLimitChecks.isConnectionLimitReached(any(TenantObject.class), any(SpanContext.class)))
                .thenReturn(Future.succeededFuture(Boolean.TRUE));
        final MqttEndpoint endpoint = getMqttEndpointAuthenticated();
        adapter.handleEndpointConnection(endpoint);

        // THEN the adapter has tried to authenticate the device
        verify(authHandler).authenticateDevice(any(MqttConnectContext.class));
        // THEN the connection request is rejected
        verify(endpoint).reject(MqttConnectReturnCode.CONNECTION_REFUSED_NOT_AUTHORIZED);
        verify(metrics).reportConnectionAttempt(
                ConnectionAttemptOutcome.TENANT_CONNECTIONS_EXCEEDED,
                Constants.DEFAULT_TENANT,
                "BUMLUX_CIPHER");
    }

    /**
     * Verifies that the connection is rejected due to the connection duration limit exceeded.
     */
    @Test
    public void testConnectionDurationLimitExceeded() {

        // GIVEN an adapter requiring devices to authenticate endpoint
        properties.setAuthenticationRequired(true);
        givenAnAdapter(properties);

        // WHEN a device tries to establish a connection
        when(authHandler.authenticateDevice(any(MqttConnectContext.class)))
                .thenReturn(Future.succeededFuture(new DeviceUser(Constants.DEFAULT_TENANT, "4711")));
        when(resourceLimitChecks.isConnectionDurationLimitReached(any(TenantObject.class), any(SpanContext.class)))
                .thenReturn(Future.succeededFuture(Boolean.TRUE));
        final MqttEndpoint endpoint = getMqttEndpointAuthenticated();
        adapter.handleEndpointConnection(endpoint);

        // THEN the adapter has tried to authenticate the device
        verify(authHandler).authenticateDevice(any(MqttConnectContext.class));
        // THEN the connection request is rejected
        verify(endpoint).reject(MqttConnectReturnCode.CONNECTION_REFUSED_NOT_AUTHORIZED);
        verify(metrics).reportConnectionAttempt(
                ConnectionAttemptOutcome.CONNECTION_DURATION_EXCEEDED,
                Constants.DEFAULT_TENANT,
                "BUMLUX_CIPHER");
    }

    /**
     * Verifies that a telemetry message is rejected due to the limit exceeded.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testMessageLimitExceededForATelemetryMessage(final VertxTestContext ctx) {

        // GIVEN an adapter
        givenAnAdapter(properties);
        givenATelemetrySenderForAnyTenant();
        when(resourceLimitChecks.isMessageLimitReached(any(TenantObject.class), anyLong(), any(SpanContext.class)))
                .thenReturn(Future.succeededFuture(Boolean.TRUE));

        // WHEN a device of "my-tenant" publishes a telemetry message
        final MqttEndpoint client = mockEndpoint();
        final MqttPublishMessage msg = mock(MqttPublishMessage.class);
        when(msg.topicName()).thenReturn("t/my-tenant/the-device");
        when(msg.qosLevel()).thenReturn(MqttQoS.AT_LEAST_ONCE);
        adapter.uploadTelemetryMessage(
                newMqttContext(msg, client, span),
                "my-tenant",
                "the-device",
                Buffer.buffer("test")).onComplete(ctx.failing(t -> {
                    ctx.verify(() -> {
                        // THEN the message has not been sent downstream
                        assertNoTelemetryMessageHasBeenSentDownstream();
                        // because the message limit is exceeded
                        assertThat(((ClientErrorException) t).getErrorCode())
                                .isEqualTo(HttpUtils.HTTP_TOO_MANY_REQUESTS);
                        // and the published message has not been acknowledged
                        verify(client, never()).publishAcknowledge(anyInt());
                        // and the message has been reported as unprocessable
                        verify(metrics).reportTelemetry(
                                any(MetricsTags.EndpointType.class),
                                anyString(),
                                any(),
                                eq(MetricsTags.ProcessingOutcome.UNPROCESSABLE),
                                any(MetricsTags.QoS.class),
                                anyInt(),
                                any());
                    });
                    ctx.completeNow();
                }));
    }

    /**
     * Verifies that an event message is rejected due to the limit exceeded.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testMessageLimitExceededForAnEventMessage(final VertxTestContext ctx) {

        // GIVEN an adapter
        givenAnAdapter(properties);
        givenAnEventSenderForAnyTenant();

        when(resourceLimitChecks.isMessageLimitReached(any(TenantObject.class), anyLong(), any(SpanContext.class)))
                .thenReturn(Future.succeededFuture(Boolean.TRUE));

        // WHEN a device of "my-tenant" publishes an event message
        final MqttEndpoint client = mockEndpoint();
        final MqttPublishMessage msg = mock(MqttPublishMessage.class);
        when(msg.topicName()).thenReturn("e/my-tenant/the-device");
        when(msg.qosLevel()).thenReturn(MqttQoS.AT_LEAST_ONCE);
        adapter.uploadEventMessage(
                newMqttContext(msg, client, span),
                "my-tenant",
                "the-device",
                Buffer.buffer("test")).onComplete(ctx.failing(t -> {
                    ctx.verify(() -> {
                        // THEN the message has not been sent downstream
                        assertNoEventHasBeenSentDownstream();
                        // because the message limit is exceeded
                        assertThat(((ClientErrorException) t).getErrorCode())
                                .isEqualTo(HttpUtils.HTTP_TOO_MANY_REQUESTS);
                        // and the event has not been acknowledged
                        verify(client, never()).publishAcknowledge(anyInt());
                        // and the message has been reported as unprocessable
                        verify(metrics).reportTelemetry(
                                any(MetricsTags.EndpointType.class),
                                anyString(),
                                any(),
                                eq(MetricsTags.ProcessingOutcome.UNPROCESSABLE),
                                any(MetricsTags.QoS.class),
                                anyInt(),
                                any());
                    });
                    ctx.completeNow();
                }));
    }

    /**
     * Verifies that a command response message is rejected due to the limit exceeded.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testMessageLimitExceededForACommandResponseMessage(final VertxTestContext ctx) {

        // GIVEN an adapter
        givenAnAdapter(properties);
        final CommandResponseSender sender = givenACommandResponseSenderForAnyTenant();

        // WHEN the message limit exceeds
        when(resourceLimitChecks.isMessageLimitReached(any(TenantObject.class), anyLong(), any(SpanContext.class)))
                .thenReturn(Future.succeededFuture(Boolean.TRUE));

        // WHEN a device of "tenant" publishes a command response message
        final MqttPublishMessage msg = mock(MqttPublishMessage.class);
        when(msg.topicName()).thenReturn("e/tenant/device");
        when(msg.qosLevel()).thenReturn(MqttQoS.AT_MOST_ONCE);
        when(msg.payload()).thenReturn(Buffer.buffer("test"));

        adapter.uploadMessage(newMqttContext(msg, mockEndpoint(), span),
                ResourceIdentifier.fromString(String.format("%s/tenant/device/res/%s/200", getCommandEndpoint(),
                        Commands.encodeRequestIdParameters("cmd123", "to", "deviceId", MessagingType.amqp))),
                msg)
                .onComplete(ctx.failing(t -> {
                    ctx.verify(() -> {

                        // THEN the request fails with a 429 error
                        assertThat(((ClientErrorException) t).getErrorCode())
                                .isEqualTo(HttpUtils.HTTP_TOO_MANY_REQUESTS);
                        // AND the response is not being forwarded
                        verify(sender, never()).sendCommandResponse(
                                any(TenantObject.class),
                                any(RegistrationAssertion.class),
                                any(CommandResponse.class),
                                (SpanContext) any());
                        // AND has reported the message as unprocessable
                        verify(metrics).reportCommand(
                                eq(MetricsTags.Direction.RESPONSE),
                                eq("tenant"),
                                any(),
                                eq(MetricsTags.ProcessingOutcome.UNPROCESSABLE),
                                anyInt(),
                                any());
                    });
                    ctx.completeNow();
                }));
    }

    /**
     * Verifies that the TTL for a downstream event is set to the given <em>time-to-live</em> value in the
     * <em>property-bag</em>.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void verifyEventMessageUsesTtlValueGivenInPropertyBag(final VertxTestContext ctx) {
        // Given an adapter
        givenAnAdapter(properties);
        givenAnEventSenderForAnyTenant();

        // WHEN a "device" of "tenant" publishes an event message with a TTL value of 30 seconds.
        final MqttPublishMessage msg = mock(MqttPublishMessage.class);
        when(msg.topicName()).thenReturn("e/tenant/device/?hono-ttl=30&param2=value2");
        when(msg.qosLevel()).thenReturn(MqttQoS.AT_LEAST_ONCE);
        adapter.uploadEventMessage(
                newMqttContext(msg, mockEndpoint(), span),
                "tenant",
                "device",
                Buffer.buffer("test")).onComplete(ctx.succeeding(t -> {
                    ctx.verify(() -> {
                        // THEN the TTL value of the amqp message is 30 seconds.
                        assertEventHasBeenSentDownstream("tenant", "device", null, 30L);
                    });
                    ctx.completeNow();
                }));
    }

    private String getCommandSubscriptionTopic(final String tenantId, final String deviceId) {
        return String.format("%s/%s/%s/req/#", getCommandEndpoint(), tenantId, deviceId);
    }

    private String getCommandEndpoint() {
        return CommandConstants.COMMAND_ENDPOINT;
    }

    private String getErrorSubscriptionTopic(final String tenantId, final String deviceId) {
        return String.format("%s/%s/%s/#", getErrorEndpoint(), tenantId, deviceId);
    }
    private String getErrorEndpoint() {
        return ErrorSubscription.ERROR_ENDPOINT;
    }

    private MqttEndpoint getMqttEndpointAuthenticated(final String username, final String password) {

        final SSLSession sslSession = mock(SSLSession.class);
        when(sslSession.getCipherSuite()).thenReturn("BUMLUX_CIPHER");

        final MqttEndpoint endpoint = mockEndpoint();
        when(endpoint.auth()).thenReturn(new MqttAuth(username, password));
        when(endpoint.subscribeHandler(VertxMockSupport.anyHandler())).thenReturn(endpoint);
        when(endpoint.unsubscribeHandler(VertxMockSupport.anyHandler())).thenReturn(endpoint);
        when(endpoint.isCleanSession()).thenReturn(Boolean.FALSE);
        when(endpoint.sslSession()).thenReturn(sslSession);
        return endpoint;
    }

    private MqttEndpoint getMqttEndpointAuthenticated() {
        return getMqttEndpointAuthenticated("sensor1@DEFAULT_TENANT", "test");
    }

    /**
     * Creates a new adapter instance to be tested.
     * <p>
     * This method
     * <ol>
     * <li>creates a new {@code MqttServer} using {@link #getMqttServer(boolean)}</li>
     * <li>assigns the result to property <em>server</em></li>
     * <li>passes the server in to {@link #getAdapter(MqttServer, MqttProtocolAdapterProperties)}</li>
     * <li>assigns the result to property <em>adapter</em></li>
     * </ol>
     *
     * @param configuration The configuration properties to use.
     * @return The adapter instance.
     */
    private AbstractVertxBasedMqttProtocolAdapter<MqttProtocolAdapterProperties> givenAnAdapter(
            final MqttProtocolAdapterProperties configuration) {

        this.server = getMqttServer(false);
        this.adapter = getAdapter(this.server, configuration);
        return adapter;
    }

    private static MqttServer getMqttServer(final boolean startupShouldFail) {

        final MqttServer server = mock(MqttServer.class);
        when(server.actualPort()).thenReturn(0, 1883);
        when(server.endpointHandler(VertxMockSupport.anyHandler())).thenReturn(server);
        when(server.listen(VertxMockSupport.anyHandler())).then(invocation -> {
            final Handler<AsyncResult<MqttServer>> handler = invocation.getArgument(0);
            if (startupShouldFail) {
                handler.handle(Future.failedFuture("MQTT server intentionally failed to start"));
            } else {
                handler.handle(Future.succeededFuture(server));
            }
            return server;
        });

        return server;
    }

    private AbstractVertxBasedMqttProtocolAdapter<MqttProtocolAdapterProperties> getAdapter(
            final MqttServer server,
            final MqttProtocolAdapterProperties configuration) {

        final AbstractVertxBasedMqttProtocolAdapter<MqttProtocolAdapterProperties> adapter = new AbstractVertxBasedMqttProtocolAdapter<>() {

            @Override
            public String getTypeName() {
                return ADAPTER_TYPE;
            }

            @Override
            protected Future<Void> onPublishedMessage(final MqttContext ctx) {
                final ResourceIdentifier topic = ResourceIdentifier.fromString(ctx.message().topicName());
                return uploadTelemetryMessage(ctx, topic.getTenantId(), topic.getResourceId(), ctx.message().payload());
            }
        };
        adapter.setConfig(configuration);
        adapter.setMetrics(metrics);
        adapter.setAuthHandler(authHandler);
        adapter.setResourceLimitChecks(resourceLimitChecks);
        setServiceClients(adapter);

        if (server != null) {
            adapter.setMqttInsecureServer(server);
            adapter.init(vertx, context);
        }

        return adapter;
    }
}
