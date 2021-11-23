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

package org.eclipse.hono.client.kafka;

import static com.google.common.truth.Truth.assertThat;

import javax.inject.Inject;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

/**
 * Verifies that Quarkus binds properties from yaml files to configuration objects.
 */
@QuarkusTest
public class KafkaClientOptionsTest {

    @Inject
    KafkaClientOptions kafkaClientOptions;

    /**
     * Asserts that the Kafka configuration is exposed as a beans.
     */
    @Test
    public void testThatClientOptionsExist() {
        assertThat(kafkaClientOptions).isNotNull();
    }

    /**
     * Asserts that common client properties are present.
     */
    @Test
    public void testThatCommonConfigIsPresent() {
        assertThat(kafkaClientOptions.commonClientConfig().get("common.property")).isEqualTo("present");
        assertThat(kafkaClientOptions.commonClientConfig().get("empty")).isEqualTo("");
    }

    /**
     * Asserts that consumer properties are present.
     */
    @Test
    public void testThatConsumerConfigIsPresent() {
        assertThat(kafkaClientOptions.consumerConfig().get("consumer.property")).isEqualTo("consumer");
    }

    /**
     * Asserts that admin client properties are present.
     */
    @Test
    public void testThatAdminClientConfigIsPresent() {
        assertThat(kafkaClientOptions.adminClientConfig().get("admin.property")).isEqualTo("admin");
    }

    /**
     * Asserts that producer properties are present.
     */
    @Test
    public void testThatProducerConfigIsPresent() {
        assertThat(kafkaClientOptions.producerConfig().get("producer.property")).isEqualTo("producer");
    }

    /**
     * Asserts that compound keys that contain dots are added and not changed.
     */
    @Test
    public void testThatKeysWithDotsAreAllowed() {
        assertThat(kafkaClientOptions.commonClientConfig().get("bootstrap.servers")).isEqualTo("example.com:9999");
    }

    /**
     * Asserts that properties with a numeric value added as strings.
     */
    @Test
    public void testThatNumbersArePresentAsStrings() {
        assertThat(kafkaClientOptions.commonClientConfig().get("number")).isEqualTo("123");
    }
}
