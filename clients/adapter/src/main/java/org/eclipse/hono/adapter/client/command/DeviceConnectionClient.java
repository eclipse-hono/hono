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
import java.util.List;

import io.opentracing.SpanContext;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

/**
 * A client for accessing Hono's Device Connection API.
 * <p>
 * See Hono's <a href="https://www.eclipse.org/hono/docs/api/device-connection/">
 * Device Connection API specification</a> for a description of the result codes returned.
 */
public interface DeviceConnectionClient extends CommandRouterClient {

    /**
     * Sets the protocol adapter instance that handles commands for the given device.
     *
     * @param tenant The tenant that the device belongs to.
     * @param deviceId The device id.
     * @param adapterInstanceId The protocol adapter instance id.
     * @param lifespan The lifespan of the mapping entry. Using a negative duration or {@code null} here is
     *                 interpreted as an unlimited lifespan. Only the number of seconds in the given duration
     *                 will be taken into account.
     * @param context The currently active OpenTracing span context or {@code null} if no span is currently active.
     *            An implementation should use this as the parent for any span it creates for tracing
     *            the execution of this operation.
     * @return A future indicating whether the operation succeeded or not.
     * @throws NullPointerException if tenant, device id or adapter instance id are {@code null}.
     */
    Future<Void> setCommandHandlingAdapterInstance(
            String tenant,
            String deviceId,
            String adapterInstanceId,
            Duration lifespan,
            SpanContext context);

    /**
     * Removes the mapping information that associates the given device with the given protocol adapter instance
     * that handles commands for the given device. The mapping entry is only deleted if its value
     * contains the given protocol adapter instance id.
     *
     * @param tenant The tenant that the device belongs to.
     * @param deviceId The device id.
     * @param adapterInstanceId The protocol adapter instance id that the entry to be removed has to contain.
     * @param context The currently active OpenTracing span context or {@code null} if no span is currently active.
     *            An implementation should use this as the parent for any span it creates for tracing
     *            the execution of this operation.
     * @return A future indicating the outcome of the operation.
     *         <p>
     *         The future will be succeeded if the entry was successfully removed.
     *         Otherwise the future will be failed with a {@code org.eclipse.hono.client.ServiceInvocationException}.
     * @throws NullPointerException if tenant, device id or adapter instance id are {@code null}.
     */
    Future<Void> removeCommandHandlingAdapterInstance(
            String tenant,
            String deviceId,
            String adapterInstanceId,
            SpanContext context);

    /**
     * Gets information about the adapter instances that can handle a command for the given device.
     * <p>
     * See Hono's <a href="https://www.eclipse.org/hono/docs/api/device-connection/">Device Connection API
     * specification</a> for a detailed description of the method's behavior and the returned JSON object.
     * <p>
     * If no adapter instances are found, the returned future is failed.
     *
     * @param tenant The tenant that the device belongs to.
     * @param deviceId The device id.
     * @param viaGateways The list of gateways that may act on behalf of the given device.
     * @param context The currently active OpenTracing span context or {@code null} if no span is currently active.
     *            Implementing classes should use this as the parent for any span they create for tracing the execution
     *            of this operation.
     * @return A future indicating the outcome of the operation.
     *         <p>
     *         If instances were found, the future will be succeeded with a JSON object containing one or more mappings
     *         from device id to adapter instance id. Otherwise the future will be failed with a
     *         {@code org.eclipse.hono.client.ServiceInvocationException}.
     * @throws NullPointerException if any of the parameters except context are {@code null}.
     */
    Future<JsonObject> getCommandHandlingAdapterInstances(
            String tenant,
            String deviceId,
            List<String> viaGateways,
            SpanContext context);
}
