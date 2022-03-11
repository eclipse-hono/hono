/*******************************************************************************
 * Copyright (c) 2016, 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.hono.client.amqp.connection.impl;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static com.google.common.truth.Truth.assertThat;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.hono.client.amqp.config.ClientConfigProperties;
import org.eclipse.hono.client.amqp.connection.AmqpErrorException;
import org.eclipse.hono.client.amqp.connection.ConnectTimeoutException;
import org.eclipse.hono.test.VertxMockSupport;
import org.eclipse.hono.util.Constants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.proton.ProtonClient;
import io.vertx.proton.ProtonClientOptions;
import io.vertx.proton.ProtonConnection;

/**
 * Verifies behavior of {@code ConnectionFactoryImpl}.
 *
 */
@ExtendWith(VertxExtension.class)
@Timeout(value = 5, timeUnit = TimeUnit.SECONDS)
public class ConnectionFactoryImplTest {

    private static final String PREFIX_KEY_PATH = "target/certs/";

    private Vertx vertx;
    private ClientConfigProperties props;

    /**
     * Sets up fixture.
     */
    @BeforeEach
    public void setup() {
        vertx = mock(Vertx.class);
        props = new ClientConfigProperties();
        props.setHost(Constants.LOOPBACK_DEVICE_ADDRESS);
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
    public void testConnectInvokesHandlerOnFailureToConnect(final VertxTestContext ctx) {

        // GIVEN a factory configured to connect to a non-existing server
        vertx = Vertx.vertx();
        final ConnectionFactoryImpl factory = new ConnectionFactoryImpl(vertx, props);

        // WHEN trying to connect to the server
        factory.connect(null, null, null)
            .onComplete(ctx.failing(t -> {
                // THEN the connection attempt fails but does not time out
                ctx.verify(() -> assertFalse(t instanceof ConnectTimeoutException));
                ctx.completeNow();
            }));
    }

    /**
     * Verifies that the given result handler is invoked if a connection attempt times out.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testConnectInvokesHandlerOnConnectTimeout(final VertxTestContext ctx) {

        final long connectTimeout = 200L;

        // GIVEN a factory configured to connect to a server with a mocked ProtonClient that won't actually try to connect
        props.setConnectTimeout((int) connectTimeout);
        final AtomicReference<Handler<Long>> timeoutHandlerRef = new AtomicReference<>();
        when(vertx.setTimer(eq(connectTimeout), VertxMockSupport.anyHandler())).thenAnswer(invocation -> {
            timeoutHandlerRef.set(invocation.getArgument(1));
            return 1L;
        });

        final ProtonClient protonClientMock = mock(ProtonClient.class);
        final ConnectionFactoryImpl factory = new ConnectionFactoryImpl(vertx, props);
        factory.setProtonClient(protonClientMock);

        // WHEN trying to connect to the server
        factory.connect(null, null, null)
            .onComplete(ctx.failing(t -> {
                // THEN the connection attempt fails with a TimeoutException and the given handler is invoked
                ctx.verify(() -> assertTrue(t instanceof ConnectTimeoutException));
                ctx.completeNow();
            }));
        timeoutHandlerRef.get().handle(1L);
    }

    /**
     * Verifies that a connection attempt is failed if there is a timeout opening the connection
     * and verifies that a subsequently received 'open' frame is ignored.
     */
    @Test
    public void testConnectIgnoresSuccessfulOpenAfterTimeout() {

        testConnectIgnoresRemoteOpenAfterTimeout(Future.succeededFuture());
    }

    /**
     * Verifies that a connection attempt is failed if there is a timeout opening the connection
     * and verifies that a subsequently triggered failed open handler is ignored.
     */
    @Test
    public void testConnectIgnoresFailedOpenAfterTimeout() {

        testConnectIgnoresRemoteOpenAfterTimeout(Future.failedFuture(new AmqpErrorException(
                "amqp:resource-limit-exceeded", "connection disallowed by local policy")));
    }

    private void testConnectIgnoresRemoteOpenAfterTimeout(final AsyncResult<ProtonConnection> remoteOpen) {

        // GIVEN a factory configured to time out a connection attempt after 500ms
        final long connectTimeout = 500L;
        props.setConnectTimeout((int) connectTimeout);
        final long closeConnectionTimeout = props.getCloseConnectionTimeout();

        final Context context = VertxMockSupport.mockContext(vertx);
        when(vertx.getOrCreateContext()).thenReturn(context);
        // keep a reference to the connect timeout handler
        final AtomicReference<Handler<Long>> connectTimeoutHandlerRef = new AtomicReference<>();
        when(vertx.setTimer(eq(connectTimeout), VertxMockSupport.anyHandler()))
            .thenAnswer(invocation -> {
                connectTimeoutHandlerRef.set(invocation.getArgument(1));
                return 1L;
            });
        // keep a reference to the close timeout handler
        final AtomicReference<Handler<Long>> closeTimeoutHandlerRef = new AtomicReference<>();
        when(vertx.setTimer(eq(closeConnectionTimeout), VertxMockSupport.anyHandler()))
            .thenAnswer(invocation -> {
                closeTimeoutHandlerRef.set(invocation.getArgument(1));
                return 1L;
            });

        final ProtonConnection protonConnectionMock = mock(ProtonConnection.class, Mockito.RETURNS_SELF);
        final ProtonClient protonClientMock = mock(ProtonClient.class);
        doAnswer(invocation -> {
            final Handler<AsyncResult<ProtonConnection>> resultHandler = invocation.getArgument(5);
            resultHandler.handle(Future.succeededFuture(protonConnectionMock));
            return null;
        }).when(protonClientMock).connect(
                any(ProtonClientOptions.class),
                any(),
                anyInt(),
                any(),
                any(),
                VertxMockSupport.anyHandler());

        final ConnectionFactoryImpl factory = new ConnectionFactoryImpl(vertx, props);
        factory.setProtonClient(protonClientMock);

        // WHEN trying to connect to the server
        final Future<ProtonConnection> connectAttempt = factory.connect(null, null, null);
        assertThat(connectAttempt.isComplete()).isFalse();
        // and the connection attempt times out
        connectTimeoutHandlerRef.get().handle(1L);

        // THEN the connect attempt fails
        assertThat(connectAttempt.failed()).isTrue();
        assertThat(connectAttempt.cause()).isInstanceOf(ConnectTimeoutException.class);

        // and when the peer finally sends its open frame
        final ArgumentCaptor<Handler<AsyncResult<ProtonConnection>>> openHandlerCaptor = VertxMockSupport.argumentCaptorHandler();
        verify(protonConnectionMock).openHandler(openHandlerCaptor.capture());
        openHandlerCaptor.getValue().handle(remoteOpen);

        // the connection will be closed
        verify(protonConnectionMock).close();
        // and when the peer doesn't send its close frame
        closeTimeoutHandlerRef.get().handle(1L);
        // the underlying TCP connection is released
        verify(protonConnectionMock).disconnect();
    }

    /**
     * Verifies that the given result handler is invoked if a connection gets closed after SASL auth was successful and
     * AMQP open frame was sent by client, but no AMQP open frame from server was received yet.
     */
    @Test
    public void testConnectInvokesHandlerOnDisconnectAfterSendingOpenFrame() {
        // GIVEN a factory configured to connect to a server (with mocked connection)
        final ConnectionFactoryImpl factory = new ConnectionFactoryImpl(vertx, props);
        final ProtonClient protonClientMock = mock(ProtonClient.class);
        final ProtonConnection protonConnectionMock = mock(ProtonConnection.class, Mockito.RETURNS_SELF);
        doAnswer(invocation -> {
            final Handler<AsyncResult<ProtonConnection>> resultHandler = invocation.getArgument(5);
            resultHandler.handle(Future.succeededFuture(protonConnectionMock));
            return null;
        }).when(protonClientMock).connect(
                any(ProtonClientOptions.class),
                any(),
                anyInt(),
                any(),
                any(),
                VertxMockSupport.anyHandler());
        factory.setProtonClient(protonClientMock);

        // WHEN trying to connect to the server
        final var connectionResult = factory.connect(new ProtonClientOptions(), null, null);

        // THEN the disconnect handler gets called which calls the given result handler with a failure
        final ArgumentCaptor<Handler<ProtonConnection>> disconnectHandlerCaptor = VertxMockSupport.argumentCaptorHandler();
        verify(protonConnectionMock).disconnectHandler(disconnectHandlerCaptor.capture());
        disconnectHandlerCaptor.getValue().handle(protonConnectionMock);
        // as we call handler ourselves handling is synchronous here
        assertTrue(connectionResult.failed(), "Connection result handler was not failed");
    }

    /**
     * Verifies that the factory does not enable SASL_PLAIN if the username and password are empty
     * strings.
     */
    @Test
    public void testConnectDoesNotUseSaslPlainForEmptyUsernameAndPassword() {

        // GIVEN a factory configured to connect to a server
        final ProtonClientOptions options = new ProtonClientOptions();
        final ProtonClient client = mock(ProtonClient.class);
        final ConnectionFactoryImpl factory = new ConnectionFactoryImpl(vertx, props);
        factory.setProtonClient(client);

        // WHEN connecting to the server using empty strings for username and password
        factory.connect(options, "", "", null, null);

        // THEN the factory does not enable the SASL_PLAIN mechanism when establishing
        // the connection
        final ArgumentCaptor<ProtonClientOptions> optionsCaptor = ArgumentCaptor.forClass(ProtonClientOptions.class);
        verify(client).connect(
                optionsCaptor.capture(),
                anyString(),
                anyInt(),
                eq(""),
                eq(""),
                VertxMockSupport.anyHandler());
        assertFalse(optionsCaptor.getValue().getEnabledSaslMechanisms().contains("PLAIN"));
    }

    /**
     * Verifies that the factory enables SASL_PLAIN if the username and password are non-empty
     * strings.
     */
    @Test
    public void testConnectAddsSaslPlainForNonEmptyUsernameAndPassword() {

        // GIVEN a factory configured to connect to a server
        final ProtonClientOptions options = new ProtonClientOptions();
        final ProtonClient client = mock(ProtonClient.class);
        final ConnectionFactoryImpl factory = new ConnectionFactoryImpl(vertx, props);
        factory.setProtonClient(client);

        // WHEN connecting to the server using non-empty strings for username and password
        factory.connect(options, "user", "pw", null, null);

        // THEN the factory uses SASL_PLAIN when establishing the connection
        final ArgumentCaptor<ProtonClientOptions> optionsCaptor = ArgumentCaptor.forClass(ProtonClientOptions.class);
        verify(client).connect(
                optionsCaptor.capture(),
                anyString(),
                anyInt(),
                eq("user"),
                eq("pw"),
                VertxMockSupport.anyHandler());
        assertTrue(optionsCaptor.getValue().getEnabledSaslMechanisms().contains("PLAIN"));
    }

    /**
     * Verifies that the factory uses TLS when connecting to the peer if no trust store
     * is configured but TLS has been enabled explicitly.
     */
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
        factory.connect(null, null, null);

        // THEN the factory uses TLS when establishing the connection
        final ArgumentCaptor<ProtonClientOptions> optionsCaptor = ArgumentCaptor.forClass(ProtonClientOptions.class);
        verify(client).connect(
                optionsCaptor.capture(),
                eq("remote.host"),
                anyInt(),
                any(),
                any(),
                VertxMockSupport.anyHandler());
        assertTrue(optionsCaptor.getValue().isSsl());
    }

    /**
     * Verifies that the factory uses TLS when connecting to the peer if a trust store
     * is configured but TLS has not been enabled explicitly.
     */
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
        factory.connect(null, null, null);

        // THEN the factory uses TLS when establishing the connection
        final ArgumentCaptor<ProtonClientOptions> optionsCaptor = ArgumentCaptor.forClass(ProtonClientOptions.class);
        verify(client).connect(
                optionsCaptor.capture(),
                eq("remote.host"),
                anyInt(),
                any(),
                any(),
                VertxMockSupport.anyHandler());
        assertTrue(optionsCaptor.getValue().isSsl());
    }

    /**
     * Verifies that the factory sets the maximum frame size on the connection to the value from the client configuration.
     */
    @Test
    public void testConnectSetsMaxFrameSize() {

        // GIVEN a factory configured with a max-message-size
        final ClientConfigProperties config = new ClientConfigProperties();
        config.setHost("remote.host");
        config.setMaxFrameSize(64 * 1024);
        final ProtonClient client = mock(ProtonClient.class);
        final ConnectionFactoryImpl factory = new ConnectionFactoryImpl(vertx, config);
        factory.setProtonClient(client);

        // WHEN connecting to the server
        factory.connect(null, null, null);

        // THEN the factory sets the max-message-size when establishing the connection
        final ArgumentCaptor<ProtonClientOptions> optionsCaptor = ArgumentCaptor.forClass(ProtonClientOptions.class);
        verify(client).connect(
                optionsCaptor.capture(),
                eq("remote.host"),
                anyInt(),
                any(),
                any(),
                VertxMockSupport.anyHandler());
        assertThat(optionsCaptor.getValue().getMaxFrameSize()).isEqualTo(64 * 1024);
    }

    /**
     * Verifies that the factory sets the configured cipher suites on the AMQP connection.
     */
    @Test
    public void testConnectUsesConfiguredCipherSuitesOnly() {

        // GIVEN a factory configured to use a set of cipher suites only
        final ClientConfigProperties config = new ClientConfigProperties();
        config.setHost("remote.host");
        config.setTrustStorePath(PREFIX_KEY_PATH + "trusted-certs.pem");
        config.setSupportedCipherSuites(Arrays.asList("TLS_PSK_WITH_AES_256_CCM_8", "TLS_ECDHE_ECDSA_WITH_AES_256_CCM_8"));
        final ProtonClient client = mock(ProtonClient.class);
        final ConnectionFactoryImpl factory = new ConnectionFactoryImpl(vertx, config);
        factory.setProtonClient(client);

        // WHEN connecting to the server
        factory.connect(null, null, null);

        // THEN the factory uses TLS when establishing the connection
        final ArgumentCaptor<ProtonClientOptions> optionsCaptor = ArgumentCaptor.forClass(ProtonClientOptions.class);
        verify(client).connect(
                optionsCaptor.capture(),
                eq("remote.host"),
                anyInt(),
                any(),
                any(),
                VertxMockSupport.anyHandler());
        assertTrue(optionsCaptor.getValue().isSsl());
        assertThat(optionsCaptor.getValue().getEnabledCipherSuites())
            .containsExactly("TLS_PSK_WITH_AES_256_CCM_8", "TLS_ECDHE_ECDSA_WITH_AES_256_CCM_8");
    }
}
