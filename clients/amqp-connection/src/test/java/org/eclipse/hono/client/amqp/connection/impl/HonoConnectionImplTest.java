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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static com.google.common.truth.Truth.assertThat;

import java.net.HttpURLConnection;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.apache.qpid.proton.amqp.Symbol;
import org.apache.qpid.proton.amqp.UnsignedLong;
import org.apache.qpid.proton.amqp.messaging.Target;
import org.apache.qpid.proton.amqp.transport.AmqpError;
import org.apache.qpid.proton.amqp.transport.ErrorCondition;
import org.apache.qpid.proton.amqp.transport.Source;
import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.ServerErrorException;
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.client.amqp.config.ClientConfigProperties;
import org.eclipse.hono.client.amqp.connection.ConnectionFactory;
import org.eclipse.hono.client.amqp.connection.DisconnectListener;
import org.eclipse.hono.client.amqp.connection.HonoConnection;
import org.eclipse.hono.test.VertxMockSupport;
import org.eclipse.hono.util.TelemetryConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.AdditionalAnswers;
import org.mockito.ArgumentCaptor;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.proton.ProtonClientOptions;
import io.vertx.proton.ProtonConnection;
import io.vertx.proton.ProtonMessageHandler;
import io.vertx.proton.ProtonQoS;
import io.vertx.proton.ProtonReceiver;
import io.vertx.proton.ProtonSender;
import io.vertx.proton.ProtonSession;
import io.vertx.proton.sasl.SaslSystemException;

/**
 * Test cases verifying the behavior of {@link HonoConnection}.
 *
 */
@ExtendWith(VertxExtension.class)
@Timeout(value = 5, timeUnit = TimeUnit.SECONDS)
public class HonoConnectionImplTest {

    private Vertx vertx;
    private ProtonConnection con;
    private ProtonSession session;
    private DisconnectHandlerProvidingConnectionFactory connectionFactory;
    private ClientConfigProperties props;

    /**
     * Sets up fixture.
     */
    @BeforeEach
    public void setUp() {
        vertx = mock(Vertx.class);
        final Context context = VertxMockSupport.mockContext(vertx);
        when(vertx.getOrCreateContext()).thenReturn(context);
        VertxMockSupport.runTimersImmediately(vertx);
        session = mock(ProtonSession.class);
        con = mock(ProtonConnection.class);
        when(con.getRemoteContainer()).thenReturn("server");
        when(con.createSession()).thenReturn(session);
        connectionFactory = new DisconnectHandlerProvidingConnectionFactory(con);
        props = new ClientConfigProperties();
        props.setServerRole("test-server");
    }

    /**
     * Verifies that the client establishes an AMQP session with the
     * configured incoming window size on the connection.
     *
     * @param ctx The vert.x test client.
     */
    @Test
    public void testConnectEstablishesSession(final VertxTestContext ctx) {

        // GIVEN a client that is configured with a specific incoming session
        // window size
        props.setMaxFrameSize(16 * 1024);
        props.setMaxSessionFrames(10);
        final var honoConnection = new HonoConnectionImpl(vertx, props, connectionFactory);

        // WHEN the client tries to connect
        honoConnection.connect()
            .onComplete(ctx.succeeding(con -> {
                // THEN the session has been configured with an incoming window size
                ctx.verify(() -> {
                    verify(session).setIncomingCapacity(10 * 16 * 1024);
                    verify(session).open();
                });
                ctx.completeNow();
            }));
    }

    /**
     * Verifies that the client tries to connect a limited
     * number of times only.
     *
     * @param ctx The vert.x test client.
     */
    @Test
    public void testConnectFailsAfterMaxConnectionAttempts(final VertxTestContext ctx) {

        // GIVEN a client that is configured to reconnect
        // two times before failing
        props.setReconnectAttempts(2);
        props.setConnectTimeout(10);
        // expect three unsuccessful connection attempts
        connectionFactory = new DisconnectHandlerProvidingConnectionFactory(con)
                .setExpectedFailingConnectionAttempts(3);
        final var honoConnection = new HonoConnectionImpl(vertx, props, connectionFactory);

        // WHEN the client tries to connect
        honoConnection.connect().onComplete(ctx.failing(t -> {
            // THEN the connection attempt fails
            ctx.verify(() -> assertThat(((ServerErrorException) t).getErrorCode()).isEqualTo(HttpURLConnection.HTTP_UNAVAILABLE));
        }));
        // and the client has indeed tried three times in total before giving up
        ctx.verify(() -> assertThat(connectionFactory.awaitFailure()).isTrue());
        ctx.completeNow();
    }

    /**
     * Verifies that a connection attempt from the client is failed if there already is an ongoing
     * connection attempt.
     *
     * @param ctx The vert.x test client.
     */
    @Test
    public void testConnectFailsIfAnotherConnectAttemptOngoing(final VertxTestContext ctx) {

        // GIVEN a client that is configured to connect to a peer
        // to which the connection isn't getting established immediately
        final Promise<ProtonConnection> delayedConnectPromise = Promise.promise();
        final ConnectionFactory factory = mock(ConnectionFactory.class);
        when(factory.connect(any(), any(), any(), any(), any(), any()))
            .thenReturn(delayedConnectPromise.future());

        final var honoConnection = new HonoConnectionImpl(vertx, props, factory);

        // WHEN trying to connect
        final Future<HonoConnection> connectFuture = honoConnection.connect();
        // and starting another connect attempt before the connection has been established
        final Future<HonoConnection> connectFuture2 = honoConnection.connect();

        delayedConnectPromise.complete(con);
        // THEN the first connect invocation succeeds and the second is failed
        connectFuture.onComplete(ctx.succeeding(con -> {
            ctx.verify(() -> {
                assertThat(connectFuture2.failed()).isTrue();
                assertThat(connectFuture2.cause()).isInstanceOf(ClientErrorException.class);
                assertThat(((ClientErrorException) connectFuture2.cause()).getErrorCode())
                    .isEqualTo(HttpURLConnection.HTTP_CONFLICT);
            });
            ctx.completeNow();
        }));
    }

    /**
     * Verifies that a connection attempt by the client is failed if it occurs while another connect invocation is
     * waiting for the next reconnect attempt.
     *
     * @param ctx The test execution context.
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testConnectFailsIfAnotherConnectAttemptIsScheduled(final VertxTestContext ctx) {
        final long reconnectMinDelay = 20L;

        // GIVEN a client that is configured to connect to a peer
        // to which the connection is only getting established on the 2nd attempt, after some delay.

        // the reconnect timer handler only shall get invoked on demand (after a corresponding promise gets completed)
        final AtomicBoolean reconnectTimerStarted = new AtomicBoolean();
        final Promise<Void> reconnectTimerContinuePromise = Promise.promise();
        when(vertx.setTimer(eq(reconnectMinDelay), any())).thenAnswer(invocation -> {
            reconnectTimerStarted.set(true);
            final Handler<Long> reconnectTimerHandler = invocation.getArgument(1);
            reconnectTimerContinuePromise.future().onComplete(ar -> reconnectTimerHandler.handle(0L));
            return 1L;
        });

        final ConnectionFactory factory = mock(ConnectionFactory.class);
        when(factory.connect(any(), any(), any(), any(), any(), any()))
            .thenReturn(Future.failedFuture("first failure"), Future.succeededFuture(con));

        props.setReconnectMinDelay(reconnectMinDelay);
        final var honoConnection = new HonoConnectionImpl(vertx, props, factory);

        // WHEN trying to connect
        final Future<HonoConnection> connectFuture = honoConnection.connect();
        assertThat(reconnectTimerStarted.get()).isTrue();
        // and starting another connect attempt before the connection has been established
        final Future<HonoConnection> connectFuture2 = honoConnection.connect();

        // and letting the first attempt finish
        reconnectTimerContinuePromise.complete();

        // THEN the first connect invocation succeeds and the second is failed
        connectFuture.onComplete(ctx.succeeding(cause -> {
            ctx.verify(() -> {
                assertThat(connectFuture2.failed()).isTrue();
                assertThat(connectFuture2.cause()).isInstanceOf(ClientErrorException.class);
                assertThat(((ClientErrorException) connectFuture2.cause()).getErrorCode())
                    .isEqualTo(HttpURLConnection.HTTP_CONFLICT);
            });
            ctx.completeNow();
        }));
    }

    /**
     * Verifies that the delay between reconnect attempts conforms
     * to how it is configured in the ClientConfigProperties.
     *
     * @param ctx The vert.x test client.
     */
    @Test
    public void testReconnectDelay(final VertxTestContext ctx) {

        // GIVEN a client that is configured to reconnect 5 times with custom delay times.
        final int reconnectAttempts = 5;
        props.setReconnectAttempts(reconnectAttempts);
        props.setReconnectMinDelay(10);
        props.setReconnectMaxDelay(1000);
        props.setReconnectDelayIncrement(100);
        props.setConnectTimeout(10);
        // expect 6 unsuccessful connection attempts
        connectionFactory = new DisconnectHandlerProvidingConnectionFactory(con)
                .setExpectedFailingConnectionAttempts(reconnectAttempts + 1);
        final var honoConnection = new HonoConnectionImpl(vertx, props, connectionFactory);

        // WHEN the client tries to connect
        honoConnection.connect().onComplete(ctx.failing(t -> {
            // THEN the connection attempt fails
            ctx.verify(() -> assertThat(((ServerErrorException) t).getErrorCode()).isEqualTo(HttpURLConnection.HTTP_UNAVAILABLE));
        }));
        // and the client has indeed tried 6 times in total before giving up
        ctx.verify(() -> {
            assertThat(connectionFactory.awaitFailure()).isTrue();
            final ArgumentCaptor<Long> delayValueCaptor = ArgumentCaptor.forClass(Long.class);
            verify(vertx, times(reconnectAttempts)).setTimer(delayValueCaptor.capture(), VertxMockSupport.anyHandler());
            // and the first delay period is the minDelay value
            assertThat(delayValueCaptor.getAllValues().get(0)).isEqualTo(10L);
        });
        ctx.completeNow();
    }

    /**
     * Verifies that the client fails with a ServerErrorException with status code 503
     * if it cannot authenticate to the server because of a transient error.
     *
     * @param ctx The vert.x test client.
     */
    @Test
    public void testConnectFailsWithClientErrorForTransientSaslSystemException(final VertxTestContext ctx) {

        // GIVEN a client that is configured to connect
        // to a peer that always throws a SaslSystemException with permanent=false
        props.setReconnectAttempts(2);
        props.setConnectTimeout(10);
        connectionFactory = new DisconnectHandlerProvidingConnectionFactory(con)
                .setExpectedFailingConnectionAttempts(3)
                .failWith(new SaslSystemException(false, "SASL handshake failed due to a transient error"));
        final var honoConnection = new HonoConnectionImpl(vertx, props, connectionFactory);

        // WHEN the client tries to connect
        honoConnection.connect().onComplete(ctx.failing(t -> {
            // THEN the connection attempt fails
            ctx.verify(() -> assertThat(((ServiceInvocationException) t).getErrorCode()).isEqualTo(HttpURLConnection.HTTP_UNAVAILABLE));
        }));
        // and the client has indeed tried three times in total
        ctx.verify(() -> assertThat(connectionFactory.awaitFailure()).isTrue());
        ctx.completeNow();
    }

    /**
     * Verifies that the client tries to re-establish a lost connection to a server.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testDownstreamDisconnectTriggersReconnect(final VertxTestContext ctx) {

        // GIVEN an client that is connected to a peer to which the
        // connection can be established on the third attempt only
        connectionFactory = new DisconnectHandlerProvidingConnectionFactory(con);
        props.setReconnectAttempts(1);
        final ProtonClientOptions options = new ProtonClientOptions()
                .setReconnectAttempts(0);
        final var honoConnection = new HonoConnectionImpl(vertx, props, connectionFactory);
        final AtomicInteger reconnectListenerInvocations = new AtomicInteger();
        honoConnection.addReconnectListener(con -> reconnectListenerInvocations.incrementAndGet());
        honoConnection.connect(options).onFailure(ctx::failNow);
        ctx.verify(() -> assertThat(connectionFactory.await()).isTrue());
        connectionFactory.setExpectedSucceedingConnectionAttempts(1);

        // WHEN the downstream connection fails
        connectionFactory.getDisconnectHandler().handle(con);

        // THEN the adapter reconnects to the downstream container
        ctx.verify(() -> assertThat(connectionFactory.await()).isTrue());
        connectionFactory.setExpectedSucceedingConnectionAttempts(1);

        // and when the downstream connection fails again
        connectionFactory.getDisconnectHandler().handle(con);

        // THEN the adapter reconnects to the downstream container again
        ctx.verify(() -> {
            assertThat(connectionFactory.await()).isTrue();
            assertThat(reconnectListenerInvocations.get()).isEqualTo(2);
        });
        ctx.completeNow();
    }

    /**
     * Verifies that the client repeatedly tries to connect until a connection is established.
     *
     * @param ctx The test context.
     */
    @Test
    public void testConnectTriesToReconnectOnFailedConnectAttempt(final VertxTestContext ctx) {

        // GIVEN a client that is configured to connect to a peer
        // to which the connection can be established on the third attempt only
        connectionFactory = new DisconnectHandlerProvidingConnectionFactory(con)
                .setExpectedFailingConnectionAttempts(2);
        props.setReconnectAttempts(2);
        props.setConnectTimeout(10);
        final var honoConnection = new HonoConnectionImpl(vertx, props, connectionFactory);
        final AtomicInteger reconnectListenerInvocations = new AtomicInteger();
        honoConnection.addReconnectListener(con -> reconnectListenerInvocations.incrementAndGet());

        // WHEN trying to connect
        honoConnection.connect().onFailure(ctx::failNow);

        ctx.verify(() -> {
            // THEN the client fails twice to connect
            assertThat(connectionFactory.awaitFailure()).isTrue();
            // and succeeds to connect on the third attempt
            assertThat(connectionFactory.await()).isTrue();
            // and there are no reconnect listener invocations (all of the above represents one connection attempt)
            assertThat(reconnectListenerInvocations.get()).isEqualTo(0);
        });
        ctx.completeNow();
    }

    /**
     * Verifies that the client tries to re-connect to a server instance if the
     * connection is closed by the peer.
     *
     * @param ctx The test context.
     */
    @Test
    public void testOnRemoteCloseTriggersReconnection(final VertxTestContext ctx) {

        // GIVEN a client that is connected to a server
        final Promise<HonoConnection> connected = Promise.promise();
        final AtomicInteger reconnectListenerInvocations = new AtomicInteger();
        @SuppressWarnings("unchecked")
        final DisconnectListener<HonoConnection> disconnectListener = mock(DisconnectListener.class);
        final var honoConnection = new HonoConnectionImpl(vertx, props, connectionFactory);
        honoConnection.addDisconnectListener(disconnectListener);
        honoConnection.addReconnectListener(con -> reconnectListenerInvocations.incrementAndGet());
        honoConnection.connect(new ProtonClientOptions())
            .onComplete(connected);
        connectionFactory.setExpectedSucceedingConnectionAttempts(1);

        connected.future().onComplete(ctx.succeeding(c -> {
            // WHEN the peer closes the connection
            connectionFactory.getCloseHandler().handle(Future.failedFuture("shutting down for maintenance"));

            ctx.verify(() -> {
                // THEN the client invokes the registered disconnect handler
                verify(disconnectListener).onDisconnect(honoConnection);
                // and the original connection has been closed locally
                verify(con).close();
                verify(con).disconnectHandler(null);
                // and the connection is re-established
                assertThat(connectionFactory.await()).isTrue();
                assertThat(reconnectListenerInvocations.get()).isEqualTo(1);
            });
            ctx.completeNow();
        }));
    }

    /**
     * Verifies that when the client tries to re-connect to a server instance if the
     * connection is closed by the peer, the configured number of reconnect attempts is taken
     * into account, skipping the reconnect if that number is zero.
     *
     * @param ctx The test context.
     */
    @Test
    public void testOnRemoteCloseTriggeredReconnectChecksReconnectAttempts(final VertxTestContext ctx) {

        // GIVEN a client that is connected to a server but should do no automatic reconnect
        props.setReconnectAttempts(0);
        final Promise<HonoConnection> connected = Promise.promise();
        final AtomicInteger reconnectListenerInvocations = new AtomicInteger();
        @SuppressWarnings("unchecked")
        final DisconnectListener<HonoConnection> disconnectListener = mock(DisconnectListener.class);
        final var honoConnection = new HonoConnectionImpl(vertx, props, connectionFactory);
        honoConnection.addDisconnectListener(disconnectListener);
        honoConnection.addReconnectListener(con -> reconnectListenerInvocations.incrementAndGet());
        honoConnection.connect(new ProtonClientOptions().setReconnectAttempts(0))
                .onComplete(connected);

        connected.future().onComplete(ctx.succeeding(c -> {
            // WHEN the peer closes the connection
            connectionFactory.getCloseHandler().handle(Future.failedFuture("shutting down for maintenance"));

            ctx.verify(() -> {
                // THEN the client invokes the registered disconnect handler
                verify(disconnectListener).onDisconnect(honoConnection);
                // and the original connection has been closed locally
                verify(con).close();
                verify(con).disconnectHandler(null);

                // and no further connect invocation has been done
                assertThat(connectionFactory.getConnectInvocations()).isEqualTo(1);
                assertThat(reconnectListenerInvocations.get()).isEqualTo(0);
            });
            ctx.completeNow();
        }));
    }

    /**
     * Verifies that the client tries to reconnect to the peer if the peer
     * closes the connection's session.
     *
     * @param ctx The test context.
     */
    @Test
    public void testRemoteSessionCloseTriggersReconnection(final VertxTestContext ctx) {

        // GIVEN a client that is connected to a server
        final Promise<HonoConnection> connected = Promise.promise();
        final AtomicInteger reconnectListenerInvocations = new AtomicInteger();
        @SuppressWarnings("unchecked")
        final DisconnectListener<HonoConnection> disconnectListener = mock(DisconnectListener.class);
        props.setServerRole("service-provider");
        final var honoConnection = new HonoConnectionImpl(vertx, props, connectionFactory);
        honoConnection.addDisconnectListener(disconnectListener);
        honoConnection.addReconnectListener(con -> reconnectListenerInvocations.incrementAndGet());
        honoConnection.connect(new ProtonClientOptions()).onComplete(connected);
        connectionFactory.setExpectedSucceedingConnectionAttempts(1);

        connected.future().onComplete(ctx.succeeding(c -> {

            ctx.verify(() -> {
                // WHEN the peer closes the session
                final ArgumentCaptor<Handler<AsyncResult<ProtonSession>>> sessionCloseHandler = VertxMockSupport.argumentCaptorHandler();
                verify(session).closeHandler(sessionCloseHandler.capture());
                sessionCloseHandler.getValue().handle(Future.succeededFuture(session));
                // THEN the client invokes the registered disconnect handler
                verify(disconnectListener).onDisconnect(honoConnection);
                // and the original connection has been closed locally
                verify(con).close();
                verify(con).disconnectHandler(null);
                // and the connection is re-established
                assertThat(connectionFactory.await()).isTrue();
                assertThat(reconnectListenerInvocations.get()).isEqualTo(1);
            });
            ctx.completeNow();
        }));

    }

    /**
     * Verifies that it fails to connect after client was shutdown.
     *
     * @param ctx The test context.
     */
    @Test
    public void testConnectFailsAfterShutdown(final VertxTestContext ctx) {

        final var honoConnection = new HonoConnectionImpl(vertx, props, connectionFactory);
        honoConnection.connect().compose(ok -> {
            // GIVEN a client that is in the process of shutting down
            honoConnection.shutdown(Promise.promise());
            // WHEN the client tries to reconnect before shut down is complete
            return honoConnection.connect();
        })
        .onComplete(ctx.failing(cause -> {
            // THEN the connection attempt fails
            ctx.verify(() -> assertThat(((ClientErrorException) cause).getErrorCode()).isEqualTo(HttpURLConnection.HTTP_CONFLICT));
            ctx.completeNow();
        }));
    }

    /**
     * Verifies that if a client disconnects from the server, then an attempt to connect again will be successful.
     *
     * @param ctx The test execution context.
     */
    @Test
    public void testConnectSucceedsAfterDisconnect(final VertxTestContext ctx) {

        final var honoConnection = new HonoConnectionImpl(vertx, props, connectionFactory);
        honoConnection.connect()
            .compose(ok -> {
                // GIVEN a client that is connected to a server
                final Promise<Void> disconnected = Promise.promise();
                // WHEN the client disconnects
                honoConnection.disconnect(disconnected);
                final ArgumentCaptor<Handler<AsyncResult<ProtonConnection>>> closeHandler = VertxMockSupport.argumentCaptorHandler();
                ctx.verify(() -> verify(con).closeHandler(closeHandler.capture()));
                closeHandler.getValue().handle(Future.succeededFuture(con));
                return disconnected.future();
            })
            .compose(d -> {
                // AND tries to reconnect again
                return honoConnection.connect(new ProtonClientOptions());
            })
            // THEN the connection succeeds
            .onComplete(ctx.succeedingThenComplete());
    }

    /**
     * Verifies that if a client disconnects from the server, then an ongoing attempt to connect is cancelled.
     *
     * @param ctx The test execution context.
     */
    @Test
    public void testDisconnectAbortsConnectAttempt(final VertxTestContext ctx) {

        // GIVEN a client that is configured to connect to a peer
        // to which the connection isn't getting established immediately
        final Promise<ProtonConnection> delayedConnectPromise = Promise.promise();
        final ConnectionFactory factory = mock(ConnectionFactory.class);
        when(factory.connect(any(), any(), any(), any(), any(), any()))
            .thenReturn(delayedConnectPromise.future());

        final var honoConnection = new HonoConnectionImpl(vertx, props, factory);

        // WHEN trying to connect
        final Future<HonoConnection> connectFuture = honoConnection.connect();
        // and disconnecting before the connection has been established
        honoConnection.disconnect();

        // THEN the connect attempt is failed
        connectFuture.onComplete(ctx.failing(cause -> {
            ctx.verify(() -> assertThat(((ClientErrorException) cause).getErrorCode()).isEqualTo(HttpURLConnection.HTTP_CONFLICT));
            ctx.completeNow();
        }));
    }

    /**
     * Verifies that if a client disconnects from the server, then an ongoing attempt to connect, currently waiting
     * for the next reconnect attempt, is cancelled.
     *
     * @param ctx The test execution context.
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testDisconnectAbortsConnectAttemptWaitingForReconnect(final VertxTestContext ctx) {
        final long reconnectTimerId = 32L;
        final long reconnectMinDelay = 20L;

        // GIVEN a client that is configured to connect to a peer
        // to which the connection is only getting established on the 2nd attempt, after some delay.

        // the reconnect timer handler will not get invoked, timer should get cancelled
        final AtomicBoolean reconnectTimerStarted = new AtomicBoolean();
        when(vertx.setTimer(eq(reconnectMinDelay), any())).thenAnswer(invocation -> {
            reconnectTimerStarted.set(true);
            return reconnectTimerId;
        });
        when(vertx.cancelTimer(eq(reconnectTimerId))).thenReturn(true);

        final ConnectionFactory factory = mock(ConnectionFactory.class);
        when(factory.connect(
                any(),
                any(),
                any(),
                any(),
                any(),
                any()))
            .thenReturn(
                    Future.failedFuture(new RuntimeException("failed to connect")),
                    Future.succeededFuture(con));

        props.setReconnectMinDelay(reconnectMinDelay);
        final var honoConnection = new HonoConnectionImpl(vertx, props, factory);

        // WHEN trying to connect
        final Future<HonoConnection> connectFuture = honoConnection.connect();
        assertThat(reconnectTimerStarted.get()).isTrue();
        // and disconnecting before the connection has been established
        honoConnection.disconnect();

        // THEN the reconnectTimer has been cancelled
        verify(vertx).cancelTimer(eq(reconnectTimerId));

        // AND the connect attempt is failed
        connectFuture.onComplete(ctx.failing(cause -> {
            ctx.verify(() -> assertThat(((ClientErrorException) cause).getErrorCode()).isEqualTo(HttpURLConnection.HTTP_CONFLICT));
            ctx.completeNow();
        }));
    }

    /**
     * Verifies that {@link HonoConnectionImpl#isConnected(long)} only completes once a concurrent
     * connection attempt (which eventually succeeds here) is finished.
     *
     * @param ctx The test execution context.
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testIsConnectedWithTimeoutSucceedsAfterConcurrentReconnectSucceeded(final VertxTestContext ctx) {

        final long isConnectedTimeout = 44444L;
        // let the vertx timer for the isConnectedTimeout do nothing
        when(vertx.setTimer(eq(isConnectedTimeout), VertxMockSupport.anyHandler())).thenAnswer(invocation -> 0L);
        // GIVEN a client that is configured to connect to a peer
        // to which the connection can be established on the third attempt only
        final Promise<ProtonConnection> firstAttemptResult = Promise.promise();
        final Promise<ProtonConnection> secondAttemptResult = Promise.promise();
        final var factory = mock(ConnectionFactory.class);
        when(factory.connect(any(), any(), any(), any(), any(), any()))
            .thenReturn(
                    firstAttemptResult.future(),
                    secondAttemptResult.future(),
                    Future.succeededFuture(con));

        props.setReconnectAttempts(2);
        props.setConnectTimeout(10);
        final var honoConnection = new HonoConnectionImpl(vertx, props, factory);

        // WHEN trying to connect
        final var result = honoConnection.connect();
        ctx.verify(() -> assertThat(result.isComplete()).isFalse());
        final var isConnected1Future = honoConnection.isConnected(isConnectedTimeout);
        final var isConnectedTimeoutForcedFuture = honoConnection.isConnected(1L);
        final var isConnected2Future = honoConnection.isConnected(isConnectedTimeout);

        ctx.verify(() -> {
            // THEN during the first connection attempt the timed out isConnected request has failed
            assertThat(isConnectedTimeoutForcedFuture.failed()).isTrue();
            // and the other requests have not been completed yet
            assertThat(isConnected1Future.isComplete()).isFalse();
            assertThat(isConnected2Future.isComplete()).isFalse();
        });

        firstAttemptResult.fail("first failure");
        ctx.verify(() -> {
            // and during the second attempt the other requests have still not been completed yet
            assertThat(isConnected1Future.isComplete()).isFalse();
            assertThat(isConnected2Future.isComplete()).isFalse();
        });

        secondAttemptResult.fail("second failure");

        result.onComplete(ctx.succeeding(ok -> {
            ctx.verify(() -> {
                // but after the final (successful) connection attempt, the connection is reported as
                // established
                assertThat(isConnected1Future.succeeded()).isTrue();
                assertThat(isConnected2Future.succeeded()).isTrue();
            });
            ctx.completeNow();
        }));
    }

    /**
     * Verifies that {@link HonoConnectionImpl#isConnected(long)} only completes once a concurrent
     * connection attempt (which eventually fails here) is finished.
     *
     * @param ctx The vert.x test client.
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testIsConnectedWithTimeoutFailsAfterConcurrentReconnectFailed(final VertxTestContext ctx) {

        final long isConnectedTimeout = 44444L;
        // let the vertx timer for the isConnectedTimeout do nothing
        when(vertx.setTimer(eq(isConnectedTimeout), VertxMockSupport.anyHandler())).thenAnswer(invocation -> 0L);
        // GIVEN a client that is configured to connect to a peer
        // to which the connection cannot be established after two attempts
        final Promise<ProtonConnection> firstAttemptResult = Promise.promise();
        final var factory = mock(ConnectionFactory.class);
        when(factory.connect(any(), any(), any(), any(), any(), any()))
            .thenReturn(
                    firstAttemptResult.future(),
                    Future.failedFuture("final failure"));

        props.setReconnectAttempts(1);
        props.setConnectTimeout(10);
        final var honoConnection = new HonoConnectionImpl(vertx, props, factory);

        // WHEN the client tries to connect
        final var result = honoConnection.connect();
        ctx.verify(() -> assertThat(result.isComplete()).isFalse());
        final var isConnected1Future = honoConnection.isConnected(isConnectedTimeout);
        final var isConnected2Future = honoConnection.isConnected(isConnectedTimeout);

        ctx.verify(() -> {
            // THEN during the first connection attempt the isConnected requests have not been completed yet
            assertThat(isConnected1Future.isComplete()).isFalse();
            assertThat(isConnected2Future.isComplete()).isFalse();
        });

        firstAttemptResult.fail("first failure");

        result.onComplete(ctx.failing(t -> {
            ctx.verify(() -> {
                // and after the final unsuccessful connection attempt, the connection is reported as
                // not established
                assertThat(ServiceInvocationException.extractStatusCode(t)).isEqualTo(HttpURLConnection.HTTP_UNAVAILABLE);
                assertThat(isConnected1Future.failed()).isTrue();
                assertThat(ServiceInvocationException.extractStatusCode(isConnected1Future.cause()))
                    .isEqualTo(HttpURLConnection.HTTP_UNAVAILABLE);
                assertThat(isConnected2Future.failed()).isTrue();
                assertThat(ServiceInvocationException.extractStatusCode(isConnected2Future.cause()))
                    .isEqualTo(HttpURLConnection.HTTP_UNAVAILABLE);
            });
            ctx.completeNow();
        }));
    }

    /**
     * Verifies that the client does not try to re-connect to a server instance if the client was shutdown.
     *
     * @param ctx The test context.
     */
    @Test
    public void testClientDoesNotTriggerReconnectionAfterShutdown(final VertxTestContext ctx) {

        // GIVEN a client that tries to connect to a server but does not succeed
        final AtomicInteger connectAttempts = new AtomicInteger(0);
        final ConnectionFactory factory = mock(ConnectionFactory.class);
        final var honoConnection = new HonoConnectionImpl(vertx, props, factory);
        when(factory.getHost()).thenReturn("server");
        when(factory.getPort()).thenReturn(5672);

        when(factory.connect(
                any(),
                any(),
                any(),
                any(),
                VertxMockSupport.anyHandler(),
                VertxMockSupport.anyHandler()))
            .thenAnswer(invocation -> {
                if (connectAttempts.incrementAndGet() == 3) {
                    // WHEN client gets shutdown
                    honoConnection.shutdown();
                }
                return Future.failedFuture(new ServerErrorException(HttpURLConnection.HTTP_UNAVAILABLE));
            });
        honoConnection.connect().onComplete(ctx.failing(cause -> {
                    // THEN three attempts have been made to connect
                    ctx.verify(() -> assertThat(connectAttempts.get()).isEqualTo(3));
                    ctx.completeNow();
                }));
    }

    /**
     * Verifies that the close handler set on a receiver link calls
     * the close hook passed in when creating the receiver.
     *
     * @param ctx The test context.
     */
    @Test
    public void testCloseHandlerCallsCloseHook(final VertxTestContext ctx) {
        testHandlerCallsCloseHook(ctx, (receiver, captor) -> verify(receiver).closeHandler(captor.capture()));
    }

    /**
     * Verifies that the detach handler set on a receiver link calls
     * the close hook passed in when creating the receiver.
     *
     * @param ctx The test context.
     */
    @Test
    public void testDetachHandlerCallsCloseHook(final VertxTestContext ctx) {
        testHandlerCallsCloseHook(ctx, (receiver, captor) -> verify(receiver).detachHandler(captor.capture()));
    }

    private void testHandlerCallsCloseHook(
            final VertxTestContext ctx,
            final BiConsumer<ProtonReceiver, ArgumentCaptor<Handler<AsyncResult<ProtonReceiver>>>> handlerCaptor) {

        // GIVEN an established connection
        final Source source = mock(Source.class);
        when(source.getAddress()).thenReturn("source/address");
        final ProtonReceiver receiver = mock(ProtonReceiver.class);
        when(receiver.isOpen()).thenReturn(Boolean.TRUE);
        when(receiver.getSource()).thenReturn(source);
        when(receiver.getRemoteSource()).thenReturn(source);
        when(session.createReceiver(anyString())).thenReturn(receiver);

        final Handler<String> remoteCloseHook = VertxMockSupport.mockHandler();
        final ArgumentCaptor<Handler<AsyncResult<ProtonReceiver>>> captor = VertxMockSupport.argumentCaptorHandler();

        final var honoConnection = new HonoConnectionImpl(vertx, props, connectionFactory);
        honoConnection.connect()
            .compose(c -> {

                // WHEN creating a receiver link with a close hook

                final Future<ProtonReceiver> r = c.createReceiver(
                        "source",
                        ProtonQoS.AT_LEAST_ONCE,
                        mock(ProtonMessageHandler.class),
                        remoteCloseHook);

                // wait for peer's attach frame
                final ArgumentCaptor<Handler<AsyncResult<ProtonReceiver>>> openHandlerCaptor = VertxMockSupport.argumentCaptorHandler();
                ctx.verify(() -> verify(receiver).openHandler(openHandlerCaptor.capture()));
                openHandlerCaptor.getValue().handle(Future.succeededFuture(receiver));

                return r;
            })
            .onComplete(ctx.succeeding(recv -> {

                // WHEN the peer sends a detach frame
                handlerCaptor.accept(receiver, captor);
                captor.getValue().handle(Future.succeededFuture(receiver));

                ctx.verify(() -> {
                    // THEN the close hook is called
                    verify(remoteCloseHook).handle(any());

                    // and the receiver link is closed
                    verify(receiver).close();
                    verify(receiver).free();
                });
                ctx.completeNow();
            }));

    }

    /**
     * Verifies that the client sets configured properties on receiver links
     * that it creates.
     *
     * @param ctx The test context.
     */
    @Test
    public void testCreateReceiverSetsConfiguredProperties(final VertxTestContext ctx) {

        // GIVEN a client configured with some properties
        props.setMaxMessageSize(3000L);
        props.setInitialCredits(123);
        final ProtonReceiver receiver = mock(ProtonReceiver.class);
        when(session.createReceiver(anyString())).thenReturn(receiver);
        final Handler<String> remoteCloseHook = VertxMockSupport.mockHandler();
        final var honoConnection = new HonoConnectionImpl(vertx, props, connectionFactory);

        // WHEN establishing a connection
        honoConnection.connect()
            .onComplete(ctx.succeeding(c -> {

                // and then creating a receiver
                c.createReceiver(
                        "source",
                        ProtonQoS.AT_LEAST_ONCE,
                        (delivery, msg) -> {},
                        remoteCloseHook);
                ctx.verify(() -> {
                    // THEN the client configures the receiver according to the given properties
                    verify(receiver).setMaxMessageSize(eq(new UnsignedLong(3000L)));
                    verify(receiver).setQoS(ProtonQoS.AT_LEAST_ONCE);
                    verify(receiver).setPrefetch(123);
                });
                ctx.completeNow();
            }));
    }

    /**
     * Verifies that the attempt to create a receiver fails with a
     * {@code ServiceInvocationException} if the remote peer refuses
     * to open the link with an error condition.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testCreateReceiverFailsForErrorCondition(final VertxTestContext ctx) {

        testCreateReceiverFails(ctx, () -> new ErrorCondition(AmqpError.RESOURCE_LIMIT_EXCEEDED, "unauthorized"), cause -> {
            return cause instanceof ServiceInvocationException;
        });
    }

    /**
     * Verifies that the attempt to create a receiver fails with a
     * {@code ClientErrorException} if the remote peer refuses
     * to open the link without an error condition.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testCreateReceiverFailsWithoutErrorCondition(final VertxTestContext ctx) {

        testCreateReceiverFails(ctx, () -> null, cause -> {
            return cause instanceof ClientErrorException &&
                    ((ClientErrorException) cause).getErrorCode() == HttpURLConnection.HTTP_NOT_FOUND;
        });
    }

    private void testCreateReceiverFails(
            final VertxTestContext ctx,
            final Supplier<ErrorCondition> errorSupplier,
            final Predicate<Throwable> failureAssertion) {

        final ProtonReceiver receiver = mock(ProtonReceiver.class);
        when(receiver.getRemoteCondition()).thenReturn(errorSupplier.get());
        when(session.createReceiver(anyString())).thenReturn(receiver);
        final Handler<String> remoteCloseHook = VertxMockSupport.mockHandler();
        when(vertx.setTimer(anyLong(), VertxMockSupport.anyHandler())).thenAnswer(invocation -> {
            // do not run timers immediately
            return 0L;
        });

        // GIVEN an established connection
        final var honoConnection = new HonoConnectionImpl(vertx, props, connectionFactory);
        honoConnection.connect()
        .compose(c -> {

            // WHEN creating a receiver
            final Future<ProtonReceiver> r = c.createReceiver(
                    "source", ProtonQoS.AT_LEAST_ONCE, (delivery, msg) -> {}, remoteCloseHook);
            ctx.verify(() -> {
                // and when the peer rejects to open the link
                final ArgumentCaptor<Handler<AsyncResult<ProtonReceiver>>> openHandler = VertxMockSupport.argumentCaptorHandler();
                verify(receiver).openHandler(openHandler.capture());
                openHandler.getValue().handle(Future.failedFuture(new IllegalStateException()));
            });
            return r;
        })
        .onComplete(ctx.failing(t -> {
            ctx.verify(() -> {
                // THEN link establishment is failed after the configured amount of time
                verify(vertx).setTimer(eq(props.getLinkEstablishmentTimeout()), VertxMockSupport.anyHandler());
                // with the expected error condition
                assertThat(failureAssertion.test(t)).isTrue();
                verify(remoteCloseHook, never()).handle(anyString());
            });
            ctx.completeNow();
        }));
    }

    /**
     * Verifies that the attempt to create a receiver fails with a
     * {@code ServerErrorException} if the remote peer doesn't
     * send its attach frame in time.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testCreateReceiverFailsOnTimeout(final VertxTestContext ctx) {

        final ProtonReceiver receiver = mock(ProtonReceiver.class);
        when(receiver.isOpen()).thenReturn(Boolean.TRUE);
        when(session.createReceiver(anyString())).thenReturn(receiver);
        final Handler<String> remoteCloseHook = VertxMockSupport.mockHandler();

        // GIVEN an established connection
        final var honoConnection = new HonoConnectionImpl(vertx, props, connectionFactory);
        honoConnection.connect()
            .compose(c -> honoConnection.createReceiver(
                "source", ProtonQoS.AT_LEAST_ONCE, (delivery, msg) -> {}, remoteCloseHook))
            .onComplete(ctx.failing(t -> {
                ctx.verify(() -> {
                    assertThat(((ServerErrorException) t).getErrorCode()).isEqualTo(HttpURLConnection.HTTP_UNAVAILABLE);
                    verify(receiver).open();
                    verify(receiver).close();
                    verify(remoteCloseHook, never()).handle(anyString());
                });
                ctx.completeNow();
            }));
    }

    /**
     * Verifies that the attempt to create a receiver fails with a
     * {@code ServerErrorException} if the connection gets disconnected
     * before the remote peer has sent its attach frame. It is verified
     * that this is done before the link establishment timeout.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testCreateReceiverFailsOnDisconnectBeforeOpen(final VertxTestContext ctx) {

        final long linkEstablishmentTimeout = 444L; // choose a distinct value here
        props.setLinkEstablishmentTimeout(linkEstablishmentTimeout);
        // don't run linkEstablishmentTimeout timer handler
        when(vertx.setTimer(eq(linkEstablishmentTimeout), VertxMockSupport.anyHandler())).thenAnswer(invocation -> 0L);

        final Source source = mock(Source.class);
        when(source.getAddress()).thenReturn("source/address");
        final ProtonReceiver receiver = mock(ProtonReceiver.class);
        when(receiver.isOpen()).thenReturn(Boolean.TRUE);
        when(receiver.getSource()).thenReturn(source);
        when(receiver.getRemoteSource()).thenReturn(source);
        when(session.createReceiver(anyString())).thenReturn(receiver);

        final Handler<String> remoteCloseHook = VertxMockSupport.mockHandler();

        // GIVEN an established connection
        final var honoConnection = new HonoConnectionImpl(vertx, props, connectionFactory);
        honoConnection.connect()
            .compose(c -> {
                // WHEN creating a receiver link with a close hook
                final Future<ProtonReceiver> result = honoConnection.createReceiver("source", ProtonQoS.AT_LEAST_ONCE,
                        mock(ProtonMessageHandler.class), remoteCloseHook);
                // THEN the result is not completed at first
                ctx.verify(() -> assertThat(result.isComplete()).isFalse());
                // WHEN the downstream connection fails
                connectionFactory.getDisconnectHandler().handle(con);
                return result;
            })
            // THEN the attempt is failed
            .onComplete(ctx.failing(t -> {
                ctx.verify(() -> assertThat(((ServerErrorException) t).getErrorCode()).isEqualTo(HttpURLConnection.HTTP_UNAVAILABLE));
                ctx.completeNow();
            }));
    }

    /**
     * Verifies that the attempt to create a sender fails with a
     * {@code ServiceInvocationException} if the remote peer refuses
     * to open the link with an error condition.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testCreateSenderFailsForErrorCondition(final VertxTestContext ctx) {

        testCreateSenderFails(
                ctx,
                () -> new ErrorCondition(AmqpError.RESOURCE_LIMIT_EXCEEDED, "unauthorized"),
                cause -> {
                    return cause instanceof ServiceInvocationException;
                });
    }

    /**
     * Verifies that the attempt to create a sender fails with a
     * {@code ClientErrorException} if the remote peer refuses
     * to open the link without an error condition.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testCreateSenderFailsWithoutErrorCondition(final VertxTestContext ctx) {

        testCreateSenderFails(
                ctx,
                () -> null,
                cause -> {
                    return cause instanceof ClientErrorException &&
                        ((ClientErrorException) cause).getErrorCode() == HttpURLConnection.HTTP_NOT_FOUND;
                });
    }

    private void testCreateSenderFails(
            final VertxTestContext ctx,
            final Supplier<ErrorCondition> errorSupplier,
            final Predicate<Throwable> failureAssertion) {

        final ProtonSender sender = mock(ProtonSender.class);
        when(sender.getRemoteCondition()).thenReturn(errorSupplier.get());
        when(session.createSender(anyString())).thenReturn(sender);
        final Handler<String> remoteCloseHook = VertxMockSupport.mockHandler();
        when(vertx.setTimer(anyLong(), VertxMockSupport.anyHandler())).thenAnswer(invocation -> {
            // do not run timers immediately
            return 0L;
        });

        // GIVEN an established connection
        final var honoConnection = new HonoConnectionImpl(vertx, props, connectionFactory);
        honoConnection.connect()
            .compose(c -> {
                final Future<ProtonSender> s = honoConnection.createSender(
                        "target", ProtonQoS.AT_LEAST_ONCE, remoteCloseHook);
                ctx.verify(() -> {
                    verify(vertx).setTimer(eq(props.getLinkEstablishmentTimeout()), VertxMockSupport.anyHandler());
                    final ArgumentCaptor<Handler<AsyncResult<ProtonSender>>> openHandler = VertxMockSupport.argumentCaptorHandler();
                    verify(sender).openHandler(openHandler.capture());
                    openHandler.getValue().handle(Future.failedFuture(new IllegalStateException()));
                });
                return s;
            })
            .onComplete(ctx.failing(t -> {
                ctx.verify(() -> {
                    assertThat(failureAssertion.test(t)).isTrue();
                    verify(remoteCloseHook, never()).handle(anyString());
                });
                ctx.completeNow();
            }));
    }

    /**
     * Verifies that the attempt to create a sender fails with a
     * {@code ServerErrorException} if the remote peer doesn't
     * send its attach frame in time.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testCreateSenderFailsOnTimeout(final VertxTestContext ctx) {

        final ProtonSender sender = mock(ProtonSender.class);
        when(sender.isOpen()).thenReturn(Boolean.TRUE);
        when(session.createSender(anyString())).thenReturn(sender);
        final Handler<String> remoteCloseHook = VertxMockSupport.mockHandler();

        // GIVEN an established connection
        final var honoConnection = new HonoConnectionImpl(vertx, props, connectionFactory);
        honoConnection.connect()
            .compose(c -> honoConnection.createSender(
                "target", ProtonQoS.AT_LEAST_ONCE, remoteCloseHook))
            .onComplete(ctx.failing(t -> {
                ctx.verify(() -> {
                    assertThat(((ServerErrorException) t).getErrorCode()).isEqualTo(HttpURLConnection.HTTP_UNAVAILABLE);
                    verify(sender).open();
                    verify(sender).close();
                    verify(remoteCloseHook, never()).handle(anyString());
                });
                ctx.completeNow();
            }));
    }

    /**
     * Verifies that the attempt to create a sender for a {@code null} target address
     * fails with a {@code ServerErrorException} if the remote peer doesn't
     * support the anonymous terminus.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testCreateSenderFailsForUnsupportedAnonTerminus(final VertxTestContext ctx) {

        when(con.getRemoteOfferedCapabilities()).thenReturn(new Symbol[] {Symbol.valueOf("some-feature")});
        final Handler<String> remoteCloseHook = VertxMockSupport.mockHandler();

        // GIVEN an established connection
        final var honoConnection = new HonoConnectionImpl(vertx, props, connectionFactory);
        honoConnection.connect()
            .compose(c -> honoConnection.createSender(
                null, ProtonQoS.AT_LEAST_ONCE, remoteCloseHook))
            .onComplete(ctx.failing(t -> {
                ctx.verify(() -> {
                    // THEN the attempt fails
                    assertThat(((ServerErrorException) t).getErrorCode()).isEqualTo(HttpURLConnection.HTTP_NOT_IMPLEMENTED);
                    // and the remote close hook is not invoked
                    verify(remoteCloseHook, never()).handle(anyString());
                });
                ctx.completeNow();
            }));

    }

    /**
     * Verifies that the attempt to create a sender fails with a
     * {@code ServerErrorException} if the remote peer sends a
     * {@code null} target in its attach frame.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testCreateSenderFailsIfPeerDoesNotCreateTerminus(final VertxTestContext ctx) {

        final ProtonSender sender = mock(ProtonSender.class);
        when(sender.getRemoteTarget()).thenReturn(null);
        when(session.createSender(anyString())).thenReturn(sender);
        final Handler<String> remoteCloseHook = VertxMockSupport.mockHandler();

        // GIVEN an established connection
        final var honoConnection = new HonoConnectionImpl(vertx, props, connectionFactory);
        honoConnection.connect()
            .compose(c -> {
                // WHEN the client tries to open a sender link
                final Future<ProtonSender> s = c.createSender(
                        TelemetryConstants.TELEMETRY_ENDPOINT, ProtonQoS.AT_LEAST_ONCE, remoteCloseHook);
                ctx.verify(() -> {
                    final ArgumentCaptor<Handler<AsyncResult<ProtonSender>>> openHandler = VertxMockSupport.argumentCaptorHandler();
                    verify(sender).open();
                    verify(sender).openHandler(openHandler.capture());
                    // and the peer does not allocate a local terminus for the link
                    openHandler.getValue().handle(Future.succeededFuture(sender));
                });
                return s;
            })
            .onComplete(ctx.failing(t -> {
                ctx.verify(() -> {

                    // THEN the link does not get established
                    assertThat(((ServerErrorException) t).getErrorCode()).isEqualTo(HttpURLConnection.HTTP_UNAVAILABLE);
                    // and the remote close hook does not get invoked
                    verify(remoteCloseHook, never()).handle(anyString());
                });
                ctx.completeNow();
            }));

    }

    /**
     * Verifies that the attempt to create a sender fails with a
     * {@code ClientErrorException} if the remote peer does not support the
     * client's minimum max-message-size.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testCreateSenderFailsForInsufficientMaxMessageSize(final VertxTestContext ctx) {

        // GIVEN a client that requires a minimum max-message-size of 2kb
        props.setMinMaxMessageSize(2048L);
        final var honoConnection = new HonoConnectionImpl(vertx, props, connectionFactory);

        // WHEN trying to open a sender link to a peer that has a max-message-size of 1kb
        final ProtonSender sender = mock(ProtonSender.class);
        when(sender.isOpen()).thenReturn(Boolean.TRUE);
        when(session.createSender(anyString())).thenReturn(sender);
        final Target target = new Target();
        target.setAddress("someAddress");
        when(sender.getRemoteTarget()).thenReturn(target);
        when(sender.getRemoteMaxMessageSize()).thenReturn(new UnsignedLong(1024L));
        // mock handlers
        doAnswer(AdditionalAnswers.answerVoid(
                (final Handler<AsyncResult<ProtonSender>> handler) -> handler.handle(Future.succeededFuture(sender))))
                        .when(sender).openHandler(VertxMockSupport.anyHandler());
        final Handler<String> remoteCloseHook = VertxMockSupport.mockHandler();

        honoConnection.connect()
            .compose(c -> honoConnection.createSender(
                "target", ProtonQoS.AT_LEAST_ONCE, remoteCloseHook))
            .onComplete(ctx.failing(t -> {
                // THEN link establishment fails
                ctx.verify(() -> {
                    assertThat(t).isInstanceOf(ClientErrorException.class);
                    // and the sender link has been closed locally
                    verify(sender).close();
                });
                ctx.completeNow();
            }));

    }

    /**
     * Verifies that the attempt to create a sender succeeds when sender never gets credits.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testCreateSenderThatGetsNoCredits(final VertxTestContext ctx) {
        final ProtonSender sender = mock(ProtonSender.class);
        when(sender.isOpen()).thenReturn(Boolean.TRUE);
        when(session.createSender(anyString())).thenReturn(sender);
        final Target target = new Target();
        target.setAddress("someAddress");
        when(sender.getRemoteTarget()).thenReturn(target);
        when(sender.getCredit()).thenReturn(0);
        // just invoke openHandler with succeeded future
        doAnswer(AdditionalAnswers.answerVoid(
                (final Handler<AsyncResult<ProtonSender>> handler) -> handler.handle(Future.succeededFuture(sender))))
                        .when(sender).openHandler(VertxMockSupport.anyHandler());
        final Handler<String> remoteCloseHook = VertxMockSupport.mockHandler();

        // GIVEN an established connection
        final var honoConnection = new HonoConnectionImpl(vertx, props, connectionFactory);
        honoConnection.connect()
            .compose(c -> honoConnection.createSender(
                "target", ProtonQoS.AT_LEAST_ONCE, remoteCloseHook))
            .onComplete(ctx.succeeding(s -> {
                    ctx.verify(() -> {
                        assertThat(s).isEqualTo(sender);
                        // sendQueueDrainHandler gets unset
                        verify(sender).sendQueueDrainHandler(null);
                    });
                    ctx.completeNow();
                }));
    }

    /**
     * Verifies that the attempt to create a sender succeeds when sender gets credits within flowLatency.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testCreateSenderThatGetsDelayedCredits(final VertxTestContext ctx) {
        // We need to delay timer task. In this case simply forever.
        final long waitOnCreditsTimerId = 23;
        when(vertx.setTimer(anyLong(), VertxMockSupport.anyHandler())).thenAnswer(invocation -> {
            // do not call handler any time
            return waitOnCreditsTimerId;
        });
        when(vertx.cancelTimer(waitOnCreditsTimerId)).thenReturn(true);

        final ProtonSender sender = mock(ProtonSender.class);
        when(sender.isOpen()).thenReturn(Boolean.TRUE);
        when(session.createSender(anyString())).thenReturn(sender);
        final Target target = new Target();
        target.setAddress("someAddress");
        when(sender.getRemoteTarget()).thenReturn(target);
        when(sender.getCredit()).thenReturn(0);
        // mock handlers
        doAnswer(AdditionalAnswers.answerVoid(
                (final Handler<AsyncResult<ProtonSender>> handler) -> handler.handle(Future.succeededFuture(sender))))
                        .when(sender).openHandler(VertxMockSupport.anyHandler());
        doAnswer(AdditionalAnswers.answerVoid(
                (final Handler<ProtonSender> handler) -> handler.handle(sender)))
                        .when(sender).sendQueueDrainHandler(VertxMockSupport.anyHandler());
        final Handler<String> remoteCloseHook = VertxMockSupport.mockHandler();

        // GIVEN an established connection
        final var honoConnection = new HonoConnectionImpl(vertx, props, connectionFactory);
        honoConnection.connect()
            .compose(c -> honoConnection.createSender(
                "target", ProtonQoS.AT_LEAST_ONCE, remoteCloseHook))
            .onComplete(ctx.succeeding(s -> {
                    ctx.verify(() -> {
                        assertThat(s).isEqualTo(sender);
                        // sendQueueDrainHandler gets unset
                        verify(sender).sendQueueDrainHandler(null);
                        verify(vertx).cancelTimer(waitOnCreditsTimerId);
                    });
                    ctx.completeNow();
                }));
    }

    /**
     * Verifies that the attempt to create a sender fails with a
     * {@code ServerErrorException} if the connection gets disconnected
     * before the remote peer has sent its attach frame. It is verified
     * that this is done before the link establishment timeout.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testCreateSenderFailsOnDisconnectBeforeOpen(final VertxTestContext ctx) {
        final long linkEstablishmentTimeout = 444L; // choose a distinct value here
        props.setLinkEstablishmentTimeout(linkEstablishmentTimeout);
        // don't run linkEstablishmentTimeout timer handler
        when(vertx.setTimer(eq(linkEstablishmentTimeout), VertxMockSupport.anyHandler())).thenAnswer(invocation -> 0L);

        final ProtonSender sender = mock(ProtonSender.class);
        when(sender.isOpen()).thenReturn(Boolean.TRUE);
        when(session.createSender(anyString())).thenReturn(sender);
        final Target target = new Target();
        target.setAddress("someAddress");
        when(sender.getRemoteTarget()).thenReturn(target);
        when(sender.getCredit()).thenReturn(0);
        // mock handlers
        final Handler<String> remoteCloseHook = VertxMockSupport.mockHandler();

        // GIVEN an established connection
        final var honoConnection = new HonoConnectionImpl(vertx, props, connectionFactory);
        honoConnection.connect()
            .compose(c -> {
                // WHEN creating a sender link with a close hook
                final Future<ProtonSender> result = honoConnection.createSender(
                        "target", ProtonQoS.AT_LEAST_ONCE, remoteCloseHook);
                // THEN the result is not completed at first
                ctx.verify(() -> assertThat(result.isComplete()).isFalse());
                // WHEN the downstream connection fails
                connectionFactory.getDisconnectHandler().handle(con);
                return result;
            })
            // THEN the attempt is failed
            .onComplete(ctx.failing(t -> {
                ctx.verify(() -> assertThat(((ServerErrorException) t).getErrorCode()).isEqualTo(HttpURLConnection.HTTP_UNAVAILABLE));
                ctx.completeNow();
            }));
    }

    /**
     * Verifies that the calculation of the maximum reconnect delay value works as expected.
     */
    @Test
    public void testGetReconnectMaxDelay() {
        final long reconnectMaxDelay = 20000;
        props.setReconnectMaxDelay(reconnectMaxDelay);
        props.setReconnectDelayIncrement(100);
        final var honoConnection = new HonoConnectionImpl(vertx, props, connectionFactory);
        assertThat(honoConnection.getReconnectMaxDelay(0)).isEqualTo(0);
        assertThat(honoConnection.getReconnectMaxDelay(1)).isEqualTo(100);
        assertThat(honoConnection.getReconnectMaxDelay(3)).isEqualTo(400);
        assertThat(honoConnection.getReconnectMaxDelay(31)).isEqualTo(reconnectMaxDelay);
        assertThat(honoConnection.getReconnectMaxDelay(Integer.MAX_VALUE)).isEqualTo(reconnectMaxDelay);
    }

    /**
     * Verifies that the calculation of the maximum reconnect delay value works as expected for large reconnect
     * attempt numbers.
     */
    @Test
    public void testGetReconnectMaxDelayWorksForLargeNumberOfReconnectAttempts() {
        final long reconnectMaxDelay = 20000;
        props.setReconnectMaxDelay(reconnectMaxDelay);
        props.setReconnectDelayIncrement(Long.MAX_VALUE);
        final var honoConnection = new HonoConnectionImpl(vertx, props, connectionFactory);
        assertThat(honoConnection.getReconnectMaxDelay(31)).isEqualTo(reconnectMaxDelay);
        assertThat(honoConnection.getReconnectMaxDelay(Integer.MAX_VALUE)).isEqualTo(reconnectMaxDelay);
    }
}
