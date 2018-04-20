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

package org.eclipse.hono.client.impl;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;

import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.MessageSender;
import org.eclipse.hono.client.RegistrationClient;
import org.eclipse.hono.client.RequestResponseClient;
import org.eclipse.hono.client.ServerErrorException;
import org.eclipse.hono.config.ClientConfigProperties;
import org.eclipse.hono.connection.ConnectionFactory;
import org.eclipse.hono.util.Constants;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.proton.ProtonClientOptions;
import io.vertx.proton.ProtonConnection;

/**
 * Test cases verifying the behavior of {@code HonoClient}.
 *
 */
@RunWith(VertxUnitRunner.class)
public class HonoClientImplTest {

    /**
     * Global timeout for each test case.
     */
    @Rule
    public Timeout timeout = Timeout.seconds(5);

    private static Vertx vertx;

    private ProtonConnection con;
    private DisconnectHandlerProvidingConnectionFactory connectionFactory;
    private ClientConfigProperties props;
    private HonoClientImpl client;

    /**
     * Sets up vertx.
     */
    @BeforeClass
    public static void setUpVertx() {
        vertx = Vertx.vertx();
    }

    /**
     * Sets up fixture.
     */
    @Before
    public void setUp() {
        con = mock(ProtonConnection.class);
        when(con.getRemoteContainer()).thenReturn("server");
        connectionFactory = new DisconnectHandlerProvidingConnectionFactory(con);
        props = new ClientConfigProperties();
        client = new HonoClientImpl(vertx, connectionFactory, props);
    }

    /**
     * Cleans up after test execution.
     * 
     * @param ctx The helper to use for running async tests.
     */
    @AfterClass
    public static void shutdown(final TestContext ctx) {
        if (vertx != null) {
            vertx.close(ctx.asyncAssertSuccess());
        }
    }

    /**
     * Verifies that the client tries to connect a limited
     * number of times only.
     * 
     * @param ctx The vert.x test client.
     */
    @Test
    public void testConnectFailsAfterMaxConnectionAttempts(final TestContext ctx) {

        // GIVEN a client that is configured to connect
        // to a peer that is not listening
        props.setHost(InetAddress.getLoopbackAddress().getHostAddress());
        props.setPort(45000);
        client = new HonoClientImpl(vertx, props);
        final ProtonClientOptions options = new ProtonClientOptions()
                .setConnectTimeout(50)
                .setReconnectAttempts(3)
                .setReconnectInterval(50);

        // WHEN the client tries to connect
        client.connect(options).setHandler(ctx.asyncAssertFailure(t -> {
            // THEN the connection attempt fails
            ctx.assertEquals(HttpURLConnection.HTTP_UNAVAILABLE, ((ServerErrorException) t).getErrorCode());
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
        client.connect(new ProtonClientOptions()).setHandler(ctx.asyncAssertSuccess(ok -> connected.complete()));
        connected.await();

        client.getOrCreateRequestResponseClient(
                "registration/tenant",
                () -> Future.future(),
                result -> {});

        // WHEN an additional, concurrent attempt is made to create a client for "tenant"
        client.getOrCreateRequestResponseClient(
                "registration/tenant",
                () -> {
                    ctx.fail("should not create concurrent client");
                    return Future.succeededFuture(mock(RegistrationClient.class));
                },
                ctx.<RequestResponseClient> asyncAssertFailure(t -> {
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
        client.connect(new ProtonClientOptions()).setHandler(ctx.asyncAssertSuccess(ok -> connected.complete()));
        connected.await();

        final Async creationFailure = ctx.async();
        final Async supplierInvocation = ctx.async();

        client.getOrCreateRequestResponseClient(
                "registration/tenant",
                () -> {
                    ctx.assertFalse(creationFailure.isCompleted());
                    supplierInvocation.complete();
                    return Future.future();
                },
                ctx.asyncAssertFailure(cause -> creationFailure.complete()));

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
        client.connect(new ProtonClientOptions()).setHandler(ctx.asyncAssertSuccess(ok -> connected.complete()));
        connected.await();

        client.getOrCreateSender("telemetry/tenant", () -> Future.future());

        // WHEN an additional, concurrent attempt is made to create a telemetry sender for "tenant"
        client.getOrCreateSender(
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
        client.connect(new ProtonClientOptions()).setHandler(ctx.asyncAssertSuccess(ok -> connected.complete()));
        connected.await();

        final Async disconnected = ctx.async();
        final Async supplierInvocation = ctx.async();

        client.getOrCreateSender(
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
        client.connect(new ProtonClientOptions()).setHandler(ctx.asyncAssertSuccess(ok -> connected.complete()));
        connected.await();

        final Async creationFailure = ctx.async();
        final Async supplierInvocation = ctx.async();
        client.createConsumer("tenant", () -> {
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

        // expect the connection factory to be invoked twice
        // first on initial connection
        // second on reconnect
        connectionFactory = new DisconnectHandlerProvidingConnectionFactory(con);

        // GIVEN a client trying to create a sender to a peer
        final ProtonClientOptions options = new ProtonClientOptions()
                .setReconnectInterval(50)
                .setReconnectAttempts(3);
        client = new HonoClientImpl(vertx, connectionFactory, props);
        client.connect(options).setHandler(ctx.asyncAssertSuccess());
        assertTrue(connectionFactory.await(1, TimeUnit.SECONDS));
        connectionFactory.setExpectedSucceedingConnectionAttempts(1);

        // WHEN the downstream connection fails just when the client wants to open a sender link
        final Async senderCreationFailure = ctx.async();
        client.getOrCreateSender("telemetry/tenant", () -> {
            connectionFactory.getDisconnectHandler().handle(con);
            return Future.future();
        }).setHandler(ctx.asyncAssertFailure(cause -> senderCreationFailure.complete()));

        // THEN the sender creation fails,
        senderCreationFailure.await();
        // the connection is re-established
        assertTrue(connectionFactory.await(1, TimeUnit.SECONDS));
        // and the next attempt to create a sender succeeds
        client.getOrCreateSender(
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
        connectionFactory = new DisconnectHandlerProvidingConnectionFactory(con, 1, 2);
        final ProtonClientOptions options = new ProtonClientOptions()
                .setReconnectInterval(50)
                .setReconnectAttempts(3);
        client = new HonoClientImpl(vertx, connectionFactory, props);
        client.connect(options).setHandler(ctx.asyncAssertSuccess());
        assertTrue(connectionFactory.await(1, TimeUnit.SECONDS));
        connectionFactory.setExpectedFailingConnectionAttempts(2);
        connectionFactory.setExpectedSucceedingConnectionAttempts(1);

        // WHEN the downstream connection fails
        connectionFactory.getDisconnectHandler().handle(con);

        // THEN the adapter reconnects to the downstream container
        assertTrue(connectionFactory.await(1, TimeUnit.SECONDS));
        connectionFactory.setExpectedFailingConnectionAttempts(2);
        connectionFactory.setExpectedSucceedingConnectionAttempts(1);

        // and when the downstream connection fails again
        connectionFactory.getDisconnectHandler().handle(con);

        // THEN the adapter reconnects to the downstream container again
        assertTrue(connectionFactory.await(1, TimeUnit.SECONDS));
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
        connectionFactory = new DisconnectHandlerProvidingConnectionFactory(con, 1, 2);
        client = new HonoClientImpl(vertx, connectionFactory, props);

        // WHEN trying to connect
        final ProtonClientOptions options = new ProtonClientOptions()
                .setReconnectInterval(50)
                .setReconnectAttempts(3);
        final Async disconnectHandlerInvocation = ctx.async();
        client.connect(
                options,
                failedCon -> disconnectHandlerInvocation.complete())
            .setHandler(ctx.asyncAssertSuccess());

        // THEN the client succeeds to connect on the third attempt
        assertTrue(connectionFactory.await(1, TimeUnit.SECONDS));
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
        client.connect(
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

        // GIVEN a shutdown client
        client.shutdown();

        // WHEN client connects
        client.connect(new ProtonClientOptions()).setHandler(
                ctx.asyncAssertFailure(cause -> {
                    //THEN connect fails
                    ctx.assertEquals(HttpURLConnection.HTTP_CONFLICT, ((ClientErrorException) cause).getErrorCode());
                }));
    }

    /**
     * Verifies that the client does not try to re-connect to a server instance if the client was shutdown.
     * 
     * @param ctx The test context.
     *
     */
    @Test
    public void testClientDoesNotTriggerReconnectionAfterShutdown(final TestContext ctx) {

        // GIVEN a client that tries to connect to a server but does not succeed
        final Async connectionHandlerInvocation = ctx.async();
        connectionFactory = new DisconnectHandlerProvidingConnectionFactory(con, 0, Integer.MAX_VALUE);
        client = new HonoClientImpl(vertx, connectionFactory, props);
        client.connect(new ProtonClientOptions().setReconnectAttempts(1)).setHandler(
                ctx.asyncAssertFailure(cause -> connectionHandlerInvocation.complete()));

        // WHEN client gets shutdown
        client.shutdown();

        // THEN reconnect gets stopped, i.e. connection fails
        connectionHandlerInvocation.await();
    }

    /**
     * A connection factory that provides access to the disconnect handler registered with
     * a connection created by the factory.
     *
     */
    private static class DisconnectHandlerProvidingConnectionFactory implements ConnectionFactory {

        private Handler<ProtonConnection> disconnectHandler;
        private Handler<AsyncResult<ProtonConnection>> closeHandler;
        private CountDownLatch expectedSucceedingConnectionAttempts;
        private CountDownLatch expectedFailingConnectionAttempts;
        private ProtonConnection connectionToCreate;

        DisconnectHandlerProvidingConnectionFactory(final ProtonConnection conToCreate) {
            this(conToCreate, 1);
        }

        DisconnectHandlerProvidingConnectionFactory(final ProtonConnection conToCreate, final int expectedSucceedingConnectionAttempts) {
            this(conToCreate, expectedSucceedingConnectionAttempts, 0);
        }

        DisconnectHandlerProvidingConnectionFactory(final ProtonConnection conToCreate, final int expectedSucceedingConnectionAttempts,
                final int expectedFailingConnectionAttempts) {
            this.connectionToCreate = conToCreate;
            this.expectedSucceedingConnectionAttempts = new CountDownLatch(expectedSucceedingConnectionAttempts);
            this.expectedFailingConnectionAttempts = new CountDownLatch(expectedFailingConnectionAttempts);
        }

        @Override
        public void connect(
                final ProtonClientOptions options,
                final Handler<AsyncResult<ProtonConnection>> closeHandler,
                final Handler<ProtonConnection> disconnectHandler,
                final Handler<AsyncResult<ProtonConnection>> connectionResultHandler) {
            connect(options, null, null, closeHandler, disconnectHandler, connectionResultHandler);
        }

        @Override
        public void connect(
                final ProtonClientOptions options,
                final String username,
                final String password,
                final Handler<AsyncResult<ProtonConnection>> closeHandler,
                final Handler<ProtonConnection> disconnectHandler,
                final Handler<AsyncResult<ProtonConnection>> connectionResultHandler) {

            this.closeHandler = closeHandler;
            this.disconnectHandler = disconnectHandler;
            if (expectedFailingConnectionAttempts.getCount() > 0) {
                connectionResultHandler.handle(Future.failedFuture("cannot connect"));
                expectedFailingConnectionAttempts.countDown();
            } else {
                connectionResultHandler.handle(Future.succeededFuture(connectionToCreate));
                expectedSucceedingConnectionAttempts.countDown();
            }
        }

        @Override
        public String getName() {
            return "client";
        }

        @Override
        public String getHost() {
            return "server";
        }

        @Override
        public int getPort() {
            return Constants.PORT_AMQP;
        }

        @Override
        public String getPathSeparator() {
            return Constants.DEFAULT_PATH_SEPARATOR;
        }

        public Handler<ProtonConnection> getDisconnectHandler() {
            return disconnectHandler;
        }

        public Handler<AsyncResult<ProtonConnection>> getCloseHandler() {
            return closeHandler;
        }

        public void setExpectedFailingConnectionAttempts(final int attempts) {
            expectedFailingConnectionAttempts = new CountDownLatch(attempts);
        }

        public void setExpectedSucceedingConnectionAttempts(final int attempts) {
            expectedSucceedingConnectionAttempts = new CountDownLatch(attempts);
        }

        public boolean await(final long timeout, final TimeUnit unit) {
            try {
                return expectedSucceedingConnectionAttempts.await(timeout, unit);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
    }

}
