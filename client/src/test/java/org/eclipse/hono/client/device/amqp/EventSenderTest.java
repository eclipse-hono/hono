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

package org.eclipse.hono.client.device.amqp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.eclipse.hono.client.AbstractAmqpAdapterClientDownstreamSenderTestBase;
import org.eclipse.hono.client.device.amqp.internal.AmqpAdapterClientEventSenderImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.opentracing.SpanContext;
import io.vertx.core.Future;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.proton.ProtonDelivery;

/**
 * Verifies behavior of {@link EventSender}.
 *
 */
@ExtendWith(VertxExtension.class)
public class EventSenderTest extends AbstractAmqpAdapterClientDownstreamSenderTestBase {

    private static final String ADDRESS = "event/" + TENANT_ID + "/" + DEVICE_ID;

    /**
     * Verifies that the message created by the client conforms to the expectations of the AMQP adapter.
     *
     * @param ctx The test context to use for running asynchronous tests.
     */
    @Test
    public void testSendCreatesValidMessage(final VertxTestContext ctx) {

        // GIVEN a EventSender instance
        final EventSender eventSender = createEventSender();

        // WHEN sending a message using the API...
        final Future<ProtonDelivery> deliveryFuture = eventSender.send(DEVICE_ID, PAYLOAD, CONTENT_TYPE,
                APPLICATION_PROPERTIES);

        // ...AND WHEN the disposition is updated by the peer
        updateDisposition();

        deliveryFuture.setHandler(ctx.succeeding(delivery -> {
            // THEN the AMQP message conforms to the expectations of the AMQP protocol adapter
            ctx.verify(() -> assertMessageConformsAmqpAdapterSpec(ADDRESS));
            ctx.completeNow();
        }));
    }

    /**
     * Verifies that {@link TraceableEventSender} uses the given SpanContext.
     *
     * @param ctx The test context to use for running asynchronous tests.
     */
    @Test
    public void testSendWithTracing(final VertxTestContext ctx) {

        // GIVEN a EventSender instance
        final TraceableEventSender eventSender = ((TraceableEventSender) createEventSender());

        // WHEN sending a message using the API...
        final SpanContext spanContext = mock(SpanContext.class);
        final Future<ProtonDelivery> deliveryFuture = eventSender.send(DEVICE_ID, PAYLOAD, CONTENT_TYPE,
                APPLICATION_PROPERTIES, spanContext);

        // ...AND WHEN the disposition is updated by the peer
        updateDisposition();

        deliveryFuture.setHandler(ctx.succeeding(delivery -> {
            // THEN the given SpanContext is used
            ctx.verify(() -> {
                verify(spanBuilder).addReference(any(), eq(spanContext));
                assertMessageConformsAmqpAdapterSpec(ADDRESS);
            });
            ctx.completeNow();
        }));
    }

    /**
     * Verifies that sending the message waits for the disposition update from the peer.
     *
     * @param ctx The test context to use for running asynchronous tests.
     * @throws InterruptedException if test is interrupted while waiting.
     */
    @Test
    public void testSendWaitsForDispositionUpdate(final VertxTestContext ctx)
            throws InterruptedException {

        // GIVEN a EventSender instance
        final EventSender eventSender = createEventSender();

        // WHEN sending a message using the API...
        final Future<ProtonDelivery> deliveryFuture = eventSender.send(DEVICE_ID, PAYLOAD, CONTENT_TYPE,
                APPLICATION_PROPERTIES);

        deliveryFuture.setHandler(ctx.completing());

        // THEN the future waits for the disposition to be updated by the peer
        Thread.sleep(100L);
        assertThat(deliveryFuture.isComplete()).isFalse();
        updateDisposition();
    }

    private EventSender createEventSender() {
        return AmqpAdapterClientEventSenderImpl.createWithAnonymousLinkAddress(connection, TENANT_ID, s -> {
                }).result();
    }

}
