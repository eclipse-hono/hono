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

import java.util.Objects;

import org.eclipse.hono.adapter.client.command.kafka.KafkaBasedCommand;
import org.eclipse.hono.adapter.client.command.kafka.KafkaBasedCommandContext;
import org.eclipse.hono.adapter.client.command.kafka.KafkaBasedInternalCommandSender;
import org.eclipse.hono.client.impl.CommandConsumer;
import org.eclipse.hono.client.kafka.tracing.KafkaTracingHelper;
import org.eclipse.hono.commandrouter.CommandTargetMapper;
import org.eclipse.hono.commandrouter.impl.AbstractMappingAndDelegatingCommandHandler;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.vertx.core.buffer.Buffer;
import io.vertx.kafka.client.consumer.KafkaConsumerRecord;

/**
 * Handler for commands received at the tenant-specific topic.
 */
public class KafkaBasedMappingAndDelegatingCommandHandler extends AbstractMappingAndDelegatingCommandHandler {
    private final Tracer tracer;

    /**
     * Creates a new KafkaBasedMappingAndDelegatingCommandHandler instance.
     *
     * @param commandTargetMapper The mapper component to determine the command target.
     * @param internalCommandSender The command sender to publish commands to the internal command topic.
     * @param tracer The tracer instance.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    public KafkaBasedMappingAndDelegatingCommandHandler(
            final CommandTargetMapper commandTargetMapper,
            final KafkaBasedInternalCommandSender internalCommandSender,
            final Tracer tracer) {
        super(commandTargetMapper, internalCommandSender);
        Objects.requireNonNull(tracer);

        this.tracer = tracer;
    }

    /**
     * Delegates an incoming command to the protocol adapter instance that the target
     * device is connected to.
     * <p>
     * Determines the target gateway (if applicable) and protocol adapter instance for an incoming command
     * and delegates the command to the resulting protocol adapter instance.
     *
     * @param consumerRecord The consumer record corresponding to the command.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    public void mapAndDelegateIncomingCommandMessage(final KafkaConsumerRecord<String, Buffer> consumerRecord) {
        Objects.requireNonNull(consumerRecord);

        final KafkaBasedCommand command;
        try {
            command = KafkaBasedCommand.from(consumerRecord);
        } catch (final IllegalArgumentException exception) {
            log.debug("command record is invalid", exception);
            return;
        }

        final SpanContext spanContext = KafkaTracingHelper.extractSpanContext(tracer, consumerRecord);
        final Span currentSpan = CommandConsumer.createSpan("map and delegate command", command.getTenant(),
                command.getDeviceId(), null, tracer, spanContext);
        final KafkaBasedCommandContext commandContext = new KafkaBasedCommandContext(command, currentSpan);

        command.logToSpan(currentSpan);
        if (command.isValid()) {
            log.trace("received valid command record [{}]", command);
            mapAndDelegateIncomingCommand(commandContext);
        } else {
            log.debug("received invalid command record [{}]", command);
            commandContext.reject("malformed command message");
        }
    }
}
