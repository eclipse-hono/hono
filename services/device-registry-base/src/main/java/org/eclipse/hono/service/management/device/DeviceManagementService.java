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

import java.net.HttpURLConnection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.eclipse.hono.service.management.Filter;
import org.eclipse.hono.service.management.Id;
import org.eclipse.hono.service.management.OperationResult;
import org.eclipse.hono.service.management.Result;
import org.eclipse.hono.service.management.SearchResult;
import org.eclipse.hono.service.management.Sort;

import io.opentracing.Span;
import io.vertx.core.Future;

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
     * @return A future indicating the outcome of the operation.
     *         The <em>status code</em> is set as specified in the 
     *         <a href="https://www.eclipse.org/hono/docs/api/management/#/devices/createDeviceRegistration"> 
     *         Device Registry Management API - Create Device Registration </a>
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
     * @return A future indicating the outcome of the operation.
     *         The <em>status code</em> is set as specified in the 
     *         <a href="https://www.eclipse.org/hono/docs/api/management/#/devices/getRegistration"> 
     *         Device Registry Management API - Get Device Registration </a>
     * @throws NullPointerException if any of the parameters is {@code null}.
     * @see <a href="https://www.eclipse.org/hono/docs/api/management/#/devices/getRegistration">
     *      Device Registry Management API - Get Device Registration</a>
     */
    Future<OperationResult<Device>> readDevice(String tenantId, String deviceId, Span span);

    /**
     * Finds devices belonging to the given tenant with optional filters, paging and sorting options.
     * <p>
     * This search operation is considered as optional since it is not required for the normal functioning of Hono and
     * is more of a convenient operation. Hence here it is declared as a default method which returns
     * {@link HttpURLConnection#HTTP_NOT_IMPLEMENTED}. It is upto the implementors of this interface to offer an
     * implementation of this service or not.
     *
     * @param tenantId The tenant that the devices belong to.
     * @param pageSize The maximum number of results to include in a response.
     * @param pageOffset The offset into the result set from which to include objects in the response. This allows to
     *                   retrieve the whole result set page by page.
     * @param filters A list of filters. The filters are predicates that objects in the result set must match.
     * @param sortOptions A list of sort options. The sortOptions specify properties to sort the result set by.
     * @param span The active OpenTracing span for this operation. It is not to be closed in this method! An
     *            implementation should log (error) events on this span and it may set tags and use this span as the
     *            parent for any spans created in this method.
     * @return A future indicating the outcome of the operation. The <em>status code</em> is set as specified in the
     *         <a href="https://www.eclipse.org/hono/docs/api/management/#/devices/searchDevicesForTenant"> Device
     *         Registry Management API - Search Devices</a>
     * @throws NullPointerException if any of the parameters is {@code null}.
     * @see <a href="https://www.eclipse.org/hono/docs/api/management/#/devices/searchDevicesForTenant"> Device Registry
     *      Management API - Search Devices</a>
     */
    default Future<OperationResult<SearchResult<DeviceWithId>>> searchDevices(
            final String tenantId,
            final int pageSize,
            final int pageOffset,
            final List<Filter> filters,
            final List<Sort> sortOptions,
            final Span span) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(filters);
        Objects.requireNonNull(sortOptions);
        Objects.requireNonNull(span);

        return Future.succeededFuture(OperationResult.empty(HttpURLConnection.HTTP_NOT_IMPLEMENTED));
    }

    /**
     * Updates device registration data.
     *
     * @param tenantId The tenant the device belongs to.
     * @param deviceId The ID of the device to update the registration for.
     * @param device Device information, must not be {@code null}.
     * @param resourceVersion The resource version that the device instance is required to have.
     *                        If empty, the resource version of the device instance on record will be ignored.
     * @param span The active OpenTracing span for this operation. It is not to be closed in this method!
     *          An implementation should log (error) events on this span and it may set tags and use this span as the
     *          parent for any spans created in this method.
     * @return A future indicating the outcome of the operation.
     *         The <em>status code</em> is set as specified in the 
     *         <a href="https://www.eclipse.org/hono/docs/api/management/#/devices/updateRegistration"> 
     *         Device Registry Management API - Update Device Registration </a>
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
     * @param resourceVersion The resource version that the device instance is required to have.
     *                        If empty, the resource version of the device instance on record will be ignored.
     * @param span The active OpenTracing span for this operation. It is not to be closed in this method!
     *          An implementation should log (error) events on this span and it may set tags and use this span as the
     *          parent for any spans created in this method.
     * @return  A future indicating the outcome of the operation.
     *         The <em>status code</em> is set as specified in the 
     *         <a href="https://www.eclipse.org/hono/docs/api/management/#/devices/deleteRegistration"> 
     *         Device Registry Management API - Delete Device Registration </a>
     * @throws NullPointerException if any of the parameters is {@code null}.
     * @see <a href="https://www.eclipse.org/hono/docs/api/management/#/devices/deleteRegistration">
     *      Device Registry Management API - Delete Device Registration</a>
     */
    Future<Result<Void>> deleteDevice(String tenantId, String deviceId, Optional<String> resourceVersion, Span span);
}
