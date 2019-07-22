/*******************************************************************************
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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

import io.opentracing.SpanContext;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

/**
 * A client for accessing Hono's Device Connection API.
 * <p>
 * An instance of this interface is always scoped to a specific tenant.
 * <p>
 * See Hono's <a href="https://www.eclipse.org/hono/docs/api/device-connection-api/">
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
     * @param context The currently active OpenTracing span or {@code null} if no span is currently active.
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
     * @param context The currently active OpenTracing span or {@code null} if no span is currently active.
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
}
