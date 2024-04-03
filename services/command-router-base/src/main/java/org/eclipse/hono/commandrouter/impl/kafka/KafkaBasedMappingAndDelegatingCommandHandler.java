/*******************************************************************************
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
 *******************************************************************************/
package org.eclipse.hono.commandrouter.impl.kafka;

import java.util.Objects;

import org.eclipse.hono.client.command.CommandContext;
import org.eclipse.hono.client.command.kafka.KafkaBasedCommand;
import org.eclipse.hono.client.command.kafka.KafkaBasedCommandContext;
import org.eclipse.hono.client.command.kafka.KafkaBasedCommandResponseSender;
import org.eclipse.hono.client.command.kafka.KafkaBasedInternalCommandSender;
import org.eclipse.hono.client.kafka.tracing.KafkaTracingHelper;
import org.eclipse.hono.client.registry.TenantClient;
import org.eclipse.hono.commandrouter.CommandRouterMetrics;
import org.eclipse.hono.commandrouter.CommandTargetMapper;
import org.eclipse.hono.commandrouter.impl.AbstractMappingAndDelegatingCommandHandler;
import org.eclipse.hono.util.MessagingType;

import io.micrometer.core.instrument.Timer;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.kafka.client.consumer.KafkaConsumerRecord;

/**
 * A Handler for commands received at the tenant-specific topic.
 */
public class KafkaBasedMappingAndDelegatingCommandHandler extends AbstractMappingAndDelegatingCommandHandler<KafkaBasedCommandContext> {

    private final KafkaBasedCommandResponseSender kafkaBasedCommandResponseSender;

    /**
     * Creates a new KafkaBasedMappingAndDelegatingCommandHandler instance.
     *
     * @param vertx The Vert.x instance to use.
     * @param tenantClient The Tenant service client.
     * @param commandQueue The command queue to use for keeping track of the command processing.
     * @param commandTargetMapper The mapper component to determine the command target.
     * @param internalCommandSender The command sender to publish commands to the internal command topic.
     * @param kafkaBasedCommandResponseSender The sender used to send command responses.
     * @param metrics The component to use for reporting metrics.
     * @param tracer The tracer instance.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    public KafkaBasedMappingAndDelegatingCommandHandler(
            final Vertx vertx,
            final TenantClient tenantClient,
            final KafkaCommandProcessingQueue commandQueue,
            final CommandTargetMapper commandTargetMapper,
            final KafkaBasedInternalCommandSender internalCommandSender,
            final KafkaBasedCommandResponseSender kafkaBasedCommandResponseSender,
            final CommandRouterMetrics metrics,
            final Tracer tracer) {
        super(vertx, tenantClient, commandQueue, commandTargetMapper, internalCommandSender, metrics, tracer);
        this.kafkaBasedCommandResponseSender = Objects.requireNonNull(kafkaBasedCommandResponseSender);
    }

    @Override
    protected final MessagingType getMessagingType() {
        return MessagingType.kafka;
    }

    /**
     * {@inheritDoc}
     *
     * @return The outcome of starting the command response sender and invoking
     * {@link AbstractMappingAndDelegatingCommandHandler#start()}.
     */
    @Override
    public Future<Void> start() {
        return Future.all(super.start(), kafkaBasedCommandResponseSender.start()).mapEmpty();
    }

    /**
     * {@inheritDoc}
     *
     * @return The outcome of stopping the command response sender and invoking
     * {@link AbstractMappingAndDelegatingCommandHandler#stop()}.
     */
    @Override
    public Future<Void> stop() {
        return Future.join(super.stop(), kafkaBasedCommandResponseSender.stop()).mapEmpty();
    }

    /**
     * Delegates an incoming command to the protocol adapter instance that the target
     * device is connected to.
     * <p>
     * Determines the target gateway (if applicable) and protocol adapter instance for an incoming command
     * and delegates the command to the resulting protocol adapter instance.
     *
     * @param consumerRecord The consumer record corresponding to the command.
     * @return A future indicating the outcome of the operation.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    public Future<Void> mapAndDelegateIncomingCommandMessage(final KafkaConsumerRecord<String, Buffer> consumerRecord) {
        Objects.requireNonNull(consumerRecord);

        final Timer.Sample timer = getMetrics().startTimer();
        final KafkaBasedCommand command;
        try {
            command = KafkaBasedCommand.from(consumerRecord);
        } catch (final IllegalArgumentException exception) {
            log.debug("command record is invalid", exception);
            return Future.failedFuture("command record is invalid");
        }

        final SpanContext spanContext = KafkaTracingHelper.extractSpanContext(tracer, consumerRecord);
        final Span currentSpan = createSpan(command.getTenant(), command.getDeviceId(), spanContext);
        KafkaTracingHelper.setRecordTags(currentSpan, consumerRecord);

        final KafkaBasedCommandContext commandContext = new KafkaBasedCommandContext(
                command,
                kafkaBasedCommandResponseSender,
                currentSpan);

        command.logToSpan(currentSpan);
        if (!command.isValid()) {
            log.debug("received invalid command record [{}]", command);
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
        log.trace("received valid command record [{}]", command);
        return mapAndDelegateIncomingCommand(commandContext, timer);
    }
}
