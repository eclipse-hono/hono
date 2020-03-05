/*******************************************************************************
 * Copyright (c) 2016, 2020 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.service.management.device;

import io.opentracing.Span;
import io.vertx.core.Future;

import java.util.Optional;

import org.eclipse.hono.service.management.Id;
import org.eclipse.hono.service.management.OperationResult;
import org.eclipse.hono.service.management.Result;

/**
 * A service for managing device registration information.
 * <p>
 * The methods defined by this interface represent the <em>devices</em> resources
 * of Hono's <a href="https://www.eclipse.org/hono/docs/api/management/">Device Registry Management API</a>.
 */
public interface DeviceManagementService {

    /**
     * Registers a device.
     *
     * @param tenantId The tenant that the device belongs to.
     * @param deviceId The ID the device should be registered under.
     * @param device Device information, must not be {@code null}.
     * @param span The active OpenTracing span for this operation. It is not to be closed in this method!
     *          An implementation should log (error) events on this span and it may set tags and use this span as the
     *          parent for any spans created in this method.
     * @return A future indicating the outcome of the operation. The <em>status</em> will be
     *            <ul>
     *            <li><em>201 Created</em> if the device has been registered successfully.</li>
     *            <li><em>409 Conflict</em> if a device with the given identifier already exists for the tenant.</li>
     *            </ul>
     * @throws NullPointerException if any of tenant, device ID or result handler is {@code null}.
     * @see <a href="https://www.eclipse.org/hono/docs/api/management/#/devices/createDeviceRegistration">
     *      Device Registry Management API - Create Device Registration</a>
     */
    Future<OperationResult<Id>> createDevice(String tenantId, Optional<String> deviceId, Device device, Span span);

    /**
     * Gets device registration data by device ID.
     *
     * @param tenantId The tenant the device belongs to.
     * @param deviceId The ID of the device to get registration data for.
     * @param span The active OpenTracing span for this operation. It is not to be closed in this method!
     *          An implementation should log (error) events on this span and it may set tags and use this span as the
     *          parent for any spans created in this method.
     * @return A future indicating the outcome of the operation. The <em>status</em> will be
     *            <ul>
     *            <li><em>200 OK</em> if a device with the given ID is registered for the tenant. The <em>payload</em>
     *            will contain the properties registered for the device.</li>
     *            <li><em>404 Not Found</em> if no device with the given identifier is registered for the tenant.</li>
     *            </ul>
     * @throws NullPointerException if any of the parameters is {@code null}.
     * @see <a href="https://www.eclipse.org/hono/docs/api/management/#/devices/getRegistration">
     *      Device Registry Management API - Get Device Registration</a>
     */
    Future<OperationResult<Device>> readDevice(String tenantId, String deviceId, Span span);

    /**
     * Updates device registration data.
     *
     * @param tenantId The tenant the device belongs to.
     * @param deviceId The ID of the device to update the registration for.
     * @param device Device information, must not be {@code null}.
     * @param resourceVersion The identifier of the resource version to update.
     * @param span The active OpenTracing span for this operation. It is not to be closed in this method!
     *          An implementation should log (error) events on this span and it may set tags and use this span as the
     *          parent for any spans created in this method.
     * @return A future indicating the outcome of the operation. The <em>status</em> will be
     *            <ul>
     *            <li><em>204 No Content</em> if the registration information has been updated successfully.</li>
     *            <li><em>404 Not Found</em> if no device with the given identifier is registered for the tenant.</li>
     *            </ul>
     * @throws NullPointerException if any of tenant, device ID or result handler is {@code null}.
     * @see <a href="https://www.eclipse.org/hono/docs/api/management/#/devices/updateRegistration">
     *      Device Registry Management API - Update Device Registration</a>
     */
    Future<OperationResult<Id>> updateDevice(String tenantId, String deviceId, Device device,
            Optional<String> resourceVersion, Span span);

    /**
     * Removes a device.
     *
     * @param tenantId The tenant the device belongs to.
     * @param deviceId The ID of the device to remove.
     * @param resourceVersion The identifier of the resource version to remove.
     * @param span The active OpenTracing span for this operation. It is not to be closed in this method!
     *          An implementation should log (error) events on this span and it may set tags and use this span as the
     *          parent for any spans created in this method.
     * @return  A future indicating the outcome of the operation. The <em>status</em> will be
     *             <ul>
     *             <li><em>204 No Content</em> if the device has been removed successfully.</li>
     *             <li><em>404 Not Found</em> if no device with the given identifier is
     *             registered for the tenant.</li>
     *             </ul>
     * @throws NullPointerException if any of the parameters is {@code null}.
     * @see <a href="https://www.eclipse.org/hono/docs/api/management/#/devices/deleteRegistration">
     *      Device Registry Management API - Delete Device Registration</a>
     */
    Future<Result<Void>> deleteDevice(String tenantId, String deviceId, Optional<String> resourceVersion, Span span);
}
