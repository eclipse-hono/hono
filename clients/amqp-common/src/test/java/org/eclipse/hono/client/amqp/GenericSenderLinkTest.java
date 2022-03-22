/*******************************************************************************
 * Copyright (c) 2016, 2021 Contributors to the Eclipse Foundation
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static com.google.common.truth.Truth.assertThat;

import java.net.HttpURLConnection;
import java.util.function.Consumer;

import org.apache.qpid.proton.amqp.Symbol;
import org.apache.qpid.proton.amqp.messaging.Accepted;
import org.apache.qpid.proton.amqp.messaging.Rejected;
import org.apache.qpid.proton.amqp.transport.AmqpError;
import org.apache.qpid.proton.amqp.transport.ErrorCondition;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.ResourceLimitExceededException;
import org.eclipse.hono.client.amqp.config.ClientConfigProperties;
import org.eclipse.hono.client.amqp.connection.AmqpUtils;
import org.eclipse.hono.client.amqp.connection.HonoConnection;
import org.eclipse.hono.client.amqp.connection.SendMessageSampler;
import org.eclipse.hono.client.amqp.test.AmqpClientUnitTestHelper;
import org.eclipse.hono.test.VertxMockSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import io.opentracing.Span;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.proton.ProtonDelivery;
import io.vertx.proton.ProtonHelper;
import io.vertx.proton.ProtonSender;

/**
 * Tests verifying behavior of {@link GenericSenderLink}.
 *
 */
public class GenericSenderLinkTest {

    private Vertx vertx;
    private ProtonSender sender;
    private ClientConfigProperties config;
    private HonoConnection connection;
    private GenericSenderLink messageSender;

    /**
     * Sets up the fixture.
     */
    @BeforeEach
    public void setUp() {

        vertx = mock(Vertx.class);
        sender = AmqpClientUnitTestHelper.mockProtonSender();
        config = new ClientConfigProperties();
        connection = AmqpClientUnitTestHelper.mockHonoConnection(vertx, config);
        messageSender = new GenericSenderLink(connection, sender, "tenant", "telemetry/tenant", SendMessageSampler.noop());
    }

    /**
     * Verifies that the sender does not wait for the peer to settle and
     * accept a message before succeeding.
     */
    @Test
    public void testSendSucceedsForRejectedOutcome() {

        // GIVEN a sender that has credit
        when(sender.sendQueueFull()).thenReturn(Boolean.FALSE);

        // WHEN trying to send a message
        final Span span = mock(Span.class);
        final Message message = ProtonHelper.message("some payload");
        message.setContentType("text/plain");
        AmqpUtils.addDeviceId(message, "device");
        final Future<ProtonDelivery> result = messageSender.send(message, span);

        // which gets rejected by the peer
        final ArgumentCaptor<Handler<ProtonDelivery>> deliveryUpdateHandler = VertxMockSupport.argumentCaptorHandler();
        verify(sender).send(any(Message.class), deliveryUpdateHandler.capture());
        final ProtonDelivery rejected = mock(ProtonDelivery.class);
        when(rejected.remotelySettled()).thenReturn(Boolean.TRUE);
        when(rejected.getRemoteState()).thenReturn(new Rejected());
        deliveryUpdateHandler.getValue().handle(rejected);

        // THEN the resulting future is succeeded nevertheless
        assertThat(result.succeeded()).isTrue();
        // and the span has been finished
        verify(span).finish();
    }

    /**
     * Verifies that sending a message with AT_MOST_ONCE QoS fails if no credit is available.
     */
    @Test
    public void testSendFailsOnLackOfCredit() {

        // GIVEN a sender that has credit
        when(sender.sendQueueFull()).thenReturn(Boolean.TRUE);

        // WHEN trying to send a message
        final Span span = mock(Span.class);
        final Message msg = ProtonHelper.message("telemetry/tenant", "hello");
        final Future<ProtonDelivery> result = messageSender.send(msg, span);

        // THEN the message is not sent
        assertThat(result.failed()).isTrue();
        verify(sender, never()).send(any(Message.class), VertxMockSupport.anyHandler());
        // but the span has been finished
        verify(span).finish();
    }

    /**
     * Verifies that the sender waits for the peer to settle and
     * accept a message before succeeding the returned future.
     */
    @Test
    public void testSendAndWaitForOutcomeWaitsForAcceptedOutcome() {

        // GIVEN a sender that has credit
        when(sender.sendQueueFull()).thenReturn(Boolean.FALSE);

        // WHEN trying to send a message
        final Span span = mock(Span.class);
        final Message message = ProtonHelper.message("some payload");
        message.setContentType("text/plain");
        AmqpUtils.addDeviceId(message, "device");
        final Future<ProtonDelivery> result = messageSender.sendAndWaitForOutcome(message, span);

        // THEN the message has been sent
        final ArgumentCaptor<Handler<ProtonDelivery>> deliveryUpdateHandler = VertxMockSupport.argumentCaptorHandler();
        verify(sender).send(any(Message.class), deliveryUpdateHandler.capture());
        // but the result is not completed
        assertThat(result.isComplete()).isFalse();

        // until the message gets accepted by the peer
        final ProtonDelivery accepted = mock(ProtonDelivery.class);
        when(accepted.remotelySettled()).thenReturn(Boolean.TRUE);
        when(accepted.getRemoteState()).thenReturn(new Accepted());
        deliveryUpdateHandler.getValue().handle(accepted);

        assertThat(result.succeeded()).isTrue();
    }

    /**
     * Verifies that the sender fails with an 503 error code if the peer rejects
     * a message with an "amqp:resource-limit-exceeded" error.
     */
    @Test
    public void testSendAndWaitForOutcomeFailsForResourceLimitExceeded() {
        testSendAndWaitForOutcomeFailsForRejectedOutcome(
                AmqpError.RESOURCE_LIMIT_EXCEEDED,
                t -> {
                    assertThat(t).isInstanceOf(ResourceLimitExceededException.class);
                    assertThat(((ResourceLimitExceededException) t).getClientFacingMessage()).isNotEmpty();
                });
    }

    /**
     * Verifies that the sender fails with an 400 error code if the peer rejects
     * a message with an arbitrary error condition.
     */
    @Test
    public void testSendAndWaitForOutcomeFailsForArbitraryError() {
        testSendAndWaitForOutcomeFailsForRejectedOutcome(
                Symbol.getSymbol("arbitrary-error"),
                t -> {
                    assertThat(t).isInstanceOf(ClientErrorException.class);
                    assertThat(((ClientErrorException) t).getErrorCode())
                            .isEqualTo(HttpURLConnection.HTTP_BAD_REQUEST);
                });
    }

    private void testSendAndWaitForOutcomeFailsForRejectedOutcome(
            final Symbol errorCondition,
            final Consumer<Throwable> failureAssertions) {

        // GIVEN a sender that has credit
        when(sender.sendQueueFull()).thenReturn(Boolean.FALSE);

        // WHEN trying to send a message
        final Span span = mock(Span.class);
        final Message message = ProtonHelper.message("some payload");
        message.setContentType("text/plain");
        AmqpUtils.addDeviceId(message, "device");
        final Future<ProtonDelivery> result = messageSender.sendAndWaitForOutcome(message, span);

        // THEN the message has been sent
        final ArgumentCaptor<Handler<ProtonDelivery>> deliveryUpdateHandler = VertxMockSupport.argumentCaptorHandler();
        verify(sender).send(any(Message.class), deliveryUpdateHandler.capture());
        // but the request is not completed
        assertThat(result.isComplete()).isFalse();

        // and when the peer rejects the message
        final var condition = new ErrorCondition();
        condition.setCondition(errorCondition);
        final var error = new Rejected();
        error.setError(condition);
        final ProtonDelivery rejected = mock(ProtonDelivery.class);
        when(rejected.remotelySettled()).thenReturn(Boolean.TRUE);
        when(rejected.getRemoteState()).thenReturn(error);
        deliveryUpdateHandler.getValue().handle(rejected);

        // the request is failed
        assertThat(result.failed()).isTrue();
     // with the expected error
        failureAssertions.accept(result.cause());
    }

    /**
     * Verifies that the sender succeeds if the peer rejects a message and no mapping of the
     * delivery output should be done.
     */
    @Test
    public void testSendAndWaitForRawOutcomeSucceedsForRejectedOutcome() {

        // GIVEN a sender that has credit
        when(sender.sendQueueFull()).thenReturn(Boolean.FALSE);

        // WHEN trying to send a message
        final Span span = mock(Span.class);
        final Message message = ProtonHelper.message("some payload");
        message.setContentType("text/plain");
        AmqpUtils.addDeviceId(message, "device");
        final Future<ProtonDelivery> result = messageSender.sendAndWaitForRawOutcome(message, span);

        // THEN the message has been sent
        final ArgumentCaptor<Handler<ProtonDelivery>> deliveryUpdateHandler = VertxMockSupport.argumentCaptorHandler();
        verify(sender).send(any(Message.class), deliveryUpdateHandler.capture());
        // but the request is not completed
        assertThat(result.isComplete()).isFalse();

        // and when the peer rejects the message
        final ProtonDelivery rejected = mock(ProtonDelivery.class);
        when(rejected.remotelySettled()).thenReturn(Boolean.TRUE);
        when(rejected.getRemoteState()).thenReturn(new Rejected());
        deliveryUpdateHandler.getValue().handle(rejected);

        // the request is succeeded
        assertThat(result.succeeded()).isTrue();
    }


    /**
     * Verifies that the sender fails if no credit is available.
     */
    @Test
    public void testSendAndWaitForOutcomeFailsOnLackOfCredit() {

        // GIVEN a sender that has credit
        when(sender.sendQueueFull()).thenReturn(Boolean.TRUE);

        // WHEN trying to send a message
        final Span span = mock(Span.class);
        final Message msg = ProtonHelper.message("telemetry/tenant", "hello");
        final Future<ProtonDelivery> result = messageSender.sendAndWaitForOutcome(msg, span);

        // THEN the message is not sent
        assertThat(result.failed()).isTrue();
        verify(sender, never()).send(any(Message.class), VertxMockSupport.anyHandler());
        // but the span has been finished
        verify(span).finish();
    }

    /**
     * Verifies that a timeout occurring while a message is sent doesn't cause the corresponding 
     * OpenTracing span to stay unfinished.
     */
    @Test
    public void testSendFinishesSpanOnTimeout() {

        // GIVEN a sender that won't receive a delivery update on sending a message 
        // and directly triggers the timeout handler
        when(sender.send(any(Message.class), VertxMockSupport.anyHandler())).thenReturn(mock(ProtonDelivery.class));
        VertxMockSupport.runTimersImmediately(vertx);

        // WHEN sending a message
        final Message message = mock(Message.class);
        final Span span = mock(Span.class);
        messageSender.send(message, span);

        // THEN the given Span will nonetheless be finished.
        verify(span).finish();
    }
}
