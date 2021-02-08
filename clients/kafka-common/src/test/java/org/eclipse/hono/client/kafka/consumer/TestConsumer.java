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

package org.eclipse.hono.client.kafka.consumer;

import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.kafka.client.consumer.KafkaConsumer;
import io.vertx.kafka.client.consumer.KafkaConsumerRecord;

/**
 * A {@link AbstractAtLeastOnceKafkaConsumer} implementation for tests.
 */
public class TestConsumer extends AbstractAtLeastOnceKafkaConsumer<JsonObject> {

    public static final String RECORD_VALUE = "value";
    public static final String RECORD_KEY = "key";
    public static final String RECORD_TOPIC = "topic";
    public static final String RECORD_OFFSET = "offset";
    public static final String RECORD_PARTITION = "partition";

    /**
     * Creates a new test consumer with empty message and close handlers.
     *
     * @param kafkaConsumer The Kafka consumer to be used.
     * @param topic The topic to consume from.
     */
    public TestConsumer(final KafkaConsumer<String, Buffer> kafkaConsumer, final String topic) {
        this(kafkaConsumer, topic, m -> {
        }, t -> {
        });
    }

    /**
     * Creates a new test consumer.
     *
     * @param kafkaConsumer The Kafka consumer to be used.
     * @param topic The topic to consume from.
     * @param messageHandler The message handler to set.
     * @param closeHandler The close handler to set.
     */
    public TestConsumer(final KafkaConsumer<String, Buffer> kafkaConsumer, final String topic,
            final Handler<JsonObject> messageHandler, final Handler<Throwable> closeHandler) {
        super(kafkaConsumer, topic, messageHandler, closeHandler, KafkaConsumerConfigProperties.DEFAULT_POLL_TIMEOUT);
    }

    /**
     * Creates a new test consumer with an empty close handler.
     *
     * @param kafkaConsumer The Kafka consumer to be used.
     * @param topic The topic to consume from.
     * @param messageHandler The message handler to set.
     * @return the new test consumer.
     */
    public static TestConsumer createWithMessageHandler(final KafkaConsumer<String, Buffer> kafkaConsumer,
            final String topic, final Handler<JsonObject> messageHandler) {
        return new TestConsumer(kafkaConsumer, topic, messageHandler, cause -> {
        });
    }

    /**
     * Creates a new test consumer with an empty message handler.
     *
     * @param kafkaConsumer The Kafka consumer to be used.
     * @param topic The topic to consume from.
     * @param closeHandler The close handler to set.
     * @return the new test consumer.
     */
    public static TestConsumer createWithCloseHandler(final KafkaConsumer<String, Buffer> kafkaConsumer,
            final String topic, final Handler<Throwable> closeHandler) {
        return new TestConsumer(kafkaConsumer, topic, msg -> {
        }, closeHandler);
    }

    @Override
    protected JsonObject createMessage(final KafkaConsumerRecord<String, Buffer> record) {
        final JsonObject jsonObject = new JsonObject();
        jsonObject.put(RECORD_TOPIC, record.topic());
        jsonObject.put(RECORD_KEY, record.key());
        jsonObject.put(RECORD_VALUE, record.value().toString());
        jsonObject.put(RECORD_OFFSET, record.offset());
        jsonObject.put(RECORD_PARTITION, record.partition());
        return jsonObject;
    }
}
