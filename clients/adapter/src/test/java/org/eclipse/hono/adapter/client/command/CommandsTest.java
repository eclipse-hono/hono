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

package org.eclipse.hono.adapter.client.command;

import static org.assertj.core.api.Assertions.assertThat;

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
    public void testGetRequestId() {
        final String correlationId = "myCorrelationId";
        final String replyToId = "myReplyToId";
        final String deviceId = "myDeviceId";
        final String requestId = Commands.getRequestId(correlationId, replyToId, deviceId);
        assertThat(requestId).isNotNull();
        assertThat(requestId.contains(correlationId)).isTrue();
        assertThat(requestId.contains(replyToId)).isTrue();

        // do the reverse operation
        final Pair<String, String> correlationAndReplyId = Commands.getCorrelationAndReplyToId(requestId, deviceId);
        assertThat(correlationAndReplyId.one()).isEqualTo(correlationId);
        assertThat(correlationAndReplyId.two()).isEqualTo(replyToId);
    }

    /**
     * Verifies that getting the request id with a replyToId starting with the deviceId returns the expected value.
     */
    @Test
    public void testGetRequestIdWithReplyToIdContainingDeviceId() {
        final String correlationId = "myCorrelationId";
        final String deviceId = "myDeviceId";
        final String replyToId = deviceId + "/myReplyId";
        final String requestId = Commands.getRequestId(correlationId, replyToId, deviceId);
        assertThat(requestId).isNotNull();
        assertThat(requestId.contains(correlationId)).isTrue();
        assertThat(requestId.contains(replyToId)).isFalse();
        assertThat(requestId.contains("myReplyId")).isTrue();

        // do the reverse operation
        final Pair<String, String> correlationAndReplyId = Commands.getCorrelationAndReplyToId(requestId, deviceId);
        assertThat(correlationAndReplyId.one()).isEqualTo(correlationId);
        assertThat(correlationAndReplyId.two()).isEqualTo(replyToId);
    }

    /**
     * Verifies that getting the request id without a given correlation id returns a non-null value.
     */
    @Test
    public void testGetRequestIdUsingOnlyCorrelationId() {
        final String correlationId = "myCorrelationId";
        final String deviceId = "myDeviceId";
        final String requestId = Commands.getRequestId(correlationId);
        assertThat(requestId).isNotNull();
        assertThat(requestId.contains(correlationId)).isTrue();

        // do the reverse operation
        final Pair<String, String> correlationAndReplyId = Commands.getCorrelationAndReplyToId(requestId, deviceId);
        assertThat(correlationAndReplyId.one()).isEqualTo(correlationId);
        assertThat(correlationAndReplyId.two()).isEmpty();
    }

    /**
     * Verifies that getting the device-facing-reply-id returns the expected result.
     */
    @Test
    public void testGetDeviceFacingReplyToId() {
        final String replyToId = "replyToId";
        final String deviceId = "deviceId";
        final String deviceFacingReplyToId = Commands.getDeviceFacingReplyToId(replyToId, deviceId);
        assertThat(deviceFacingReplyToId).contains(deviceId);

        // do the reverse operation
        final String originalReplyToId = Commands.getOriginalReplyToId(deviceFacingReplyToId, deviceId);
        assertThat(originalReplyToId).isEqualTo(replyToId);
    }

    /**
     * Verifies that getting the device-facing-reply-id with a replyToId starting with the deviceId
     * returns the expected result.
     */
    @Test
    public void testGetDeviceFacingReplyToIdWithReplyToIdContainingDeviceId() {
        final String deviceId = "deviceId";
        final String replyToId = deviceId + "/myReplyId";
        final String deviceFacingReplyToId = Commands.getDeviceFacingReplyToId(replyToId, deviceId);
        assertThat(deviceFacingReplyToId).startsWith(deviceId);
        assertThat(deviceFacingReplyToId).doesNotContain(replyToId);
        assertThat(deviceFacingReplyToId).endsWith("myReplyId");

        // do the reverse operation
        final String originalReplyToId = Commands.getOriginalReplyToId(deviceFacingReplyToId, deviceId);
        assertThat(originalReplyToId).isEqualTo(replyToId);
    }

    /**
     * Verifies that getting the device-facing-reply-id with a null replyToId returns the expected result.
     */
    @Test
    public void testGetDeviceFacingReplyToIdForNullReplyToId() {
        final String deviceId = "deviceId";
        final String deviceFacingReplyToId = Commands.getDeviceFacingReplyToId(null, deviceId);
        assertThat(deviceFacingReplyToId).contains(deviceId);

        // do the reverse operation
        final String originalReplyToId = Commands.getOriginalReplyToId(deviceFacingReplyToId, deviceId);
        assertThat(originalReplyToId).isEqualTo("");
    }
}
