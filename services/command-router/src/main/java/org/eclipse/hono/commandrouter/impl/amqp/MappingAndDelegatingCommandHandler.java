/*******************************************************************************
 * Copyright (c) 2020, 2021 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.commandrouter.impl.amqp;

import java.net.HttpURLConnection;
import java.util.Objects;

import org.apache.qpid.proton.amqp.messaging.Rejected;
import org.apache.qpid.proton.amqp.transport.AmqpError;
import org.apache.qpid.proton.amqp.transport.ErrorCondition;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.adapter.client.command.amqp.ProtonBasedCommand;
import org.eclipse.hono.adapter.client.command.amqp.ProtonBasedCommandContext;
import org.eclipse.hono.adapter.client.command.amqp.ProtonBasedInternalCommandSender;
import org.eclipse.hono.client.HonoConnection;
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.client.impl.CommandConsumer;
import org.eclipse.hono.commandrouter.CommandTargetMapper;
import org.eclipse.hono.tracing.TracingHelper;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.DeviceConnectionConstants;
import org.eclipse.hono.util.ResourceIdentifier;
import org.eclipse.hono.util.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.proton.ProtonDelivery;

/**
 * Handler for commands received at the tenant-specific address.
 */
public class MappingAndDelegatingCommandHandler {

    private static final Logger LOG = LoggerFactory.getLogger(MappingAndDelegatingCommandHandler.class);

    private final HonoConnection connection;
    private final CommandTargetMapper commandTargetMapper;
    private final ProtonBasedInternalCommandSender internalCommandSender;

    /**
     * Creates a new MappingAndDelegatingCommandHandler instance.
     *
     * @param connection The connection to the AMQP network.
     * @param commandTargetMapper The mapper component to determine the command target.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    public MappingAndDelegatingCommandHandler(
            final HonoConnection connection,
            final CommandTargetMapper commandTargetMapper) {
        this.connection = Objects.requireNonNull(connection);
        this.commandTargetMapper = Objects.requireNonNull(commandTargetMapper);

        this.internalCommandSender = new ProtonBasedInternalCommandSender(connection);
    }

    /**
     * Delegates an incoming command to the protocol adapter instance that the target
     * device is connected to.
     * <p>
     * Determines the target gateway (if applicable) and protocol adapter instance for an incoming command
     * and delegates the command to the resulting protocol adapter instance.
     *
     * @param tenantId The tenant that the command target must belong to.
     * @param messageDelivery The delivery of the command message.
     * @param message The command message.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    public void mapAndDelegateIncomingCommandMessage(
            final String tenantId,
            final ProtonDelivery messageDelivery,
            final Message message) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(messageDelivery);
        Objects.requireNonNull(message);

        // this is the place where a command message on the "command/${tenant}" address arrives *first*
        if (Strings.isNullOrEmpty(message.getAddress())) {
            LOG.debug("command message has no address");
            final Rejected rejected = new Rejected();
            rejected.setError(new ErrorCondition(Constants.AMQP_BAD_REQUEST, "missing command target address"));
            messageDelivery.disposition(rejected, true);
            return;
        }
        final ResourceIdentifier targetAddress = ResourceIdentifier.fromString(message.getAddress());
        final String deviceId = targetAddress.getResourceId();
        if (!tenantId.equals(targetAddress.getTenantId())) {
            LOG.debug("command message address contains invalid tenant [expected: {}, found: {}]", tenantId, targetAddress.getTenantId());
            final Rejected rejected = new Rejected();
            rejected.setError(new ErrorCondition(AmqpError.UNAUTHORIZED_ACCESS, "unauthorized to send command to tenant"));
            messageDelivery.disposition(rejected, true);
            return;
        } else if (Strings.isNullOrEmpty(deviceId)) {
            LOG.debug("invalid command message address: {}", message.getAddress());
            final Rejected rejected = new Rejected();
            rejected.setError(new ErrorCondition(Constants.AMQP_BAD_REQUEST, "invalid command target address"));
            messageDelivery.disposition(rejected, true);
            return;
        }

        final ProtonBasedCommand command = ProtonBasedCommand.from(message);
        if (command.isValid()) {
            LOG.trace("received valid command message: {}", command);
        } else {
            LOG.debug("received invalid command message: {}", command);
        }
        final SpanContext spanContext = TracingHelper.extractSpanContext(connection.getTracer(), message);
        final Span currentSpan = CommandConsumer.createSpan("map and delegate command", tenantId, deviceId, null,
                connection.getTracer(), spanContext);
        command.logToSpan(currentSpan);
        final ProtonBasedCommandContext commandContext = new ProtonBasedCommandContext(command, messageDelivery, currentSpan);
        if (command.isValid()) {
            mapAndDelegateIncomingCommand(tenantId, deviceId, commandContext);
        } else {
            // command message is invalid
            commandContext.reject("malformed command message");
        }
    }

    private void mapAndDelegateIncomingCommand(final String tenantId, final String deviceId,
            final ProtonBasedCommandContext commandContext) {
        final ProtonBasedCommand command = commandContext.getCommand();

        // determine last used gateway device id
        LOG.trace("determine command target gateway/adapter for [{}]", command);
        final Future<JsonObject> commandTargetFuture = commandTargetMapper.getTargetGatewayAndAdapterInstance(tenantId,
                deviceId, commandContext.getTracingContext());

        commandTargetFuture.onComplete(cmdTargetResult -> {
            if (cmdTargetResult.succeeded()) {
                final String targetDeviceId = cmdTargetResult.result().getString(DeviceConnectionConstants.FIELD_PAYLOAD_DEVICE_ID);
                final String targetAdapterInstance = cmdTargetResult.result().getString(DeviceConnectionConstants.FIELD_ADAPTER_INSTANCE_ID);

                final String targetGatewayId = targetDeviceId.equals(deviceId) ? null : targetDeviceId;
                if (targetGatewayId == null) {
                    LOG.trace("command not mapped to gateway, use original device id [{}]", command.getDeviceId());
                } else {
                    LOG.trace("determined target gateway [{}] for device [{}]", targetGatewayId, command.getDeviceId());
                    commandContext.getTracingSpan().log("determined target gateway [" + targetGatewayId + "]");
                    command.setGatewayId(targetGatewayId);
                }
                LOG.trace("delegate command to target adapter instance '{}' [command: {}]", targetAdapterInstance,
                        commandContext.getCommand());
                internalCommandSender.sendCommand(commandContext, targetAdapterInstance);

            } else {
                if (cmdTargetResult.cause() instanceof ServiceInvocationException
                        && ((ServiceInvocationException) cmdTargetResult.cause()).getErrorCode() == HttpURLConnection.HTTP_NOT_FOUND) {
                    LOG.debug("no target adapter instance found for command for device {}", deviceId);
                    TracingHelper.logError(commandContext.getTracingSpan(),
                            "no target adapter instance found for command with device id " + deviceId);
                } else {
                    LOG.debug("error getting target gateway and adapter instance for command with device id {}",
                            deviceId, cmdTargetResult.cause());
                    TracingHelper.logError(commandContext.getTracingSpan(),
                            "error getting target gateway and adapter instance for command with device id " + deviceId,
                            cmdTargetResult.cause());
                }
                commandContext.release();
            }
        });
    }

}
