/*******************************************************************************
 * Copyright (c) 2016, 2020 Contributors to the Eclipse Foundation
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

import static org.assertj.core.api.Assertions.assertThat;
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

import java.net.HttpURLConnection;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.apache.qpid.proton.amqp.Symbol;
import org.apache.qpid.proton.amqp.messaging.Target;
import org.apache.qpid.proton.amqp.transport.AmqpError;
import org.apache.qpid.proton.amqp.transport.ErrorCondition;
import org.apache.qpid.proton.amqp.transport.Source;
import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.DisconnectListener;
import org.eclipse.hono.client.HonoConnection;
import org.eclipse.hono.client.ServerErrorException;
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.config.ClientConfigProperties;
import org.eclipse.hono.connection.ConnectionFactory;
import org.eclipse.hono.util.TelemetryConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.AdditionalAnswers;
import org.mockito.ArgumentCaptor;

import io.vertx.core.AsyncResult;
import io.vertx.core.CompositeFuture;
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
    private DisconnectHandlerProvidingConnectionFactory connectionFactory;
    private ClientConfigProperties props;
    private HonoConnectionImpl honoConnection;

    /**
     * Sets up fixture.
     */
    @SuppressWarnings("unchecked")
    @BeforeEach
    public void setUp() {
        vertx = mock(Vertx.class);
        final Context context = HonoClientUnitTestHelper.mockContext(vertx);
        when(vertx.getOrCreateContext()).thenReturn(context);
        // run any timer immediately
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
    public void testConnectFailsAfterMaxConnectionAttempts(final VertxTestContext ctx) {

        // GIVEN a client that is configured to reconnect
        // two times before failing
        props.setReconnectAttempts(2);
        props.setConnectTimeout(10);
        // expect three unsuccessful connection attempts
        connectionFactory = new DisconnectHandlerProvidingConnectionFactory(con)
                .setExpectedFailingConnectionAttempts(3);
        honoConnection = new HonoConnectionImpl(vertx, connectionFactory, props);

        // WHEN the client tries to connect
        honoConnection.connect().setHandler(ctx.failing(t -> {
            // THEN the connection attempt fails
            ctx.verify(() -> assertThat(((ServerErrorException) t).getErrorCode()).isEqualTo(HttpURLConnection.HTTP_UNAVAILABLE));
        }));
        // and the client has indeed tried three times in total before giving up
        ctx.verify(() -> assertThat(connectionFactory.awaitFailure()).isTrue());
        ctx.completeNow();
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
        honoConnection = new HonoConnectionImpl(vertx, connectionFactory, props);

        // WHEN the client tries to connect
        honoConnection.connect().setHandler(ctx.failing(t -> {
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
        honoConnection = new HonoConnectionImpl(vertx, connectionFactory, props);

        // WHEN the client tries to connect
        honoConnection.connect().setHandler(ctx.failing(t -> {
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
        honoConnection = new HonoConnectionImpl(vertx, connectionFactory, props);
        honoConnection.connect(options).setHandler(ctx.succeeding());
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
        ctx.verify(() -> assertThat(connectionFactory.await()).isTrue());
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
        honoConnection = new HonoConnectionImpl(vertx, connectionFactory, props);

        // WHEN trying to connect
        honoConnection.connect().setHandler(ctx.succeeding());

        ctx.verify(() -> {
            // THEN the client fails twice to connect
            assertThat(connectionFactory.awaitFailure()).isTrue();
            // and succeeds to connect on the third attempt
            assertThat(connectionFactory.await()).isTrue();
        });
        ctx.completeNow();
    }

    /**
     * Verifies that the client tries to re-connect to a server instance if the
     * connection is closed by the peer.
     * 
     * @param ctx The test context.
     *
     */
    @Test
    public void testOnRemoteCloseTriggersReconnection(final VertxTestContext ctx) {

        // GIVEN a client that is connected to a server
        final Promise<HonoConnection> connected = Promise.promise();
        @SuppressWarnings("unchecked")
        final DisconnectListener<HonoConnection> disconnectListener = mock(DisconnectListener.class);
        honoConnection.addDisconnectListener(disconnectListener);
        honoConnection.connect(new ProtonClientOptions().setReconnectAttempts(1))
            .setHandler(connected);
        connectionFactory.setExpectedSucceedingConnectionAttempts(1);

        connected.future().setHandler(ctx.succeeding(c -> {
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
            });
            ctx.completeNow();
        }));
    }

    /**
     * Verifies that it fails to connect after client was shutdown.
     * 
     * @param ctx The test context.
     *
     */
    @Test
    public void testConnectFailsAfterShutdown(final VertxTestContext ctx) {

        honoConnection.connect().compose(ok -> {
            // GIVEN a client that is in the process of shutting down
            honoConnection.shutdown(Promise.<Void>promise().future());
            // WHEN the client tries to reconnect before shut down is complete
            return honoConnection.connect();
        })
        .setHandler(ctx.failing(cause -> {
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

        honoConnection.connect()
            .compose(ok -> {
                // GIVEN a client that is connected to a server
                final Promise<Void> disconnected = Promise.promise();
                // WHEN the client disconnects
                honoConnection.disconnect(disconnected);
                @SuppressWarnings("unchecked")
                final ArgumentCaptor<Handler<AsyncResult<ProtonConnection>>> closeHandler = ArgumentCaptor.forClass(Handler.class);
                ctx.verify(() -> verify(con).closeHandler(closeHandler.capture()));
                closeHandler.getValue().handle(Future.succeededFuture(con));
                return disconnected.future();
            })
            .compose(d -> {
                // AND tries to reconnect again
                return honoConnection.connect(new ProtonClientOptions());
            })
            // THEN the connection succeeds
            .setHandler(ctx.completing());
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
        when(vertx.setTimer(eq(isConnectedTimeout), any(Handler.class))).thenAnswer(invocation -> 0L);
        final AtomicBoolean isConnectedInvocationsDone = new AtomicBoolean(false);
        final AtomicReference<Future<Void>> isConnected1FutureRef = new AtomicReference<>();
        final AtomicReference<Future<Void>> isConnectedTimeoutForcedFutureRef = new AtomicReference<>();
        final AtomicReference<Future<Void>> isConnected2FutureRef = new AtomicReference<>();
        // GIVEN a client that is configured to connect to a peer
        // to which the connection can be established on the third attempt only
        connectionFactory = new DisconnectHandlerProvidingConnectionFactory(con) {
            @Override
            public void connect(final ProtonClientOptions options, final String username, final String password,
                                final Handler<AsyncResult<ProtonConnection>> closeHandler,
                                final Handler<ProtonConnection> disconnectHandler,
                                final Handler<AsyncResult<ProtonConnection>> connectionResultHandler) {
                // and GIVEN "isConnected" invocations done while the "connect" invocation is still in progress
                if (isConnectedInvocationsDone.compareAndSet(false, true)) {
                    isConnected1FutureRef.set(honoConnection.isConnected(isConnectedTimeout));
                    isConnectedTimeoutForcedFutureRef.set(honoConnection.isConnected(1L));
                    isConnected2FutureRef.set(honoConnection.isConnected(isConnectedTimeout));
                    // assert "isConnected" invocations have not completed yet, apart from the one with the forced timeout
                    ctx.verify(() -> {
                        assertThat(isConnected1FutureRef.get().isComplete()).isFalse();
                        assertThat(isConnectedTimeoutForcedFutureRef.get().failed()).isTrue();
                        assertThat(isConnected2FutureRef.get().isComplete()).isFalse();
                    });
                }
                super.connect(options, username, password, closeHandler, disconnectHandler, connectionResultHandler);
            }
        };
        connectionFactory.setExpectedFailingConnectionAttempts(2);
        props.setReconnectAttempts(2);
        props.setConnectTimeout(10);
        honoConnection = new HonoConnectionImpl(vertx, connectionFactory, props);

        // WHEN trying to connect
        honoConnection.connect()
                // THEN the "isConnected" futures succeed
                .compose(v -> CompositeFuture.all(isConnected1FutureRef.get(), isConnected2FutureRef.get()))
                .setHandler(ctx.succeeding());

        ctx.verify(() -> {
            // and the client fails twice to connect
            assertThat(connectionFactory.awaitFailure()).isTrue();
            // and succeeds to connect on the third attempt
            assertThat(connectionFactory.await()).isTrue();
        });
        ctx.completeNow();
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
        when(vertx.setTimer(eq(isConnectedTimeout), any(Handler.class))).thenAnswer(invocation -> 0L);
        final AtomicBoolean isConnectedInvocationsDone = new AtomicBoolean(false);
        final AtomicReference<Future<Void>> isConnected1FutureRef = new AtomicReference<>();
        final AtomicReference<Future<Void>> isConnected2FutureRef = new AtomicReference<>();
        // GIVEN a client that is configured to connect to a peer
        // to which the connection can be established on the third attempt only
        connectionFactory = new DisconnectHandlerProvidingConnectionFactory(con) {
            @Override
            public void connect(final ProtonClientOptions options, final String username, final String password,
                                final Handler<AsyncResult<ProtonConnection>> closeHandler,
                                final Handler<ProtonConnection> disconnectHandler,
                                final Handler<AsyncResult<ProtonConnection>> connectionResultHandler) {
                // and GIVEN "isConnected" invocations done while the "connect" invocation is still in progress
                if (isConnectedInvocationsDone.compareAndSet(false, true)) {
                    isConnected1FutureRef.set(honoConnection.isConnected(isConnectedTimeout));
                    isConnected2FutureRef.set(honoConnection.isConnected(isConnectedTimeout));
                    // assert "isConnected" invocations have not completed yet
                    ctx.verify(() -> {
                        assertThat(isConnected1FutureRef.get().isComplete()).isFalse();
                        assertThat(isConnected2FutureRef.get().isComplete()).isFalse();
                    });
                }
                super.connect(options, username, password, closeHandler, disconnectHandler, connectionResultHandler);
            }
        };
        connectionFactory.setExpectedFailingConnectionAttempts(3);
        props.setReconnectAttempts(2);
        props.setConnectTimeout(10);
        honoConnection = new HonoConnectionImpl(vertx, connectionFactory, props);

        // WHEN the client tries to connect
        honoConnection.connect().setHandler(ctx.failing(t -> {
            ctx.verify(() -> {
                // THEN the connection attempt fails and the "isConnected" futures fail as well
                assertThat(((ServerErrorException) t).getErrorCode()).isEqualTo(HttpURLConnection.HTTP_UNAVAILABLE);

                assertThat(isConnected1FutureRef.get().failed()).isTrue();
                assertThat(((ServerErrorException) isConnected1FutureRef.get().cause()).getErrorCode()).isEqualTo(HttpURLConnection.HTTP_UNAVAILABLE);

                assertThat(isConnected2FutureRef.get().failed()).isTrue();
                assertThat(((ServerErrorException) isConnected2FutureRef.get().cause()).getErrorCode()).isEqualTo(HttpURLConnection.HTTP_UNAVAILABLE);
            });
        }));
        // and the client has indeed tried three times in total before giving up
        ctx.verify(() -> assertThat(connectionFactory.awaitFailure()).isTrue());
        ctx.completeNow();
    }

    /**
     * Verifies that the client does not try to re-connect to a server instance if the client was shutdown.
     * 
     * @param ctx The test context.
     *
     */
    @Test
    public void testClientDoesNotTriggerReconnectionAfterShutdown(final VertxTestContext ctx) {

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
        }).when(factory).connect(any(), VertxMockSupport.anyHandler(), VertxMockSupport.anyHandler(), VertxMockSupport.anyHandler());
        honoConnection = new HonoConnectionImpl(vertx, factory, props);
        honoConnection.connect().setHandler(ctx.failing(cause -> {
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

    @SuppressWarnings("unchecked")
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
        when(con.createReceiver(anyString())).thenReturn(receiver);

        final Handler<String> remoteCloseHook = mock(Handler.class);
        final ArgumentCaptor<Handler<AsyncResult<ProtonReceiver>>> captor = ArgumentCaptor.forClass(Handler.class);

        honoConnection.connect()
            .compose(c -> {

                // WHEN creating a receiver link with a close hook

                final Future<ProtonReceiver> r = c.createReceiver(
                        "source",
                        ProtonQoS.AT_LEAST_ONCE,
                        mock(ProtonMessageHandler.class),
                        remoteCloseHook);

                // wait for peer's attach frame
                final ArgumentCaptor<Handler<AsyncResult<ProtonReceiver>>> openHandlerCaptor = ArgumentCaptor.forClass(Handler.class);
                ctx.verify(() -> verify(receiver).openHandler(openHandlerCaptor.capture()));
                openHandlerCaptor.getValue().handle(Future.succeededFuture(receiver));

                return r;
            })
            .setHandler(ctx.succeeding(recv -> {

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
        when(con.createReceiver(anyString())).thenReturn(receiver);
        @SuppressWarnings("unchecked")
        final Handler<String> remoteCloseHook = mock(Handler.class);
        when(vertx.setTimer(anyLong(), VertxMockSupport.anyHandler())).thenAnswer(invocation -> {
            // do not run timers immediately
            return 0L;
        });

        // GIVEN an established connection
        honoConnection.connect()
        .compose(c -> {

            // WHEN creating a receiver
            final Future<ProtonReceiver> r = c.createReceiver(
                    "source", ProtonQoS.AT_LEAST_ONCE, (delivery, msg) -> {}, remoteCloseHook);
            ctx.verify(() -> {
                // and when the peer rejects to open the link
                @SuppressWarnings("unchecked")
                final ArgumentCaptor<Handler<AsyncResult<ProtonReceiver>>> openHandler = ArgumentCaptor.forClass(Handler.class);
                verify(receiver).openHandler(openHandler.capture());
                openHandler.getValue().handle(Future.failedFuture(new IllegalStateException()));
            });
            return r;
        })
        .setHandler(ctx.failing(t -> {
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
        when(con.createReceiver(anyString())).thenReturn(receiver);
        final Handler<String> remoteCloseHook = VertxMockSupport.mockHandler();

        // GIVEN an established connection
        honoConnection.connect()
            .compose(c -> honoConnection.createReceiver(
                "source", ProtonQoS.AT_LEAST_ONCE, (delivery, msg) -> {}, remoteCloseHook))
            .setHandler(ctx.failing(t -> {
                ctx.verify(() -> {
                    assertThat(((ServerErrorException) t).getErrorCode()).isEqualTo(HttpURLConnection.HTTP_UNAVAILABLE);
                    verify(receiver).open();
                    verify(receiver).close();
                    verify(receiver).free();
                    verify(remoteCloseHook, never()).handle(anyString());
                });
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

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private void testCreateSenderFails(
            final VertxTestContext ctx,
            final Supplier<ErrorCondition> errorSupplier,
            final Predicate<Throwable> failureAssertion) {

        final ProtonSender sender = mock(ProtonSender.class);
        when(sender.getRemoteCondition()).thenReturn(errorSupplier.get());
        when(con.createSender(anyString())).thenReturn(sender);
        final Handler<String> remoteCloseHook = mock(Handler.class);
        when(vertx.setTimer(anyLong(), any(Handler.class))).thenAnswer(invocation -> {
            // do not run timers immediately
            return 0L;
        });

        // GIVEN an established connection
        honoConnection.connect()
            .compose(c -> {
                final Future<ProtonSender> s = honoConnection.createSender(
                        "target", ProtonQoS.AT_LEAST_ONCE, remoteCloseHook);
                ctx.verify(() -> {
                    verify(vertx).setTimer(eq(props.getLinkEstablishmentTimeout()), any(Handler.class));
                    final ArgumentCaptor<Handler> openHandler = ArgumentCaptor.forClass(Handler.class);
                    verify(sender).openHandler(openHandler.capture());
                    openHandler.getValue().handle(Future.failedFuture(new IllegalStateException()));
                });
                return s;
            })
            .setHandler(ctx.failing(t -> {
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
    @SuppressWarnings("unchecked")
    @Test
    public void testCreateSenderFailsOnTimeout(final VertxTestContext ctx) {

        final ProtonSender sender = mock(ProtonSender.class);
        when(sender.isOpen()).thenReturn(Boolean.TRUE);
        when(con.createSender(anyString())).thenReturn(sender);
        final Handler<String> remoteCloseHook = mock(Handler.class);

        // GIVEN an established connection
        honoConnection.connect()
            .compose(c -> honoConnection.createSender(
                "target", ProtonQoS.AT_LEAST_ONCE, remoteCloseHook))
            .setHandler(ctx.failing(t -> {
                ctx.verify(() -> {
                    assertThat(((ServerErrorException) t).getErrorCode()).isEqualTo(HttpURLConnection.HTTP_UNAVAILABLE);
                    verify(sender).open();
                    verify(sender).close();
                    verify(sender).free();
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
    @SuppressWarnings("unchecked")
    @Test
    public void testCreateSenderFailsForUnsupportedAnonTerminus(final VertxTestContext ctx) {

        when(con.getRemoteOfferedCapabilities()).thenReturn(new Symbol[] {Symbol.valueOf("some-feature")});
        final Handler<String> remoteCloseHook = mock(Handler.class);

        // GIVEN an established connection
        honoConnection.connect()
            .compose(c -> honoConnection.createSender(
                null, ProtonQoS.AT_LEAST_ONCE, remoteCloseHook))
            .setHandler(ctx.failing(t -> {
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
    @SuppressWarnings("unchecked")
    @Test
    public void testCreateSenderFailsIfPeerDoesNotCreateTerminus(final VertxTestContext ctx) {

        final ProtonSender sender = mock(ProtonSender.class);
        when(sender.getRemoteTarget()).thenReturn(null);
        when(con.createSender(anyString())).thenReturn(sender);
        final Handler<String> remoteCloseHook = mock(Handler.class);

        // GIVEN an established connection
        honoConnection.connect()
            .compose(c -> {
                // WHEN the client tries to open a sender link
                final Future<ProtonSender> s = c.createSender(
                        TelemetryConstants.TELEMETRY_ENDPOINT, ProtonQoS.AT_LEAST_ONCE, remoteCloseHook);
                ctx.verify(() -> {
                    final ArgumentCaptor<Handler<AsyncResult<ProtonSender>>> openHandler = ArgumentCaptor.forClass(Handler.class);
                    verify(sender).open();
                    verify(sender).openHandler(openHandler.capture());
                    // and the peer does not allocate a local terminus for the link
                    openHandler.getValue().handle(Future.succeededFuture(sender));
                });
                return s;
            })
            .setHandler(ctx.failing(t -> {
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
     * Verifies that the attempt to create a sender succeeds when sender never gets credits.
     *
     * @param ctx The vert.x test context.
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testCreateSenderThatGetsNoCredits(final VertxTestContext ctx) {
        final ProtonSender sender = mock(ProtonSender.class);
        when(sender.isOpen()).thenReturn(Boolean.TRUE);
        when(con.createSender(anyString())).thenReturn(sender);
        final Target target = new Target();
        target.setAddress("someAddress");
        when(sender.getRemoteTarget()).thenReturn(target);
        when(sender.getCredit()).thenReturn(0);
        // just invoke openHandler with succeeded future
        doAnswer(AdditionalAnswers.answerVoid(
                (final Handler<AsyncResult<ProtonSender>> handler) -> handler.handle(Future.succeededFuture(sender))))
                        .when(sender).openHandler(any(Handler.class));
        final Handler<String> remoteCloseHook = mock(Handler.class);

        // GIVEN an established connection
        honoConnection.connect()
            .compose(c -> honoConnection.createSender(
                "target", ProtonQoS.AT_LEAST_ONCE, remoteCloseHook))
            .setHandler(ctx.succeeding(s -> {
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
    @SuppressWarnings("unchecked")
    @Test
    public void testCreateSenderThatGetsDelayedCredits(final VertxTestContext ctx) {
        // We need to delay timer task. In this case simply forever.
        final long waitOnCreditsTimerId = 23;
        when(vertx.setTimer(anyLong(), any(Handler.class))).thenAnswer(invocation -> {
            // do not call handler any time
            return waitOnCreditsTimerId;
        });
        when(vertx.cancelTimer(waitOnCreditsTimerId)).thenReturn(true);

        final ProtonSender sender = mock(ProtonSender.class);
        when(sender.isOpen()).thenReturn(Boolean.TRUE);
        when(con.createSender(anyString())).thenReturn(sender);
        final Target target = new Target();
        target.setAddress("someAddress");
        when(sender.getRemoteTarget()).thenReturn(target);
        when(sender.getCredit()).thenReturn(0);
        // mock handlers
        doAnswer(AdditionalAnswers.answerVoid(
                (final Handler<AsyncResult<ProtonSender>> handler) -> handler.handle(Future.succeededFuture(sender))))
                        .when(sender).openHandler(any(Handler.class));
        doAnswer(AdditionalAnswers.answerVoid(
                (final Handler<ProtonSender> handler) -> handler.handle(sender)))
                        .when(sender).sendQueueDrainHandler(any(Handler.class));
        final Handler<String> remoteCloseHook = mock(Handler.class);

        // GIVEN an established connection
        honoConnection.connect()
            .compose(c -> honoConnection.createSender(
                "target", ProtonQoS.AT_LEAST_ONCE, remoteCloseHook))
            .setHandler(ctx.succeeding(s -> {
                    ctx.verify(() -> {
                        assertThat(s).isEqualTo(sender);
                        // sendQueueDrainHandler gets unset
                        verify(sender).sendQueueDrainHandler(null);
                        verify(vertx).cancelTimer(waitOnCreditsTimerId);
                    });
                    ctx.completeNow();
                }));
    }
}
