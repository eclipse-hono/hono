/*******************************************************************************
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.Map;

import org.apache.qpid.proton.amqp.messaging.Accepted;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.impl.HonoClientUnitTestHelper;
import org.eclipse.hono.client.impl.VertxMockSupport;
import org.eclipse.hono.util.MessageHelper;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.ArgumentCaptor;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.proton.ProtonDelivery;
import io.vertx.proton.ProtonSender;

/**
 * Base class for tests of the downstream senders of the AMQP Adapter client.
 *
 */
public abstract class AbstractAmqpAdapterClientDownstreamSenderTestBase {

    protected static final String TENANT_ID = "test-tenant";
    protected static final String DEVICE_ID = "test-device";
    protected static final String CONTENT_TYPE = "text/plain";
    protected static final byte[] PAYLOAD = "test-value".getBytes();
    protected static final String TEST_PROPERTY_KEY = "test-key";
    protected static final String TEST_PROPERTY_VALUE = "test-value";
    protected static final Map<String, String> APPLICATION_PROPERTIES = Collections.singletonMap(TEST_PROPERTY_KEY,
            TEST_PROPERTY_VALUE);

    protected ProtonSender sender;
    protected HonoConnection connection;
    protected ProtonDelivery protonDelivery;
    protected Tracer.SpanBuilder spanBuilder;

    /**
     * Sets up fixture.
     */
    @BeforeEach
    public void setUp() {
        sender = HonoClientUnitTestHelper.mockProtonSender();

        protonDelivery = mock(ProtonDelivery.class);
        when(protonDelivery.remotelySettled()).thenReturn(true);
        final Accepted deliveryState = new Accepted();
        when(protonDelivery.getRemoteState()).thenReturn(deliveryState);

        when(sender.send(any(Message.class), VertxMockSupport.anyHandler())).thenReturn(protonDelivery);

        final Span span = mock(Span.class);
        when(span.context()).thenReturn(mock(SpanContext.class));
        spanBuilder = HonoClientUnitTestHelper.mockSpanBuilder(span);

        final Tracer tracer = mock(Tracer.class);
        when(tracer.buildSpan(anyString())).thenReturn(spanBuilder);

        connection = HonoClientUnitTestHelper.mockHonoConnection(mock(Vertx.class));

        when(connection.getTracer()).thenReturn(tracer);
        when(connection.createSender(any(), any(), any())).thenReturn(Future.succeededFuture(sender));

    }

    /**
     * Updates the disposition for the {@link ProtonSender#send(Message, Handler)} operation.
     */
    @SuppressWarnings("unchecked")
    protected void updateDisposition() {
        final ArgumentCaptor<Handler<ProtonDelivery>> dispositionHandlerCaptor = ArgumentCaptor.forClass(Handler.class);
        verify(sender).send(any(Message.class), dispositionHandlerCaptor.capture());
        dispositionHandlerCaptor.getValue().handle(protonDelivery);
    }

    /**
     * Executes the assertions that check that the message created by the client conforms to the expectations of the
     * AMQP adapter.
     *
     * @param expectedAddress The expected target address.
     * @return The captured message.
     */
    protected Message assertMessageConformsAmqpAdapterSpec(final String expectedAddress) {

        final ArgumentCaptor<Message> messageArgumentCaptor = ArgumentCaptor.forClass(Message.class);
        verify(sender).send(messageArgumentCaptor.capture(), any());

        final Message message = messageArgumentCaptor.getValue();

        assertThat(message.getAddress()).isEqualTo(expectedAddress);

        assertThat(MessageHelper.getPayloadAsString(message)).isEqualTo(new String(PAYLOAD));
        assertThat(message.getContentType()).isEqualTo(CONTENT_TYPE);

        assertThat(MessageHelper.getApplicationProperty(message.getApplicationProperties(), TEST_PROPERTY_KEY,
                String.class)).isEqualTo(TEST_PROPERTY_VALUE);

        assertThat(MessageHelper.getDeviceId(message)).isEqualTo(DEVICE_ID);

        return message;
    }

}
