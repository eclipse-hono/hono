/**
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
 */


package org.eclipse.hono.deviceregistry.service.tenant;

import java.net.HttpURLConnection;
import java.util.Optional;

import org.eclipse.hono.service.management.OperationResult;
import org.eclipse.hono.service.management.Result;
import org.eclipse.hono.service.management.tenant.Tenant;

import io.opentracing.Span;
import io.vertx.core.Future;


/**
 * A simple implementation that is not backed by any real data.
 *
 */
public final class NoopTenantInformationService implements TenantInformationService {

    /**
     * {@inheritDoc}
     * <p>
     * This implementation always returns a succeeded future containing a key for the given tenant.
     */
    @Override
    public Future<Result<TenantKey>> tenantExists(final String tenantId, final Span span) {
        return Future.succeededFuture(OperationResult.ok(HttpURLConnection.HTTP_OK, TenantKey.from(tenantId),
                Optional.empty(), Optional.empty()));
    }

    @Override
    public Future<Tenant> getTenant(final String tenantId, final Span span) {
        return Future.succeededFuture(new Tenant());
    }

}
