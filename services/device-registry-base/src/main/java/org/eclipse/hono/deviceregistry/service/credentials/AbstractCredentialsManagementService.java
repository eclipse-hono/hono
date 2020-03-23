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

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.eclipse.hono.deviceregistry.service.device.DeviceKey;
import org.eclipse.hono.deviceregistry.service.tenant.TenantInformationService;
import org.eclipse.hono.service.management.OperationResult;
import org.eclipse.hono.service.management.credentials.CommonCredential;
import org.eclipse.hono.service.management.credentials.CredentialsManagementService;
import org.springframework.beans.factory.annotation.Autowired;

import io.opentracing.Span;
import io.vertx.core.Future;

/**
 * An abstract base class implementation for {@link CredentialsManagementService}.
 * <p>
 * It checks the parameters, validate tenant using {@link TenantInformationService} and creates {@link DeviceKey} for looking up the credentials.
 */
public abstract class AbstractCredentialsManagementService implements CredentialsManagementService {

    protected TenantInformationService tenantInformationService;

    /**
     * Set tenant information service.
     * @param tenantInformationService The tenant information service.
     */
    @Autowired
    public void setTenantInformationService(final TenantInformationService tenantInformationService) {
        this.tenantInformationService = tenantInformationService;
    }

    /**
     * Update credentials with a specified device key and value objects.
     *
     * @param key The device key object.
     * @param resourceVersion The identifier of the resource version to update.
     * @param credentials The credentials value object.
     * @param span The active OpenTracing span for this operation.
     * @return A future indicating the outcome of the operation.
     */
    protected abstract Future<OperationResult<Void>> processUpdateCredentials(DeviceKey key, Optional<String> resourceVersion, List<CommonCredential> credentials, Span span);

    /**
     * Read credentials with a specified device key.
     *
     * @param key The device key object.
     * @param span The active OpenTracing span for this operation.
     * @return A future indicating the outcome of the operation.
     */
    protected abstract Future<OperationResult<List<CommonCredential>>> processReadCredentials(DeviceKey key, Span span);

    @Override
    public Future<OperationResult<Void>> updateCredentials(final String tenantId, final String deviceId, final List<CommonCredential> credentials, final Optional<String> resourceVersion, final Span span) {
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(deviceId);
        Objects.requireNonNull(resourceVersion);

        //TODO add credentials verification and encoding logic here to be shared between implementations

        return this.tenantInformationService
                .tenantExists(tenantId, span)
                .compose(result -> result.isError()
                        ? Future.succeededFuture(OperationResult.empty(result.getStatus()))
                        : processUpdateCredentials(DeviceKey.from(result.getPayload(), deviceId), resourceVersion, credentials, span));
    }

    @Override
    public Future<OperationResult<List<CommonCredential>>> readCredentials(final String tenantId, final String deviceId, final Span span) {
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(deviceId);

        return this.tenantInformationService
                .tenantExists(tenantId, span)
                .compose(result -> result.isError()
                        ? Future.succeededFuture(OperationResult.empty(result.getStatus()))
                        : processReadCredentials(DeviceKey.from(result.getPayload(), deviceId), span));
    }

}
