/*******************************************************************************
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.client.command;

import static com.google.common.truth.Truth.assertThat;

import org.eclipse.hono.util.MessagingType;
import org.eclipse.hono.util.Pair;
import org.junit.jupiter.api.Test;

/**
 * Tests verifying behavior of {@link Commands}.
 *
 */
public class CommandsTest {

    /**
     * Verifies that getting the request id with non-empty parameters returns the expected value.
     */
    @Test
    public void testEncodeRequestIdParameters() {
        final String correlationId = "myCorrelationId";
        final String replyToId = "myReplyToId";
        final String deviceId = "myDeviceId";
        final String requestId = Commands.encodeRequestIdParameters(correlationId, replyToId, deviceId, MessagingType.kafka);
        assertThat(requestId).isNotNull();
        assertThat(requestId.contains(correlationId)).isTrue();
        assertThat(requestId.contains(replyToId)).isTrue();

        // do the reverse operation
        final CommandRequestIdParameters requestIdParams = Commands.decodeRequestIdParameters(requestId, deviceId);
        assertThat(requestIdParams.getCorrelationId()).isEqualTo(correlationId);
        assertThat(requestIdParams.getReplyToId()).isEqualTo(replyToId);
        assertThat(requestIdParams.getMessagingType()).isEqualTo(MessagingType.kafka);
    }

    /**
     * Verifies that getting the request id with non-empty parameters and messaging type AMQP returns the expected value.
     */
    @Test
    public void testEncodeRequestIdParametersWithMessagingTypeAmqp() {
        final String correlationId = "myCorrelationId";
        final String replyToId = "myReplyToId";
        final String deviceId = "myDeviceId";
        final String requestId = Commands.encodeRequestIdParameters(correlationId, replyToId, deviceId, MessagingType.amqp);
        assertThat(requestId).isNotNull();
        assertThat(requestId.contains(correlationId)).isTrue();
        assertThat(requestId.contains(replyToId)).isTrue();

        // do the reverse operation
        final CommandRequestIdParameters requestIdParams = Commands.decodeRequestIdParameters(requestId, deviceId);
        assertThat(requestIdParams.getCorrelationId()).isEqualTo(correlationId);
        assertThat(requestIdParams.getReplyToId()).isEqualTo(replyToId);
        assertThat(requestIdParams.getMessagingType()).isEqualTo(MessagingType.amqp);
    }

    /**
     * Verifies that getting the request id with non-empty parameters and messaging type Pub/Sub returns the expected value.
     */
    @Test
    public void testEncodeRequestIdParametersWithMessagingTypePubSub() {
        final String correlationId = "myCorrelationId";
        final String replyToId = "myReplyToId";
        final String deviceId = "myDeviceId";
        final String requestId = Commands.encodeRequestIdParameters(correlationId, replyToId, deviceId, MessagingType.pubsub);
        assertThat(requestId).isNotNull();
        assertThat(requestId.contains(correlationId)).isTrue();
        assertThat(requestId.contains(replyToId)).isTrue();

        // do the reverse operation
        final CommandRequestIdParameters requestIdParams = Commands.decodeRequestIdParameters(requestId, deviceId);
        assertThat(requestIdParams.getCorrelationId()).isEqualTo(correlationId);
        assertThat(requestIdParams.getReplyToId()).isEqualTo(replyToId);
        assertThat(requestIdParams.getMessagingType()).isEqualTo(MessagingType.pubsub);
    }

    /**
     * Verifies that getting the request id with a replyToId starting with the deviceId returns the expected value.
     */
    @Test
    public void testEncodeRequestIdParametersWithReplyToIdContainingDeviceId() {
        final String correlationId = "myCorrelationId";
        final String deviceId = "myDeviceId";
        final String replyToId = deviceId + "/myReplyId";
        final String requestId = Commands.encodeRequestIdParameters(correlationId, replyToId, deviceId, MessagingType.kafka);
        assertThat(requestId).isNotNull();
        assertThat(requestId.contains(correlationId)).isTrue();
        assertThat(requestId.contains(replyToId)).isFalse();
        assertThat(requestId.contains("myReplyId")).isTrue();

        // do the reverse operation
        final CommandRequestIdParameters requestIdParams = Commands.decodeRequestIdParameters(requestId, deviceId);
        assertThat(requestIdParams.getCorrelationId()).isEqualTo(correlationId);
        assertThat(requestIdParams.getReplyToId()).isEqualTo(replyToId);
        assertThat(requestIdParams.getMessagingType()).isEqualTo(MessagingType.kafka);
    }

    /**
     * Verifies that getting the request id without a given correlation id returns a non-null value.
     */
    @Test
    public void testEncodeRequestIdParametersUsingOnlyCorrelationId() {
        final String correlationId = "myCorrelationId";
        final String deviceId = "myDeviceId";
        final String requestId = Commands.encodeRequestIdParameters(correlationId, MessagingType.kafka);
        assertThat(requestId).isNotNull();
        assertThat(requestId.contains(correlationId)).isTrue();

        // do the reverse operation
        final CommandRequestIdParameters requestIdParams = Commands.decodeRequestIdParameters(requestId, deviceId);
        assertThat(requestIdParams.getCorrelationId()).isEqualTo(correlationId);
        assertThat(requestIdParams.getReplyToId()).isEmpty();
        assertThat(requestIdParams.getMessagingType()).isEqualTo(MessagingType.kafka);
    }

    /**
     * Verifies that getting the device-facing-reply-id returns the expected result.
     */
    @Test
    public void testGetDeviceFacingReplyToId() {
        final String replyToId = "replyToId";
        final String deviceId = "deviceId";
        final String deviceFacingReplyToId = Commands.getDeviceFacingReplyToId(replyToId, deviceId, MessagingType.kafka);
        assertThat(deviceFacingReplyToId).contains(deviceId);

        // do the reverse operation
        final Pair<String, MessagingType> originalReplyToIdMessagingTypePair = Commands
                .getOriginalReplyToIdAndMessagingType(deviceFacingReplyToId, deviceId);
        assertThat(originalReplyToIdMessagingTypePair.one()).isEqualTo(replyToId);
        assertThat(originalReplyToIdMessagingTypePair.two()).isEqualTo(MessagingType.kafka);
    }

    /**
     * Verifies that getting the device-facing-reply-id with messaging type set to AMQP returns the expected result.
     */
    @Test
    public void testGetDeviceFacingReplyToIdWithMessagingTypeAmqp() {
        final String replyToId = "replyToId";
        final String deviceId = "deviceId";
        final String deviceFacingReplyToId = Commands.getDeviceFacingReplyToId(replyToId, deviceId, MessagingType.amqp);
        assertThat(deviceFacingReplyToId).contains(deviceId);

        // do the reverse operation
        final Pair<String, MessagingType> originalReplyToIdMessagingTypePair = Commands
                .getOriginalReplyToIdAndMessagingType(deviceFacingReplyToId, deviceId);
        assertThat(originalReplyToIdMessagingTypePair.one()).isEqualTo(replyToId);
        assertThat(originalReplyToIdMessagingTypePair.two()).isEqualTo(MessagingType.amqp);
    }

    /**
     * Verifies that getting the device-facing-reply-id with messaging type set to Pub/Sub returns the expected result.
     */
    @Test
    public void testGetDeviceFacingReplyToIdWithMessagingTypePubSub() {
        final String replyToId = "replyToId";
        final String deviceId = "deviceId";
        final String deviceFacingReplyToId = Commands.getDeviceFacingReplyToId(replyToId, deviceId, MessagingType.pubsub);
        assertThat(deviceFacingReplyToId).contains(deviceId);

        // do the reverse operation
        final Pair<String, MessagingType> originalReplyToIdMessagingTypePair = Commands
                .getOriginalReplyToIdAndMessagingType(deviceFacingReplyToId, deviceId);
        assertThat(originalReplyToIdMessagingTypePair.one()).isEqualTo(replyToId);
        assertThat(originalReplyToIdMessagingTypePair.two()).isEqualTo(MessagingType.pubsub);
    }

    /**
     * Verifies that getting the device-facing-reply-id with a replyToId starting with the deviceId
     * returns the expected result.
     */
    @Test
    public void testGetDeviceFacingReplyToIdWithReplyToIdContainingDeviceId() {
        final String deviceId = "deviceId";
        final String replyToId = deviceId + "/myReplyId";
        final String deviceFacingReplyToId = Commands.getDeviceFacingReplyToId(replyToId, deviceId, MessagingType.kafka);
        assertThat(deviceFacingReplyToId).startsWith(deviceId);
        assertThat(deviceFacingReplyToId).doesNotContain(replyToId);
        assertThat(deviceFacingReplyToId).endsWith("myReplyId");

        // do the reverse operation
        final Pair<String, MessagingType> originalReplyToIdMessagingTypePair = Commands
                .getOriginalReplyToIdAndMessagingType(deviceFacingReplyToId, deviceId);
        assertThat(originalReplyToIdMessagingTypePair.one()).isEqualTo(replyToId);
        assertThat(originalReplyToIdMessagingTypePair.two()).isEqualTo(MessagingType.kafka);
    }

    /**
     * Verifies that getting the device-facing-reply-id with a null replyToId returns the expected result.
     */
    @Test
    public void testGetDeviceFacingReplyToIdForNullReplyToId() {
        final String deviceId = "deviceId";
        final String deviceFacingReplyToId = Commands.getDeviceFacingReplyToId(null, deviceId, MessagingType.kafka);
        assertThat(deviceFacingReplyToId).contains(deviceId);

        // do the reverse operation
        final Pair<String, MessagingType> originalReplyToIdMessagingTypePair = Commands
                .getOriginalReplyToIdAndMessagingType(deviceFacingReplyToId, deviceId);
        assertThat(originalReplyToIdMessagingTypePair.one()).isEqualTo("");
        assertThat(originalReplyToIdMessagingTypePair.two()).isEqualTo(MessagingType.kafka);
    }
}
