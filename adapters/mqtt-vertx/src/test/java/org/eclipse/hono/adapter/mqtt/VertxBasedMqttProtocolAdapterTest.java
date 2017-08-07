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

import static org.mockito.Mockito.*;

import io.netty.handler.codec.mqtt.MqttConnectReturnCode;
import io.vertx.core.*;
import io.vertx.core.eventbus.EventBus;
import io.vertx.mqtt.MqttAuth;
import io.vertx.mqtt.MqttEndpoint;
import io.vertx.mqtt.MqttServer;
import org.eclipse.hono.adapter.mqtt.credentials.MqttUsernamePassword;
import org.eclipse.hono.client.CredentialsClient;
import org.eclipse.hono.client.HonoClient;
import org.eclipse.hono.config.ServiceConfigProperties;
import org.eclipse.hono.util.Constants;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.vertx.core.http.HttpServer;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.ext.web.Router;
import io.vertx.proton.ProtonClientOptions;
import org.mockito.Mock;

/**
 * Verifies behavior of {@link VertxBasedMqttProtocolAdapter}.
 * 
 */
@RunWith(VertxUnitRunner.class)
public class VertxBasedMqttProtocolAdapterTest {
    private static final int IANA_MQTT_PORT = 1883;
    private static final int IANA_SECURE_MQTT_PORT = 8883;

    HonoClient messagingClient;
    HonoClient registrationClient;
    HonoClient credentialsClient;

    MqttProtocolAdapterProperties config;

    private Vertx vertx;

    /**
     * Cleans up fixture.
     */
    @After
    public void shutDown() {
        vertx.close();
    }

    /**
     * Creates a 
     */
    @Before
    public void setup() {

        vertx = Vertx.vertx();

        messagingClient = mock(HonoClient.class);
        registrationClient = mock(HonoClient.class);
        credentialsClient = mock(HonoClient.class);
        config = new MqttProtocolAdapterProperties();
        config.setInsecurePortEnabled(true);
    }

    /**
     * TODO:
     * Verifies that a client provided http server is started instead of creating and starting a new http server.
     * 
     * @param ctx The helper to use for running async tests on vertx.
     * @throws Exception if the test fails.
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testStartup(final TestContext ctx) throws Exception {

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
        verify(credentialsClient).connect(any(ProtonClientOptions.class), any(Handler.class), any(Handler.class));
    }

    // TODO: startup fail test

    @SuppressWarnings("unchecked")
    @Test
    public void testEndpointHandlerFailsWithoutConnect(final TestContext ctx) throws Exception {

        // GIVEN an endpoint
        MqttEndpoint endpoint = mock(MqttEndpoint.class);

        MqttServer server = getMqttServer(false);
        VertxBasedMqttProtocolAdapter adapter = getAdapter(server);

        adapter.handleEndpointConnection(endpoint);
        verify(endpoint).reject(MqttConnectReturnCode.CONNECTION_REFUSED_SERVER_UNAVAILABLE);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testEndpointHandlerSetsPublishAndCloseHandlers(final TestContext ctx) throws Exception {

        // GIVEN an endpoint
        MqttEndpoint endpoint = mock(MqttEndpoint.class);
        when(endpoint.auth()).thenReturn(new MqttAuth() {
            @Override
            public String userName() {
                return "billie";
            }

            @Override
            public String password() {
                return "test";
            }
        });

        MqttServer server = getMqttServer(false);
        VertxBasedMqttProtocolAdapter adapter = getAdapter(server);

        forceClientMocksToConnected();

        adapter.handleEndpointConnection(endpoint);
        verify(endpoint).publishHandler(any(Handler.class));
        verify(endpoint).closeHandler(any(Handler.class));
    }

    private void forceClientMocksToConnected() {
        when(messagingClient.isConnected()).thenReturn(true);
        when(registrationClient.isConnected()).thenReturn(true);
        when(credentialsClient.isConnected()).thenReturn(true);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testEndpointHandlerVerifiesCredentialsIfNotConfigured(final TestContext ctx) throws Exception {

        // GIVEN an endpoint
        MqttEndpoint endpoint = mock(MqttEndpoint.class);

        MqttServer server = getMqttServer(false);
        config.setAuthenticationRequired(false);
        VertxBasedMqttProtocolAdapter adapter = getAdapter(server);

        forceClientMocksToConnected();

        adapter.handleEndpointConnection(endpoint);
        verify(endpoint).accept(false);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testEndpointHandlerVerifiesCredentialsIfConfigured(final TestContext ctx) throws Exception {

        // GIVEN an endpoint
        MqttEndpoint endpoint = mock(MqttEndpoint.class);
        when(endpoint.auth()).thenReturn(new MqttAuth() {
            @Override
            public String userName() {
                return "billie";
            }

            @Override
            public String password() {
                return "test";
            }
        });

        MqttServer server = getMqttServer(false);
        config.setAuthenticationRequired(true);
        config.setSingleTenant(true);
        VertxBasedMqttProtocolAdapter adapter = getAdapter(server);

        forceClientMocksToConnected();

        adapter.handleEndpointConnection(endpoint);
        verify(credentialsClient).getOrCreateCredentialsClient(matches(Constants.DEFAULT_TENANT), any(Handler.class));
    }

    private MqttServer getMqttServer(final boolean startupShouldFail) {

        MqttServer server = mock(MqttServer.class);
        when(server.actualPort()).thenReturn(0, 1883);
        when(server.endpointHandler(any(Handler.class))).thenReturn(server);
        when(server.listen(any(Handler.class))).then(invocation -> {
            Handler<AsyncResult<MqttServer>> handler = (Handler<AsyncResult<MqttServer>>) invocation.getArgumentAt(0, Handler.class);
            if (startupShouldFail) {
                handler.handle(Future.failedFuture("mqtt server intentionally failed to start"));
            } else {
                handler.handle(Future.succeededFuture(server));
            }
            return server;
        });

        return server;
    }

    private VertxBasedMqttProtocolAdapter getAdapter(final MqttServer server) {
        VertxBasedMqttProtocolAdapter adapter = new VertxBasedMqttProtocolAdapter() {
            @Override
            public void setConfig(final MqttProtocolAdapterProperties configuration) {
                setSpecificConfig(configuration);
            }
        };
        adapter.setMqttInsecureServer(server);
        adapter.setConfig(config);
        adapter.setHonoMessagingClient(messagingClient);
        adapter.setRegistrationServiceClient(registrationClient);
        adapter.setCredentialsServiceClient(credentialsClient);
        adapter.init(vertx, mock(Context.class));
        return adapter;
    }
}
