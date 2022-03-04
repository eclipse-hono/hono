/**
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */


package org.eclipse.hono.application.client.amqp;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static com.google.common.truth.Truth.assertThat;

import java.net.HttpURLConnection;
import java.util.concurrent.TimeUnit;

import org.apache.qpid.proton.amqp.messaging.Accepted;
import org.apache.qpid.proton.amqp.messaging.Modified;
import org.apache.qpid.proton.amqp.messaging.Rejected;
import org.apache.qpid.proton.amqp.messaging.Released;
import org.eclipse.hono.application.client.DownstreamMessage;
import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.amqp.connection.DisconnectListener;
import org.eclipse.hono.client.amqp.connection.HonoConnection;
import org.eclipse.hono.client.amqp.test.AmqpClientUnitTestHelper;
import org.eclipse.hono.test.VertxMockSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.proton.ProtonDelivery;
import io.vertx.proton.ProtonHelper;
import io.vertx.proton.ProtonMessageHandler;
import io.vertx.proton.ProtonQoS;
import io.vertx.proton.ProtonReceiver;
import io.vertx.proton.ProtonSender;


/**
 * Tests verifying behavior of {@link ProtonBasedApplicationClient}.
 *
 */
@ExtendWith(VertxExtension.class)
@Timeout(value = 5, timeUnit = TimeUnit.SECONDS)
class ProtonBasedApplicationClientTest {

    private HonoConnection connection;
    private ProtonBasedApplicationClient client;

    /**
     * Sets up the fixture.
     */
    @BeforeEach
    void setUp() {
        final var vertx = mock(Vertx.class);
        when(vertx.eventBus()).thenReturn(mock(EventBus.class));
        connection = AmqpClientUnitTestHelper.mockHonoConnection(vertx);
        final ProtonReceiver receiver = AmqpClientUnitTestHelper.mockProtonReceiver();
        when(connection.createReceiver(
                anyString(),
                any(ProtonQoS.class),
                any(ProtonMessageHandler.class),
                anyInt(),
                anyBoolean(),
                VertxMockSupport.anyHandler())).thenReturn(Future.succeededFuture(receiver));
        final ProtonSender sender = AmqpClientUnitTestHelper.mockProtonSender();
        when(connection.createSender(anyString(), any(), any())).thenReturn(Future.succeededFuture(sender));
        client = new ProtonBasedApplicationClient(connection);
    }

    /**
     * Verifies that starting the client triggers the underlying connection to be established.
     *
     * @param ctx The vertx test context.
     */
    @Test
    void testConnectTriggersConnectionEstablishment(final VertxTestContext ctx) {
        when(connection.connect()).thenReturn(Future.succeededFuture(connection));
        client.connect().onComplete(ctx.succeeding(ok -> {
            ctx.verify(() -> verify(connection).connect());
            ctx.completeNow();
        }));
    }

    /**
     * Verifies that disconnecting the client stops the underlying connection.
     */
    @Test
    void testDisconnectTerminatesConnection() {
        client.disconnect();
        verify(connection).disconnect(VertxMockSupport.anyHandler());
    }

    /**
     * Verifies that disconnecting the client stops the underlying connection.
     */
    @Test
    void testDisconnectWithHandlerTerminatesConnection() {

        final Promise<Void> result = Promise.promise();
        client.disconnect(result);
        assertThat(result.future().isComplete()).isFalse();
        final ArgumentCaptor<Handler<AsyncResult<Void>>> resultHandler = VertxMockSupport.argumentCaptorHandler();
        verify(connection).disconnect(resultHandler.capture());
        resultHandler.getValue().handle(Future.succeededFuture());
        assertThat(result.future().succeeded()).isTrue();
    }

    /**
     * Verifies that the message consumer created by the factory catches an exception
     * thrown by the client provided handler and releases the message.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    @SuppressWarnings("unchecked")
    void testCreateTelemetryConsumerReleasesMessageOnException(final VertxTestContext ctx) {

        // GIVEN a client provided message handler that throws an exception on
        // each message received
        final Handler<DownstreamMessage<AmqpMessageContext>> consumer = VertxMockSupport.mockHandler();
        doThrow(new IllegalArgumentException("message does not contain required properties"),
                new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST))
            .when(consumer).handle(any(DownstreamMessage.class));

        client.createTelemetryConsumer("tenant", consumer, t -> {})
            .onComplete(ctx.succeeding(mc -> {
                final ArgumentCaptor<ProtonMessageHandler> messageHandler = ArgumentCaptor.forClass(ProtonMessageHandler.class);
                ctx.verify(() -> {
                    verify(connection).createReceiver(
                            eq("telemetry/tenant"),
                            eq(ProtonQoS.AT_LEAST_ONCE),
                            messageHandler.capture(),
                            anyInt(),
                            anyBoolean(),
                            VertxMockSupport.anyHandler());

                    final var msg = ProtonHelper.message();
                    // WHEN a message is received and the client provided consumer
                    // throws an IllegalArgumentException
                    var delivery = mock(ProtonDelivery.class);
                    messageHandler.getValue().handle(delivery, msg);
                    // THEN the message is forwarded to the client provided handler
                    verify(consumer).handle(any(DownstreamMessage.class));
                    // AND the AMQP message is being released
                    verify(delivery).disposition(any(Released.class), eq(Boolean.TRUE));

                    // WHEN a message is received and the client provided consumer
                    // throws a ClientErrorException
                    delivery = mock(ProtonDelivery.class);
                    messageHandler.getValue().handle(delivery, msg);
                    // THEN the message is forwarded to the client provided handler
                    verify(consumer, times(2)).handle(any(DownstreamMessage.class));
                    // AND the AMQP message is being rejected
                    verify(delivery).disposition(any(Rejected.class), eq(Boolean.TRUE));
                });
                ctx.completeNow();
            }));
    }

    /**
     * Verifies that the close handler provided when creating the message consumer is invoked
     * when the consumer connection gets closed by the remote peer.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    void testConsumerCloseHandlerIsInvokedOnDisconnect(final VertxTestContext ctx) {

        @SuppressWarnings("unchecked")
        final ArgumentCaptor<DisconnectListener<HonoConnection>> disconnectHandlerCaptor = ArgumentCaptor.forClass(DisconnectListener.class);
        verify(connection, atLeastOnce()).addDisconnectListener(disconnectHandlerCaptor.capture());

        final Handler<DownstreamMessage<AmqpMessageContext>> consumer = VertxMockSupport.mockHandler();
        final Handler<Throwable> closeHandler = VertxMockSupport.mockHandler();

        // GIVEN a consumer created with a close handler
        client.createTelemetryConsumer("tenant", consumer, closeHandler)
                .onComplete(ctx.succeeding(mc -> {
                    ctx.verify(() -> {
                        verify(connection).createReceiver(
                                eq("telemetry/tenant"),
                                eq(ProtonQoS.AT_LEAST_ONCE),
                                any(ProtonMessageHandler.class),
                                anyInt(),
                                anyBoolean(),
                                VertxMockSupport.anyHandler());

                        // WHEN the underlying connection is closed and the disconnect handlers get invoked
                        disconnectHandlerCaptor.getAllValues().forEach(h -> h.onDisconnect(connection));
                        // THEN the consumer close handler is invoked
                        verify(closeHandler).handle(any());
                    });
                    ctx.completeNow();
                }));
    }

    /**
     * Verifies that the message consumer created by the factory allows the client provided handler
     * to manually perform a disposition update for a received message.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    @SuppressWarnings("unchecked")
    void testCreateTelemetryConsumerSupportsManualDispositionHandling(final VertxTestContext ctx) {

        // GIVEN a client provided message handler that manually settles messages
        final Handler<DownstreamMessage<AmqpMessageContext>> consumer = VertxMockSupport.mockHandler();

        // WHEN creating a telemetry consumer
        client.createTelemetryConsumer("tenant", consumer, t -> {})
            .onComplete(ctx.succeeding(mc -> {
                final ArgumentCaptor<ProtonMessageHandler> messageHandler = ArgumentCaptor.forClass(ProtonMessageHandler.class);
                ctx.verify(() -> {
                    verify(connection).createReceiver(
                            eq("telemetry/tenant"),
                            eq(ProtonQoS.AT_LEAST_ONCE),
                            messageHandler.capture(),
                            anyInt(),
                            anyBoolean(),
                            VertxMockSupport.anyHandler());
                    final var delivery = mock(ProtonDelivery.class);
                    final var msg = ProtonHelper.message();
                    // over which a message is being received
                    messageHandler.getValue().handle(delivery, msg);
                    // THEN the message is forwarded to the client provided handler
                    final ArgumentCaptor<DownstreamMessage<AmqpMessageContext>> downstreamMessage = ArgumentCaptor.forClass(DownstreamMessage.class);
                    verify(consumer).handle(downstreamMessage.capture());
                    final var messageContext = downstreamMessage.getValue().getMessageContext();
                    ProtonHelper.modified(messageContext.getDelivery(), true, true, true);
                    // AND the AMQP message is being settled with the modified outcome
                    verify(delivery).disposition(any(Modified.class), eq(Boolean.TRUE));
                });
                ctx.completeNow();
            }));
    }

    /**
     * Verifies that the message consumer created by the factory settles an event with the
     * accepted outcome if the client provided handler does not throw an exception.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    @SuppressWarnings("unchecked")
    void testCreateEventConsumerAcceptsMessage(final VertxTestContext ctx) {

        // GIVEN a client provided message handler
        final Handler<DownstreamMessage<AmqpMessageContext>> consumer = VertxMockSupport.mockHandler();

        // WHEN creating an event consumer
        client.createEventConsumer("tenant", consumer, t -> {})
            .onComplete(ctx.succeeding(mc -> {
                final ArgumentCaptor<ProtonMessageHandler> messageHandler = ArgumentCaptor.forClass(ProtonMessageHandler.class);
                ctx.verify(() -> {
                    verify(connection).createReceiver(
                            eq("event/tenant"),
                            eq(ProtonQoS.AT_LEAST_ONCE),
                            messageHandler.capture(),
                            anyInt(),
                            anyBoolean(),
                            VertxMockSupport.anyHandler());
                    final var delivery = mock(ProtonDelivery.class);
                    when(delivery.isSettled()).thenReturn(Boolean.FALSE);
                    final var msg = ProtonHelper.message();
                    // over which a message is being received
                    messageHandler.getValue().handle(delivery, msg);
                    // THEN the message is forwarded to the client provided handler
                    verify(consumer).handle(any(DownstreamMessage.class));
                    // AND the AMQP message is being accepted
                    verify(delivery).disposition(any(Accepted.class), eq(Boolean.TRUE));
                });
                ctx.completeNow();
            }));
    }

}
