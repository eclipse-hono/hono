/*******************************************************************************
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
import org.eclipse.hono.client.Command;
import org.eclipse.hono.client.CommandContext;
import org.eclipse.hono.client.CommandTargetMapper;
import org.eclipse.hono.client.DelegatedCommandSender;
import org.eclipse.hono.client.HonoConnection;
import org.eclipse.hono.client.SendMessageSampler;
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.client.impl.CachingClientFactory;
import org.eclipse.hono.client.impl.CommandConsumer;
import org.eclipse.hono.client.impl.DelegatedCommandSenderImpl;
import org.eclipse.hono.tracing.TracingHelper;
import org.eclipse.hono.util.CommandConstants;
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
    private final CachingClientFactory<DelegatedCommandSender> delegatedCommandSenderFactory;
    private final SendMessageSampler sampler;

    /**
     * Creates a new MappingAndDelegatingCommandHandler instance.
     *
     * @param connection The connection to the AMQP network.
     * @param commandTargetMapper The mapper component to determine the command target.
     * @param sampler The sampler to use.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    public MappingAndDelegatingCommandHandler(
            final HonoConnection connection,
            final CommandTargetMapper commandTargetMapper,
            final SendMessageSampler sampler) {
        this.connection = Objects.requireNonNull(connection);
        this.commandTargetMapper = Objects.requireNonNull(commandTargetMapper);
        this.sampler = sampler;

        this.delegatedCommandSenderFactory = new CachingClientFactory<>(connection.getVertx(), s -> s.isOpen());
        this.connection.addDisconnectListener(con -> delegatedCommandSenderFactory.clearState());
    }

    /**
     * Delegates an incoming command to the protocol adapter instance that the target
     * device is connected to.
     * <p>
     * Determines the target gateway (if applicable) and protocol adapter instance for an incoming command
     * and delegates the command to the resulting protocol adapter instance.
     *
     * @param tenantId The tenant that the command target must belong to.
     * @param originalMessageDelivery The delivery of the command message.
     * @param message The command message.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    public void mapAndDelegateIncomingCommandMessage(
            final String tenantId,
            final ProtonDelivery originalMessageDelivery,
            final Message message) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(originalMessageDelivery);
        Objects.requireNonNull(message);

        // this is the place where a command message on the "command/${tenant}" address arrives *first*
        if (Strings.isNullOrEmpty(message.getAddress())) {
            LOG.debug("command message has no address");
            final Rejected rejected = new Rejected();
            rejected.setError(new ErrorCondition(Constants.AMQP_BAD_REQUEST, "missing command target address"));
            originalMessageDelivery.disposition(rejected, true);
            return;
        }
        final ResourceIdentifier targetAddress = ResourceIdentifier.fromString(message.getAddress());
        final String deviceId = targetAddress.getResourceId();
        if (!tenantId.equals(targetAddress.getTenantId())) {
            LOG.debug("command message address contains invalid tenant [expected: {}, found: {}]", tenantId, targetAddress.getTenantId());
            final Rejected rejected = new Rejected();
            rejected.setError(new ErrorCondition(AmqpError.UNAUTHORIZED_ACCESS, "unauthorized to send command to tenant"));
            originalMessageDelivery.disposition(rejected, true);
            return;
        } else if (deviceId == null) {
            LOG.debug("invalid command message address: {}", message.getAddress());
            final Rejected rejected = new Rejected();
            rejected.setError(new ErrorCondition(Constants.AMQP_BAD_REQUEST, "invalid command target address"));
            originalMessageDelivery.disposition(rejected, true);
            return;
        }

        final Command command = Command.from(message, tenantId, deviceId);
        if (command.isValid()) {
            LOG.trace("received valid command message: [{}]", command);
        } else {
            LOG.debug("received invalid command message: {}", command);
        }
        final SpanContext spanContext = TracingHelper.extractSpanContext(connection.getTracer(), message);
        final Span currentSpan = CommandConsumer.createSpan("map and delegate command", tenantId, deviceId, null,
                connection.getTracer(), spanContext);
        CommandConsumer.logReceivedCommandToSpan(command, currentSpan);
        final CommandContext commandContext = CommandContext.from(command, originalMessageDelivery, currentSpan);
        mapAndDelegateIncomingCommand(tenantId, deviceId, commandContext);
    }

    private void mapAndDelegateIncomingCommand(final String tenantId, final String originalDeviceId,
            final CommandContext originalCommandContext) {
        // note that the command might be invalid here - a matching local handler to reject it (and report metrics) shall be found in that case
        final Command originalCommand = originalCommandContext.getCommand();

        // determine last used gateway device id
        LOG.trace("determine command target gateway/adapter for [{}]", originalCommand);
        final Future<JsonObject> commandTargetFuture = commandTargetMapper.getTargetGatewayAndAdapterInstance(tenantId,
                originalDeviceId, originalCommandContext.getTracingContext());

        commandTargetFuture.onComplete(commandTargetResult -> {
            if (commandTargetResult.succeeded()) {
                final String targetDeviceId = commandTargetResult.result().getString(DeviceConnectionConstants.FIELD_PAYLOAD_DEVICE_ID);
                final String targetAdapterInstance = commandTargetResult.result().getString(DeviceConnectionConstants.FIELD_ADAPTER_INSTANCE_ID);

                delegateIncomingCommand(originalDeviceId, originalCommandContext, targetDeviceId, targetAdapterInstance);

            } else {
                if (commandTargetResult.cause() instanceof ServiceInvocationException
                        && ((ServiceInvocationException) commandTargetResult.cause()).getErrorCode() == HttpURLConnection.HTTP_NOT_FOUND) {
                    LOG.debug("no target adapter instance found for command for device {}", originalDeviceId);
                    TracingHelper.logError(originalCommandContext.getTracingSpan(),
                            "no target adapter instance found for command with device id " + originalDeviceId);
                } else {
                    LOG.debug("error getting target gateway and adapter instance for command with device id {}",
                            originalDeviceId, commandTargetResult.cause());
                    TracingHelper.logError(originalCommandContext.getTracingSpan(),
                            "error getting target gateway and adapter instance for command with device id " + originalDeviceId,
                            commandTargetResult.cause());
                }
                originalCommandContext.release();
            }
        });
    }

    private void delegateIncomingCommand(
            final String originalDeviceId,
            final CommandContext originalCommandContext,
            final String targetDeviceId,
            final String targetAdapterInstance) {

        // note that the command might be invalid here - a matching local handler to reject it (and report metrics) shall be found in that case
        final Command originalCommand = originalCommandContext.getCommand();
        final String targetGatewayId = targetDeviceId.equals(originalDeviceId) ? null : targetDeviceId;

        if (originalCommand.isValid()) {
            // delegate to matching consumer via downstream peer
            final CommandContext commandContext = adaptCommandContextToGatewayIfNeeded(originalCommandContext, targetGatewayId);
            delegateCommandMessageToAdapterInstance(targetAdapterInstance, commandContext);
        } else {
            // command message is invalid
            originalCommandContext.reject(getMalformedMessageError());
        }
    }

    private ErrorCondition getMalformedMessageError() {
        return new ErrorCondition(Constants.AMQP_BAD_REQUEST, "malformed command message");
    }

    private CommandContext adaptCommandContextToGatewayIfNeeded(final CommandContext originalCommandContext, final String gatewayId) {
        final Command originalCommand = originalCommandContext.getCommand();
        final String tenantId = originalCommand.getTenant();
        final String originalDeviceId = originalCommand.getDeviceId();

        final CommandContext commandContext;
        if (gatewayId == null) {
            LOG.trace("command not mapped to gateway, use original device id {}", originalDeviceId);
            commandContext = originalCommandContext;
        } else {
            LOG.trace("determined target gateway {} for device {}", gatewayId, originalDeviceId);
            originalCommandContext.getTracingSpan().log("determined target gateway " + gatewayId);
            if (!originalCommand.isOneWay()) {
                originalCommand.getCommandMessage().setReplyTo(String.format("%s/%s/%s",
                        CommandConstants.NORTHBOUND_COMMAND_RESPONSE_ENDPOINT, tenantId, originalCommand.getReplyToId()));
            }
            final Command command = Command.from(originalCommand.getCommandMessage(), tenantId, gatewayId);
            commandContext = CommandContext.from(command, originalCommandContext.getDelivery(),
                    originalCommandContext.getTracingSpan());
        }
        return commandContext;
    }

    private void delegateCommandMessageToAdapterInstance(final String targetAdapterInstance, final CommandContext commandContext) {
        LOG.trace("delegate command to target adapter instance '{}' [command: {}]", targetAdapterInstance, commandContext.getCommand());
        getOrCreateDelegatedCommandSender(targetAdapterInstance)
                .compose(sender -> sender.sendCommandMessage(commandContext.getCommand(), commandContext.getTracingContext()))
                .onComplete(ar -> {
                    if (ar.succeeded()) {
                        final ProtonDelivery delegatedMsgDelivery = ar.result();
                        LOG.trace("command [{}] sent to downstream peer; remote state of delivery: {}",
                                commandContext.getCommand(), delegatedMsgDelivery.getRemoteState());
                        commandContext.disposition(delegatedMsgDelivery.getRemoteState());
                    } else {
                        LOG.debug("failed to send command [{}] to downstream peer", commandContext.getCommand(), ar.cause());
                        TracingHelper.logError(commandContext.getTracingSpan(),
                                "failed to send command message to downstream peer: " + ar.cause());
                        commandContext.release();
                    }
                });
    }

    private Future<DelegatedCommandSender> getOrCreateDelegatedCommandSender(final String protocolAdapterInstanceId) {
        return connection.executeOnContext(result -> {
            delegatedCommandSenderFactory.getOrCreateClient(protocolAdapterInstanceId,
                    () -> DelegatedCommandSenderImpl.create(connection, protocolAdapterInstanceId, sampler,
                            onSenderClosed -> delegatedCommandSenderFactory.removeClient(protocolAdapterInstanceId)),
                    result);
        });
    }

}
