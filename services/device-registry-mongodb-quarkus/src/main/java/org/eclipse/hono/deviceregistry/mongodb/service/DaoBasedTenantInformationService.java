/**
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
 */


package org.eclipse.hono.deviceregistry.mongodb.service;

import java.net.HttpURLConnection;
import java.util.Objects;
import java.util.Optional;

import org.eclipse.hono.deviceregistry.mongodb.model.TenantDao;
import org.eclipse.hono.deviceregistry.service.tenant.TenantInformationService;
import org.eclipse.hono.deviceregistry.service.tenant.TenantKey;
import org.eclipse.hono.service.management.OperationResult;
import org.eclipse.hono.service.management.Result;
import org.eclipse.hono.service.management.tenant.Tenant;
import org.eclipse.hono.service.management.tenant.TenantDto;

import io.opentracing.Span;
import io.vertx.core.Future;


/**
 * A tenant information service that uses a {@link TenantDao} to
 * retrieve tenant data.
 */
public final class DaoBasedTenantInformationService implements TenantInformationService {

    private final TenantDao dao;

    /**
     * Creates a new service for a data access object.
     *
     * @param tenantDao The data access object to use for accessing data in the MongoDB.
     * @throws NullPointerException if dao is {@code null}.
     */
    public DaoBasedTenantInformationService(final TenantDao tenantDao) {

        this.dao = Objects.requireNonNull(tenantDao);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Future<Tenant> getTenant(final String tenantId, final Span span) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(span);

        return dao.getById(tenantId, span.context())
                .map(TenantDto::getData);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Future<Result<TenantKey>> tenantExists(final String tenantId, final Span span) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(span);

        return dao.getById(tenantId, span.context())
                .map(dto -> OperationResult.ok(
                        HttpURLConnection.HTTP_OK,
                        TenantKey.from(tenantId),
                        Optional.empty(),
                        Optional.empty()));
    }
}
