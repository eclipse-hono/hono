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
package org.eclipse.hono.commandrouter.impl.pubsub;

import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.hono.client.command.pubsub.PubSubBasedCommandResponseSender;
import org.eclipse.hono.client.command.pubsub.PubSubBasedInternalCommandSender;
import org.eclipse.hono.client.pubsub.PubSubMessageHelper;
import org.eclipse.hono.client.pubsub.publisher.PubSubPublisherFactory;
import org.eclipse.hono.client.pubsub.subscriber.PubSubSubscriberFactory;
import org.eclipse.hono.client.registry.TenantClient;
import org.eclipse.hono.client.util.ServiceClient;
import org.eclipse.hono.commandrouter.CommandConsumerFactory;
import org.eclipse.hono.commandrouter.CommandRouterMetrics;
import org.eclipse.hono.commandrouter.CommandTargetMapper;
import org.eclipse.hono.notification.NotificationEventBusSupport;
import org.eclipse.hono.notification.deviceregistry.LifecycleChange;
import org.eclipse.hono.notification.deviceregistry.TenantChangeNotification;
import org.eclipse.hono.tracing.TracingHelper;
import org.eclipse.hono.util.CommandConstants;
import org.eclipse.hono.util.LifecycleStatus;
import org.eclipse.hono.util.MessagingType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.cloud.pubsub.v1.AckReplyConsumer;
import com.google.cloud.pubsub.v1.MessageReceiver;
import com.google.pubsub.v1.PubsubMessage;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.tag.Tags;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.ext.healthchecks.HealthCheckHandler;
import io.vertx.ext.healthchecks.Status;

/**
 * A factory for creating clients for the <em>Pub/Sub messaging infrastructure</em> to receive commands.
 * <p>
 * Command messages are first received by the Pub/Sub subscriber on the tenant-specific topic. It is then determined
 * which protocol adapter instance can handle the command. The command is then forwarded to Pub/Sub on a topic
 * containing that adapter instance id.
 */
public class PubSubBasedCommandConsumerFactoryImpl implements CommandConsumerFactory, ServiceClient {

    private static final Logger LOG = LoggerFactory.getLogger(PubSubBasedCommandConsumerFactoryImpl.class);

    private final Vertx vertx;
    private final TenantClient tenantClient;
    private final Tracer tracer;
    private final PubSubBasedInternalCommandSender internalCommandSender;
    private final PubSubBasedCommandResponseSender responseSender;
    private final LifecycleStatus lifecycleStatus = new LifecycleStatus();
    private final CommandTargetMapper commandTargetMapper;
    private final CommandRouterMetrics metrics;
    private final PubSubSubscriberFactory subscriberFactory;
    private final Set<String> tenantIds = new HashSet<>();
    private final Map<String, MessageReceiver> activeReceivers = new ConcurrentHashMap<>();
    private PubSubBasedMappingAndDelegatingCommandHandler commandHandler;

    /**
     * Creates a new factory to process commands via Google Pub/Sub.
     *
     * @param vertx The Vert.x instance to use.
     * @param tenantClient The Tenant service client.
     * @param tracer The tracer instance.
     * @param pubSubPublisherFactory The publisher factory for creating Pub/Sub publishers for sending messages.
     * @param projectId The identifier of the Google Cloud Project to connect to.
     * @param commandTargetMapper The component for mapping an incoming command to the gateway (if applicable) and
     *            protocol adapter instance that can handle it. Note that no initialization of this factory will be done
     *            here, that is supposed to be done by the calling method.
     * @param metrics The component to use for reporting metrics.
     * @param subscriberFactory The subscriber factory for creating Pub/Sub subscribers for receiving messages.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    public PubSubBasedCommandConsumerFactoryImpl(
            final Vertx vertx,
            final TenantClient tenantClient,
            final Tracer tracer,
            final PubSubPublisherFactory pubSubPublisherFactory,
            final String projectId,
            final CommandTargetMapper commandTargetMapper,
            final CommandRouterMetrics metrics,
            final PubSubSubscriberFactory subscriberFactory) {
        this.vertx = Objects.requireNonNull(vertx);
        this.tenantClient = Objects.requireNonNull(tenantClient);
        this.tracer = Objects.requireNonNull(tracer);
        this.commandTargetMapper = Objects.requireNonNull(commandTargetMapper);
        this.metrics = Objects.requireNonNull(metrics);
        this.subscriberFactory = Objects.requireNonNull(subscriberFactory);
        Objects.requireNonNull(pubSubPublisherFactory);
        Objects.requireNonNull(projectId);

        this.internalCommandSender = new PubSubBasedInternalCommandSender(pubSubPublisherFactory, projectId, tracer);
        this.responseSender = new PubSubBasedCommandResponseSender(vertx, pubSubPublisherFactory, projectId, tracer);
    }

    @Override
    public Future<Void> start() {
        if (lifecycleStatus.isStarting()) {
            return Future.succeededFuture();
        } else if (!lifecycleStatus.setStarting()) {
            return Future.failedFuture(new IllegalStateException("factory is already started/stopping"));
        }

        final var commandQueue = new PubSubBasedCommandProcessingQueue(vertx);
        commandHandler = new PubSubBasedMappingAndDelegatingCommandHandler(
                vertx,
                tenantClient,
                commandQueue,
                commandTargetMapper,
                internalCommandSender,
                metrics,
                tracer,
                responseSender);

        registerTenantCreationListener();
        return commandHandler.start().onSuccess(s -> lifecycleStatus.setStarted());
    }

    @Override
    public Future<Void> stop() {
        return lifecycleStatus.runStopAttempt(
                () -> Future.join(subscriberFactory.closeAllSubscribers(), commandHandler.stop()).mapEmpty());
    }

    @Override
    public MessagingType getMessagingType() {
        return MessagingType.pubsub;
    }

    @Override
    public Future<Void> createCommandConsumer(final String tenantId, final SpanContext context) {
        if (tenantIds.contains(tenantId)) {
            LOG.debug("Command consumer for tenant {} is already registered", tenantId);
            return Future.succeededFuture();
        }
        tenantIds.add(tenantId);

        final Span span = TracingHelper.buildServerChildSpan(
                tracer,
                context,
                "create command consumer",
                CommandConsumerFactory.class.getSimpleName())
                .start();
        TracingHelper.TAG_TENANT_ID.set(span, tenantId);
        Tags.MESSAGE_BUS_DESTINATION.set(span, CommandConstants.COMMAND_ENDPOINT);

        final MessageReceiver messageReceiver = getOrCreateReceiver(tenantId);

        final String subscriptionId = PubSubMessageHelper.getTopicName(CommandConstants.COMMAND_ENDPOINT, tenantId);
        return subscriberFactory
                .getOrCreateSubscriber(subscriptionId, messageReceiver)
                .subscribe(false);
    }

    @Override
    public void registerReadinessChecks(final HealthCheckHandler readinessHandler) {
        internalCommandSender.registerReadinessChecks(readinessHandler);
        responseSender.registerReadinessChecks(readinessHandler);
        readinessHandler.register(
                "command-consumer-factory-pubsub-subscriber-%s".formatted(UUID.randomUUID()),
                status -> status.tryComplete(Status.OK()));
    }

    @Override
    public void registerLivenessChecks(final HealthCheckHandler livenessHandler) {
        internalCommandSender.registerLivenessChecks(livenessHandler);
        responseSender.registerLivenessChecks(livenessHandler);
    }

    private void registerTenantCreationListener() {
        NotificationEventBusSupport.registerConsumer(vertx, TenantChangeNotification.TYPE,
                notification -> {
                    if (lifecycleStatus.isStarted() && notification.getChange() == LifecycleChange.CREATE
                            && notification.isTenantEnabled()) {
                        // Optional optimization:
                        // Prepare Pub/Sub consumer to receive commands for devices of a newly created tenant by creating
                        // the subscription. If not done here, this preparation is done in the "createCommandConsumer" method.
                        // Doing it here will speed up the first invocation of "createCommandConsumer" for this tenant.
                        final String tenantId = notification.getTenantId();
                        final MessageReceiver messageReceiver = getOrCreateReceiver(tenantId);
                        final String subscriptionId = PubSubMessageHelper.getTopicName(CommandConstants.COMMAND_ENDPOINT, tenantId);
                        subscriberFactory.getOrCreateSubscriber(subscriptionId, messageReceiver);
                    }
                });
    }

    private MessageReceiver getOrCreateReceiver(final String tenantId) {
        return activeReceivers.computeIfAbsent(tenantId, r -> createMessageReceiver(tenantId));
    }

    private MessageReceiver createMessageReceiver(final String tenantId) {
        return (PubsubMessage message, AckReplyConsumer consumer) -> {
            commandHandler.mapAndDelegateIncomingCommandMessage(message, tenantId);
            consumer.ack();
        };
    }
}
