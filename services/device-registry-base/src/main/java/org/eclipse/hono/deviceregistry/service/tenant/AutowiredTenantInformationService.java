/*******************************************************************************
 * Copyright (c) 2019, 2020 Contributors to the Eclipse Foundation
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

import org.eclipse.hono.service.management.OperationResult;
import org.eclipse.hono.service.management.Result;
import org.eclipse.hono.service.tenant.TenantService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import io.opentracing.Span;
import io.vertx.core.Future;

/**
 * A default implementation of {@link TenantInformationService} that uses embedded {@link TenantService} to verify if tenant exists.
 */
@Component
public class AutowiredTenantInformationService implements TenantInformationService {

    private TenantService service;

    @Override
    public final Future<Result<TenantKey>> tenantExists(final String tenantId, final Span span) {
        return service.get(tenantId, span)
                .compose(result -> {
                    if (result.isOk()) {
                        return Future.succeededFuture(
                                OperationResult.ok(
                                        HttpURLConnection.HTTP_OK,
                                        TenantKey.from(tenantId),
                                        Optional.empty(),
                                        Optional.empty()
                                )
                        );
                    } else {
                        final Result<TenantKey> res = Result.from(HttpURLConnection.HTTP_NOT_FOUND);
                        return Future.succeededFuture(res);
                    }
                });
    }

    /**
     * Sets the backend service to delegate to.
     * 
     * @param service The service.
     * @throws NullPointerException if service is {@code null}.
     */
    @Autowired
    @Qualifier("backend")
    public final void setService(final TenantService service) {
        this.service = Objects.requireNonNull(service);
    }
}
