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

package org.eclipse.hono.client.device.amqp.impl;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import static com.google.common.truth.Truth.assertThat;

import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.device.amqp.AmqpAdapterClientTestBase;
import org.eclipse.hono.client.device.amqp.impl.ProtonBasedAmqpAdapterClient;
import org.eclipse.hono.util.CommandConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import io.opentracing.SpanContext;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

/**
 * Verifies behavior of {@link ProtonBasedAmqpAdapterClient}'s functionality for sending command response messages.
 *
 */
@ExtendWith(VertxExtension.class)
public class CommandResponderTest extends AmqpAdapterClientTestBase {

    private static final String ADDRESS = "%s/%s/123".formatted(
            CommandConstants.COMMAND_RESPONSE_ENDPOINT,
            TENANT_ID);
    private static final String CORRELATION_ID = "0";
    private static final int STATUS = 200;

    private ProtonBasedAmqpAdapterClient client;

    /**
     * Creates the client.
     */
    @BeforeEach
    public void createClient() {

        client = new ProtonBasedAmqpAdapterClient(connection);
    }

    private void assertMessageConformsAmqpAdapterSpec() {
        final Message message = assertMessageConformsAmqpAdapterSpec(ADDRESS.toString());
        assertThat(message.getCorrelationId()).isEqualTo(CORRELATION_ID);
        assertThat(message.getApplicationProperties().getValue().get("status")).isEqualTo(STATUS);
    }

    /**
     * Verifies that a command response message sent by the client conforms to the expectations of the AMQP adapter.
     *
     * @param useSpanContext {@code true} if the sending should be tracked.
     * @param ctx The test context to use for running asynchronous tests.
     */
    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    public void testSendCommandResponseCreatesValidMessage(
            final boolean useSpanContext,
            final VertxTestContext ctx) {

        final var spanContext = mock(SpanContext.class);
        // WHEN sending a command response using the API
        final var result = client.sendCommandResponse(
                ADDRESS,
                CORRELATION_ID,
                STATUS,
                PAYLOAD,
                CONTENT_TYPE,
                useSpanContext ? spanContext : null);

        // THEN the future waits for the disposition to be updated by the peer
        assertThat(result.isComplete()).isFalse();
        // ...AND WHEN the disposition is updated by the peer
        updateDisposition();

        result.onComplete(ctx.succeeding(ok -> {
                // THEN the AMQP message conforms to the expectations of the AMQP protocol adapter
                ctx.verify(() -> {
                    assertMessageConformsAmqpAdapterSpec();
                    if (useSpanContext) {
                        // and the given SpanContext is used
                        verify(spanBuilder).addReference(any(), eq(spanContext));
                    } else {
                        verify(spanBuilder, never()).addReference(any(), any(SpanContext.class));
                    }
                });

                ctx.completeNow();
            }));
    }
}
