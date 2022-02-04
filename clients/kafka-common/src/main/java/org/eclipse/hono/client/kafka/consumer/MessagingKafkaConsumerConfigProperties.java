/*
 * Copyright (c) 2020, 2022 Contributors to the Eclipse Foundation
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

import org.apache.kafka.common.serialization.StringDeserializer;
import org.eclipse.hono.client.kafka.CommonKafkaClientOptions;

import io.quarkus.runtime.annotations.RegisterForReflection;
import io.vertx.kafka.client.serialization.BufferDeserializer;

/**
 * Configuration properties for Kafka consumers used for Hono's messaging.
 *
 * Record keys will be deserialized with {@link StringDeserializer}, the values with {@link BufferDeserializer}.
 *
 * @see <a href="https://kafka.apache.org/documentation/#consumerconfigs">Kafka Consumer Configs</a>
 * @see <a href="https://www.eclipse.org/hono/docs/api/telemetry-kafka">Telemetry API for Kafka Specification</a>
 * @see <a href="https://www.eclipse.org/hono/docs/api/event-kafka">Event API for Kafka Specification</a>
 * @see <a href="https://www.eclipse.org/hono/docs/api/command-and-control-kafka/">Command &amp; Control API for Kafka Specification</a>
 */
// When renaming or moving this class, please update it in the documentation
@RegisterForReflection(targets = BufferDeserializer.class)
public class MessagingKafkaConsumerConfigProperties extends KafkaConsumerConfigProperties {

    /**
     * Creates an instance.
     */
    public MessagingKafkaConsumerConfigProperties() {
        super(StringDeserializer.class, BufferDeserializer.class);
    }

    /**
     * Creates properties using existing options.
     *
     * @param commonOptions The common Kafka client options to use.
     * @param options The producer options to use.
     */
    public MessagingKafkaConsumerConfigProperties(final CommonKafkaClientOptions commonOptions,
            final KafkaConsumerOptions options) {
        super(StringDeserializer.class, BufferDeserializer.class, commonOptions, options);
    }

}
