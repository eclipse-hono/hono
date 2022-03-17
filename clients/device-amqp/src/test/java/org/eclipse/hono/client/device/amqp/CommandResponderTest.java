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

import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.device.amqp.impl.AmqpAdapterClientCommandResponseSender;
import org.eclipse.hono.util.CommandConstants;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.opentracing.SpanContext;
import io.vertx.core.Future;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.proton.ProtonDelivery;

/**
 * Verifies behavior of {@link CommandResponder}.
 *
 */
@ExtendWith(VertxExtension.class)
public class CommandResponderTest extends AmqpAdapterClientSenderTestBase {

    private static final String ADDRESS = String.format(
            "%s/%s/%s/123",
            CommandConstants.COMMAND_RESPONSE_ENDPOINT,
            TENANT_ID,
            DEVICE_ID);
    private static final String CORRELATION_ID = "0";
    private static final int STATUS = 200;

    /**
     * Verifies that the message created by the client conforms to the expectations of the AMQP adapter.
     *
     * @param ctx The test context to use for running asynchronous tests.
     */
    @Test
    public void testSendCommandResponseCreatesValidMessage(final VertxTestContext ctx) {

        // WHEN sending a message using the API...
        final Future<ProtonDelivery> deliveryFuture = createCommandResponder()
                .compose(responder -> responder.sendCommandResponse(DEVICE_ID, ADDRESS, CORRELATION_ID, STATUS,
                        PAYLOAD, CONTENT_TYPE));

        // ...AND WHEN the disposition is updated by the peer
        updateDisposition();

        deliveryFuture.onComplete(ctx.succeeding(delivery -> {
            // THEN the AMQP message conforms to the expectations of the AMQP protocol adapter
            ctx.verify(this::assertMessageConformsAmqpAdapterSpec);
            ctx.completeNow();
        }));
    }

    /**
     * Verifies that {@link TraceableCommandResponder} uses the given SpanContext.
     *
     * @param ctx The test context to use for running asynchronous tests.
     */
    @Test
    public void testSendCommandResponseWithTracing(final VertxTestContext ctx) {

        // WHEN sending a message using the API...
        final SpanContext spanContext = mock(SpanContext.class);
        final Future<ProtonDelivery> deliveryFuture = createCommandResponder()
                .compose(responder -> responder.sendCommandResponse(DEVICE_ID, ADDRESS, CORRELATION_ID, STATUS,
                        PAYLOAD, CONTENT_TYPE, spanContext));

        // ...AND WHEN the disposition is updated by the peer
        updateDisposition();

        deliveryFuture.onComplete(ctx.succeeding(delivery -> {
            // THEN the given SpanContext is used
            ctx.verify(() -> {
                verify(spanBuilder).addReference(any(), eq(spanContext));
                assertMessageConformsAmqpAdapterSpec();
            });
            ctx.completeNow();
        }));
    }

    /**
     * Verifies that sending the command response waits for the disposition update from the peer.
     *
     * @param ctx The test context to use for running asynchronous tests.
     */
    @Test
    public void testSendingWaitsForDispositionUpdate(final VertxTestContext ctx) {

        // WHEN sending a message using the API
        final Future<ProtonDelivery> deliveryFuture = createCommandResponder()
                .compose(responder -> responder.sendCommandResponse(DEVICE_ID, ADDRESS,
                CORRELATION_ID, STATUS, PAYLOAD, CONTENT_TYPE));

        // THEN the future waits for the disposition to be updated by the peer
        assertThat(deliveryFuture.isComplete()).isFalse();
        updateDisposition();

        deliveryFuture.onComplete(ctx.succeedingThenComplete());
    }

    private Future<TraceableCommandResponder> createCommandResponder() {
        return AmqpAdapterClientCommandResponseSender.create(
                connection,
                TENANT_ID,
                s -> {});
    }

    private void assertMessageConformsAmqpAdapterSpec() {
        final Message message = assertMessageConformsAmqpAdapterSpec(ADDRESS.toString());
        assertThat(message.getCorrelationId()).isEqualTo(CORRELATION_ID);
        assertThat(message.getApplicationProperties().getValue().get("status")).isEqualTo(STATUS);
    }
}
