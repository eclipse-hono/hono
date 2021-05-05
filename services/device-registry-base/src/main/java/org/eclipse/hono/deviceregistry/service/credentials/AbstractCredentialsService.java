/*******************************************************************************
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
 *******************************************************************************/

package org.eclipse.hono.deviceregistry.service.credentials;

import java.util.Objects;

import javax.annotation.PostConstruct;

import org.eclipse.hono.deviceregistry.service.tenant.NoopTenantInformationService;
import org.eclipse.hono.deviceregistry.service.tenant.TenantInformationService;
import org.eclipse.hono.deviceregistry.service.tenant.TenantKey;
import org.eclipse.hono.deviceregistry.util.DeviceRegistryUtils;
import org.eclipse.hono.service.credentials.CredentialsService;
import org.eclipse.hono.service.management.credentials.CredentialsManagementService;
import org.eclipse.hono.service.management.device.AutoProvisioning;
import org.eclipse.hono.service.management.device.DeviceManagementService;
import org.eclipse.hono.util.CacheDirective;
import org.eclipse.hono.util.CredentialsConstants;
import org.eclipse.hono.util.CredentialsResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(AbstractCredentialsService.class);

    protected TenantInformationService tenantInformationService = new NoopTenantInformationService();

    private DeviceManagementService deviceManagementService;
    private CredentialsManagementService credentialsManagementService;

    /**
     * Sets the service to use for checking existence of tenants.
     * <p>
     * If not set, tenant existence will not be verified.
     *
     * @param tenantInformationService The tenant information service.
     * @throws NullPointerException if service is {@code null};
     */
    @Autowired(required = false)
    public void setTenantInformationService(final TenantInformationService tenantInformationService) {
        this.tenantInformationService = Objects.requireNonNull(tenantInformationService);
    }

    /**
     * Set the service to use for auto-provisioning devices.
     * <p>
     * If the service is not configured, auto-provisioning will be disabled.
     *
     * @param deviceManagementService The service to use.
     */
    @Autowired(required = false)
    public void setDeviceManagementService(final DeviceManagementService deviceManagementService) {
        this.deviceManagementService = deviceManagementService;
    }

    /**
     * Log the status of auto-provisioning on startup.
     */
    @PostConstruct
    protected void log() {
        if (isAutoProvisioningConfigured()) {
            log.info("Auto-provisioning is available");
        } else {
            log.info("Auto-provisioning is not available");
        }
    }

    /**
     * Set the service to use for auto-provisioning devices.
     * <p>
     * If the service is not configured, auto-provisioning will be disabled.
     *
     * @param credentialsManagementService The service to use.
     */
    @Autowired(required = false)
    public void setCredentialsManagementService(final CredentialsManagementService credentialsManagementService) {
        this.credentialsManagementService = credentialsManagementService;
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

    /**
     * Gets a cache directive for a type of credentials.
     *
     * @param type The type of credentials.
     * @param maxAge The number of seconds that the credentials may be cached.
     * @return A max-age directive if the type is either hashed-password or X.509,
     *         a no-cache directive otherwise.
     * @throws NullPointerException if type is {@code null}.
     */
    protected final CacheDirective getCacheDirective(final String type, final long maxAge) {

        switch (type) {
        case CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD:
        case CredentialsConstants.SECRETS_TYPE_X509_CERT:
            return DeviceRegistryUtils.getCacheDirective(maxAge);
        default:
            return CacheDirective.noCacheDirective();
        }
    }

    @Override
    public final Future<CredentialsResult<JsonObject>> get(final String tenantId, final String type, final String authId, final Span span) {
        return get(tenantId, type, authId, null, span);
    }

    @Override
    public final Future<CredentialsResult<JsonObject>> get(
            final String tenantId,
            final String type,
            final String authId,
            final JsonObject clientContext,
            final Span span) {

        return this.tenantInformationService.tenantExists(tenantId, span)
                .compose(result -> {
                    if (result.isError()) {
                        return Future.succeededFuture(CredentialsResult.from(result.getStatus()));
                    }
                    return processGet(TenantKey.from(tenantId), CredentialKey.from(tenantId, authId, type),
                            clientContext, span)
                                    .compose(credentialsResult -> {
                                        if (credentialsResult.isNotFound() && isAutoProvisioningConfigured()) {
                                            return AutoProvisioning.provisionIfEnabled(
                                                    tenantId,
                                                    authId,
                                                    clientContext,
                                                    this.credentialsManagementService,
                                                    this.deviceManagementService,
                                                    this.tenantInformationService,
                                                    span);
                                        }
                                        return Future.succeededFuture(credentialsResult);
                                    });
                });
    }

    private boolean isAutoProvisioningConfigured() {
        return this.credentialsManagementService != null && this.deviceManagementService != null;
    }
}
