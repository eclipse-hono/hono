/*******************************************************************************
 * Copyright (c) 2016, 2023 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.adapter.auth.device;

import java.net.HttpURLConnection;
import java.util.Objects;

import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.ServerErrorException;
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.client.registry.CredentialsClient;
import org.eclipse.hono.service.auth.DeviceUser;
import org.eclipse.hono.tracing.TracingHelper;
import org.eclipse.hono.util.CredentialsObject;
import org.eclipse.hono.util.MessageHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.User;


/**
 * A base class for implementing authentication providers that verify credentials provided by devices
 * against information on record retrieved using Hono's <em>Credentials</em> API.
 *
 * @param <T> The type of credentials this provider can validate.
 */
public abstract class CredentialsApiAuthProvider<T extends AbstractDeviceCredentials> implements
        DeviceCredentialsAuthProvider<T> {

    /**
     * A logger to be used by subclasses.
     */
    protected final Logger log = LoggerFactory.getLogger(getClass());
    private final CredentialsClient credentialsClient;
    private final Tracer tracer;

    /**
     * Creates a new authentication provider for a credentials client.
     *
     * @param credentialsClient The client for accessing the Credentials service.
     * @param tracer The tracer instance.
     * @throws NullPointerException if the client or the tracer are {@code null}
     */
    protected CredentialsApiAuthProvider(final CredentialsClient credentialsClient, final Tracer tracer) {
        this.credentialsClient = Objects.requireNonNull(credentialsClient);
        this.tracer = Objects.requireNonNull(tracer);
    }

    /**
     * Retrieves credentials from the Credentials service.
     *
     * @param deviceCredentials The credentials provided by the device.
     * @param spanContext The {@code SpanContext} (may be {@code null}).
     * @return A future containing the credentials on record as retrieved from
     *         Hono's <em>Credentials</em> API.
     * @throws NullPointerException if device credentials is {@code null}.
     */
    protected final Future<CredentialsObject> getCredentialsForDevice(
            final DeviceCredentials deviceCredentials,
            final SpanContext spanContext) {

        Objects.requireNonNull(deviceCredentials);
        return credentialsClient.get(
                deviceCredentials.getTenantId(),
                deviceCredentials.getType(),
                deviceCredentials.getAuthId(),
                deviceCredentials.getClientContext(),
                spanContext);
    }

    @Override
    public final void authenticate(
            final T deviceCredentials,
            final SpanContext spanContext,
            final Handler<AsyncResult<DeviceUser>> resultHandler) {

        Objects.requireNonNull(deviceCredentials);
        Objects.requireNonNull(resultHandler);

        final Span currentSpan = TracingHelper.buildServerChildSpan(tracer, spanContext, "authenticate device", getClass().getSimpleName())
                .withTag(MessageHelper.APP_PROPERTY_TENANT_ID, deviceCredentials.getTenantId())
                .withTag(TracingHelper.TAG_AUTH_ID.getKey(), deviceCredentials.getAuthId())
                .start();

        getCredentialsForDevice(deviceCredentials, currentSpan.context())
        .recover(t -> Future.failedFuture(mapNotFoundToBadCredentialsException(t)))
        .compose(credentialsOnRecord -> validateCredentials(deviceCredentials, credentialsOnRecord, currentSpan.context()))
        .onComplete(authAttempt -> {
            if (authAttempt.succeeded()) {
                currentSpan.log("successfully authenticated device");
            } else {
                currentSpan.log("authentication of device failed");
                TracingHelper.logError(currentSpan, authAttempt.cause());
            }
            currentSpan.finish();
            resultHandler.handle(authAttempt);
        });
    }

    /**
     * Checks if the given exception has status code <em>HTTP_NOT_FOUND</em> and returns a <em>HTTP_UNAUTHORIZED</em>
     * {@link ClientErrorException} with message "bad credentials" in that case.
     * Otherwise the given exception is returned.
     *
     * @param throwable The exception to map.
     * @return The mapped exception.
     */
    public static Throwable mapNotFoundToBadCredentialsException(final Throwable throwable) {
        return ServiceInvocationException.extractStatusCode(throwable) == HttpURLConnection.HTTP_NOT_FOUND
                ? new ClientErrorException(HttpURLConnection.HTTP_UNAUTHORIZED, "bad credentials")
                : throwable;
    }

    /**
     * Verifies that the credentials provided by a device during the authentication
     * process match the credentials on record for that device.
     *
     * @param deviceCredentials The credentials provided by the device.
     * @param credentialsOnRecord The credentials to match against.
     * @param spanContext The OpenTracing context to use for tracking the operation.
     * @return A future that is succeeded with the authenticated device if the
     *         credentials have been validated successfully. Otherwise, the
     *         future is failed with a {@link ServiceInvocationException}.
     */
    private Future<DeviceUser> validateCredentials(
            final T deviceCredentials,
            final CredentialsObject credentialsOnRecord,
            final SpanContext spanContext) {

        final Span currentSpan = TracingHelper.buildServerChildSpan(tracer, spanContext, "validate credentials", getClass().getSimpleName())
                .withTag(MessageHelper.APP_PROPERTY_TENANT_ID, deviceCredentials.getTenantId())
                .withTag(TracingHelper.TAG_AUTH_ID.getKey(), deviceCredentials.getAuthId())
                .withTag(TracingHelper.TAG_CREDENTIALS_TYPE.getKey(), deviceCredentials.getType())
                .start();

        final Promise<DeviceUser> result = Promise.promise();
        if (!deviceCredentials.getAuthId().equals(credentialsOnRecord.getAuthId())) {
            currentSpan.log(String.format(
                    "Credentials service returned wrong credentials-on-record [auth-id: %s]",
                    credentialsOnRecord.getAuthId()));
            result.fail(new ServerErrorException(HttpURLConnection.HTTP_INTERNAL_ERROR));
        } else if (!deviceCredentials.getType().equals(credentialsOnRecord.getType())) {
            currentSpan.log(String.format(
                    "Credentials service returned wrong credentials-on-record [type: %s]",
                    credentialsOnRecord.getType()));
            result.fail(new ServerErrorException(HttpURLConnection.HTTP_INTERNAL_ERROR));
        } else if (!credentialsOnRecord.isEnabled()) {
            currentSpan.log("credentials-on-record are disabled");
            result.fail(new ClientErrorException(HttpURLConnection.HTTP_UNAUTHORIZED));
        } else {
            doValidateCredentials(deviceCredentials, credentialsOnRecord).onComplete(result);
        }
        return result.future()
                .onSuccess(authenticatedDevice -> {
                    currentSpan.log("validation of credentials succeeded");
                    currentSpan.finish();
                })
                .onFailure(t -> {
                    currentSpan.log("validation of credentials failed");
                    TracingHelper.logError(currentSpan, t);
                    currentSpan.finish();
                });
    }

    /**
     * Verifies that the credentials provided by a device during the authentication
     * process match the credentials on record for that device.
     *
     * @param deviceCredentials The credentials provided by the device.
     * @param credentialsOnRecord The credentials on record.
     * @return A future that is succeeded with the authenticated device if the
     *         credentials have been validated successfully. Otherwise, the
     *         future is failed with a {@link ServiceInvocationException}.
     */
    protected abstract Future<DeviceUser> doValidateCredentials(
            T deviceCredentials,
            CredentialsObject credentialsOnRecord);

    @Override
    public final void authenticate(final JsonObject authInfo, final Handler<AsyncResult<User>> resultHandler) {

        final T credentials = getCredentials(Objects.requireNonNull(authInfo));
        if (credentials == null) {
            resultHandler.handle(Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_UNAUTHORIZED, "malformed credentials")));
        } else {
            authenticate(credentials, TracingHelper.extractSpanContext(tracer, authInfo), s -> {
                if (s.succeeded()) {
                    resultHandler.handle(Future.succeededFuture(s.result()));
                } else {
                    resultHandler.handle(Future.failedFuture(s.cause()));
                }
            });
        }
    }

}
