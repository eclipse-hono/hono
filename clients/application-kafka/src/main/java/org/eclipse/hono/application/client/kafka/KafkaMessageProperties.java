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

package org.eclipse.hono.application.client.kafka;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.eclipse.hono.application.client.MessageProperties;
import org.eclipse.hono.client.kafka.KafkaRecordHelper;

import io.vertx.core.buffer.Buffer;
import io.vertx.kafka.client.consumer.KafkaConsumerRecord;

/**
 * The metadata of a Kafka Message created from a {@link KafkaConsumerRecord}.
 */
public class KafkaMessageProperties implements MessageProperties {

    private final Map<String, Object> properties = new HashMap<>();

    /**
     * Creates message properties from a Kafka consumer record.
     *
     * @param record The consumer record to extract the metadata from.
     * @throws NullPointerException if record is {@code null}.
     */
    public KafkaMessageProperties(final KafkaConsumerRecord<String, Buffer> record) {
        Objects.requireNonNull(record);

        record.headers().forEach(header -> properties.put(header.key(), header.value()));
    }

    /**
     * {@inheritDoc}
     * <p>
     * The values in the map are of type {@link Buffer}.
     *
     * @return An unmodifiable map containing the headers of the {@link KafkaConsumerRecord}.
     */
    @Override
    public final Map<String, Object> getPropertiesMap() {
        return Collections.unmodifiableMap(properties);
    }

    /**
     * {@inheritDoc}
     * <p>
     * The values in the map are of type {@link Buffer}.
     */
    @Override
    public final <T> T getProperty(final String name, final Class<T> type) {
        return Optional.ofNullable(properties.get(name))
                .filter(Buffer.class::isInstance)
                .map(Buffer.class::cast)
                .map(value -> KafkaRecordHelper.decode(value, type))
                .orElse(null);
    }

}
