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
package org.eclipse.hono.application.client.kafka.impl;

import java.net.HttpURLConnection;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.eclipse.hono.application.client.CommandSender;
import org.eclipse.hono.application.client.DownstreamMessage;
import org.eclipse.hono.application.client.MessageConsumer;
import org.eclipse.hono.application.client.kafka.KafkaMessageContext;
import org.eclipse.hono.client.SendMessageTimeoutException;
import org.eclipse.hono.client.StatusCodeMapper;
import org.eclipse.hono.client.kafka.HonoTopic;
import org.eclipse.hono.client.kafka.KafkaProducerConfigProperties;
import org.eclipse.hono.client.kafka.KafkaProducerFactory;
import org.eclipse.hono.client.kafka.KafkaRecordHelper;
import org.eclipse.hono.client.kafka.consumer.KafkaConsumerConfigProperties;
import org.eclipse.hono.client.kafka.producer.AbstractKafkaBasedMessageSender;
import org.eclipse.hono.tracing.TracingHelper;
import org.eclipse.hono.util.MessageHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.tag.Tags;
import io.vertx.core.AsyncResult;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.kafka.client.consumer.KafkaConsumer;

/**
 * A Kafka based client for sending commands and receiving command responses.
 *
 * @see <a href="https://www.eclipse.org/hono/docs/api/command-and-control-kafka/">
 *      Command &amp; Control API for Kafka Specification</a>
 */
public class KafkaBasedCommandSender extends AbstractKafkaBasedMessageSender
        implements CommandSender<KafkaMessageContext> {
    private static final Logger LOGGER = LoggerFactory.getLogger(KafkaBasedCommandSender.class);
    //TODO to make this timeout configurable
    private static final Duration COMMAND_TIMEOUT = Duration.ofSeconds(30);

    private final KafkaConsumerConfigProperties consumerConfig;
    private Function<Handler<DownstreamMessage<KafkaMessageContext>>, Future<MessageConsumer>> commandResponseConsumerFactory;
    // The tenant identifiers are stored as keys in the map. The message consumers which are used to receive command
    // responses for the tenant mentioned in the key are stored as values.
    private final ConcurrentHashMap<String, MessageConsumer> commandResponseSubscriptions = new ConcurrentHashMap<>();
    // The tenant identifiers are stored as keys in the map. The value is an another map with correlation ids as keys
    // and expiring command promises as values. This correlation ids are used to correlate the response messages with 
    // that of the command.
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, ExpiringCommandPromise>> pendingCommandResponses = new ConcurrentHashMap<>();
    private Supplier<String> correlationIdSupplier = () -> UUID.randomUUID().toString();
    private final Vertx vertx;

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
            final KafkaConsumerConfigProperties consumerConfig,
            final KafkaProducerFactory<String, Buffer> producerFactory,
            final KafkaProducerConfigProperties producerConfig,
            final Tracer tracer) {
        super(producerFactory, "command-sender", producerConfig, tracer);
        this.vertx = Objects.requireNonNull(vertx);
        this.consumerConfig = Objects.requireNonNull(consumerConfig);
    }

    @SuppressWarnings("rawtypes")
    @Override
    public Future<Void> stop() {
        final List<Future> closeKafkaClientsTracker = commandResponseSubscriptions
                .keySet()
                .stream()
                .map(commandResponseSubscriptions::remove)
                .filter(Objects::nonNull)
                .map(MessageConsumer::close)
                .collect(Collectors.toList());

        closeKafkaClientsTracker.add(super.stop());

        return CompositeFuture.all(closeKafkaClientsTracker)
                .mapEmpty();
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
            final String contentType,
            final Buffer data,
            final String correlationId,
            final String replyId,
            final Map<String, Object> properties,
            final SpanContext context) {
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(deviceId);
        Objects.requireNonNull(command);
        Objects.requireNonNull(correlationId);

        return sendCommand(tenantId, deviceId, command, contentType, data, correlationId, properties, true, context);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Future<Void> sendOneWayCommand(
            final String tenantId,
            final String deviceId,
            final String command,
            final String contentType,
            final Buffer data,
            final Map<String, Object> properties,
            final SpanContext context) {
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(deviceId);
        Objects.requireNonNull(command);

        return sendCommand(tenantId, deviceId, command, contentType, data, null, properties, false, context);
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * The replyId is not used in the Kafka based implementation. It can be set to {@code null}.
     * If set it will be ignored.
     *
     * @throws NullPointerException if tenantId, deviceId, or command is {@code null}.
     */
    @Override
    public Future<DownstreamMessage<KafkaMessageContext>> sendCommand(
            final String tenantId,
            final String deviceId,
            final String command,
            final String contentType,
            final Buffer data,
            final String replyId,
            final Map<String, Object> properties,
            final SpanContext context) {
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(deviceId);
        Objects.requireNonNull(command);

        final String correlationId = correlationIdSupplier.get();
        final Span span = TracingHelper
                .buildChildSpan(tracer, context, "send command and wait for response", getClass().getSimpleName())
                .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_CLIENT)
                .withTag(TracingHelper.TAG_TENANT_ID, tenantId)
                .withTag(TracingHelper.TAG_DEVICE_ID, deviceId)
                .withTag(TracingHelper.TAG_CORRELATION_ID, correlationId)
                .start();
        final ExpiringCommandPromise expiringCommandPromise = new ExpiringCommandPromise(
                correlationId,
                COMMAND_TIMEOUT,
                // Remove the correlation id if times out
                x -> Optional.ofNullable(pendingCommandResponses.get(tenantId))
                        .ifPresent(ids -> ids.remove(correlationId)),
                span);

        subscribeForCommandResponse(tenantId, span)
                .compose(ok -> sendCommand(tenantId, deviceId, command, contentType, data, correlationId, properties,
                        true, span.context())
                                .onSuccess(sent -> {
                                    //Store the correlation id and the expiring command promise
                                    pendingCommandResponses.computeIfAbsent(tenantId, k -> new ConcurrentHashMap<>())
                                            .put(correlationId, expiringCommandPromise);
                                    LOGGER.debug("sent command [correlation-id: {}] and waiting for response", correlationId);
                                    span.log("sent command and waiting for response");
                                })
                                .onFailure(error -> {
                                    LOGGER.debug("error sending command", error);
                                    TracingHelper.logError(span, "error sending command", error);
                                    expiringCommandPromise.tryCompleteAndCancelTimer(Future.failedFuture(error));
                                }));

        return expiringCommandPromise.future()
                .onComplete(o -> span.finish());
    }

    // visible for testing
    void setCommandResponseConsumerFactory(
            final Function<Handler<DownstreamMessage<KafkaMessageContext>>, Future<MessageConsumer>> commandResponseConsumerFactory) {
        this.commandResponseConsumerFactory = commandResponseConsumerFactory;
    }

    // visible for testing
    void setCorrelationIdSupplier(final Supplier<String> correlationIdSupplier) {
        Objects.requireNonNull(correlationIdSupplier);
        this.correlationIdSupplier = correlationIdSupplier;
    }

    private Future<Void> sendCommand(final String tenantId, final String deviceId, final String command,
            final String contentType, final Buffer data, final String correlationId,
            final Map<String, Object> properties, final boolean responseRequired, final SpanContext context) {

        final HonoTopic topic = new HonoTopic(HonoTopic.Type.COMMAND, tenantId);
        final Map<String, Object> headerProperties = getHeaderProperties(deviceId, command, contentType, correlationId,
                responseRequired, properties);

        return sendAndWaitForOutcome(topic.toString(), tenantId, deviceId, data, headerProperties, context);
    }

    private Map<String, Object> getHeaderProperties(final String deviceId, final String subject,
            final String contentType, final String correlationId, final boolean responseRequired,
            final Map<String, Object> properties) {

        final Map<String, Object> props = Optional.ofNullable(properties)
                .map(HashMap::new)
                .orElseGet(HashMap::new);

        props.put(MessageHelper.APP_PROPERTY_DEVICE_ID, deviceId);
        props.put(MessageHelper.SYS_PROPERTY_SUBJECT, subject);
        props.put(MessageHelper.SYS_PROPERTY_CONTENT_TYPE,
                Objects.nonNull(contentType) ? contentType : MessageHelper.CONTENT_TYPE_OCTET_STREAM);
        Optional.ofNullable(correlationId)
                .ifPresent(id -> props.put(MessageHelper.SYS_PROPERTY_CORRELATION_ID, id));
        props.put(KafkaRecordHelper.HEADER_RESPONSE_REQUIRED, responseRequired);

        return props;
    }

    private Handler<DownstreamMessage<KafkaMessageContext>> getCommandResponseHandler(final String tenantId) {
        return message -> {
            if (message.getCorrelationId() != null) {
                // if the correlation id of the received response matches with that of the command
                Optional.ofNullable(pendingCommandResponses.get(tenantId))
                        .map(ids -> ids.remove(message.getCorrelationId()))
                        .ifPresentOrElse(
                                expiringCommandPromise -> expiringCommandPromise
                                        .tryCompleteAndCancelTimer(mapResponseResult(message)),
                                () -> LOGGER.trace(
                                        "ignoring received command response [correlation-id: {}] as there is no matching correlation id found",
                                        message.getCorrelationId()));
            }
        };
    }

    /**
     * Maps the status according to
     * {@link CommandSender#sendCommand(String, String, String, String, Buffer, String, Map, SpanContext)}.
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
            return Future.failedFuture(StatusCodeMapper.from(status, null));
        }
    }

    private Future<Void> subscribeForCommandResponse(final String tenantId, final Span span) {
        final Handler<DownstreamMessage<KafkaMessageContext>> messageHandler = getCommandResponseHandler(tenantId);

        if (commandResponseSubscriptions.get(tenantId) != null) {
            LOGGER.debug("command response consumer already exists for tenant [{}]", tenantId);
            span.log("command response consumer already exists");
            return Future.succeededFuture();
        } else {
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

            return Optional.ofNullable(commandResponseConsumerFactory)
                    .map(t -> t.apply(messageHandler))
                    .orElseGet(
                            () -> KafkaBasedDownstreamMessageConsumer.create(
                                    tenantId,
                                    HonoTopic.Type.COMMAND_RESPONSE,
                                    KafkaConsumer.create(vertx, consumerConfig),
                                    this.consumerConfig,
                                    messageHandler,
                                    t -> LOGGER.debug("closed consumer for tenant [{}]", tenantId)))
                    .recover(error -> {
                        LOGGER.debug("error creating command response consumer for tenant [{}]", tenantId, error);
                        TracingHelper.logError(span, "error creating command response consumer", error);
                        return Future.failedFuture(error);
                    })
                    .map(consumer -> {
                        LOGGER.debug("created command response consumer for tenant [{}]", tenantId);
                        span.log("created command response consumer");
                        commandResponseSubscriptions.put(tenantId, consumer);
                        return null;
                    });
        }
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
         * @param timeout The timeout duration to use for the timer.
         * @param timeOutHandler The operation to run after this promise got failed as part of a timeout.
         * @param span The active OpenTracing span for this operation. It is not to be closed in this method!
         *             An implementation should log (error) events on this span and it may set tags and use this span as
         *             the parent for any spans created in this method.
         * @throws NullPointerException if the timeout or span is {@code null}.
         */
        ExpiringCommandPromise(final String correlationId, final Duration timeout, final Handler<Void> timeOutHandler,
                final Span span) {
            Objects.requireNonNull(timeout);
            Objects.requireNonNull(span);

            this.span = span;
            timerId = vertx.setTimer(timeout.toMillis(), id -> {
                final SendMessageTimeoutException error = new SendMessageTimeoutException(
                        "send command/wait for response timed out after " + timeout + "ms");
                timerId = null;
                LOGGER.debug("cancelling sending command [correlation-id: {}] and waiting for response after {} ms",
                        correlationId, timeout);
                TracingHelper.logError(span, error);
                promise.tryFail(error);
                Optional.ofNullable(timeOutHandler)
                        .ifPresent(handler -> handler.handle(null));
            });
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
