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

package org.eclipse.hono.client.impl;

import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.HttpURLConnection;
import java.util.concurrent.atomic.AtomicInteger;

import javax.security.sasl.AuthenticationException;

import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.HonoConnection;
import org.eclipse.hono.client.MessageSender;
import org.eclipse.hono.client.RegistrationClient;
import org.eclipse.hono.client.RequestResponseClient;
import org.eclipse.hono.client.ServerErrorException;
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.config.ClientConfigProperties;
import org.eclipse.hono.connection.ConnectionFactory;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.proton.ProtonClientOptions;
import io.vertx.proton.ProtonConnection;
import io.vertx.proton.sasl.SaslSystemException;

/**
 * Test cases verifying the behavior of {@link HonoConnection}.
 *
 */
@RunWith(VertxUnitRunner.class)
public class HonoConnectionImplTest {

    /**
     * Global timeout for each test case.
     */
    @Rule
    public Timeout timeout = Timeout.seconds(3);

    private Vertx vertx;
    private ProtonConnection con;
    private DisconnectHandlerProvidingConnectionFactory connectionFactory;
    private ClientConfigProperties props;
    private HonoConnectionImpl honoConnection;

    /**
     * Sets up fixture.
     */
    @SuppressWarnings("unchecked")
    @Before
    public void setUp() {
        vertx = mock(Vertx.class);
        final Context context = HonoClientUnitTestHelper.mockContext(vertx);
        when(vertx.getOrCreateContext()).thenReturn(context);
        when(vertx.setTimer(anyLong(), any(Handler.class))).thenAnswer(invocation -> {
            final Handler<Void> handler = invocation.getArgument(1);
            handler.handle(null);
            return 0L;
        });
        con = mock(ProtonConnection.class);
        when(con.getRemoteContainer()).thenReturn("server");
        connectionFactory = new DisconnectHandlerProvidingConnectionFactory(con);
        props = new ClientConfigProperties();
        honoConnection = new HonoConnectionImpl(vertx, connectionFactory, props);
    }

    /**
     * Verifies that the client tries to connect a limited
     * number of times only.
     * 
     * @param ctx The vert.x test client.
     */
    @Test
    public void testConnectFailsAfterMaxConnectionAttempts(final TestContext ctx) {

        // GIVEN a client that is configured to reconnect
        // two times before failing
        props.setReconnectAttempts(2);
        props.setConnectTimeout(10);
        // expect three unsuccessful connection attempts
        connectionFactory = new DisconnectHandlerProvidingConnectionFactory(con)
                .setExpectedFailingConnectionAttempts(3);
        honoConnection = new HonoConnectionImpl(vertx, connectionFactory, props);

        // WHEN the client tries to connect
        honoConnection.connect().setHandler(ctx.asyncAssertFailure(t -> {
            // THEN the connection attempt fails
            ctx.assertEquals(HttpURLConnection.HTTP_UNAVAILABLE, ((ServerErrorException) t).getErrorCode());
        }));
        // and the client has indeed tried three times in total before giving up
        assertTrue(connectionFactory.awaitFailure());
    }

    /**
     * Verifies that the client fails with a ClientErrorException with status code 401
     * if it cannot authenticate to the server due to wrong credentials.
     * 
     * @param ctx The vert.x test client.
     */
    @Test
    public void testConnectFailsWithClientErrorForAuthenticationException(final TestContext ctx) {

        // GIVEN a client that is configured to connect
        // to a peer that always throws an AuthenticationException
        props.setReconnectAttempts(2);
        props.setConnectTimeout(10);
        connectionFactory = new DisconnectHandlerProvidingConnectionFactory(con)
                .setExpectedFailingConnectionAttempts(1) // only one connection attempt expected here
                .failWith(new AuthenticationException("Failed to authenticate"));
        honoConnection = new HonoConnectionImpl(vertx, connectionFactory, props);

        // WHEN the client tries to connect
        honoConnection.connect().setHandler(ctx.asyncAssertFailure(t -> {
            // THEN the connection attempt fails due do lack of authorization
            ctx.assertEquals(HttpURLConnection.HTTP_UNAUTHORIZED, ((ServiceInvocationException) t).getErrorCode());
        }));
        // and the client has indeed tried three times in total
        assertTrue(connectionFactory.awaitFailure());
    }

    /**
     * Verifies that the client fails with a ClientErrorException with status code 401
     * if it cannot authenticate to the server because no suitable SASL mechanism was found.
     * 
     * @param ctx The vert.x test client.
     */
    @Test
    public void testConnectFailsWithClientErrorForNoSaslMechanismException(final TestContext ctx) {

        // GIVEN a client that is configured to connect
        // to a peer that always throws a SaslSystemException (as if no credentials were given)
        props.setReconnectAttempts(2);
        props.setConnectTimeout(10);
        connectionFactory = new DisconnectHandlerProvidingConnectionFactory(con)
                .setExpectedFailingConnectionAttempts(1) // only one connection attempt expected here
                .failWith(new SaslSystemException(
                        true, "Could not find a suitable SASL mechanism for the remote peer using the available credentials."));
        honoConnection = new HonoConnectionImpl(vertx, connectionFactory, props);

        // WHEN the client tries to connect
        honoConnection.connect().setHandler(ctx.asyncAssertFailure(t -> {
            // THEN the connection attempt fails due do lack of authorization
            ctx.assertEquals(HttpURLConnection.HTTP_UNAUTHORIZED, ((ServiceInvocationException) t).getErrorCode());
            ctx.assertEquals("no suitable SASL mechanism found for authentication with server", t.getMessage());
        }));
        // and the client has indeed tried three times in total
        assertTrue(connectionFactory.awaitFailure());
    }

    /**
     * Verifies that the client fails with a ServerErrorException with status code 503
     * if it cannot authenticate to the server because of a transient error.
     *
     * @param ctx The vert.x test client.
     */
    @Test
    public void testConnectFailsWithClientErrorForTransientSaslSystemException(final TestContext ctx) {

        // GIVEN a client that is configured to connect
        // to a peer that always throws a SaslSystemException with permanent=false
        props.setReconnectAttempts(2);
        props.setConnectTimeout(10);
        connectionFactory = new DisconnectHandlerProvidingConnectionFactory(con)
                .setExpectedFailingConnectionAttempts(3)
                .failWith(new SaslSystemException(false, "SASL handshake failed due to a transient error"));
        honoConnection = new HonoConnectionImpl(vertx, connectionFactory, props);

        // WHEN the client tries to connect
        honoConnection.connect().setHandler(ctx.asyncAssertFailure(t -> {
            // THEN the connection attempt fails
            ctx.assertEquals(HttpURLConnection.HTTP_UNAVAILABLE, ((ServiceInvocationException) t).getErrorCode());
        }));
        // and the client has indeed tried three times in total
        assertTrue(connectionFactory.awaitFailure());
    }

    /**
     * Verifies that the client fails with a ServerErrorException with status code 503
     * if it cannot authenticate to the server because of a permanent error.
     *
     * @param ctx The vert.x test client.
     */
    @Test
    public void testConnectFailsWithClientErrorForPermanentSaslSystemException(final TestContext ctx) {

        // GIVEN a client that is configured to connect
        // to a peer that always throws a SaslSystemException with permanent=true
        props.setReconnectAttempts(2);
        props.setConnectTimeout(10);
        connectionFactory = new DisconnectHandlerProvidingConnectionFactory(con)
                .setExpectedFailingConnectionAttempts(1) // only one connection attempt expected here
                .failWith(new SaslSystemException(true, "SASL handshake failed due to an unrecoverable error"));
        honoConnection = new HonoConnectionImpl(vertx, connectionFactory, props);

        // WHEN the client tries to connect
        honoConnection.connect().setHandler(ctx.asyncAssertFailure(t -> {
            // THEN the connection attempt fails
            ctx.assertEquals(HttpURLConnection.HTTP_UNAVAILABLE, ((ServiceInvocationException) t).getErrorCode());
        }));
        // and the client has indeed tried three times in total
        assertTrue(connectionFactory.awaitFailure());
    }

    /**
     * Verifies that a request to create a request-response client fails the given
     * future for tracking the attempt if the client is not connected to the peer.
     * 
     * @param ctx The helper to use for running async tests.
     */
    @Test
    public void testGetOrCreateRequestResponseClientFailsWhenNotConnected(final TestContext ctx) {

        honoConnection.getOrCreateRequestResponseClient("the-key", () -> Future.succeededFuture(mock(RequestResponseClient.class)))
        .setHandler(ctx.asyncAssertFailure(t -> {
            ctx.assertTrue(t instanceof ServerErrorException);
        }));
    }

    /**
     * Verifies that a concurrent request to create a request-response client fails the given
     * future for tracking the attempt.
     * 
     * @param ctx The helper to use for running async tests.
     */
    @Test
    public void testGetOrCreateRequestResponseClientFailsIfInvokedConcurrently(final TestContext ctx) {

        // GIVEN a client that already tries to create a registration client for "tenant"
        final Async connected = ctx.async();
        honoConnection.connect(new ProtonClientOptions()).setHandler(ctx.asyncAssertSuccess(ok -> connected.complete()));
        connected.await();

        honoConnection.getOrCreateRequestResponseClient(
                "registration/tenant",
                () -> Future.future());

        // WHEN an additional, concurrent attempt is made to create a client for "tenant"
        honoConnection.getOrCreateRequestResponseClient(
                "registration/tenant",
                () -> {
                    ctx.fail("should not create concurrent client");
                    return Future.succeededFuture(mock(RegistrationClient.class));
                }).setHandler(ctx.asyncAssertFailure(t -> {
                    // THEN the concurrent attempt fails without any attempt being made to create another client
                    ctx.assertTrue(ServerErrorException.class.isInstance(t));
                }));
    }

    /**
     * Verifies that a request to create a request-response client is failed immediately when the
     * underlying connection to the server fails.
     * 
     * @param ctx The Vertx test context.
     */
    @Test
    public void testGetOrCreateRequestResponseClientFailsOnConnectionFailure(final TestContext ctx) {

        // GIVEN a client that tries to create a registration client for "tenant"
        final Async connected = ctx.async();
        honoConnection.connect(new ProtonClientOptions()).setHandler(ctx.asyncAssertSuccess(ok -> connected.complete()));
        connected.await();

        final Async creationFailure = ctx.async();
        final Async supplierInvocation = ctx.async();

        honoConnection.getOrCreateRequestResponseClient(
                "registration/tenant",
                () -> {
                    ctx.assertFalse(creationFailure.isCompleted());
                    supplierInvocation.complete();
                    return Future.future();
                }).setHandler(ctx.asyncAssertFailure(cause -> creationFailure.complete()));

        // WHEN the underlying connection fails
        supplierInvocation.await();
        connectionFactory.getDisconnectHandler().handle(con);

        // THEN all creation requests are failed
        creationFailure.await();
    }

    /**
     * Verifies that a concurrent request to create a sender fails the given future for tracking the attempt.
     * 
     * @param ctx The helper to use for running async tests.
     */
    @Test
    public void testGetOrCreateTelemetrySenderFailsIfInvokedConcurrently(final TestContext ctx) {

        // GIVEN a client that already tries to create a telemetry sender for "tenant"
        final Async connected = ctx.async();
        honoConnection.connect(new ProtonClientOptions()).setHandler(ctx.asyncAssertSuccess(ok -> connected.complete()));
        connected.await();

        honoConnection.getOrCreateSender("telemetry/tenant", () -> Future.future());

        // WHEN an additional, concurrent attempt is made to create a telemetry sender for "tenant"
        honoConnection.getOrCreateSender(
                "telemetry/tenant",
                () -> {
                    ctx.fail("should not create concurrent client");
                    return Future.succeededFuture(mock(MessageSender.class));
                }).setHandler(ctx.asyncAssertFailure(t -> {
                    // THEN the concurrent attempt fails without any attempt being made to create another sender
                    ctx.assertTrue(ServerErrorException.class.isInstance(t));
                }));
    }

    /**
     * Verifies that a request to create a message sender is failed immediately when the
     * underlying connection to the server fails.
     * 
     * @param ctx The Vertx test context.
     */
    @Test
    public void testGetOrCreateSenderFailsOnConnectionFailure(final TestContext ctx) {

        // GIVEN a client that tries to create a telemetry sender for "tenant"
        final Async connected = ctx.async();
        honoConnection.connect(new ProtonClientOptions()).setHandler(ctx.asyncAssertSuccess(ok -> connected.complete()));
        connected.await();

        final Async disconnected = ctx.async();
        final Async supplierInvocation = ctx.async();

        honoConnection.getOrCreateSender(
                "telemetry/tenant",
                () -> {
                    ctx.assertFalse(disconnected.isCompleted());
                    supplierInvocation.complete();
                    return Future.future();
                }).setHandler(ctx.asyncAssertFailure(cause -> {
                    disconnected.complete();
                }));

        // WHEN the underlying connection fails
        supplierInvocation.await();
        connectionFactory.getDisconnectHandler().handle(con);

        // THEN all creation requests are failed
        disconnected.await();
    }

    /**
     * Verifies that a request to create a consumer is failed immediately when the
     * underlying connection to the server fails.
     * 
     * @param ctx The vert.x test context.
     */
    @Test
    public void testCreateConsumerFailsOnConnectionFailure(final TestContext ctx) {

        // GIVEN a connected client that already tries to create a telemetry sender for "tenant"
        final Async connected = ctx.async();
        honoConnection.connect(new ProtonClientOptions()).setHandler(ctx.asyncAssertSuccess(ok -> connected.complete()));
        connected.await();

        final Async creationFailure = ctx.async();
        final Async supplierInvocation = ctx.async();
        honoConnection.createConsumer("tenant", () -> {
            supplierInvocation.complete();
            return Future.future();
        }).setHandler(ctx.asyncAssertFailure(cause -> {
            creationFailure.complete();
        }));
        // wait until the consumer supplier has been invoked
        // so that we can be sure that the disconnect handler for
        // for the creation request has been registered
        supplierInvocation.await();

        // WHEN the underlying connection fails
        connectionFactory.getDisconnectHandler().handle(con);

        // THEN all creation requests are failed
        creationFailure.await();
    }

    /**
     * Verifies that all sender creation locks are cleared when the connection to the server fails.
     * 
     * @param ctx The Vertx test context.
     */
    @Test
    public void testDownstreamDisconnectClearsSenderCreationLocks(final TestContext ctx) {

        // GIVEN a client connected to a peer
        connectionFactory = new DisconnectHandlerProvidingConnectionFactory(con);
        final ProtonClientOptions options = new ProtonClientOptions()
                .setReconnectInterval(50)
                .setReconnectAttempts(0);
        honoConnection = new HonoConnectionImpl(vertx, connectionFactory, props);
        honoConnection.connect(options).setHandler(ctx.asyncAssertSuccess());
        assertTrue(connectionFactory.await());
        connectionFactory.setExpectedSucceedingConnectionAttempts(1);

        // WHEN the downstream connection fails just when the client wants to open a sender link
        final Async senderCreationFailure = ctx.async();
        honoConnection.getOrCreateSender("telemetry/tenant", () -> {
            connectionFactory.getDisconnectHandler().handle(con);
            return Future.future();
        }).setHandler(ctx.asyncAssertFailure(cause -> senderCreationFailure.complete()));

        // THEN the sender creation fails,
        senderCreationFailure.await();
        // the connection is re-established
        assertTrue(connectionFactory.await());
        // and the next attempt to create a sender succeeds
        honoConnection.getOrCreateSender(
                "telemetry/tenant",
                () -> Future.succeededFuture(mock(MessageSender.class))).setHandler(ctx.asyncAssertSuccess());
    }

    /**
     * Verifies that the client tries to re-establish a lost connection to a server.
     * 
     * @param ctx The vert.x test context.
     */
    @Test
    public void testDownstreamDisconnectTriggersReconnect(final TestContext ctx) {

        // GIVEN an client that is connected to a peer to which the
        // connection can be established on the third attempt only
        connectionFactory = new DisconnectHandlerProvidingConnectionFactory(con);
        props.setReconnectAttempts(1);
        final ProtonClientOptions options = new ProtonClientOptions()
                .setReconnectInterval(50)
                .setReconnectAttempts(0);
        honoConnection = new HonoConnectionImpl(vertx, connectionFactory, props);
        honoConnection.connect(options).setHandler(ctx.asyncAssertSuccess());
        assertTrue(connectionFactory.await());
        connectionFactory.setExpectedSucceedingConnectionAttempts(1);

        // WHEN the downstream connection fails
        connectionFactory.getDisconnectHandler().handle(con);

        // THEN the adapter reconnects to the downstream container
        assertTrue(connectionFactory.await());
        connectionFactory.setExpectedSucceedingConnectionAttempts(1);

        // and when the downstream connection fails again
        connectionFactory.getDisconnectHandler().handle(con);

        // THEN the adapter reconnects to the downstream container again
        assertTrue(connectionFactory.await());
    }

    /**
     * Verifies that the client repeatedly tries to connect until a connection is established.
     * 
     * @param ctx The test context.
     */
    @Test
    public void testConnectTriesToReconnectOnFailedConnectAttempt(final TestContext ctx) {

        // GIVEN a client that is configured to connect to a peer
        // to which the connection can be established on the third attempt only
        connectionFactory = new DisconnectHandlerProvidingConnectionFactory(con)
                .setExpectedFailingConnectionAttempts(2);
        props.setReconnectAttempts(2);
        props.setConnectTimeout(10);
        honoConnection = new HonoConnectionImpl(vertx, connectionFactory, props);

        // WHEN trying to connect
        final Async disconnectHandlerInvocation = ctx.async();
        honoConnection.connect(null, failedCon -> disconnectHandlerInvocation.complete()).setHandler(ctx.asyncAssertSuccess());

        // THEN the client fails twice to connect
        assertTrue(connectionFactory.awaitFailure());
        // and succeeds to connect on the third attempt
        assertTrue(connectionFactory.await());
        // and sets the disconnect handler provided as a parameter to the connect method
        connectionFactory.getDisconnectHandler().handle(con);
        disconnectHandlerInvocation.await();
    }

    /**
     * Verifies that the client tries to re-connect to a server instance if the
     * connection is closed by the peer.
     * 
     * @param ctx The test context.
     *
     */
    @Test
    public void testOnRemoteCloseTriggersReconnection(final TestContext ctx) {

        // GIVEN a client that is connected to a server
        final Async connected = ctx.async();
        final Async disconnectHandlerInvocation = ctx.async();
        honoConnection.connect(
                new ProtonClientOptions().setReconnectAttempts(1),
                failedCon -> disconnectHandlerInvocation.complete())
            .setHandler(ctx.asyncAssertSuccess(ok -> connected.complete()));
        connected.await();

        // WHEN the peer closes the connection
        connectionFactory.getCloseHandler().handle(Future.failedFuture("shutting down for maintenance"));

        // THEN the client invokes the disconnect handler provided in the original connect method call
        disconnectHandlerInvocation.await();
        // and the original connection has been closed locally
        verify(con).close();
        verify(con).disconnectHandler(null);
    }

    /**
     * Verifies that it fails to connect after client was shutdown.
     * 
     * @param ctx The test context.
     *
     */
    @Test
    public void testConnectFailsAfterShutdown(final TestContext ctx) {

        honoConnection.connect().compose(ok -> {
            // GIVEN a client that is in the process of shutting down
            honoConnection.shutdown(Future.future());
            // WHEN the client tries to reconnect before shut down is complete
            return honoConnection.connect();
        }).setHandler(ctx.asyncAssertFailure(cause -> {
            // THEN the connection attempt fails
            ctx.assertEquals(HttpURLConnection.HTTP_CONFLICT, ((ClientErrorException) cause).getErrorCode());
        }));
    }

    /**
     * Verifies that if a client disconnects from the server, then an attempt to connect again will be successful.
     *
     * @param ctx The test execution context.
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testConnectSucceedsAfterDisconnect(final TestContext ctx) {

        honoConnection.connect().compose(ok -> {
            // GIVEN a client that is connected to a server
            final Future<Void> disconnected = Future.future();
            // WHEN the client disconnects
            honoConnection.disconnect(disconnected);
            final ArgumentCaptor<Handler<AsyncResult<ProtonConnection>>> closeHandler = ArgumentCaptor.forClass(Handler.class);
            verify(con).closeHandler(closeHandler.capture());
            closeHandler.getValue().handle(Future.succeededFuture(con));
            return disconnected;
        }).compose(d -> {
            // AND tries to reconnect again
            return honoConnection.connect(new ProtonClientOptions());
        }).setHandler(ctx.asyncAssertSuccess(success -> {
            // THEN the connection succeeds
        }));
    }

    /**
     * Verifies that the client does not try to re-connect to a server instance if the client was shutdown.
     * 
     * @param ctx The test context.
     *
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testClientDoesNotTriggerReconnectionAfterShutdown(final TestContext ctx) {

        // GIVEN a client that tries to connect to a server but does not succeed
        final AtomicInteger connectAttempts = new AtomicInteger(0);
        final ConnectionFactory factory = mock(ConnectionFactory.class);
        when(factory.getHost()).thenReturn("server");
        when(factory.getPort()).thenReturn(5672);
        doAnswer(invocation -> {
            final Handler<AsyncResult<ProtonConnection>> resultHandler = invocation.getArgument(3);
            if (connectAttempts.incrementAndGet() == 3) {
                // WHEN client gets shutdown
                honoConnection.shutdown();
            }
            resultHandler.handle(Future.failedFuture(new ServerErrorException(HttpURLConnection.HTTP_UNAVAILABLE)));
            return null;
        }).when(factory).connect(any(), any(Handler.class), any(Handler.class), any(Handler.class));
        honoConnection = new HonoConnectionImpl(vertx, factory, props);
        honoConnection.connect().setHandler(
                ctx.asyncAssertFailure(cause -> {
                    // THEN three attempts have been made to connect
                    ctx.assertTrue(connectAttempts.get() == 3);
                }));
    }
}
