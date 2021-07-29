/*******************************************************************************
 * Copyright (c) 2020, 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.hono.deviceregistry.service.device;

import java.net.HttpURLConnection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.eclipse.hono.client.ServerErrorException;
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.deviceregistry.service.tenant.NoopTenantInformationService;
import org.eclipse.hono.deviceregistry.service.tenant.TenantInformationService;
import org.eclipse.hono.deviceregistry.util.DeviceRegistryUtils;
import org.eclipse.hono.service.management.Filter;
import org.eclipse.hono.service.management.Id;
import org.eclipse.hono.service.management.OperationResult;
import org.eclipse.hono.service.management.Result;
import org.eclipse.hono.service.management.SearchResult;
import org.eclipse.hono.service.management.Sort;
import org.eclipse.hono.service.management.device.Device;
import org.eclipse.hono.service.management.device.DeviceManagementService;
import org.eclipse.hono.service.management.device.DeviceWithId;
import org.springframework.beans.factory.annotation.Autowired;

import io.opentracing.Span;
import io.vertx.core.Future;

/**
 * An abstract base class implementation for {@link DeviceManagementService}.
 * <p>
 * It checks the parameters, validate tenant using {@link TenantInformationService} and creates {@link DeviceKey} for device operations.
 */
public abstract class AbstractDeviceManagementService implements DeviceManagementService {

    protected TenantInformationService tenantInformationService = new NoopTenantInformationService();

    /**
     * Sets the service to use for checking existence of tenants.
     * <p>
     * If not set, tenant existence will not be verified.
     *
     * @param tenantInformationService The tenant information service.
     */
    @Autowired(required = false)
    public void setTenantInformationService(final TenantInformationService tenantInformationService) {
        this.tenantInformationService = Objects.requireNonNull(tenantInformationService);
    }

    /**
     * Creates a device.
     * <p>
     * This method is invoked by {@link #createDevice(String, Optional, Device, Span)} after all parameter checks
     * have succeeded.
     *
     * @param key The device's key.
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
     */
    protected abstract Future<OperationResult<Id>> processCreateDevice(DeviceKey key, Device device, Span span);

    /**
     * Gets device registration data for a key.
     * <p>
     * This method is invoked by {@link #readDevice(String, String, Span)} after all parameter checks
     * have succeeded.
     *
     * @param key The device's key.
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
     */
    protected abstract Future<OperationResult<Device>> processReadDevice(DeviceKey key, Span span);

    /**
     * Updates device registration data.
     * <p>
     * This method is invoked by {@link #updateDevice(String, String, Device, Optional, Span)} after all parameter checks
     * have succeeded.
     *
     * @param key The device's key.
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
     */
    protected abstract Future<OperationResult<Id>> processUpdateDevice(
            DeviceKey key,
            Device device,
            Optional<String> resourceVersion,
            Span span);

    /**
     * Deletes a device.
     * <p>
     * This method is invoked by {@link #deleteDevice(String, String, Optional, Span)} after all parameter checks
     * have succeeded.
     *
     * @param key The device's key.
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
     */
    protected abstract Future<Result<Void>> processDeleteDevice(DeviceKey key, Optional<String> resourceVersion, Span span);

    /**
     * Finds devices for search criteria.
     * <p>
     * This method is invoked by {@link #searchDevices(String, int, int, List, List, Span)} after all parameter checks
     * have succeeded.
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
     * @return A future indicating the outcome of the operation.
     *         <p>
     *         The future will be succeeded with a result containing the matching devices. Otherwise, the future will
     *         be failed with a {@link org.eclipse.hono.client.ServiceInvocationException} containing an error code
     *         as specified in the Device Registry Management API.
     */
    protected Future<OperationResult<SearchResult<DeviceWithId>>> processSearchDevices(
            final String tenantId,
            final int pageSize,
            final int pageOffset,
            final List<Filter> filters,
            final List<Sort> sortOptions,
            final Span span) {

        return Future.failedFuture(new ServerErrorException(
                tenantId,
                HttpURLConnection.HTTP_NOT_IMPLEMENTED,
                "this implementation does not support the search devices operation"));
    }

    /**
     * Generates a unique device identifier for a given tenant. A default implementation generates a random UUID value.
     *
     * @param tenantId The tenant identifier.
     * @return The device identifier.
     */
    protected String generateDeviceId(final String tenantId) {
        return UUID.randomUUID().toString();
    }

    @Override
    public final Future<OperationResult<Id>> createDevice(
            final String tenantId,
            final Optional<String> deviceId,
            final Device device,
            final Span span) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(deviceId);
        Objects.requireNonNull(device);
        Objects.requireNonNull(span);

        final String deviceIdValue = deviceId.orElseGet(() -> generateDeviceId(tenantId));

        return this.tenantInformationService
                .tenantExists(tenantId, span)
                .compose(result -> result.isError()
                        ? Future.failedFuture(ServiceInvocationException.create(
                                tenantId,
                                result.getStatus(),
                                "tenant does not exist",
                                null))
                        : processCreateDevice(DeviceKey.from(result.getPayload(), deviceIdValue), device, span))
                .recover(t -> DeviceRegistryUtils.mapError(t, tenantId));
    }

    @Override
    public final Future<OperationResult<Device>> readDevice(final String tenantId, final String deviceId, final Span span) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(deviceId);
        Objects.requireNonNull(span);

        return this.tenantInformationService
                .tenantExists(tenantId, span)
                .compose(result -> result.isError()
                        ? Future.failedFuture(ServiceInvocationException.create(
                                tenantId,
                                result.getStatus(),
                                "tenant does not exist",
                                null))
                        : processReadDevice(DeviceKey.from(result.getPayload(), deviceId), span))
                .recover(t -> DeviceRegistryUtils.mapError(t, tenantId));
    }

    @Override
    public final Future<OperationResult<Id>> updateDevice(
            final String tenantId,
            final String deviceId,
            final Device device,
            final Optional<String> resourceVersion,
            final Span span) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(deviceId);
        Objects.requireNonNull(device);
        Objects.requireNonNull(resourceVersion);
        Objects.requireNonNull(span);

        return this.tenantInformationService
                .tenantExists(tenantId, span)
                .compose(result -> result.isError()
                        ? Future.failedFuture(ServiceInvocationException.create(
                                tenantId,
                                result.getStatus(),
                                "tenant does not exist",
                                null))
                        : processUpdateDevice(DeviceKey.from(result.getPayload(), deviceId), device, resourceVersion, span))
                .recover(t -> DeviceRegistryUtils.mapError(t, tenantId));
    }

    @Override
    public final Future<Result<Void>> deleteDevice(
            final String tenantId,
            final String deviceId,
            final Optional<String> resourceVersion,
            final Span span) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(deviceId);
        Objects.requireNonNull(resourceVersion);
        Objects.requireNonNull(span);

        return this.tenantInformationService
                .tenantExists(tenantId, span)
                .compose(result -> result.isError()
                        ? Future.failedFuture(ServiceInvocationException.create(
                                tenantId,
                                result.getStatus(),
                                "tenant does not exist",
                                null))
                        : processDeleteDevice(DeviceKey.from(result.getPayload(), deviceId), resourceVersion, span))
                .recover(t -> DeviceRegistryUtils.mapError(t, tenantId));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final Future<OperationResult<SearchResult<DeviceWithId>>> searchDevices(
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

        if (pageSize <= 0) {
            throw new IllegalArgumentException("page size must be a positive integer");
        }
        if (pageOffset < 0) {
            throw new IllegalArgumentException("page offset must not be negative");
        }

        return this.tenantInformationService
                .tenantExists(tenantId, span)
                .compose(result -> result.isError()
                        ? Future.failedFuture(ServiceInvocationException.create(
                                tenantId,
                                result.getStatus(),
                                "tenant does not exist",
                                null))
                        : processSearchDevices(tenantId, pageSize, pageOffset, filters, sortOptions, span))
                .recover(t -> DeviceRegistryUtils.mapError(t, tenantId));
    }
}
