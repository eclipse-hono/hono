/*******************************************************************************
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
 *******************************************************************************/
package org.eclipse.hono.deviceregistry.mongodb.service;

import java.net.HttpURLConnection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.deviceregistry.mongodb.config.MongoDbBasedRegistrationConfigProperties;
import org.eclipse.hono.deviceregistry.mongodb.model.CredentialsDao;
import org.eclipse.hono.deviceregistry.mongodb.model.DeviceDao;
import org.eclipse.hono.deviceregistry.mongodb.model.MongoDbBasedDeviceDto;
import org.eclipse.hono.deviceregistry.service.device.AbstractDeviceManagementService;
import org.eclipse.hono.deviceregistry.service.device.DeviceKey;
import org.eclipse.hono.deviceregistry.util.DeviceRegistryUtils;
import org.eclipse.hono.deviceregistry.util.Versioned;
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
import org.eclipse.hono.service.management.tenant.RegistrationLimits;
import org.eclipse.hono.service.management.tenant.Tenant;
import org.eclipse.hono.tracing.TracingHelper;

import io.opentracing.Span;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;

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
     * @param deviceDao The data access object to use for accessing device data in the MongoDB.
     * @param credentialsDao The data access object to use for accessing credentials data in the MongoDB.
     * @param config The properties for configuring this service.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    public MongoDbBasedDeviceManagementService(
            final DeviceDao deviceDao,
            final CredentialsDao credentialsDao,
            final MongoDbBasedRegistrationConfigProperties config) {

        Objects.requireNonNull(deviceDao);
        Objects.requireNonNull(credentialsDao);
        Objects.requireNonNull(config);

        this.deviceDao = deviceDao;
        this.credentialsDao = credentialsDao;
        this.config = config;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Future<OperationResult<Id>> processCreateDevice(
            final DeviceKey key,
            final Device device,
            final Span span) {

        return tenantInformationService.getTenant(key.getTenantId(), span)
                .compose(tenant -> checkDeviceLimitReached(tenant, key.getTenantId(), span))
                .compose(ok -> {
                    final DeviceDto deviceDto = DeviceDto.forCreation(
                            MongoDbBasedDeviceDto::new,
                            key.getTenantId(),
                            key.getDeviceId(),
                            device,
                            DeviceRegistryUtils.getUniqueIdentifier());
                    final var emptySetOfCredentials = CredentialsDto.forCreation(
                            key.getTenantId(),
                            key.getDeviceId(),
                            List.of(),
                            DeviceRegistryUtils.getUniqueIdentifier());
                    final Future<String> createDeviceResult = deviceDao.create(deviceDto, span.context());
                    final Future<String> createCredentialsResult = credentialsDao.create(emptySetOfCredentials, span.context());
                    return CompositeFuture.join(createDeviceResult, createCredentialsResult)
                            .map(entitiesCreated -> createDeviceResult.result())
                            .recover(t -> {
                                TracingHelper.logError(span, "failed to create device with empty set of credentials, rolling back ...", t);
                                return CompositeFuture.join(
                                        deviceDao.delete(key.getTenantId(), key.getDeviceId(), Optional.empty(), span.context()),
                                        credentialsDao.delete(key.getTenantId(), key.getDeviceId(), Optional.empty(), span.context()))
                                    .compose(done -> Future.<String>failedFuture(t))
                                    .recover(error -> Future.<String>failedFuture(t));
                            });
                })
                .map(deviceResourceVersion -> OperationResult.ok(
                            HttpURLConnection.HTTP_CREATED,
                            Id.of(key.getDeviceId()),
                            Optional.empty(),
                            Optional.of(deviceResourceVersion)))
                .otherwise(error -> DeviceRegistryUtils.mapErrorToResult(error, span));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Future<OperationResult<Device>> processReadDevice(final DeviceKey key, final Span span) {

        return deviceDao.getById(key.getTenantId(), key.getDeviceId(), span.context())
                .map(deviceDto -> OperationResult.ok(
                                HttpURLConnection.HTTP_OK,
                                deviceDto.getDeviceWithStatus(),
                                Optional.ofNullable(DeviceRegistryUtils.getCacheDirective(config.getCacheMaxAge())),
                                Optional.ofNullable(deviceDto.getVersion())))
                .otherwise(error -> DeviceRegistryUtils.mapErrorToResult(error, span));
    }

    @Override
    public Future<OperationResult<SearchResult<DeviceWithId>>> searchDevices(
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

        return tenantInformationService.getTenant(tenantId, span)
                .compose(ok -> deviceDao.find(tenantId, pageSize, pageOffset, filters, sortOptions, span.context()))
                .map(result -> OperationResult.ok(
                        HttpURLConnection.HTTP_OK,
                        result,
                        Optional.empty(),
                        Optional.empty()))
                .otherwise(error -> DeviceRegistryUtils.mapErrorToResult(error, span));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Future<OperationResult<Id>> processUpdateDevice(
            final DeviceKey key,
            final Device device,
            final Optional<String> resourceVersion,
            final Span span) {

        final DeviceDto deviceDto = DeviceDto.forUpdate(
                MongoDbBasedDeviceDto::new,
                key.getTenantId(),
                key.getDeviceId(),
                device,
                new Versioned<>(device).getVersion());

        return deviceDao.update(deviceDto, resourceVersion, span.context())
                .map(newResourceVersion -> OperationResult.ok(
                        HttpURLConnection.HTTP_NO_CONTENT,
                        Id.of(key.getDeviceId()),
                        Optional.empty(),
                        Optional.of(newResourceVersion)))
                .otherwise(error -> DeviceRegistryUtils.mapErrorToResult(error, span));
    }

    /**
     * {@inheritDoc}
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
                    // ignore errors occurring while deleting credentials
                    // TODO use transaction spanning both collections?
                    .recover(t -> Future.succeededFuture()))
                .map(ok -> Result.<Void> from(HttpURLConnection.HTTP_NO_CONTENT))
                .otherwise(error -> DeviceRegistryUtils.mapErrorToResult(error, span));
    }

    private Future<Void> checkDeviceLimitReached(final Tenant tenant, final String tenantId, final Span span) {

        final boolean isLimitedAtTenantLevel = Optional.ofNullable(tenant.getRegistrationLimits())
                .map(RegistrationLimits::isNumberOfDevicesLimited)
                .orElse(false);

        if (!config.isNumberOfDevicesPerTenantLimited() && !isLimitedAtTenantLevel) {
            return Future.succeededFuture();
        }

        final int maxNumberOfDevices;
        if (isLimitedAtTenantLevel) {
            maxNumberOfDevices = tenant.getRegistrationLimits().getMaxNumberOfDevices();
        } else {
            maxNumberOfDevices = config.getMaxDevicesPerTenant();
        }

        return deviceDao.count(tenantId, span.context())
                .compose(existingNoOfDevices -> {
                    if (existingNoOfDevices >= maxNumberOfDevices) {
                        return Future.failedFuture(
                                new ClientErrorException(
                                        HttpURLConnection.HTTP_FORBIDDEN,
                                        String.format(
                                                "configured device limit reached [tenant-id: %s, max devices: %d]",
                                                tenantId, maxNumberOfDevices)));
                    } else {
                        return Future.succeededFuture();
                    }
                });
    }
}
