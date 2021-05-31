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
import java.util.Optional;

import javax.annotation.PostConstruct;

import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.deviceregistry.service.tenant.NoopTenantInformationService;
import org.eclipse.hono.deviceregistry.service.tenant.TenantInformationService;
import org.eclipse.hono.deviceregistry.service.tenant.TenantKey;
import org.eclipse.hono.deviceregistry.util.DeviceRegistryUtils;
import org.eclipse.hono.service.credentials.CredentialsService;
import org.eclipse.hono.service.management.device.DeviceAndGatewayAutoProvisioner;
import org.eclipse.hono.util.CacheDirective;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.CredentialsConstants;
import org.eclipse.hono.util.CredentialsResult;
import org.eclipse.hono.util.Lifecycle;
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
public abstract class AbstractCredentialsService implements CredentialsService, Lifecycle {

    private static final Logger log = LoggerFactory.getLogger(AbstractCredentialsService.class);

    protected TenantInformationService tenantInformationService = new NoopTenantInformationService();

    private DeviceAndGatewayAutoProvisioner deviceAndGatewayAutoProvisioner;

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
     * Set the service to use for auto-provisioning devices and gateways.
     * <p>
     * If the service is not configured, auto-provisioning will be disabled.
     *
     * @param deviceAndGatewayAutoProvisioner The service to use for auto-provisioning devices and gateways.
     * @throws NullPointerException if the service is {@code null}.
     */
    public void setDeviceAndGatewayAutoProvisioner(
            final DeviceAndGatewayAutoProvisioner deviceAndGatewayAutoProvisioner) {
        this.deviceAndGatewayAutoProvisioner = Objects.requireNonNull(deviceAndGatewayAutoProvisioner);
    }

    @Override
    public final Future<Void> start() {
        return Optional.ofNullable(this.deviceAndGatewayAutoProvisioner)
                .map(DeviceAndGatewayAutoProvisioner::start)
                .orElse(Future.succeededFuture());
    }

    @Override
    public final Future<Void> stop() {
        return Optional.ofNullable(this.deviceAndGatewayAutoProvisioner)
                .map(DeviceAndGatewayAutoProvisioner::stop)
                .orElse(Future.succeededFuture());
    }

    /**
     * Log the status of auto-provisioning on startup.
     */
    @PostConstruct
    protected void log() {
        if (isAutoProvisioningConfigured()) {
            log.info("Auto-provisioning of devices/gateways is available");
        } else {
            log.info("Auto-provisioning of devices/gateways is not available");
        }
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

        return this.tenantInformationService.getTenant(tenantId, span)
                .compose(tenant -> {
                    return processGet(TenantKey.from(tenantId), CredentialKey.from(tenantId, authId, type),
                            clientContext, span)
                                    .compose(credentialsResult -> {
                                        if (credentialsResult.isNotFound() && isAutoProvisioningConfigured()) {
                                            return deviceAndGatewayAutoProvisioner.provisionIfEnabled(
                                                    tenantId,
                                                    tenant,
                                                    authId,
                                                    clientContext,
                                                    span);
                                        }
                                        return Future.succeededFuture(credentialsResult);
                                    });
                })
                .recover(this::convertToCredentialsResult);
    }

    private boolean isAutoProvisioningConfigured() {
        return this.deviceAndGatewayAutoProvisioner != null;
    }

    private Future<CredentialsResult<JsonObject>> convertToCredentialsResult(final Throwable error) {
        return Future.succeededFuture(CredentialsResult.from(ServiceInvocationException.extractStatusCode(error),
                new JsonObject().put(Constants.JSON_FIELD_DESCRIPTION, error.getMessage())));
    }
}
