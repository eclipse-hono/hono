/**
 * Copyright (c) 2023 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * https://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.hono.client.pubsub;

import static org.junit.jupiter.api.Assertions.assertTrue;

import static com.google.common.truth.Truth.assertThat;

import java.util.Optional;
import java.util.Random;

import org.junit.jupiter.api.Test;

import com.google.protobuf.ByteString;
import com.google.pubsub.v1.PubsubMessage;

/**
 * Verifies the generic behavior of {@link PubSubMessageHelper}.
 */
public class PubSubMessageHelperTest {

    /**
     * Verifies that the getTopicName method returns the formatted topic.
     */
    @Test
    public void testThatGetTopicNameReturnsFormattedString() {
        final String topic = "event";
        final String prefix = "testTenant";

        final String result = PubSubMessageHelper.getTopicName(topic, prefix);
        assertThat(result).isEqualTo("testTenant.event");
    }

    /**
     * Verifies that the getPayload method returns the optional of bytes representing the payload.
     */
    @Test
    public void testThatGetPayloadReturnsOptionalOfCorrectByteArray() {
        final byte[] b = new byte[22];
        new Random().nextBytes(b);

        final ByteString data = ByteString.copyFrom(b);
        final PubsubMessage pubsubMessage = PubsubMessage.newBuilder().setData(data).build();

        final Optional<byte[]> result = PubSubMessageHelper.getPayload(pubsubMessage);
        assertTrue(result.isPresent());
        assertThat(result.get()).isEqualTo(b);
        assertThat(result.get().length).isEqualTo(22);
    }

    /**
     * Verifies that the getPayload method returns the optional of an empty byte array when the payload has no data.
     */
    @Test
    public void testThatGetPayloadReturnsOptionalOfEmptyByteArray() {
        final PubsubMessage pubsubMessage = PubsubMessage.newBuilder().build();
        final Optional<byte[]> result = PubSubMessageHelper.getPayload(pubsubMessage);
        assertTrue(result.isPresent());
        assertThat(result.get()).isEqualTo(new byte[0]);
        assertThat(result.get().length).isEqualTo(0);
    }

    /**
     * Verifies that the getPayload method returns an empty optional when the PubsubMessage is {@code null}.
     */
    @Test
    public void testThatGetPayloadReturnsEmptyOptional() {
        assertTrue(PubSubMessageHelper.getPayload(null).isEmpty());
    }
}
