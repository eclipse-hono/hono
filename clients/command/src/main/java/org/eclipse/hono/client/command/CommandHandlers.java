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

package org.eclipse.hono.client.command;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Context;
import io.vertx.core.Future;

/**
 * A container for command handlers associated with devices.
 */
public class CommandHandlers {

    private static final Logger LOG = LoggerFactory.getLogger(CommandHandlers.class);

    private final Map<String, CommandHandlerWrapper> handlers = new HashMap<>();

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
     *                       The future returned by the handler indicates the outcome of handling the command.
     * @param context The vert.x context to run the handler on or {@code null} to invoke the handler directly.
     * @return The replaced handler entry or {@code null} if there was none.
     * @throws NullPointerException If any of tenantId, deviceId or commandHandler is {@code null}.
     */
    public CommandHandlerWrapper putCommandHandler(final String tenantId, final String deviceId,
            final String gatewayId, final Function<CommandContext, Future<Void>> commandHandler, final Context context) {
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(deviceId);
        Objects.requireNonNull(commandHandler);

        return putCommandHandler(new CommandHandlerWrapper(tenantId, deviceId, gatewayId, commandHandler, context, null));
    }

    /**
     * Adds a handler for commands targeted at a device that is connected either directly or via a gateway.
     *
     * @param commandHandlerWrapper The wrapper containing the command handler and device/gateway identifier.
     * @return The replaced entry or {@code null} if there was none.
     * @throws NullPointerException If commandHandlerWrapper is {@code null}.
     */
    public CommandHandlerWrapper putCommandHandler(final CommandHandlerWrapper commandHandlerWrapper) {
        Objects.requireNonNull(commandHandlerWrapper);

        final String key = getDeviceKey(commandHandlerWrapper);
        if (handlers.containsKey(key)) {
            LOG.debug("replacing existing command handler [tenant-id: {}, device-id: {}]",
                    commandHandlerWrapper.getTenantId(), commandHandlerWrapper.getDeviceId());
        }
        return handlers.put(key, commandHandlerWrapper);
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
    public CommandHandlerWrapper getCommandHandler(final String tenantId, final String deviceId) {
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(deviceId);
        return handlers.get(getDeviceKey(tenantId, deviceId));
    }

    /**
     * Gets the contained command handlers.
     *
     * @return The command handlers.
     */
    public Collection<CommandHandlerWrapper> getCommandHandlers() {
        return handlers.values();
    }

    /**
     * Removes the handler for the given device id.
     *
     * @param tenantId The tenant id of the handler to remove.
     * @param deviceId The device id of the handler to remove.
     * @return {@code true} if the handler was removed.
     * @throws NullPointerException If tenantId or deviceId is {@code null}.
     */
    public boolean removeCommandHandler(final String tenantId, final String deviceId) {
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(deviceId);
        final CommandHandlerWrapper removedHandler = handlers.remove(getDeviceKey(tenantId, deviceId));
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
    public boolean removeCommandHandler(final CommandHandlerWrapper commandHandlerWrapper) {
        Objects.requireNonNull(commandHandlerWrapper);
        final boolean removed = handlers.remove(getDeviceKey(commandHandlerWrapper), commandHandlerWrapper);
        LOG.trace("Removed {}: {}", commandHandlerWrapper, removed);
        return removed;
    }

    private String getDeviceKey(final CommandHandlerWrapper commandHandlerWrapper) {
        return getDeviceKey(commandHandlerWrapper.getTenantId(), commandHandlerWrapper.getDeviceId());
    }

    private String getDeviceKey(final String tenantId, final String deviceId) {
        return String.format("%s/%s", tenantId, deviceId);
    }

}
