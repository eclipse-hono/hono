/*******************************************************************************
 * Copyright (c) 2016, 2018 Contributors to the Eclipse Foundation
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

import static io.netty.handler.codec.mqtt.MqttConnectReturnCode.CONNECTION_REFUSED_NOT_AUTHORIZED;
import static io.netty.handler.codec.mqtt.MqttConnectReturnCode.CONNECTION_REFUSED_SERVER_UNAVAILABLE;
import static java.net.HttpURLConnection.HTTP_NOT_FOUND;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.CommandConnection;
import org.eclipse.hono.client.HonoClient;
import org.eclipse.hono.client.MessageConsumer;
import org.eclipse.hono.client.MessageSender;
import org.eclipse.hono.client.RegistrationClient;
import org.eclipse.hono.client.ServerErrorException;
import org.eclipse.hono.client.TenantClient;
import org.eclipse.hono.config.ProtocolAdapterProperties;
import org.eclipse.hono.service.auth.DeviceUser;
import org.eclipse.hono.service.auth.device.AuthHandler;
import org.eclipse.hono.util.EventConstants;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.RegistrationConstants;
import org.eclipse.hono.util.ResourceIdentifier;
import org.eclipse.hono.util.TelemetryConstants;
import org.eclipse.hono.util.TenantConstants;
import org.eclipse.hono.util.TenantObject;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;

import io.netty.handler.codec.mqtt.MqttConnectReturnCode;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.opentracing.SpanContext;
import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.mqtt.MqttAuth;
import io.vertx.mqtt.MqttEndpoint;
import io.vertx.mqtt.MqttServer;
import io.vertx.mqtt.MqttTopicSubscription;
import io.vertx.mqtt.messages.MqttPublishMessage;
import io.vertx.mqtt.messages.MqttSubscribeMessage;
import io.vertx.proton.ProtonDelivery;

/**
 * Verifies behavior of {@link AbstractVertxBasedMqttProtocolAdapter}.
 *
 */
@RunWith(VertxUnitRunner.class)
public class AbstractVertxBasedMqttProtocolAdapterTest {

    private static final String ADAPTER_TYPE = "mqtt";

    private static Vertx vertx = Vertx.vertx();

    /**
     * Global timeout for all test cases.
     */
    @Rule
    public Timeout globalTimeout = new Timeout(5000, TimeUnit.SECONDS);

    private HonoClient tenantServiceClient;
    private HonoClient credentialsServiceClient;
    private HonoClient messagingClient;
    private HonoClient deviceRegistrationServiceClient;
    private RegistrationClient regClient;
    private TenantClient tenantClient;
    private AuthHandler<MqttContext> authHandler;
    private ProtocolAdapterProperties config;
    private MqttAdapterMetrics metrics;
    private CommandConnection commandConnection;
    private Context context;

    /**
     * Creates clients for the needed micro services and sets the configuration to enable the insecure port.
     */
    @Before
    @SuppressWarnings("unchecked")
    public void setup() {

        context = mock(Context.class);
        doAnswer(invocation -> {
            final Handler<Void> handler = invocation.getArgument(0);
            handler.handle(null);
            return null;
        }).when(context).runOnContext(any(Handler.class));

        config = new ProtocolAdapterProperties();
        config.setInsecurePortEnabled(true);

        metrics = mock(MqttAdapterMetrics.class);

        regClient = mock(RegistrationClient.class);
        final JsonObject result = new JsonObject().put(RegistrationConstants.FIELD_ASSERTION, "token");
        when(regClient.assertRegistration(anyString(), (String) any(), (SpanContext) any())).thenReturn(Future.succeededFuture(result));

        tenantClient = mock(TenantClient.class);
        when(tenantClient.get(anyString(), (SpanContext) any())).thenAnswer(invocation -> {
            return Future.succeededFuture(TenantObject.from(invocation.getArgument(0), true));
        });

        tenantServiceClient = mock(HonoClient.class);
        when(tenantServiceClient.isConnected()).thenReturn(Future.failedFuture(new ServerErrorException(HttpURLConnection.HTTP_UNAVAILABLE)));
        when(tenantServiceClient.connect(any(Handler.class))).thenReturn(Future.succeededFuture(tenantServiceClient));
        when(tenantServiceClient.getOrCreateTenantClient()).thenReturn(Future.succeededFuture(tenantClient));

        credentialsServiceClient = mock(HonoClient.class);
        when(credentialsServiceClient.isConnected()).thenReturn(Future.failedFuture(new ServerErrorException(HttpURLConnection.HTTP_UNAVAILABLE)));
        when(credentialsServiceClient.connect(any(Handler.class)))
                .thenReturn(Future.succeededFuture(credentialsServiceClient));

        messagingClient = mock(HonoClient.class);
        when(messagingClient.isConnected()).thenReturn(Future.failedFuture(new ServerErrorException(HttpURLConnection.HTTP_UNAVAILABLE)));
        when(messagingClient.connect(any(Handler.class))).thenReturn(Future.succeededFuture(messagingClient));
        when(messagingClient.getOrCreateEventSender(anyString())).thenReturn(Future.succeededFuture(mock(MessageSender.class)));
        when(messagingClient.getOrCreateTelemetrySender(anyString())).thenReturn(Future.succeededFuture(mock(MessageSender.class)));

        deviceRegistrationServiceClient = mock(HonoClient.class);
        when(deviceRegistrationServiceClient.isConnected()).thenReturn(
                Future.failedFuture(new ServerErrorException(HttpURLConnection.HTTP_UNAVAILABLE)));
        when(deviceRegistrationServiceClient.connect(any(Handler.class)))
                .thenReturn(Future.succeededFuture(deviceRegistrationServiceClient));
        when(deviceRegistrationServiceClient.getOrCreateRegistrationClient(anyString()))
                .thenReturn(Future.succeededFuture(regClient));

        commandConnection = mock(CommandConnection.class);
        when(commandConnection.isConnected()).thenReturn(
                Future.failedFuture(new ServerErrorException(HttpURLConnection.HTTP_UNAVAILABLE)));
        when(commandConnection.connect(any(Handler.class))).thenReturn(Future.succeededFuture(commandConnection));
        when(commandConnection.closeCommandConsumer(anyString(), anyString())).thenReturn(Future.succeededFuture(null));

        authHandler = mock(AuthHandler.class);
    }

    /**
     * Cleans up fixture.
     * 
     * @param ctx The vert.x test context.
     */
    @AfterClass
    public static void shutDown(final TestContext ctx) {
        vertx.close(ctx.asyncAssertSuccess());
    }

    private static MqttContext newMqttContext(final MqttPublishMessage message, final MqttEndpoint endpoint) {
        final MqttContext result = MqttContext.fromPublishPacket(message, endpoint);
        result.setTracingContext(mock(SpanContext.class));
        return result;
    }

    @SuppressWarnings("unchecked")
    private static MqttEndpoint mockEndpoint() {
        final MqttEndpoint endpoint = mock(MqttEndpoint.class);
        when(endpoint.clientIdentifier()).thenReturn("test-device");
        when(endpoint.subscribeHandler(any(Handler.class))).thenReturn(endpoint);
        when(endpoint.unsubscribeHandler(any(Handler.class))).thenReturn(endpoint);
        return endpoint;
    }

    /**
     * Verifies that an MQTT server is bound to the insecure port during startup and connections to required services
     * have been established.
     *
     * @param ctx The helper to use for running async tests on vertx.
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testStartup(final TestContext ctx) {

        final MqttServer server = getMqttServer(false);
        final AbstractVertxBasedMqttProtocolAdapter<ProtocolAdapterProperties> adapter = getAdapter(server);

        final Async startup = ctx.async();

        final Future<Void> startupTracker = Future.future();
        startupTracker.setHandler(ctx.asyncAssertSuccess(s -> {
            startup.complete();
        }));
        adapter.start(startupTracker);

        startup.await();

        verify(server).listen(any(Handler.class));
        verify(server).endpointHandler(any(Handler.class));
    }

    // TODO: startup fail test

    /**
     * Verifies that a connection attempt from a device is refused if
     * the adapter is not connected to all of the services it depends on.
     */
    @Test
    public void testEndpointHandlerFailsWithoutDownstreamConnections() {

        // GIVEN an adapter that is not connected to
        // all of its required services
        final MqttServer server = getMqttServer(false);
        final AbstractVertxBasedMqttProtocolAdapter<ProtocolAdapterProperties> adapter = getAdapter(server);

        // WHEN a client tries to connect
        final MqttEndpoint endpoint = mockEndpoint();
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
        config.setAuthenticationRequired(false);
        final MqttServer server = getMqttServer(false);
        final AbstractVertxBasedMqttProtocolAdapter<ProtocolAdapterProperties> adapter = getAdapter(server);
        forceClientMocksToConnected();

        // WHEN a device connects without providing credentials
        final MqttEndpoint endpoint = mockEndpoint();
        adapter.handleEndpointConnection(endpoint);

        // THEN the connection is established
        verify(endpoint).accept(false);
    }

    /**
     * Verifies that an adapter rejects a connection attempt from a device that belongs to a tenant for which the
     * adapter is disabled.
     */
    @Test
    public void testEndpointHandlerRejectsDeviceOfDisabledTenant() {

        // GIVEN an adapter
        final MqttServer server = getMqttServer(false);
        // which is disabled for tenant "my-tenant"
        final TenantObject myTenantConfig = TenantObject.from("my-tenant", true);
        myTenantConfig.addAdapterConfiguration(new JsonObject()
                .put(TenantConstants.FIELD_ADAPTERS_TYPE, ADAPTER_TYPE)
                .put(TenantConstants.FIELD_ENABLED, false));
        when(tenantClient.get(eq("my-tenant"), (SpanContext) any())).thenReturn(Future.succeededFuture(myTenantConfig));
        when(authHandler.authenticateDevice(any(MqttContext.class))).thenReturn(Future.succeededFuture(new DeviceUser("my-tenant", "4711")));
        final AbstractVertxBasedMqttProtocolAdapter<ProtocolAdapterProperties> adapter = getAdapter(server);
        forceClientMocksToConnected();

        // WHEN a device of "my-tenant" tries to connect
        final MqttEndpoint endpoint = mockEndpoint();
        adapter.handleEndpointConnection(endpoint);

        // THEN the connection is not established
        verify(endpoint).reject(CONNECTION_REFUSED_NOT_AUTHORIZED);
    }

    /**
     * Verifies that an adapter that is configured to require devices to authenticate rejects
     * connections from devices which do not provide proper credentials.
     */
    @Test
    public void testEndpointHandlerRejectsUnauthenticatedDevices() {

        // GIVEN an adapter that does require devices to authenticate
        final MqttServer server = getMqttServer(false);
        final AbstractVertxBasedMqttProtocolAdapter<ProtocolAdapterProperties> adapter = getAdapter(server);
        when(authHandler.authenticateDevice(any(MqttContext.class))).thenReturn(
                Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_UNAUTHORIZED)));
        forceClientMocksToConnected();

        // WHEN a device connects without providing proper credentials
        final MqttEndpoint endpoint = mockEndpoint();
        adapter.handleEndpointConnection(endpoint);
        adapter.handleEndpointConnection(endpoint);
        adapter.handleEndpointConnection(endpoint);

        // THEN the connection is refused
        verify(authHandler, times(3)).authenticateDevice(any(MqttContext.class));
    }

    /**
     * Verifies that an adapter retrieves credentials on record for a device connecting to the adapter.
     */
    @Test
    public void testEndpointHandlerTriesToAuthenticateDevice() {

        // GIVEN an adapter requiring devices to authenticate endpoint
        final MqttServer server = getMqttServer(false);
        config.setAuthenticationRequired(true);
        final AbstractVertxBasedMqttProtocolAdapter<ProtocolAdapterProperties> adapter = getAdapter(server);
        forceClientMocksToConnected();

        // WHEN a device tries to establish a connection
        when(authHandler.authenticateDevice(any(MqttContext.class))).thenReturn(
                Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_UNAUTHORIZED)));
        final MqttEndpoint endpoint = getMqttEndpointAuthenticated();
        adapter.handleEndpointConnection(endpoint);

        // THEN the adapter has tried to authenticate the device
        verify(authHandler).authenticateDevice(any(MqttContext.class));
    }

    /**
     * Verifies that an adapter rejects connections when the connection limit is exceeded.
     */
    @Test
    public void testEndpointHandlerRejectsConnectionsAboveLimit() {

        // GIVEN an adapter
        final MqttServer server = getMqttServer(false);
        // with a connection limit of 1
        config.setMaxConcurrentConnections(1);
        final AbstractVertxBasedMqttProtocolAdapter<ProtocolAdapterProperties> adapter = getAdapter(server);
        forceClientMocksToConnected();
        // which already has 1 connection open
        when(metrics.getNumberOfConnections()).thenReturn(1L);

        // WHEN a device tries to establish a connection
        final MqttEndpoint endpoint = getMqttEndpointAuthenticated();
        adapter.handleEndpointConnection(endpoint);

        // THEN the connection is not established
        verify(endpoint).reject(CONNECTION_REFUSED_SERVER_UNAVAILABLE);
    }

    /**
     * Verifies that on successful authentication the adapter sets appropriate message and close handlers on the client
     * endpoint.
     */
    @SuppressWarnings({ "unchecked" })
    @Test
    public void testAuthenticatedMqttAdapterCreatesMessageHandlersForAuthenticatedDevices() {

        // GIVEN an adapter
        final MqttServer server = getMqttServer(false);
        final AbstractVertxBasedMqttProtocolAdapter<ProtocolAdapterProperties> adapter = getAdapter(server);
        forceClientMocksToConnected();
        when(authHandler.authenticateDevice(any(MqttContext.class))).thenReturn(Future.succeededFuture(new DeviceUser("DEFAULT_TENANT", "4711")));

        // WHEN a device tries to connect with valid credentials
        final MqttEndpoint endpoint = getMqttEndpointAuthenticated();
        adapter.handleEndpointConnection(endpoint);

        // THEN the device's logical ID is successfully established and corresponding handlers
        // are registered
        verify(authHandler).authenticateDevice(any(MqttContext.class));
        verify(endpoint).accept(false);
        verify(endpoint).publishHandler(any(Handler.class));
        verify(endpoint).closeHandler(any(Handler.class));
    }

    /**
     * Verifies that unregistered devices with valid credentials cannot establish connection.
     */
    @Test
    public void testAuthenticatedMqttAdapterRejectsConnectionForNonExistingDevice() {

        // GIVEN an adapter
        final MqttServer server = getMqttServer(false);
        final AbstractVertxBasedMqttProtocolAdapter<ProtocolAdapterProperties> adapter = getAdapter(server);
        forceClientMocksToConnected();
        // which is connected to a Credentials service that has credentials on record for device 9999
        when(authHandler.authenticateDevice(any(MqttContext.class))).thenReturn(Future.succeededFuture(new DeviceUser("DEFAULT_TENANT", "9999")));
        // but for which no registration information is available
        when(regClient.assertRegistration(eq("9999"), (String) any(), (SpanContext) any()))
                .thenReturn(Future.failedFuture(new ClientErrorException(
                        HTTP_NOT_FOUND, "device unknown or disabled")));

        // WHEN a device tries to connect with valid credentials
        final MqttEndpoint endpoint = getMqttEndpointAuthenticated();
        adapter.handleEndpointConnection(endpoint);

        // THEN the device's credentials are verified successfully
        verify(authHandler).authenticateDevice(any(MqttContext.class));
        // but the connection is refused
        verify(endpoint).reject(MqttConnectReturnCode.CONNECTION_REFUSED_BAD_USER_NAME_OR_PASSWORD);
    }

    /**
     * Verifies that the adapter registers message handlers on client connections when device authentication is
     * disabled.
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testUnauthenticatedMqttAdapterCreatesMessageHandlersForAllDevices() {

        // GIVEN an adapter that does not require devices to authenticate
        config.setAuthenticationRequired(false);
        final MqttServer server = getMqttServer(false);
        final AbstractVertxBasedMqttProtocolAdapter<ProtocolAdapterProperties> adapter = getAdapter(server);

        forceClientMocksToConnected();

        // WHEN a device connects that does not provide any credentials
        final MqttEndpoint endpoint = mockEndpoint();
        adapter.handleEndpointConnection(endpoint);

        // THEN the connection is established and handlers are registered
        verify(authHandler, never()).authenticateDevice(any(MqttContext.class));
        verify(endpoint).publishHandler(any(Handler.class));
        verify(endpoint).closeHandler(any(Handler.class));
        verify(endpoint).accept(false);
    }

    /**
     * Verifies that the adapter discards messages that contain a malformed
     * topic.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testHandlePublishedMessageFailsForMalformedTopic(final TestContext ctx) {

        // GIVEN an adapter
        final MqttServer server = getMqttServer(false);
        final AbstractVertxBasedMqttProtocolAdapter<ProtocolAdapterProperties> adapter = getAdapter(server);

        // WHEN a device publishes a message with a malformed topic
        final MqttPublishMessage msg = mock(MqttPublishMessage.class);
        final MqttEndpoint device = mockEndpoint();
        when(msg.qosLevel()).thenReturn(MqttQoS.AT_LEAST_ONCE);
        when(msg.topicName()).thenReturn(null);
        adapter.handlePublishedMessage(newMqttContext(msg, device));

        // THEN the message is not processed
        verify(metrics, never()).incrementProcessedMessages(anyString(), anyString());
        // and no PUBACK is sent to the device
        verify(device, never()).publishAcknowledge(anyInt());
    }

    /**
     * Verifies that the adapter does not forward a message published by a device if the device's registration status
     * cannot be asserted.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testUploadTelemetryMessageFailsForUnknownDevice(final TestContext ctx) {

        // GIVEN an adapter
        final MqttServer server = getMqttServer(false);
        final AbstractVertxBasedMqttProtocolAdapter<ProtocolAdapterProperties> adapter = getAdapter(server);
        givenAQoS0TelemetrySender();

        // WHEN an unknown device publishes a telemetry message
        when(regClient.assertRegistration(eq("unknown"), any(), any())).thenReturn(
                Future.failedFuture(new ClientErrorException(HTTP_NOT_FOUND)));
        final MessageSender sender = mock(MessageSender.class);
        when(messagingClient.getOrCreateTelemetrySender(anyString())).thenReturn(Future.succeededFuture(sender));

        final MqttPublishMessage msg = mock(MqttPublishMessage.class);
        when(msg.topicName()).thenReturn("t/tenant/device");
        adapter.uploadTelemetryMessage(
                newMqttContext(msg, mockEndpoint()),
                "my-tenant",
                "unknown",
                Buffer.buffer("test")).setHandler(ctx.asyncAssertFailure(t -> {
                    // THEN the message has not been sent downstream
                    verify(sender, never()).send(any(Message.class));
                    // because the device's registration status could not be asserted
                    ctx.assertEquals(HTTP_NOT_FOUND,
                            ((ClientErrorException) t).getErrorCode());
                    // and the message has not been reported as processed
                    verify(metrics, never()).incrementProcessedMessages(anyString(), anyString());
                }));
    }

    /**
     * Verifies that the adapter does not forward a message published by a device if the device belongs to a tenant for
     * which the adapter has been disabled.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testUploadTelemetryMessageFailsForDisabledTenant(final TestContext ctx) {

        // GIVEN an adapter
        final MqttServer server = getMqttServer(false);
        // which is disabled for tenant "my-tenant"
        final TenantObject myTenantConfig = TenantObject.from("my-tenant", true);
        myTenantConfig.addAdapterConfiguration(new JsonObject()
                .put(TenantConstants.FIELD_ADAPTERS_TYPE, ADAPTER_TYPE)
                .put(TenantConstants.FIELD_ENABLED, false));
        when(tenantClient.get(eq("my-tenant"), (SpanContext) any())).thenReturn(Future.succeededFuture(myTenantConfig));
        final AbstractVertxBasedMqttProtocolAdapter<ProtocolAdapterProperties> adapter = getAdapter(server);
        forceClientMocksToConnected();
        final MessageSender sender = mock(MessageSender.class);
        when(messagingClient.getOrCreateTelemetrySender(anyString())).thenReturn(Future.succeededFuture(sender));

        // WHEN a device of "my-tenant" publishes a telemetry message
        final MqttPublishMessage msg = mock(MqttPublishMessage.class);
        when(msg.topicName()).thenReturn("t/tenant/device");
        adapter.uploadTelemetryMessage(
                newMqttContext(msg, mockEndpoint()),
                "my-tenant",
                "the-device",
                Buffer.buffer("test")).setHandler(ctx.asyncAssertFailure(t -> {
                    // THEN the message has not been sent downstream
                    verify(sender, never()).send(any(Message.class));
                    // because the tenant is not enabled
                    ctx.assertEquals(HttpURLConnection.HTTP_FORBIDDEN,
                            ((ClientErrorException) t).getErrorCode());
                    // and the message has not been reported as processed
                    verify(metrics, never()).incrementProcessedMessages(anyString(), anyString());
                }));
    }

    /**
     * Verifies that the adapter waits for an event being settled and accepted by a downstream peer before sending a
     * PUBACK package to the device.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testUploadEventMessageSendsPubAckOnSuccess(final TestContext ctx) {

        // GIVEN an adapter with a downstream event consumer
        final Future<ProtonDelivery> outcome = Future.future();
        givenAnEventSenderForOutcome(outcome);
        testUploadQoS1MessageSendsPubAckOnSuccess(outcome, (adapter, mqttContext) -> {
            adapter.uploadEventMessage(mqttContext, "my-tenant", "4712", mqttContext.message().payload())
                    .setHandler(ctx.asyncAssertSuccess());
        });
    }

    /**
     * Verifies that the adapter waits for a QoS 1 telemetry message being settled
     * and accepted by a downstream peer before sending a PUBACK package to the device.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testUploadTelemetryMessageSendsPubAckOnSuccess(final TestContext ctx) {

        // GIVEN an adapter with a downstream telemetry consumer
        final Future<ProtonDelivery> outcome = Future.future();
        givenAQoS1TelemetrySender(outcome);

        testUploadQoS1MessageSendsPubAckOnSuccess(outcome, (adapter, mqttContext) -> {
            // WHEN forwarding a telemetry message that has been published with QoS 1
            adapter.uploadTelemetryMessage(mqttContext, "my-tenant", "4712", mqttContext.message().payload())
                    .setHandler(ctx.asyncAssertSuccess());
        });
    }

    private void testUploadQoS1MessageSendsPubAckOnSuccess(
            final Future<ProtonDelivery> outcome,
            final BiConsumer<AbstractVertxBasedMqttProtocolAdapter<?>, MqttContext> upload) {

        final MqttServer server = getMqttServer(false);
        final AbstractVertxBasedMqttProtocolAdapter<ProtocolAdapterProperties> adapter = getAdapter(server);

        // WHEN a device publishes a message using QoS 1
        final MqttEndpoint endpoint = mockEndpoint();
        when(endpoint.isConnected()).thenReturn(Boolean.TRUE);
        final Buffer payload = Buffer.buffer("some payload");
        final MqttPublishMessage messageFromDevice = mock(MqttPublishMessage.class);
        when(messageFromDevice.qosLevel()).thenReturn(MqttQoS.AT_LEAST_ONCE);
        when(messageFromDevice.messageId()).thenReturn(5555555);
        when(messageFromDevice.payload()).thenReturn(payload);
        when(messageFromDevice.topicName()).thenReturn("t/tenant/device");
        final MqttContext context = newMqttContext(messageFromDevice, endpoint);
        upload.accept(adapter, context);

        // THEN the device does not receive a PUBACK
        verify(endpoint, never()).publishAcknowledge(anyInt());
        // and the message has not been reported as processed
        verify(metrics, never()).incrementProcessedMessages(anyString(), anyString());

        // until the message has been settled and accepted
        outcome.complete(mock(ProtonDelivery.class));
        verify(endpoint).publishAcknowledge(5555555);
        verify(metrics).incrementProcessedMessages(anyString(), anyString());
    }

    /**
     * Verifies that the adapter does not send a PUBACK package to the device if an event message has not been accepted
     * by the peer.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testOnUnauthenticatedMessageDoesNotSendPubAckOnFailure(final TestContext ctx) {

        // GIVEN an adapter with a downstream event consumer
        final Future<ProtonDelivery> outcome = Future.future();
        givenAnEventSenderForOutcome(outcome);
        final MqttServer server = getMqttServer(false);
        final AbstractVertxBasedMqttProtocolAdapter<ProtocolAdapterProperties> adapter = getAdapter(server);

        // WHEN a device publishes an event
        final Buffer payload = Buffer.buffer("some payload");
        final MqttEndpoint endpoint = mockEndpoint();
        when(endpoint.isConnected()).thenReturn(Boolean.TRUE);
        final MqttPublishMessage messageFromDevice = mock(MqttPublishMessage.class);
        when(messageFromDevice.qosLevel()).thenReturn(MqttQoS.AT_LEAST_ONCE);
        when(messageFromDevice.messageId()).thenReturn(5555555);
        when(messageFromDevice.topicName()).thenReturn("e/tenant/device");
        final MqttContext context = newMqttContext(messageFromDevice, endpoint);

        adapter.uploadEventMessage(context, "my-tenant", "4712", payload).setHandler(ctx.asyncAssertFailure());

        // and the peer rejects the message
        outcome.fail(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST));

        // THEN the device has not received a PUBACK
        verify(endpoint, never()).publishAcknowledge(anyInt());
        // and the message has not been reported as processed
        verify(metrics, never()).incrementProcessedMessages(anyString(), anyString());

    }

    /**
     * Verifies that the adapter includes a message annotation in a downstream
     * message if the device publishes a message with its <em>retain</em> flag set.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testUploadTelemetryMessageIncludesRetainAnnotation(final TestContext ctx) {

        // GIVEN an adapter with a downstream telemetry consumer
        final MessageSender sender = givenAQoS1TelemetrySender(Future.succeededFuture());
        final MqttServer server = getMqttServer(false);
        final AbstractVertxBasedMqttProtocolAdapter<ProtocolAdapterProperties> adapter = getAdapter(server);

        // WHEN a device publishes a message with its retain flag set
        final MqttEndpoint endpoint = mockEndpoint();
        when(endpoint.isConnected()).thenReturn(Boolean.TRUE);
        final MqttPublishMessage messageFromDevice = mock(MqttPublishMessage.class);
        when(messageFromDevice.qosLevel()).thenReturn(MqttQoS.AT_LEAST_ONCE);
        when(messageFromDevice.messageId()).thenReturn(5555555);
        when(messageFromDevice.topicName()).thenReturn("t/tenant/device");
        when(messageFromDevice.isRetain()).thenReturn(Boolean.TRUE);
        final MqttContext context = newMqttContext(messageFromDevice, endpoint);

        adapter.uploadTelemetryMessage(context, "my-tenant", "4712", Buffer.buffer("some payload")).setHandler(ctx.asyncAssertSuccess(ok -> {

            // THEN the device has received a PUBACK
            verify(endpoint).publishAcknowledge(5555555);
            // and the message has been sent downstream
            final ArgumentCaptor<Message> msgCaptor = ArgumentCaptor.forClass(Message.class);
            verify(sender).sendAndWaitForOutcome(msgCaptor.capture(), (SpanContext) any());
            // including the "retain" annotation
            assertThat(MessageHelper.getAnnotation(msgCaptor.getValue(), MessageHelper.ANNOTATION_X_OPT_RETAIN, Boolean.class), is(Boolean.TRUE));
            verify(metrics).incrementProcessedMessages(TelemetryConstants.TELEMETRY_ENDPOINT, "my-tenant");
        }));
    }

    /**
     * Verifies that the adapter creates a command consumer that is checked periodically
     * for a subscription and registers a hook to close the command consumer
     * when the connection to the device is closed.
     * 
     * @param ctx The vert.x test context.
     */
    @SuppressWarnings({ "unchecked" })
    @Test
    public void testOnSubscribeRegistersCommandConsumerCloseHook(final TestContext ctx) {

        // GIVEN a device connected to an adapter
        final Future<ProtonDelivery> outcome = Future.succeededFuture(mock(ProtonDelivery.class));
        final MessageSender sender = givenAnEventSenderForOutcome(outcome);
        final MqttServer server = getMqttServer(false);
        final AbstractVertxBasedMqttProtocolAdapter<ProtocolAdapterProperties> adapter = getAdapter(server);
        final MqttEndpoint endpoint = mockEndpoint();
        when(endpoint.keepAliveTimeSeconds()).thenReturn(10); // 10 seconds

        // WHEN a device subscribes to commands
        final MessageConsumer commandConsumer = mock(MessageConsumer.class);
        when(commandConnection.createCommandConsumer(eq("tenant"), eq("deviceId"), any(Handler.class), any(Handler.class), anyLong()))
            .thenReturn(Future.succeededFuture(commandConsumer));
        final List<MqttTopicSubscription> subscriptions = Collections.singletonList(
                newMockTopicSubscription("control/tenant/deviceId/req/#", MqttQoS.AT_MOST_ONCE));
        final MqttSubscribeMessage msg = mock(MqttSubscribeMessage.class);
        when(msg.messageId()).thenReturn(15);
        when(msg.topicSubscriptions()).thenReturn(subscriptions);

        adapter.onSubscribe(endpoint, null, msg);

        // THEN the adapter creates a command consumer that is checked periodically
        verify(commandConnection).createCommandConsumer(eq("tenant"), eq("deviceId"), any(Handler.class), any(Handler.class), anyLong());
        // and the adapter registers a hook on the connection to the device
        final ArgumentCaptor<Handler<Void>> closeHookCaptor = ArgumentCaptor.forClass(Handler.class);
        verify(endpoint).closeHandler(closeHookCaptor.capture());
        // which closes the command consumer when the device disconnects
        closeHookCaptor.getValue().handle(null);
        verify(commandConsumer).close(any());
        // and sends an empty notification downstream with TTD 0
        final ArgumentCaptor<Message> msgCaptor = ArgumentCaptor.forClass(Message.class);
        verify(sender, times(2)).sendAndWaitForOutcome(msgCaptor.capture(), any());
        assertThat(msgCaptor.getValue().getContentType(), is(EventConstants.CONTENT_TYPE_EMPTY_NOTIFICATION));
        assertThat(MessageHelper.getTimeUntilDisconnect(msgCaptor.getValue()), is(0));
    }

    /**
     * Verifies that the adapter includes a status code for each topic filter
     * in its SUBACK packet.
     * 
     * @param ctx The vert.x test context.
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testOnSubscribeIncludesStatusCodeForEachFilter(final TestContext ctx) {

        // GIVEN a device connected to an adapter
        final Future<ProtonDelivery> outcome = Future.succeededFuture(mock(ProtonDelivery.class));
        final MessageSender sender = givenAnEventSenderForOutcome(outcome);
        final MqttServer server = getMqttServer(false);
        final AbstractVertxBasedMqttProtocolAdapter<ProtocolAdapterProperties> adapter = getAdapter(server);
        final MqttEndpoint endpoint = mockEndpoint();
        when(endpoint.isConnected()).thenReturn(true);

        // WHEN a device sends a SUBSCRIBE packet for several unsupported filters
        final List<MqttTopicSubscription> subscriptions = new ArrayList<>();
        subscriptions.add(newMockTopicSubscription("unsupported/#", MqttQoS.AT_LEAST_ONCE));
        subscriptions.add(newMockTopicSubscription("bumlux/+/+/#", MqttQoS.AT_MOST_ONCE));
        subscriptions.add(newMockTopicSubscription("bumlux/+/+/#", MqttQoS.AT_MOST_ONCE));
        // and for subscribing to commands
        when(commandConnection.createCommandConsumer(eq("tenant-1"), eq("device-A"), any(Handler.class), any(Handler.class), anyLong()))
            .thenReturn(Future.succeededFuture(mock(MessageConsumer.class)));
        subscriptions.add(newMockTopicSubscription("control/tenant-1/device-A/req/#", MqttQoS.AT_MOST_ONCE));
        subscriptions.add(newMockTopicSubscription("control/tenant-1/device-B/req/#", MqttQoS.EXACTLY_ONCE));
        final MqttSubscribeMessage msg = mock(MqttSubscribeMessage.class);
        when(msg.messageId()).thenReturn(15);
        when(msg.topicSubscriptions()).thenReturn(subscriptions);

        adapter.onSubscribe(endpoint, null, msg);

        // THEN the adapter sends a SUBACK packet to the device
        // which contains a failure status code for each unsupported filter
        final ArgumentCaptor<List<MqttQoS>> codeCaptor = ArgumentCaptor.forClass(List.class);
        verify(endpoint).subscribeAcknowledge(eq(15), codeCaptor.capture());
        assertThat(codeCaptor.getValue().size(), is(5));
        assertThat(codeCaptor.getValue().get(0), is(MqttQoS.FAILURE));
        assertThat(codeCaptor.getValue().get(1), is(MqttQoS.FAILURE));
        assertThat(codeCaptor.getValue().get(2), is(MqttQoS.FAILURE));
        assertThat(codeCaptor.getValue().get(3), is(MqttQoS.AT_MOST_ONCE));
        assertThat(codeCaptor.getValue().get(2), is(MqttQoS.FAILURE)); // QoS 2 is not supported
        // and sends an empty notification downstream with TTD -1
        final ArgumentCaptor<Message> msgCaptor = ArgumentCaptor.forClass(Message.class);
        verify(sender).sendAndWaitForOutcome(msgCaptor.capture(), any());
        assertThat(MessageHelper.getDeviceId(msgCaptor.getValue()), is("device-A"));
        assertThat(msgCaptor.getValue().getContentType(), is(EventConstants.CONTENT_TYPE_EMPTY_NOTIFICATION));
        assertThat(MessageHelper.getTimeUntilDisconnect(msgCaptor.getValue()), is(-1));

    }

    private static MqttTopicSubscription newMockTopicSubscription(final String filter, final MqttQoS qos) {
        final MqttTopicSubscription result = mock(MqttTopicSubscription.class);
        when(result.qualityOfService()).thenReturn(qos);
        when(result.topicName()).thenReturn(filter);
        return result;
    }

    /**
     *
     * Verifies that the adapter will accept uploading messages to standard as well
     * as shortened topic names.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testUploadMessageSupportsShortAndLongEndpointNames(final TestContext ctx) {

        // GIVEN an adapter with downstream telemetry & event consumers
        final MqttServer server = getMqttServer(false);
        final AbstractVertxBasedMqttProtocolAdapter<ProtocolAdapterProperties> adapter = getAdapter(server);
        givenAQoS1TelemetrySender(Future.succeededFuture(mock(ProtonDelivery.class)));
        givenAnEventSenderForOutcome(Future.succeededFuture(mock(ProtonDelivery.class)));

        // WHEN a device publishes events and telemetry messages
        final MqttEndpoint endpoint = mockEndpoint();
        when(endpoint.isConnected()).thenReturn(Boolean.TRUE);
        final Buffer payload = Buffer.buffer("some payload");
        final MqttPublishMessage messageFromDevice = mock(MqttPublishMessage.class);
        when(messageFromDevice.qosLevel()).thenReturn(MqttQoS.AT_LEAST_ONCE);
        when(messageFromDevice.messageId()).thenReturn(5555555);
        when(messageFromDevice.payload()).thenReturn(payload);
        when(messageFromDevice.topicName()).thenReturn("bumlux");
        final MqttContext context = newMqttContext(messageFromDevice, endpoint);

        ResourceIdentifier resourceId = ResourceIdentifier.from("telemetry", "my-tenant", "4712");
        adapter.uploadMessage(context, resourceId, messageFromDevice).setHandler(ctx.asyncAssertSuccess());

        resourceId = ResourceIdentifier.from("event", "my-tenant", "4712");
        adapter.uploadMessage(context, resourceId, messageFromDevice).setHandler(ctx.asyncAssertSuccess());

        resourceId = ResourceIdentifier.from("t", "my-tenant", "4712");
        adapter.uploadMessage(context, resourceId, messageFromDevice).setHandler(ctx.asyncAssertSuccess());

        resourceId = ResourceIdentifier.from("e", "my-tenant", "4712");
        adapter.uploadMessage(context, resourceId, messageFromDevice).setHandler(ctx.asyncAssertSuccess());

        resourceId = ResourceIdentifier.from("unknown", "my-tenant", "4712");
        adapter.uploadMessage(context, resourceId, messageFromDevice).setHandler(ctx.asyncAssertFailure());

    }

    /**
     * Verifies the connection metrics for authenticated connections.
     * <p>
     * This test should check if the metrics receive a call to increment and decrement when a connection is being
     * established and then closed.
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testConnectionMetrics() {

        final MqttServer server = getMqttServer(false);
        final AbstractVertxBasedMqttProtocolAdapter<ProtocolAdapterProperties> adapter = getAdapter(server);

        forceClientMocksToConnected();
        when(authHandler.authenticateDevice(any(MqttContext.class))).thenReturn(Future.succeededFuture(new DeviceUser("DEFAULT_TENANT", "4711")));

        final MqttEndpoint endpoint = getMqttEndpointAuthenticated();
        adapter.handleEndpointConnection(endpoint);

        verify(metrics).incrementConnections("DEFAULT_TENANT");
        final ArgumentCaptor<Handler<Void>> closeHandlerCaptor = ArgumentCaptor.forClass(Handler.class);
        verify(endpoint).closeHandler(closeHandlerCaptor.capture());
        closeHandlerCaptor.getValue().handle(null);
        verify(metrics).decrementConnections("DEFAULT_TENANT");
    }

    /**
     * Verifies the connection metrics for unauthenticated connections.
     * <p>
     * This test should check if the metrics receive a call to increment and decrement when a connection is being
     * established and then closed.
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testUnauthenticatedConnectionMetrics() {

        config.setAuthenticationRequired(false);

        final MqttServer server = getMqttServer(false);
        final AbstractVertxBasedMqttProtocolAdapter<ProtocolAdapterProperties> adapter = getAdapter(server);

        forceClientMocksToConnected();

        final MqttEndpoint endpoint = mockEndpoint();
        adapter.handleEndpointConnection(endpoint);

        // THEN the adapter does not try to authenticate the device
        verify(authHandler, never()).authenticateDevice(any(MqttContext.class));
        // and increments the number of unauthenticated connections
        verify(metrics).incrementUnauthenticatedConnections();
        // and when the device closes the connection
        final ArgumentCaptor<Handler<Void>> closeHandlerCaptor = ArgumentCaptor.forClass(Handler.class);
        verify(endpoint).closeHandler(closeHandlerCaptor.capture());
        closeHandlerCaptor.getValue().handle(null);
        // the number of unauthenticated connections is decremented again
        verify(metrics).decrementUnauthenticatedConnections();
    }

    private void forceClientMocksToConnected() {
        when(tenantServiceClient.isConnected()).thenReturn(Future.succeededFuture());
        when(messagingClient.isConnected()).thenReturn(Future.succeededFuture());
        when(deviceRegistrationServiceClient.isConnected()).thenReturn(Future.succeededFuture());
        when(credentialsServiceClient.isConnected()).thenReturn(Future.succeededFuture());
        when(commandConnection.isConnected()).thenReturn(Future.succeededFuture());
    }

    @SuppressWarnings("unchecked")
    private MqttEndpoint getMqttEndpointAuthenticated(final String username, final String password) {
        final MqttEndpoint endpoint = mockEndpoint();
        when(endpoint.auth()).thenReturn(new MqttAuth(username, password));
        when(endpoint.subscribeHandler(any(Handler.class))).thenReturn(endpoint);
        when(endpoint.unsubscribeHandler(any(Handler.class))).thenReturn(endpoint);
        when(endpoint.isCleanSession()).thenReturn(Boolean.FALSE);
        return endpoint;
    }

    private MqttEndpoint getMqttEndpointAuthenticated() {
        return getMqttEndpointAuthenticated("sensor1@DEFAULT_TENANT", "test");
    }

    @SuppressWarnings("unchecked")
    private static MqttServer getMqttServer(final boolean startupShouldFail) {

        final MqttServer server = mock(MqttServer.class);
        when(server.actualPort()).thenReturn(0, 1883);
        when(server.endpointHandler(any(Handler.class))).thenReturn(server);
        when(server.listen(any(Handler.class))).then(invocation -> {
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

    private AbstractVertxBasedMqttProtocolAdapter<ProtocolAdapterProperties> getAdapter(final MqttServer server) {

        final AbstractVertxBasedMqttProtocolAdapter<ProtocolAdapterProperties> adapter = new AbstractVertxBasedMqttProtocolAdapter<ProtocolAdapterProperties>() {

            @Override
            protected String getTypeName() {
                return ADAPTER_TYPE;
            }

            @Override
            protected Future<Void> onPublishedMessage(final MqttContext ctx) {
                final ResourceIdentifier topic = ResourceIdentifier.fromString(ctx.message().topicName());
                return uploadTelemetryMessage(ctx, topic.getTenantId(), topic.getResourceId(), ctx.message().payload());
            }
        };
        adapter.setConfig(config);
        adapter.setMetrics(metrics);
        adapter.setTenantServiceClient(tenantServiceClient);
        adapter.setHonoMessagingClient(messagingClient);
        adapter.setRegistrationServiceClient(deviceRegistrationServiceClient);
        adapter.setCredentialsServiceClient(credentialsServiceClient);
        adapter.setCommandConnection(commandConnection);
        adapter.setAuthHandler(authHandler);

        if (server != null) {
            adapter.setMqttInsecureServer(server);
            adapter.init(vertx, context);
        }

        return adapter;
    }

    private MessageSender givenAnEventSenderForOutcome(final Future<ProtonDelivery> outcome) {

        final MessageSender sender = mock(MessageSender.class);
        when(sender.getEndpoint()).thenReturn(EventConstants.EVENT_ENDPOINT);
        when(sender.send(any(Message.class), (SpanContext) any())).thenReturn(outcome);
        when(sender.sendAndWaitForOutcome(any(Message.class), (SpanContext) any())).thenReturn(outcome);

        when(messagingClient.getOrCreateEventSender(anyString())).thenReturn(Future.succeededFuture(sender));
        return sender;
    }

    private MessageSender givenAQoS0TelemetrySender() {

        final MessageSender sender = mock(MessageSender.class);
        when(sender.getEndpoint()).thenReturn(TelemetryConstants.TELEMETRY_ENDPOINT);
        when(sender.send(any(Message.class), (SpanContext) any())).thenReturn(Future.succeededFuture(mock(ProtonDelivery.class)));
        when(sender.sendAndWaitForOutcome(any(Message.class), (SpanContext) any())).thenThrow(new UnsupportedOperationException());

        when(messagingClient.getOrCreateTelemetrySender(anyString())).thenReturn(Future.succeededFuture(sender));
        return sender;
    }

    private MessageSender givenAQoS1TelemetrySender(final Future<ProtonDelivery> outcome) {

        final MessageSender sender = mock(MessageSender.class);
        when(sender.getEndpoint()).thenReturn(TelemetryConstants.TELEMETRY_ENDPOINT);
        when(sender.send(any(Message.class), (SpanContext) any())).thenThrow(new UnsupportedOperationException());
        when(sender.sendAndWaitForOutcome(any(Message.class), (SpanContext) any())).thenReturn(outcome);

        when(messagingClient.getOrCreateTelemetrySender(anyString())).thenReturn(Future.succeededFuture(sender));
        return sender;
    }
}
