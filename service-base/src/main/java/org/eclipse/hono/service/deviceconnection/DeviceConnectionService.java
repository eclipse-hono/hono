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

package org.eclipse.hono.service.deviceconnection;

import java.util.List;

import org.eclipse.hono.util.DeviceConnectionResult;

import io.opentracing.Span;
import io.vertx.core.Future;

/**
 * A service for keeping record of device connection information.
 *
 * @see <a href="https://www.eclipse.org/hono/docs/api/device-connection/">Device Connection API</a>
 */
public interface DeviceConnectionService {

    /**
     * Sets the given gateway as the last gateway that acted on behalf of the given device.
     * <p>
     * If a device connects directly instead of through a gateway, the device identifier is to be used as value for
     * the <em>gatewayId</em> parameter.
     *
     * @param tenantId The tenant id.
     * @param deviceId The device id.
     * @param gatewayId The gateway id (or the device id if the last message came from the device directly).
     * @param span The active OpenTracing span for this operation. It is not to be closed in this method!
     *            An implementation should log (error) events on this span and it may set tags and use this span as the
     *            parent for any spans created in this method.
     * @return A future indicating the outcome of the operation.
     *             The <em>status</em> will be <em>204 No Content</em> if the operation completed successfully.
     *             <br>
     *             An implementation may return a <em>404 Not Found</em> status in order to indicate that 
     *             no device and/or gateway with the given identifier exists for the given tenant.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    Future<DeviceConnectionResult> setLastKnownGatewayForDevice(String tenantId, String deviceId, String gatewayId,
            Span span);

    /**
     * Gets the gateway that last acted on behalf of the given device.
     * <p>
     * If no last known gateway has been set for the given device yet, the result handler is invoked with a <em>404 Not
     * Found</em> status result.
     *
     * @param tenantId The tenant id.
     * @param deviceId The device id.
     * @param span The active OpenTracing span for this operation. It is not to be closed in this method! An
     *            implementation should log (error) events on this span and it may set tags and use this span as the
     *            parent for any spans created in this method.
     * @return A future indicating the outcome of the operation.
     *            The <em>status</em> will be
     *            <ul>
     *            <li><em>200 OK</em> if a result could be determined. The <em>payload</em>
     *            will contain a <em>device-id</em> property with the gateway id.</li>
     *            <li><em>404 Not Found</em> if there is no last known gateway assigned to the device</li>
     *            </ul>
     *            An implementation may return a <em>404 Not Found</em> status in order to indicate that
     *            no device with the given identifier exists for the given tenant.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    Future<DeviceConnectionResult> getLastKnownGatewayForDevice(String tenantId, String deviceId, Span span);

    /**
     * Sets the protocol adapter instance that handles commands for the given device or gateway.
     *
     * @param tenantId The tenant id.
     * @param deviceId The device id.
     * @param adapterInstanceId The protocol adapter instance id.
     * @param span The active OpenTracing span for this operation. It is not to be closed in this method! An
     *            implementation should log (error) events on this span and it may set tags and use this span as the
     *            parent for any spans created in this method.
     * @return A future indicating the outcome of the operation.
     *         The <em>status</em> will be <em>204 No Content</em> if the operation completed successfully.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    Future<DeviceConnectionResult> setCommandHandlingAdapterInstance(String tenantId, String deviceId, String adapterInstanceId, Span span);

    /**
     * Removes the mapping information that associates the given device with the given protocol adapter instance
     * that handles commands for the given device. The mapping entry is only deleted if its value
     * contains the given protocol adapter instance id.
     *
     * @param tenantId The tenant id.
     * @param deviceId The device id.
     * @param adapterInstanceId The protocol adapter instance id that the entry to be removed has to contain.
     * @param span The active OpenTracing span for this operation. It is not to be closed in this method! An
     *            implementation should log (error) events on this span and it may set tags and use this span as the
     *            parent for any spans created in this method.
     * @return A future indicating the outcome of the operation.
     *         The <em>status</em> will be <em>204 No Content</em> if the entry was successfully removed.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    Future<DeviceConnectionResult> removeCommandHandlingAdapterInstance(String tenantId, String deviceId, String adapterInstanceId, Span span);

    /**
     * Gets information about the adapter instances that can handle a command for the given device.
     * <p>
     * See Hono's <a href="https://www.eclipse.org/hono/docs/api/device-connection/">Device Connection API
     * specification</a> for a detailed description of the method's behaviour and the returned JSON object.
     * <p>
     * If no adapter instances are found, the result handler will be invoked with an error status.
     *
     * @param tenantId The tenant id.
     * @param deviceId The device id.
     * @param viaGateways The list of gateways that may act on behalf of the given device.
     * @param span The currently active OpenTracing span or {@code null} if no span is currently active.
     *            Implementing classes should use this as the parent for any span they create for tracing
     *            the execution of this operation.
     * @return A future indicating the outcome of the operation.
     *         The <em>status</em> will be
     *         <ul>
     *         <li><em>200 OK</em> if instances have been found. The
     *         <em>payload</em> will consist of the JSON object containing one or more mappings from device id
     *         to adapter instance id.</li>
     *         <li><em>404 Not Found</em> if no instances were found.</li>
     *         </ul>
     * @throws NullPointerException if any of the parameters except context is {@code null}.
     */
    Future<DeviceConnectionResult> getCommandHandlingAdapterInstances(String tenantId, String deviceId, List<String> viaGateways, Span span);
}
