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

package org.eclipse.hono.client.kafka.producer;

import java.util.Map;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;

import io.vertx.kafka.client.serialization.BufferSerializer;

/**
 * Configuration properties for Kafka producers used for Hono's messaging.
 *
 * Record keys will be serialized with {@link StringSerializer}, the values with {@link BufferSerializer}.
 *
 * The properties that are required by Hono's messaging APIs are will be set in
 * {@link MessagingKafkaProducerConfigProperties#adaptConfiguration(Map)}.
 *
 * @see <a href="https://kafka.apache.org/documentation/#producerconfigs">Kafka Producer Configs</a>
 * @see <a href="https://www.eclipse.org/hono/docs/api/telemetry-kafka">Telemetry API for Kafka Specification</a>
 * @see <a href="https://www.eclipse.org/hono/docs/api/event-kafka">Event API for Kafka Specification</a>
 * @see <a href="https://www.eclipse.org/hono/docs/api/command-and-control-kafka/">Command &amp; Control API for Kafka Specification</a>
 */
public class MessagingKafkaProducerConfigProperties extends KafkaProducerConfigProperties {

    /**
     * Creates an instance.
     */
    public MessagingKafkaProducerConfigProperties() {
        super(StringSerializer.class, BufferSerializer.class);
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
