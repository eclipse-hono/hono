/*******************************************************************************
 * Copyright (c) 2020, 2022 Contributors to the Eclipse Foundation
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

import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.deviceregistry.service.tenant.AbstractTenantManagementService;
import org.eclipse.hono.service.base.jdbc.store.tenant.ManagementStore;
import org.eclipse.hono.service.management.Filter;
import org.eclipse.hono.service.management.Id;
import org.eclipse.hono.service.management.OperationResult;
import org.eclipse.hono.service.management.Result;
import org.eclipse.hono.service.management.SearchResult;
import org.eclipse.hono.service.management.Sort;
import org.eclipse.hono.service.management.tenant.Tenant;
import org.eclipse.hono.service.management.tenant.TenantWithId;

import io.opentracing.Span;
import io.vertx.core.Future;
import io.vertx.core.Vertx;


/**
 * Implementation of a <em>Tenant management service</em>.
 */
public class TenantManagementServiceImpl extends AbstractTenantManagementService {

    private final ManagementStore store;

    /**
     * Create a new instance.
     *
     * @param vertx The vert.x instance to use.
     * @param store The backing store to use.
     */
    public TenantManagementServiceImpl(final Vertx vertx, final ManagementStore store) {
        super(vertx);
        this.store = store;
    }

    @Override
    protected Future<OperationResult<Id>> processCreateTenant(
            final String tenantId,
            final Tenant tenantObj,
            final Span span) {

        return this.store

                .create(tenantId, tenantObj, span.context())
                .map(r -> OperationResult.ok(
                        HttpURLConnection.HTTP_CREATED,
                        Id.of(tenantId),
                        Optional.empty(),
                        Optional.of(r.getVersion())))

                .recover(e -> Services.recover(e));

    }

    @Override
    protected Future<OperationResult<Tenant>> processReadTenant(final String tenantId, final Span span) {

        return this.store

                .read(tenantId, span.context())
                .map(result -> result

                        .map(tenant -> OperationResult.ok(
                                HttpURLConnection.HTTP_OK,
                                tenant.getTenant(),
                                Optional.empty(),
                                tenant.getResourceVersion()
                        ))

                        .orElseThrow(() -> new ClientErrorException(
                                tenantId,
                                HttpURLConnection.HTTP_NOT_FOUND,
                                "no such tenant")));

    }

    @Override
    public Future<OperationResult<Void>> processUpdateTenant(final String tenantId, final Tenant tenantObj,
            final Optional<String> resourceVersion, final Span span) {

        return this.store

                .update(tenantId, tenantObj, resourceVersion, span.context())
                .<OperationResult<Void>>map(r -> OperationResult.<Void>ok(
                        HttpURLConnection.HTTP_NO_CONTENT,
                        null,
                        Optional.empty(),
                        Optional.of(r.getVersion())
                ))
                .recover(e -> Services.recover(e));

    }

    @Override
    protected Future<Result<Void>> processDeleteTenant(final String tenantId, final Optional<String> resourceVersion, final Span span) {

        return this.store

                .delete(tenantId, resourceVersion, span.context())
                .map(r -> {
                    if (r.getUpdated() <= 0) {
                        throw new ClientErrorException(
                                tenantId,
                                HttpURLConnection.HTTP_NOT_FOUND,
                                "no such tenant");
                    } else {
                        return Result.<Void>from(HttpURLConnection.HTTP_NO_CONTENT);
                    }
                })
                .recover(e -> Services.recover(e));

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

        return store.find(pageSize, pageOffset, span.context())
            .map(result -> OperationResult.ok(
                    HttpURLConnection.HTTP_OK,
                    result,
                    Optional.empty(),
                    Optional.empty()))
            .recover(e -> Services.recover(e));
    }
}
