/**
 * Copyright (c) 2017, 2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 */

package org.eclipse.hono.adapter.mqtt;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;

import java.net.HttpURLConnection;
import java.util.concurrent.TimeUnit;

import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.HonoClient;
import org.eclipse.hono.client.MessageSender;
import org.eclipse.hono.client.RegistrationClient;
import org.eclipse.hono.config.ProtocolAdapterProperties;
import org.eclipse.hono.service.auth.device.Device;
import org.eclipse.hono.service.auth.device.DeviceCredentials;
import org.eclipse.hono.service.auth.device.HonoClientBasedAuthProvider;
import org.eclipse.hono.service.auth.device.UsernamePasswordCredentials;
import org.eclipse.hono.util.EventConstants;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.RegistrationConstants;
import org.eclipse.hono.util.ResourceIdentifier;
import org.eclipse.hono.util.TelemetryConstants;
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
import io.vertx.proton.ProtonClientOptions;
import io.vertx.proton.ProtonDelivery;
import io.vertx.proton.ProtonHelper;

/**
 * Verifies behavior of {@link AbstractVertxBasedMqttProtocolAdapter}.
 * 
 */
@RunWith(VertxUnitRunner.class)
public class AbstractVertxBasedMqttProtocolAdapterTest {

    private static Vertx vertx = Vertx.vertx();

    /**
     * Global timeout for all test cases.
     */
    @Rule
    public Timeout globalTimeout = new Timeout(2, TimeUnit.SECONDS);

    private HonoClient messagingClient;
    private HonoClient registrationClient;
    private HonoClientBasedAuthProvider credentialsAuthProvider;
    private ProtocolAdapterProperties config;

    /**
     * Creates clients for the needed micro services and sets the configuration to enable the insecure port.
     */
    @Before
    public void setup() {

        messagingClient = mock(HonoClient.class);
        registrationClient = mock(HonoClient.class);
        credentialsAuthProvider = mock(HonoClientBasedAuthProvider.class);
        when(credentialsAuthProvider.start()).thenReturn(Future.succeededFuture());
        config = new ProtocolAdapterProperties();
        config.setInsecurePortEnabled(true);
    }

    /**
     * Cleans up fixture.
     */
    @AfterClass
    public static void shutDown() {
        vertx.close();
    }

    /**
     * Verifies that an MQTT server is bound to the insecure port during startup and connections
     * to required services have been established.
     * 
     * @param ctx The helper to use for running async tests on vertx.
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testStartup(final TestContext ctx) {

        MqttServer server = getMqttServer(false);
        AbstractVertxBasedMqttProtocolAdapter<ProtocolAdapterProperties> adapter = getAdapter(server);

        Async startup = ctx.async();

        Future<Void> startupTracker = Future.future();
        startupTracker.setHandler(ctx.asyncAssertSuccess(s -> {
            startup.complete();
        }));
        adapter.start(startupTracker);

        startup.await();

        verify(server).listen(any(Handler.class));
        verify(server).endpointHandler(any(Handler.class));
        verify(messagingClient).connect(any(ProtonClientOptions.class), any(Handler.class));
        verify(registrationClient).connect(any(ProtonClientOptions.class), any(Handler.class));
    }

    // TODO: startup fail test

    /**
     * Verifies that a connection attempt from a device is refused if the adapter is not
     * connected to all of the services it depends on.
     */
    @Test
    public void testEndpointHandlerFailsWithoutConnect() {

        // GIVEN an endpoint
        MqttEndpoint endpoint = mock(MqttEndpoint.class);

        MqttServer server = getMqttServer(false);
        AbstractVertxBasedMqttProtocolAdapter<ProtocolAdapterProperties> adapter = getAdapter(server);

        adapter.handleEndpointConnection(endpoint);
        verify(endpoint).reject(MqttConnectReturnCode.CONNECTION_REFUSED_SERVER_UNAVAILABLE);
    }

    /**
     * Verifies that an adapter that is configured to not require devices to authenticate,
     * accepts connections from devices not providing any credentials.
     */
    @Test
    public void testEndpointHandlerAcceptsUnauthenticatedDevices() {

        // GIVEN an adapter that does not require devices to authenticate
        config.setAuthenticationRequired(false);
        MqttServer server = getMqttServer(false);
        AbstractVertxBasedMqttProtocolAdapter<ProtocolAdapterProperties> adapter = getAdapter(server);
        forceClientMocksToConnected();

        // WHEN a device connects without providing credentials
        MqttEndpoint endpoint = mock(MqttEndpoint.class);
        adapter.handleEndpointConnection(endpoint);

        // THEN the connection is established
        verify(endpoint).accept(false);
    }

    /**
     * Verifies that an adapter that is configured to require devices to authenticate,
     * rejects connections from devices not providing any credentials.
     */
    @Test
    public void testEndpointHandlerRejectsUnauthenticatedDevices() {

        // GIVEN an adapter that does require devices to authenticate
        MqttServer server = getMqttServer(false);
        AbstractVertxBasedMqttProtocolAdapter<ProtocolAdapterProperties> adapter = getAdapter(server);

        forceClientMocksToConnected();

        // WHEN a device connects without providing any credentials
        MqttEndpoint endpoint = mock(MqttEndpoint.class);
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
        MqttServer server = getMqttServer(false);
        config.setAuthenticationRequired(true);
        AbstractVertxBasedMqttProtocolAdapter<ProtocolAdapterProperties> adapter = getAdapter(server);

        forceClientMocksToConnected();

        MqttEndpoint endpoint = getMqttEndpointAuthenticated();
        adapter.handleEndpointConnection(endpoint);

        verify(credentialsAuthProvider).authenticate(any(UsernamePasswordCredentials.class), any(Handler.class));
    }

    /**
     * Verifies that on successful authentication the adapter sets appropriate message and close
     * handlers on the client endpoint.
     */
    @SuppressWarnings({ "unchecked" })
    @Test
    public void testAuthenticatedMqttAdapterCreatesMessageHandlersForAuthenticatedDevices() {

        // GIVEN an adapter
        MqttServer server = getMqttServer(false);
        AbstractVertxBasedMqttProtocolAdapter<ProtocolAdapterProperties> adapter = getAdapter(server);
        forceClientMocksToConnected();
        doAnswer(invocation -> {
            Handler<AsyncResult<Device>> resultHandler = invocation.getArgumentAt(1, Handler.class);
            resultHandler.handle(Future.succeededFuture(new Device("DEFAULT_TENANT", "4711")));
            return null;
        }).when(credentialsAuthProvider).authenticate(any(DeviceCredentials.class), any(Handler.class));

        // WHEN a device tries to connect with valid credentials
        MqttEndpoint endpoint = getMqttEndpointAuthenticated();
        adapter.handleEndpointConnection(endpoint);

        // THEN the device's logical ID is successfully established and corresponding handlers
        // are registered
        ArgumentCaptor<DeviceCredentials> credentialsCaptor = ArgumentCaptor.forClass(DeviceCredentials.class);
        verify(credentialsAuthProvider).authenticate(credentialsCaptor.capture(), any(Handler.class));
        assertThat(credentialsCaptor.getValue().getAuthId(), is("sensor1"));
        verify(endpoint).accept(false);
        verify(endpoint).publishHandler(any(Handler.class));
        verify(endpoint).closeHandler(any(Handler.class));
    }

    /**
     * Verifies that the adapter registers message handlers on client connections
     * when device authentication is disabled.
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testUnauthenticatedMqttAdapterCreatesMessageHandlersForAllDevices() {

        // GIVEN an adapter that does not require devices to authenticate
        config.setAuthenticationRequired(false);
        MqttServer server = getMqttServer(false);
        AbstractVertxBasedMqttProtocolAdapter<ProtocolAdapterProperties> adapter = getAdapter(server);

        forceClientMocksToConnected();

        // WHEN a device connects that does not provide any credentials
        MqttEndpoint endpoint = mock(MqttEndpoint.class);
        adapter.handleEndpointConnection(endpoint);

        // THEN the connection is established and handlers are registered
        verify(credentialsAuthProvider, never()).authenticate(any(DeviceCredentials.class), any(Handler.class));
        verify(endpoint).publishHandler(any(Handler.class));
        verify(endpoint).closeHandler(any(Handler.class));
        verify(endpoint).accept(false);
    }

    /**
     * Verifies that the adapter does not forward a message published by an authenticated
     * device, if the device identity does not match the downstream message's address and
     * device ID.
     * 
     * @param ctx The vert.x test context.
     */
    @Test
    public void testOnUnauthenticatedMessageFailsAuthorization(final TestContext ctx) {

        // GIVEN an adapter
        MqttServer server = getMqttServer(false);
        AbstractVertxBasedMqttProtocolAdapter<ProtocolAdapterProperties> adapter = getAdapter(server);

        // WHEN an anonymous device publishes a message to a topic that does not contain
        // a device ID
        final MqttEndpoint endpoint = mock(MqttEndpoint.class);
        final MqttPublishMessage messageFromDevice = mock(MqttPublishMessage.class);
        when(messageFromDevice.topicName()).thenReturn("telemetry/my-tenant");
        final Async sendingFailed = ctx.async();
        Future<Void> result = adapter.onUnauthenticatedMessage(endpoint, messageFromDevice).recover(t -> {
            sendingFailed.complete();
            return Future.failedFuture(t);
        });

        // THEN the message has not been sent downstream because the device is not authorized
        sendingFailed.await();
        ctx.assertTrue(IllegalArgumentException.class.isInstance(result.cause()));

    }

    /**
     * Verifies that the adapter does not forward a message published by an authenticated
     * device, if the device identity does not match the downstream message's address and
     * device ID.
     * 
     * @param ctx The vert.x test context.
     */
    @Test
    public void testOnAuthenticatedMessageFailsAuthorization(final TestContext ctx) {

        // GIVEN an adapter
        MqttServer server = getMqttServer(false);
        AbstractVertxBasedMqttProtocolAdapter<ProtocolAdapterProperties> adapter = getAdapter(server);

        // WHEN an authenticated device publishes a message to a topic
        // that does not match the device's identity
        final Device authenticatedDevice = new Device("my-tenant", "4711");
        final MqttEndpoint endpoint = mock(MqttEndpoint.class);
        final MqttPublishMessage messageFromDevice = mock(MqttPublishMessage.class);
        when(messageFromDevice.topicName()).thenReturn("telemetry/my-tenant/4712");
        final Async sendingFailed = ctx.async();
        Future<Void> result = adapter.onAuthenticatedMessage(endpoint, messageFromDevice, authenticatedDevice).recover(t -> {
            sendingFailed.complete();
            return Future.failedFuture(t);
        });

        // THEN the message has not been sent downstream because the device is not authorized
        sendingFailed.await();
        ctx.assertTrue(ClientErrorException.class.isInstance(result.cause()));

    }

    /**
     * Verifies that the adapter does not wait for a telemetry message being settled and accepted
     * by a downstream peer.
     * 
     * @param ctx The vert.x test context.
     */
    @Test
    public void testOnUnauthenticatedMessageDoesNotWaitForAcceptedOutcome(final TestContext ctx) {

        // GIVEN an adapter with a downstream telemetry consumer
        final Future<ProtonDelivery> outcome = Future.succeededFuture(mock(ProtonDelivery.class));
        givenATelemetrySenderForOutcome(outcome);
        MqttServer server = getMqttServer(false);
        AbstractVertxBasedMqttProtocolAdapter<ProtocolAdapterProperties> adapter = getAdapter(server);

        // WHEN a device publishes a telemetry message
        final Buffer payload = Buffer.buffer("some payload");
        final MqttEndpoint endpoint = mock(MqttEndpoint.class);
        final MqttPublishMessage messageFromDevice = mock(MqttPublishMessage.class);
        when(messageFromDevice.topicName()).thenReturn("telemetry/my-tenant/4712");
        when(messageFromDevice.qosLevel()).thenReturn(MqttQoS.AT_MOST_ONCE);
        when(messageFromDevice.payload()).thenReturn(payload);
        adapter.onUnauthenticatedMessage(endpoint, messageFromDevice).setHandler(ctx.asyncAssertSuccess());
    }

    /**
     * Verifies that the adapter waits for an event being settled and accepted
     * by a downstream peer before sending a PUBACK package to the device.
     * 
     * @param ctx The vert.x test context.
     */
    @Test
    public void testOnUnauthenticatedMessageSendsPubAckOnSuccess(final TestContext ctx) {

        // GIVEN an adapter with a downstream event consumer
        final Future<ProtonDelivery> outcome = Future.future();
        givenAnEventSenderForOutcome(outcome);
        MqttServer server = getMqttServer(false);
        AbstractVertxBasedMqttProtocolAdapter<ProtocolAdapterProperties> adapter = getAdapter(server);

        // WHEN a device publishes an event
        final Buffer payload = Buffer.buffer("some payload");
        final MqttEndpoint endpoint = mock(MqttEndpoint.class);
        when(endpoint.isConnected()).thenReturn(Boolean.TRUE);
        final MqttPublishMessage messageFromDevice = mock(MqttPublishMessage.class);
        when(messageFromDevice.topicName()).thenReturn("event/my-tenant/4712");
        when(messageFromDevice.qosLevel()).thenReturn(MqttQoS.AT_LEAST_ONCE);
        when(messageFromDevice.payload()).thenReturn(payload);
        when(messageFromDevice.messageId()).thenReturn(5555555);
        adapter.onUnauthenticatedMessage(endpoint, messageFromDevice).setHandler(ctx.asyncAssertSuccess());

        // THEN the device does not receive a PUBACK
        verify(endpoint, never()).publishAcknowledge(anyInt());

        // until the event has been settled and accepted
        outcome.complete(mock(ProtonDelivery.class));
        verify(endpoint).publishAcknowledge(5555555);
    }

    /**
     * Verifies that the adapter does not send a PUBACK package to the device if
     * the message has not been accepted by the peer.
     * 
     * @param ctx The vert.x test context.
     */
    @Test
    public void testOnUnauthenticatedMessageDoesNotSendPubAckOnFailure(final TestContext ctx) {

        // GIVEN an adapter with a downstream event consumer
        final Future<ProtonDelivery> outcome = Future.future();
        givenAnEventSenderForOutcome(outcome);
        MqttServer server = getMqttServer(false);
        AbstractVertxBasedMqttProtocolAdapter<ProtocolAdapterProperties> adapter = getAdapter(server);

        // WHEN a device publishes an event that is not accepted
        // by the peer
        final Buffer payload = Buffer.buffer("some payload");
        final MqttEndpoint endpoint = mock(MqttEndpoint.class);
        when(endpoint.isConnected()).thenReturn(Boolean.TRUE);
        final MqttPublishMessage messageFromDevice = mock(MqttPublishMessage.class);
        when(messageFromDevice.topicName()).thenReturn("event/my-tenant/4712");
        when(messageFromDevice.qosLevel()).thenReturn(MqttQoS.AT_LEAST_ONCE);
        when(messageFromDevice.payload()).thenReturn(payload);
        when(messageFromDevice.messageId()).thenReturn(5555555);

        adapter.onUnauthenticatedMessage(endpoint, messageFromDevice).setHandler(ctx.asyncAssertFailure());
        outcome.fail(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST));

        // THEN the device has not received a PUBACK
        verify(endpoint, never()).publishAcknowledge(5555555);
    }

    private void forceClientMocksToConnected() {
        when(messagingClient.isConnected()).thenReturn(Future.succeededFuture(Boolean.TRUE));
        when(registrationClient.isConnected()).thenReturn(Future.succeededFuture(Boolean.TRUE));
    }

    private MqttEndpoint getMqttEndpointAuthenticated() {
        MqttEndpoint endpoint = mock(MqttEndpoint.class);
        when(endpoint.auth()).thenReturn(new MqttAuth("sensor1@DEFAULT_TENANT","test"));
        return endpoint;
    }

    @SuppressWarnings("unchecked")
    private static MqttServer getMqttServer(final boolean startupShouldFail) {

        MqttServer server = mock(MqttServer.class);
        when(server.actualPort()).thenReturn(0, 1883);
        when(server.endpointHandler(any(Handler.class))).thenReturn(server);
        when(server.listen(any(Handler.class))).then(invocation -> {
            Handler<AsyncResult<MqttServer>> handler = (Handler<AsyncResult<MqttServer>>) invocation.getArgumentAt(0, Handler.class);
            if (startupShouldFail) {
                handler.handle(Future.failedFuture("MQTT server intentionally failed to start"));
            } else {
                handler.handle(Future.succeededFuture(server));
            }
            return server;
        });

        return server;
    }

    @SuppressWarnings("unchecked")
    private AbstractVertxBasedMqttProtocolAdapter<ProtocolAdapterProperties> getAdapter(final MqttServer server) {

        AbstractVertxBasedMqttProtocolAdapter<ProtocolAdapterProperties> adapter = new AbstractVertxBasedMqttProtocolAdapter<ProtocolAdapterProperties>() {

            @Override
            protected String getTypeName() {
                return "mqtt";
            }

            @Override
            protected Future<Message> getDownstreamMessage(final MqttPublishMessage message) {
                final ResourceIdentifier topic = ResourceIdentifier.fromString(message.topicName());
                final Message result = ProtonHelper.message();
                result.setAddress(topic.getBasePath());
                MessageHelper.addDeviceId(result, topic.getResourceId());
                return Future.succeededFuture(result);
            }

            @Override
            protected Future<Message> getDownstreamMessage(final MqttPublishMessage message, final Device authenticatedDevice) {
                return getDownstreamMessage(message);
            }
        };
        adapter.setConfig(config);
        adapter.setMetrics(new MqttAdapterMetrics());
        adapter.setHonoMessagingClient(messagingClient);
        when(messagingClient.connect(any(ProtonClientOptions.class), any(Handler.class))).thenReturn(Future.succeededFuture(messagingClient));
        adapter.setRegistrationServiceClient(registrationClient);
        when(registrationClient.connect(any(ProtonClientOptions.class), any(Handler.class))).thenReturn(Future.succeededFuture(registrationClient));
        adapter.setCredentialsAuthProvider(credentialsAuthProvider);

        final RegistrationClient regClient = mock(RegistrationClient.class);
        final JsonObject result = new JsonObject().put(RegistrationConstants.FIELD_ASSERTION, "token");
        when(regClient.assertRegistration(anyString())).thenReturn(Future.succeededFuture(result));

        when(registrationClient.getOrCreateRegistrationClient(anyString())).thenReturn(Future.succeededFuture(regClient));

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

        when(messagingClient.getOrCreateEventSender(anyString())).thenReturn(Future.succeededFuture(sender));
    }

    private void givenATelemetrySenderForOutcome(final Future<ProtonDelivery> outcome) {

        final MessageSender sender = mock(MessageSender.class);
        when(sender.getEndpoint()).thenReturn(TelemetryConstants.TELEMETRY_ENDPOINT);
        when(sender.send(any(Message.class))).thenReturn(outcome);

        when(messagingClient.getOrCreateTelemetrySender(anyString())).thenReturn(Future.succeededFuture(sender));
    }
}
