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
package org.eclipse.hono.commandrouter.impl;

import java.net.HttpURLConnection;
import java.util.Objects;

import org.eclipse.hono.adapter.client.command.Command;
import org.eclipse.hono.adapter.client.command.CommandContext;
import org.eclipse.hono.adapter.client.command.InternalCommandSender;
import org.eclipse.hono.adapter.client.registry.TenantClient;
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.commandrouter.CommandTargetMapper;
import org.eclipse.hono.tracing.TenantTraceSamplingHelper;
import org.eclipse.hono.tracing.TracingHelper;
import org.eclipse.hono.util.DeviceConnectionConstants;
import org.eclipse.hono.util.Lifecycle;
import org.eclipse.hono.util.TenantObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Future;

/**
 * Handler for mapping received commands to the corresponding target protocol adapter instance
 * and forwarding them using the {@link org.eclipse.hono.adapter.client.command.InternalCommandSender}.
 */
public abstract class AbstractMappingAndDelegatingCommandHandler implements Lifecycle {

    /**
     * A logger to be shared with subclasses.
     */
    protected final Logger log = LoggerFactory.getLogger(getClass());

    private final TenantClient tenantClient;
    private final CommandTargetMapper commandTargetMapper;
    private final InternalCommandSender internalCommandSender;

    /**
     * Creates a new MappingAndDelegatingCommandHandler instance.
     *
     * @param tenantClient The Tenant service client.
     * @param commandTargetMapper The mapper component to determine the command target.
     * @param internalCommandSender The command sender to publish commands to the internal command topic.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    public AbstractMappingAndDelegatingCommandHandler(
            final TenantClient tenantClient,
            final CommandTargetMapper commandTargetMapper,
            final InternalCommandSender internalCommandSender) {

        this.tenantClient = Objects.requireNonNull(tenantClient);
        this.commandTargetMapper = Objects.requireNonNull(commandTargetMapper);
        this.internalCommandSender = Objects.requireNonNull(internalCommandSender);
    }

    @Override
    public Future<Void> start() {
        return internalCommandSender.start();
    }

    @Override
    public Future<Void> stop() {
        return internalCommandSender.stop();
    }

    /**
     * Delegates an incoming command to the protocol adapter instance that the target
     * device is connected to.
     * <p>
     * Determines the target gateway (if applicable) and protocol adapter instance for an incoming command
     * and delegates the command to the resulting protocol adapter instance.
     *
     * @param commandContext The context of the command to send.
     * @return A future indicating the output of the operation.
     * @throws NullPointerException if the commandContext is {@code null}.
     */
    protected final Future<Void> mapAndDelegateIncomingCommand(final CommandContext commandContext) {
        final Command command = commandContext.getCommand();

        // determine last used gateway device id
        log.trace("determine command target gateway/adapter for [{}]", command);

        final Future<TenantObject> tenantObjectFuture = tenantClient
                .get(command.getTenant(), commandContext.getTracingContext());
        return tenantObjectFuture
                .map(tenantObject -> {
                    TenantTraceSamplingHelper.applyTraceSamplingPriority(tenantObject, null,
                            commandContext.getTracingSpan());
                    return tenantObject;
                })
                .compose(tenantObject -> commandTargetMapper.getTargetGatewayAndAdapterInstance(command.getTenant(),
                        command.getDeviceId(), commandContext.getTracingContext()))
                .recover(cause -> {
                    final String errorMsg;

                    if (tenantObjectFuture.failed()) {
                        errorMsg = "error getting tenant information for tenant " + command.getTenant();
                    } else if (ServiceInvocationException.extractStatusCode(cause) == HttpURLConnection.HTTP_NOT_FOUND) {
                        errorMsg = "no target adapter instance found for command with device id "
                                + command.getDeviceId();
                    } else {
                        errorMsg = "error getting target gateway and adapter instance for command with device id "
                                + command.getDeviceId();
                    }
                    log.debug(errorMsg, cause);
                    TracingHelper.logError(commandContext.getTracingSpan(), errorMsg, cause);
                    commandContext.release(cause);
                    return Future.failedFuture(cause);
                })
                .compose(result -> {
                    final String targetAdapterInstanceId = result
                            .getString(DeviceConnectionConstants.FIELD_ADAPTER_INSTANCE_ID);
                    final String targetDeviceId = result.getString(DeviceConnectionConstants.FIELD_PAYLOAD_DEVICE_ID);
                    final String targetGatewayId = targetDeviceId.equals(command.getDeviceId()) ? null : targetDeviceId;

                    if (Objects.isNull(targetGatewayId)) {
                        log.trace("determined target adapter instance [{}] for [{}] (command not mapped to gateway)",
                                targetAdapterInstanceId, command);
                    } else {
                        command.setGatewayId(targetGatewayId);
                        log.trace("determined target gateway [{}] and adapter instance [{}] for [{}]", targetGatewayId,
                                targetAdapterInstanceId, command);
                        commandContext.getTracingSpan().log("determined target gateway [" + targetGatewayId + "]");
                    }
                    return sendCommand(commandContext, targetAdapterInstanceId);
                });
    }

    /**
     * Sends the given command to the internal Command and Control API endpoint provided by protocol adapters,
     * adhering to the specification of {@link InternalCommandSender#sendCommand(CommandContext, String)}.
     * <p>
     * This default implementation just invokes the {@link InternalCommandSender#sendCommand(CommandContext, String)}
     * method.
     * <p>
     * Subclasses may override this method to do further checks before actually sending the command.
     *
     * @param commandContext Context of the command to send.
     * @param targetAdapterInstanceId The target protocol adapter instance id.
     * @return A future indicating the output of the operation.
     */
    protected Future<Void> sendCommand(final CommandContext commandContext, final String targetAdapterInstanceId) {
        return internalCommandSender.sendCommand(commandContext, targetAdapterInstanceId);
    }
}
