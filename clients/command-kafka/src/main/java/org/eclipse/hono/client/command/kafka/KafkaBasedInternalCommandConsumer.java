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

package org.eclipse.hono.client.command.kafka;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.eclipse.hono.client.NoConsumerException;
import org.eclipse.hono.client.command.CommandAlreadyProcessedException;
import org.eclipse.hono.client.command.CommandContext;
import org.eclipse.hono.client.command.CommandHandlerWrapper;
import org.eclipse.hono.client.command.CommandHandlers;
import org.eclipse.hono.client.command.CommandResponseSender;
import org.eclipse.hono.client.kafka.HonoTopic;
import org.eclipse.hono.client.kafka.KafkaAdminClientConfigProperties;
import org.eclipse.hono.client.kafka.KafkaRecordHelper;
import org.eclipse.hono.client.kafka.consumer.KafkaConsumerConfigProperties;
import org.eclipse.hono.client.kafka.tracing.KafkaTracingHelper;
import org.eclipse.hono.tracing.TracingHelper;
import org.eclipse.hono.util.Lifecycle;
import org.eclipse.hono.util.MessageHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.kafka.client.common.TopicPartition;
import io.vertx.kafka.client.consumer.KafkaConsumer;
import io.vertx.kafka.client.consumer.KafkaConsumerRecord;

/**
 * A Kafka based consumer to receive commands forwarded by the Command Router on the internal command topic.
 *
 */
public class KafkaBasedInternalCommandConsumer implements Lifecycle {

    private static final Logger LOG = LoggerFactory.getLogger(KafkaBasedInternalCommandConsumer.class);

    private static final int NUM_PARTITIONS = 1;
    private static final String CLIENT_NAME = "internal-cmd";

    private final Supplier<KafkaConsumer<String, Buffer>> consumerCreator;
    private final String adapterInstanceId;
    private final CommandHandlers commandHandlers;
    private final Tracer tracer;
    private final CommandResponseSender commandResponseSender;
    private final Admin adminClient;
    /**
     * Key is the tenant id, value is a Map with partition index as key and offset as value.
     */
    private final Map<String, Map<Integer, Long>> lastHandledPartitionOffsetsPerTenant = new HashMap<>();

    private KafkaConsumer<String, Buffer> consumer;
    private Context context;

    /**
     * Creates a consumer.
     *
     * @param vertx The Vert.x instance to use.
     * @param adminClientConfigProperties The Kafka admin client config properties.
     * @param consumerConfigProperties The Kafka consumer config properties.
     * @param commandResponseSender The sender used to send command responses.
     * @param adapterInstanceId The adapter instance id.
     * @param commandHandlers The command handlers to choose from for handling a received command.
     * @param tracer The OpenTracing tracer.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    public KafkaBasedInternalCommandConsumer(
            final Vertx vertx,
            final KafkaAdminClientConfigProperties adminClientConfigProperties,
            final KafkaConsumerConfigProperties consumerConfigProperties,
            final CommandResponseSender commandResponseSender,
            final String adapterInstanceId,
            final CommandHandlers commandHandlers,
            final Tracer tracer) {
        Objects.requireNonNull(vertx);
        Objects.requireNonNull(adminClientConfigProperties);
        Objects.requireNonNull(consumerConfigProperties);
        this.commandResponseSender = Objects.requireNonNull(commandResponseSender);
        this.adapterInstanceId = Objects.requireNonNull(adapterInstanceId);
        this.commandHandlers = Objects.requireNonNull(commandHandlers);
        this.tracer = Objects.requireNonNull(tracer);

        final Map<String, String> adminClientConfig = adminClientConfigProperties.getAdminClientConfig(CLIENT_NAME);
        // Vert.x KafkaAdminClient doesn't support creating topics using the broker default replication factor,
        // therefore use Kafka Admin client directly here
        adminClient = Admin.create(new HashMap<>(adminClientConfig));

        final Map<String, String> consumerConfig = consumerConfigProperties.getConsumerConfig(CLIENT_NAME);
        consumerConfig.put(ConsumerConfig.GROUP_ID_CONFIG, adapterInstanceId);
        // no commits of partition offsets needed - topic only used during lifetime of this consumer
        consumerConfig.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        // there will be no offsets stored for this consumer - so the "auto.offset.reset" config is relevant
        // - set it to "earliest" just in case records have already been published to it
        consumerConfig.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        consumerCreator = () -> KafkaConsumer.create(vertx, consumerConfig, String.class, Buffer.class);
    }

    /**
     * Creates a consumer.
     * <p>
     * To be used for unit tests.
     *
     * @param context The vert.x context to run on.
     * @param kafkaAdminClient The Kafka admin client to use.
     * @param kafkaConsumer The Kafka consumer to use.
     * @param commandResponseSender The sender used to send command responses.
     * @param adapterInstanceId The adapter instance id.
     * @param commandHandlers The command handlers to choose from for handling a received command.
     * @param tracer The OpenTracing tracer.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    KafkaBasedInternalCommandConsumer(
            final Context context,
            final Admin kafkaAdminClient,
            final KafkaConsumer<String, Buffer> kafkaConsumer,
            final CommandResponseSender commandResponseSender,
            final String adapterInstanceId,
            final CommandHandlers commandHandlers,
            final Tracer tracer) {
        this.context = Objects.requireNonNull(context);
        this.adminClient = Objects.requireNonNull(kafkaAdminClient);
        this.consumer = Objects.requireNonNull(kafkaConsumer);
        this.commandResponseSender = Objects.requireNonNull(commandResponseSender);
        this.adapterInstanceId = Objects.requireNonNull(adapterInstanceId);
        this.commandHandlers = Objects.requireNonNull(commandHandlers);
        this.tracer = Objects.requireNonNull(tracer);
        consumerCreator = () -> consumer;
    }

    @Override
    public Future<Void> start() {
        if (context == null) {
            context = Vertx.currentContext();
            if (context == null) {
                return Future.failedFuture(new IllegalStateException("Consumer must be started in a Vert.x context"));
            }
        }
        // create KafkaConsumer here so that it is created in the Vert.x context of the start() method (KafkaConsumer uses vertx.getOrCreateContext())
        consumer = consumerCreator.get();
        // trigger creation of adapter specific topic and consumer
        return createTopic().compose(v -> subscribeToTopic());
    }

    private Future<Void> createTopic() {
        final Promise<Void> promise = Promise.promise();
        final String topicName = getTopicName();

        // create topic with unspecified replication factor - broker "default.replication.factor" should be used
        final NewTopic newTopic = new NewTopic(topicName, Optional.of(NUM_PARTITIONS), Optional.empty());
        adminClient.createTopics(List.of(newTopic))
                .all()
                .whenComplete((v, ex) -> {
                    context.runOnContext(v1 -> Optional.ofNullable(ex).ifPresentOrElse(promise::fail, promise::complete));
                });
        return promise.future()
                .onSuccess(v -> LOG.debug("created topic [{}]", topicName))
                .onFailure(thr -> LOG.error("error creating topic [{}]", topicName, thr));
    }

    private Future<Void> subscribeToTopic() {
        consumer.handler(this::handleCommandMessage);
        consumer.exceptionHandler(thr -> {
            LOG.error("consumer error occurred", thr);
        });
        consumer.partitionsRevokedHandler(this::onPartitionsRevoked);
        final Promise<Void> partitionAssignedPromise = Promise.promise();
        consumer.partitionsAssignedHandler(partitionsSet -> {
            LOG.debug("partitions assigned: {}", partitionsSet);
            partitionAssignedPromise.tryComplete();
        });
        final String topicName = getTopicName();
        final Promise<Void> subscribedPromise = Promise.promise();
        consumer.subscribe(topicName, subscribedPromise);

        return CompositeFuture.all(subscribedPromise.future(), partitionAssignedPromise.future())
                .map((Void) null)
                .onComplete(ar -> consumer.partitionsAssignedHandler(this::onPartitionsAssigned))
                .onSuccess(v -> LOG.debug("subscribed and got partition assignment for topic [{}]", topicName))
                .onFailure(thr -> LOG.error("error subscribing to topic [{}]", topicName, thr));
    }

    private void onPartitionsAssigned(final Set<TopicPartition> partitionsSet) {
        LOG.debug("partitions assigned: {}", partitionsSet);
    }

    private void onPartitionsRevoked(final Set<TopicPartition> partitionsSet) {
        LOG.debug("partitions revoked: {}", partitionsSet);
    }

    private String getTopicName() {
        return new HonoTopic(HonoTopic.Type.COMMAND_INTERNAL, adapterInstanceId).toString();
    }

    @Override
    public Future<Void> stop() {
        if (consumer == null) {
            return Future.failedFuture("not started");
        }
        final String topicName = getTopicName();
        final Promise<Void> adminClientClosePromise = Promise.promise();
        LOG.debug("stop: delete topic [{}]", topicName);
        adminClient.deleteTopics(List.of(topicName))
                .all()
                .whenComplete((v, ex) -> {
                    if (ex != null) {
                        LOG.warn("error deleting topic [{}]", topicName, ex);
                    }
                    context.executeBlocking(future -> {
                        adminClient.close();
                        future.complete();
                    }, adminClientClosePromise);
                });
        adminClientClosePromise.future().onComplete(ar -> LOG.debug("admin client closed"));

        final Promise<Void> consumerClosePromise = Promise.promise();
        LOG.debug("stop: close consumer");
        consumer.close(consumerClosePromise);
        consumerClosePromise.future().onComplete(ar -> LOG.debug("consumer closed"));
        return CompositeFuture.all(adminClientClosePromise.future(), consumerClosePromise.future())
                .mapEmpty();
    }

    void handleCommandMessage(final KafkaConsumerRecord<String, Buffer> record) {

        // get partition/offset of the command record - related to the tenant-based topic the command was originally received in
        final Integer commandPartition = KafkaRecordHelper
                .getHeaderValue(record.headers(), KafkaRecordHelper.HEADER_ORIGINAL_PARTITION, Integer.class)
                .orElse(null);
        final Long commandOffset = KafkaRecordHelper
                .getHeaderValue(record.headers(), KafkaRecordHelper.HEADER_ORIGINAL_OFFSET, Long.class)
                .orElse(null);
        if (commandPartition == null || commandOffset == null) {
            LOG.warn("command record is invalid - missing required original partition/offset headers");
            return;
        }

        final KafkaBasedCommand command;
        try {
            command = KafkaBasedCommand.fromRoutedCommandRecord(record);
        } catch (final IllegalArgumentException e) {
            LOG.warn("command record is invalid [tenant-id: {}, device-id: {}]",
                    KafkaRecordHelper
                            .getHeaderValue(record.headers(), MessageHelper.APP_PROPERTY_TENANT_ID, String.class)
                            .orElse(""),
                    KafkaRecordHelper
                            .getHeaderValue(record.headers(), MessageHelper.APP_PROPERTY_DEVICE_ID, String.class)
                            .orElse(""),
                    e);
            return;
        }
        final CommandHandlerWrapper commandHandler = commandHandlers.getCommandHandler(command.getTenant(),
                command.getGatewayOrDeviceId());
        if (commandHandler != null && commandHandler.getGatewayId() != null) {
            // Gateway information set in command handler means a gateway has subscribed for commands for a specific device.
            // This information isn't getting set in the record (by the Command Router) and therefore has to be adopted manually here.
            command.setGatewayId(commandHandler.getGatewayId());
        }

        final SpanContext spanContext = KafkaTracingHelper.extractSpanContext(tracer, record);
        final Span currentSpan = CommandContext.createSpan(tracer, command, spanContext);
        currentSpan.setTag(MessageHelper.APP_PROPERTY_ADAPTER_INSTANCE_ID, adapterInstanceId);

        final CommandContext commandContext = new KafkaBasedCommandContext(command, currentSpan, commandResponseSender);

        if (commandHandler != null) {
            // partition index and offset here are related to the *tenant-based* topic the command was originally received in
            // therefore they are stored with the tenant as key
            final Map<Integer, Long> lastHandledPartitionOffsets = lastHandledPartitionOffsetsPerTenant
                    .computeIfAbsent(command.getTenant(), k -> new HashMap<>());
            final Long lastHandledOffset = lastHandledPartitionOffsets.get(commandPartition);
            if (lastHandledOffset != null && commandOffset <= lastHandledOffset) {
                LOG.debug("ignoring command - record partition offset {} <= last handled offset {} [{}]", commandOffset,
                        lastHandledOffset, command);
                TracingHelper.logError(currentSpan, "command record already handled before");
                commandContext.release(new CommandAlreadyProcessedException());
            } else {
                lastHandledPartitionOffsets.put(commandPartition, commandOffset);
                LOG.trace("using [{}] for received command [{}]", commandHandler, command);
                // command.isValid() check not done here - it is to be done in the command handler
                commandHandler.handleCommand(commandContext);
            }
        } else {
            LOG.info("no command handler found for command [{}]", command);
            commandContext.release(new NoConsumerException("no command handler found for command"));
        }
    }

}
