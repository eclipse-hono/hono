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

package org.eclipse.hono.client.impl;

import java.net.HttpURLConnection;
import java.util.Objects;

import org.apache.qpid.proton.amqp.messaging.ApplicationProperties;
import org.apache.qpid.proton.amqp.messaging.Rejected;
import org.apache.qpid.proton.amqp.transport.AmqpError;
import org.apache.qpid.proton.amqp.transport.ErrorCondition;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.Command;
import org.eclipse.hono.client.CommandContext;
import org.eclipse.hono.client.CommandTargetMapper;
import org.eclipse.hono.client.DelegatedCommandSender;
import org.eclipse.hono.client.HonoConnection;
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.tracing.TracingHelper;
import org.eclipse.hono.util.CommandConstants;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.DeviceConnectionConstants;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.ResourceIdentifier;
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

    /**
     * Used for integration tests (with only a single instance of each protocol adapter):
     * <p>
     * System property value defining whether incoming command messages on the tenant
     * scoped consumer may be rerouted via the AMQP messaging network to a device-specific
     * consumer even if there is a local handler for the command.<p>
     * The second condition for the rerouting to take place is that the command message
     * contains a {@link #FORCE_COMMAND_REROUTING_APPLICATION_PROPERTY} application
     * property with a {@code true} value.
     */
    private static final Boolean FORCED_COMMAND_REROUTING_ENABLED = Boolean
            .valueOf(System.getProperty("enableForcedCommandRerouting", "false"));
    /**
     * Name of the boolean command message application property with which commands are
     * forced to be rerouted via the AMQP messaging network to a device-specific consumer.
     * Precondition is that the {@link #FORCED_COMMAND_REROUTING_ENABLED} system property
     * is set to {@code true}.
     */
    private static final String FORCE_COMMAND_REROUTING_APPLICATION_PROPERTY = "force-command-rerouting";

    private final HonoConnection connection;
    private final CommandTargetMapper commandTargetMapper;
    private final AdapterInstanceCommandHandler adapterInstanceCommandHandler;
    private final String adapterInstanceId;
    private final CachingClientFactory<DelegatedCommandSender> delegatedCommandSenderFactory;

    /**
     * Creates a new MappingAndDelegatingCommandHandler instance.
     *
     * @param connection The connection to the AMQP network.
     * @param commandTargetMapper The mapper component to determine the command target.
     * @param adapterInstanceCommandHandler The handler to delegate command handling to if the command is to be
     *                                      handled by the local adapter instance.
     * @param adapterInstanceId The id of the protocol adapter instance that this handler is running in.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    public MappingAndDelegatingCommandHandler(final HonoConnection connection,
            final CommandTargetMapper commandTargetMapper,
            final AdapterInstanceCommandHandler adapterInstanceCommandHandler, final String adapterInstanceId) {
        this.connection = Objects.requireNonNull(connection);
        this.commandTargetMapper = Objects.requireNonNull(commandTargetMapper);
        this.adapterInstanceCommandHandler = Objects.requireNonNull(adapterInstanceCommandHandler);
        this.adapterInstanceId = Objects.requireNonNull(adapterInstanceId);

        this.delegatedCommandSenderFactory = new CachingClientFactory<>(connection.getVertx(), s -> s.isOpen());
    }

    /**
     * Delegates an incoming command to the protocol adapter instance that the target
     * device is connected to.
     * <p>
     * Determines the target gateway (if applicable) and protocol adapter instance for an incoming command
     * and delegates the command either to the local AdapterInstanceCommandHandler or to the resulting
     * protocol adapter instance.
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
        if (message.getAddress() == null) {
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

                delegateIncomingCommand(tenantId, originalDeviceId, originalCommandContext, targetDeviceId, targetAdapterInstance);

            } else {
                if (commandTargetResult.cause() instanceof ServiceInvocationException
                        && ((ServiceInvocationException) commandTargetResult.cause()).getErrorCode() == HttpURLConnection.HTTP_NOT_FOUND) {
                    LOG.debug("no target adapter instance found for command for device {}", originalDeviceId);
                    TracingHelper.logError(originalCommandContext.getCurrentSpan(),
                            "no target adapter instance found for command with device id " + originalDeviceId);
                } else {
                    LOG.debug("error getting target gateway and adapter instance for command with device id {}",
                            originalDeviceId, commandTargetResult.cause());
                    TracingHelper.logError(originalCommandContext.getCurrentSpan(),
                            "error getting target gateway and adapter instance for command with device id " + originalDeviceId,
                            commandTargetResult.cause());
                }
                originalCommandContext.release();
            }
        });
    }

    private void delegateIncomingCommand(final String tenantId, final String originalDeviceId,
            final CommandContext originalCommandContext, final String targetDeviceId,
            final String targetAdapterInstance) {

        // note that the command might be invalid here - a matching local handler to reject it (and report metrics) shall be found in that case
        final Command originalCommand = originalCommandContext.getCommand();
        final String targetGatewayId = targetDeviceId.equals(originalDeviceId) ? null : targetDeviceId;

        // if command is targeted at this adapter instance, determine the command handler
        final CommandHandlerWrapper commandHandler = adapterInstanceId.equals(targetAdapterInstance)
                ? adapterInstanceCommandHandler.getDeviceSpecificCommandHandler(tenantId, targetDeviceId)
                : null;

        if (adapterInstanceId.equals(targetAdapterInstance) && commandHandler == null) {
            LOG.info("local command handler not found for target {} {} [{}]",
                    targetDeviceId.equals(originalDeviceId) ? "device" : "gateway", targetDeviceId, originalCommand);
            TracingHelper.logError(originalCommandContext.getCurrentSpan(),
                    "local command handler not found for command; target device: " + targetDeviceId);
            if (originalCommand.isValid()) {
                originalCommandContext.release();
            } else {
                originalCommandContext.reject(getMalformedMessageError());
            }
        } else {
            final boolean forcedCommandReroutingSet = isForcedCommandReroutingSet(originalCommandContext);
            if (commandHandler != null && forcedCommandReroutingSet) { // used for integration tests
                LOG.debug("forced command rerouting is set, skip usage of local {} for {}", commandHandler, originalCommand);
            }
            if (commandHandler != null && !forcedCommandReroutingSet) {
                // Adopt gateway id from command handler if set;
                // for that kind of command handler (gateway subscribing for specific device commands), the
                // gateway information is not stored in the device connection service ("deviceConnectionService.setCommandHandlingAdapterInstance()" doesn't have an extra gateway id parameter);
                // and therefore not set in the commandTargetMapper result
                final String actualGatewayId = commandHandler.getGatewayId() != null
                        ? commandHandler.getGatewayId()
                        : targetGatewayId;
                final CommandContext commandContext = originalCommand.isValid()
                        ? adaptCommandContextToGatewayIfNeeded(originalCommandContext, actualGatewayId)
                        : originalCommandContext;
                LOG.trace("use local {} for {}", commandHandler, commandContext.getCommand());
                commandHandler.handleCommand(commandContext);
            } else if (originalCommand.isValid()) {
                // delegate to matching consumer via downstream peer
                final CommandContext commandContext = adaptCommandContextToGatewayIfNeeded(originalCommandContext, targetGatewayId);
                delegateCommandMessageToAdapterInstance(targetAdapterInstance, commandContext);
            } else {
                // command message is invalid
                originalCommandContext.reject(getMalformedMessageError());
            }
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
            originalCommandContext.getCurrentSpan().log("determined target gateway " + gatewayId);
            if (!originalCommand.isOneWay()) {
                originalCommand.getCommandMessage().setReplyTo(String.format("%s/%s/%s",
                        CommandConstants.NORTHBOUND_COMMAND_RESPONSE_ENDPOINT, tenantId, originalCommand.getReplyToId()));
            }
            final Command command = Command.from(originalCommand.getCommandMessage(), tenantId, gatewayId);
            commandContext = CommandContext.from(command, originalCommandContext.getDelivery(),
                    originalCommandContext.getCurrentSpan());
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
                        TracingHelper.logError(commandContext.getCurrentSpan(),
                                "failed to send command message to downstream peer: " + ar.cause());
                        commandContext.release();
                    }
                });
    }

    private boolean isForcedCommandReroutingSet(final CommandContext commandContext) {
        if (!FORCED_COMMAND_REROUTING_ENABLED || !commandContext.getCommand().isValid()) {
            return false;
        }
        final ApplicationProperties applicationProperties = commandContext.getCommand().getCommandMessage()
                .getApplicationProperties();
        return Boolean.TRUE.equals(MessageHelper.getApplicationProperty(applicationProperties,
                FORCE_COMMAND_REROUTING_APPLICATION_PROPERTY, Boolean.class));
    }

    private Future<DelegatedCommandSender> getOrCreateDelegatedCommandSender(final String protocolAdapterInstanceId) {
        return connection.executeOnContext(result -> {
            delegatedCommandSenderFactory.getOrCreateClient(protocolAdapterInstanceId,
                    () -> DelegatedCommandSenderImpl.create(connection, protocolAdapterInstanceId, null), result);
        });
    }

}
