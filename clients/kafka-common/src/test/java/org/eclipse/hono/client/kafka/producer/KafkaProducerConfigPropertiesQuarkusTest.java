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

package org.eclipse.hono.client.kafka.producer;

import static com.google.common.truth.Truth.assertThat;

import javax.inject.Inject;

import org.apache.kafka.common.serialization.StringSerializer;
import org.eclipse.hono.client.kafka.CommonKafkaClientOptions;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.config.ConfigMapping;

/**
 * Verifies the creation of {@link KafkaProducerConfigProperties} from {@link KafkaProducerOptions}.
 *
 */
@QuarkusTest
public class KafkaProducerConfigPropertiesQuarkusTest {

    private KafkaProducerConfigProperties commandResponseConfig;
    private KafkaProducerConfigProperties commandInternalConfig;

    @Inject
    void setCommandResponseClientOptions(
            @ConfigMapping(prefix = "hono.kafka")
            final CommonKafkaClientOptions commonOptions,
            @ConfigMapping(prefix = "hono.kafka.producerTest")
            final KafkaProducerOptions commandResponseProducerOptions) {

        commandResponseConfig = new KafkaProducerConfigProperties(
                StringSerializer.class,
                StringSerializer.class,
                commonOptions,
                commandResponseProducerOptions);
    }

    @Inject
    void setCommandInternalClientOptions(
            @ConfigMapping(prefix = "hono.kafka")
            final CommonKafkaClientOptions commonOptions,
            @ConfigMapping(prefix = "hono.kafka.commandInternal")
            final KafkaProducerOptions commandInternalProducerOptions) {

        commandInternalConfig = new KafkaProducerConfigProperties(
                StringSerializer.class,
                StringSerializer.class,
                commonOptions,
                commandInternalProducerOptions);
    }

    /**
     * Asserts that common client properties are present.
     */
    @Test
    public void testThatCommonConfigIsPresent() {
        assertThat(commandResponseConfig.getProducerConfig("test").get("common.property")).isEqualTo("present");
    }

    /**
     * Asserts that command internal producer properties are present.
     */
    @Test
    public void testThatCommandInternalProducerConfigIsPresent() {
        assertThat(commandInternalConfig.getProducerConfig("one")).isNotNull();
    }

    /**
     * Asserts that producer properties are present.
     */
    @Test
    public void testThatProducerConfigIsPresent() {
        assertThat(commandResponseConfig.getProducerConfig("test").get("producer.property")).isEqualTo("producer");
        assertThat(commandInternalConfig.getProducerConfig("one")).isNotNull();
    }

    /**
     * Asserts that properties with a numeric value added as strings.
     */
    @Test
    public void testThatNumbersArePresentAsStrings() {
        assertThat(commandResponseConfig.getProducerConfig("test").get("number")).isEqualTo("123");
    }

    /**
     * Asserts that properties with an empty string as the value are added.
     */
    @Test
    public void testThatEmptyValuesAreMaintained() {
        assertThat(commandResponseConfig.getProducerConfig("test").get("empty")).isEqualTo("");
    }

}
