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

package org.eclipse.hono.application.client.kafka.impl;

import java.util.Objects;

import org.eclipse.hono.application.client.DownstreamMessage;
import org.eclipse.hono.application.client.MessageConsumer;
import org.eclipse.hono.application.client.kafka.KafkaMessageContext;
import org.eclipse.hono.client.ServerErrorException;
import org.eclipse.hono.client.kafka.consumer.AbstractAtLeastOnceKafkaConsumer;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.kafka.client.consumer.KafkaConsumer;
import io.vertx.kafka.client.consumer.KafkaConsumerRecord;

/**
 * A consumer that consumes downstream messages from Kafka.
 * <p>
 * This consumer needs to be closed by invoking {@link #close()} when it is no longer needed.
 */
public abstract class DownstreamMessageConsumer
        extends AbstractAtLeastOnceKafkaConsumer<DownstreamMessage<KafkaMessageContext>> implements MessageConsumer {

    /**
     * Creates a new downstream consumer.
     *
     * @param kafkaConsumer The Kafka consumer to be exclusively used by this instance to consume records.
     * @param topic The Kafka topic to consume records from.
     * @param messageHandler The handler to be invoked for each message created from a record. The handler may throw a
     *            {@link ServerErrorException} to indicate a transient error but should not throw any other exceptions.
     * @param closeHandler The handler to be invoked when the Kafka consumer has been closed due to an error.
     * @param pollTimeout The maximal number of milliseconds to wait for messages during a poll operation.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    protected DownstreamMessageConsumer(final KafkaConsumer<String, Buffer> kafkaConsumer, final String topic,
            final Handler<DownstreamMessage<KafkaMessageContext>> messageHandler, final Handler<Throwable> closeHandler,
            final long pollTimeout) {
        super(kafkaConsumer, topic, messageHandler, closeHandler, pollTimeout);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected DownstreamMessage<KafkaMessageContext> createMessage(final KafkaConsumerRecord<String, Buffer> record) {
        Objects.requireNonNull(record);

        return new KafkaDownstreamMessage(record);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Future<Void> close() {
        return stop();
    }
}
