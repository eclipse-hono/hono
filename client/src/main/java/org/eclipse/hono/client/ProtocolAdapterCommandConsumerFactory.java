/*******************************************************************************
 * Copyright (c) 2019, 2020 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.client;

import java.time.Duration;

import org.eclipse.hono.client.impl.ProtocolAdapterCommandConsumerFactoryImpl;

import io.opentracing.SpanContext;
import io.vertx.core.Future;
import io.vertx.core.Handler;

/**
 * A factory for creating clients for the <em>AMQP 1.0 Messaging Network</em> to
 * receive commands and send responses.
 */
public interface ProtocolAdapterCommandConsumerFactory extends ConnectionLifecycle<HonoConnection> {

    /**
     * Creates a new factory for an existing connection.
     *
     * @param connection The connection to the AMQP network.
     * @return The factory.
     * @throws NullPointerException if connection or gatewayMapper is {@code null}.
     */
    static ProtocolAdapterCommandConsumerFactory create(final HonoConnection connection) {
        return create(connection, SendMessageSampler.Factory.noop());
    }

    /**
     * Creates a new factory for an existing connection.
     *
     * @param connection The connection to the AMQP network.
     * @param samplerFactory The sampler factory to use.
     * @return The factory.
     * @throws NullPointerException if connection or gatewayMapper is {@code null}.
     */
    static ProtocolAdapterCommandConsumerFactory create(final HonoConnection connection, final SendMessageSampler.Factory samplerFactory) {
        return new ProtocolAdapterCommandConsumerFactoryImpl(connection, samplerFactory);
    }

    /**
     * Initializes the ProtocolAdapterCommandConsumerFactory with the given components.
     *
     * @param commandTargetMapper The component for mapping an incoming command to the gateway (if applicable) and
     *            protocol adapter instance that can handle it. Note that no initialization of this factory will be done
     *            here, that is supposed to be done by the calling method.
     * @param deviceConnectionClientFactory The factory to create a device connection client instance. Note that no
     *            initialization of this factory will be done here, that is supposed to be done by the calling method.
     */
    void initialize(CommandTargetMapper commandTargetMapper,
            BasicDeviceConnectionClientFactory deviceConnectionClientFactory);

    /**
     * Creates a command consumer for a device.
     * <p>
     * For each device only one command consumer may be active at any given time. Invoking this method multiple times
     * with the same parameters will each time overwrite the previous entry.
     * <p>
     * It is the responsibility of the calling code to properly close a consumer
     * once it is no longer needed by invoking its {@link ProtocolAdapterCommandConsumer#close(SpanContext)}
     * method.
     * <p>
     * Note that {@link #initialize(CommandTargetMapper, BasicDeviceConnectionClientFactory)} has to have been called
     * already, otherwise a failed future is returned.
     *
     * @param tenantId The tenant to consume commands from.
     * @param deviceId The device for which the consumer will be created.
     * @param commandHandler The handler to invoke with every command received. The handler must invoke one of the
     *                       terminal methods of the passed in {@link CommandContext} in order to settle the command
     *                       message transfer and finish the trace span associated with the {@link CommandContext}.
     * @param lifespan The time period in which the command consumer shall be active. Using a negative duration or
     *                 {@code null} here is interpreted as an unlimited lifespan. The guaranteed granularity
     *                 taken into account here is seconds.
     * @param context The currently active OpenTracing span context or {@code null} if no span is currently active.
     *                An implementation should use this as the parent for any span it creates for tracing
     *                the execution of this operation.
     * @return A future indicating the outcome of the operation.
     *         <p>
     *         The future will be completed with the newly created consumer once the link
     *         has been established.
     *         <p>
     *         The future will be failed with a {@link ServiceInvocationException} with an error code indicating
     *         the cause of the failure.
     * @throws NullPointerException if any of tenant, device ID or command handler is {@code null}.
     */
    Future<ProtocolAdapterCommandConsumer> createCommandConsumer(
            String tenantId,
            String deviceId,
            Handler<CommandContext> commandHandler,
            Duration lifespan,
            SpanContext context);

    /**
     * Creates a command consumer for a device that is connected via a gateway.
     * <p>
     * For each device only one command consumer may be active at any given time. Invoking this method multiple times
     * with the same parameters will each time overwrite the previous entry.
     * <p>
     * It is the responsibility of the calling code to properly close a consumer
     * once it is no longer needed by invoking its {@link ProtocolAdapterCommandConsumer#close(SpanContext)}
     * method.
     * <p>
     * Note that {@link #initialize(CommandTargetMapper, BasicDeviceConnectionClientFactory)} has to have been called
     * already, otherwise a failed future is returned.
     *
     * @param tenantId The tenant to consume commands from.
     * @param deviceId The device for which the consumer will be created.
     * @param gatewayId The gateway that wants to act on behalf of the device.
     * @param commandHandler The handler to invoke with every command received. The handler must invoke one of the
     *                       terminal methods of the passed in {@link CommandContext} in order to settle the command
     *                       message transfer and finish the trace span associated with the {@link CommandContext}.
     * @param lifespan The time period in which the command consumer shall be active. Using a negative duration or
     *                 {@code null} here is interpreted as an unlimited lifespan. The guaranteed granularity
     *                 taken into account here is seconds.
     * @param context The currently active OpenTracing span context or {@code null} if no span is currently active.
     *                An implementation should use this as the parent for any span it creates for tracing
     *                the execution of this operation.
     * @return A future indicating the outcome of the operation.
     *         <p>
     *         The future will be completed with the newly created consumer once the link
     *         has been established.
     *         <p>
     *         The future will be failed with a {@link ServiceInvocationException} with an error code indicating
     *         the cause of the failure.
     * @throws NullPointerException if any of tenant, device ID, gateway ID or command handler is {@code null}.
     */
    Future<ProtocolAdapterCommandConsumer> createCommandConsumer(
            String tenantId,
            String deviceId,
            String gatewayId,
            Handler<CommandContext> commandHandler,
            Duration lifespan,
            SpanContext context);

    /**
     * Gets a sender for sending command responses to a business application.
     * <p>
     * It is the responsibility of the calling code to properly close the
     * link by invoking {@link CommandResponseSender#close(Handler)}
     * once the sender is no longer needed anymore.
     *
     * @param tenantId The ID of the tenant to send the command responses for.
     * @param replyId The ID used to build the reply address as {@code command_response/tenantId/replyId}.
     * @return A future that will complete with the sender once the link has been established.
     *         The future will be failed with a {@link ServiceInvocationException} if
     *         the link cannot be established, e.g. because this client is not connected.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    Future<CommandResponseSender> getCommandResponseSender(String tenantId, String replyId);

}
