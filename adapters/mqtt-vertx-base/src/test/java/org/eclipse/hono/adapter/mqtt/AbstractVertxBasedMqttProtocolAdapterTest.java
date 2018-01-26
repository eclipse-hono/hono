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
    private HonoClient deviceRegistryClient;
    private RegistrationClient regClient;
    private HonoClientBasedAuthProvider credentialsAuthProvider;
    private ProtocolAdapterProperties config;

    /**
     * Creates clients for the needed micro services and sets the configuration to enable the insecure port.
     */
    @Before
    @SuppressWarnings("unchecked")
    public void setup() {

        config = new ProtocolAdapterProperties();
        config.setInsecurePortEnabled(true);

        regClient = mock(RegistrationClient.class);
        final JsonObject result = new JsonObject().put(RegistrationConstants.FIELD_ASSERTION, "token");
        when(regClient.assertRegistration(anyString(), anyString())).thenReturn(Future.succeededFuture(result));

        messagingClient = mock(HonoClient.class);
        when(messagingClient.connect(
                any(ProtonClientOptions.class),
                any(Handler.class))).thenReturn(Future.succeededFuture(messagingClient));

        deviceRegistryClient = mock(HonoClient.class);
        when(deviceRegistryClient.connect(
                any(ProtonClientOptions.class),
                any(Handler.class))).thenReturn(Future.succeededFuture(deviceRegistryClient));
        when(deviceRegistryClient.getOrCreateRegistrationClient(anyString())).thenReturn(Future.succeededFuture(regClient));

        credentialsAuthProvider = mock(HonoClientBasedAuthProvider.class);
        when(credentialsAuthProvider.start()).thenReturn(Future.succeededFuture());
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
        verify(messagingClient).connect(any(ProtonClientOptions.class), any(Handler.class));
        verify(deviceRegistryClient).connect(any(ProtonClientOptions.class), any(Handler.class));
    }

    // TODO: startup fail test

    /**
     * Verifies that a connection attempt from a device is refused if the adapter is not
     * connected to all of the services it depends on.
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
     * Verifies that an adapter that is configured to not require devices to authenticate,
     * accepts connections from devices not providing any credentials.
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
     * Verifies that an adapter that is configured to require devices to authenticate,
     * rejects connections from devices not providing any credentials.
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
        final MqttServer server = getMqttServer(false);
        final AbstractVertxBasedMqttProtocolAdapter<ProtocolAdapterProperties> adapter = getAdapter(server);
        forceClientMocksToConnected();
        doAnswer(invocation -> {
            Handler<AsyncResult<Device>> resultHandler = invocation.getArgumentAt(1, Handler.class);
            resultHandler.handle(Future.succeededFuture(new Device("DEFAULT_TENANT", "4711")));
            return null;
        }).when(credentialsAuthProvider).authenticate(any(DeviceCredentials.class), any(Handler.class));

        // WHEN a device tries to connect with valid credentials
        final MqttEndpoint endpoint = getMqttEndpointAuthenticated();
        adapter.handleEndpointConnection(endpoint);

        // THEN the device's logical ID is successfully established and corresponding handlers
        // are registered
        final ArgumentCaptor<DeviceCredentials> credentialsCaptor = ArgumentCaptor.forClass(DeviceCredentials.class);
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
        final MqttServer server = getMqttServer(false);
        final AbstractVertxBasedMqttProtocolAdapter<ProtocolAdapterProperties> adapter = getAdapter(server);

        forceClientMocksToConnected();

        // WHEN a device connects that does not provide any credentials
        final MqttEndpoint endpoint = mock(MqttEndpoint.class);
        adapter.handleEndpointConnection(endpoint);

        // THEN the connection is established and handlers are registered
        verify(credentialsAuthProvider, never()).authenticate(any(DeviceCredentials.class), any(Handler.class));
        verify(endpoint).publishHandler(any(Handler.class));
        verify(endpoint).closeHandler(any(Handler.class));
        verify(endpoint).accept(false);
    }

    /**
     * Verifies that the adapter does not forward a message published by a device
     * if the device's registration status cannot be asserted.
     * 
     * @param ctx The vert.x test context.
     */
    @Test
    public void testUploadTelemetryMessageFailsForUnknownDevice(final TestContext ctx) {

        // GIVEN an adapter
        final MqttServer server = getMqttServer(false);
        final AbstractVertxBasedMqttProtocolAdapter<ProtocolAdapterProperties> adapter = getAdapter(server);
        givenATelemetrySenderForOutcome(Future.succeededFuture(mock(ProtonDelivery.class)));

        // WHEN an unknown device publishes a telemetry message
        when(regClient.assertRegistration(eq("unknown"), anyString())).thenReturn(
                Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_NOT_FOUND)));

        adapter.uploadTelemetryMessage(
                new MqttContext(mock(MqttPublishMessage.class), mock(MqttEndpoint.class)),
                "my-tenant",
                "unknown",
                Buffer.buffer("test")).setHandler(ctx.asyncAssertFailure(t -> {
                    // THEN the message has not been sent downstream because the device's
                    // registration status could not be asserted
                    ctx.assertEquals(HttpURLConnection.HTTP_NOT_FOUND,
                            ((ClientErrorException) t).getErrorCode());
                }));
    }

    /**
     * Verifies that the adapter waits for an event being settled and accepted
     * by a downstream peer before sending a PUBACK package to the device.
     * 
     * @param ctx The vert.x test context.
     */
    @Test
    public void testUploadEventMessageSendsPubAckOnSuccess(final TestContext ctx) {

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
        adapter.uploadEventMessage(context, "my-tenant", "4712", payload).setHandler(ctx.asyncAssertSuccess());

        // THEN the device does not receive a PUBACK
        verify(endpoint, never()).publishAcknowledge(anyInt());

        // until the event has been settled and accepted
        outcome.complete(mock(ProtonDelivery.class));
        verify(endpoint).publishAcknowledge(5555555);
    }

    /**
     * Verifies that the adapter does not send a PUBACK package to the device if
     * an event message has not been accepted by the peer.
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

    private void forceClientMocksToConnected() {
        when(messagingClient.isConnected()).thenReturn(Future.succeededFuture(Boolean.TRUE));
        when(deviceRegistryClient.isConnected()).thenReturn(Future.succeededFuture(Boolean.TRUE));
    }

    private MqttEndpoint getMqttEndpointAuthenticated() {
        final MqttEndpoint endpoint = mock(MqttEndpoint.class);
        when(endpoint.auth()).thenReturn(new MqttAuth("sensor1@DEFAULT_TENANT","test"));
        return endpoint;
    }

    @SuppressWarnings("unchecked")
    private static MqttServer getMqttServer(final boolean startupShouldFail) {

        final MqttServer server = mock(MqttServer.class);
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

    private AbstractVertxBasedMqttProtocolAdapter<ProtocolAdapterProperties> getAdapter(final MqttServer server) {

        final AbstractVertxBasedMqttProtocolAdapter<ProtocolAdapterProperties> adapter = new AbstractVertxBasedMqttProtocolAdapter<ProtocolAdapterProperties>() {

            @Override
            protected String getTypeName() {
                return "mqtt";
            }

            @Override
            protected Future<Void> onPublishedMessage(final MqttContext ctx) {
                final ResourceIdentifier topic = ResourceIdentifier.fromString(ctx.message().topicName());
                return uploadTelemetryMessage(ctx, topic.getTenantId(), topic.getResourceId(), ctx.message().payload());
            }
        };
        adapter.setConfig(config);
        adapter.setMetrics(new MqttAdapterMetrics());
        adapter.setHonoMessagingClient(messagingClient);
        adapter.setRegistrationServiceClient(deviceRegistryClient);
        adapter.setCredentialsAuthProvider(credentialsAuthProvider);

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
