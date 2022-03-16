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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import static com.google.common.truth.Truth.assertThat;

import org.eclipse.hono.client.device.amqp.impl.AmqpAdapterClientTelemetrySenderImpl;
import org.eclipse.hono.util.ResourceIdentifier;
import org.eclipse.hono.util.TelemetryConstants;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.opentracing.SpanContext;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.proton.ProtonDelivery;

/**
 * Verifies behavior of {@link TelemetrySender}.
 *
 */
@ExtendWith(VertxExtension.class)
public class TelemetrySenderTest extends AmqpAdapterClientSenderTestBase {

    private static final ResourceIdentifier ADDRESS = ResourceIdentifier.from(
            TelemetryConstants.TELEMETRY_ENDPOINT,
            TENANT_ID,
            DEVICE_ID);

    /**
     * Verifies that the message created by {@link TelemetrySender#send(String, Buffer, String, java.util.Map)} conforms to the
     * expectations of the AMQP adapter.
     *
     * @param ctx The test context to use for running asynchronous tests.
     */
    @Test
    public void testSendCreatesValidMessage(final VertxTestContext ctx) {

        // WHEN sending a message using the API
        createTelemetrySender()
            .compose(sender -> sender.send(DEVICE_ID, PAYLOAD, CONTENT_TYPE, APPLICATION_PROPERTIES))
            .onComplete(ctx.succeeding(ok -> {
                // THEN the AMQP message conforms to the expectations of the AMQP protocol adapter
                ctx.verify(() -> assertMessageConformsAmqpAdapterSpec(ADDRESS.toString()));
                ctx.completeNow();
            }));
    }

    /**
     * Verifies that the message created by {@link TelemetrySender#sendAndWaitForOutcome(String, Buffer, String, java.util.Map)}
     * conforms to the expectations of the AMQP adapter.
     *
     * @param ctx The test context to use for running asynchronous tests.
     */
    @Test
    public void testSendAndWaitForOutcomeCreatesValidMessage(final VertxTestContext ctx) {

        // WHEN sending a message using the API...
        final Future<ProtonDelivery> deliveryFuture = createTelemetrySender()
                .compose(sender -> sender.sendAndWaitForOutcome(DEVICE_ID, PAYLOAD,
                CONTENT_TYPE, APPLICATION_PROPERTIES));

        // ...AND WHEN the disposition is updated by the peer
        updateDisposition();

        deliveryFuture.onComplete(ctx.succeeding(delivery -> {
            // THEN the AMQP message conforms to the expectations of the AMQP protocol adapter
            ctx.verify(() -> assertMessageConformsAmqpAdapterSpec(ADDRESS.toString()));
            ctx.completeNow();
        }));
    }

    /**
     * Verifies that the message created by {@link TelemetrySender#send(String, Buffer, String, java.util.Map)} conforms
     * to the expectations of the AMQP adapter.
     *
     * @param ctx The test context to use for running asynchronous tests.
     */
    @Test
    public void testSendWithTracing(final VertxTestContext ctx) {

        // WHEN sending a message using the API
        final SpanContext spanContext = mock(SpanContext.class);
        createTelemetrySender()
            .compose(sender -> sender.send(DEVICE_ID, PAYLOAD, CONTENT_TYPE, APPLICATION_PROPERTIES, spanContext))
            .onComplete(ctx.succeeding(ok -> {
                ctx.verify(() -> {
                    // THEN the given SpanContext is used
                    verify(spanBuilder).addReference(any(), eq(spanContext));
                    assertMessageConformsAmqpAdapterSpec(ADDRESS.toString());
                });
                ctx.completeNow();
            }));

    }

    /**
     * Verifies that {@link TraceableTelemetrySender#sendAndWaitForOutcome(String, Buffer, String, java.util.Map, SpanContext)}
     * uses the given SpanContext.
     *
     * @param ctx The test context to use for running asynchronous tests.
     */
    @Test
    public void testSendAndWaitForOutcomeWithTracing(final VertxTestContext ctx) {

        // WHEN sending a message using the API...
        final SpanContext spanContext = mock(SpanContext.class);
        final Future<ProtonDelivery> deliveryFuture = createTelemetrySender()
                .compose(sender -> sender.sendAndWaitForOutcome(
                        DEVICE_ID,
                        PAYLOAD,
                        CONTENT_TYPE,
                        APPLICATION_PROPERTIES,
                        spanContext));

        // ...AND WHEN the disposition is updated by the peer
        updateDisposition();

        deliveryFuture.onComplete(ctx.succeeding(delivery -> {
            // THEN the given SpanContext is used
            ctx.verify(() -> {
                verify(spanBuilder).addReference(any(), eq(spanContext));
                assertMessageConformsAmqpAdapterSpec(ADDRESS.toString());
            });
            ctx.completeNow();
        }));
    }

    /**
     * Verifies that sending the message waits for the disposition update from the peer.
     *
     * @param ctx The test context to use for running asynchronous tests.
     */
    @Test
    public void testSendAndWaitForOutcomeWaitsForDispositionUpdate(final VertxTestContext ctx) {

        // WHEN sending a message using the API
        final Future<ProtonDelivery> deliveryFuture = createTelemetrySender()
                .compose(sender -> sender.sendAndWaitForOutcome(DEVICE_ID, PAYLOAD, CONTENT_TYPE, APPLICATION_PROPERTIES));

        // THEN the future waits for the disposition to be updated by the peer
        assertThat(deliveryFuture.isComplete()).isFalse();
        updateDisposition();

        deliveryFuture.onComplete(ctx.succeedingThenComplete());
    }

    private Future<TraceableTelemetrySender> createTelemetrySender() {
        return AmqpAdapterClientTelemetrySenderImpl.create(
                connection,
                TENANT_ID, s -> {});
    }
}
