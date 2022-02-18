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
import java.util.Optional;

import javax.security.auth.x500.X500Principal;

import org.eclipse.hono.deviceregistry.jdbc.config.TenantServiceOptions;
import org.eclipse.hono.deviceregistry.service.tenant.AbstractTenantService;
import org.eclipse.hono.service.base.jdbc.store.tenant.AdapterStore;
import org.eclipse.hono.util.CacheDirective;
import org.eclipse.hono.util.TenantResult;

import io.opentracing.Span;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

/**
 * A service implementing the <em>Hono Tenant API</em>.
 */
public class TenantServiceImpl extends AbstractTenantService {

    private final AdapterStore store;
    private final TenantServiceOptions properties;

    /**
     * Create a new instance.
     *
     * @param store The backing store to use.
     * @param properties The service properties.
     */
    public TenantServiceImpl(final AdapterStore store, final TenantServiceOptions properties) {
        this.store = store;
        this.properties = properties;
    }

    @Override
    public Future<TenantResult<JsonObject>> get(final String tenantId, final Span span) {

        return this.store
                .getById(tenantId, span.context())
                .map(this::mapResult);

    }

    @Override
    public Future<TenantResult<JsonObject>> get(final X500Principal subjectDn, final Span span) {

        return this.store
                .getByTrustAnchor(subjectDn.getName(), span.context())
                .map(this::mapResult);

    }

    /**
     * Map a tenant, read from the store, to the API result.
     *
     * @param result The read tenant.
     * @return The API result.
     */
    protected TenantResult<JsonObject> mapResult(final Optional<JsonObject> result) {

        return result
                .map(tenant -> TenantResult.from(
                        HttpURLConnection.HTTP_OK,
                        tenant,
                        CacheDirective.maxAgeDirective(this.properties.tenantTtl()))
                )
                .orElseGet(() -> TenantResult.from(HttpURLConnection.HTTP_NOT_FOUND));

    }

}
