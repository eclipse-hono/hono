/**
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
package org.eclipse.hono.tests;

import java.util.List;
import java.util.Map;

import org.eclipse.hono.client.kafka.producer.AbstractKafkaBasedMessageSender;
import org.eclipse.hono.client.kafka.producer.KafkaProducerFactory;
import org.eclipse.hono.client.kafka.producer.MessagingKafkaProducerConfigProperties;

import io.opentracing.noop.NoopSpan;
import io.opentracing.noop.NoopTracerFactory;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.kafka.client.producer.KafkaHeader;

/**
 * A generic Kafka sender.
 */
public class GenericKafkaSender extends AbstractKafkaBasedMessageSender<Buffer> {

    /**
     * Creates a new generic Kafka sender.
     *
     * @param producerFactory The factory to use for creating Kafka producers.
     * @param producerConfig The Kafka producer configuration.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    public GenericKafkaSender(
            final KafkaProducerFactory<String, Buffer> producerFactory,
            final MessagingKafkaProducerConfigProperties producerConfig) {

        super(producerFactory, "generic-sender", producerConfig, NoopTracerFactory.create());
    }

    /**
     * Sends a message to a kafka cluster and waits for the outcome.
     *
     * @param topic      The topic to send the message to.
     * @param tenantId   The tenant that the device belongs to.
     * @param deviceId   The device identifier.
     * @param payload    The data to send.
     * @param properties Additional meta data that should be included in the message.
     * @return A future indicating the outcome of the operation.
     * <p>
     * The future will be succeeded if the message has been sent.
     * <p>
     * The future will be failed with a {@link org.eclipse.hono.client.ServerErrorException} if the data could
     * not be sent. The error code contained in the exception indicates the cause of the failure.
     * @throws NullPointerException if topic, tenantId, deviceId or properties are {@code null}.
     */
    public Future<Void> sendAndWaitForOutcome(
            final String topic,
            final String tenantId,
            final String deviceId,
            final Buffer payload,
            final Map<String, Object> properties) {

        return super.sendAndWaitForOutcome(topic, tenantId, deviceId, payload, properties, NoopSpan.INSTANCE);
    }

    /**
     * Sends a message to a kafka cluster and waits for the outcome.
     *
     * @param topic    The topic to send the message to.
     * @param tenantId The tenant that the device belongs to.
     * @param deviceId The device identifier.
     * @param payload  The data to send.
     * @param headers  Additional meta data that should be included in the message.
     * @return A future indicating the outcome of the operation.
     * <p>
     * The future will be succeeded if the message has been sent.
     * <p>
     * The future will be failed with a {@link org.eclipse.hono.client.ServerErrorException} if the data could
     * not be sent. The error code contained in the exception indicates the cause of the failure.
     * @throws NullPointerException if topic, tenantId, deviceId or headers are {@code null}.
     */
    public Future<Void> sendAndWaitForOutcome(
            final String topic,
            final String tenantId,
            final String deviceId,
            final Buffer payload,
            final List<KafkaHeader> headers) {

        return super.sendAndWaitForOutcome(topic, tenantId, deviceId, payload, headers, NoopSpan.INSTANCE);
    }

}
