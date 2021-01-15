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

package org.eclipse.hono.adapter.client.command.amqp;

import java.util.Objects;

import org.apache.qpid.proton.amqp.messaging.Rejected;
import org.apache.qpid.proton.amqp.transport.ErrorCondition;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.adapter.client.command.CommandContext;
import org.eclipse.hono.adapter.client.command.CommandHandlerWrapper;
import org.eclipse.hono.adapter.client.command.CommandHandlers;
import org.eclipse.hono.tracing.TracingHelper;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.MessageHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.vertx.proton.ProtonDelivery;

/**
 * Handler for commands received at the protocol adapter instance specific address.
 */
public class ProtonBasedAdapterInstanceCommandHandler {

    protected static final Logger LOG = LoggerFactory.getLogger(ProtonBasedAdapterInstanceCommandHandler.class);

    private final String adapterInstanceId;
    private final Tracer tracer;

    /**
     * Creates a new ProtonBasedAdapterInstanceCommandHandler instance.
     *
     * @param tracer The tracer instance.
     * @param adapterInstanceId The id of the protocol adapter instance that this handler is running in.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    public ProtonBasedAdapterInstanceCommandHandler(final Tracer tracer,
            final String adapterInstanceId) {
        this.tracer = Objects.requireNonNull(tracer);
        this.adapterInstanceId = Objects.requireNonNull(adapterInstanceId);
    }

    /**
     * Handles a received command message.
     *
     * @param msg The command message.
     * @param delivery The command message delivery.
     * @param commandHandlers The container from which the device specific command handler for the command
     *            is chosen and invoked.
     * @throws NullPointerException If any of the parameters is {@code null}.
     */
    public void handleCommandMessage(final Message msg, final ProtonDelivery delivery, final CommandHandlers commandHandlers) {
        Objects.requireNonNull(msg);
        Objects.requireNonNull(delivery);
        Objects.requireNonNull(commandHandlers);

        final ProtonBasedCommand command;
        try {
            command = ProtonBasedCommand.fromRoutedCommandMessage(msg);
        } catch (final IllegalArgumentException e) {
            LOG.debug("address of command message is invalid: {}", msg.getAddress());
            final Rejected rejected = new Rejected();
            rejected.setError(new ErrorCondition(Constants.AMQP_BAD_REQUEST, "invalid command target address"));
            delivery.disposition(rejected, true);
            return;
        }
        final CommandHandlerWrapper commandHandler = commandHandlers.getCommandHandler(command.getTenant(),
                command.getGatewayOrDeviceId());
        if (commandHandler != null && commandHandler.getGatewayId() != null) {
            // Gateway information set in command handler means a gateway has subscribed for commands for a specific device.
            // This information isn't getting set in the message (by the Command Router) and therefore has to be adopted manually here.
            command.setGatewayId(commandHandler.getGatewayId());
        }

        final SpanContext spanContext = TracingHelper.extractSpanContext(tracer, msg);
        final Span currentSpan = org.eclipse.hono.client.impl.CommandConsumer.createSpan("handle command",
                command.getTenant(), command.getDeviceId(), command.getGatewayId(), tracer, spanContext);
        currentSpan.setTag(MessageHelper.APP_PROPERTY_ADAPTER_INSTANCE_ID, adapterInstanceId);
        command.logToSpan(currentSpan);

        final CommandContext commandContext = new ProtonBasedCommandContext(command, delivery, currentSpan);

        if (commandHandler != null) {
            LOG.trace("using [{}] for received command [{}]", commandHandler, command);
            // command.isValid() check not done here - it is to be done in the command handler
            commandHandler.handleCommand(commandContext);
        } else {
            LOG.info("no command handler found for command with device id {}, gateway id {} [tenant-id: {}]",
                    command.getDeviceId(), command.getGatewayId(), command.getTenant());
            TracingHelper.logError(currentSpan, "no command handler found for command");
            commandContext.release();
        }
    }
}
