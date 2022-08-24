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

import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import static com.google.common.truth.Truth.assertThat;

import java.util.Optional;

import org.eclipse.hono.client.device.amqp.AmqpAdapterClientTestBase;
import org.eclipse.hono.util.EventConstants;
import org.eclipse.hono.util.QoS;
import org.eclipse.hono.util.ResourceIdentifier;
import org.eclipse.hono.util.TelemetryConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import io.opentracing.SpanContext;
import io.vertx.core.buffer.Buffer;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

/**
 * Verifies behavior of {@link ProtonBasedAmqpAdapterClient}'s functionality for sending telemetry messages.
 *
 */
@ExtendWith(VertxExtension.class)
public class TelemetrySenderTest extends AmqpAdapterClientTestBase {

    private ProtonBasedAmqpAdapterClient client;

    /**
     * Creates the client.
     */
    @BeforeEach
    public void createClient() {

        client = new ProtonBasedAmqpAdapterClient(connection);
    }

    /**
     * Verifies that a telemetry message sent by the client conforms to the expectations of the AMQP adapter.
     *
     * @param qos The delivery semantics.
     * @param payload The payload to put in the message body.
     * @param contentType The value to set as the message's content-type.
     * @param tenantId The tenant that the device belongs to.
     * @param deviceId The identifier of the device.
     * @param useSpanContext {@code true} if the sending should be tracked.
     * @param ctx The test context to use for running asynchronous tests.
     */
    @ParameterizedTest
    @CsvSource(value = {
        "AT_MOST_ONCE,the-payload,,,my-device,true",
        "AT_MOST_ONCE,,custom/content,my-tenant,my-device,false",
        "AT_LEAST_ONCE,the-payload,custom/content,,my-device,false",
        "AT_LEAST_ONCE,,,my-tenant,my-device,true",
    })
    public void testSendTelemetryCreatesValidMessage(
            final QoS qos,
            final String payload,
            final String contentType,
            final String tenantId,
            final String deviceId,
            final boolean useSpanContext,
            final VertxTestContext ctx) {

        final var spanContext = mock(SpanContext.class);
        final var expectedBody = Optional.ofNullable(payload).map(Buffer::buffer).orElse(null);

        // WHEN sending a message using the API
        final var result = client.sendTelemetry(
                qos,
                expectedBody,
                contentType,
                tenantId,
                deviceId,
                useSpanContext ? spanContext : null);

        if (qos == QoS.AT_LEAST_ONCE) {
            // THEN the future waits for the disposition to be updated by the peer
            assertThat(result.isComplete()).isFalse();
            // ...AND WHEN the disposition is updated by the peer
            updateDisposition();
        }

        result.onComplete(ctx.succeeding(ok -> {
                // THEN the AMQP message conforms to the expectations of the AMQP protocol adapter
                ctx.verify(() -> {
                    assertMessageConformsToAmqpAdapterSpec(
                            ResourceIdentifier.fromPath(TelemetryConstants.TELEMETRY_ENDPOINT, tenantId, deviceId).toString(),
                            contentType,
                            expectedBody);
                    if (useSpanContext) {
                        // and the given SpanContext is used
                        verify(spanBuilder).addReference(any(), eq(spanContext));
                    }
                });

                ctx.completeNow();
            }));
    }

    @Test
    void testSendTelemetryRejectsInvalidDeviceSpec() {
        assertThrowsExactly(
                IllegalArgumentException.class,
                () -> client.sendTelemetry(QoS.AT_MOST_ONCE, Buffer.buffer("test"), null, "my-tenant", null, null));
    }

    /**
     * Verifies that an event message sent by the client conforms to the expectations of the AMQP adapter.
     *
     * @param payload The payload to put in the message body.
     * @param contentType The value to set as the message's content-type.
     * @param tenantId The tenant that the device belongs to.
     * @param deviceId The identifier of the device.
     * @param useSpanContext {@code true} if the sending should be tracked.
     * @param ctx The test context to use for running asynchronous tests.
     */
    @ParameterizedTest
    @CsvSource(value = {
        "the-payload,custom/content,,,true",
        ",,,other-device,false",
        "the-payload,,my-tenant,my-device,true"
    })
    public void testSendEventCreatesValidMessage(
            final String payload,
            final String contentType,
            final String tenantId,
            final String deviceId,
            final boolean useSpanContext,
            final VertxTestContext ctx) {

        final var spanContext = mock(SpanContext.class);
        final var expectedBody = Optional.ofNullable(payload).map(Buffer::buffer).orElse(null);
        // WHEN sending a message using the API
        final var result = client.sendEvent(
                expectedBody,
                contentType,
                tenantId,
                deviceId,
                useSpanContext ? spanContext : null);

        // THEN the future waits for the disposition to be updated by the peer
        assertThat(result.isComplete()).isFalse();
        // ...AND WHEN the disposition is updated by the peer
        updateDisposition();

        result.onComplete(ctx.succeeding(ok -> {
                // THEN the AMQP message conforms to the expectations of the AMQP protocol adapter
                ctx.verify(() -> {
                    assertMessageConformsToAmqpAdapterSpec(
                            ResourceIdentifier.fromPath(EventConstants.EVENT_ENDPOINT, tenantId, deviceId).toString(),
                            contentType,
                            expectedBody);
                    if (useSpanContext) {
                        // and the given SpanContext is used
                        verify(spanBuilder).addReference(any(), eq(spanContext));
                    }
                });

                ctx.completeNow();
            }));
    }

    @Test
    void testSendEventRejectsInvalidDeviceSpec() {
        assertThrowsExactly(
                IllegalArgumentException.class,
                () -> client.sendEvent(Buffer.buffer("test"), null, "my-tenant", null, null));
    }
}
