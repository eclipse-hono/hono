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

package org.eclipse.hono.client.amqp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static com.google.common.truth.Truth.assertThat;

import java.net.HttpURLConnection;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.apache.qpid.proton.amqp.Symbol;
import org.apache.qpid.proton.amqp.messaging.Accepted;
import org.apache.qpid.proton.amqp.messaging.Data;
import org.apache.qpid.proton.amqp.messaging.Rejected;
import org.apache.qpid.proton.amqp.transport.AmqpError;
import org.apache.qpid.proton.amqp.transport.DeliveryState;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.ResourceLimitExceededException;
import org.eclipse.hono.client.ServerErrorException;
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.client.amqp.config.RequestResponseClientConfigProperties;
import org.eclipse.hono.client.amqp.connection.AmqpUtils;
import org.eclipse.hono.client.amqp.connection.HonoConnection;
import org.eclipse.hono.client.amqp.connection.SendMessageSampler;
import org.eclipse.hono.client.amqp.test.AmqpClientUnitTestHelper;
import org.eclipse.hono.test.TracingMockSupport;
import org.eclipse.hono.test.VertxMockSupport;
import org.eclipse.hono.util.MessageHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;

import io.opentracing.Span;
import io.opentracing.Tracer;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonObject;
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
 * Tests verifying behavior of {@link RequestResponseClient}.
 *
 */
@ExtendWith(VertxExtension.class)
@Timeout(value = 5, timeUnit = TimeUnit.SECONDS)
public class RequestResponseClientTest  {

    private Future<RequestResponseClient<SimpleRequestResponseResult>> client;
    private Vertx vertx;
    private HonoConnection connection;
    private ProtonReceiver receiver;
    private ProtonSender sender;
    private RequestResponseClientConfigProperties clientConfig;
    private SendMessageSampler.Sample sample;
    private Span span;

    /**
     * Sets up the fixture.
     */
    @BeforeEach
    public void setUp() {

        span = TracingMockSupport.mockSpan();
        final Tracer tracer = TracingMockSupport.mockTracer(span);

        final EventBus eventBus = mock(EventBus.class);
        vertx = mock(Vertx.class);
        when(vertx.eventBus()).thenReturn(eventBus);
        receiver = AmqpClientUnitTestHelper.mockProtonReceiver();
        sender = AmqpClientUnitTestHelper.mockProtonSender();

        final SendMessageSampler sampler = mock(SendMessageSampler.class);
        sample = mock(SendMessageSampler.Sample.class);
        when(sampler.start(anyString())).thenReturn(sample);

        clientConfig = new RequestResponseClientConfigProperties();
        connection = AmqpClientUnitTestHelper.mockHonoConnection(vertx, clientConfig, tracer);
        when(connection.isConnected(anyLong())).thenReturn(Future.succeededFuture());
        when(connection.createReceiver(anyString(), any(ProtonQoS.class), any(ProtonMessageHandler.class), VertxMockSupport.anyHandler()))
                .thenReturn(Future.succeededFuture(receiver));
        when(connection.createSender(anyString(), any(ProtonQoS.class), VertxMockSupport.anyHandler()))
            .thenReturn(Future.succeededFuture(sender));

        client = RequestResponseClient.forEndpoint(
                connection,
                "ep",
                "tenant",
                sampler,
                VertxMockSupport.mockHandler(),
                VertxMockSupport.mockHandler());
    }

    private void assertFailureCause(
            final Span span,
            final Throwable cause,
            final int expectedErrorCode) {

        assertEquals(
                expectedErrorCode,
                ((ServiceInvocationException) cause).getErrorCode());
        verify(span, never()).finish();
    }

    private Message verifySenderSend() {
        final ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(sender).send(messageCaptor.capture(), VertxMockSupport.anyHandler());
        return messageCaptor.getValue();
    }

    private Message verifySenderSendAndUpdateDelivery(final DeliveryState deliveryState) {
        final ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        final ArgumentCaptor<Handler<ProtonDelivery>> deliveryUpdatedHandlerCaptor = VertxMockSupport.argumentCaptorHandler();
        verify(sender).send(messageCaptor.capture(), deliveryUpdatedHandlerCaptor.capture());

        final ProtonDelivery requestDelivery = mock(ProtonDelivery.class);
        when(requestDelivery.getRemoteState()).thenReturn(deliveryState);
        deliveryUpdatedHandlerCaptor.getValue().handle(requestDelivery);
        return messageCaptor.getValue();
    }

    private ProtonMessageHandler verifyResponseHandlerSet() {
        final ArgumentCaptor<ProtonMessageHandler> messageHandler = ArgumentCaptor.forClass(ProtonMessageHandler.class);
        verify(connection).createReceiver(anyString(), any(ProtonQoS.class), messageHandler.capture(), VertxMockSupport.anyHandler());
        return messageHandler.getValue();
    }

    /**
     * Verifies that the client fails the handler for sending a request message
     * with a 503 {@link ServerErrorException} if the link to the peer has no credit left.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testCreateAndSendRequestFailsIfSendQueueFull(final VertxTestContext ctx) {

        // GIVEN a request-response client with a full send queue
        when(sender.sendQueueFull()).thenReturn(Boolean.TRUE);

        // WHEN sending a request message
        client
            .compose(c -> c.createAndSendRequest(
                "get",
                null,
                Buffer.buffer("hello"),
                "text/plain",
                SimpleRequestResponseResult::from,
                span))
            .onComplete(ctx.failing(t -> {
                ctx.verify(() -> {
                    // THEN the message is not sent
                    verify(sender, never()).send(any(Message.class), VertxMockSupport.anyHandler());
                    // and the request result handler is failed with a 503
                    assertFailureCause(span, t, HttpURLConnection.HTTP_UNAVAILABLE);
                });
                ctx.completeNow();
            }));
    }

    /**
     * Verifies that the client creates and sends a message based on provided headers and payload
     * and sets a timer for canceling the request if no response is received.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testCreateAndSendRequestSendsProperRequestMessage(final VertxTestContext ctx) {

        // WHEN sending a request message with some headers and payload
        final JsonObject payload = new JsonObject().put("key", "value");
        final Map<String, Object> props = Map.of("test-key", "test-value");
        client
            .onComplete(ctx.succeeding(c -> {
                c.createAndSendRequest(
                        "get",
                        props,
                        payload.toBuffer(),
                        "application/json",
                        SimpleRequestResponseResult::from,
                        span);
            }));

        // THEN the message is sent and the message being sent contains the headers as application properties
        final Message request = verifySenderSend();
        assertThat(request).isNotNull();
        assertThat(request.getBody()).isNotNull();
        assertThat(request.getBody()).isInstanceOf(Data.class);
        final Buffer body = AmqpUtils.getPayload(request);
        assertThat(body.getBytes()).isEqualTo(payload.toBuffer().getBytes());
        assertThat(request.getApplicationProperties()).isNotNull();
        assertThat(request.getApplicationProperties().getValue().get("test-key")).isEqualTo("test-value");
        // and a timer has been set to time out the request
        verify(vertx).setTimer(eq(clientConfig.getRequestTimeout()), VertxMockSupport.anyHandler());
        ctx.completeNow();
    }

    /**
     * Verifies that the client configured with a zero request timeout sets no timer for
     * canceling the request in case of no received response.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testCreateAndSendRequestSetsNoTimerIfRequestTimeoutZero(final VertxTestContext ctx) {

        // WHEN configuring the client with a zero request timeout and sending a request message
        final JsonObject payload = new JsonObject().put("key", "value");
        client
            .map(c -> {
                c.setRequestTimeout(0);
                return c;
            })
            .onComplete(ctx.succeeding(c -> {
                c.createAndSendRequest(
                        "get",
                        null,
                        payload.toBuffer(),
                        "application/json",
                        SimpleRequestResponseResult::from,
                        span);
            }));

        // THEN the message is sent
        final Message request = verifySenderSend();
        assertThat(request).isNotNull();
        assertThat(request.getBody()).isNotNull();
        assertThat(request.getBody()).isInstanceOf(Data.class);
        final Buffer body = AmqpUtils.getPayload(request);
        assertThat(body.getBytes()).isEqualTo(payload.toBuffer().getBytes());
        // and no timer has been set to time out the request
        verify(vertx, never()).setTimer(anyLong(), VertxMockSupport.anyHandler());
        ctx.completeNow();
    }

    /**
     * Verifies that the sender fails with an 503 error code if the peer rejects
     * a message with an "amqp:resource-limit-exceeded" error.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testCreateAndSendRequestFailsForResourceLimitExceeded(final VertxTestContext ctx) {
        testCreateAndSendRequestFailsOnRejectedMessage(
                ctx,
                AmqpError.RESOURCE_LIMIT_EXCEEDED,
                t -> {
                    assertThat(t).isInstanceOf(ResourceLimitExceededException.class);
                    assertThat(((ResourceLimitExceededException) t).getClientFacingMessage()).isNotEmpty();
                });
    }

    /**
     * Verifies that the sender fails with an 400 error code if the peer rejects
     * a message with an arbitrary error condition.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testCreateAndSendRequestFailsForArbitraryError(final VertxTestContext ctx) {
        testCreateAndSendRequestFailsOnRejectedMessage(
                ctx,
                Symbol.getSymbol("arbitrary-error"),
                t -> {
                    assertThat(t).isInstanceOf(ClientErrorException.class);
                    assertThat(((ClientErrorException) t).getErrorCode())
                            .isEqualTo(HttpURLConnection.HTTP_BAD_REQUEST);
                });
    }

    private void testCreateAndSendRequestFailsOnRejectedMessage(
            final VertxTestContext ctx,
            final Symbol errorCondition,
            final Consumer<Throwable> failureAssertions) {

        // WHEN sending a request message with some headers and payload
        final JsonObject payload = new JsonObject().put("key", "value");
        client
            .compose(c -> c.createAndSendRequest(
                    "get",
                    null,
                    payload.toBuffer(),
                    "application/json",
                    SimpleRequestResponseResult::from,
                    span))
            .onComplete(ctx.failing(t -> {
                ctx.verify(() -> {
                    // THEN the result handler is failed with the expected error
                    failureAssertions.accept(t);
                    verify(sample).completed(isA(Rejected.class));
                    // and a timer has been set to time out the request
                    final ArgumentCaptor<Handler<Long>> timeoutHandlerCaptor = VertxMockSupport.argumentCaptorHandler();
                    verify(vertx).setTimer(eq(clientConfig.getRequestTimeout()), timeoutHandlerCaptor.capture());
                    // triggering the timer now that the request has been handled should not invoke the sampler timeout method
                    timeoutHandlerCaptor.getValue().handle(1L);
                    verify(sample, never()).timeout();
                });
                ctx.completeNow();
            }));

        // and the peer rejects the message
        final Rejected rejected = new Rejected();
        rejected.setError(ProtonHelper.condition(errorCondition, "request message cannot be processed"));
        final ProtonDelivery delivery = mock(ProtonDelivery.class);
        when(delivery.getRemoteState()).thenReturn(rejected);
        final ArgumentCaptor<Handler<ProtonDelivery>> dispositionHandlerCaptor = VertxMockSupport.argumentCaptorHandler();
        verify(sender).send(any(Message.class), dispositionHandlerCaptor.capture());
        dispositionHandlerCaptor.getValue().handle(delivery);
    }

    /**
     * Verifies that the client returns the service's response message that correlates with the request.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testCreateAndSendRequestReturnsCorrespondingResponseMessage(final VertxTestContext ctx) {

        // WHEN sending a request message to the peer
        client
            .compose(c -> c.createAndSendRequest(
                "request",
                null,
                Buffer.buffer("hello"),
                "text/plain",
                SimpleRequestResponseResult::from,
                span))
            .onComplete(ctx.succeeding(s -> {
                ctx.verify(() -> {
                    // THEN the response is passed to the handler registered with the request
                    assertEquals(200, s.getStatus());
                    assertEquals("payload", s.getPayload().toString());
                    verify(sample).completed(isA(Accepted.class));
                    // and a timer has been set to time out the request
                    final ArgumentCaptor<Handler<Long>> timeoutHandlerCaptor = VertxMockSupport.argumentCaptorHandler();
                    verify(vertx).setTimer(eq(clientConfig.getRequestTimeout()), timeoutHandlerCaptor.capture());
                    // triggering the timer now that the request has been handled should not invoke the sampler timeout method
                    timeoutHandlerCaptor.getValue().handle(1L);
                    verify(sample, never()).timeout();
                });
                ctx.completeNow();
            }));

        // WHEN a response is received for the request
        final Message request = verifySenderSendAndUpdateDelivery(new Accepted());
        final ProtonMessageHandler responseHandler = verifyResponseHandlerSet();
        final Message response = ProtonHelper.message("payload");
        response.setCorrelationId(request.getMessageId());
        AmqpUtils.addProperty(response, MessageHelper.APP_PROPERTY_STATUS, 200);
        final ProtonDelivery delivery = mock(ProtonDelivery.class);
        responseHandler.handle(delivery, response);
    }

    /**
     * Verifies that the client cancels and fails a request for which no response
     * has been received after a certain amount of time. The request is then
     * failed with a {@link ServerErrorException} with a 503 status code.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testCreateAndSendRequestReturnsFailedFutureIfRequestTimesOut(final VertxTestContext ctx) {

        // WHEN no response is received for a request sent to the peer
        VertxMockSupport.runTimersImmediately(vertx);

        client.compose(c -> c.createAndSendRequest(
                "request",
                null,
                Buffer.buffer("hello"),
                "text/plain",
                SimpleRequestResponseResult::from,
                span))
            .onComplete(ctx.failing(t -> {
                ctx.verify(() -> {
                    // THEN the request handler is failed
                    assertEquals(
                            HttpURLConnection.HTTP_UNAVAILABLE,
                            ((ServerErrorException) t).getErrorCode());
                    verify(span, never()).finish();
                    verify(sample, never()).completed(any(DeliveryState.class));
                    verify(sample).timeout();
                });
                ctx.completeNow();
            }));
    }

    /**
     * Verifies that a response handler is immediately failed with a
     * {@link ServerErrorException} when the sender link is not open (yet).
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testCreateAndSendRequestFailsIfSenderIsNotOpen(final VertxTestContext ctx) {

        // GIVEN a client whose sender is not open
        when(sender.isOpen()).thenReturn(Boolean.FALSE);

        // WHEN sending a request
        client.compose(c -> c.createAndSendRequest(
                "get",
                null,
                Buffer.buffer("hello"),
                "text/plain",
                SimpleRequestResponseResult::from,
                span))
            .onComplete(ctx.failing(t -> {
                ctx.verify(() -> {
                    // THEN the request fails immediately with a 503
                    verify(sender, never()).send(any(Message.class), VertxMockSupport.anyHandler());
                    assertFailureCause(span, t, HttpURLConnection.HTTP_UNAVAILABLE);
                });
                ctx.completeNow();
            }));
    }

    /**
     * Verifies that a response handler is immediately failed with a
     * {@link ServerErrorException} when the receiver link is not open (yet).
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testCreateAndSendRequestFailsIfReceiverIsNotOpen(final VertxTestContext ctx) {

        // GIVEN a client whose receiver is not open
        when(receiver.isOpen()).thenReturn(Boolean.FALSE);

        // WHEN sending a request
        client.compose(c -> c.createAndSendRequest(
                "get",
                null,
                Buffer.buffer("hello"),
                "text/plain",
                SimpleRequestResponseResult::from,
                span))
            .onComplete(ctx.failing(t -> {
                ctx.verify(() -> {
                    // THEN the request fails immediately with a 503
                    verify(sender, never()).send(any(Message.class), VertxMockSupport.anyHandler());
                    assertFailureCause(span, t, HttpURLConnection.HTTP_UNAVAILABLE);
                });
                ctx.completeNow();
            }));
    }
}
