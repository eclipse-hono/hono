/*******************************************************************************
 * Copyright (c) 2019, 2020 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.client.impl;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.apache.qpid.proton.amqp.messaging.Rejected;
import org.apache.qpid.proton.amqp.transport.ErrorCondition;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.auth.Device;
import org.eclipse.hono.client.Command;
import org.eclipse.hono.client.CommandContext;
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
import io.vertx.core.Handler;
import io.vertx.proton.ProtonDelivery;
import io.vertx.proton.ProtonHelper;

/**
 * Handler for commands received at the protocol adapter instance specific address.
 */
public final class AdapterInstanceCommandHandler {

    private static final Logger LOG = LoggerFactory.getLogger(AdapterInstanceCommandHandler.class);

    private final Map<String, CommandHandlerWrapper> commandHandlers = new HashMap<>();
    private final Tracer tracer;
    private final String adapterInstanceId;

    /**
     * Creates a new AdapterInstanceCommandHandler instance.
     *
     * @param tracer The tracer instance.
     * @param adapterInstanceId The id of the protocol adapter instance that this handler is running in.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    public AdapterInstanceCommandHandler(final Tracer tracer, final String adapterInstanceId) {
        this.tracer = Objects.requireNonNull(tracer);
        this.adapterInstanceId = Objects.requireNonNull(adapterInstanceId);
    }

    /**
     * Handles a received command message.
     *
     * @param msg The command message.
     * @param delivery The delivery.
     * @throws NullPointerException If msg or delivery is {@code null}.
     */
    public void handleCommandMessage(final Message msg, final ProtonDelivery delivery) {
        Objects.requireNonNull(msg);
        Objects.requireNonNull(delivery);
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
        final String gatewayIdFromMessage = MessageHelper.getApplicationProperty(msg.getApplicationProperties(), MessageHelper.APP_PROPERTY_CMD_VIA, String.class);
        final String targetDeviceId = gatewayIdFromMessage != null ? gatewayIdFromMessage : originalDeviceId;
        final CommandHandlerWrapper commandHandler = getDeviceSpecificCommandHandler(tenantId, targetDeviceId);

        // Adopt gateway id from command handler if set;
        // for that kind of command handler (gateway subscribing for specific device commands), the
        // gateway information is not stored in the device connection service ("deviceConnectionService.setCommandHandlingAdapterInstance()" doesn't have an extra gateway id parameter);
        // and therefore not set in the delegated command message
        final String gatewayId = commandHandler != null && commandHandler.getGatewayId() != null
                ? commandHandler.getGatewayId()
                : gatewayIdFromMessage;

        final Command command = Command.from(msg, tenantId, gatewayId != null ? gatewayId : originalDeviceId);

        final SpanContext spanContext = TracingHelper.extractSpanContext(tracer, msg);
        final Span currentSpan = CommandConsumer.createSpan("handle command", tenantId, originalDeviceId,
                gatewayId, tracer, spanContext);
        currentSpan.setTag(MessageHelper.APP_PROPERTY_ADAPTER_INSTANCE_ID, adapterInstanceId);
        CommandConsumer.logReceivedCommandToSpan(command, currentSpan);

        if (commandHandler != null) {
            LOG.trace("using [{}] for received command [{}]", commandHandler, command);
            // command.isValid() check not done here - it is to be done in the command handler
            commandHandler.handleCommand(CommandContext.from(command, delivery, currentSpan));
        } else {
            LOG.info("no command handler found for command with device id {}, gateway id {} [tenant-id: {}]",
                    originalDeviceId, gatewayId, tenantId);
            TracingHelper.logError(currentSpan, "no command handler found for command");
            currentSpan.finish();
            ProtonHelper.released(delivery, true);
        }
    }

    /**
     * Adds a handler for commands targeted at a device that is connected either directly or via a gateway.
     *
     * @param tenantId The tenant id.
     * @param deviceId The identifier of the device or gateway that is the target of the commands being handled.
     * @param gatewayId The identifier of the gateway in case the handler gets added as part of the gateway
     *                  subscribing specifically for commands of the given device, or {@code null} otherwise.
     *                  (A gateway subscribing for commands of all devices, that it may act on behalf of, would mean
     *                  using a {@code null} value here and providing the gateway id in the <em>deviceId</em>
     *                  parameter.)
     * @param commandHandler The command handler. The handler must invoke one of the terminal methods of the passed
     *                       in {@link CommandContext} in order to settle the command message transfer and finish
     *                       the trace span associated with the {@link CommandContext}.
     * @return The replaced handler entry or {@code null} if there was none.
     * @throws NullPointerException If any of tenantId, deviceId or commandHandler is {@code null}.
     */
    public CommandHandlerWrapper putDeviceSpecificCommandHandler(final String tenantId, final String deviceId,
            final String gatewayId, final Handler<CommandContext> commandHandler) {
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(deviceId);
        Objects.requireNonNull(commandHandler);

        return putDeviceSpecificCommandHandler(new CommandHandlerWrapper(tenantId, deviceId, gatewayId, commandHandler));
    }

    /**
     * Adds a handler for commands targeted at a device that is connected either directly or via a gateway.
     *
     * @param commandHandlerWrapper The wrapper containing the command handler and device/gateway identifier.
     * @return The replaced entry or {@code null} if there was none.
     * @throws NullPointerException If commandHandlerWrapper is {@code null}.
     */
    public CommandHandlerWrapper putDeviceSpecificCommandHandler(final CommandHandlerWrapper commandHandlerWrapper) {
        Objects.requireNonNull(commandHandlerWrapper);

        final String key = getDeviceKey(commandHandlerWrapper);
        if (commandHandlers.containsKey(key)) {
            LOG.debug("replacing existing command handler [tenant-id: {}, device-id: {}]",
                    commandHandlerWrapper.getTenantId(), commandHandlerWrapper.getDeviceId());
        }
        return commandHandlers.put(key, commandHandlerWrapper);
    }

    /**
     * Gets a handler for the given device id.
     * <p>
     * When providing a gateway id as <em>deviceId</em> here, the handler is returned that handles
     * commands for all devices that the gateway may act on behalf of, if such a handler was set before.
     *
     * @param tenantId The tenant id.
     * @param deviceId The device id to get the handler for.
     * @return The handler or {@code null}.
     * @throws NullPointerException If tenantId or deviceId is {@code null}.
     */
    public CommandHandlerWrapper getDeviceSpecificCommandHandler(final String tenantId, final String deviceId) {
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(deviceId);
        return commandHandlers.get(getDeviceKey(tenantId, deviceId));
    }

    /**
     * Gets the contained command handlers.
     *
     * @return The command handlers.
     */
    public Collection<CommandHandlerWrapper> getDeviceSpecificCommandHandlers() {
        return commandHandlers.values();
    }

    /**
     * Removes the handler for the given device id.
     *
     * @param tenantId The tenant id of the handler to remove.
     * @param deviceId The device id of the handler to remove.
     * @return {@code true} if the handler was removed.
     * @throws NullPointerException If tenantId or deviceId is {@code null}.
     */
    public boolean removeDeviceSpecificCommandHandler(final String tenantId, final String deviceId) {
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(deviceId);
        final CommandHandlerWrapper removedHandler = commandHandlers.remove(getDeviceKey(tenantId, deviceId));
        LOG.trace("Removed handler for tenant {}, device {}: {}", tenantId, deviceId, removedHandler != null);
        return removedHandler != null;
    }

    /**
     * Removes the given handler.
     *
     * @param commandHandlerWrapper The handler to remove.
     * @return {@code true} if the handler was removed.
     * @throws NullPointerException If commandHandlerWrapper is {@code null}.
     */
    public boolean removeDeviceSpecificCommandHandler(final CommandHandlerWrapper commandHandlerWrapper) {
        Objects.requireNonNull(commandHandlerWrapper);
        final boolean removed = commandHandlers.remove(getDeviceKey(commandHandlerWrapper), commandHandlerWrapper);
        LOG.trace("Removed {}: {}", commandHandlerWrapper, removed);
        return removed;
    }

    private String getDeviceKey(final CommandHandlerWrapper commandHandlerWrapper) {
        return getDeviceKey(commandHandlerWrapper.getTenantId(), commandHandlerWrapper.getDeviceId());
    }

    private String getDeviceKey(final String tenantId, final String deviceId) {
        return Device.asAddress(tenantId, deviceId);
    }

}
