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

package org.eclipse.hono.client.notification;

import java.util.Map;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.eclipse.hono.client.kafka.CommonKafkaClientOptions;
import org.eclipse.hono.client.kafka.producer.KafkaProducerConfigProperties;
import org.eclipse.hono.client.kafka.producer.KafkaProducerOptions;

import io.vertx.kafka.client.serialization.JsonObjectSerializer;

/**
 * Configuration properties for Kafka producers used for Hono's notifications.
 *
 * Record keys will be serialized with {@link StringSerializer}, the values with {@link JsonObjectSerializer}.
 */
public class NotificationKafkaProducerConfigProperties extends KafkaProducerConfigProperties {

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
     * Sets the required properties.
     *
     * The following properties are set here to the given configuration:
     * <ul>
     * <li>{@code enable.idempotence=true}: enables idempotent producer behavior</li>
     * </ul>
     *
     * @see <a href="https://kafka.apache.org/documentation/#enable.idempotence">The Kafka documentation -
     *      "Producer Configs" - enable.idempotence</a>
     */
    @Override
    protected final void adaptConfiguration(final Map<String, String> config) {
        overrideConfigProperty(config, ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, "true");
    }

}
