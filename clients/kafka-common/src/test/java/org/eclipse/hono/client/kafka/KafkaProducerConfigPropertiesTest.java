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

package org.eclipse.hono.client.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * Verifies the behavior of {@link KafkaProducerConfigProperties}.
 */
public class KafkaProducerConfigPropertiesTest {

    static final String PROPERTY_FILE_CLIENT_ID = "target/test-classes/clientid.properties";
    static final String PROPERTY_FILE_COMMON = "target/test-classes/common.properties";
    static final String PROPERTY_FILE_PRODUCER = "target/test-classes/producer.properties";

    /**
     * Verifies that properties returned by {@link KafkaProducerConfigProperties#getProducerConfig(String)} contain
     * the predefined entries, overriding any corresponding properties defined in property files.
     */
    @Test
    public void testThatGetProducerConfigReturnsAdaptedConfig() {

        final KafkaProducerConfigProperties config = new KafkaProducerConfigProperties();
        config.setPropertyFiles(List.of(PROPERTY_FILE_PRODUCER));

        final Map<String, String> producerConfig = config.getProducerConfig("producerName");

        assertThat(producerConfig.get("key.serializer"))
                .isEqualTo("org.apache.kafka.common.serialization.StringSerializer");
        assertThat(producerConfig.get("value.serializer"))
                .isEqualTo("io.vertx.kafka.client.serialization.BufferSerializer");
        assertThat(producerConfig.get("enable.idempotence")).isEqualTo("true");
    }

    /**
     * Verifies that the client ID set with {@link KafkaAdminClientConfigProperties#setDefaultClientIdPrefix(String)} is applied when it
     * is NOT present in the configuration.
     */
    @Test
    public void testThatClientIdIsApplied() {
        final String clientId = "the-client";

        final KafkaProducerConfigProperties config = new KafkaProducerConfigProperties();
        config.setDefaultClientIdPrefix(clientId);

        final Map<String, String> producerConfig = config.getProducerConfig("producerName");
        assertThat(producerConfig.get("client.id")).startsWith(clientId + "-producerName-");
    }

    /**
     * Verifies that the client ID set with {@link KafkaAdminClientConfigProperties#setDefaultClientIdPrefix(String)} is NOT applied
     * when it is present in the configuration.
     */
    @Test
    public void testThatClientIdIsNotAppliedIfAlreadyPresent() {

        final KafkaProducerConfigProperties config = new KafkaProducerConfigProperties();
        config.setPropertyFiles(List.of(PROPERTY_FILE_CLIENT_ID));
        config.setDefaultClientIdPrefix("other-client");

        final Map<String, String> producerConfig = config.getProducerConfig("producerName");
        assertThat(producerConfig.get("client.id")).startsWith("configured-id-producerName-");
    }
}
