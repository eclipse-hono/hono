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

import static com.google.common.truth.Truth.assertThat;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * Verifies the behavior of {@link MessagingKafkaConsumerConfigProperties}.
 */
public class MessagingKafkaConsumerConfigPropertiesTest {


    /**
     * Verifies that properties returned in {@link MessagingKafkaConsumerConfigProperties#getConsumerConfig(String)} ()}
     * contain the predefined entries, overriding any corresponding properties given in
     * {@link MessagingKafkaConsumerConfigProperties#setConsumerConfig(Map)}.
     */
    @Test
    public void testThatGetConsumerConfigReturnsAdaptedConfig() {

        final Map<String, String> properties = new HashMap<>();
        properties.put("key.deserializer", "foo");
        properties.put("value.deserializer", "bar");

        final MessagingKafkaConsumerConfigProperties config = new MessagingKafkaConsumerConfigProperties();
        config.setConsumerConfig(properties);

        final Map<String, String> consumerConfig = config.getConsumerConfig("consumerName");

        assertThat(consumerConfig.get("key.deserializer"))
                .isEqualTo("org.apache.kafka.common.serialization.StringDeserializer");
        assertThat(consumerConfig.get("value.deserializer"))
                .isEqualTo("io.vertx.kafka.client.serialization.BufferDeserializer");
    }

}
