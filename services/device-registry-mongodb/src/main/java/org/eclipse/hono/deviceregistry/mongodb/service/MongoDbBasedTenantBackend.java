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
package org.eclipse.hono.deviceregistry.mongodb.service;

import java.util.Optional;

import javax.security.auth.x500.X500Principal;

import org.eclipse.hono.service.management.Id;
import org.eclipse.hono.service.management.OperationResult;
import org.eclipse.hono.service.management.Result;
import org.eclipse.hono.service.management.tenant.Tenant;
import org.eclipse.hono.service.management.tenant.TenantBackend;
import org.eclipse.hono.util.TenantResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;

import io.opentracing.Span;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Verticle;
import io.vertx.core.json.JsonObject;

/**
 * A tenant backend that leverages {@link MongoDbBasedTenantService}.
 */
@Repository
@Qualifier("backend")
public class MongoDbBasedTenantBackend extends AbstractVerticle
        implements TenantBackend, Verticle {

    private final MongoDbBasedTenantService tenantService;

    /**
     * Create a new instance.
     *
     * @param tenantService an implementation of tenant service.
     */
    @Autowired
    public MongoDbBasedTenantBackend(
            @Qualifier("serviceImpl") final MongoDbBasedTenantService tenantService) {
        this.tenantService = tenantService;
    }

    @Override
    public Future<OperationResult<Id>> createTenant(final Optional<String> tenantId, final Tenant tenantObj,
            final Span span) {
        return tenantService.createTenant(tenantId, tenantObj, span);

    }

    @Override
    public Future<OperationResult<Tenant>> readTenant(final String tenantId, final Span span) {
        return tenantService.readTenant(tenantId, span);

    }

    @Override
    public Future<OperationResult<Void>> updateTenant(final String tenantId, final Tenant tenantObj,
            final Optional<String> resourceVersion, final Span span) {
        return tenantService.updateTenant(tenantId, tenantObj, resourceVersion, span);
    }

    @Override
    public Future<Result<Void>> deleteTenant(final String tenantId, final Optional<String> resourceVersion,
            final Span span) {
        return tenantService.deleteTenant(tenantId, resourceVersion, span);
    }

    @Override
    public Future<TenantResult<JsonObject>> get(final String tenantId) {
        return tenantService.get(tenantId);
    }

    @Override
    public Future<TenantResult<JsonObject>> get(final X500Principal subjectDn) {
        return tenantService.get(subjectDn);
    }
}
