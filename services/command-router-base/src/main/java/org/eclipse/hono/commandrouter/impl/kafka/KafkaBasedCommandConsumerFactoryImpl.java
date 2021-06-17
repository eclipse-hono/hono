/*******************************************************************************
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
 *******************************************************************************/
package org.eclipse.hono.commandrouter.impl.kafka;

import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.eclipse.hono.client.command.kafka.KafkaBasedCommandResponseSender;
import org.eclipse.hono.client.command.kafka.KafkaBasedInternalCommandSender;
import org.eclipse.hono.client.kafka.HonoTopic;
import org.eclipse.hono.client.kafka.KafkaProducerConfigProperties;
import org.eclipse.hono.client.kafka.KafkaProducerFactory;
import org.eclipse.hono.client.kafka.consumer.AsyncHandlingAutoCommitKafkaConsumer;
import org.eclipse.hono.client.kafka.consumer.KafkaConsumerConfigProperties;
import org.eclipse.hono.client.registry.TenantClient;
import org.eclipse.hono.commandrouter.CommandConsumerFactory;
import org.eclipse.hono.commandrouter.CommandTargetMapper;
import org.eclipse.hono.tracing.TracingHelper;
import org.eclipse.hono.util.MessagingType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.tag.Tags;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.kafka.client.common.impl.Helper;

/**
 * A factory for creating clients for the <em>Kafka messaging infrastructure</em> to receive commands.
 * <p>
 * This factory uses a wild-card based topic pattern to subscribe for commands, which receives the command
 * messages irrespective of the tenants.
 * <p>
 * Command messages are first received by the Kafka consumer on the tenant-specific topic. It is then determined
 * which protocol adapter instance can handle the command. The command is then forwarded to the Kafka cluster on
 * a topic containing that adapter instance id.
 */
public class KafkaBasedCommandConsumerFactoryImpl implements CommandConsumerFactory {
    private static final Logger LOG = LoggerFactory.getLogger(KafkaBasedCommandConsumerFactoryImpl.class);

    private static final Pattern COMMANDS_TOPIC_PATTERN = Pattern
            .compile(Pattern.quote(HonoTopic.Type.COMMAND.prefix) + ".*");

    private final Vertx vertx;
    private final TenantClient tenantClient;
    private final CommandTargetMapper commandTargetMapper;
    private final KafkaConsumerConfigProperties kafkaConsumerConfig;
    private final Tracer tracer;
    private final KafkaBasedInternalCommandSender internalCommandSender;
    private final KafkaBasedCommandResponseSender kafkaBasedCommandResponseSender;

    private KafkaBasedMappingAndDelegatingCommandHandler commandHandler;
    private AsyncHandlingAutoCommitKafkaConsumer kafkaConsumer;

    /**
     * Creates a new factory to process commands via the Kafka cluster.
     *
     * @param vertx The Vert.x instance to use.
     * @param tenantClient The Tenant service client.
     * @param commandTargetMapper The component for mapping an incoming command to the gateway (if applicable) and
     *            protocol adapter instance that can handle it. Note that no initialization of this factory will be done
     *            here, that is supposed to be done by the calling method.
     * @param kafkaProducerFactory The producer factory for creating Kafka producers for sending messages.
     * @param kafkaProducerConfig The Kafka producer configuration.
     * @param kafkaConsumerConfig The Kafka consumer configuration.
     * @param tracer The tracer instance.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    public KafkaBasedCommandConsumerFactoryImpl(
            final Vertx vertx,
            final TenantClient tenantClient,
            final CommandTargetMapper commandTargetMapper,
            final KafkaProducerFactory<String, Buffer> kafkaProducerFactory,
            final KafkaProducerConfigProperties kafkaProducerConfig,
            final KafkaConsumerConfigProperties kafkaConsumerConfig,
            final Tracer tracer) {

        this.vertx = Objects.requireNonNull(vertx);
        this.tenantClient = Objects.requireNonNull(tenantClient);
        this.commandTargetMapper = Objects.requireNonNull(commandTargetMapper);
        Objects.requireNonNull(kafkaProducerFactory);
        Objects.requireNonNull(kafkaProducerConfig);
        this.kafkaConsumerConfig = Objects.requireNonNull(kafkaConsumerConfig);
        this.tracer = Objects.requireNonNull(tracer);

        internalCommandSender = new KafkaBasedInternalCommandSender(kafkaProducerFactory, kafkaProducerConfig, tracer);
        kafkaBasedCommandResponseSender = new KafkaBasedCommandResponseSender(kafkaProducerFactory, kafkaProducerConfig,
                tracer);
    }

    @Override
    public MessagingType getMessagingType() {
        return MessagingType.kafka;
    }

    @Override
    public Future<Void> start() {
        final Context context = Vertx.currentContext();
        if (context == null) {
            return Future.failedFuture(new IllegalStateException("factory must be started in a Vert.x context"));
        }
        final KafkaCommandProcessingQueue commandQueue = new KafkaCommandProcessingQueue(context);
        commandHandler = new KafkaBasedMappingAndDelegatingCommandHandler(tenantClient, commandQueue,
                commandTargetMapper, internalCommandSender, kafkaBasedCommandResponseSender, tracer);

        final Map<String, String> consumerConfig = kafkaConsumerConfig.getConsumerConfig("consumer");
        consumerConfig.put(ConsumerConfig.GROUP_ID_CONFIG, "cmd-router-group");
        kafkaConsumer = new AsyncHandlingAutoCommitKafkaConsumer(vertx, COMMANDS_TOPIC_PATTERN,
                commandHandler::mapAndDelegateIncomingCommandMessage, consumerConfig);
        kafkaConsumer.setOnRebalanceDoneHandler(
                partitions -> commandQueue.setCurrentlyHandledPartitions(Helper.to(partitions)));

        return CompositeFuture.all(commandHandler.start(), kafkaConsumer.start())
                .mapEmpty();
    }

    @Override
    public Future<Void> stop() {
        return CompositeFuture.all(commandHandler.stop(), kafkaConsumer.stop())
                .mapEmpty();
    }

    @Override
    public Future<Void> createCommandConsumer(final String tenantId, final SpanContext context) {
        final String topic = new HonoTopic(HonoTopic.Type.COMMAND, tenantId).toString();
        if (kafkaConsumer.isAmongKnownSubscribedTopics(topic)) {
            LOG.debug("createCommandConsumer: topic is already subscribed [{}]", topic);
            return Future.succeededFuture();
        }
        LOG.debug("createCommandConsumer: topic not subscribed; check for its existence, triggering auto-creation if enabled [{}]", topic);
        final Span span = TracingHelper
                .buildServerChildSpan(tracer, context, "wait for topic subscription update", CommandConsumerFactory.class.getSimpleName())
                .start();
        TracingHelper.TAG_TENANT_ID.set(span, tenantId);
        Tags.MESSAGE_BUS_DESTINATION.set(span, topic);
        return kafkaConsumer.ensureTopicIsAmongSubscribedTopicPatternTopics(topic)
                .onComplete(ar -> {
                    if (ar.failed()) {
                        TracingHelper.logError(span, ar.cause());
                    }
                    span.finish();
                });
    }
}
