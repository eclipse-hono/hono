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

import org.eclipse.hono.client.kafka.consumer.KafkaConsumerOptions;
import org.eclipse.hono.client.kafka.producer.KafkaProducerOptions;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.config.ConfigMapping;

/**
 * Verifies that Quarkus binds properties from yaml files to configuration objects.
 */
@QuarkusTest
public class KafkaClientOptionsTest {

    @Inject
    @ConfigMapping(prefix = "hono.kafka")
    CommonKafkaClientOptions commonOptions;

    @Inject
    @ConfigMapping(prefix = "hono.kafka.producerTest")
    KafkaProducerOptions producerOptions;

    @Inject
    @ConfigMapping(prefix = "hono.kafka.consumerTest")
    KafkaConsumerOptions consumerOptions;

    @Inject
    @ConfigMapping(prefix = "hono.kafka.adminClientTest")
    KafkaAdminClientOptions adminClientOptions;

    /**
     * Asserts that the Kafka configuration is exposed as a beans.
     */
    @Test
    public void testThatClientOptionsExist() {
        assertThat(commonOptions).isNotNull();
        assertThat(producerOptions).isNotNull();
        assertThat(consumerOptions).isNotNull();
        assertThat(adminClientOptions).isNotNull();
    }

    /**
     * Asserts that common client properties are present.
     */
    @Test
    public void testThatCommonConfigIsPresent() {
        assertThat(commonOptions.commonClientConfig().get("common.property").getValue()).isEqualTo("present");
    }

    /**
     * Asserts that consumer properties are present.
     */
    @Test
    public void testThatConsumerConfigIsPresent() {
        assertThat(consumerOptions.consumerConfig().get("consumer.property").getValue()).isEqualTo("consumer");
    }

    /**
     * Asserts that admin client properties are present.
     */
    @Test
    public void testThatAdminClientConfigIsPresent() {
        assertThat(adminClientOptions.adminClientConfig().get("admin.property").getValue()).isEqualTo("admin");
    }

    /**
     * Asserts that producer properties are present.
     */
    @Test
    public void testThatProducerConfigIsPresent() {
        assertThat(producerOptions.producerConfig().get("producer.property").getValue()).isEqualTo("producer");
    }

    /**
     * Asserts that compound keys that contain dots are added and not changed.
     */
    @Test
    public void testThatKeysWithDotsAreAllowed() {
        assertThat(commonOptions.commonClientConfig().get("sasl.client.callback.handler.class").getValue())
                .isEqualTo("Something.class");
    }

    /**
     * Asserts that properties with a numeric value added as strings.
     */
    @Test
    public void testThatNumbersArePresentAsStrings() {
        assertThat(commonOptions.commonClientConfig().get("number").getValue()).isEqualTo("123");
        assertThat(producerOptions.producerConfig().get("number").getValue()).isEqualTo("123");
        assertThat(consumerOptions.consumerConfig().get("number").getValue()).isEqualTo("123");
        assertThat(adminClientOptions.adminClientConfig().get("number").getValue()).isEqualTo("123");
    }

    /**
     * Asserts that properties with an empty string as the value are added.
     */
    @Test
    public void testThatEmptyValuesAreMaintained() {
        assertThat(commonOptions.commonClientConfig().get("empty").getValue()).isEqualTo("");
        assertThat(producerOptions.producerConfig().get("empty").getValue()).isEqualTo("");
        assertThat(consumerOptions.consumerConfig().get("empty").getValue()).isEqualTo("");
        assertThat(adminClientOptions.adminClientConfig().get("empty").getValue()).isEqualTo("");
    }

}
