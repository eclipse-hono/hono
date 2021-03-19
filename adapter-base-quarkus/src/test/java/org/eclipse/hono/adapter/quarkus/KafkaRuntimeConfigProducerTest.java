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

import javax.inject.Inject;

import org.eclipse.hono.client.kafka.KafkaAdminClientConfigProperties;
import org.eclipse.hono.client.kafka.KafkaProducerConfigProperties;
import org.eclipse.hono.client.kafka.consumer.KafkaConsumerConfigProperties;
import org.junit.jupiter.api.Assertions;
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
     * Asserts that the Kafka configuration is exposed as a bean.
     */
    @Test
    public void testThatBeanIsProvided() {
        Assertions.assertNotNull(kafkaProducerConfig);
        Assertions.assertNotNull(kafkaProducerConfig);

        Assertions.assertNotNull(kafkaConsumerConfig);
        Assertions.assertNotNull(kafkaConsumerConfig);

        Assertions.assertNotNull(kafkaAdminClientConfig);
        Assertions.assertNotNull(kafkaAdminClientConfig);
    }

    /**
     * Asserts that properties with prefix {@link KafkaRuntimeConfigProducer#COMMON_CONFIG_PREFIX} are present.
     */
    @Test
    public void testThatCommonConfigIsPresent() {
        Assertions.assertEquals("present", kafkaProducerConfig.getProducerConfig("test").get("common.property"));
    }

    /**
     * Asserts that properties with prefix {@link KafkaRuntimeConfigProducer#CONSUMER_CONFIG_PREFIX} are present.
     */
    @Test
    public void testThatConsumerConfigIsPresent() {
        Assertions.assertEquals("consumer", kafkaConsumerConfig.getConsumerConfig("test").get("consumer.property"));
    }

    /**
     * Asserts that properties with prefix {@link KafkaRuntimeConfigProducer#ADMIN_CLIENT_CONFIG_PREFIX} are present.
     */
    @Test
    public void testThatAdminClientConfigIsPresent() {
        Assertions.assertEquals("admin",
                kafkaAdminClientConfig.getAdminClientConfig("test").get("admin.property"));
    }

    /**
     * Asserts that properties with prefix {@link KafkaRuntimeConfigProducer#PRODUCER_CONFIG_PREFIX} are present.
     */
    @Test
    public void testThatProducerConfigIsPresent() {
        Assertions.assertEquals("producer", kafkaProducerConfig.getProducerConfig("test").get("producer.property"));
    }

    /**
     * Asserts that the properties' keys are converted to lowercase.
     */
    @Test
    public void testThatKeysAreLowercase() {
        Assertions.assertEquals("bar", kafkaProducerConfig.getProducerConfig("test").get("foo"));
    }

    /**
     * Asserts that compound keys that contain dots are added and not changed.
     */
    @Test
    public void testThatKeysWithDotsAreAllowed() {
        Assertions.assertEquals("example.com:9999",
                kafkaProducerConfig.getProducerConfig("test").get("bootstrap.servers"));
    }

    /**
     * Asserts that properties with a numeric value added as strings.
     */
    @Test
    public void testThatNumberArePresentAsStrings() {
        Assertions.assertEquals("123", kafkaProducerConfig.getProducerConfig("test").get("number"));
    }

}
