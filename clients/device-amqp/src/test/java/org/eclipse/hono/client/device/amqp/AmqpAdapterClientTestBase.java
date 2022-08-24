/*******************************************************************************
 * Copyright (c) 2020, 2022 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.client.device.amqp;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static com.google.common.truth.Truth.assertThat;

import org.apache.qpid.proton.amqp.messaging.Accepted;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.amqp.connection.AmqpUtils;
import org.eclipse.hono.client.amqp.connection.HonoConnection;
import org.eclipse.hono.client.amqp.test.AmqpClientUnitTestHelper;
import org.eclipse.hono.test.TracingMockSupport;
import org.eclipse.hono.test.VertxMockSupport;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.ArgumentCaptor;

import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.Tracer.SpanBuilder;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.proton.ProtonDelivery;
import io.vertx.proton.ProtonSender;

/**
 * Base class for tests of the downstream senders of the AMQP Adapter client.
 *
 */
public abstract class AmqpAdapterClientTestBase {

    protected static final String TENANT_ID = "test-tenant";
    protected static final String DEVICE_ID = "test-device";
    protected static final String TEST_PROPERTY_KEY = "test-key";
    protected static final String TEST_PROPERTY_VALUE = "test-value";

    /**
     * The connection to the adapter.
     */
    protected HonoConnection connection;
    /**
     * The anonymous sender link.
     */
    protected ProtonSender sender;
    /**
     * The disposition update for the current transfer.
     */
    protected ProtonDelivery protonDelivery;
    /**
     * The builder for creating new spans.
     */
    protected SpanBuilder spanBuilder;

    /**
     * Sets up fixture.
     */
    @BeforeEach
    public void setUp() {
        sender = AmqpClientUnitTestHelper.mockProtonSender();

        protonDelivery = mock(ProtonDelivery.class);
        when(protonDelivery.remotelySettled()).thenReturn(true);
        when(protonDelivery.getRemoteState()).thenReturn(new Accepted());

        when(sender.send(any(Message.class), VertxMockSupport.anyHandler())).thenReturn(protonDelivery);

        final Span span = TracingMockSupport.mockSpan();
        spanBuilder = TracingMockSupport.mockSpanBuilder(span);
        final Tracer tracer = TracingMockSupport.mockTracer(spanBuilder);

        connection = AmqpClientUnitTestHelper.mockHonoConnection(mock(Vertx.class));

        when(connection.getTracer()).thenReturn(tracer);
        when(connection.createSender(any(), any(), any())).thenReturn(Future.succeededFuture(sender));
        when(connection.isConnected(anyLong())).thenReturn(Future.succeededFuture());
    }

    /**
     * Updates the disposition for the {@link ProtonSender#send(Message, Handler)} operation.
     */
    protected void updateDisposition() {
        final ArgumentCaptor<Handler<ProtonDelivery>> dispositionHandlerCaptor = VertxMockSupport.argumentCaptorHandler();
        verify(sender).send(any(Message.class), dispositionHandlerCaptor.capture());
        dispositionHandlerCaptor.getValue().handle(protonDelivery);
    }

    /**
     * Executes the assertions that check that the message created by the client conforms to the expectations of the
     * AMQP adapter.
     *
     * @param expectedAddress The target address expected to be found in the message that has been sent.
     * @param expectedContentType The content type expected to be found in the message that has been sent.
     * @param expectedPayload The payload expected to be found in the message that has been sent.
     * @return The captured message.
     */
    protected Message assertMessageConformsToAmqpAdapterSpec(
            final String expectedAddress,
            final String expectedContentType,
            final Buffer expectedPayload) {

        final ArgumentCaptor<Message> messageArgumentCaptor = ArgumentCaptor.forClass(Message.class);

        verify(sender).send(messageArgumentCaptor.capture(), any());
        final Message message = messageArgumentCaptor.getValue();

        assertAll(() -> assertThat(message.getAddress()).isEqualTo(expectedAddress),
                () -> {
                    if (expectedContentType != null) {
                        assertThat(message.getContentType()).isEqualTo(expectedContentType);
                    } else {
                        assertThat(message.getContentType()).isNull();
                    }
                },
                () -> {
                    if (expectedPayload != null) {
                        assertThat(AmqpUtils.getPayload(message)).isEqualTo(expectedPayload);
                    } else {
                        assertThat(AmqpUtils.getPayload(message)).isNull();
                    }
                });

        return message;
    }
}
