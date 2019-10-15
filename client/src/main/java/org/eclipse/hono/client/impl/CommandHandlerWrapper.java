/*******************************************************************************
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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

import java.util.Objects;

import org.eclipse.hono.client.CommandContext;

import io.vertx.core.Handler;

/**
 * Wraps a command handler to be used in a command consumer.
 */
public final class CommandHandlerWrapper {

    private final String deviceId;
    private final String gatewayId;
    private final Handler<CommandContext> commandHandler;
    private final Handler<Void> remoteCloseHandler;

    /**
     * Creates a new CommandHandlerWrapper.
     * 
     * @param deviceId The identifier of the device that is the target of the commands being handled.
     * @param gatewayId The identifier of the gateway that is acting on behalf of the device that is
     *                  the target of the commands being handled, or {@code null} otherwise.
     * @param commandHandler The command handler.
     * @param remoteCloseHandler The handler to be invoked when the command consumer is closed remotely. May be
     *            {@code null}.
     * @throws NullPointerException If deviceId or commandHandler is {@code null}.
     */
    public CommandHandlerWrapper(final String deviceId, final String gatewayId,
            final Handler<CommandContext> commandHandler, final Handler<Void> remoteCloseHandler) {
        this.deviceId = Objects.requireNonNull(deviceId);
        this.gatewayId = gatewayId;
        this.commandHandler = Objects.requireNonNull(commandHandler);
        this.remoteCloseHandler = remoteCloseHandler;
    }

    /**
     * Gets the identifier of the device to handle commands for.
     *
     * @return The identifier.
     */
    public String getDeviceId() {
        return deviceId;
    }

    /**
     * Gets the identifier of the gateway that the command target device is connected to.
     *
     * @return The identifier or {@code null}.
     */
    public String getGatewayId() {
        return gatewayId;
    }

    /**
     * Invokes the command handler with the given command context.
     *
     * @param commandContext The command context to pass on to the command handler.
     */
    public void handleCommand(final CommandContext commandContext) {
        commandHandler.handle(commandContext);
    }

    /**
     * Invokes the handler for the case that the command consumer is closed remotely.
     */
    public void handleRemoteClose() {
        if (remoteCloseHandler != null) {
            remoteCloseHandler.handle(null);
        }
    }

    @Override
    public String toString() {
        return "CommandHandlerWrapper{" +
                "deviceId='" + deviceId + '\'' +
                ", gatewayId='" + gatewayId + '\'' +
                '}';
    }
}
