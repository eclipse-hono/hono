/*******************************************************************************
 * Copyright (c) 2016, 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.hono.connection.impl;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.eclipse.hono.config.ClientConfigProperties;
import org.eclipse.hono.connection.ConnectTimeoutException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.proton.ProtonClient;
import io.vertx.proton.ProtonClientOptions;
import io.vertx.proton.ProtonConnection;

/**
 * Verifies behavior of {@code ConnectionFactoryImpl}.
 *
 */
@RunWith(VertxUnitRunner.class)
public class ConnectionFactoryImplTest {

    private static final String PREFIX_KEY_PATH = "target/certs/";

    private final Vertx vertx = Vertx.vertx();
    private ClientConfigProperties props;

    /**
     * Sets up fixture.
     */
    @Before
    public void setup() {
        props = new ClientConfigProperties();
        props.setHost("127.0.0.1");
        props.setPort(25673); // no server running on port
        props.setAmqpHostname("hono");
        props.setName("client");
    }

    /**
     * Verifies that the given result handler is invoked if a connection attempt fails.
     * 
     * @param ctx The vert.x test context.
     */
    @Test
    public void testConnectInvokesHandlerOnFailureToConnect(final TestContext ctx) {

        // GIVEN a factory configured to connect to a non-existing server
        final ConnectionFactoryImpl factory = new ConnectionFactoryImpl(vertx, props);

        // WHEN trying to connect to the server
        final Async handlerInvocation = ctx.async();

        factory.connect(null, null, null, ctx.asyncAssertFailure(t -> {
            ctx.assertFalse(t instanceof ConnectTimeoutException);
            handlerInvocation.complete();
        }));

        // THEN the connection attempt fails and the given handler is invoked
        handlerInvocation.await(2000);
    }

    /**
     * Verifies that the given result handler is invoked if a connection attempt times out.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testConnectInvokesHandlerOnConnectTimeout(final TestContext ctx) {

        // GIVEN a factory configured to connect to a server with a mocked ProtonClient that won't actually try to connect
        props.setConnectTimeout(10);
        final ConnectionFactoryImpl factory = new ConnectionFactoryImpl(vertx, props);
        final ProtonClient protonClientMock = mock(ProtonClient.class);
        factory.setProtonClient(protonClientMock);

        // WHEN trying to connect to the server
        final Async handlerInvocation = ctx.async();

        factory.connect(null, null, null, ctx.asyncAssertFailure(t -> {
            ctx.assertTrue(t instanceof ConnectTimeoutException);
            handlerInvocation.complete();
        }));

        // THEN the connection attempt fails with a TimeoutException and the given handler is invoked
        handlerInvocation.await(2000);
    }

    /**
     * Verifies that the given result handler is invoked if a connection gets closed after SASL auth was successful and
     * AMQP open frame was sent by client, but no AMQP open frame from server was received yet.
     *
     * @param ctx The vert.x test context.
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testConnectInvokesHandlerOnDisconnectAfterSendingOpenFrame(final TestContext ctx) {
        // GIVEN a factory configured to connect to a server (with mocked connection)
        final ConnectionFactoryImpl factory = new ConnectionFactoryImpl(vertx, props);
        final ProtonClient protonClientMock = mock(ProtonClient.class);
        final ProtonConnection protonConnectionMock = mock(ProtonConnection.class, Mockito.RETURNS_SELF);
        doAnswer(invocation -> {
            final Handler<AsyncResult<ProtonConnection>> resultHandler = invocation.getArgument(5);
            resultHandler.handle(Future.succeededFuture(protonConnectionMock));
            return null;
        }).when(protonClientMock).connect(any(ProtonClientOptions.class), any(), anyInt(), any(), any(), any(Handler.class));
        factory.setProtonClient(protonClientMock);

        // WHEN trying to connect to the server
        final Promise<ProtonConnection> resultHandler = Promise.promise();

        factory.connect(new ProtonClientOptions(), null, null, resultHandler);

        // THEN the disconnect handler gets called which calls the given result handler with a failure
        final ArgumentCaptor<Handler<ProtonConnection>> disconnectHandlerCaptor = ArgumentCaptor.forClass(Handler.class);
        verify(protonConnectionMock).disconnectHandler(disconnectHandlerCaptor.capture());
        disconnectHandlerCaptor.getValue().handle(protonConnectionMock);
        // as we call handler ourselves handling is synchronous here
        assertTrue("Connection result handler was not failed", resultHandler.future().failed());
    }

    /**
     * Verifies that the factory does not enable SASL_PLAIN if the username and password are empty
     * strings.
     * 
     * @param ctx The vert.x test context.
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testConnectDoesNotUseSaslPlainForEmptyUsernameAndPassword(final TestContext ctx) {

        // GIVEN a factory configured to connect to a server
        final ProtonClientOptions options = new ProtonClientOptions();
        final ProtonClient client = mock(ProtonClient.class);
        final ConnectionFactoryImpl factory = new ConnectionFactoryImpl(vertx, props);
        factory.setProtonClient(client);

        // WHEN connecting to the server using empty strings for username and password
        factory.connect(options, "", "", null, null, c -> {});

        // THEN the factory does not enable the SASL_PLAIN mechanism when establishing
        // the connection
        final ArgumentCaptor<ProtonClientOptions> optionsCaptor = ArgumentCaptor.forClass(ProtonClientOptions.class);
        verify(client).connect(optionsCaptor.capture(), anyString(), anyInt(), eq(""), eq(""), any(Handler.class));
        assertFalse(optionsCaptor.getValue().getEnabledSaslMechanisms().contains("PLAIN"));
    }

    /**
     * Verifies that the factory enables SASL_PLAIN if the username and password are non-empty
     * strings.
     * 
     * @param ctx The vert.x test context.
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testConnectAddsSaslPlainForNonEmptyUsernameAndPassword(final TestContext ctx) {

        // GIVEN a factory configured to connect to a server
        final ProtonClientOptions options = new ProtonClientOptions();
        final ProtonClient client = mock(ProtonClient.class);
        final ConnectionFactoryImpl factory = new ConnectionFactoryImpl(vertx, props);
        factory.setProtonClient(client);

        // WHEN connecting to the server using non-empty strings for username and password
        factory.connect(options, "user", "pw", null, null, c -> {});

        // THEN the factory uses SASL_PLAIN when establishing the connection
        final ArgumentCaptor<ProtonClientOptions> optionsCaptor = ArgumentCaptor.forClass(ProtonClientOptions.class);
        verify(client).connect(optionsCaptor.capture(), anyString(), anyInt(), eq("user"), eq("pw"), any(Handler.class));
        assertTrue(optionsCaptor.getValue().getEnabledSaslMechanisms().contains("PLAIN"));
    }

    /**
     * Verifies that the factory uses TLS when connecting to the peer if no trust store
     * is configured but TLS has been enabled explicitly.
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testConnectEnablesSslIfExplicitlyConfigured() {

        // GIVEN a factory configured to connect to a server using TLS
        final ClientConfigProperties config = new ClientConfigProperties();
        config.setHost("remote.host");
        config.setTlsEnabled(true);
        final ProtonClient client = mock(ProtonClient.class);
        final ConnectionFactoryImpl factory = new ConnectionFactoryImpl(vertx, config);
        factory.setProtonClient(client);

        // WHEN connecting to the server
        factory.connect(null, null, null, c -> {});

        // THEN the factory uses TLS when establishing the connection
        final ArgumentCaptor<ProtonClientOptions> optionsCaptor = ArgumentCaptor.forClass(ProtonClientOptions.class);
        verify(client).connect(optionsCaptor.capture(), eq("remote.host"), anyInt(), any(), any(), any(Handler.class));
        assertTrue(optionsCaptor.getValue().isSsl());
    }

    /**
     * Verifies that the factory uses TLS when connecting to the peer if a trust store
     * is configured but TLS has not been enabled explicitly.
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testConnectEnablesSslIfTrustStoreIsConfigured() {

        // GIVEN a factory configured to use a specific trust store
        final ClientConfigProperties config = new ClientConfigProperties();
        config.setHost("remote.host");
        config.setTrustStorePath(PREFIX_KEY_PATH + "trusted-certs.pem");
        final ProtonClient client = mock(ProtonClient.class);
        final ConnectionFactoryImpl factory = new ConnectionFactoryImpl(vertx, config);
        factory.setProtonClient(client);

        // WHEN connecting to the server
        factory.connect(null, null, null, c -> {});

        // THEN the factory uses TLS when establishing the connection
        final ArgumentCaptor<ProtonClientOptions> optionsCaptor = ArgumentCaptor.forClass(ProtonClientOptions.class);
        verify(client).connect(optionsCaptor.capture(), eq("remote.host"), anyInt(), any(), any(), any(Handler.class));
        assertTrue(optionsCaptor.getValue().isSsl());
    }
}
