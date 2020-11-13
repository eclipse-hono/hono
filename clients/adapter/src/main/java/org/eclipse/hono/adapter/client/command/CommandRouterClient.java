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

package org.eclipse.hono.adapter.client.command;

import java.time.Duration;

import org.eclipse.hono.util.Lifecycle;

import io.opentracing.SpanContext;
import io.vertx.core.Future;

/**
 * A client for accessing Hono's Command Router API.
 * <p>
 * See Hono's <a href="https://www.eclipse.org/hono/docs/api/command-router">
 * Command Router API</a> for a description of the status codes returned.
 */
public interface CommandRouterClient extends Lifecycle {

    /**
     * Sets the given gateway as the last gateway that acted on behalf of the given device.
     * <p>
     * If a device connects directly instead of through a gateway, the device identifier itself is to be used as value
     * for the <em>gatewayId</em> parameter.
     *
     * @param tenantId The tenant id.
     * @param deviceId The device id.
     * @param gatewayId The gateway id (or the device id if the last message came from the device directly).
     * @param context The currently active OpenTracing span context or {@code null} if no span is currently active.
     *            An implementation should use this as the parent for any span it creates for tracing
     *            the execution of this operation.
     * @return A future indicating the outcome of the operation.
     *         <p>
     *         The future will be succeeded if the entry was successfully set.
     *         Otherwise the future will be failed with a {@code org.eclipse.hono.client.ServiceInvocationException}.
     * @throws NullPointerException if any of the parameters except context is {@code null}.
     */
    Future<Void> setLastKnownGatewayForDevice(String tenantId, String deviceId, String gatewayId, SpanContext context);

    /**
     * Registers a protocol adapter instance as the consumer of command &amp; control messages
     * for a device.
     *
     * @param tenantId The tenant id.
     * @param deviceId The device id.
     * @param adapterInstanceId The protocol adapter instance id.
     * @param lifespan The lifespan of the registration entry. Using a negative duration or {@code null} here is
     *                 interpreted as an unlimited lifespan. Only the number of seconds in the given duration
     *                 will be taken into account.
     * @param context The currently active OpenTracing span context or {@code null} if no span is currently active.
     *            An implementation should use this as the parent for any span it creates for tracing
     *            the execution of this operation.
     * @return A future indicating the outcome of the operation.
     *         <p>
     *         The future will be succeeded if the consumer was successfully registered.
     *         Otherwise the future will be failed with a {@code org.eclipse.hono.client.ServiceInvocationException}.
     * @throws NullPointerException if tenantId, deviceId or adapterInstanceId is {@code null}.
     */
    Future<Void> registerCommandConsumer(String tenantId, String deviceId, String adapterInstanceId, Duration lifespan,
            SpanContext context);

    /**
     * Unregisters a command consumer for a device.
     * <p>
     * The registration entry is only deleted if the device is currently mapped to the given adapter instance.
     *
     * @param tenantId The tenant id.
     * @param deviceId The device id.
     * @param adapterInstanceId The protocol adapter instance id that the entry to be removed has to contain.
     * @param context The currently active OpenTracing span context or {@code null} if no span is currently active.
     *            An implementation should use this as the parent for any span it creates for tracing
     *            the execution of this operation.
     * @return A future indicating the outcome of the operation.
     *         <p>
     *         The future will be succeeded if the consumer was successfully unregistered.
     *         Otherwise the future will be failed with a {@code org.eclipse.hono.client.ServiceInvocationException}.
     * @throws NullPointerException if any of the parameters except context is {@code null}.
     */
    Future<Void> unregisterCommandConsumer(String tenantId, String deviceId, String adapterInstanceId, SpanContext context);

}
