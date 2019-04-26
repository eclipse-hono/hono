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

import org.apache.qpid.proton.amqp.transport.ErrorCondition;
import org.eclipse.hono.client.Command;
import org.eclipse.hono.client.CommandContext;
import org.eclipse.hono.client.GatewayMapper;
import org.eclipse.hono.tracing.TracingHelper;
import org.eclipse.hono.util.CommandConstants;
import org.eclipse.hono.util.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Future;
import io.vertx.core.Handler;

/**
 * Command handler that maps commands to gateway devices (if applicable) and passes the commands to a given
 * next command handler.
 */
public class GatewayMappingCommandHandler implements Handler<CommandContext> {

    private static final Logger LOG = LoggerFactory.getLogger(GatewayMappingCommandHandler.class);

    private final GatewayMapper gatewayMapper;
    private final Handler<CommandContext> nextCommandHandler;

    /**
     * Creates a new GatewayMappingCommandHandler instance.
     * 
     * @param gatewayMapper The component mapping a command device id to the corresponding gateway device id.
     * @param nextCommandHandler The handler to invoke with the mapped command.
     */
    public GatewayMappingCommandHandler(final GatewayMapper gatewayMapper, final Handler<CommandContext> nextCommandHandler) {
        this.gatewayMapper = gatewayMapper;
        this.nextCommandHandler = nextCommandHandler;
    }

    @Override
    public void handle(final CommandContext originalCommandContext) {
        final Command originalCommand = originalCommandContext.getCommand();
        if (!originalCommand.isValid()) {
            originalCommandContext.reject(new ErrorCondition(Constants.AMQP_BAD_REQUEST, "malformed command message"));
            return;
        }
        final String tenantId = originalCommand.getTenant();
        final String originalDeviceId = originalCommand.getDeviceId();
        // determine last used gateway device id
        LOG.trace("determine 'via' device to use for received command [{}]", originalCommand);
        final Future<String> lastViaDeviceIdFuture = gatewayMapper.getMappedGatewayDevice(tenantId, originalDeviceId);

        lastViaDeviceIdFuture.setHandler(deviceIdFutureResult -> {
            if (deviceIdFutureResult.succeeded()) {
                final String lastViaDeviceId = deviceIdFutureResult.result();
                if (lastViaDeviceId != null) {
                    LOG.trace("determined 'via' device {} for device {}", lastViaDeviceId, originalDeviceId);
                    final CommandContext commandContext;
                    if (lastViaDeviceId.equals(originalDeviceId)) {
                        commandContext = originalCommandContext;
                    } else {
                        originalCommandContext.getCurrentSpan().log("determined 'via' device " + lastViaDeviceId);
                        if (!originalCommand.isOneWay()) {
                            originalCommand.getCommandMessage().setReplyTo(String.format("%s/%s/%s",
                                    CommandConstants.COMMAND_ENDPOINT, tenantId, originalCommand.getReplyToId()));
                        }
                        final Command command = Command.from(originalCommand.getCommandMessage(), tenantId, lastViaDeviceId);
                        commandContext = CommandContext.from(command, originalCommandContext.getDelivery(), originalCommandContext.getReceiver(), originalCommandContext.getCurrentSpan());
                    }
                    nextCommandHandler.handle(commandContext);
                } else {
                    // lastViaDeviceId is null - device hasn't connected yet
                    LOG.error("last-via for device {} is not set", originalDeviceId);
                    TracingHelper.logError(originalCommandContext.getCurrentSpan(), "last-via for device " + originalDeviceId + " is not set");
                    originalCommandContext.release();
                }
            } else {
                // failed to get last-via; a common cause for this is a lost connection to the device registry - automatic reconnects should resolve such a situation for subsequent requests
                LOG.error("error getting last-via for device {}", originalDeviceId, deviceIdFutureResult.cause());
                TracingHelper.logError(originalCommandContext.getCurrentSpan(),
                        "error getting last-via for device: " + deviceIdFutureResult.cause());
                originalCommandContext.release();
            }
        });
    }

}
