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

package org.eclipse.hono.client.kafka.producer;

import static com.google.common.truth.Truth.assertThat;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * Verifies the behavior of {@link MessagingKafkaProducerConfigProperties}.
 */
public class MessagingKafkaProducerConfigPropertiesTest {

    /**
     * Verifies that properties returned in {@link MessagingKafkaProducerConfigProperties#getProducerConfig(String)} contain
     * the predefined entries, overriding any corresponding properties given in {@link MessagingKafkaProducerConfigProperties#setProducerConfig(Map)}.
     */
    @Test
    public void testThatGetProducerConfigReturnsAdaptedConfig() {

        final Map<String, String> properties = new HashMap<>();
        properties.put("key.serializer", "foo");
        properties.put("value.serializer", "bar");
        properties.put("enable.idempotence", "baz");

        final MessagingKafkaProducerConfigProperties config = new MessagingKafkaProducerConfigProperties();
        config.setProducerConfig(properties);

        final Map<String, String> producerConfig = config.getProducerConfig("producerName");

        assertThat(producerConfig.get("key.serializer"))
                .isEqualTo("org.apache.kafka.common.serialization.StringSerializer");
        assertThat(producerConfig.get("value.serializer"))
                .isEqualTo("io.vertx.kafka.client.serialization.BufferSerializer");
        assertThat(producerConfig.get("enable.idempotence")).isEqualTo("true");
    }

}
