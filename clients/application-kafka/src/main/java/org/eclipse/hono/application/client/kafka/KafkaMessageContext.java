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

import java.util.Objects;

import org.eclipse.hono.application.client.MessageContext;

import io.vertx.core.buffer.Buffer;
import io.vertx.kafka.client.consumer.KafkaConsumerRecord;

/**
 * The context of a Kafka message.
 * <p>
 * It provides access to the {@link KafkaConsumerRecord}.
 */
public class KafkaMessageContext implements MessageContext {

    private final KafkaConsumerRecord<String, Buffer> record;

    /**
     * Creates a context.
     *
     * @param record The consumer record from which the message is created.
     * @throws NullPointerException if record is {@code null}.
     */
    public KafkaMessageContext(final KafkaConsumerRecord<String, Buffer> record) {
        Objects.requireNonNull(record);
        this.record = record;
    }

    /**
     * Gets the consumer record from which the message is created.
     *
     * @return The consumer record.
     */
    public final KafkaConsumerRecord<String, Buffer> getRecord() {
        return record;
    }
}
