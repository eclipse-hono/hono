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
import org.eclipse.hono.util.ResourceIdentifier;
import org.eclipse.hono.util.Strings;
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
        // command could have been mapped to a gateway, but the original address stays the same in the message address in that case
        final ResourceIdentifier resourceIdentifier = Strings.isNullOrEmpty(msg.getAddress()) ? null
                : ResourceIdentifier.fromString(msg.getAddress());
        if (resourceIdentifier == null || resourceIdentifier.getResourceId() == null) {
            LOG.debug("address of command message is invalid: {}", msg.getAddress());
            final Rejected rejected = new Rejected();
            rejected.setError(new ErrorCondition(Constants.AMQP_BAD_REQUEST, "invalid command target address"));
            delivery.disposition(rejected, true);
            return;
        }
        final String tenantId = resourceIdentifier.getTenantId();
        final String originalDeviceId = resourceIdentifier.getResourceId();
        // fetch "via" property (if set)
        final String gatewayIdFromMessage = MessageHelper.getApplicationProperty(msg.getApplicationProperties(),
                MessageHelper.APP_PROPERTY_CMD_VIA, String.class);
        final String targetDeviceId = gatewayIdFromMessage != null ? gatewayIdFromMessage : originalDeviceId;
        final CommandHandlerWrapper commandHandler = commandHandlers.getCommandHandler(tenantId, targetDeviceId);

        // Adopt gateway id from command handler if set;
        // for that kind of command handler (gateway subscribing for specific device commands), the
        // gateway information is not stored in the device connection service ("deviceConnectionService.setCommandHandlingAdapterInstance()" doesn't have an extra gateway id parameter);
        // and therefore not set in the delegated command message
        final String gatewayId = commandHandler != null && commandHandler.getGatewayId() != null
                ? commandHandler.getGatewayId()
                : gatewayIdFromMessage;

        final ProtonBasedCommand command = new ProtonBasedCommand(msg, tenantId,
                gatewayId != null ? gatewayId : originalDeviceId);

        final SpanContext spanContext = TracingHelper.extractSpanContext(tracer, msg);
        final Span currentSpan = org.eclipse.hono.client.impl.CommandConsumer.createSpan("handle command", tenantId,
                originalDeviceId, gatewayId, tracer, spanContext);
        currentSpan.setTag(MessageHelper.APP_PROPERTY_ADAPTER_INSTANCE_ID, adapterInstanceId);
        command.logToSpan(currentSpan);

        final CommandContext commandContext = new ProtonBasedCommandContext(command, delivery, currentSpan);

        if (commandHandler != null) {
            LOG.trace("using [{}] for received command [{}]", commandHandler, command);
            // command.isValid() check not done here - it is to be done in the command handler
            commandHandler.handleCommand(commandContext);
        } else {
            LOG.info("no command handler found for command with device id {}, gateway id {} [tenant-id: {}]",
                    originalDeviceId, gatewayId, tenantId);
            TracingHelper.logError(currentSpan, "no command handler found for command");
            commandContext.release();
        }
    }
}
