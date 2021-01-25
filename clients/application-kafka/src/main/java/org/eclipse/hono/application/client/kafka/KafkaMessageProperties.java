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

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.eclipse.hono.application.client.Message;
import org.eclipse.hono.application.client.MessageProperties;

import io.vertx.core.buffer.Buffer;

/**
 * The metadata of a {@link Message} created from a {@link ConsumerRecord}.
 */
public class KafkaMessageProperties implements MessageProperties {

    private final Map<String, Object> properties = new HashMap<>();

    /**
     * Creates message properties from a Kafka consumer record.
     *
     * @param record The consumer record to extract the metadata from.
     * @throws NullPointerException if record is {@code null}.
     */
    public KafkaMessageProperties(final ConsumerRecord<String, Buffer> record) {
        Objects.requireNonNull(record);

        record.headers().forEach(header -> properties.put(header.key(), header.value()));
    }

    /**
     * {@inheritDoc}
     *
     * @return The headers of the {@link ConsumerRecord}.
     */
    @Override
    public final Map<String, Object> getPropertiesMap() {
        return properties;
    }

}
