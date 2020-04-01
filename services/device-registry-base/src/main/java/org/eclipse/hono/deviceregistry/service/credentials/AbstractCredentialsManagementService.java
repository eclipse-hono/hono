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

import java.net.HttpURLConnection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.eclipse.hono.auth.HonoPasswordEncoder;
import org.eclipse.hono.deviceregistry.service.device.DeviceKey;
import org.eclipse.hono.deviceregistry.service.tenant.TenantInformationService;
import org.eclipse.hono.service.management.OperationResult;
import org.eclipse.hono.service.management.credentials.CommonCredential;
import org.eclipse.hono.service.management.credentials.CredentialsManagementService;
import org.eclipse.hono.service.management.credentials.PasswordCredential;
import org.eclipse.hono.service.management.credentials.PasswordSecret;
import org.eclipse.hono.util.Futures;
import org.eclipse.hono.util.Strings;
import org.springframework.beans.factory.annotation.Autowired;

import io.opentracing.Span;
import io.vertx.core.Future;
import io.vertx.core.Vertx;

/**
 * An abstract base class implementation for {@link CredentialsManagementService}.
 * <p>
 * It checks the parameters, validate tenant using {@link TenantInformationService} and creates {@link DeviceKey} for looking up the credentials.
 */
public abstract class AbstractCredentialsManagementService implements CredentialsManagementService {

    protected TenantInformationService tenantInformationService;

    private HonoPasswordEncoder passwordEncoder;

    private final Vertx vertx;

    /**
     * Creates a service for a Vertx instance.
     *
     * @param vertx The Vertx instance to use.
     * @throws NullPointerException if vertx is {@code null};
     */
    @Autowired
    public AbstractCredentialsManagementService(final Vertx vertx) {
        this.vertx = Objects.requireNonNull(vertx);
    }

    /**
     * Set tenant information service.
     *
     * @param tenantInformationService The tenant information service.
     * @throws NullPointerException if service is {@code null};
     */
    @Autowired
    public void setTenantInformationService(final TenantInformationService tenantInformationService) {
        this.tenantInformationService = Objects.requireNonNull(tenantInformationService);
    }

    /**
     * Set password encoder.
     *
     * @param passwordEncoder The password encoder.
     * @throws NullPointerException if encoder is {@code null};
     */
    @Autowired
    public void setPasswordEncoder(final HonoPasswordEncoder passwordEncoder) {
        this.passwordEncoder = Objects.requireNonNull(passwordEncoder);
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

        return this.tenantInformationService
                .tenantExists(tenantId, span)
                .compose(result -> {

                    if (result.isError()) {
                        return Future.succeededFuture(OperationResult.empty(result.getStatus()));
                    }

                    return verifyAndEncodePasswords(credentials)
                            .compose(encodedCredentials -> processUpdateCredentials(DeviceKey.from(result.getPayload(), deviceId), resourceVersion, encodedCredentials, span))
                            .recover(t -> Future.succeededFuture(OperationResult.empty(HttpURLConnection.HTTP_BAD_REQUEST)));
                })
                .recover(t -> Future.succeededFuture(OperationResult.empty(HttpURLConnection.HTTP_BAD_REQUEST)));
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

    private Future<List<CommonCredential>> verifyAndEncodePasswords(final List<CommonCredential> credentials) {
        // Check if we need to encode passwords
        if (needToEncode(credentials)) {
            // ... yes, encode passwords asynchronously
            return Futures.executeBlocking(this.vertx, () -> checkCredentials(checkCredentials(credentials)));
        } else {
            // ... no, so don't fork off a worker task, but inline work
            return Future.succeededFuture(checkCredentials(credentials));
        }
    }

    /**
     * Check if we need to encode any secrets.
     *
     * @param credentials The credentials to check.
     * @return {@code true} is the list contains at least one password which needs to be encoded on the
     *         server side.
     */
    private static boolean needToEncode(final List<CommonCredential> credentials) {
        return credentials
                .stream()
                .filter(PasswordCredential.class::isInstance)
                .map(PasswordCredential.class::cast)
                .flatMap(c -> c.getSecrets().stream())
                .anyMatch(s -> !Strings.isNullOrEmpty(s.getPasswordPlain()));
    }

    /**
     *  Checks credentials and encodes secrets if necessary.
     *
     * @param credentials The credentials to check.
     * @return Verified and encoded credentials.
     * @throws IllegalStateException if the secret is not valid.
     */
    protected List<CommonCredential> checkCredentials(final List<CommonCredential> credentials) {
        for (final CommonCredential credential : credentials) {
            credential.checkValidity();
            if (credential instanceof PasswordCredential) {
                for (final PasswordSecret passwordSecret : ((PasswordCredential) credential).getSecrets()) {
                    passwordSecret.encode(this.passwordEncoder);
                    passwordSecret.checkValidity();
                }
            }
        }
        return credentials;
    }

}
