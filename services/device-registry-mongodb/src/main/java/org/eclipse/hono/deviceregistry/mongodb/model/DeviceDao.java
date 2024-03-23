/**
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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


package org.eclipse.hono.deviceregistry.mongodb.model;


import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.eclipse.hono.service.management.Filter;
import org.eclipse.hono.service.management.SearchResult;
import org.eclipse.hono.service.management.Sort;
import org.eclipse.hono.service.management.device.DeviceDto;
import org.eclipse.hono.service.management.device.DeviceWithId;

import io.opentracing.SpanContext;
import io.vertx.core.Future;

/**
 * A data access object for reading and writing device data from/to a persistent store.
 *
 */
public interface DeviceDao {

    /**
     * Initially persists a device instance.
     *
     * @param deviceConfig The device's configuration.
     * @param tracingContext The context to track the processing of the request in
     *                       or {@code null} if no such context exists.
     * @return A future indicating the outcome of the operation.
     *         <p>
     *         The future will be succeeded with the instance's (initial) resource version if the device has
     *         been persisted successfully.
     *         Otherwise, it will be failed with a {@link org.eclipse.hono.client.ServiceInvocationException}.
     * @throws NullPointerException if device configuration is {@code null}.
     */
    Future<String> create(DeviceDto deviceConfig, SpanContext tracingContext);

    /**
     * Gets a device by its identifier.
     *
     * @param tenantId The tenant that the device belongs to.
     * @param deviceId The identifier of the device to retrieve.
     * @param tracingContext The context to track the processing of the request in
     *                       or {@code null} if no such context exists.
     * @return A future indicating the outcome of the operation.
     *         <p>
     *         The future will be succeeded if an instance with the given identifier exists, otherwise
     *         it will be failed with a {@link org.eclipse.hono.client.ServiceInvocationException}.
     * @throws NullPointerException if tenant or device identifier are {@code null}.
     */
    Future<DeviceDto> getById(String tenantId, String deviceId, SpanContext tracingContext);

    /**
     * Resolves a given set of device groups to the (device) identifiers of the groups's members.
     *
     * @param tenantId The tenant that the device groups belong to.
     * @param viaGroups The identifiers of the device groups to resolve.
     * @param tracingContext The context to track the processing of the request in
     *                       or {@code null} if no such context exists.
     * @return A future indicating the outcome of the operation.
     *         <p>
     *         The future will be completed with the set of device identifiers that the groups have been resolved to
     *         or it will be failed with a {@link org.eclipse.hono.client.ServiceInvocationException} if the groups
     *         could not be resolved.
     * @throws NullPointerException if tenant or group IDs are {@code null}.
     */
    Future<Set<String>> resolveGroupMembers(String tenantId, Set<String> viaGroups, SpanContext tracingContext);

    /**
     * Finds devices by search criteria.
     *
     * @param tenantId The tenant that the devices belong to.
     * @param pageSize The maximum number of results to include in a response.
     * @param pageOffset The offset into the result set from which to include objects in the response. This allows to
     *                   retrieve the whole result set page by page.
     * @param filters A list of filters. The filters are predicates that objects in the result set must match.
     * @param sortOptions A list of sort options. The sortOptions specify properties to sort the result set by.
     * @param isGateway A filter for restricting the search to gateway ({@code True}) or edge ({@code False} devices only.
     *                  If <em>empty</em>, the search will not be restricted.
     * @param tracingContext The context to track the processing of the request in
     *                       or {@code null} if no such context exists.
     * @return A future indicating the outcome of the operation.
     *         <p>
     *         The future will be succeeded with a set of matching devices or failed with a
     *         {@link org.eclipse.hono.client.ServiceInvocationException}, if the query could not be
     *         executed.
     * @throws NullPointerException if any of the parameters other than tracing context are {@code null}.
     * @throws IllegalArgumentException if page size is &lt;= 0 or page offset is negative.
     */
    Future<SearchResult<DeviceWithId>> find(
            String tenantId,
            int pageSize,
            int pageOffset,
            List<Filter> filters,
            List<Sort> sortOptions,
            Optional<Boolean> isGateway,
            SpanContext tracingContext);

    /**
     * Updates an existing device.
     *
     * @param deviceConfig The device configuration to set.
     * @param resourceVersion The resource version that the instance is required to have.
     * @param tracingContext The context to track the processing of the request in
     *                       or {@code null} if no such context exists.
     * @return A future indicating the outcome of the operation.
     *         <p>
     *         The future will be succeeded with the new resource version if an instance with a matching
     *         identifier and resource version exists and has been updated.
     *         Otherwise the future will be failed with a {@link org.eclipse.hono.client.ServiceInvocationException}.
     * @throws NullPointerException if device configuration or resource version are {@code null}.
     */
    Future<String> update(
            DeviceDto deviceConfig,
            Optional<String> resourceVersion,
            SpanContext tracingContext);

    /**
     * Deletes an existing device instance.
     *
     * @param tenantId The tenant that the device belongs to.
     * @param deviceId The identifier of the device to delete.
     * @param resourceVersion The resource version that the device is required to match.
     * @param tracingContext The context to track the processing of the request in
     *                       or {@code null} if no such context exists.
     * @return A future indicating the outcome of the operation.
     *         <p>
     *         The future will be succeeded if a device matching the given identifier and resource version
     *         exists and has been deleted.
     *         Otherwise the future will be failed with a {@link org.eclipse.hono.client.ServiceInvocationException}.
     * @throws NullPointerException if tenant ID, device ID or resource version are {@code null}.
     */
    Future<Void> delete(String tenantId, String deviceId, Optional<String> resourceVersion, SpanContext tracingContext);

    /**
     * Deletes all device instances of a tenant.
     *
     * @param tenantId The tenant that the devices belong to.
     * @param tracingContext The context to track the processing of the request in
     *                       or {@code null} if no such context exists.
     * @return A future indicating the outcome of the operation.
     *         <p>
     *         The future will be succeeded if all devices belonging to the given tenant have been deleted.
     *         Otherwise the future will be failed with a {@link org.eclipse.hono.client.ServiceInvocationException}.
     * @throws NullPointerException if tenant ID is {@code null}.
     */
    Future<Void> delete(String tenantId, SpanContext tracingContext);

    /**
     * Gets the number of device instances for a tenant.
     *
     * @param tenantId The tenant to get the number of devices for.
     * @param tracingContext The context to track the processing of the request in
     *                       or {@code null} if no such context exists.
     * @return A future indicating the outcome of the operation.
     *         <p>
     *         The future will be succeeded with the number of devices if the tenant exists.
     *         Otherwise the future will be failed with a {@link org.eclipse.hono.client.ServiceInvocationException}.
     * @throws NullPointerException if tenant ID is {@code null}.
     */
    Future<Long> count(String tenantId, SpanContext tracingContext);
}
