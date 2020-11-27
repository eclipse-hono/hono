/*******************************************************************************
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.hono.commandrouter.impl.kafka;

import java.net.HttpURLConnection;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.eclipse.hono.adapter.client.telemetry.kafka.AbstractKafkaBasedMessageSender;
import org.eclipse.hono.client.CommandTargetMapper;
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.commandrouter.CommandConsumerFactory;
import org.eclipse.hono.kafka.client.HonoTopic;
import org.eclipse.hono.kafka.client.KafkaConsumerConfigProperties;
import org.eclipse.hono.kafka.client.KafkaConsumerFactory;
import org.eclipse.hono.kafka.client.KafkaProducerConfigProperties;
import org.eclipse.hono.kafka.client.KafkaProducerFactory;
import org.eclipse.hono.tracing.TracingHelper;
import org.eclipse.hono.util.CommandConstants;
import org.eclipse.hono.util.DeviceConnectionConstants;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.QoS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.tag.Tags;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.kafka.client.consumer.KafkaConsumerRecord;

/**
 * A factory for creating clients for the <em>Kafka messaging infrastructure</em> to receive commands.
 * <p>
 * The factory uses tenant-scoped topics, created (if not already existing for the tenant) when
 * {@link CommandConsumerFactory#createCommandConsumer(String, SpanContext)} is invoked.
 * <p>
 * Command messages are first received on the tenant-scoped topic. It is then determined which protocol adapter instance
 * can handle the command. The command is then forwarded to the Kafka cluster on an topic containing that adapter
 * instance id.
 */
public class KafkaBasedCommandConsumerFactoryImpl extends AbstractKafkaBasedMessageSender
        implements CommandConsumerFactory {

    /**
     * A logger to be shared with subclasses.
     */
    // TODO the topic should follow the hono topic convention hono.command.<tenant-id> and check how HonoTopic can be
    // adapted for that
    private static final Pattern topicPatternForIncomingCommands = Pattern.compile(Pattern.quote(
            "hono" + HonoTopic.getSeparator() + CommandConstants.COMMAND_ENDPOINT + HonoTopic.getSeparator()) + ".*");
    protected final Logger log = LoggerFactory.getLogger(getClass());
    private final String componentName;
    private final KafkaConsumerFactory<String, Buffer> kafkaConsumerFactory;
    private final KafkaConsumerConfigProperties kafkaConsumerConfig;
    private CommandTargetMapper commandTargetMapper;

    /**
     * Creates a new factory to process commands via the Kafka cluster.
     *
     * @param componentName Then name of the component.
     * @param kafkaProducerFactory The producer factory for creating Kafka producers for sending messages.
     * @param kafkaProducerConfig The Kafka producer configuration.
     * @param kafkaConsumerFactory The consumer factory for creating Kafka consumers for consuming messages.
     * @param kafkaConsumerConfig The Kafka Consumer configuration.
     * @param tracer The tracer instance.
     */
    public KafkaBasedCommandConsumerFactoryImpl(
            final String componentName,
            final KafkaProducerFactory<String, Buffer> kafkaProducerFactory,
            final KafkaProducerConfigProperties kafkaProducerConfig,
            final KafkaConsumerFactory<String, Buffer> kafkaConsumerFactory,
            final KafkaConsumerConfigProperties kafkaConsumerConfig,
            final Tracer tracer) {
        super(kafkaProducerFactory, componentName, kafkaProducerConfig.getProducerConfig(), tracer);
        this.componentName = componentName;
        this.kafkaConsumerFactory = Objects.requireNonNull(kafkaConsumerFactory);
        this.kafkaConsumerConfig = Objects.requireNonNull(kafkaConsumerConfig);
    }

    @Override
    public void initialize(final CommandTargetMapper commandTargetMapper) {
        this.commandTargetMapper = Objects.requireNonNull(commandTargetMapper);
    }

    @Override
    public Future<Void> createCommandConsumer(final String tenantId, final SpanContext spanContext) {
        // The consumer is created already in start method as it uses topic pattern (irrespective of tenant-id) to
        // subscribe.
        return Future.succeededFuture();
    }

    @Override
    public Future<Void> start() {
        final Promise<Void> subscriptionPromise = Promise.promise();
        final Map<String, String> consumerConfig = kafkaConsumerConfig.getConsumerConfig();

        consumerConfig.computeIfAbsent(ConsumerConfig.GROUP_ID_CONFIG, k -> componentName);
        kafkaConsumerFactory.getOrCreateConsumer(componentName, consumerConfig)
                .subscribe(topicPatternForIncomingCommands, subscriptionPromise)
                .handler(this::mapAndDelegateIncomingCommandMessage);

        return subscriptionPromise.future()
                .map(ok -> {
                    log.info("Kafka consumer subscribed to the topic [{}]", topicPatternForIncomingCommands);
                    return null;
                });
    }

    @Override
    public Future<Void> stop() {
        return CompositeFuture.all(super.stop(), kafkaConsumerFactory.closeConsumer(componentName))
                .mapEmpty();
    }

    private void mapAndDelegateIncomingCommandMessage(final KafkaConsumerRecord<String, Buffer> incomingCommandRecord) {
        Objects.requireNonNull(incomingCommandRecord);

        final Command command = Command.from(incomingCommandRecord);
        final Span currentSpan = tracer.buildSpan("map and delegate command")
                .ignoreActiveSpan()
                .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_SERVER)
                .withTag(TracingHelper.TAG_TENANT_ID, command.getTenantId())
                .withTag(TracingHelper.TAG_DEVICE_ID, command.getDeviceId())
                .start();

        log.debug("map and delegate command [commandTargetMapper: {}]", commandTargetMapper);
        if (!command.isValid()) {
            log.debug("command is invalid");
            TracingHelper.logError(currentSpan, "command is invalid");
            return;
        }

        commandTargetMapper
                .getTargetGatewayAndAdapterInstance(command.getTenantId(), command.getDeviceId(), currentSpan.context())
                .onSuccess(result -> {
                    final String targetAdapterInstanceId = result
                            .getString(DeviceConnectionConstants.FIELD_ADAPTER_INSTANCE_ID);
                    final String targetDeviceId = result.getString(DeviceConnectionConstants.FIELD_PAYLOAD_DEVICE_ID);
                    final String gatewayId = targetDeviceId.equals(command.getDeviceId()) ? null : targetDeviceId;
                    forwardCommand(command, gatewayId, targetAdapterInstanceId, incomingCommandRecord.value(),
                            currentSpan);
                    // TODO to explore options for committing manually based on the QoS once the command is
                    // successfully forwarded
                })
                .onFailure(cause -> {
                    final String errorMsg;
                    if (ServiceInvocationException.extractStatusCode(cause) == HttpURLConnection.HTTP_NOT_FOUND) {
                        errorMsg = "no target adapter instance found for command with device id "
                                + command.getDeviceId();
                    } else {
                        errorMsg = "error getting target gateway and adapter instance for command with device id"
                                + command.getDeviceId();
                    }
                    log.debug(errorMsg, cause);
                    TracingHelper.logError(currentSpan, errorMsg, cause);
                }).onComplete(ok -> currentSpan.finish());
    }

    private Future<Void> forwardCommand(final Command command,
            final String gatewayId,
            final String targetAdapterInstanceId,
            final Buffer commandPayload,
            final Span span) {
        //TODO to make HonoTopic generic and not just mention the second argument as tenantId.
        final HonoTopic topic = new HonoTopic(HonoTopic.Type.COMMAND_INTERNAL, targetAdapterInstanceId);
        final Map<String, Object> msgProperties = Optional.ofNullable(gatewayId)
                .map(id -> Map.<String, Object>of(MessageHelper.APP_PROPERTY_GATEWAY_ID, id))
                .orElse(null);

        log.debug("forwarding a command [tenantId: {}, deviceId: {}, topic: {}]", command.getTenantId(),
                command.getDeviceId(), topic.toString());
        return send(topic, command.getTenantId(), command.getDeviceId(), QoS.AT_LEAST_ONCE, command.getContentType(),
                commandPayload, msgProperties, span.context())
                        .onFailure(cause -> {
                            log.debug("Error forwarding command", cause);
                            TracingHelper.logError(span, "Error forwarding command", cause);
                        });
    }

    private static class Command {
        private String tenantId;
        private String deviceId;
        private final String contentType = "text/plain";
        private boolean valid = false;

        private Command(final KafkaConsumerRecord<String, Buffer> commandRecord) {
            Optional.ofNullable(commandRecord)
                    .ifPresent(command -> {
                        final HonoTopic commandTopic = HonoTopic.fromString(command.topic());
                        if (Objects.nonNull(commandTopic) && Objects.nonNull(commandTopic.getTenantId())) {
                            tenantId = commandTopic.getTenantId();
                        }
                        Optional.ofNullable(commandRecord.key())
                                .ifPresent(id -> deviceId = id);
                        if (Objects.nonNull(deviceId) && Objects.nonNull(tenantId)) {
                            valid = true;
                        }
                    });
            // Check content-type and other required values.
        }

        private static Command from(final KafkaConsumerRecord<String, Buffer> commandRecord) {
            return new Command(commandRecord);
        }

        private String getTenantId() {
            return tenantId;
        }

        private String getDeviceId() {
            return deviceId;
        }

        private String getContentType() {
            return contentType;
        }

        private boolean isValid() {
            return valid;
        }
    }
}
