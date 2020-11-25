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

package org.eclipse.hono.deviceregistry.jdbc.impl;

import java.net.HttpURLConnection;
import java.util.Optional;

import org.eclipse.hono.deviceregistry.jdbc.config.TenantServiceProperties;
import org.eclipse.hono.deviceregistry.service.tenant.AbstractTenantManagementService;
import org.eclipse.hono.service.base.jdbc.store.tenant.ManagementStore;
import org.eclipse.hono.service.management.Id;
import org.eclipse.hono.service.management.OperationResult;
import org.eclipse.hono.service.management.Result;
import org.eclipse.hono.service.management.tenant.Tenant;
import org.eclipse.hono.util.CacheDirective;

import io.opentracing.Span;
import io.vertx.core.Future;

/**
 * Implementation of a <em>Tenant management service</em>.
 */
public class TenantManagementServiceImpl extends AbstractTenantManagementService {

    private final ManagementStore store;
    private final CacheDirective ttl;

    /**
     * Create a new instance.
     *
     * @param store The backing store to use.
     * @param properties The service properties.
     */
    public TenantManagementServiceImpl(final ManagementStore store, final TenantServiceProperties properties) {
        this.store = store;
        this.ttl = CacheDirective.maxAgeDirective(properties.getTenantTtl());
    }

    @Override
    public Future<OperationResult<Id>> createTenant(final String tenantId, final Tenant tenantObj, final Span span) {

        return this.store

                .create(tenantId, tenantObj, span.context())
                .map(r -> OperationResult.ok(
                        HttpURLConnection.HTTP_CREATED,
                        Id.of(tenantId),
                        Optional.empty(),
                        Optional.of(r.getVersion())))

                .recover(e -> Services.recover(e, OperationResult::empty));

    }

    @Override
    public Future<OperationResult<Tenant>> readTenant(final String tenantId, final Span span) {

        return this.store

                .read(tenantId, span.context())
                .map(result -> result

                        .map(tenant -> OperationResult.ok(
                                HttpURLConnection.HTTP_OK,
                                tenant.getTenant(),
                                Optional.of(ttl),
                                tenant.getResourceVersion()
                        ))

                        .orElseGet(() -> OperationResult.empty(HttpURLConnection.HTTP_NOT_FOUND)));

    }

    @Override
    public Future<OperationResult<Void>> updateTenant(final String tenantId, final Tenant tenantObj, final Optional<String> resourceVersion, final Span span) {

        return this.store

                .update(tenantId, tenantObj, resourceVersion, span.context())
                .<OperationResult<Void>>map(r -> OperationResult.<Void>ok(
                        HttpURLConnection.HTTP_NO_CONTENT,
                        null,
                        Optional.empty(),
                        Optional.of(r.getVersion())
                ))
                .recover(e -> Services.recover(e, OperationResult::empty));

    }

    @Override
    public Future<Result<Void>> deleteTenant(final String tenantId, final Optional<String> resourceVersion, final Span span) {

        return this.store

                .delete(tenantId, resourceVersion, span.context())
                .map(r -> {
                    if (r.getUpdated() <= 0) {
                        return Result.<Void>from(HttpURLConnection.HTTP_NOT_FOUND);
                    } else {
                        return Result.<Void>from(HttpURLConnection.HTTP_NO_CONTENT);
                    }
                })
                .recover(e -> Services.recover(e, Result::from));

    }

}
