/**
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
 */

package org.eclipse.hono.client.kafka.consumer;

import static com.google.common.truth.Truth.assertThat;

import javax.inject.Inject;

import org.apache.kafka.common.serialization.StringDeserializer;
import org.eclipse.hono.client.kafka.CommonKafkaClientOptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.config.ConfigMapping;

/**
 * Verifies the creation of {@link KafkaConsumerConfigProperties} from {@link KafkaConsumerOptions}.
 *
 */
@QuarkusTest
public class KafkaConsumerConfigPropertiesQuarkusTest {

    @Inject
    @ConfigMapping(prefix = "hono.kafka")
    CommonKafkaClientOptions commonOptions;

    @Inject
    @ConfigMapping(prefix = "hono.kafka.consumerTest")
    KafkaConsumerOptions consumerOptions;

    private KafkaConsumerConfigProperties config;

    @BeforeEach
    void setUp() {
        config = new KafkaConsumerConfigProperties(StringDeserializer.class, StringDeserializer.class, commonOptions,
                consumerOptions);
    }

    /**
     * Asserts that the poll timeout property is present.
     */
    @Test
    public void testThatPollTimeoutIsSet() {
        assertThat(config.getPollTimeout()).isEqualTo(999);
    }

    /**
     * Asserts that common client properties are present.
     */
    @Test
    public void testThatCommonConfigIsPresent() {
        assertThat(config.getConsumerConfig("test").get("common.property")).isEqualTo("present");
    }

    /**
     * Asserts that consumer properties are present.
     */
    @Test
    public void testThatConsumerConfigIsPresent() {
        assertThat(config.getConsumerConfig("test").get("consumer.property")).isEqualTo("consumer");
    }

    /**
     * Asserts that properties with a numeric value added as strings.
     */
    @Test
    public void testThatNumbersArePresentAsStrings() {
        assertThat(config.getConsumerConfig("test").get("number")).isEqualTo("123");
    }

    /**
     * Asserts that properties with an empty string as the value are added.
     */
    @Test
    public void testThatEmptyValuesAreMaintained() {
        assertThat(config.getConsumerConfig("test").get("empty")).isEqualTo("");
    }

}
