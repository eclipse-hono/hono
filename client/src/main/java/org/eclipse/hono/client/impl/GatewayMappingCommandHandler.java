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

import java.net.HttpURLConnection;

import org.apache.qpid.proton.amqp.transport.ErrorCondition;
import org.eclipse.hono.client.Command;
import org.eclipse.hono.client.CommandContext;
import org.eclipse.hono.client.GatewayMapper;
import org.eclipse.hono.client.ServiceInvocationException;
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
        LOG.trace("determine mapped gateway (if any) to use for received command [{}]", originalCommand);
        final Future<String> mappedGatewayDeviceFuture = gatewayMapper.getMappedGatewayDevice(tenantId, originalDeviceId,
                originalCommandContext.getTracingContext());

        mappedGatewayDeviceFuture.setHandler(mappedGatewayResult -> {
            if (mappedGatewayResult.succeeded()) {
                final String mappedGatewayId = mappedGatewayResult.result();
                final CommandContext commandContext;
                if (mappedGatewayId.equals(originalDeviceId)) {
                    LOG.trace("gateway mapper returned original device {}", originalDeviceId);
                    commandContext = originalCommandContext;
                } else {
                    LOG.trace("determined mapped gateway {} for device {}", mappedGatewayId, originalDeviceId);
                    originalCommandContext.getCurrentSpan().log("determined mapped gateway " + mappedGatewayId);
                    if (!originalCommand.isOneWay()) {
                        originalCommand.getCommandMessage().setReplyTo(String.format("%s/%s/%s",
                                CommandConstants.NORTHBOUND_COMMAND_RESPONSE_ENDPOINT, tenantId, originalCommand.getReplyToId()));
                    }
                    final Command command = Command.from(originalCommand.getCommandMessage(), tenantId, mappedGatewayId);
                    commandContext = CommandContext.from(command, originalCommandContext.getDelivery(),
                            originalCommandContext.getReceiver(), originalCommandContext.getCurrentSpan());
                }
                nextCommandHandler.handle(commandContext);
            } else {
                if (mappedGatewayResult.cause() instanceof ServiceInvocationException
                        && ((ServiceInvocationException) mappedGatewayResult.cause()).getErrorCode() == HttpURLConnection.HTTP_NOT_FOUND) {
                    LOG.debug("no mapped gateway set for device {}", originalDeviceId);
                    TracingHelper.logError(originalCommandContext.getCurrentSpan(),
                            "no mapped gateway set for device " + originalDeviceId);
                } else {
                    LOG.error("error getting mapped gateway for device {}", originalDeviceId, mappedGatewayResult.cause());
                    TracingHelper.logError(originalCommandContext.getCurrentSpan(),
                            "error getting mapped gateway for device: " + mappedGatewayResult.cause());
                }
                originalCommandContext.release();
            }
        });
    }

}
