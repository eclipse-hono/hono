/*******************************************************************************
 * Copyright (c) 2021, 2022 Contributors to the Eclipse Foundation
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

import org.eclipse.hono.deviceregistry.mongodb.config.MongoDbBasedTenantsConfigProperties;
import org.eclipse.hono.deviceregistry.mongodb.model.TenantDao;
import org.eclipse.hono.deviceregistry.service.tenant.AbstractTenantManagementService;
import org.eclipse.hono.deviceregistry.util.DeviceRegistryUtils;
import org.eclipse.hono.service.management.Filter;
import org.eclipse.hono.service.management.Id;
import org.eclipse.hono.service.management.OperationResult;
import org.eclipse.hono.service.management.Result;
import org.eclipse.hono.service.management.SearchResult;
import org.eclipse.hono.service.management.Sort;
import org.eclipse.hono.service.management.tenant.Tenant;
import org.eclipse.hono.service.management.tenant.TenantDto;
import org.eclipse.hono.service.management.tenant.TenantWithId;

import io.opentracing.Span;
import io.vertx.core.Future;
import io.vertx.core.Vertx;

/**
 * A tenant management service that persists data in a MongoDB collection.
 *
 * @see <a href="https://www.eclipse.org/hono/docs/api/management/">Device Registry Management API</a>
 */
public final class MongoDbBasedTenantManagementService extends AbstractTenantManagementService {

    private final TenantDao dao;
    private final MongoDbBasedTenantsConfigProperties config;

    /**
     * Creates a new service for configuration properties.
     *
     * @param vertx The vert.x instance to use.
     * @param tenantDao The data access object to use for accessing data in the MongoDB.
     * @param config The properties for configuring this service.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    public MongoDbBasedTenantManagementService(
            final Vertx vertx,
            final TenantDao tenantDao,
            final MongoDbBasedTenantsConfigProperties config) {
        super(vertx);
        Objects.requireNonNull(tenantDao);
        Objects.requireNonNull(config);

        this.dao = tenantDao;
        this.config = config;
    }

    @Override
    protected Future<OperationResult<Id>> processCreateTenant(
            final String tenantId,
            final Tenant tenantObj,
            final Span span) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(tenantObj);
        Objects.requireNonNull(span);

        final TenantDto tenantDto = TenantDto.forCreation(tenantId, tenantObj, DeviceRegistryUtils.getUniqueIdentifier());

        return dao.create(tenantDto, span.context())
            .map(resourceVersion -> {
                return OperationResult.ok(
                        HttpURLConnection.HTTP_CREATED,
                        Id.of(tenantId),
                        Optional.empty(),
                        Optional.of(resourceVersion));
            });
    }

    @Override
    protected Future<OperationResult<Tenant>> processReadTenant(final String tenantId, final Span span) {
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(span);

        return dao.getById(tenantId, span.context())
                .map(dto -> OperationResult.ok(
                        HttpURLConnection.HTTP_OK,
                        dto.getData(),
                        Optional.ofNullable(DeviceRegistryUtils.getCacheDirective(config.getCacheMaxAge())),
                        Optional.ofNullable(dto.getVersion())));
    }

    @Override
    public Future<OperationResult<Void>> processUpdateTenant(
            final String tenantId,
            final Tenant tenantObj,
            final Optional<String> resourceVersion,
            final Span span) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(tenantObj);
        Objects.requireNonNull(resourceVersion);
        Objects.requireNonNull(span);

        return dao.getById(tenantId, span.context())
                .map(currentTenantConfig -> TenantDto.forUpdate(
                            // use creation date from DB as this will never change
                            () -> currentTenantConfig,
                            // but use updated tenant configuration that has been passed in
                            tenantObj,
                            DeviceRegistryUtils.getUniqueIdentifier()))
                .compose(updatedTenantConfig -> dao.update(updatedTenantConfig, resourceVersion, span.context()))
                .map(newVersion -> OperationResult.ok(
                        HttpURLConnection.HTTP_NO_CONTENT,
                        (Void) null,
                        Optional.empty(),
                        Optional.of(newVersion)));
    }

    @Override
    protected Future<Result<Void>> processDeleteTenant(
            final String tenantId,
            final Optional<String> resourceVersion,
            final Span span) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(resourceVersion);
        Objects.requireNonNull(span);

        return dao.delete(tenantId, resourceVersion, span.context())
                .map(ok -> Result.<Void> from(HttpURLConnection.HTTP_NO_CONTENT));
    }

    @Override
    protected Future<OperationResult<SearchResult<TenantWithId>>> processSearchTenants(
            final int pageSize,
            final int pageOffset,
            final List<Filter> filters,
            final List<Sort> sortOptions,
            final Span span) {

        Objects.requireNonNull(filters);
        Objects.requireNonNull(sortOptions);
        Objects.requireNonNull(span);

        return dao.find(pageSize, pageOffset, filters, sortOptions, span.context())
                .map(result -> OperationResult.ok(
                                HttpURLConnection.HTTP_OK,
                                result,
                                Optional.empty(),
                                Optional.empty()));
    }
}
