/*
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

package org.eclipse.hono.client.notification.kafka;

import java.util.Map;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.eclipse.hono.client.kafka.CommonKafkaClientOptions;
import org.eclipse.hono.client.kafka.producer.KafkaProducerConfigProperties;
import org.eclipse.hono.client.kafka.producer.KafkaProducerOptions;

import io.quarkus.runtime.annotations.RegisterForReflection;
import io.vertx.kafka.client.serialization.JsonObjectSerializer;

/**
 * Configuration properties for Kafka producers used for Hono's notifications.
 * <p>
 * Record keys will be serialized with {@link StringSerializer}, the values with {@link JsonObjectSerializer}.
 */
@RegisterForReflection(targets = JsonObjectSerializer.class)
public class NotificationKafkaProducerConfigProperties extends KafkaProducerConfigProperties {

    /**
     * Default value for the {@value ProducerConfig#DELIVERY_TIMEOUT_MS_CONFIG} config property.
     */
    public static final String DEFAULT_DELIVERY_TIMEOUT_MS = "2500";
    /**
     * Default value for the {@value ProducerConfig#REQUEST_TIMEOUT_MS_CONFIG} config property.
     */
    public static final String DEFAULT_REQUEST_TIMEOUT_MS = "750";
    /**
     * Default value for the {@value ProducerConfig#MAX_BLOCK_MS_CONFIG} config property.
     */
    public static final String DEFAULT_MAX_BLOCK_MS = "500";

    /**
     * Creates an instance.
     */
    public NotificationKafkaProducerConfigProperties() {
        super(StringSerializer.class, JsonObjectSerializer.class);
    }

    /**
     * Creates properties using existing options.
     *
     * @param commonOptions The common Kafka client options to use.
     * @param options The producer options to use.
     */
    public NotificationKafkaProducerConfigProperties(final CommonKafkaClientOptions commonOptions,
            final KafkaProducerOptions options) {
        super(StringSerializer.class, JsonObjectSerializer.class, commonOptions, options);
    }

    /**
     * Adapts the given configuration, setting required and default values.
     * <p>
     * {@value ProducerConfig#ENABLE_IDEMPOTENCE_CONFIG} is always set to {@code true}, default values
     * are applied for selected timeout properties.
     */
    @Override
    protected final void adaptConfiguration(final Map<String, String> config) {
        // set properties with required values
        overrideConfigProperty(config, ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, "true");

        // set default values
        config.putIfAbsent(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, DEFAULT_DELIVERY_TIMEOUT_MS);
        config.putIfAbsent(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, DEFAULT_REQUEST_TIMEOUT_MS);
        config.putIfAbsent(ProducerConfig.MAX_BLOCK_MS_CONFIG, DEFAULT_MAX_BLOCK_MS);
    }

}
