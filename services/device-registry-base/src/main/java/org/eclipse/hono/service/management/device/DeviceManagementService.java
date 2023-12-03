/*******************************************************************************
 * Copyright (c) 2016 Contributors to the Eclipse Foundation
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
import java.util.Optional;

import org.eclipse.hono.client.ServerErrorException;
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
     * Creates a device.
     *
     * @param tenantId The tenant that the device belongs to.
     * @param deviceId The identifier that the device should be registered under. If empty, the service implementation
     *                 will create an identifier for the device.
     * @param device The registration information to add for the device.
     * @param span The active OpenTracing span to use for tracking this operation.
     *             <p>
     *             Implementations <em>must not</em> invoke the {@link Span#finish()} nor the {@link Span#finish(long)}
     *             methods. However,implementations may log (error) events on this span, set tags and use this span
     *             as the parent for additional spans created as part of this method's execution.
     * @return A future indicating the outcome of the operation.
     *         <p>
     *         The future will be succeeded with a result containing the created device's identifier if the device
     *         has been created successfully. Otherwise, the future will be failed with a
     *         {@link org.eclipse.hono.client.ServiceInvocationException} containing an error code as specified
     *         in the Device Registry Management API.
     * @throws NullPointerException if any of the parameters are {@code null}.
     * @see <a href="https://www.eclipse.org/hono/docs/api/management/#/devices/createDeviceRegistration">
     *      Device Registry Management API - Create Device Registration</a>
     */
    Future<OperationResult<Id>> createDevice(String tenantId, Optional<String> deviceId, Device device, Span span);

    /**
     * Gets device registration data for a device identifier.
     *
     * @param tenantId The tenant that the device belongs to.
     * @param deviceId The identifier of the device to get registration data for.
     * @param span The active OpenTracing span to use for tracking this operation.
     *             <p>
     *             Implementations <em>must not</em> invoke the {@link Span#finish()} nor the {@link Span#finish(long)}
     *             methods. However,implementations may log (error) events on this span, set tags and use this span
     *             as the parent for additional spans created as part of this method's execution.
     * @return A future indicating the outcome of the operation.
     *         <p>
     *         The future will be succeeded with a result containing the retrieved device information if a device
     *         with the given identifier exists. Otherwise, the future will be failed with a
     *         {@link org.eclipse.hono.client.ServiceInvocationException} containing an error code as specified
     *         in the Device Registry Management API.
     * @throws NullPointerException if any of the parameters are {@code null}.
     * @see <a href="https://www.eclipse.org/hono/docs/api/management/#/devices/getRegistration">
     *      Device Registry Management API - Get Device Registration</a>
     */
    Future<OperationResult<Device>> readDevice(String tenantId, String deviceId, Span span);

    /**
     * Finds devices for search criteria.
     * <p>
     * This search operation is considered as optional since it is not required for the normal functioning of Hono and
     * is more of a convenient operation.
     * <p>
     * This default implementation returns a future failed with a {@link org.eclipse.hono.client.ServerErrorException}
     * having a {@link HttpURLConnection#HTTP_NOT_IMPLEMENTED} status code.
     *
     * @param tenantId The tenant that the devices belong to.
     * @param pageSize The maximum number of results to include in a response.
     * @param pageOffset The offset into the result set from which to include objects in the response. This allows to
     *                   retrieve the whole result set page by page.
     * @param filters A list of filters. The filters are predicates that objects in the result set must match.
     * @param sortOptions A list of sort options. The sortOptions specify properties to sort the result set by.
     * @param span The active OpenTracing span to use for tracking this operation.
     *             <p>
     *             Implementations <em>must not</em> invoke the {@link Span#finish()} nor the {@link Span#finish(long)}
     *             methods. However,implementations may log (error) events on this span, set tags and use this span
     *             as the parent for additional spans created as part of this method's execution.
     * @param isGateway A filter for restricting the search to gateway ({@code True}) or edge ({@code False} devices only.
     *                  If <em>empty</em>, the search will not be restricted.
     * @return A future indicating the outcome of the operation.
     *         <p>
     *         The future will be succeeded with a result containing the matching devices. Otherwise, the future will
     *         be failed with a {@link org.eclipse.hono.client.ServiceInvocationException} containing an error code
     *         as specified in the Device Registry Management API.
     * @throws NullPointerException if any of filters, sort options, gateway filter or tracing span are {@code null}.
     * @throws IllegalArgumentException if page size is &lt;= 0 or page offset is &lt; 0.
     * @see <a href="https://www.eclipse.org/hono/docs/api/management/#/devices/searchDevicesForTenant"> Device Registry
     *      Management API - Search Devices</a>
     */
    default Future<OperationResult<SearchResult<DeviceWithId>>> searchDevices(
            final String tenantId,
            final int pageSize,
            final int pageOffset,
            final List<Filter> filters,
            final List<Sort> sortOptions,
            final Optional<Boolean> isGateway,
            final Span span) {

        return Future.failedFuture(new ServerErrorException(
                tenantId,
                HttpURLConnection.HTTP_NOT_IMPLEMENTED,
                "this implementation does not support the search devices operation"));
    }

    /**
     * Updates device registration data.
     *
     * @param tenantId The tenant the device belongs to.
     * @param deviceId The identifier of the device to update the registration for.
     * @param device Device information, must not be {@code null}.
     * @param resourceVersion The resource version that the device instance is required to have.
     *                        If empty, the resource version of the device instance on record will be ignored.
     * @param span The active OpenTracing span to use for tracking this operation.
     *             <p>
     *             Implementations <em>must not</em> invoke the {@link Span#finish()} nor the {@link Span#finish(long)}
     *             methods. However,implementations may log (error) events on this span, set tags and use this span
     *             as the parent for additional spans created as part of this method's execution.
     * @return A future indicating the outcome of the operation.
     *         <p>
     *         The future will be succeeded with a result containing the updated device's identifier if the device
     *         has been updated successfully. Otherwise, the future will be failed with a
     *         {@link org.eclipse.hono.client.ServiceInvocationException} containing an error code as specified
     *         in the Device Registry Management API.
     * @throws NullPointerException if any of the parameters are {@code null}.
     * @see <a href="https://www.eclipse.org/hono/docs/api/management/#/devices/updateRegistration">
     *      Device Registry Management API - Update Device Registration</a>
     */
    Future<OperationResult<Id>> updateDevice(
            String tenantId,
            String deviceId,
            Device device,
            Optional<String> resourceVersion,
            Span span);

    /**
     * Deletes a device.
     *
     * @param tenantId The tenant the device belongs to.
     * @param deviceId The identifier of the device to remove.
     * @param resourceVersion The resource version that the device instance is required to have.
     *                        If empty, the resource version of the device instance on record will be ignored.
     * @param span The active OpenTracing span to use for tracking this operation.
     *             <p>
     *             Implementations <em>must not</em> invoke the {@link Span#finish()} nor the {@link Span#finish(long)}
     *             methods. However,implementations may log (error) events on this span, set tags and use this span
     *             as the parent for additional spans created as part of this method's execution.
     * @return A future indicating the outcome of the operation.
     *         <p>
     *         The future will be succeeded if a device matching the criteria exists and has been deleted successfully.
     *         Otherwise, the future will be failed with a
     *         {@link org.eclipse.hono.client.ServiceInvocationException} containing an error code as specified
     *         in the Device Registry Management API.
     * @throws NullPointerException if any of the parameters are {@code null}.
     * @see <a href="https://www.eclipse.org/hono/docs/api/management/#/devices/deleteRegistration">
     *      Device Registry Management API - Delete Device Registration</a>
     */
    Future<Result<Void>> deleteDevice(String tenantId, String deviceId, Optional<String> resourceVersion, Span span);

    /**
     * Deletes all devices of a tenant.
     *
     * @param tenantId The tenant that the devices to be deleted belong to.
     * @param span The active OpenTracing span to use for tracking this operation.
     *             <p>
     *             Implementations <em>must not</em> invoke the {@link Span#finish()} nor the {@link Span#finish(long)}
     *             methods. However,implementations may log (error) events on this span, set tags and use this span
     *             as the parent for additional spans created as part of this method's execution.
     * @return A future indicating the outcome of the operation.
     *         <p>
     *         The future will be succeeded if all of the tenant's devices have been deleted successfully.
     *         Otherwise, the future will be failed with a
     *         {@link org.eclipse.hono.client.ServiceInvocationException} containing an error code as specified
     *         in the Device Registry Management API.
     * @throws NullPointerException if any of the parameters are {@code null}.
     * @see <a href="https://www.eclipse.org/hono/docs/api/management/#/devices/deleteDevicesOfTenant">
     *      Device Registry Management API - Delete Devices of Tenant</a>
     */
    Future<Result<Void>> deleteDevicesOfTenant(String tenantId, Span span);
}
