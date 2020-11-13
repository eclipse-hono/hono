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

package org.eclipse.hono.commandrouter;

import java.time.Duration;

import org.eclipse.hono.client.CommandTargetMapper;
import org.eclipse.hono.client.ConnectionLifecycle;
import org.eclipse.hono.client.HonoConnection;
import org.eclipse.hono.client.SendMessageSampler;
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.commandrouter.impl.amqp.ProtonBasedCommandConsumerFactoryImpl;

import io.opentracing.SpanContext;
import io.vertx.core.Future;

/**
 * A factory for creating clients for the <em>AMQP 1.0 Messaging Network</em> to
 * receive commands and send responses.
 */
public interface CommandConsumerFactory extends ConnectionLifecycle<HonoConnection> {

    /**
     * Creates a new factory for an existing connection.
     *
     * @param connection The connection to the AMQP network.
     * @return The factory.
     * @throws NullPointerException if connection or gatewayMapper is {@code null}.
     */
    static CommandConsumerFactory create(final HonoConnection connection) {
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
    static CommandConsumerFactory create(final HonoConnection connection, final SendMessageSampler.Factory samplerFactory) {
        return new ProtonBasedCommandConsumerFactoryImpl(connection, samplerFactory);
    }

    /**
     * Initializes the ProtocolAdapterCommandConsumerFactory with the given components.
     *
     * @param commandTargetMapper The component for mapping an incoming command to the gateway (if applicable) and
     *            protocol adapter instance that can handle it. Note that no initialization of this factory will be done
     *            here, that is supposed to be done by the calling method.
     */
    void initialize(CommandTargetMapper commandTargetMapper);

    /**
     * Creates a command consumer for a device.
     * <p>
     * For each device only one command consumer may be active at any given time. Invoking this method multiple times
     * with the same parameters will each time overwrite the previous entry.
     * <p>
     * Note that {@link #initialize(CommandTargetMapper)} has to have been called already, otherwise a failed future
     * is returned.
     *
     * @param tenantId The tenant to consume commands from.
     * @param deviceId The device for which the consumer will be created.
     * @param adapterInstanceId The protocol adapter instance id.
     * @param lifespan The time period in which the command consumer shall be active. Using a negative duration or
     *                 {@code null} here is interpreted as an unlimited lifespan. The guaranteed granularity
     *                 taken into account here is seconds.
     * @param context The currently active OpenTracing span context or {@code null} if no span is currently active.
     *                An implementation should use this as the parent for any span it creates for tracing
     *                the execution of this operation.
     * @return A future indicating the outcome of the operation.
     *         <p>
     *         The future will be failed with a {@link ServiceInvocationException} with an error code indicating
     *         the cause of the failure.
     * @throws NullPointerException if any of tenant, device ID or command handler is {@code null}.
     */
    Future<Void> createCommandConsumer(
            String tenantId,
            String deviceId,
            String adapterInstanceId,
            Duration lifespan,
            SpanContext context);

    /**
     *
     * @param tenantId The tenant to consume commands from.
     * @param deviceId The device for which the consumer will be created.
     * @param adapterInstanceId The protocol adapter instance id.
     * @param context The currently active OpenTracing span context or {@code null} if no span is currently active.
     *                An implementation should use this as the parent for any span it creates for tracing
     *                the execution of this operation.
     * @return A future indicating the outcome of the operation.
     *         <p>
     *         The future will be failed with a {@link ServiceInvocationException} with an error code indicating
     *         the cause of the failure.
     * @throws NullPointerException if any of tenant, device ID, gateway ID or command handler is {@code null}.
     */
    Future<Void> removeCommandConsumer(String tenantId, String deviceId, String adapterInstanceId, SpanContext context);

}
