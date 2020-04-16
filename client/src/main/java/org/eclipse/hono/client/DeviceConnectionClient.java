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

import java.util.List;

import io.opentracing.SpanContext;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

/**
 * A client for accessing Hono's Device Connection API.
 * <p>
 * An instance of this interface is always scoped to a specific tenant.
 * <p>
 * See Hono's <a href="https://www.eclipse.org/hono/docs/api/device-connection/">
 * Device Connection API specification</a> for a description of the result codes returned.
 */
public interface DeviceConnectionClient extends RequestResponseClient {

    /**
     * Sets the given gateway as the last gateway that acted on behalf of the given device.
     * <p>
     * If a device connects directly instead of through a gateway, the device identifier itself is to be used as value
     * for the <em>gatewayId</em> parameter.
     *
     * @param deviceId The device id.
     * @param gatewayId The gateway id (or the device id if the last message came from the device directly).
     * @param context The currently active OpenTracing span context or {@code null} if no span is currently active.
     *            An implementation should use this as the parent for any span it creates for tracing
     *            the execution of this operation.
     * @return A future indicating whether the operation succeeded or not.
     * @throws NullPointerException if device id or gateway id is {@code null}.
     */
    Future<Void> setLastKnownGatewayForDevice(String deviceId, String gatewayId, SpanContext context);

    /**
     * Gets the gateway that last acted on behalf of the given device.
     * <p>
     * If no last known gateway has been set for the given device yet, a failed future with status <em>Not Found</em>
     * is returned.
     *
     * @param deviceId The device id.
     * @param context The currently active OpenTracing span context or {@code null} if no span is currently active.
     *            An implementation should use this as the parent for any span it creates for tracing
     *            the execution of this operation.
     * @return A future indicating the result of the operation.
     *         <p>
     *         The future will succeed if a response with status 200 has been received from the device connection service.
     *         In that case the value of the future will contain a <em>gateway-id</em> property with the
     *         gateway id.
     *         <p>
     *         In case a status other then 200 is received, the future will fail with a
     *         {@link ServiceInvocationException} containing the (error) status code returned by the service.
     * @throws NullPointerException if device id is {@code null}.
     */
    Future<JsonObject> getLastKnownGatewayForDevice(String deviceId, SpanContext context);

    /**
     * Sets the protocol adapter instance that handles commands for the given device.
     *
     * @param deviceId The device id.
     * @param adapterInstanceId The protocol adapter instance id.
     * @param lifespanSeconds The lifespan of the mapping entry in seconds. A negative value is interpreted as an
     *            unlimited lifespan.
     * @param context The currently active OpenTracing span context or {@code null} if no span is currently active.
     *            An implementation should use this as the parent for any span it creates for tracing
     *            the execution of this operation.
     * @return A future indicating whether the operation succeeded or not.
     * @throws NullPointerException if device id or adapter instance id is {@code null}.
     */
    Future<Void> setCommandHandlingAdapterInstance(String deviceId, String adapterInstanceId, int lifespanSeconds,
            SpanContext context);

    /**
     * Removes the mapping information that associates the given device with the given protocol adapter instance
     * that handles commands for the given device. The mapping entry is only deleted if its value
     * contains the given protocol adapter instance id.
     *
     * @param deviceId The device id.
     * @param adapterInstanceId The protocol adapter instance id that the entry to be removed has to contain.
     * @param context The currently active OpenTracing span context or {@code null} if no span is currently active.
     *            An implementation should use this as the parent for any span it creates for tracing
     *            the execution of this operation.
     * @return A future indicating the outcome of the operation.
     *         <p>
     *         The future will be succeeded if the entry was successfully removed.
     *         Otherwise the future will be failed with a {@link org.eclipse.hono.client.ServiceInvocationException}.
     * @throws NullPointerException if device id or adapter instance id is {@code null}.
     */
    Future<Void> removeCommandHandlingAdapterInstance(String deviceId, String adapterInstanceId, SpanContext context);

    /**
     * Gets information about the adapter instances that can handle a command for the given device.
     * <p>
     * See Hono's <a href="https://www.eclipse.org/hono/docs/api/device-connection/">Device Connection API
     * specification</a> for a detailed description of the method's behaviour and the returned JSON object.
     * <p>
     * If no adapter instances are found, the returned future is failed.
     *
     * @param deviceId The device id.
     * @param viaGateways The list of gateways that may act on behalf of the given device.
     * @param context The currently active OpenTracing span context or {@code null} if no span is currently active.
     *            Implementing classes should use this as the parent for any span they create for tracing the execution
     *            of this operation.
     * @return A future indicating the outcome of the operation.
     *         <p>
     *         If instances were found, the future will be succeeded with a JSON object containing one or more mappings
     *         from device id to adapter instance id. Otherwise the future will be failed with a
     *         {@link org.eclipse.hono.client.ServiceInvocationException}.
     * @throws NullPointerException if any of the parameters except context is {@code null}.
     */
    Future<JsonObject> getCommandHandlingAdapterInstances(String deviceId, List<String> viaGateways, SpanContext context);
}
