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

package org.eclipse.hono.deviceregistry.service.tenant;

import javax.security.auth.x500.X500Principal;

import org.eclipse.hono.service.tenant.TenantService;
import org.eclipse.hono.util.TenantResult;

import io.opentracing.Span;
import io.opentracing.noop.NoopSpan;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

/**
 * An abstract base class implementation for {@link TenantService}.
 */
public abstract class AbstractTenantService implements TenantService {

    @Override
    public abstract Future<TenantResult<JsonObject>> get(String tenantId, Span span);

    @Override
    public abstract Future<TenantResult<JsonObject>> get(X500Principal subjectDn, Span span);

    @Override
    public Future<TenantResult<JsonObject>> get(final String tenantId) {
        return get(tenantId, NoopSpan.INSTANCE);
    }

    @Override
    public Future<TenantResult<JsonObject>> get(final X500Principal subjectDn) {
        return get(subjectDn, NoopSpan.INSTANCE);
    }

}
