/**
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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


package org.eclipse.hono.deviceregistry.jdbc.impl;

import java.net.HttpURLConnection;
import java.util.Objects;
import java.util.Optional;

import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.deviceregistry.service.tenant.TenantInformationService;
import org.eclipse.hono.deviceregistry.service.tenant.TenantKey;
import org.eclipse.hono.service.base.jdbc.store.tenant.AdapterStore;
import org.eclipse.hono.service.base.jdbc.store.tenant.TenantReadResult;
import org.eclipse.hono.service.management.OperationResult;
import org.eclipse.hono.service.management.Result;
import org.eclipse.hono.service.management.tenant.Tenant;

import io.opentracing.Span;
import io.vertx.core.Future;


/**
 * A tenant information service that uses an {@link AdapterStore} to
 * retrieve tenant data.
 */
public final class StoreBasedTenantInformationService implements TenantInformationService {

    private final AdapterStore tenantStore;

    /**
     * Creates a new service for a data access object.
     *
     * @param tenantStore The data store to use for accessing the DB.
     * @throws NullPointerException if tenantStore is {@code null}.
     */
    public StoreBasedTenantInformationService(final AdapterStore tenantStore) {

        this.tenantStore = Objects.requireNonNull(tenantStore);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Future<Tenant> getTenant(final String tenantId, final Span span) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(span);

        return tenantStore.readTenant(tenantId, span.context())
                .map(result -> result

                        .map(TenantReadResult::getTenant)

                        .orElseThrow(() -> new ClientErrorException(
                                tenantId,
                                HttpURLConnection.HTTP_NOT_FOUND,
                                "no such tenant")));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Future<Result<TenantKey>> tenantExists(final String tenantId, final Span span) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(span);

        return tenantStore.getById(tenantId, span.context())
                .map(optionalJson -> optionalJson
                        .map(json -> OperationResult.ok(
                            HttpURLConnection.HTTP_OK,
                            TenantKey.from(tenantId),
                            Optional.empty(),
                            Optional.empty()))
                        .orElse(OperationResult.empty(HttpURLConnection.HTTP_NOT_FOUND)));
    }
}
