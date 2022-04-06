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

package org.eclipse.hono.deviceregistry.mongodb.service;

import java.net.HttpURLConnection;
import java.util.Objects;

import javax.security.auth.x500.X500Principal;

import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.deviceregistry.mongodb.config.MongoDbBasedTenantsConfigProperties;
import org.eclipse.hono.deviceregistry.mongodb.model.TenantDao;
import org.eclipse.hono.deviceregistry.service.tenant.AbstractTenantService;
import org.eclipse.hono.deviceregistry.util.DeviceRegistryUtils;
import org.eclipse.hono.util.TenantResult;

import io.opentracing.Span;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

/**
 * A Tenant service implementation that retrieves tenant data from a MongoDB.
 */
public final class MongoDbBasedTenantService extends AbstractTenantService {

    private final TenantDao dao;
    private final MongoDbBasedTenantsConfigProperties config;

    /**
     * Creates a new service for a data access object.
     *
     * @param tenantDao The data access object to use for accessing data in the MongoDB.
     * @param config The properties for configuring this service.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    public MongoDbBasedTenantService(
            final TenantDao tenantDao,
            final MongoDbBasedTenantsConfigProperties config) {

        Objects.requireNonNull(tenantDao);
        Objects.requireNonNull(config);

        this.dao = tenantDao;
        this.config = config;
    }

    @Override
    public Future<TenantResult<JsonObject>> get(final String tenantId, final Span span) {
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(span);

        return dao.getByIdOrAlias(tenantId, span.context())
                .map(tenantDto -> TenantResult.from(
                            HttpURLConnection.HTTP_OK,
                            DeviceRegistryUtils.convertTenant(tenantDto.getTenantId(), tenantDto.getData(), true),
                            DeviceRegistryUtils.getCacheDirective(config.getCacheMaxAge())))
                .otherwise(t -> TenantResult.from(ServiceInvocationException.extractStatusCode(t)));
    }

    @Override
    public Future<TenantResult<JsonObject>> get(final X500Principal subjectDn, final Span span) {
        Objects.requireNonNull(subjectDn);
        Objects.requireNonNull(span);

        return dao.getBySubjectDn(subjectDn, span.context())
            .map(tenantDto -> TenantResult.from(
                        HttpURLConnection.HTTP_OK,
                        DeviceRegistryUtils.convertTenant(tenantDto.getTenantId(), tenantDto.getData(), true),
                        DeviceRegistryUtils.getCacheDirective(config.getCacheMaxAge())))
            .otherwise(t -> TenantResult.from(ServiceInvocationException.extractStatusCode(t)));
    }
}
