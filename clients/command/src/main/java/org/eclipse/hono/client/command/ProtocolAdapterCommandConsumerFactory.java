/*******************************************************************************
 * Copyright (c) 2020, 2022 Contributors to the Eclipse Foundation
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

import java.time.Duration;
import java.util.function.Function;

import org.eclipse.hono.util.Lifecycle;

import io.opentracing.SpanContext;
import io.vertx.core.Future;

/**
 * A Protocol Adapter factory for creating consumers for commands targeted at devices.
 */
public interface ProtocolAdapterCommandConsumerFactory extends Lifecycle {

    /**
     * Creates a command consumer for a device.
     * <p>
     * For each device only one command consumer may be active at any given time. Invoking this method multiple times
     * with the same parameters will each time overwrite the previous entry.
     * <p>
     * It is the responsibility of the calling code to properly close a consumer
     * once it is no longer needed by invoking its {@link ProtocolAdapterCommandConsumer#close(boolean, SpanContext)}
     * method.
     *
     * @param tenantId The tenant to consume commands from.
     * @param deviceId The device for which the consumer will be created.
     * @param sendEvent {@code true} if <em>connected notification</em> event should be sent.
     * @param commandHandler The handler to invoke with every command received. The handler must invoke one of the
     *                       terminal methods of the passed in {@link CommandContext} in order to settle the command
     *                       message transfer and finish the trace span associated with the {@link CommandContext}.
     *                       The future returned by the handler indicates the outcome of handling the command.
     * @param lifespan The time period in which the command consumer shall be active. Using a negative duration or
     *                 {@code null} here is interpreted as an unlimited life span. The guaranteed granularity
     *                 taken into account here is seconds.
     * @param context The currently active OpenTracing span context or {@code null} if no span is currently active.
     *                An implementation should use this as the parent for any span it creates for tracing
     *                the execution of this operation.
     * @return A future indicating the outcome of the operation.
     *         <p>
     *         The future will be completed with the newly created consumer once the link
     *         has been established.
     *         <p>
     *         The future will be failed with a {@code org.eclipse.hono.client.ServiceInvocationException} with an
     *         error code indicating the cause of the failure.
     * @throws NullPointerException if any of tenant, device ID or command handler is {@code null}.
     */
    Future<ProtocolAdapterCommandConsumer> createCommandConsumer(
            String tenantId,
            String deviceId,
            boolean sendEvent,
            Function<CommandContext, Future<Void>> commandHandler,
            Duration lifespan,
            SpanContext context);

    /**
     * Creates a command consumer for a device that is connected via a gateway.
     * <p>
     * For each device only one command consumer may be active at any given time. Invoking this method multiple times
     * with the same parameters will each time overwrite the previous entry.
     * <p>
     * It is the responsibility of the calling code to properly close a consumer
     * once it is no longer needed by invoking its {@link ProtocolAdapterCommandConsumer#close(boolean, SpanContext)}
     * method.
     *
     * @param tenantId The tenant to consume commands from.
     * @param deviceId The device for which the consumer will be created.
     * @param gatewayId The gateway that wants to act on behalf of the device.
     * @param sendEvent {@code true} if <em>connected notification</em> event should be sent.
     * @param commandHandler The handler to invoke with every command received. The handler must invoke one of the
     *                       terminal methods of the passed in {@link CommandContext} in order to settle the command
     *                       message transfer and finish the trace span associated with the {@link CommandContext}.
     *                       The future returned by the handler indicates the outcome of handling the command.
     * @param lifespan The time period in which the command consumer shall be active. Using a negative duration or
     *                 {@code null} here is interpreted as an unlimited life span. The guaranteed granularity
     *                 taken into account here is seconds.
     * @param context The currently active OpenTracing span context or {@code null} if no span is currently active.
     *                An implementation should use this as the parent for any span it creates for tracing
     *                the execution of this operation.
     * @return A future indicating the outcome of the operation.
     *         <p>
     *         The future will be completed with the newly created consumer once the link
     *         has been established.
     *         <p>
     *         The future will be failed with a {@code org.eclipse.hono.client.ServiceInvocationException} with an error code indicating
     *         the cause of the failure.
     * @throws NullPointerException if any of tenant, device ID, gateway ID or command handler is {@code null}.
     */
    Future<ProtocolAdapterCommandConsumer> createCommandConsumer(
            String tenantId,
            String deviceId,
            String gatewayId,
            boolean sendEvent,
            Function<CommandContext, Future<Void>> commandHandler,
            Duration lifespan,
            SpanContext context);
}
