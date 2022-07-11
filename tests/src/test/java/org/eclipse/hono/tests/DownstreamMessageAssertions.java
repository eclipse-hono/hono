/**
 * Copyright (c) 2021, 2022 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.hono.tests;

import static org.junit.jupiter.api.Assertions.assertAll;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import java.time.Duration;

import org.eclipse.hono.application.client.DownstreamMessage;
import org.eclipse.hono.application.client.MessageContext;
import org.eclipse.hono.application.client.amqp.AmqpMessageContext;
import org.eclipse.hono.application.client.kafka.KafkaMessageContext;
import org.eclipse.hono.client.amqp.connection.AmqpUtils;
import org.eclipse.hono.client.kafka.tracing.KafkaTracingHelper;
import org.eclipse.hono.util.MessageHelper;

import io.opentracing.SpanContext;
import io.vertx.core.buffer.Buffer;


/**
 * A utility class encapsulating assertions on {@link DownstreamMessage}s.
 */
public final class DownstreamMessageAssertions {

    private DownstreamMessageAssertions() {
        // prevent instantiation
    }

    /**
     * Verifies that an AMQP message is durable.
     *
     * @param msg The message to be verified.
     * @throws AssertionError if the given message is an AMQP message but is not durable.
     */
    public static void assertMessageIsDurable(final DownstreamMessage<? extends MessageContext> msg) {
        if (msg.getMessageContext() instanceof AmqpMessageContext amqpMessageContext) {
            assertWithMessage("message is durable").that(amqpMessageContext.getRawMessage().isDurable()).isTrue();
        }
    }

    /**
     * Verifies that a downstream message contains a time-to-live.
     *
     * @param msg The message to check.
     * @param expectedTtl The expected time-to-live.
     * @throws AssertionError if the message does not contain the expected ttl.
     */
    public static void assertMessageContainsTimeToLive(
            final DownstreamMessage<? extends MessageContext> msg,
            final Duration expectedTtl) {

        assertWithMessage("message contains expected ttl").that(msg.getTimeToLive()).isEqualTo(expectedTtl);
    }

    /**
     * Verifies that a downstream message contains a creation-time.
     *
     * @param msg The message to check.
     * @throws AssertionError if any of the checks fails.
     */
    public static void assertMessageContainsCreationTime(final DownstreamMessage<? extends MessageContext> msg) {
        assertWithMessage("message contains creation time").that(msg.getCreationTime()).isNotNull();
    }

    /**
     * Verifies that a downstream message contains a time until disconnect.
     *
     * @param msg The message to check.
     * @throws AssertionError if any of the checks fails.
     */
    public static void assertMessageContainsTimeTillDisconnect(final DownstreamMessage<? extends MessageContext> msg) {
        assertWithMessage("ttd property value").that(msg.getTimeTillDisconnect()).isAtLeast(-1);
    }

    /**
     * Verifies that a downstream message contains a tenant ID.
     *
     * @param msg The message to check.
     * @param expectedTenantId The expected tenant identifier.
     * @throws AssertionError if any of the checks fails.
     */
    public static void assertMessageContainsTenantId(
            final DownstreamMessage<? extends MessageContext> msg,
            final String expectedTenantId) {
        assertWithMessage("message contains tenant ID").that(msg.getTenantId()).isEqualTo(expectedTenantId);
    }

    /**
     * Verifies that a downstream message contains the adapter and address that the message has originally
     * been uploaded to.
     *
     * @param msg The message to check.
     * @throws AssertionError if any of the checks fail.
     */
    public static void assertMessageContainsAdapterAndAddress(final DownstreamMessage<? extends MessageContext> msg) {
        assertAll("message contains original adapter type and address",
            () -> assertWithMessage("message contains %s", MessageHelper.APP_PROPERTY_ORIG_ADDRESS)
                .that(msg.getProperties().getProperty(MessageHelper.APP_PROPERTY_ORIG_ADDRESS, String.class))
                .isNotNull(),
            () -> assertWithMessage("message contains %s", MessageHelper.APP_PROPERTY_ORIG_ADAPTER)
                .that(msg.getProperties().getProperty(MessageHelper.APP_PROPERTY_ORIG_ADAPTER, String.class))
                .isNotNull());
    }


    /**
     * Verifies that a telemetry message that has been received by a downstream consumer contains
     * all properties that are required by the north bound Telemetry API.
     *
     * @param msg The message to check.
     * @throws AssertionError if any of the checks fail.
     */
    public static void assertTelemetryApiProperties(
            final DownstreamMessage<? extends MessageContext> msg) {

        assertAll("message has properties required by Telemetry API",
                () -> assertWithMessage("message has content type").that(msg.getContentType()).isNotNull(),
                () -> assertMessageContainsCreationTime(msg),
                () -> assertWithMessage("message has device ID").that(msg.getDeviceId()).isNotNull(),
                () -> assertMessageContainsTracingContext(msg, null));

    }

    /**
     * Verifies that a command response message that has been received by a downstream consumer contains
     * all properties that are required by the north bound Command &amp; Control API.
     *
     * @param msg The message to check.
     * @param expectedTenantId The expected tenant identifier.
     * @param expectedDeviceId The expected device identifier.
     * @throws AssertionError if any of the checks fail.
     */
    public static void assertCommandAndControlApiProperties(
            final DownstreamMessage<? extends MessageContext> msg,
            final String expectedTenantId,
            final String expectedDeviceId) {

        assertAll("message has properties required by Command & Control API",
                () -> assertMessageContainsCreationTime(msg),
                () -> assertMessageContainsTenantId(msg, expectedTenantId),
                () -> assertWithMessage("message has device ID").that(msg.getDeviceId()).isEqualTo(expectedDeviceId),
                () -> assertWithMessage("message has correlation ID").that(msg.getCorrelationId()).isNotNull(),
                () -> assertWithMessage("message has status code").that(msg.getStatus()).isNotNull());
    }

    /**
     * Asserts that a downstream message contains a tracing context.
     *
     * @param msg The message to check.
     * @param expectedTraceId The trace ID that the tracing context is expected to have or {@code null} if the ID should
     *                        not be checked.
     * @throws AssertionError if the message does not contain a tracing context.
     */
    public static void assertMessageContainsTracingContext(
            final DownstreamMessage<? extends MessageContext> msg,
            final String expectedTraceId) {

        final SpanContext spanContext;
        if (msg.getMessageContext() instanceof AmqpMessageContext ctx) {
            spanContext = AmqpUtils.extractSpanContext(IntegrationTestSupport.CLIENT_TRACER, ctx.getRawMessage());
        } else if (msg.getMessageContext() instanceof KafkaMessageContext ctx) {
            spanContext = KafkaTracingHelper.extractSpanContext(IntegrationTestSupport.CLIENT_TRACER, ctx.getRecord());
        } else {
            throw new AssertionError("unsupported DownstreamMessage type [%s]".formatted(msg.getClass().getName()));
        }
        assertWithMessage("message contains a tracing context").that(spanContext).isNotNull();
        if (expectedTraceId != null) {
            assertWithMessage("message contains a tracing context with trace ID").that(spanContext.toTraceId())
                    .isEqualTo(expectedTraceId);
        }
    }

    /**
     * Asserts that a downstream message contains Lora network meta data.
     *
     * @param msg The message to check.
     * @throws AssertionError if the message does not contain meta data.
     */
    public static void assertLoraMetaDataPresent(final DownstreamMessage<? extends MessageContext> msg) {
        final var metaDataBuffer = Buffer.buffer(msg.getProperties().getProperty("meta_data", String.class));
        final var metaData = metaDataBuffer.toJsonObject();
        assertThat(metaData).isNotEmpty();
        assertThat(metaData.getDouble("frequency")).isNotNull();
    }
}
