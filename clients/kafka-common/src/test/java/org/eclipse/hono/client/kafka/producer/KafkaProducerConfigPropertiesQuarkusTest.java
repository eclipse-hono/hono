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

package org.eclipse.hono.client.kafka.producer;

import static org.junit.jupiter.api.Assertions.assertAll;

import static com.google.common.truth.Truth.assertThat;

import org.apache.kafka.common.serialization.StringSerializer;
import org.eclipse.hono.client.kafka.CommonKafkaClientOptions;
import org.eclipse.hono.test.ConfigMappingSupport;
import org.junit.jupiter.api.Test;

/**
 * Verifies the creation of {@link KafkaProducerConfigProperties} from {@link KafkaProducerOptions}.
 *
 */
class KafkaProducerConfigPropertiesQuarkusTest {

    @Test
    void testProducerOptionsAreSet() {

        final var commonOptions = ConfigMappingSupport.getConfigMapping(
                CommonKafkaClientOptions.class,
                this.getClass().getResource("/producer-options.yaml"),
                "hono.kafka");
        final var producerOptions = ConfigMappingSupport.getConfigMapping(
                KafkaProducerOptions.class,
                this.getClass().getResource("/producer-options.yaml"),
                "hono.kafka.producerTest");
        final var commandResponseConfig = new KafkaProducerConfigProperties(
                StringSerializer.class,
                StringSerializer.class,
                commonOptions,
                producerOptions);

        assertAll(() -> assertThat(commandResponseConfig.getProducerConfig("test").get("common.property"))
                    .isEqualTo("present"),
                () -> assertThat(commandResponseConfig.getProducerConfig("test").get("producer.property"))
                    .isEqualTo("producer"),
                () -> assertThat(commandResponseConfig.getProducerConfig("test").get("number")).isEqualTo("123"),
                () -> assertThat(commandResponseConfig.getProducerConfig("test").get("empty")).isEqualTo(""));
    }
}
