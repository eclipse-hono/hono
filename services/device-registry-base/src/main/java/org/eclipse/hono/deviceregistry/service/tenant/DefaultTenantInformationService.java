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
package org.eclipse.hono.deviceregistry.service.tenant;

import java.net.HttpURLConnection;
import java.util.Objects;
import java.util.Optional;

import org.eclipse.hono.client.util.StatusCodeMapper;
import org.eclipse.hono.service.management.OperationResult;
import org.eclipse.hono.service.management.Result;
import org.eclipse.hono.service.management.tenant.Tenant;
import org.eclipse.hono.service.management.tenant.TenantManagementService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opentracing.Span;
import io.vertx.core.Future;

/**
 * An implementation of {@link TenantInformationService} that uses {@link TenantManagementService}
 * internally.
 */
public class DefaultTenantInformationService implements TenantInformationService {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultTenantInformationService.class);

    private final TenantManagementService tenantManagementService;

    /**
     * Creates a default tenant information service that uses the given {@link TenantManagementService}.
     *
     * @param tenantManagementService The tenant management service.
     * @throws NullPointerException if the tenantManagementService is {@code null}.
     */
    public DefaultTenantInformationService(final TenantManagementService tenantManagementService) {
        this.tenantManagementService = Objects.requireNonNull(tenantManagementService);
    }

    @Override
    public Future<Result<TenantKey>> tenantExists(final String tenantId, final Span span) {
        return tenantManagementService.readTenant(tenantId, span)
                .map(result -> {
                    if (result.isOk()) {
                        LOG.trace("tenant [{}] exists", tenantId);
                        return OperationResult.ok(
                                HttpURLConnection.HTTP_OK,
                                TenantKey.from(tenantId),
                                Optional.empty(),
                                Optional.empty());
                    } else {
                        LOG.trace("tenant [{}] does not exist", tenantId);
                        return Result.from(HttpURLConnection.HTTP_NOT_FOUND);
                    }
                });
    }

    @Override
    public Future<Tenant> getTenant(final String tenantId, final Span span) {
        return tenantManagementService.readTenant(tenantId, span)
                .compose(result -> {
                    if (result.isError()) {
                        return Future.failedFuture(
                                StatusCodeMapper.from(result.getStatus(), "error getting tenant [" + tenantId + "]"));
                    }
                    return Future.succeededFuture(result.getPayload());
                });
    }
}
