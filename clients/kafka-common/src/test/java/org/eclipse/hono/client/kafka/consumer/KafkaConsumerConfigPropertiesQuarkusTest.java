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

package org.eclipse.hono.client.kafka.consumer;

import static org.junit.jupiter.api.Assertions.assertAll;

import static com.google.common.truth.Truth.assertThat;

import org.apache.kafka.common.serialization.StringDeserializer;
import org.eclipse.hono.client.kafka.CommonKafkaClientOptions;
import org.eclipse.hono.test.ConfigMappingSupport;
import org.junit.jupiter.api.Test;

/**
 * Verifies the creation of {@link KafkaConsumerConfigProperties} from {@link KafkaConsumerOptions}.
 *
 */
class KafkaConsumerConfigPropertiesQuarkusTest {

    @Test
    void testConsumerOptionsAreSet() {
        final var commonOptions = ConfigMappingSupport.getConfigMapping(
                CommonKafkaClientOptions.class,
                this.getClass().getResource("/consumer-options.yaml"),
                "hono.kafka");
        final var consumerOptions = ConfigMappingSupport.getConfigMapping(
                KafkaConsumerOptions.class,
                this.getClass().getResource("/consumer-options.yaml"),
                "hono.kafka.consumerTest");
        final var config = new KafkaConsumerConfigProperties(
                StringDeserializer.class,
                StringDeserializer.class,
                commonOptions,
                consumerOptions);

        assertAll(() -> assertThat(config.getConsumerConfig("test").get("common.property")).isEqualTo("present"),
                () -> assertThat(config.getConsumerConfig("test").get("number")).isEqualTo("123"),
                () -> assertThat(config.getConsumerConfig("test").get("empty")).isEqualTo(""));

        assertAll(() -> assertThat(config.getPollTimeout()).isEqualTo(999),
                () -> assertThat(config.getConsumerConfig("test").get("consumer.property")).isEqualTo("consumer"));
    }
}
