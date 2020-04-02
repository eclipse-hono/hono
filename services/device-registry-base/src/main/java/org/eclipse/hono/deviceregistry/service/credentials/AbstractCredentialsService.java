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
package org.eclipse.hono.deviceregistry.service.credentials;

import java.util.Objects;

import org.eclipse.hono.deviceregistry.service.tenant.NoopTenantInformationService;
import org.eclipse.hono.deviceregistry.service.tenant.TenantInformationService;
import org.eclipse.hono.deviceregistry.service.tenant.TenantKey;
import org.eclipse.hono.service.credentials.CredentialsService;
import org.eclipse.hono.util.CredentialsResult;
import org.springframework.beans.factory.annotation.Autowired;

import io.opentracing.Span;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

/**
 * An abstract base class implementation for {@link CredentialsService}.
 * <p>
 * It checks the parameters, validate tenant using {@link TenantInformationService} and creates {@link CredentialKey} for looking up the credentials.
 */
public abstract class AbstractCredentialsService implements CredentialsService {

    protected TenantInformationService tenantInformationService = new NoopTenantInformationService();

    /**
     * Sets the service to use for checking existence of tenants.
     * <p>
     * If not set, tenant existence will not be verified.
     * 
     * @param tenantInformationService The tenant information service.
     * @throws NullPointerException if service is {@code null};
     */
    @Autowired(required = false)
    public final void setTenantInformationService(final TenantInformationService tenantInformationService) {
        this.tenantInformationService = Objects.requireNonNull(tenantInformationService);
    }

    /**
     * Get credentials for a device.
     *
     * @param tenant The tenant key object.
     * @param key The credentials key object.
     * @param clientContext Optional bag of properties that can be used to identify the device.
     * @param span The active OpenTracing span for this operation.
     * @return A future indicating the outcome of the operation.
     */
    protected abstract Future<CredentialsResult<JsonObject>> processGet(TenantKey tenant, CredentialKey key, JsonObject clientContext, Span span);

    @Override
    public final Future<CredentialsResult<JsonObject>> get(final String tenantId, final String type, final String authId, final Span span) {
        return get(tenantId, type, authId, null, span);
    }

    @Override
    public final Future<CredentialsResult<JsonObject>> get(final String tenantId, final String type, final String authId, final JsonObject clientContext, final Span span) {
        return this.tenantInformationService
                .tenantExists(tenantId, span)
                .compose(result -> result.isError()
                        ? Future.succeededFuture(CredentialsResult.from(result.getStatus()))
                        : processGet(result.getPayload(), CredentialKey.from(result.getPayload(), authId, type), clientContext, span));
    }
}
