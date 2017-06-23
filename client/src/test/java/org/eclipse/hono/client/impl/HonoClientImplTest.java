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

package org.eclipse.hono.client.impl;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.eclipse.hono.client.MessageSender;
import org.eclipse.hono.connection.ConnectionFactory;
import org.eclipse.hono.util.Constants;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
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

    Vertx vertx;

    /**
     * Sets up common test bed.
     */
    @Before
    public void setUp() {
        vertx = Vertx.vertx();
    }

    /**
     * Cleans up after test execution.
     * 
     * @param ctx The helper to use for running async tests.
     */
    @After
    public void shutdown(final TestContext ctx) {
        if (vertx != null) {
            vertx.close(ctx.asyncAssertSuccess());
        }
    }

    /**
     * Verifies that a concurrent request to create a sender fails the given future for tracking the attempt.
     * 
     * @param ctx The helper to use for running async tests.
     */
    @Test
    public void testGetOrCreateTelemetrySenderFailsIfInvokedConcurrently(final TestContext ctx) {

        // GIVEN a client that already tries to create a telemetry sender for "tenant"
        ConnectionFactory connectionFactory = mock(ConnectionFactory.class);
        HonoClientImpl client = new HonoClientImpl(vertx, connectionFactory);
        final Future<MessageSender> firstSenderTracker = Future.future();
        client.getOrCreateSender("telemetry/tenant", handler -> {
            firstSenderTracker.setHandler(creationAttempt -> {
                handler.handle(creationAttempt);
            });
        }, result -> {});

        // WHEN an additional, concurrent attempt is made to create a telemetry sender for "tenant"
        final Async creationFailure = ctx.async();
        client.getOrCreateSender("telemetry/tenant", handler -> {
            handler.handle(Future.succeededFuture(mock(MessageSender.class)));
        }, creationAttempt -> {
            ctx.assertFalse(creationAttempt.succeeded());
            creationFailure.complete();
        });

        // THEN the concurrent attempt fails immediately without any attempt being made to create another sender
        creationFailure.await(2000);

        // succeed first creation attempt, thus invoking result handler
        firstSenderTracker.complete(mock(MessageSender.class));
    }

    /**
     * Verifies that a request to create a message sender is failed immediately when the
     * underlying connection to the server fails.
     * 
     * @param ctx The Vertx test context.
     */
    @Test
    public void testGetOrCreateSenderFailsOnConnectionFailure(final TestContext ctx) {

        // GIVEN a client that already tries to create a telemetry sender for "tenant"
        ProtonConnection con = mock(ProtonConnection.class);
        DisconnectHandlerProvidingConnectionFactory connectionFactory = new DisconnectHandlerProvidingConnectionFactory(con);
        final Async connected = ctx.async();
        final Async disconnected = ctx.async();
        HonoClientImpl client = new HonoClientImpl(vertx, connectionFactory);
        client.connect(new ProtonClientOptions(), ctx.asyncAssertSuccess(ok -> connected.complete()));
        connected.await(200);

        client.getOrCreateSender("telemetry/tenant", creationResultHandler -> {
            ctx.assertFalse(disconnected.isCompleted());
        }, ctx.asyncAssertFailure(cause -> {
            disconnected.complete();
        }));

        // WHEN the underlying connection fails
        connectionFactory.getDisconnectHandler().handle(con);

        // THEN all creation requests are failed
        disconnected.await(200);
    }

    /**
     * Verifies that a request to create a telemetry consumer is failed immediately when the
     * underlying connection to the server fails.
     * 
     * @param ctx The Vertx test context.
     */
    @Test
    public void testCreateTelemetryConsumerFailsOnConnectionFailure(final TestContext ctx) {

        // GIVEN a client that already tries to create a telemetry sender for "tenant"
        ProtonConnection con = mock(ProtonConnection.class);
        DisconnectHandlerProvidingConnectionFactory connectionFactory = new DisconnectHandlerProvidingConnectionFactory(con);
        final Async connected = ctx.async();
        final Async disconnected = ctx.async();
        HonoClientImpl client = new HonoClientImpl(vertx, connectionFactory);
        client.connect(new ProtonClientOptions(), ctx.asyncAssertSuccess(ok -> connected.complete()));
        connected.await(200);

        client.createTelemetryConsumer("tenant", msg -> {}, ctx.asyncAssertFailure(cause -> {
            disconnected.complete();
        }));

        // WHEN the underlying connection fails
        connectionFactory.getDisconnectHandler().handle(con);

        // THEN all creation requests are failed
        disconnected.await(200);
    }

    /**
     * Verifies that a request to create an event consumer is failed immediately when the
     * underlying connection to the server fails.
     * 
     * @param ctx The Vertx test context.
     */
    @Test
    public void testCreateEventConsumerFailsOnConnectionFailure(final TestContext ctx) {

        // GIVEN a client that already tries to create a telemetry sender for "tenant"
        ProtonConnection con = mock(ProtonConnection.class);
        DisconnectHandlerProvidingConnectionFactory connectionFactory = new DisconnectHandlerProvidingConnectionFactory(con);
        final Async connected = ctx.async();
        HonoClientImpl client = new HonoClientImpl(vertx, connectionFactory);
        client.connect(new ProtonClientOptions(), ctx.asyncAssertSuccess(ok -> connected.complete()));
        connected.await(200);

        final Async disconnected = ctx.async();
        client.createEventConsumer("tenant", msg -> {}, ctx.asyncAssertFailure(cause -> {
            disconnected.complete();
        }));

        // WHEN the underlying connection fails
        connectionFactory.getDisconnectHandler().handle(con);

        // THEN all creation requests are failed
        disconnected.await(200);
    }

    /**
     * Verifies that all sender creation locks are cleared when the connection to the server fails.
     * 
     * @param ctx The Vertx test context.
     */
    @Test
    public void testDownstreamDisconnectClearsSenderCreationLocks(final TestContext ctx) {

        final ProtonConnection connectionToCreate = mock(ProtonConnection.class);
        when(connectionToCreate.getRemoteContainer()).thenReturn("server");
        // expect the connection factory to be invoked twice
        // first on initial connection
        // second on reconnect
        DisconnectHandlerProvidingConnectionFactory connectionFactory = new DisconnectHandlerProvidingConnectionFactory(connectionToCreate, 2);

        // GIVEN a client connected to a server
        final Async connected = ctx.async();
        HonoClientImpl client = new HonoClientImpl(vertx, connectionFactory);
        client.connect(new ProtonClientOptions().setReconnectAttempts(1), ctx.asyncAssertSuccess(ok -> connected.complete()));
        connected.await(200);

        final Async senderCreationFailure = ctx.async();
        // WHEN the downstream connection fails just when the client wants to open a sender link
        client.getOrCreateSender("telemetry/tenant", creationAttemptHandler -> {
            connectionFactory.getDisconnectHandler().handle(connectionToCreate);
            // the creationAttempHandler will not be invoked at all
        }, ctx.asyncAssertFailure(cause -> senderCreationFailure.complete()));

        // THEN the sender creation fails,
        senderCreationFailure.await(1000);
        // the connection is re-established
        connectionFactory.await(1, TimeUnit.SECONDS);
        // and the next attempt to create a sender succeeds
        final Async senderSupplierInvocation = ctx.async();
        client.getOrCreateSender("telemetry/tenant", creationAttemptHandler -> {
            senderSupplierInvocation.complete();
            creationAttemptHandler.handle(Future.succeededFuture(mock(MessageSender.class)));
        }, ctx.asyncAssertSuccess());
        senderSupplierInvocation.await(1000);
    }

    /**
     * Verifies that the client tries to re-establish a lost connection to a server.
     * 
     * @param ctx The Vertx test context.
     */
    @Test
    public void testDownstreamDisconnectTriggersReconnect(final TestContext ctx) {

        final ProtonConnection connectionToCreate = mock(ProtonConnection.class);
        when(connectionToCreate.getRemoteContainer()).thenReturn("server");
        // expect the connection factory to be invoked twice
        // first on initial connection
        // second on re-connect attempt
        DisconnectHandlerProvidingConnectionFactory connectionFactory = new DisconnectHandlerProvidingConnectionFactory(connectionToCreate, 2);

        // GIVEN an client connected to a server
        final Async connected = ctx.async();
        HonoClientImpl client = new HonoClientImpl(vertx, connectionFactory);
        client.connect(new ProtonClientOptions().setReconnectAttempts(1), ctx.asyncAssertSuccess(ok -> connected.complete()));
        connected.await(200);

        // WHEN the downstream connection fails
        connectionFactory.getDisconnectHandler().handle(connectionToCreate);

        // THEN the adapter tries to reconnect to the downstream container
        connectionFactory.await(1, TimeUnit.SECONDS);
    }

    /**
     * Verifies that the client adapter repeatedly tries to connect until a connection is established.
     */
    @Test
    public void testConnectTriesToReconnectOnFailedConnectAttempt() {

        // expect the connection factory to be invoked 3 times
        DisconnectHandlerProvidingConnectionFactory connectionFactory = new DisconnectHandlerProvidingConnectionFactory(null, 3);

        // GIVEN an client that cannot connect to the server
        HonoClientImpl client = new HonoClientImpl(vertx, connectionFactory);

        // WHEN trying to connect
        client.connect(new ProtonClientOptions().setReconnectAttempts(1), attempt -> {});

        // THEN the client repeatedly tries to connect
        assertTrue(connectionFactory.await(4 * Constants.DEFAULT_RECONNECT_INTERVAL_MILLIS, TimeUnit.MILLISECONDS));
    }

    private class DisconnectHandlerProvidingConnectionFactory implements ConnectionFactory {

        private Handler<ProtonConnection> disconnectHandler;
        private CountDownLatch expectedConnectionAttemps;
        private ProtonConnection connectionToCreate;

        public DisconnectHandlerProvidingConnectionFactory(final ProtonConnection conToCreate) {
            this(conToCreate, 1);
        }

        public DisconnectHandlerProvidingConnectionFactory(final ProtonConnection conToCreate, final int expectedConnectionAttempts) {
            this.connectionToCreate = conToCreate;
            this.expectedConnectionAttemps = new CountDownLatch(expectedConnectionAttempts);
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

            expectedConnectionAttemps.countDown();
            this.disconnectHandler = disconnectHandler;
            if (connectionToCreate == null) {
                connectionResultHandler.handle(Future.failedFuture("cannot connect"));
            } else {
                connectionResultHandler.handle(Future.succeededFuture(connectionToCreate));
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

        public boolean await(long timeout, TimeUnit unit) {
            try {
                return expectedConnectionAttemps.await(timeout, unit);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
    }

}
