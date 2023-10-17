/**
 * Copyright (c) 2021, 2023 Contributors to the Eclipse Foundation
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

import java.net.HttpURLConnection;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.eclipse.hono.application.client.DownstreamMessage;
import org.eclipse.hono.application.client.kafka.KafkaCommandSender;
import org.eclipse.hono.application.client.kafka.KafkaMessageContext;
import org.eclipse.hono.client.SendMessageTimeoutException;
import org.eclipse.hono.client.kafka.HonoTopic;
import org.eclipse.hono.client.kafka.KafkaRecordHelper;
import org.eclipse.hono.client.kafka.consumer.HonoKafkaConsumer;
import org.eclipse.hono.client.kafka.consumer.MessagingKafkaConsumerConfigProperties;
import org.eclipse.hono.client.kafka.producer.AbstractKafkaBasedMessageSender;
import org.eclipse.hono.client.kafka.producer.KafkaProducerFactory;
import org.eclipse.hono.client.kafka.producer.MessagingKafkaProducerConfigProperties;
import org.eclipse.hono.client.util.StatusCodeMapper;
import org.eclipse.hono.tracing.TracingHelper;
import org.eclipse.hono.util.MessageHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.tag.Tags;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.kafka.client.consumer.KafkaConsumerRecord;

/**
 * A Kafka based client for sending commands and receiving command responses.
 *
 * @see <a href="https://www.eclipse.org/hono/docs/api/command-and-control-kafka/">
 *      Command &amp; Control API for Kafka Specification</a>
 */
public class KafkaBasedCommandSender extends AbstractKafkaBasedMessageSender<Buffer> implements KafkaCommandSender {

    /**
     * The default number of milliseconds to wait for the Kafka cluster to accept a command message.
     */
    protected static final long DEFAULT_COMMAND_TIMEOUT_IN_MS = 10000;

    private static final Logger LOGGER = LoggerFactory.getLogger(KafkaBasedCommandSender.class);

    private final Vertx vertx;
    private final MessagingKafkaConsumerConfigProperties consumerConfig;
    /**
     * Key is the tenant identifier, value the corresponding consumer for receiving the command responses.
     */
    private final Map<String, HonoKafkaConsumer<Buffer>> commandResponseConsumers = new ConcurrentHashMap<>();
    /**
     * Key is the tenant identifier, value is a map with correlation ids as keys and expiring command promises as values.
     * These correlation ids are used to correlate the response messages with the sent commands.
     */
    private final Map<String, Map<String, ExpiringCommandPromise>> pendingCommandResponses = new ConcurrentHashMap<>();
    private Supplier<Consumer<String, Buffer>> kafkaConsumerSupplier;
    private Supplier<String> correlationIdSupplier = () -> UUID.randomUUID().toString();

    /**
     * Creates a new Kafka-based command sender.
     *
     * @param vertx The vert.x instance to use.
     * @param consumerConfig The Kafka consumer configuration properties to use.
     *                       The value of the config parameter 'auto.offset.reset' should be 'latest'. 
     *                       If any other value is set then it is overridden with 'latest'.
     * @param producerFactory The factory to use for creating Kafka producers.
     * @param producerConfig The Kafka producer configuration properties to use.
     * @param tracer The OpenTracing tracer.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    public KafkaBasedCommandSender(
            final Vertx vertx,
            final MessagingKafkaConsumerConfigProperties consumerConfig,
            final KafkaProducerFactory<String, Buffer> producerFactory,
            final MessagingKafkaProducerConfigProperties producerConfig,
            final Tracer tracer) {
        super(producerFactory, "command-sender", producerConfig, tracer);
        this.vertx = Objects.requireNonNull(vertx);
        this.consumerConfig = Objects.requireNonNull(consumerConfig);
    }

    @Override
    public Future<Void> stop() {

        return lifecycleStatus.runStopAttempt(() -> {
            // assemble futures for closing the command response consumers
            final List<Future<Void>> stopConsumersTracker = commandResponseConsumers.values().stream()
                    .map(HonoKafkaConsumer::stop)
                    .collect(Collectors.toList());
            commandResponseConsumers.clear();
            return Future.join(
                    stopProducer(),
                    Future.join(stopConsumersTracker))
                .mapEmpty();
        });
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * The replyId is not used in the Kafka based implementation. It can be set to {@code null}.
     * If set it will be ignored.
     *
     * @throws NullPointerException if tenantId, deviceId, command or correlationId is {@code null}.
     */
    @Override
    public Future<Void> sendAsyncCommand(
            final String tenantId,
            final String deviceId,
            final String command,
            final String correlationId,
            final String replyId,
            final Buffer data,
            final String contentType,
            final SpanContext context) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(deviceId);
        Objects.requireNonNull(command);
        Objects.requireNonNull(correlationId);

        return sendCommand(tenantId, deviceId, command, correlationId, true, data, contentType, null,
                "send command", context);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Future<Void> sendAsyncCommand(
            final String tenantId,
            final String deviceId,
            final String command,
            final String correlationId,
            final Buffer data,
            final String contentType,
            final Map<String, Object> failureNotificationMetadata,
            final SpanContext context) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(deviceId);
        Objects.requireNonNull(command);
        Objects.requireNonNull(correlationId);

        return sendCommand(tenantId, deviceId, command, correlationId, true, data, contentType,
                failureNotificationMetadata, "send command", context);
    }

    @Override
    public Future<Void> sendOneWayCommand(
            final String tenantId,
            final String deviceId,
            final String command,
            final Buffer data,
            final String contentType,
            final SpanContext context) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(deviceId);
        Objects.requireNonNull(command);

        return sendCommand(tenantId, deviceId, command, null, false, data, contentType, null,
                "send one-way command", context);
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * The replyId is not used in the Kafka based implementation. It can be set to {@code null}.
     * If set it will be ignored.
     * <p>
     * If the timeout duration is {@code null} then the default timeout value of 
     * {@value #DEFAULT_COMMAND_TIMEOUT_IN_MS} ms is used.
     */
    @Override
    public Future<DownstreamMessage<KafkaMessageContext>> sendCommand(
            final String tenantId,
            final String deviceId,
            final String command,
            final Buffer data,
            final String contentType,
            final String replyId,
            final Duration timeout,
            final SpanContext context) {
        return sendCommand(tenantId, deviceId, command, data, contentType, (Map<String, Object>) null, timeout, context);
    }

    @Override
    public Future<DownstreamMessage<KafkaMessageContext>> sendCommand(
            final String tenantId,
            final String deviceId,
            final String command,
            final Buffer data,
            final String contentType,
            final Map<String, Object> failureNotificationMetadata,
            final Duration timeout,
            final SpanContext context) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(deviceId);
        Objects.requireNonNull(command);

        final long timeoutInMs = Optional.ofNullable(timeout)
                .map(t -> {
                    if (t.isNegative()) {
                        throw new IllegalArgumentException("command timeout duration must be >= 0");
                    }
                    return t.toMillis();
                })
                .orElse(DEFAULT_COMMAND_TIMEOUT_IN_MS);

        final String correlationId = correlationIdSupplier.get();
        final Span span = TracingHelper
                .buildChildSpan(tracer, context, "send command and receive response", getClass().getSimpleName())
                .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_CLIENT)
                .withTag(TracingHelper.TAG_TENANT_ID, tenantId)
                .withTag(TracingHelper.TAG_DEVICE_ID, deviceId)
                .withTag(TracingHelper.TAG_CORRELATION_ID, correlationId)
                .start();
        final ExpiringCommandPromise expiringCommandPromise = new ExpiringCommandPromise(
                correlationId,
                timeoutInMs,
                // Remove the corresponding pending response entry if times out
                x -> removePendingCommandResponse(tenantId, correlationId),
                span);

        subscribeForCommandResponse(tenantId, span)
                .compose(ok -> {
                    // Store the correlation id and the expiring command promise
                    pendingCommandResponses.computeIfAbsent(tenantId, k -> new ConcurrentHashMap<>())
                            .put(correlationId, expiringCommandPromise);
                    return sendCommand(tenantId, deviceId, command, correlationId, true, data, contentType,
                            failureNotificationMetadata, "send command", span.context())
                                    .onSuccess(sent -> {
                                        LOGGER.debug("sent command [correlation-id: {}], waiting for response", correlationId);
                                        span.log("sent command, waiting for response");
                                    })
                                    .onFailure(error -> {
                                        LOGGER.debug("error sending command", error);
                                        // To ensure that the span is not already finished.
                                        if (!expiringCommandPromise.future().isComplete()) {
                                            TracingHelper.logError(span, "error sending command", error);
                                        }
                                        removePendingCommandResponse(tenantId, correlationId);
                                        expiringCommandPromise.tryCompleteAndCancelTimer(Future.failedFuture(error));
                                    });
                });

        return expiringCommandPromise.future()
                .onComplete(o -> span.finish());
    }

    /**
     * To be used for unit tests.
     * @param kafkaConsumerSupplier The KafkaConsumer supplier with the configuration as parameter.
     */
    void setKafkaConsumerSupplier(final Supplier<Consumer<String, Buffer>> kafkaConsumerSupplier) {
        this.kafkaConsumerSupplier = Objects.requireNonNull(kafkaConsumerSupplier);
    }

    /**
     * To be used for unit tests.
     * @param correlationIdSupplier The supplier of a correlation id.
     */
    void setCorrelationIdSupplier(final Supplier<String> correlationIdSupplier) {
        this.correlationIdSupplier = Objects.requireNonNull(correlationIdSupplier);
    }

    private Future<Void> sendCommand(
            final String tenantId,
            final String deviceId,
            final String command,
            final String correlationId,
            final boolean responseRequired,
            final Buffer data,
            final String contentType,
            final Map<String, Object> failureNotificationMetadata,
            final String spanOperationName,
            final SpanContext context) {

        final HonoTopic topic = new HonoTopic(HonoTopic.Type.COMMAND, tenantId);
        final Map<String, Object> headerProperties = getHeaderProperties(
                deviceId,
                command,
                contentType,
                correlationId,
                responseRequired,
                failureNotificationMetadata);
        final String topicName = topic.toString();
        final Span currentSpan = startChildSpan(spanOperationName, topicName, tenantId, deviceId, context);
        return sendAndWaitForOutcome(
                topicName,
                tenantId,
                deviceId,
                data,
                headerProperties,
                currentSpan)
            .onComplete(ar -> currentSpan.finish());
    }

    private Map<String, Object> getHeaderProperties(
            final String deviceId,
            final String subject,
            final String contentType,
            final String correlationId,
            final boolean responseRequired,
            final Map<String, Object> failureNotificationMetadata) {

        final Map<String, Object> props = new HashMap<>();

        props.put(MessageHelper.APP_PROPERTY_DEVICE_ID, deviceId);
        props.put(MessageHelper.SYS_PROPERTY_SUBJECT, subject);
        Optional.ofNullable(contentType).ifPresent(ct -> props.put(MessageHelper.SYS_PROPERTY_CONTENT_TYPE, ct));
        Optional.ofNullable(correlationId).ifPresent(id -> props.put(MessageHelper.SYS_PROPERTY_CORRELATION_ID, id));
        props.put(KafkaRecordHelper.HEADER_RESPONSE_REQUIRED, responseRequired);

        if (failureNotificationMetadata != null) {
            failureNotificationMetadata.forEach((key, value) -> props.put(
                    String.format("%s.%s", KafkaRecordHelper.DELIVERY_FAILURE_NOTIFICATION_METADATA_PREFIX, key),
                    value));
        }

        return props;
    }

    private Handler<DownstreamMessage<KafkaMessageContext>> getCommandResponseHandler(final String tenantId) {
        return message -> {
            if (message.getCorrelationId() == null) {
                LOGGER.trace("ignoring received command response - no correlation id set [tenant: {}]", tenantId);
                return;
            }
            removePendingCommandResponse(tenantId, message.getCorrelationId())
                    .ifPresentOrElse(expiringCommandPromise -> expiringCommandPromise
                            .tryCompleteAndCancelTimer(mapResponseResult(message)),
                            () -> LOGGER.trace("ignoring received command response - no response pending [tenant: {}, correlation-id: {}]",
                                    tenantId, message.getCorrelationId()));
        };
    }

    /**
     * Maps a command response to a future based on the status property.
     *
     * @param message The received command response.
     * @return The outcome: a succeeded future if the message contains a 2xx status and a failed future otherwise.
     */
    private Future<DownstreamMessage<KafkaMessageContext>> mapResponseResult(
            final DownstreamMessage<KafkaMessageContext> message) {

        final int status = Optional.ofNullable(message.getStatus()).orElseGet(() -> {
            LOGGER.warn("response message has no status code header [tenant ID: {}, device ID: {}, correlation ID: {}]",
                    message.getTenantId(), message.getDeviceId(), message.getCorrelationId());
            return HttpURLConnection.HTTP_INTERNAL_ERROR;
        });

        if (StatusCodeMapper.isSuccessful(status)) {
            return Future.succeededFuture(message);
        } else {
            final var detail = Optional.ofNullable(message.getPayload())
                    .filter(b -> b.length() > 0)
                    .map(Buffer::toString)
                    .orElse(null);
            return Future.failedFuture(StatusCodeMapper.from(status, detail));
        }
    }

    private Optional<ExpiringCommandPromise> removePendingCommandResponse(final String tenantId,
            final String correlationId) {
        return Optional.ofNullable(pendingCommandResponses.get(tenantId))
                .map(ids -> ids.remove(correlationId));
    }

    private Future<Void> subscribeForCommandResponse(final String tenantId, final Span span) {
        if (commandResponseConsumers.get(tenantId) != null) {
            LOGGER.debug("command response consumer already exists for tenant [{}]", tenantId);
            span.log("command response consumer already exists");
            return Future.succeededFuture();
        }
        final Map<String, String> consumerConfig = this.consumerConfig
                .getConsumerConfig(HonoTopic.Type.COMMAND_RESPONSE.toString());
        final String autoOffsetResetConfigValue = consumerConfig.get(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG);
        //Ensure that 'auto.offset.reset' is always set to 'latest'.
        if (autoOffsetResetConfigValue != null && !autoOffsetResetConfigValue.equals("latest")) {
            LOGGER.warn("[auto.offset.reset] value is set to other than [latest]. It will be ignored and internally set to [latest]");
        }
        consumerConfig.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
        // Use a unique group-id so that all command responses for this tenant are received by this consumer.
        // Thereby the responses can be correlated with the command that has been sent.
        consumerConfig.put(ConsumerConfig.GROUP_ID_CONFIG, tenantId + "-" + UUID.randomUUID());

        final String topic = new HonoTopic(HonoTopic.Type.COMMAND_RESPONSE, tenantId).toString();
        final Handler<KafkaConsumerRecord<String, Buffer>> recordHandler = record -> {
            getCommandResponseHandler(tenantId)
                    .handle(new KafkaDownstreamMessage(record));
        };
        final Promise<Void> readyTracker = Promise.promise();
        final HonoKafkaConsumer<Buffer> consumer = new HonoKafkaConsumer<>(vertx, Set.of(topic), recordHandler, consumerConfig);
        consumer.setPollTimeout(Duration.ofMillis(this.consumerConfig.getPollTimeout()));
        Optional.ofNullable(kafkaConsumerSupplier)
                .ifPresent(consumer::setKafkaConsumerSupplier);
        consumer.addOnKafkaConsumerReadyHandler(readyTracker);
        return consumer.start()
                .compose(ok -> readyTracker.future())
                .recover(error -> {
                    LOGGER.debug("error creating command response consumer for tenant [{}]", tenantId, error);
                    TracingHelper.logError(span, "error creating command response consumer", error);
                    return Future.failedFuture(error);
                })
                .onSuccess(v -> {
                    LOGGER.debug("created command response consumer for tenant [{}]", tenantId);
                    span.log("created command response consumer");
                    commandResponseConsumers.put(tenantId, consumer);
                });
    }

    /**
     * Wrapped promise with an expiration mechanism, failing the promise after a given time if it has not been completed
     * yet.
     */
    private class ExpiringCommandPromise {
        private final Promise<DownstreamMessage<KafkaMessageContext>> promise = Promise.promise();
        private final Span span;
        private Long timerId;

        /**
         * Starts a timer so that after the given timeout value, this promise shall get failed if not completed already.
         *
         * @param correlationId The identifier to use for correlating a command with its response.
         * @param timeoutInMs The timeout duration in milliseconds to use for the timer.
         *                    If it is set to &lt;= 0 then the promise never times out.
         * @param timeOutHandler The operation to run after this promise got failed as part of a timeout.
         * @param span The active OpenTracing span for this operation. It is not to be closed in this method!
         *             An implementation should log (error) events on this span and it may set tags and use this span as
         *             the parent for any spans created in this method.
         * @throws NullPointerException if the span is {@code null}.
         */
        ExpiringCommandPromise(final String correlationId, final long timeoutInMs, final Handler<Void> timeOutHandler,
                final Span span) {
            Objects.requireNonNull(span);

            this.span = span;
            if (timeoutInMs > 0) {
                timerId = vertx.setTimer(timeoutInMs, id -> {
                    final SendMessageTimeoutException error = new SendMessageTimeoutException(
                            "send command/wait for response timed out after " + timeoutInMs + "ms");
                    timerId = null;
                    LOGGER.debug("cancelling sending command [correlation-id: {}] and waiting for response after {} ms",
                            correlationId, timeoutInMs);
                    TracingHelper.logError(span, error);
                    promise.tryFail(error);
                    Optional.ofNullable(timeOutHandler)
                            .ifPresent(handler -> handler.handle(null));
                });
            }
        }

        /**
         * Completes this promise with the given result and stops the expiration timer.
         *
         * @param commandResponseResult The result that contains the command response to complete this promise with.
         * @throws NullPointerException if the commandResponseResult is {@code null}.
         */
        final void tryCompleteAndCancelTimer(
                final AsyncResult<DownstreamMessage<KafkaMessageContext>> commandResponseResult) {
            Objects.requireNonNull(commandResponseResult);

            Optional.ofNullable(timerId)
                    .ifPresent(vertx::cancelTimer);

            if (commandResponseResult.succeeded()) {
                final String correlationId = Optional.ofNullable(commandResponseResult.result())
                        .map(DownstreamMessage::getCorrelationId)
                        .orElse("");
                LOGGER.trace("received command response [correlation-id: {}]", correlationId);
                span.log("received command response");
                promise.tryComplete(commandResponseResult.result());
            } else {
                promise.tryFail(commandResponseResult.cause());
            }
        }

        /**
         * Returns the future corresponding to this promise.
         *
         * @return the future corresponding to this promise.
         */
        final Future<DownstreamMessage<KafkaMessageContext>> future() {
            return promise.future();
        }
    }
}
