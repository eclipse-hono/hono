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

import java.util.Optional;

import org.eclipse.hono.client.device.amqp.AmqpAdapterClientTestBase;
import org.eclipse.hono.util.CommandConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import io.opentracing.SpanContext;
import io.vertx.core.buffer.Buffer;
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

    /**
     * Verifies that a command response message sent by the client conforms to the expectations of the AMQP adapter.
     *
     * @param payload The payload to put in the message body.
     * @param contentType The value to set as the message's content-type.
     * @param useSpanContext {@code true} if the sending should be tracked.
     * @param ctx The test context to use for running asynchronous tests.
     */
    @ParameterizedTest
    @CsvSource(value = {
        "the-payload,custom/content,true",
        "the-payload,,false",
        ",custom/content,true",
        ",,false",
    })
    public void testSendCommandResponseCreatesValidMessage(
            final String payload,
            final String contentType,
            final boolean useSpanContext,
            final VertxTestContext ctx) {

        final var spanContext = mock(SpanContext.class);
        final var expectedBody = Optional.ofNullable(payload).map(Buffer::buffer).orElse(null);
        // WHEN sending a command response using the API
        final var result = client.sendCommandResponse(
                ADDRESS,
                CORRELATION_ID,
                STATUS,
                expectedBody,
                contentType,
                useSpanContext ? spanContext : null);

        // THEN the future waits for the disposition to be updated by the peer
        assertThat(result.isComplete()).isFalse();
        // ...AND WHEN the disposition is updated by the peer
        updateDisposition();

        result.onComplete(ctx.succeeding(ok -> {
                // THEN the AMQP message conforms to the expectations of the AMQP protocol adapter
                ctx.verify(() -> {
                    final var message = assertMessageConformsToAmqpAdapterSpec(ADDRESS.toString(), contentType, expectedBody);
                    assertThat(message.getCorrelationId()).isEqualTo(CORRELATION_ID);
                    assertThat(message.getApplicationProperties().getValue().get("status")).isEqualTo(STATUS);
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
