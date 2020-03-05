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
}
