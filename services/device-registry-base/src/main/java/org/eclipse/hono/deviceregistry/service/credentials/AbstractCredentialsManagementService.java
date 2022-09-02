/*******************************************************************************
 * Copyright (c) 2020, 2022 Contributors to the Eclipse Foundation
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
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.util.StatusCodeMapper;
import org.eclipse.hono.deviceregistry.service.device.DeviceKey;
import org.eclipse.hono.deviceregistry.service.tenant.NoopTenantInformationService;
import org.eclipse.hono.deviceregistry.service.tenant.TenantInformationService;
import org.eclipse.hono.deviceregistry.util.DeviceRegistryUtils;
import org.eclipse.hono.notification.NotificationEventBusSupport;
import org.eclipse.hono.notification.deviceregistry.CredentialsChangeNotification;
import org.eclipse.hono.service.auth.HonoPasswordEncoder;
import org.eclipse.hono.service.management.OperationResult;
import org.eclipse.hono.service.management.credentials.CommonCredential;
import org.eclipse.hono.service.management.credentials.CredentialsManagementService;
import org.eclipse.hono.service.management.credentials.PasswordCredential;
import org.eclipse.hono.util.Futures;
import org.eclipse.hono.util.Strings;

import io.opentracing.Span;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;

/**
 * An abstract base class implementation for {@link CredentialsManagementService}.
 * <p>
 * It checks the parameters, validate tenant using {@link TenantInformationService} and creates {@link DeviceKey} for looking up the credentials.
 */
public abstract class AbstractCredentialsManagementService implements CredentialsManagementService {

    /**
     * The vert.x instance that this instance is running on.
     */
    protected final Vertx vertx;

    /**
     * The service to use for retrieving tenant information.
     */
    protected TenantInformationService tenantInformationService = new NoopTenantInformationService();

    private final HonoPasswordEncoder passwordEncoder;
    private final int maxBcryptCostFactor;
    private final Set<String> hashAlgorithmsWhitelist;
    private final Handler<CredentialsChangeNotification> notificationSender;

    /**
     * Creates a service for the given Vertx and password encoder instances.
     *
     * @param vertx The Vertx instance to use.
     * @param passwordEncoder The password encoder.
     * @param maxBcryptCostFactor The maximum cost factor allowed for bcrypt password hashes.
     * @param hashAlgorithmsWhitelist An optional collection of allowed password hashes.
     * @throws NullPointerException if any of the required parameters is {@code null};
     */
    public AbstractCredentialsManagementService(
            final Vertx vertx,
            final HonoPasswordEncoder passwordEncoder,
            final int maxBcryptCostFactor,
            final Set<String> hashAlgorithmsWhitelist) {

        this.vertx = Objects.requireNonNull(vertx);
        this.passwordEncoder = Objects.requireNonNull(passwordEncoder);
        this.maxBcryptCostFactor = maxBcryptCostFactor;
        this.hashAlgorithmsWhitelist = hashAlgorithmsWhitelist != null ? hashAlgorithmsWhitelist : Collections.emptySet();
        this.notificationSender = NotificationEventBusSupport.getNotificationSender(vertx);
    }

    /**
     * Sets the service to use for checking existence of tenants.
     * <p>
     * If not set, tenant existence will not be verified.
     *
     * @param tenantInformationService The tenant information service.
     * @throws NullPointerException if service is {@code null};
     */
    public void setTenantInformationService(final TenantInformationService tenantInformationService) {
        this.tenantInformationService = Objects.requireNonNull(tenantInformationService);
    }

    /**
     * Updates or creates a set of credentials.
     * <p>
     * This method is invoked by {@link #updateCredentials(String, String, List, Optional, Span)} after all parameter checks
     * have succeeded.
     *
     * @param key The device's key.
     * @param credentials The credentials to set. See
     *                    <a href="https://www.eclipse.org/hono/docs/api/credentials/#credentials-format">
     *                    Credentials Format</a> for details.
     * @param resourceVersion The resource version that the credentials are required to have.
     *                        If empty, the resource version of the credentials on record will be ignored.
     * @param span The active OpenTracing span to use for tracking this operation.
     *             <p>
     *             Implementations <em>must not</em> invoke the {@link Span#finish()} nor the {@link Span#finish(long)}
     *             methods. However,implementations may log (error) events on this span, set tags and use this span
     *             as the parent for additional spans created as part of this method's execution.
     * @return A future indicating the outcome of the operation.
     *         <p>
     *         The future will be succeeded if the credentials have been created/updated successfully.
     *         Otherwise, the future will be failed with a
     *         {@link org.eclipse.hono.client.ServiceInvocationException} containing an error code as specified
     *         in the Device Registry Management API.
     */
    protected abstract Future<OperationResult<Void>> processUpdateCredentials(
            DeviceKey key,
            List<CommonCredential> credentials,
            Optional<String> resourceVersion,
            Span span);

    /**
     * Gets all credentials registered for a device.
     *
     * @param key The device's key.
     * @param span The active OpenTracing span to use for tracking this operation.
     *             <p>
     *             Implementations <em>must not</em> invoke the {@link Span#finish()} nor the {@link Span#finish(long)}
     *             methods. However,implementations may log (error) events on this span, set tags and use this span
     *             as the parent for additional spans created as part of this method's execution.
     * @return A future indicating the outcome of the operation.
     *         <p>
     *         The future will be succeeded with a result containing the retrieved credentials if a device
     *         with the given identifier exists. Otherwise, the future will be failed with a
     *         {@link org.eclipse.hono.client.ServiceInvocationException} containing an error code as specified
     *         in the Device Registry Management API.
     */
    protected abstract Future<OperationResult<List<CommonCredential>>> processReadCredentials(DeviceKey key, Span span);

    @Override
    public final Future<OperationResult<Void>> updateCredentials(
            final String tenantId,
            final String deviceId,
            final List<CommonCredential> credentials,
            final Optional<String> resourceVersion,
            final Span span) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(deviceId);
        Objects.requireNonNull(credentials);
        Objects.requireNonNull(resourceVersion);
        Objects.requireNonNull(span);

        return this.tenantInformationService
                .getTenant(tenantId, span)
                .compose(tenant -> tenant.checkCredentialsLimitExceeded(tenantId, credentials))
                .compose(ok -> verifyAndEncodePasswords(credentials))
                .compose(encodedCredentials -> processUpdateCredentials(
                        DeviceKey.from(tenantId, deviceId),
                        encodedCredentials,
                        resourceVersion,
                        span))
                .onSuccess(result -> notificationSender
                        .handle(new CredentialsChangeNotification(tenantId, deviceId, Instant.now())))
                .recover(t -> DeviceRegistryUtils.mapError(t, tenantId));
    }

    @Override
    public final Future<OperationResult<List<CommonCredential>>> readCredentials(
            final String tenantId,
            final String deviceId,
            final Span span) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(deviceId);
        Objects.requireNonNull(span);

        return this.tenantInformationService

                .tenantExists(tenantId, span)
                .compose(result -> result.isError()
                        ? Future.failedFuture(StatusCodeMapper.from(
                                tenantId,
                                result.getStatus(),
                                "tenant does not exist"))
                        : processReadCredentials(DeviceKey.from(result.getPayload(), deviceId), span))

                // strip private information
                .map(v -> {
                    if (v != null && v.getPayload() != null) {
                        v.getPayload().forEach(CommonCredential::stripPrivateInfo);
                    }
                    return v;
                })
                .recover(t -> DeviceRegistryUtils.mapError(t, tenantId));
    }

    private Future<List<CommonCredential>> verifyAndEncodePasswords(final List<CommonCredential> credentials) {

        return DeviceRegistryUtils.assertTypeAndAuthIdUniqueness(credentials)
                .compose(ok -> {
                    // Check if we need to encode passwords
                    if (isEncodingOfSecretsRequired(credentials)) {
                        // ... yes, encode passwords asynchronously
                        return Futures.executeBlocking(this.vertx, () -> checkCredentials(credentials));
                    } else {
                        try {
                            // ... no, so don't fork off a worker task, but inline work
                            return Future.succeededFuture(checkCredentials(credentials));
                        } catch (final IllegalStateException e) {
                            return Future.failedFuture(new ClientErrorException(
                                    HttpURLConnection.HTTP_BAD_REQUEST,
                                    e.getMessage()));
                        }
                    }
                });
    }

    /**
     * Check if we need to encode any secrets.
     *
     * @param credentials The credentials to check.
     * @return {@code true} is the list contains at least one password which needs to be encoded on the
     * server side.
     */
    private static boolean isEncodingOfSecretsRequired(final List<CommonCredential> credentials) {
        return credentials
                .stream()
                .filter(PasswordCredential.class::isInstance)
                .map(PasswordCredential.class::cast)
                .flatMap(c -> c.getSecrets().stream())
                .anyMatch(s -> !Strings.isNullOrEmpty(s.getPasswordPlain()));
    }

    /**
     * Checks credentials and encodes secrets if necessary.
     *
     * @param credentials The credentials to check.
     * @return Verified and encoded credentials.
     * @throws IllegalStateException if the secret is not valid.
     */
    protected List<CommonCredential> checkCredentials(final List<CommonCredential> credentials) {

        for (final CommonCredential credential : credentials) {
            DeviceRegistryUtils.checkCredential(
                    credential,
                    passwordEncoder,
                    hashAlgorithmsWhitelist,
                    maxBcryptCostFactor);
        }

        return credentials;
    }

}
