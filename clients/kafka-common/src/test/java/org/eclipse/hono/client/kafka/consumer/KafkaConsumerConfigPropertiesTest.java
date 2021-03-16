/*
 * Copyright (c) 2020, 2021 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.client.kafka.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * Verifies the behavior of {@link KafkaConsumerConfigProperties}.
 */
public class KafkaConsumerConfigPropertiesTest {

    static final String PROPERTY_FILE_CLIENT_ID = "target/test-classes/clientid.properties";
    static final String PROPERTY_FILE_COMMON = "target/test-classes/common.properties";
    static final String PROPERTY_FILE_CONSUMER = "target/test-classes/consumer.properties";

    /**
     * Verifies that trying to set a negative poll timeout throws an {@link IllegalArgumentException}.
     */
    @Test
    public void testThatPollTimeoutCanNotBeSetToNull() {
        assertThrows(IllegalArgumentException.class, () -> new KafkaConsumerConfigProperties().setPollTimeout(-1L));
    }

    /**
     * Verifies that properties returned by {@link KafkaConsumerConfigProperties#getProducerConfig(String)} contain
     * the predefined entries, overriding any corresponding properties defined in property files.
     */
    @Test
    public void testThatGetConsumerConfigReturnsAdaptedConfig() {

        final var config = new KafkaConsumerConfigProperties();
        config.setPropertyFiles(List.of(PROPERTY_FILE_CONSUMER));

        final Map<String, String> consumerConfig = config.getConsumerConfig("consumerName");

        assertThat(consumerConfig.get("key.deserializer"))
                .isEqualTo("org.apache.kafka.common.serialization.StringDeserializer");
        assertThat(consumerConfig.get("value.deserializer"))
                .isEqualTo("io.vertx.kafka.client.serialization.BufferDeserializer");
    }

    /**
     * Verifies that the client ID set with {@link KafkaConsumerConfigProperties#setDefaultClientIdPrefix(String)} is applied when it
     * is NOT present in the configuration.
     */
    @Test
    public void testThatClientIdIsApplied() {
        final String clientId = "the-client";

        final var config = new KafkaConsumerConfigProperties();
        config.setDefaultClientIdPrefix(clientId);

        final Map<String, String> consumerConfig = config.getConsumerConfig("consumerName");
        assertThat(consumerConfig.get("client.id")).startsWith(clientId + "-consumerName-");
    }

    /**
     * Verifies that the client ID set with {@link KafkaConsumerConfigProperties#setDefaultClientIdPrefix(String)} is NOT applied
     * when it is present in the configuration.
     */
    @Test
    public void testThatClientIdIsNotAppliedIfAlreadyPresent() {

        final var config = new KafkaConsumerConfigProperties();
        config.setPropertyFiles(List.of(PROPERTY_FILE_CLIENT_ID));
        config.setDefaultClientIdPrefix("other-client");

        final Map<String, String> consumerConfig = config.getConsumerConfig("consumerName");
        assertThat(consumerConfig.get("client.id")).startsWith("configured-id-consumerName-");
    }
}
