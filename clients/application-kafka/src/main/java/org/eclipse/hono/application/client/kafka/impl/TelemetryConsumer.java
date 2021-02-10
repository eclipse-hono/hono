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
import org.eclipse.hono.client.kafka.HonoTopic;
import org.eclipse.hono.client.kafka.consumer.KafkaConsumerConfigProperties;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.kafka.client.consumer.KafkaConsumer;

/**
 * A consumer that consumes <em>telemetry</em> messages from Kafka.
 * <p>
 * The telemetry consumer needs to be closed by invoking {@link #close()} ()} when it is no longer needed.
 */
public class TelemetryConsumer extends DownstreamMessageConsumer {

    private TelemetryConsumer(final KafkaConsumer<String, Buffer> kafkaConsumer, final String topic,
            final Handler<DownstreamMessage<KafkaMessageContext>> messageHandler, final Handler<Throwable> closeHandler,
            final long pollTimeout) {

        super(kafkaConsumer, topic, messageHandler, closeHandler, pollTimeout);
    }

    /**
     * Creates and starts a message consumer for telemetry data.
     *
     * @param kafkaConsumer The Kafka consumer to be exclusively used by this instance to consume records.
     * @param config The Kafka consumer configuration properties to use.
     * @param tenantId The tenant to consume telemetry data for.
     * @param messageHandler The handler to be invoked for each message created from a record. The handler may throw a
     *            {@link ServerErrorException} to indicate a transient error but should not throw any other exceptions.
     * @param closeHandler The handler to be invoked when the Kafka consumer has been closed due to an error.
     * @return a future indicating the outcome. When {@link #start()} completes successfully, the future will be
     *         completed with the consumer. Otherwise the future will fail with the cause.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    public static Future<MessageConsumer> create(final KafkaConsumer<String, Buffer> kafkaConsumer,
            final KafkaConsumerConfigProperties config, final String tenantId,
            final Handler<DownstreamMessage<KafkaMessageContext>> messageHandler,
            final Handler<Throwable> closeHandler) {

        Objects.requireNonNull(kafkaConsumer);
        Objects.requireNonNull(config);
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(messageHandler);
        Objects.requireNonNull(closeHandler);

        final String topic = new HonoTopic(HonoTopic.Type.TELEMETRY, tenantId).toString();
        final long pollTimeout = config.getPollTimeout();

        final TelemetryConsumer consumer = new TelemetryConsumer(kafkaConsumer, topic, messageHandler, closeHandler,
                pollTimeout);

        return consumer.start().map(consumer);
    }

}
