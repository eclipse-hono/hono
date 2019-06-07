/*******************************************************************************
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.hono.deviceregistry;

import java.net.HttpURLConnection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.eclipse.hono.service.management.Id;
import org.eclipse.hono.service.management.OperationResult;
import org.eclipse.hono.service.management.Result;
import org.eclipse.hono.service.management.credentials.CommonCredential;
import org.eclipse.hono.service.management.device.Device;
import org.eclipse.hono.service.management.device.DeviceBackend;
import org.eclipse.hono.util.CredentialsResult;
import org.eclipse.hono.util.RegistrationResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import com.google.common.base.MoreObjects;
import com.google.common.base.MoreObjects.ToStringHelper;

import io.opentracing.Span;
import io.vertx.core.AsyncResult;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;

/**
 * A device backend that keeps all data in memory but is backed by a file. This is done by leveraging and unifying
 * {@link FileBasedRegistrationService} and {@link FileBasedCredentialsService}
 */
@Repository
@Qualifier("backend")
@ConditionalOnProperty(name = "hono.app.type", havingValue = "file", matchIfMissing = true)
public class FileBasedDeviceBackend implements DeviceBackend {

    /**
     * The name of the JSON array containing device registration information for a tenant.
     */
    public static final String ARRAY_DEVICES = "devices";
    /**
     * The name of the JSON property containing the tenant ID.
     */
    public static final String FIELD_TENANT = "tenant";

    private final FileBasedRegistrationService registrationService;
    private final FileBasedCredentialsService credentialsService;

    /**
     * Create a new instance.
     * 
     * @param registrationService an implementation of registration service.
     * @param credentialsService an implementation of credentials service.
     */
    @Autowired
    public FileBasedDeviceBackend(
            @Qualifier("serviceImpl") final FileBasedRegistrationService registrationService,
            @Qualifier("serviceImpl") final FileBasedCredentialsService credentialsService) {
        this.registrationService = registrationService;
        this.credentialsService = credentialsService;
    }

    // DEVICES

    @Override
    public void assertRegistration(final String tenantId, final String deviceId,
            final Handler<AsyncResult<RegistrationResult>> resultHandler) {
        registrationService.assertRegistration(tenantId, deviceId, resultHandler);
    }

    @Override
    public void assertRegistration(final String tenantId, final String deviceId, final String gatewayId,
            final Handler<AsyncResult<RegistrationResult>> resultHandler) {
        registrationService.assertRegistration(tenantId, deviceId, gatewayId, resultHandler);
    }

    @Override
    public void readDevice(final String tenantId, final String deviceId, final Span span,
            final Handler<AsyncResult<OperationResult<Device>>> resultHandler) {
        registrationService.readDevice(tenantId, deviceId, span, resultHandler);
    }

    @Override
    public void deleteDevice(final String tenantId, final String deviceId, final Optional<String> resourceVersion,
            final Span span, final Handler<AsyncResult<Result<Void>>> resultHandler) {

        final Future<Result<Void>> future = Future.future();
        registrationService.deleteDevice(tenantId, deviceId, resourceVersion, span, future);

        future.compose(r -> {
            if (r.getStatus() != HttpURLConnection.HTTP_NO_CONTENT) {
                return Future.succeededFuture(r);
            }

            // now delete the credentials set
            final Future<Result<Void>> f = Future.future();
            credentialsService.remove(
                    tenantId,
                    deviceId,
                    span,
                    f);

            // pass on the original result
            return f.map(r);
        })
        .setHandler(resultHandler);
    }

    @Override
    public void createDevice(final String tenantId, final Optional<String> deviceId, final Device device,
           final Span span, final Handler<AsyncResult<OperationResult<Id>>> resultHandler) {

        final Future<OperationResult<Id>> future = Future.future();
        registrationService.createDevice(tenantId, deviceId, device, span, future);

        future
                .compose(r -> {

                    if (r.getStatus() != HttpURLConnection.HTTP_CREATED) {
                        return Future.succeededFuture(r);
                    }

                    // now create the empty credentials set
                    final Future<OperationResult<Void>> f = Future.future();
                    credentialsService.set(
                            tenantId,
                            r.getPayload().getId(),
                            Optional.empty(),
                            Collections.emptyList(),
                            span,
                            f);

                    // pass on the original result
                    return f.map(r);

                })

                .setHandler(resultHandler);

    }

    @Override
    public void updateDevice(final String tenantId, final String deviceId, final Device device,
            final Optional<String> resourceVersion, final Span span,
            final Handler<AsyncResult<OperationResult<Id>>> resultHandler) {
        registrationService.updateDevice(tenantId, deviceId, device, resourceVersion, span, resultHandler);
    }

    // CREDENTIALS

    @Override
    public final void get(final String tenantId, final String type, final String authId,
            final Handler<AsyncResult<CredentialsResult<JsonObject>>> resultHandler) {
        credentialsService.get(tenantId, type, authId, resultHandler);
    }

    @Override
    public void get(final String tenantId, final String type, final String authId, final Span span,
            final Handler<AsyncResult<CredentialsResult<JsonObject>>> resultHandler) {
        credentialsService.get(tenantId, type, authId, span, resultHandler);
    }

    @Override
    public final void get(final String tenantId, final String type, final String authId, final JsonObject clientContext,
            final Handler<AsyncResult<CredentialsResult<JsonObject>>> resultHandler) {
        credentialsService.get(tenantId, type, authId, clientContext, resultHandler);
    }

    @Override
    public void get(final String tenantId, final String type, final String authId, final JsonObject clientContext,
            final Span span,
            final Handler<AsyncResult<CredentialsResult<JsonObject>>> resultHandler) {
        credentialsService.get(tenantId, type, authId, clientContext, span, resultHandler);
    }

    @Override
    public void set(final String tenantId, final String deviceId, final Optional<String> resourceVersion,
            final List<CommonCredential> credentials, final Span span, final Handler<AsyncResult<OperationResult<Void>>> resultHandler) {
        //TODO check if device exists
        credentialsService.set(tenantId, deviceId, resourceVersion, credentials, span, resultHandler);
    }

    @Override
    public void get(final String tenantId, final String deviceId, final Span span,
            final Handler<AsyncResult<OperationResult<List<CommonCredential>>>> resultHandler) {

        final Future<OperationResult<List<CommonCredential>>> f = Future.future();
        credentialsService.get(tenantId, deviceId, span, f);
        f.compose(r -> {
            if (r.getStatus() == HttpURLConnection.HTTP_NOT_FOUND) {
                final Future<OperationResult<Device>> readFuture = Future.future();
                registrationService.readDevice(tenantId, deviceId, span, readFuture);
                return readFuture.map(d -> {
                    if (d.getStatus() == HttpURLConnection.HTTP_OK) {
                        return OperationResult.ok(HttpURLConnection.HTTP_OK,
                                Collections.<CommonCredential> emptyList(),
                                r.getCacheDirective(),
                                r.getResourceVersion());
                    } else {
                        return r;
                    }
                });
            } else {
                return Future.succeededFuture(r);
            }
        }).setHandler(resultHandler);
    }

    Future<?> saveToFile() {
        return CompositeFuture.all(
                this.registrationService.saveToFile(),
                this.credentialsService.saveToFile());
    }

    Future<?> loadFromFile() {
        return CompositeFuture.all(
                this.registrationService.loadRegistrationData(),
                this.credentialsService.loadCredentials());
    }

    /**
     * Removes all credentials from the registry.
     */
    public void clear() {
        registrationService.clear();
        credentialsService.clear();
    }

    /**
     * Creator for {@link ToStringHelper}.
     * 
     * @return A new instance for this instance.
     */
    protected ToStringHelper toStringHelper() {
        return MoreObjects.toStringHelper(this)
                .add("credentialsService", this.credentialsService)
                .add("registrationService", this.registrationService);
    }

    @Override
    public String toString() {
        return toStringHelper().toString();
    }
}
