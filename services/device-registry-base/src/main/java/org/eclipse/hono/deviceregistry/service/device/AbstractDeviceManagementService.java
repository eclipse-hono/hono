/*******************************************************************************
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
 *******************************************************************************/
package org.eclipse.hono.deviceregistry.service.device;

import java.util.Objects;
import java.util.Optional;

import org.eclipse.hono.deviceregistry.service.tenant.TenantInformationService;
import org.eclipse.hono.service.management.Id;
import org.eclipse.hono.service.management.OperationResult;
import org.eclipse.hono.service.management.Result;
import org.eclipse.hono.service.management.device.Device;
import org.eclipse.hono.service.management.device.DeviceManagementService;
import org.springframework.beans.factory.annotation.Autowired;

import io.opentracing.Span;
import io.vertx.core.Future;

/**
 * An abstract base class implementation for {@link DeviceManagementService}.
 * <p>
 * It checks the parameters, validate tenant using {@link TenantInformationService} and creates {@link DeviceKey} for device operations.
 */
public abstract class AbstractDeviceManagementService implements DeviceManagementService {


    protected TenantInformationService tenantInformationService;

    /**
     * Set tenant information service.
     * @param tenantInformationService The tenant information service.
     */
    @Autowired
    public void setTenantInformationService(final TenantInformationService tenantInformationService) {
        this.tenantInformationService = tenantInformationService;
    }

    /**
     * Create a device with a specified key and value object.
     *
     * @param key The device key object.
     * @param device The device value object.
     * @param span The active OpenTracing span for this operation.
     * @return A future indicating the outcome of the operation.
     */
    protected abstract Future<OperationResult<Id>> processCreateDevice(DeviceKey key, Device device, Span span);

    /**
     * Read a device with a specified key.
     *
     * @param key The device key object.
     * @param span The active OpenTracing span for this operation.
     * @return A future indicating the outcome of the operation.
     */
    protected abstract Future<OperationResult<Device>> processReadDevice(DeviceKey key, Span span);

    /**
     * Update a device with a specified key and value object.
     *
     * @param key The device key object.
     * @param device The device value object.
     * @param resourceVersion The identifier of the resource version to update.
     * @param span The active OpenTracing span for this operation.
     * @return A future indicating the outcome of the operation.
     */
    protected abstract Future<OperationResult<Id>> processUpdateDevice(DeviceKey key, Device device, Optional<String> resourceVersion, Span span);

    /**
     * Delete a device with a specified key.
     *
     * @param key The device key object.
     * @param resourceVersion The identifier of the resource version to update.
     * @param span The active OpenTracing span for this operation.
     * @return A future indicating the outcome of the operation.
     */
    protected abstract Future<Result<Void>> processDeleteDevice(DeviceKey key, Optional<String> resourceVersion, Span span);

    /**
     * Generates a unique device identifier for a given tenant.
     *
     * @param tenantId The tenant identifier.
     * @return The device identifier.
     */
    protected abstract String generateDeviceId(String tenantId);

    @Override
    public Future<OperationResult<Id>> createDevice(final String tenantId, final Optional<String> deviceId, final Device device, final Span span) {

        Objects.requireNonNull(tenantId);
        final String deviceIdValue = deviceId.orElseGet(() -> generateDeviceId(tenantId));

        return this.tenantInformationService
                .tenantExists(tenantId, span)
                .flatMap(tenantKey -> processCreateDevice(DeviceKey.from(tenantKey, deviceIdValue), device, span));

    }

    @Override
    public Future<OperationResult<Device>> readDevice(final String tenantId, final String deviceId, final Span span) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(deviceId);

        return this.tenantInformationService
                .tenantExists(tenantId, span)
                .flatMap(tenantKey -> processReadDevice(DeviceKey.from(tenantKey, deviceId), span));

    }

    @Override
    public Future<OperationResult<Id>> updateDevice(final String tenantId, final String deviceId, final Device device, final Optional<String> resourceVersion, final Span span) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(deviceId);
        Objects.requireNonNull(resourceVersion);

        return this.tenantInformationService
                .tenantExists(tenantId, span)
                .flatMap(tenantKey -> processUpdateDevice(DeviceKey.from(tenantKey, deviceId), device, resourceVersion, span));

    }

    @Override
    public Future<Result<Void>> deleteDevice(final String tenantId, final String deviceId, final Optional<String> resourceVersion, final Span span) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(deviceId);
        Objects.requireNonNull(resourceVersion);

        return this.tenantInformationService
                .tenantExists(tenantId, span)
                .flatMap(tenantKey -> processDeleteDevice(DeviceKey.from(tenantKey, deviceId), resourceVersion, span));

    }
}
