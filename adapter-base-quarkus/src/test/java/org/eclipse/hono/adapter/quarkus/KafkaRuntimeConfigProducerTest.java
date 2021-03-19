/*
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

package org.eclipse.hono.adapter.quarkus;

import static org.assertj.core.api.Assertions.assertThat;

import javax.inject.Inject;

import org.eclipse.hono.client.kafka.KafkaAdminClientConfigProperties;
import org.eclipse.hono.client.kafka.KafkaProducerConfigProperties;
import org.eclipse.hono.client.kafka.consumer.KafkaConsumerConfigProperties;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

/**
 * Verifies that {@link KafkaRuntimeConfigProducer} parses yaml files correctly and exposes the configuration as a
 * beans.
 */
@QuarkusTest
public class KafkaRuntimeConfigProducerTest {

    @Inject
    KafkaProducerConfigProperties kafkaProducerConfig;

    @Inject
    KafkaConsumerConfigProperties kafkaConsumerConfig;

    @Inject
    KafkaAdminClientConfigProperties kafkaAdminClientConfig;

    /**
     * Asserts that the Kafka configuration is exposed as a beans.
     */
    @Test
    public void testThatBeansAreProvided() {
        assertThat(kafkaProducerConfig).isNotNull();
        assertThat(kafkaConsumerConfig).isNotNull();
        assertThat(kafkaAdminClientConfig).isNotNull();
    }

    /**
     * Asserts that properties with prefix {@link KafkaRuntimeConfigProducer#COMMON_CONFIG_PREFIX} are present.
     */
    @Test
    public void testThatCommonConfigIsPresent() {
        assertThat(kafkaProducerConfig.getProducerConfig("test").get("common.property")).isEqualTo("present");
    }

    /**
     * Asserts that properties with prefix {@link KafkaRuntimeConfigProducer#CONSUMER_CONFIG_PREFIX} are present.
     */
    @Test
    public void testThatConsumerConfigIsPresent() {
        assertThat(kafkaConsumerConfig.getConsumerConfig("test").get("consumer.property")).isEqualTo("consumer");
    }

    /**
     * Asserts that properties with prefix {@link KafkaRuntimeConfigProducer#ADMIN_CLIENT_CONFIG_PREFIX} are present.
     */
    @Test
    public void testThatAdminClientConfigIsPresent() {
        assertThat(kafkaAdminClientConfig.getAdminClientConfig("test").get("admin.property")).isEqualTo("admin");
    }

    /**
     * Asserts that properties with prefix {@link KafkaRuntimeConfigProducer#PRODUCER_CONFIG_PREFIX} are present.
     */
    @Test
    public void testThatProducerConfigIsPresent() {
        assertThat(kafkaProducerConfig.getProducerConfig("test").get("producer.property")).isEqualTo("producer");
    }

    /**
     * Asserts that the properties' keys are converted to lowercase.
     */
    @Test
    public void testThatKeysAreLowercase() {
        assertThat(kafkaProducerConfig.getProducerConfig("test").get("foo")).isEqualTo("bar");
    }

    /**
     * Asserts that compound keys that contain dots are added and not changed.
     */
    @Test
    public void testThatKeysWithDotsAreAllowed() {
        assertThat(kafkaProducerConfig.getProducerConfig("test").get("bootstrap.servers"))
                .isEqualTo("example.com:9999");
    }

    /**
     * Asserts that properties with a numeric value added as strings.
     */
    @Test
    public void testThatNumberArePresentAsStrings() {
        assertThat(kafkaProducerConfig.getProducerConfig("test").get("number")).isEqualTo("123");
    }

}
