/**
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
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
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

import org.eclipse.hono.client.HonoClient;
import org.eclipse.hono.config.ProtocolAdapterProperties;
import org.eclipse.hono.service.auth.device.Device;
import org.eclipse.hono.service.auth.device.DeviceCredentials;
import org.eclipse.hono.service.auth.device.HonoClientBasedAuthProvider;
import org.eclipse.hono.service.auth.device.UsernamePasswordCredentials;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;

import io.netty.handler.codec.mqtt.MqttConnectReturnCode;
import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.mqtt.MqttAuth;
import io.vertx.mqtt.MqttEndpoint;
import io.vertx.mqtt.MqttServer;
import io.vertx.proton.ProtonClientOptions;

/**
 * Verifies behavior of {@link VertxBasedMqttProtocolAdapter}.
 * 
 */
@RunWith(VertxUnitRunner.class)
public class VertxBasedMqttProtocolAdapterTest {

    private HonoClient messagingClient;
    private HonoClient registrationClient;
    private HonoClientBasedAuthProvider credentialsAuthProvider;
    private ProtocolAdapterProperties config;
    private Vertx vertx;

    /**
     * Creates clients for the needed microservices and sets the configuration to enable the insecure port.
     */
    @Before
    public void setup() {

        vertx = Vertx.vertx();

        messagingClient = mock(HonoClient.class);
        registrationClient = mock(HonoClient.class);
        credentialsAuthProvider = mock(HonoClientBasedAuthProvider.class);
        config = new ProtocolAdapterProperties();
        config.setInsecurePortEnabled(true);
    }

    /**
     * Cleans up fixture.
     */
    @After
    public void shutDown() {
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
        VertxBasedMqttProtocolAdapter adapter = getAdapter(server);

        Async startup = ctx.async();

        Future<Void> startupTracker = Future.future();
        startupTracker.setHandler(ctx.asyncAssertSuccess(s -> {
            startup.complete();
        }));
        adapter.start(startupTracker);

        startup.await(1000);

        verify(server).listen(any(Handler.class));
        verify(server).endpointHandler(any(Handler.class));
        verify(messagingClient).connect(any(ProtonClientOptions.class), any(Handler.class), any(Handler.class));
        verify(registrationClient).connect(any(ProtonClientOptions.class), any(Handler.class), any(Handler.class));
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
        VertxBasedMqttProtocolAdapter adapter = getAdapter(server);

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
        VertxBasedMqttProtocolAdapter adapter = getAdapter(server);
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
        VertxBasedMqttProtocolAdapter adapter = getAdapter(server);

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
        VertxBasedMqttProtocolAdapter adapter = getAdapter(server);

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
        VertxBasedMqttProtocolAdapter adapter = getAdapter(server);
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

    @SuppressWarnings("unchecked")
    @Test
    public void testUnauthenticatedMqttAdapterCreatesMessageHandlersForAllDevices() {

        // GIVEN an adapter that does not require devices to authenticate
        config.setAuthenticationRequired(false);
        MqttServer server = getMqttServer(false);
        VertxBasedMqttProtocolAdapter adapter = getAdapter(server);

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

    private void forceClientMocksToConnected() {
        when(messagingClient.isConnected()).thenReturn(true);
        when(registrationClient.isConnected()).thenReturn(true);
    }

    private MqttEndpoint getMqttEndpointAuthenticated() {
        MqttEndpoint endpoint = mock(MqttEndpoint.class);
        when(endpoint.auth()).thenReturn(new MqttAuth() {
            @Override
            public String userName() {
                return "sensor1@DEFAULT_TENANT";
            }

            @Override
            public String password() {
                return "test";
            }
        });
        return endpoint;
    }

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

    private VertxBasedMqttProtocolAdapter getAdapter(final MqttServer server) {
        VertxBasedMqttProtocolAdapter adapter = spy(VertxBasedMqttProtocolAdapter.class);
        adapter.setMqttInsecureServer(server);
        adapter.setConfig(config);
        adapter.setHonoMessagingClient(messagingClient);
        adapter.setRegistrationServiceClient(registrationClient);
        adapter.setCredentialsAuthProvider(credentialsAuthProvider);
        adapter.init(vertx, mock(Context.class));

        return adapter;
    }
}
