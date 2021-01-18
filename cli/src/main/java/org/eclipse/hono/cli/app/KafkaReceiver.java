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

package org.eclipse.hono.cli.app;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.Set;

import javax.annotation.PostConstruct;

import org.eclipse.hono.cli.AbstractCliClient;
import org.eclipse.hono.kafka.client.HonoTopic;
import org.eclipse.hono.kafka.client.consumer.KafkaConsumerConfigProperties;
import org.eclipse.hono.util.EventConstants;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.Strings;
import org.eclipse.hono.util.TelemetryConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.kafka.client.consumer.KafkaConsumer;
import io.vertx.kafka.client.consumer.KafkaConsumerRecord;

/**
 * A command line client for receiving messages from via Hono's north bound Kafka-based Telemetry and/or Event APIs.
 * <p>
 * Messages are output to stdout.
 * <p>
 * Note that this example intentionally does not support Command &amp; Control and rather is the most simple version of
 * a receiver for downstream data.
 */
@Component
@Profile("receiver-kafka")
public class KafkaReceiver extends AbstractCliClient {

    private static final String TYPE_TELEMETRY = "telemetry";
    private static final String TYPE_EVENT = "event";
    private static final String TYPE_ALL = "all";

    @Value(value = "${tenant.id}")
    protected String tenantId;

    /**
     * The type of messages to create a consumer for.
     */
    @Value(value = "${message.type}")
    protected String messageType;

    @Value(value = "${print.verbose:true}")
    protected Boolean isPrintVerbose;

    private KafkaConsumerConfigProperties config;

    @Autowired
    public void setConfig(final KafkaConsumerConfigProperties config) {
        this.config = config;
    }

    /**
     * Starts this component.
     *
     * @return A future indicating the outcome of the startup process.
     */
    @PostConstruct
    Future<Void> start() {
        return createConsumer()
                .onComplete(this::handleCreateConsumerStatus);
    }

    private Future<Void> createConsumer() {

        if (Strings.isNullOrEmpty(tenantId)) {
            return Future.failedFuture("tenant id is not set");
        }

        final Set<String> topics = getTopics();
        if (topics.isEmpty()) {
            return Future.failedFuture(String.format(
                    "Invalid message type [\"%s\"]. Valid types are \"telemetry\", \"event\" or \"all\"", messageType));
        }

        final KafkaConsumer<String, Buffer> consumer = KafkaConsumer.create(vertx, config.getConsumerConfig(),
                String.class, Buffer.class);

        consumer.handler(this::logMessage);
        final Promise<Void> promise = Promise.promise();
        consumer.subscribe(topics, promise);
        return promise.future();
    }

    private Set<String> getTopics() {
        final Set<String> topics = new HashSet<>();
        if (messageType.equals(TYPE_TELEMETRY) || messageType.equals(TYPE_ALL)) {
            topics.add(new HonoTopic(HonoTopic.Type.TELEMETRY, tenantId).toString());
        }

        if (messageType.equals(TYPE_EVENT) || messageType.equals(TYPE_ALL)) {
            topics.add(new HonoTopic(HonoTopic.Type.EVENT, tenantId).toString());
        }
        return topics;
    }

    private void handleCreateConsumerStatus(final AsyncResult<Void> startup) {
        if (startup.succeeded()) {
            log.info("Receiver [tenant: {}, mode: {}] subscribed successfully, hit ctrl-c to exit", tenantId,
                    messageType);
        } else {
            log.error("Error occurred during initialization of receiver: {}", startup.cause().getMessage());
            vertx.close();
        }
    }

    /**
     * Handle received message.
     *
     * Write log messages to stdout.
     *
     * @param record The Kafka consumer record.
     */
    private void logMessage(final KafkaConsumerRecord<String, Buffer> record) {

        if (isPrintVerbose) {
            logVerbosely(record);
        } else {
            logBriefly(record);
        }

    }

    private void logVerbosely(final KafkaConsumerRecord<String, Buffer> record) {
        final long timestamp = record.timestamp();
        final LocalDateTime time = LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault());

        final StringBuilder stringBuilder = new StringBuilder();
        record.headers().forEach(h -> {
            stringBuilder.append("    ");
            stringBuilder.append(h.key());
            stringBuilder.append("=");
            stringBuilder.append(h.value());
            stringBuilder.append("\n");
        });
        final String headers = stringBuilder.toString();

        log.info(
                "topic: {}, partition: {}, offset: {}, timestamp: {} ({})\n  Headers:\n{}  Key:\n    {}\n  Value:\n    {}",
                record.topic(), record.partition(), record.offset(), timestamp, time, headers, record.key(),
                record.value());

    }

    private void logBriefly(final KafkaConsumerRecord<String, Buffer> record) {
        final String endpoint = record.topic().equals(new HonoTopic(HonoTopic.Type.EVENT, tenantId).toString())
                ? EventConstants.EVENT_ENDPOINT
                : TelemetryConstants.TELEMETRY_ENDPOINT;

        final String contentType = record.headers().stream()
                .filter(h -> h.key().equals(MessageHelper.SYS_PROPERTY_CONTENT_TYPE))
                .findAny()
                .map(h -> h.value().toString())
                .orElse("");

        log.info("received {} message [device: {}, content-type: {}]: {}", endpoint, record.key(), contentType,
                record.value());
    }

}
