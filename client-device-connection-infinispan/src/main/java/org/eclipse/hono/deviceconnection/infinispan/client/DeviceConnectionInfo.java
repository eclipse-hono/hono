/**
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
 */

package org.eclipse.hono.deviceconnection.infinispan.client;

import java.time.Duration;
import java.util.Set;

import io.opentracing.Span;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

/**
 * A repository for keeping connection information about devices.
 *
 */
public interface DeviceConnectionInfo {

    /**
     * Sets the gateway that last acted on behalf of a device.
     * <p>
     * If a device connects directly instead of through a gateway, the device identifier itself is to be used as value
     * for the <em>gatewayId</em> parameter.
     *
     * @param tenant The tenant that the device belongs to.
     * @param deviceId The device identifier.
     * @param gatewayId The gateway identifier. This may be the same as the device identifier if the device is
     *                  (currently) not connected via a gateway but directly to a protocol adapter.
     * @param span The active OpenTracing span for this operation. It is not to be closed in this method!
     *            An implementation should log (error) events on this span and it may set tags and use this span as the
     *            parent for any spans created in this method.
     * @return A future indicating the outcome of the operation.
     *         <p>
     *         The future will be succeeded if the device connection information has been updated.
     *         Otherwise the future will be failed with a {@link org.eclipse.hono.client.ServiceInvocationException}.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    Future<Void> setLastKnownGatewayForDevice(String tenant, String deviceId, String gatewayId, Span span);

    /**
     * Gets the gateway that last acted on behalf of a device.
     * <p>
     * If no last known gateway has been set for the given device yet, a failed future with status
     * <em>404</em> is returned.
     *
     * @param tenant The tenant that the device belongs to.
     * @param deviceId The device identifier.
     * @param span The active OpenTracing span for this operation. It is not to be closed in this method!
     *            An implementation should log (error) events on this span and it may set tags and use this span as the
     *            parent for any spans created in this method.
     * @return A future indicating the outcome of the operation.
     *         <p>
     *         The future will be succeeded with a JSON object containing the currently mapped gateway ID
     *         in the <em>gateway-id</em> property, if device connection information has been found for
     *         the given device.
     *         Otherwise the future will be failed with a {@link org.eclipse.hono.client.ServiceInvocationException}.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    Future<JsonObject> getLastKnownGatewayForDevice(String tenant, String deviceId, Span span);

    /**
     * Sets the protocol adapter instance that handles commands for the given device or gateway.
     *
     * @param tenantId The tenant id.
     * @param deviceId The device id.
     * @param adapterInstanceId The protocol adapter instance id.
     * @param lifespan The lifespan of the mapping entry. Using a negative duration or {@code null} here is
     *                 interpreted as an unlimited lifespan.
     * @param span The active OpenTracing span for this operation. It is not to be closed in this method!
     *            An implementation should log (error) events on this span and it may set tags and use this span as the
     *            parent for any spans created in this method.
     * @return A future indicating the outcome of the operation.
     *         <p>
     *         The future will be succeeded if the device connection information has been updated.
     *         Otherwise the future will be failed with a {@link org.eclipse.hono.client.ServiceInvocationException}.
     * @throws NullPointerException if any of the parameters except lifespan is {@code null}.
     */
    Future<Void> setCommandHandlingAdapterInstance(String tenantId, String deviceId, String adapterInstanceId,
            Duration lifespan, Span span);

    /**
     * Removes the mapping information that associates the given device with the given protocol adapter instance
     * that handles commands for the given device. The mapping entry is only deleted if its value
     * contains the given protocol adapter instance id.
     *
     * @param tenantId The tenant id.
     * @param deviceId The device id.
     * @param adapterInstanceId The protocol adapter instance id that the entry to be removed has to contain.
     * @param span The active OpenTracing span for this operation. It is not to be closed in this method!
     *            An implementation should log (error) events on this span and it may set tags and use this span as the
     *            parent for any spans created in this method.
     * @return A future indicating the outcome of the operation, with its value indicating whether the protocol
     *         adapter instance value was removed or not.
     *         <p>
     *         The future will be failed with a {@link org.eclipse.hono.client.ServiceInvocationException} if there
     *         was an error removing the value.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    Future<Boolean> removeCommandHandlingAdapterInstance(String tenantId, String deviceId, String adapterInstanceId, Span span);

    /**
     * Gets information about the adapter instances that can handle a command for the given device.
     * <p>
     * See Hono's <a href="https://www.eclipse.org/hono/docs/api/device-connection/">Device Connection API
     * specification</a> for a detailed description of the method's behaviour and the returned JSON object.
     * <p>
     * If no adapter instances are found, the returned future is failed.
     *
     * @param tenantId The tenant id.
     * @param deviceId The device id.
     * @param viaGateways The set of gateways that may act on behalf of the given device.
     * @param span The active OpenTracing span for this operation. It is not to be closed in this method!
     *            An implementation should log (error) events on this span and it may set tags and use this span as the
     *            parent for any spans created in this method.
     * @return A future indicating the outcome of the operation.
     *         <p>
     *         If instances were found, the future will be succeeded with a JSON object containing one or more mappings
     *         from device id to adapter instance id.
     *         Otherwise the future will be failed with a {@link org.eclipse.hono.client.ServiceInvocationException}.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    Future<JsonObject> getCommandHandlingAdapterInstances(String tenantId, String deviceId, Set<String> viaGateways, Span span);
}
