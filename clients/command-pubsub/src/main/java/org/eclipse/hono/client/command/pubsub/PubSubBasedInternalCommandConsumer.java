/**
 * Copyright (c) 2023 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * https://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.hono.client.command.pubsub;

import java.net.HttpURLConnection;
import java.util.Objects;

import org.eclipse.hono.client.NoConsumerException;
import org.eclipse.hono.client.ServerErrorException;
import org.eclipse.hono.client.command.CommandContext;
import org.eclipse.hono.client.command.CommandHandlerWrapper;
import org.eclipse.hono.client.command.CommandHandlers;
import org.eclipse.hono.client.command.CommandResponseSender;
import org.eclipse.hono.client.command.InternalCommandConsumer;
import org.eclipse.hono.client.pubsub.PubSubBasedAdminClientManager;
import org.eclipse.hono.client.pubsub.PubSubMessageHelper;
import org.eclipse.hono.client.pubsub.subscriber.PubSubSubscriberFactory;
import org.eclipse.hono.client.pubsub.tracing.PubSubTracingHelper;
import org.eclipse.hono.client.registry.TenantClient;
import org.eclipse.hono.tracing.TracingHelper;
import org.eclipse.hono.util.CommandConstants;
import org.eclipse.hono.util.LifecycleStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.api.gax.core.CredentialsProvider;
import com.google.cloud.pubsub.v1.AckReplyConsumer;
import com.google.cloud.pubsub.v1.MessageReceiver;
import com.google.pubsub.v1.PubsubMessage;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.healthchecks.HealthCheckHandler;
import io.vertx.ext.healthchecks.Status;

/**
 * A Pub/Sub based consumer to receive commands forwarded by the Command Router on the internal command topic.
 */
public class PubSubBasedInternalCommandConsumer implements InternalCommandConsumer {

    private static final Logger log = LoggerFactory.getLogger(PubSubBasedInternalCommandConsumer.class);

    private final CommandResponseSender commandResponseSender;
    private final String adapterInstanceId;
    private final CommandHandlers commandHandlers;
    private final TenantClient tenantClient;
    private final Tracer tracer;
    private final PubSubSubscriberFactory subscriberFactory;
    private final LifecycleStatus lifecycleStatus = new LifecycleStatus();
    private final PubSubBasedAdminClientManager adminClientManager;
    private final Vertx vertx;
    private MessageReceiver receiver;

    /**
     * Creates a Pub/Sub based internal command consumer.
     *
     * @param commandResponseSender The sender used to send command responses.
     * @param vertx The Vert.x instance to use.
     * @param adapterInstanceId The adapter instance id.
     * @param commandHandlers The command handlers to choose from for handling a received command.
     * @param tenantClient The client to use for retrieving tenant configuration data.
     * @param tracer The OpenTracing tracer.
     * @param subscriberFactory The subscriber factory for creating Pub/Sub subscribers for receiving messages.
     * @param projectId The identifier of the Google Cloud Project to connect to.
     * @param credentialsProvider The provider for credentials to use for authenticating to the Pub/Sub service.
     * @throws NullPointerException If any of these parameters are {@code null}.
     */
    public PubSubBasedInternalCommandConsumer(
            final CommandResponseSender commandResponseSender,
            final Vertx vertx,
            final String adapterInstanceId,
            final CommandHandlers commandHandlers,
            final TenantClient tenantClient,
            final Tracer tracer,
            final PubSubSubscriberFactory subscriberFactory,
            final String projectId,
            final CredentialsProvider credentialsProvider) {
        Objects.requireNonNull(projectId);
        Objects.requireNonNull(credentialsProvider);
        this.vertx = Objects.requireNonNull(vertx);
        this.commandResponseSender = Objects.requireNonNull(commandResponseSender);
        this.adapterInstanceId = Objects.requireNonNull(adapterInstanceId);
        this.commandHandlers = Objects.requireNonNull(commandHandlers);
        this.tenantClient = Objects.requireNonNull(tenantClient);
        this.tracer = Objects.requireNonNull(tracer);
        this.subscriberFactory = Objects.requireNonNull(subscriberFactory);
        this.adminClientManager = new PubSubBasedAdminClientManager(projectId, credentialsProvider);
        createReceiver();
        adminClientManager
                .getOrCreateTopic(CommandConstants.INTERNAL_COMMAND_ENDPOINT, adapterInstanceId)
                .onFailure(thr -> log.error("Could not create topic for endpoint {} and {}",
                        CommandConstants.INTERNAL_COMMAND_ENDPOINT, adapterInstanceId, thr))
                .compose(t -> adminClientManager.getOrCreateSubscription(CommandConstants.INTERNAL_COMMAND_ENDPOINT, adapterInstanceId))
                .onFailure(thr -> log.error("Could not create subscription for endpoint {} and {}",
                        CommandConstants.INTERNAL_COMMAND_ENDPOINT, adapterInstanceId, thr))
                .onSuccess(s -> subscriberFactory.getOrCreateSubscriber(s, receiver));
    }

    /**
     * Creates a Pub/Sub based internal command consumer. To be used for Unittests.
     *
     * @param commandResponseSender The sender used to send command responses.
     * @param vertx The Vert.x instance to use.
     * @param adapterInstanceId The adapter instance id.
     * @param commandHandlers The command handlers to choose from for handling a received command.
     * @param tenantClient The client to use for retrieving tenant configuration data.
     * @param tracer The OpenTracing tracer.
     * @param subscriberFactory The subscriber factory for creating Pub/Sub subscribers for receiving messages.
     * @param adminClientManager The Pub/Sub based admin client manager to manage topics and subscriptions.
     * @param receiver The message receiver used to process the received message.
     * @throws NullPointerException If any of these parameters are {@code null}.
     */
    public PubSubBasedInternalCommandConsumer(
            final CommandResponseSender commandResponseSender,
            final Vertx vertx,
            final String adapterInstanceId,
            final CommandHandlers commandHandlers,
            final TenantClient tenantClient,
            final Tracer tracer,
            final PubSubSubscriberFactory subscriberFactory,
            final PubSubBasedAdminClientManager adminClientManager,
            final MessageReceiver receiver) {
        this.vertx = Objects.requireNonNull(vertx);
        this.commandResponseSender = Objects.requireNonNull(commandResponseSender);
        this.adapterInstanceId = Objects.requireNonNull(adapterInstanceId);
        this.commandHandlers = Objects.requireNonNull(commandHandlers);
        this.tenantClient = Objects.requireNonNull(tenantClient);
        this.tracer = Objects.requireNonNull(tracer);
        this.subscriberFactory = Objects.requireNonNull(subscriberFactory);
        this.adminClientManager = Objects.requireNonNull(adminClientManager);
        this.receiver = Objects.requireNonNull(receiver);
        adminClientManager
                .getOrCreateTopic(CommandConstants.INTERNAL_COMMAND_ENDPOINT, adapterInstanceId)
                .onFailure(thr -> log.error("Could not create topic for endpoint {} and {}",
                        CommandConstants.INTERNAL_COMMAND_ENDPOINT, adapterInstanceId, thr))
                .compose(t -> adminClientManager.getOrCreateSubscription(CommandConstants.INTERNAL_COMMAND_ENDPOINT, adapterInstanceId))
                .onFailure(thr -> log.error("Could not create subscription for endpoint {} and {}",
                        CommandConstants.INTERNAL_COMMAND_ENDPOINT, adapterInstanceId, thr))
                .onSuccess(s -> subscriberFactory.getOrCreateSubscriber(s, receiver));
    }

    @Override
    public void registerReadinessChecks(final HealthCheckHandler readinessHandler) {
        log.trace("registering readiness check using Pub/Sub based internal command consumer [adapter instance id: {}]",
                adapterInstanceId);
        readinessHandler.register("internal-command-consumer[%s]-readiness".formatted(adapterInstanceId),
                status -> {
                    if (lifecycleStatus.isStarted()) {
                        status.tryComplete(Status.OK());
                    } else {
                        final JsonObject data = new JsonObject();
                        if (lifecycleStatus.isStarting()) {
                            if (subscriberFactory
                                    .getSubscriber(CommandConstants.INTERNAL_COMMAND_ENDPOINT, adapterInstanceId)
                                    .isEmpty()) {
                                log.debug("readiness check failed, subscriber not created yet");
                                data.put("status", "subscriber not created yet");
                            } else {
                                log.debug("readiness check failed");
                            }
                        }
                        status.tryComplete(Status.KO(data));
                    }
                });
    }

    @Override
    public void registerLivenessChecks(final HealthCheckHandler livenessHandler) {
        // no liveness checks to be added
    }

    @Override
    public Future<Void> start() {
        if (lifecycleStatus.isStarting()) {
            return Future.succeededFuture();
        } else if (!lifecycleStatus.setStarting()) {
            return Future.failedFuture(new IllegalStateException("subscriber is already started/stopping"));
        }

        final String subscriptionId = PubSubMessageHelper.getTopicName(
                CommandConstants.INTERNAL_COMMAND_ENDPOINT,
                adapterInstanceId);
        return subscriberFactory
                .getOrCreateSubscriber(subscriptionId, receiver)
                .subscribe(true)
                .onSuccess(s -> lifecycleStatus.setStarted())
                .onFailure(
                        e -> log.warn("Error starting Internal Command Consumer for adapter {}", adapterInstanceId, e));
    }

    private void createReceiver() {
        receiver = (PubsubMessage message, AckReplyConsumer consumer) -> handleCommandMessage(message);
    }

    Future<Void> handleCommandMessage(final PubsubMessage message) {
        final PubSubBasedCommand command;
        try {
            command = PubSubBasedCommand.fromRoutedCommandMessage(message);
        } catch (IllegalArgumentException e) {
            log.warn("Command record is invalid [tenant-id: {}, device-id: {}]",
                    PubSubMessageHelper.getTenantId(message.getAttributesMap()).orElse(null),
                    PubSubMessageHelper.getDeviceId(message.getAttributesMap()).orElse(null), e);
            return Future.failedFuture("invalid command message");
        }

        final CommandHandlerWrapper commandHandler = commandHandlers.getCommandHandler(command.getTenant(),
                command.getGatewayOrDeviceId());
        if (commandHandler != null && commandHandler.getGatewayId() != null) {
            command.setGatewayId(commandHandler.getGatewayId());
        }
        final SpanContext spanContext = PubSubTracingHelper.extractSpanContext(tracer, message);
        final SpanContext followsFromSpanContext = commandHandler != null
                ? commandHandler.getConsumerCreationSpanContext()
                : null;
        final Span currentSpan = CommandContext.createSpan(tracer, command, spanContext, followsFromSpanContext,
                getClass().getSimpleName());
        TracingHelper.TAG_ADAPTER_INSTANCE_ID.set(currentSpan, adapterInstanceId);

        final var commandContext = new PubSubBasedCommandContext(command, commandResponseSender, currentSpan);
        return tenantClient.get(command.getTenant(), null)
                .recover(t -> {
                    log.warn("error retrieving tenant configuration [{}]", command);
                    final var exception = new ServerErrorException(
                            command.getTenant(),
                            HttpURLConnection.HTTP_UNAVAILABLE,
                            "error retrieving tenant configuration",
                            t);
                    commandContext.release(exception);
                    return Future.failedFuture(exception);
                })
                .compose(tenantConfig -> {
                    commandContext.put(CommandContext.KEY_TENANT_CONFIG, tenantConfig);
                    if (commandHandler != null) {
                        log.debug("using [{}] for received command [{}]", commandHandler, command);
                        // command.isValid() check not done here - it is to be done in the command handler
                        return commandHandler.handleCommand(commandContext);
                    } else {
                        log.info("no command handler found for command [{}]", command);
                        final var exception = new NoConsumerException("no command handler found for command");
                        commandContext.release(exception);
                        return Future.failedFuture(exception);
                    }
                });
    }

    @Override
    public Future<Void> stop() {
        return lifecycleStatus.runStopAttempt(() -> CompositeFuture.all(
                subscriberFactory.closeSubscriber(CommandConstants.INTERNAL_COMMAND_ENDPOINT, adapterInstanceId),
                vertx.executeBlocking(promise -> {
                    adminClientManager.closeAdminClients();
                    promise.complete();
                })
        ).mapEmpty());
    }

}
