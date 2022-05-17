/*******************************************************************************
 * Copyright (c) 2019, 2022 Contributors to the Eclipse Foundation
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

import java.util.Objects;
import java.util.function.Function;

import io.opentracing.SpanContext;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;

/**
 * Wraps a command handler to be used in a command consumer.
 */
public final class CommandHandlerWrapper {

    private final String tenantId;
    private final String deviceId;
    private final String gatewayId;
    private final Function<CommandContext, Future<Void>> commandHandler;
    private final Context context;
    private final SpanContext consumerCreationSpanContext;

    /**
     * Creates a new CommandHandlerWrapper.
     *
     * @param tenantId The tenant id.
     * @param deviceId The identifier of the device or gateway that is the target of the commands being handled.
     * @param gatewayId The identifier of the gateway in case the handler is used as part of the gateway
     *                  subscribing specifically for commands for the given device, or {@code null} otherwise.
     *                  (A gateway subscribing for commands for all devices, that it may act on behalf of, would mean
     *                  using a {@code null} value here and providing the gateway id in the <em>deviceId</em>
     *                  parameter.)
     * @param commandHandler The command handler.
     * @param context The vert.x context to run the handler on or {@code null} to invoke the handler directly.
     * @param consumerCreationSpanContext The context of the span for tracking the creation of the command consumer
     *                                    that this command handler belongs to. If not {@code null}, the given context
     *                                    is intended to be used by that command consumer for creating a
     *                                    <em>follows-from</em> reference in the span created for the processing of a
     *                                    command request message. Note that providing a span context here only makes
     *                                    sense for short-lived command consumers as created by the HTTP and CoAP
     *                                    adapters for example.
     * @throws NullPointerException If tenantId, deviceId or commandHandler is {@code null}.
     */
    public CommandHandlerWrapper(final String tenantId, final String deviceId, final String gatewayId,
            final Function<CommandContext, Future<Void>> commandHandler, final Context context,
            final SpanContext consumerCreationSpanContext) {
        this.tenantId = Objects.requireNonNull(tenantId);
        this.deviceId = Objects.requireNonNull(deviceId);
        this.gatewayId = gatewayId;
        this.commandHandler = Objects.requireNonNull(commandHandler);
        this.context = context;
        this.consumerCreationSpanContext = consumerCreationSpanContext;
    }

    /**
     * Gets the tenant identifier.
     *
     * @return The identifier.
     */
    public String getTenantId() {
        return tenantId;
    }

    /**
     * Gets the identifier of the device or gateway to handle commands for.
     *
     * @return The identifier.
     */
    public String getDeviceId() {
        return deviceId;
    }

    /**
     * Gets the identifier of the gateway in case the handler is used by a gateway having specifically subscribed for
     * commands for the device returned by {@link #getDeviceId()}.
     *
     * @return The identifier or {@code null}.
     */
    public String getGatewayId() {
        return gatewayId;
    }

    /**
     * The span context of the command consumer creation operation or {@code null} if
     * that context isn't available or shouldn't be used.
     *
     * @return The span context or {@code null}.
     */
    public SpanContext getConsumerCreationSpanContext() {
        return consumerCreationSpanContext;
    }

    /**
     * Invokes the command handler with the given command context.
     *
     * @param commandContext The command context to pass on to the command handler.
     * @return The result of the command handler invocation.
     */
    public Future<Void> handleCommand(final CommandContext commandContext) {
        if (context == null || Vertx.currentContext() == context) {
            return commandHandler.apply(commandContext);
        } else {
            final Promise<Void> resultPromise = Promise.promise();
            context.runOnContext(go -> commandHandler.apply(commandContext).onComplete(resultPromise));
            return resultPromise.future();
        }
    }

    @Override
    public String toString() {
        return "CommandHandlerWrapper{" +
                "tenantId='" + tenantId + '\'' +
                ", deviceId='" + deviceId + '\'' +
                (gatewayId != null ? (", gatewayId='" + gatewayId + '\'') : "") +
                '}';
    }
}
