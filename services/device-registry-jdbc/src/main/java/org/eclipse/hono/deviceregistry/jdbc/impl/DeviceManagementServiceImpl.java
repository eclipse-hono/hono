/*******************************************************************************
 * Copyright (c) 2020, 2023 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.deviceregistry.jdbc.impl;

import java.net.HttpURLConnection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.deviceregistry.jdbc.config.DeviceServiceOptions;
import org.eclipse.hono.deviceregistry.service.device.AbstractDeviceManagementService;
import org.eclipse.hono.deviceregistry.service.device.DeviceKey;
import org.eclipse.hono.service.base.jdbc.store.device.TableManagementStore;
import org.eclipse.hono.service.management.Filter;
import org.eclipse.hono.service.management.Id;
import org.eclipse.hono.service.management.OperationResult;
import org.eclipse.hono.service.management.Result;
import org.eclipse.hono.service.management.SearchResult;
import org.eclipse.hono.service.management.Sort;
import org.eclipse.hono.service.management.device.Device;
import org.eclipse.hono.service.management.device.DeviceWithId;
import org.eclipse.hono.util.CacheDirective;

import io.opentracing.Span;
import io.vertx.core.Future;
import io.vertx.core.Vertx;

/**
 * Implementation of the <em>device management service</em>.
 */
public class DeviceManagementServiceImpl extends AbstractDeviceManagementService {

    private final TableManagementStore store;
    private final Optional<CacheDirective> ttl;
    private final DeviceServiceOptions config;

    /**
     * Create a new instance.
     *
     * @param vertx The vert.x instance to use.
     * @param store The backing store to use.
     * @param properties The service properties.
     */
    public DeviceManagementServiceImpl(final Vertx vertx, final TableManagementStore store, final DeviceServiceOptions properties) {
        super(vertx);
        this.store = store;
        this.ttl = Optional.of(CacheDirective.maxAgeDirective(properties.registrationTtl()));
        this.config = properties;
    }

    @Override
    protected Future<OperationResult<Id>> processCreateDevice(final DeviceKey key, final Device device, final Span span) {

        return this.tenantInformationService.getTenant(key.getTenantId(), span)
                .compose(tenant -> this.store.createDevice(
                        key,
                        device,
                        tenant,
                        config.maxDevicesPerTenant(),
                        span.context()))
                .map(r -> OperationResult
                        .ok(
                                HttpURLConnection.HTTP_CREATED,
                                Id.of(key.getDeviceId()),
                                Optional.empty(),
                                Optional.of(r.getVersion()))
                )

                .recover(Services::recover);

    }

    @Override
    protected Future<OperationResult<Device>> processReadDevice(final DeviceKey key, final Span span) {

        return this.store.readDevice(key, span.context())

                .map(r -> r
                        .map(result -> OperationResult.ok(
                                HttpURLConnection.HTTP_OK,
                                result.getDevice(),
                                this.ttl,
                                result.getResourceVersion()))
                        .orElseThrow(() -> new ClientErrorException(
                                key.getTenantId(),
                                HttpURLConnection.HTTP_NOT_FOUND,
                                "no such device")));

    }

    @Override
    protected Future<OperationResult<Id>> processUpdateDevice(final DeviceKey key, final Device device, final Optional<String> resourceVersion, final Span span) {

        return this.store.updateDevice(key, device, resourceVersion, span.context())

                .map(r -> OperationResult.ok(
                        HttpURLConnection.HTTP_NO_CONTENT,
                        Id.of(key.getDeviceId()),
                        Optional.empty(),
                        Optional.of(r.getVersion())))

                .recover(Services::recover);

    }

    @Override
    protected Future<Result<Void>> processDeleteDevice(final DeviceKey key, final Optional<String> resourceVersion, final Span span) {

        return this.store
                .deleteDevice(key, resourceVersion, span.context())
                .map(r -> {
                    if (r.getUpdated() <= 0) {
                        throw new ClientErrorException(
                                key.getTenantId(),
                                HttpURLConnection.HTTP_NOT_FOUND,
                                "no such device");
                    } else {
                        return Result.<Void>from(HttpURLConnection.HTTP_NO_CONTENT);
                    }
                })
                .recover(Services::recover);

    }

    @Override
    protected Future<Result<Void>> processDeleteDevicesOfTenant(final String tenantId, final Span span) {

        return this.store
                .dropTenant(tenantId, span.context())
                .map(r -> Result.<Void>from(HttpURLConnection.HTTP_NO_CONTENT))
                .recover(Services::recover);
    }

    @Override
    protected String generateDeviceId(final String tenantId) {
        return UUID.randomUUID().toString();
    }

    @Override
    protected Future<OperationResult<SearchResult<DeviceWithId>>> processSearchDevices(
            final String tenantId,
            final int pageSize,
            final int pageOffset,
            final List<Filter> filters,
            final List<Sort> sortOptions,
            final Optional<Boolean> isGateway,
            final Span span) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(span);

        return store.findDevices(tenantId, pageSize, pageOffset, filters, isGateway, span.context())
                .map(result -> OperationResult.ok(
                        HttpURLConnection.HTTP_OK,
                        result,
                        Optional.empty(),
                        Optional.empty()))
                .recover(Services::recover);
    }
}
