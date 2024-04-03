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

import java.util.Objects;

import org.eclipse.hono.client.command.CommandContext;
import org.eclipse.hono.client.command.pubsub.PubSubBasedCommand;
import org.eclipse.hono.client.command.pubsub.PubSubBasedCommandContext;
import org.eclipse.hono.client.command.pubsub.PubSubBasedCommandResponseSender;
import org.eclipse.hono.client.command.pubsub.PubSubBasedInternalCommandSender;
import org.eclipse.hono.client.pubsub.tracing.PubSubTracingHelper;
import org.eclipse.hono.client.registry.TenantClient;
import org.eclipse.hono.commandrouter.CommandRouterMetrics;
import org.eclipse.hono.commandrouter.CommandTargetMapper;
import org.eclipse.hono.commandrouter.impl.AbstractMappingAndDelegatingCommandHandler;
import org.eclipse.hono.commandrouter.impl.CommandProcessingQueue;
import org.eclipse.hono.util.MessagingType;

import com.google.pubsub.v1.PubsubMessage;

import io.micrometer.core.instrument.Timer;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.vertx.core.Future;
import io.vertx.core.Vertx;

/**
 * A Handler for commands received at the tenant-specific topic.
 */
public class PubSubBasedMappingAndDelegatingCommandHandler
        extends AbstractMappingAndDelegatingCommandHandler<PubSubBasedCommandContext> {

    private final PubSubBasedCommandResponseSender pubSubBasedCommandResponseSender;

    /**
     * Creates a new PubSubBasedMappingAndDelegatingCommandHandler instance.
     *
     * @param vertx The Vert.x instance to use.
     * @param tenantClient The Tenant service client.
     * @param commandQueue The command processing queue instance to use.
     * @param commandTargetMapper The mapper component to determine the command target.
     * @param internalCommandSender The command sender to publish commands to the internal command topic.
     * @param metrics The component to use for reporting metrics.
     * @param tracer The tracer instance.
     * @param pubSubBasedCommandResponseSender The sender used to send command responses.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    public PubSubBasedMappingAndDelegatingCommandHandler(final Vertx vertx,
            final TenantClient tenantClient,
            final CommandProcessingQueue<PubSubBasedCommandContext> commandQueue,
            final CommandTargetMapper commandTargetMapper,
            final PubSubBasedInternalCommandSender internalCommandSender,
            final CommandRouterMetrics metrics,
            final Tracer tracer,
            final PubSubBasedCommandResponseSender pubSubBasedCommandResponseSender) {
        super(vertx, tenantClient, commandQueue, commandTargetMapper, internalCommandSender, metrics, tracer);
        this.pubSubBasedCommandResponseSender = Objects.requireNonNull(pubSubBasedCommandResponseSender);
    }

    @Override
    protected MessagingType getMessagingType() {
        return MessagingType.pubsub;
    }

    /**
     * {@inheritDoc}
     *
     * @return The outcome of starting the command response sender and invoking
     *         {@link AbstractMappingAndDelegatingCommandHandler#start()}.
     */
    @Override
    public Future<Void> start() {
        return Future.all(super.start(), pubSubBasedCommandResponseSender.start()).mapEmpty();
    }

    /**
     * {@inheritDoc}
     *
     * @return The outcome of stopping the command response sender and invoking
     *         {@link AbstractMappingAndDelegatingCommandHandler#stop()}.
     */
    @Override
    public Future<Void> stop() {
        return Future.join(super.stop(), pubSubBasedCommandResponseSender.stop()).mapEmpty();
    }

    /**
     * Delegates an incoming command to the protocol adapter instance that the target device is connected to.
     * <p>
     * Determines the target gateway (if applicable) and protocol adapter instance for an incoming command and delegates
     * the command to the resulting protocol adapter instance.
     *
     * @param pubsubMessage The Pub/Sub message corresponding to the command.
     * @param tenantId The tenant this Pub/Sub message belongs to.
     * @return A future indicating the outcome of the operation.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    public Future<Void> mapAndDelegateIncomingCommandMessage(final PubsubMessage pubsubMessage, final String tenantId) {
        Objects.requireNonNull(pubsubMessage);
        Objects.requireNonNull(tenantId);

        final Timer.Sample timer = getMetrics().startTimer();
        final PubSubBasedCommand command;
        try {
            command = PubSubBasedCommand.from(pubsubMessage, tenantId);
        } catch (final IllegalArgumentException e) {
            log.warn("Pub/Sub command message is invalid", e);
            return Future.failedFuture("Pub/Sub command message is invalid");
        }

        final SpanContext spanContext = PubSubTracingHelper.extractSpanContext(tracer, pubsubMessage);
        final Span currentSpan = createSpan(command.getTenant(), command.getDeviceId(), spanContext);
        command.logToSpan(currentSpan);

        final PubSubBasedCommandContext commandContext = new PubSubBasedCommandContext(
                command,
                pubSubBasedCommandResponseSender,
                currentSpan);

        if (!command.isValid()) {
            log.debug("received invalid Pub/Sub command message [{}]", command);
            return tenantClient.get(command.getTenant(), currentSpan.context())
                    .compose(tenantConfig -> {
                        commandContext.put(CommandContext.KEY_TENANT_CONFIG, tenantConfig);
                        return Future.failedFuture("command is invalid");
                    })
                    .onComplete(ar -> {
                        commandContext.reject("malformed command message");
                        reportInvalidCommand(commandContext, timer);
                    })
                    .mapEmpty();
        }
        log.info("received valid command record [{}]", command);
        return mapAndDelegateIncomingCommand(commandContext, timer);
    }
}
