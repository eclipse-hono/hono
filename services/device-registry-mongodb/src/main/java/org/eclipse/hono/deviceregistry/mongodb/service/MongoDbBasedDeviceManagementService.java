/*******************************************************************************
 * Copyright (c) 2021, 2023 Contributors to the Eclipse Foundation
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
package org.eclipse.hono.deviceregistry.mongodb.service;

import java.net.HttpURLConnection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.eclipse.hono.deviceregistry.mongodb.config.MongoDbBasedRegistrationConfigProperties;
import org.eclipse.hono.deviceregistry.mongodb.model.CredentialsDao;
import org.eclipse.hono.deviceregistry.mongodb.model.DeviceDao;
import org.eclipse.hono.deviceregistry.service.device.AbstractDeviceManagementService;
import org.eclipse.hono.deviceregistry.service.device.DeviceKey;
import org.eclipse.hono.deviceregistry.util.DeviceRegistryUtils;
import org.eclipse.hono.service.management.Filter;
import org.eclipse.hono.service.management.Id;
import org.eclipse.hono.service.management.OperationResult;
import org.eclipse.hono.service.management.Result;
import org.eclipse.hono.service.management.SearchResult;
import org.eclipse.hono.service.management.Sort;
import org.eclipse.hono.service.management.credentials.CredentialsDto;
import org.eclipse.hono.service.management.device.Device;
import org.eclipse.hono.service.management.device.DeviceDto;
import org.eclipse.hono.service.management.device.DeviceWithId;
import org.eclipse.hono.service.management.tenant.Tenant;
import org.eclipse.hono.tracing.TracingHelper;

import io.opentracing.Span;
import io.vertx.core.Future;
import io.vertx.core.Vertx;

/**
 * A device management service that uses a Mongo DB for persisting data.
 * <p>
 * This implementation also creates an empty set of credentials for a device created by
 * {@link #createDevice(String, Optional, Device, Span)} and deletes all credentials on record
 * for a device deleted by {@link MongoDbBasedDeviceManagementService#deleteDevice(String, String, Optional, Span)}.
 *
 * @see <a href="https://www.eclipse.org/hono/docs/api/management/">Device Registry Management API</a>
 */
public final class MongoDbBasedDeviceManagementService extends AbstractDeviceManagementService {

    private final MongoDbBasedRegistrationConfigProperties config;
    private final DeviceDao deviceDao;
    private final CredentialsDao credentialsDao;

    /**
     * Creates a new service for configuration properties.
     *
     * @param vertx The vert.x instance to use.
     * @param deviceDao The data access object to use for accessing device data in the MongoDB.
     * @param credentialsDao The data access object to use for accessing credentials data in the MongoDB.
     * @param config The properties for configuring this service.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    public MongoDbBasedDeviceManagementService(
            final Vertx vertx,
            final DeviceDao deviceDao,
            final CredentialsDao credentialsDao,
            final MongoDbBasedRegistrationConfigProperties config) {
        super(vertx);
        Objects.requireNonNull(deviceDao);
        Objects.requireNonNull(credentialsDao);
        Objects.requireNonNull(config);

        this.deviceDao = deviceDao;
        this.credentialsDao = credentialsDao;
        this.config = config;
    }

    @Override
    protected Future<OperationResult<Id>> processCreateDevice(
            final DeviceKey key,
            final Device device,
            final Span span) {

        return tenantInformationService.getTenant(key.getTenantId(), span)
                .compose(tenant -> checkDeviceLimitReached(tenant, key.getTenantId(), span))
                .compose(ok -> {
                    final DeviceDto deviceDto = DeviceDto.forCreation(
                            DeviceDto::new,
                            key.getTenantId(),
                            key.getDeviceId(),
                            device,
                            DeviceRegistryUtils.getUniqueIdentifier());
                    return deviceDao.create(deviceDto, span.context());
                })
                .compose(deviceResourceVersion -> {
                    final var emptySetOfCredentials = CredentialsDto.forCreation(
                            key.getTenantId(),
                            key.getDeviceId(),
                            List.of(),
                            DeviceRegistryUtils.getUniqueIdentifier());
                    return credentialsDao.create(emptySetOfCredentials, span.context())
                            .map(deviceResourceVersion)
                            .recover(t -> {
                                TracingHelper.logError(
                                        span,
                                        "failed to create device with empty set of credentials, rolling back ...",
                                        t);
                                return deviceDao.delete(key.getTenantId(), key.getDeviceId(), Optional.empty(), span.context())
                                        .compose(done -> Future.<String>failedFuture(t))
                                        .recover(error -> Future.<String>failedFuture(t));
                            });
                })
                .map(deviceResourceVersion -> OperationResult.ok(
                            HttpURLConnection.HTTP_CREATED,
                            Id.of(key.getDeviceId()),
                            Optional.empty(),
                            Optional.of(deviceResourceVersion)));
    }

    @Override
    protected Future<OperationResult<Device>> processReadDevice(final DeviceKey key, final Span span) {

        return deviceDao.getById(key.getTenantId(), key.getDeviceId(), span.context())
                .map(deviceDto -> OperationResult.ok(
                                HttpURLConnection.HTTP_OK,
                                deviceDto.getDeviceWithStatus(),
                                Optional.ofNullable(DeviceRegistryUtils.getCacheDirective(config.getCacheMaxAge())),
                                Optional.ofNullable(deviceDto.getVersion())));
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
        Objects.requireNonNull(filters);
        Objects.requireNonNull(sortOptions);
        Objects.requireNonNull(isGateway);
        Objects.requireNonNull(span);

        return tenantInformationService.getTenant(tenantId, span)
                .compose(ok -> deviceDao.find(tenantId, pageSize, pageOffset, filters, sortOptions, isGateway, span.context()))
                .map(result -> OperationResult.ok(
                        HttpURLConnection.HTTP_OK,
                        result,
                        Optional.empty(),
                        Optional.empty()));
    }

    @Override
    protected Future<OperationResult<Id>> processUpdateDevice(
            final DeviceKey key,
            final Device device,
            final Optional<String> resourceVersion,
            final Span span) {

        return deviceDao.getById(key.getTenantId(), key.getDeviceId(), span.context())
            .map(currentDeviceConfig -> DeviceDto.forUpdate(
                        // use creation date from DB as this will never change
                        () -> currentDeviceConfig,
                        // but copy all other (updated) data from parameters that have been passed in
                        key.getTenantId(),
                        key.getDeviceId(),
                        device,
                        DeviceRegistryUtils.getUniqueIdentifier()))
            .compose(updatedDeviceConfig -> deviceDao.update(updatedDeviceConfig, resourceVersion, span.context()))
            .map(newResourceVersion -> OperationResult.ok(
                    HttpURLConnection.HTTP_NO_CONTENT,
                    Id.of(key.getDeviceId()),
                    Optional.empty(),
                    Optional.of(newResourceVersion)));
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation also deletes the device's credentials. However, it does not use a transaction
     * for doing so. This may lead to a situation where the device is deleted but the credentials still
     * exist if an error occurs when trying to delete the credentials.
     */
    @Override
    protected Future<Result<Void>> processDeleteDevice(
            final DeviceKey key,
            final Optional<String> resourceVersion,
            final Span span) {

        return deviceDao.delete(key.getTenantId(), key.getDeviceId(), resourceVersion, span.context())
                .compose(ok -> credentialsDao.delete(
                        key.getTenantId(),
                        key.getDeviceId(),
                        Optional.empty(),
                        span.context())
                    // errors occurring while deleting credentials will already
                    // have been logged by the credentials DAO
                    // TODO use transaction spanning both collections?
                    .recover(t -> Future.succeededFuture()))
                .map(ok -> Result.<Void> from(HttpURLConnection.HTTP_NO_CONTENT));
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation also deletes the devices' credentials. However, it does not use a transaction
     * for doing so. This may lead to a situation where the devices have been deleted but the credentials still
     * exist if an error occurs when trying to delete the credentials.
     */
    @Override
    protected Future<Result<Void>> processDeleteDevicesOfTenant(final String tenantId, final Span span) {

        return deviceDao.delete(tenantId, span.context())
                .compose(ok -> credentialsDao.delete(tenantId, span.context())
                    // errors occurring while deleting credentials will already
                    // have been logged by the credentials DAO
                    // TODO use transaction spanning both collections?
                    .recover(t -> Future.succeededFuture()))
                .map(ok -> Result.<Void> from(HttpURLConnection.HTTP_NO_CONTENT));
    }

    private Future<Void> checkDeviceLimitReached(final Tenant tenant, final String tenantId, final Span span) {

        return deviceDao.count(tenantId, span.context())
                .compose(currentDeviceCount -> tenant.checkDeviceLimitReached(
                        tenantId,
                        currentDeviceCount,
                        config.getMaxDevicesPerTenant()));
    }
}
