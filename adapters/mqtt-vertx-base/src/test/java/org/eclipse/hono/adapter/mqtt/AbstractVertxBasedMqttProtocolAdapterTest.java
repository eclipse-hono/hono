/**
 * Copyright (c) 2017, 2018 Bosch Software Innovations GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 *    Red Hat Inc
 */

package org.eclipse.hono.adapter.mqtt;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.net.HttpURLConnection;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;

import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.HonoClient;
import org.eclipse.hono.client.MessageSender;
import org.eclipse.hono.client.RegistrationClient;
import org.eclipse.hono.client.TenantClient;
import org.eclipse.hono.config.ProtocolAdapterProperties;
import org.eclipse.hono.service.auth.device.Device;
import org.eclipse.hono.service.auth.device.DeviceCredentials;
import org.eclipse.hono.service.auth.device.HonoClientBasedAuthProvider;
import org.eclipse.hono.service.auth.device.UsernamePasswordCredentials;
import org.eclipse.hono.util.EventConstants;
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
import io.vertx.mqtt.messages.MqttPublishMessage;
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
    public Timeout globalTimeout = new Timeout(2, TimeUnit.SECONDS);

    private HonoClient tenantServiceClient;
    private HonoClient credentialsServiceClient;
    private HonoClient messagingClient;
    private HonoClient deviceRegistrationServiceClient;
    private RegistrationClient regClient;
    private TenantClient tenantClient;
    private HonoClientBasedAuthProvider usernamePasswordAuthProvider;
    private ProtocolAdapterProperties config;
    private MqttAdapterMetrics metrics;

    /**
     * Creates clients for the needed micro services and sets the configuration to enable the insecure port.
     */
    @Before
    @SuppressWarnings("unchecked")
    public void setup() {

        config = new ProtocolAdapterProperties();
        config.setInsecurePortEnabled(true);

        metrics = mock(MqttAdapterMetrics.class);

        regClient = mock(RegistrationClient.class);
        final JsonObject result = new JsonObject().put(RegistrationConstants.FIELD_ASSERTION, "token");
        when(regClient.assertRegistration(anyString(), any())).thenReturn(Future.succeededFuture(result));

        tenantClient = mock(TenantClient.class);
        when(tenantClient.get(anyString())).thenAnswer(invocation -> {
            return Future.succeededFuture(TenantObject.from(invocation.getArgument(0), true));
        });

        tenantServiceClient = mock(HonoClient.class);
        when(tenantServiceClient.connect(any(Handler.class))).thenReturn(Future.succeededFuture(tenantServiceClient));
        when(tenantServiceClient.getOrCreateTenantClient()).thenReturn(Future.succeededFuture(tenantClient));

        credentialsServiceClient = mock(HonoClient.class);
        when(credentialsServiceClient.connect(any(Handler.class)))
                .thenReturn(Future.succeededFuture(credentialsServiceClient));

        messagingClient = mock(HonoClient.class);
        when(messagingClient.connect(any(Handler.class))).thenReturn(Future.succeededFuture(messagingClient));

        deviceRegistrationServiceClient = mock(HonoClient.class);
        when(deviceRegistrationServiceClient.connect(any(Handler.class)))
                .thenReturn(Future.succeededFuture(deviceRegistrationServiceClient));
        when(deviceRegistrationServiceClient.getOrCreateRegistrationClient(anyString()))
                .thenReturn(Future.succeededFuture(regClient));

        usernamePasswordAuthProvider = mock(HonoClientBasedAuthProvider.class);
    }

    /**
     * Cleans up fixture.
     */
    @AfterClass
    public static void shutDown() {
        vertx.close();
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
     * Verifies that a connection attempt from a device is refused if the adapter is not connected to all of the
     * services it depends on.
     */
    @Test
    public void testEndpointHandlerFailsWithoutConnect() {

        // GIVEN an endpoint
        final MqttEndpoint endpoint = mock(MqttEndpoint.class);

        final MqttServer server = getMqttServer(false);
        final AbstractVertxBasedMqttProtocolAdapter<ProtocolAdapterProperties> adapter = getAdapter(server);

        adapter.handleEndpointConnection(endpoint);
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
        final MqttEndpoint endpoint = mock(MqttEndpoint.class);
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
        when(tenantClient.get("my-tenant")).thenReturn(Future.succeededFuture(myTenantConfig));
        final AbstractVertxBasedMqttProtocolAdapter<ProtocolAdapterProperties> adapter = getAdapter(server);
        forceClientMocksToConnected();

        // WHEN a device of "my-tenant" tries to connect
        final MqttAuth deviceCredentials = new MqttAuth("device@my-tenant", "irrelevant");
        final MqttEndpoint endpoint = mock(MqttEndpoint.class);
        when(endpoint.auth()).thenReturn(deviceCredentials);
        adapter.handleEndpointConnection(endpoint);

        // THEN the connection is not established
        verify(endpoint).reject(MqttConnectReturnCode.CONNECTION_REFUSED_NOT_AUTHORIZED);
    }

    /**
     * Verifies that an adapter that is configured to require devices to authenticate, rejects connections from devices
     * not providing any credentials.
     */
    @Test
    public void testEndpointHandlerRejectsUnauthenticatedDevices() {

        // GIVEN an adapter that does require devices to authenticate
        final MqttServer server = getMqttServer(false);
        final AbstractVertxBasedMqttProtocolAdapter<ProtocolAdapterProperties> adapter = getAdapter(server);

        forceClientMocksToConnected();

        // WHEN a device connects without providing any credentials
        final MqttEndpoint endpoint = mock(MqttEndpoint.class);
        adapter.handleEndpointConnection(endpoint);

        // THEN the connection is refused
        verify(endpoint).reject(MqttConnectReturnCode.CONNECTION_REFUSED_BAD_USER_NAME_OR_PASSWORD);
    }

    /**
     * Verifies that an adapter retrieves credentials on record for a device connecting to the adapter.
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testEndpointHandlerRetrievesCredentialsOnRecord() {

        // GIVEN an adapter requiring devices to authenticate endpoint
        final MqttServer server = getMqttServer(false);
        config.setAuthenticationRequired(true);
        final AbstractVertxBasedMqttProtocolAdapter<ProtocolAdapterProperties> adapter = getAdapter(server);

        forceClientMocksToConnected();

        final MqttEndpoint endpoint = getMqttEndpointAuthenticated();
        adapter.handleEndpointConnection(endpoint);

        verify(usernamePasswordAuthProvider).authenticate(any(UsernamePasswordCredentials.class), any(Handler.class));
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
        doAnswer(invocation -> {
            Handler<AsyncResult<Device>> resultHandler = invocation.getArgument(1);
            resultHandler.handle(Future.succeededFuture(new Device("DEFAULT_TENANT", "4711")));
            return null;
        }).when(usernamePasswordAuthProvider).authenticate(any(DeviceCredentials.class), any(Handler.class));

        // WHEN a device tries to connect with valid credentials
        final MqttEndpoint endpoint = getMqttEndpointAuthenticated();
        adapter.handleEndpointConnection(endpoint);

        // THEN the device's logical ID is successfully established and corresponding handlers
        // are registered
        final ArgumentCaptor<DeviceCredentials> credentialsCaptor = ArgumentCaptor.forClass(DeviceCredentials.class);
        verify(usernamePasswordAuthProvider).authenticate(credentialsCaptor.capture(), any(Handler.class));
        assertThat(credentialsCaptor.getValue().getAuthId(), is("sensor1"));
        verify(endpoint).accept(false);
        verify(endpoint).publishHandler(any(Handler.class));
        verify(endpoint).closeHandler(any(Handler.class));
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
        final MqttEndpoint endpoint = mock(MqttEndpoint.class);
        adapter.handleEndpointConnection(endpoint);

        // THEN the connection is established and handlers are registered
        verify(usernamePasswordAuthProvider, never()).authenticate(any(DeviceCredentials.class), any(Handler.class));
        verify(endpoint).publishHandler(any(Handler.class));
        verify(endpoint).closeHandler(any(Handler.class));
        verify(endpoint).accept(false);
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
        when(regClient.assertRegistration(eq("unknown"), any())).thenReturn(
                Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_NOT_FOUND)));
        final MessageSender sender = mock(MessageSender.class);
        when(messagingClient.getOrCreateTelemetrySender(anyString())).thenReturn(Future.succeededFuture(sender));

        adapter.uploadTelemetryMessage(
                new MqttContext(mock(MqttPublishMessage.class), mock(MqttEndpoint.class)),
                "my-tenant",
                "unknown",
                Buffer.buffer("test")).setHandler(ctx.asyncAssertFailure(t -> {
                    // THEN the message has not been sent downstream
                    verify(sender, never()).send(any(Message.class));
                    // because the device's registration status could not be asserted
                    ctx.assertEquals(HttpURLConnection.HTTP_NOT_FOUND,
                            ((ClientErrorException) t).getErrorCode());
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
        when(tenantClient.get("my-tenant")).thenReturn(Future.succeededFuture(myTenantConfig));
        final AbstractVertxBasedMqttProtocolAdapter<ProtocolAdapterProperties> adapter = getAdapter(server);
        forceClientMocksToConnected();
        final MessageSender sender = mock(MessageSender.class);
        when(messagingClient.getOrCreateTelemetrySender(anyString())).thenReturn(Future.succeededFuture(sender));

        // WHEN a device of "my-tenant" publishes a telemetry message
        adapter.uploadTelemetryMessage(
                new MqttContext(mock(MqttPublishMessage.class), mock(MqttEndpoint.class)),
                "my-tenant",
                "the-device",
                Buffer.buffer("test")).setHandler(ctx.asyncAssertFailure(t -> {
                    // THEN the message has not been sent downstream
                    verify(sender, never()).send(any(Message.class));
                    // because the tenant is not enabled
                    ctx.assertEquals(HttpURLConnection.HTTP_FORBIDDEN,
                            ((ClientErrorException) t).getErrorCode());
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
        final MqttEndpoint endpoint = mock(MqttEndpoint.class);
        when(endpoint.isConnected()).thenReturn(Boolean.TRUE);
        final Buffer payload = Buffer.buffer("some payload");
        final MqttPublishMessage messageFromDevice = mock(MqttPublishMessage.class);
        when(messageFromDevice.qosLevel()).thenReturn(MqttQoS.AT_LEAST_ONCE);
        when(messageFromDevice.messageId()).thenReturn(5555555);
        when(messageFromDevice.payload()).thenReturn(payload);
        final MqttContext context = new MqttContext(messageFromDevice, endpoint);
        upload.accept(adapter, context);

        // THEN the device does not receive a PUBACK
        verify(endpoint, never()).publishAcknowledge(anyInt());

        // until the message has been settled and accepted
        outcome.complete(mock(ProtonDelivery.class));
        verify(endpoint).publishAcknowledge(5555555);
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
        final MqttEndpoint endpoint = mock(MqttEndpoint.class);
        when(endpoint.isConnected()).thenReturn(Boolean.TRUE);
        final MqttPublishMessage messageFromDevice = mock(MqttPublishMessage.class);
        when(messageFromDevice.qosLevel()).thenReturn(MqttQoS.AT_LEAST_ONCE);
        when(messageFromDevice.messageId()).thenReturn(5555555);
        final MqttContext context = new MqttContext(messageFromDevice, endpoint);

        adapter.uploadEventMessage(context, "my-tenant", "4712", payload).setHandler(ctx.asyncAssertFailure());

        // and the peer rejects the message
        outcome.fail(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST));

        // THEN the device has not received a PUBACK
        verify(endpoint, never()).publishAcknowledge(anyInt());
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
        final MqttEndpoint endpoint = mock(MqttEndpoint.class);
        when(endpoint.isConnected()).thenReturn(Boolean.TRUE);
        final Buffer payload = Buffer.buffer("some payload");
        final MqttPublishMessage messageFromDevice = mock(MqttPublishMessage.class);
        when(messageFromDevice.qosLevel()).thenReturn(MqttQoS.AT_LEAST_ONCE);
        when(messageFromDevice.messageId()).thenReturn(5555555);
        when(messageFromDevice.payload()).thenReturn(payload);
        final MqttContext context = new MqttContext(messageFromDevice, endpoint);

        ResourceIdentifier resourceId = ResourceIdentifier.from("telemetry", "my-tenant", "4712");
        adapter.uploadMessage(context, resourceId, payload).setHandler(ctx.asyncAssertSuccess());

        resourceId = ResourceIdentifier.from("event", "my-tenant", "4712");
        adapter.uploadMessage(context, resourceId, payload).setHandler(ctx.asyncAssertSuccess());

        resourceId = ResourceIdentifier.from("t", "my-tenant", "4712");
        adapter.uploadMessage(context, resourceId, payload).setHandler(ctx.asyncAssertSuccess());

        resourceId = ResourceIdentifier.from("e", "my-tenant", "4712");
        adapter.uploadMessage(context, resourceId, payload).setHandler(ctx.asyncAssertSuccess());

        resourceId = ResourceIdentifier.from("unknown", "my-tenant", "4712");
        adapter.uploadMessage(context, resourceId, payload).setHandler(ctx.asyncAssertFailure());

    }

    private void forceClientMocksToConnected() {
        when(tenantServiceClient.isConnected()).thenReturn(Future.succeededFuture());
        when(messagingClient.isConnected()).thenReturn(Future.succeededFuture());
        when(deviceRegistrationServiceClient.isConnected()).thenReturn(Future.succeededFuture());
        when(credentialsServiceClient.isConnected()).thenReturn(Future.succeededFuture());
    }

    private MqttEndpoint getMqttEndpointAuthenticated(final String username, final String password) {
        final MqttEndpoint endpoint = mock(MqttEndpoint.class);
        when(endpoint.auth()).thenReturn(new MqttAuth(username, password));
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
            Handler<AsyncResult<MqttServer>> handler = (Handler<AsyncResult<MqttServer>>) invocation.getArgument(0);
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
        adapter.setUsernamePasswordAuthProvider(usernamePasswordAuthProvider);

        if (server != null) {
            adapter.setMqttInsecureServer(server);
            adapter.init(vertx, mock(Context.class));
        }

        return adapter;
    }

    private void givenAnEventSenderForOutcome(final Future<ProtonDelivery> outcome) {

        final MessageSender sender = mock(MessageSender.class);
        when(sender.getEndpoint()).thenReturn(EventConstants.EVENT_ENDPOINT);
        when(sender.send(any(Message.class))).thenReturn(outcome);
        when(sender.sendAndWaitForOutcome(any(Message.class))).thenReturn(outcome);

        when(messagingClient.getOrCreateEventSender(anyString())).thenReturn(Future.succeededFuture(sender));
    }

    private void givenAQoS0TelemetrySender() {

        final MessageSender sender = mock(MessageSender.class);
        when(sender.getEndpoint()).thenReturn(TelemetryConstants.TELEMETRY_ENDPOINT);
        when(sender.send(any(Message.class))).thenReturn(Future.succeededFuture(mock(ProtonDelivery.class)));
        when(sender.sendAndWaitForOutcome(any(Message.class))).thenThrow(new UnsupportedOperationException());

        when(messagingClient.getOrCreateTelemetrySender(anyString())).thenReturn(Future.succeededFuture(sender));
    }

    private void givenAQoS1TelemetrySender(final Future<ProtonDelivery> outcome) {

        final MessageSender sender = mock(MessageSender.class);
        when(sender.getEndpoint()).thenReturn(TelemetryConstants.TELEMETRY_ENDPOINT);
        when(sender.send(any(Message.class))).thenThrow(new UnsupportedOperationException());
        when(sender.sendAndWaitForOutcome(any(Message.class))).thenReturn(outcome);

        when(messagingClient.getOrCreateTelemetrySender(anyString())).thenReturn(Future.succeededFuture(sender));
    }

    /**
     * Verify that a missing password rejects the connection.
     * <p>
     * The connection must be rejected with the code {@link MqttConnectReturnCode#CONNECTION_REFUSED_BAD_USER_NAME_OR_PASSWORD}.
     */
    @Test
    public void testMissingPassword() {

        final MqttServer server = getMqttServer(false);
        final AbstractVertxBasedMqttProtocolAdapter<ProtocolAdapterProperties> adapter = getAdapter(server);

        forceClientMocksToConnected();

        final MqttEndpoint endpoint = getMqttEndpointAuthenticated("foo", null);

        adapter.handleEndpointConnection(endpoint);

        verify(endpoint).reject(MqttConnectReturnCode.CONNECTION_REFUSED_BAD_USER_NAME_OR_PASSWORD);
    }

    /**
     * Verify that a missing username rejects the connection.
     * <p>
     * The connection must be rejected with the code {@link MqttConnectReturnCode#CONNECTION_REFUSED_BAD_USER_NAME_OR_PASSWORD}.
     */
    @Test
    public void testMissingUsername() {

        final MqttServer server = getMqttServer(false);
        final AbstractVertxBasedMqttProtocolAdapter<ProtocolAdapterProperties> adapter = getAdapter(server);

        forceClientMocksToConnected();

        final MqttEndpoint endpoint = getMqttEndpointAuthenticated(null, "bar");

        adapter.handleEndpointConnection(endpoint);

        verify(endpoint).reject(MqttConnectReturnCode.CONNECTION_REFUSED_BAD_USER_NAME_OR_PASSWORD);
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
        doAnswer(invocation -> {
            Handler<AsyncResult<Device>> resultHandler = invocation.getArgument(1);
            resultHandler.handle(Future.succeededFuture(new Device("DEFAULT_TENANT", "4711")));
            return null;
        }).when(usernamePasswordAuthProvider).authenticate(any(DeviceCredentials.class), any(Handler.class));

        AtomicReference<Handler<Void>> closeHandlerRef = new AtomicReference<>();

        final MqttEndpoint endpoint = getMqttEndpointAuthenticated();
        doAnswer(invocation -> {
            closeHandlerRef.set(invocation.getArgument(0));
            return endpoint;
        }).when(endpoint).closeHandler(any(Handler.class));

        adapter.handleEndpointConnection(endpoint);

        verify(metrics).incrementMqttConnections("DEFAULT_TENANT");

        closeHandlerRef.get().handle(null);

        verify(metrics).decrementMqttConnections("DEFAULT_TENANT");
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

        AtomicReference<Handler<Void>> closeHandlerRef = new AtomicReference<>();

        final MqttEndpoint endpoint = mock(MqttEndpoint.class);
        doAnswer(invocation -> {
            closeHandlerRef.set(invocation.getArgument(0));
            return endpoint;
        }).when(endpoint).closeHandler(any(Handler.class));

        adapter.handleEndpointConnection(endpoint);

        verify(metrics).incrementUnauthenticatedMqttConnections();

        closeHandlerRef.get().handle(null);

        verify(metrics).decrementUnauthenticatedMqttConnections();
    }
}
