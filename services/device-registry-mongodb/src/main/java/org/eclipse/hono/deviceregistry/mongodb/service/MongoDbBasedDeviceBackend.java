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
package org.eclipse.hono.deviceregistry.mongodb.service;

import java.net.HttpURLConnection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.deviceregistry.service.device.AbstractAutoProvisioningEventSender;
import org.eclipse.hono.deviceregistry.service.tenant.TenantInformationService;
import org.eclipse.hono.service.management.Filter;
import org.eclipse.hono.service.management.Id;
import org.eclipse.hono.service.management.OperationResult;
import org.eclipse.hono.service.management.Result;
import org.eclipse.hono.service.management.SearchResult;
import org.eclipse.hono.service.management.Sort;
import org.eclipse.hono.service.management.credentials.CommonCredential;
import org.eclipse.hono.service.management.device.Device;
import org.eclipse.hono.service.management.device.DeviceAndGatewayAutoProvisioner;
import org.eclipse.hono.service.management.device.DeviceBackend;
import org.eclipse.hono.service.management.device.DeviceManagementService;
import org.eclipse.hono.service.management.device.DeviceWithId;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.CredentialsResult;
import org.eclipse.hono.util.Lifecycle;
import org.eclipse.hono.util.RegistryManagementConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.MoreObjects;
import com.google.common.base.MoreObjects.ToStringHelper;

import io.opentracing.Span;
import io.opentracing.noop.NoopSpan;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

/**
 * A device backend that delegates to a {@link DeviceManagementService} and a
 * {@link MongoDbBasedCredentialsService}.
 */
public class MongoDbBasedDeviceBackend implements DeviceBackend, Lifecycle {

    private static final Logger LOG = LoggerFactory.getLogger(MongoDbBasedDeviceBackend.class);

    private final DeviceManagementService deviceManagementService;
    private final MongoDbBasedCredentialsService credentialsService;
    private final TenantInformationService tenantInformationService;
    private DeviceAndGatewayAutoProvisioner deviceAndGatewayAutoProvisioner;

    /**
     * Creates a new instance.
     *
     * @param deviceManagementService an implementation of registration service.
     * @param credentialsService an implementation of credentials service.
     * @param tenantInformationService an implementation of tenant information service.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    public MongoDbBasedDeviceBackend(
            final DeviceManagementService deviceManagementService,
            final MongoDbBasedCredentialsService credentialsService,
            final TenantInformationService tenantInformationService) {
        Objects.requireNonNull(deviceManagementService);
        Objects.requireNonNull(credentialsService);
        Objects.requireNonNull(tenantInformationService);

        this.deviceManagementService = deviceManagementService;
        this.credentialsService = credentialsService;
        this.tenantInformationService = tenantInformationService;
    }

    /**
     * Sets the service to use for auto-provisioning devices and gateways.
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

    /**
     * {@inheritDoc}
     */
    @Override
    public Future<Void> start() {
        LOG.debug("starting up services");
        return CompositeFuture.all(
                credentialsService.start(),
                Optional.ofNullable(deviceAndGatewayAutoProvisioner)
                    .map(AbstractAutoProvisioningEventSender::start)
                    .orElseGet(Future::succeededFuture))
                .mapEmpty();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Future<Void> stop() {
        LOG.debug("stopping services");
        return CompositeFuture.join(
                credentialsService.start(),
                Optional.ofNullable(deviceAndGatewayAutoProvisioner)
                    .map(AbstractAutoProvisioningEventSender::start)
                    .orElseGet(Future::succeededFuture))
                .mapEmpty();
    }

    // DEVICES

    @Override
    public Future<OperationResult<Device>> readDevice(final String tenantId, final String deviceId, final Span span) {
        return deviceManagementService.readDevice(tenantId, deviceId, span);
    }

    @Override
    public Future<OperationResult<SearchResult<DeviceWithId>>> searchDevices(final String tenantId,
            final int pageSize,
            final int pageOffset,
            final List<Filter> filters,
            final List<Sort> sortOptions,
            final Span span) {
        return deviceManagementService.searchDevices(tenantId, pageSize, pageOffset, filters, sortOptions, span);
    }

    @Override
    public Future<Result<Void>> deleteDevice(final String tenantId, final String deviceId,
            final Optional<String> resourceVersion,
            final Span span) {

        return deviceManagementService.deleteDevice(tenantId, deviceId, resourceVersion, span)
                .compose(result -> {
                    if (result.getStatus() != HttpURLConnection.HTTP_NO_CONTENT) {
                        return Future.succeededFuture(result);
                    }
                    // now delete the credentials set and pass on the original result
                    return credentialsService.removeCredentials(
                            tenantId,
                            deviceId,
                            span)
                            .map(result);
                });
    }

    @Override
    public Future<OperationResult<Id>> createDevice(
            final String tenantId,
            final Optional<String> deviceId,
            final Device device,
            final Span span) {

        return deviceManagementService.createDevice(tenantId, deviceId, device, span)
                .compose(result -> {
                    if (result.getStatus() != HttpURLConnection.HTTP_CREATED) {
                        return Future.succeededFuture(result);
                    }
                    // now create the empty credentials set and pass on the original result
                    return credentialsService.addCredentials(
                            tenantId,
                            result.getPayload().getId(),
                            Collections.emptyList(),
                            Optional.empty(),
                            span
                    ).map(result);
                });
    }

    @Override
    public Future<OperationResult<Id>> updateDevice(final String tenantId, final String deviceId, final Device device,
            final Optional<String> resourceVersion, final Span span) {
        return deviceManagementService.updateDevice(tenantId, deviceId, device, resourceVersion, span);
    }

    // CREDENTIALS

    @Override
    public final Future<CredentialsResult<JsonObject>> get(final String tenantId, final String type,
            final String authId) {
        return credentialsService.get(tenantId, type, authId);
    }

    @Override
    public Future<CredentialsResult<JsonObject>> get(final String tenantId, final String type, final String authId,
            final Span span) {
        return credentialsService.get(tenantId, type, authId, span);
    }

    @Override
    public final Future<CredentialsResult<JsonObject>> get(final String tenantId, final String type,
            final String authId, final JsonObject clientContext) {
        return get(tenantId, type, authId, clientContext, NoopSpan.INSTANCE);
    }

    @Override
    public Future<CredentialsResult<JsonObject>> get(final String tenantId, final String type, final String authId,
            final JsonObject clientContext, final Span span) {

        return this.tenantInformationService.getTenant(tenantId, span)
                .compose(tenant -> credentialsService.get(tenantId, type, authId, clientContext, span)
                        .compose(credentialsResult -> {
                            if (deviceAndGatewayAutoProvisioner != null) {
                                if (credentialsResult.isNotFound()) {
                                    return deviceAndGatewayAutoProvisioner.provisionIfEnabled(
                                            tenantId,
                                            tenant,
                                            authId,
                                            clientContext,
                                            span);
                                } else {
                                    final String deviceId = credentialsResult.getPayload()
                                            .getString(RegistryManagementConstants.FIELD_PAYLOAD_DEVICE_ID);
                                    return deviceAndGatewayAutoProvisioner
                                            .sendAutoProvisioningEventIfNeeded(tenantId, tenant, deviceId, span)
                                            .map(ok -> credentialsResult);
                                }
                            }
                            return Future.succeededFuture(credentialsResult);
                        }))
                .recover(this::convertToCredentialsResult);
    }

    @Override
    public Future<OperationResult<Void>> updateCredentials(final String tenantId, final String deviceId,
            final List<CommonCredential> credentials, final Optional<String> resourceVersion,
            final Span span) {
        return credentialsService.updateCredentials(tenantId, deviceId, credentials, resourceVersion, span);
    }

    @Override
    public Future<OperationResult<List<CommonCredential>>> readCredentials(final String tenantId, final String deviceId,
            final Span span) {

        return credentialsService.readCredentials(tenantId, deviceId, span)
                .compose(result -> {
                    if (result.getStatus() == HttpURLConnection.HTTP_NOT_FOUND) {
                        return deviceManagementService.readDevice(tenantId, deviceId, span)
                                .map(d -> {
                                    if (d.getStatus() == HttpURLConnection.HTTP_OK) {
                                        return OperationResult.ok(HttpURLConnection.HTTP_OK,
                                                Collections.emptyList(),
                                                result.getCacheDirective(),
                                                result.getResourceVersion());
                                    } else {
                                        return result;
                                    }
                                });
                    } else {
                        return Future.succeededFuture(result);
                    }
                });
    }

    /**
     * Creator for {@link ToStringHelper}.
     *
     * @return A new instance for this instance.
     */
    protected ToStringHelper toStringHelper() {
        return MoreObjects.toStringHelper(this)
                .add("credentialsService", this.credentialsService)
                .add("registrationService", this.deviceManagementService);
    }

    @Override
    public String toString() {
        return toStringHelper().toString();
    }

    private Future<CredentialsResult<JsonObject>> convertToCredentialsResult(final Throwable error) {
        return Future.succeededFuture(CredentialsResult.from(ServiceInvocationException.extractStatusCode(error),
                new JsonObject().put(Constants.JSON_FIELD_DESCRIPTION, error.getMessage())));
    }
}
