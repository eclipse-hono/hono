/*******************************************************************************
 * Copyright (c) 2016, 2019 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.service.auth.device;

import java.net.HttpURLConnection;
import java.util.Objects;

import org.eclipse.hono.auth.Device;
import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.CredentialsClient;
import org.eclipse.hono.client.CredentialsClientFactory;
import org.eclipse.hono.client.ServerErrorException;
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.service.auth.DeviceUser;
import org.eclipse.hono.tracing.TracingHelper;
import org.eclipse.hono.util.CredentialsObject;
import org.eclipse.hono.util.ExecutionContext;
import org.eclipse.hono.util.GenericExecutionContext;
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
    private final CredentialsClientFactory credentialsClientFactory;
    private final Tracer tracer;

    /**
     * Creates a new authentication provider for a credentials client factory.
     *
     * @param credentialsClientFactory The factory.
     * @param tracer The tracer instance.
     * @throws NullPointerException if the factory or the tracer are {@code null}
     */
    public CredentialsApiAuthProvider(final CredentialsClientFactory credentialsClientFactory, final Tracer tracer) {
        this.credentialsClientFactory = Objects.requireNonNull(credentialsClientFactory);
        this.tracer = Objects.requireNonNull(tracer);
    }

    /**
     * Gets a client for the Credentials service.
     *
     * @param tenantId The tenant to get the client for.
     * @return A future containing the client.
     */
    protected final Future<CredentialsClient> getCredentialsClient(final String tenantId) {
        return credentialsClientFactory.getOrCreateCredentialsClient(tenantId);
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
    protected final Future<CredentialsObject> getCredentialsForDevice(final DeviceCredentials deviceCredentials,
            final SpanContext spanContext) {

        Objects.requireNonNull(deviceCredentials);
        return getCredentialsClient(deviceCredentials.getTenantId())
                .compose(client -> client.get(deviceCredentials.getType(), deviceCredentials.getAuthId(),
                        deviceCredentials.getClientContext(), spanContext));
    }

    @Override
    public final void authenticate(
            final T deviceCredentials,
            final ExecutionContext executionContext,
            final Handler<AsyncResult<DeviceUser>> resultHandler) {

        Objects.requireNonNull(deviceCredentials);
        Objects.requireNonNull(executionContext);
        Objects.requireNonNull(resultHandler);

        final Span currentSpan = TracingHelper.buildServerChildSpan(tracer, executionContext.getTracingContext(), "authenticate device", getClass().getSimpleName())
                .withTag(MessageHelper.APP_PROPERTY_TENANT_ID, deviceCredentials.getTenantId())
                .withTag(TracingHelper.TAG_AUTH_ID.getKey(), deviceCredentials.getAuthId())
                .start();

        getCredentialsForDevice(deviceCredentials, currentSpan.context())
        .recover(t -> {

            if (t instanceof ServiceInvocationException) {
                final ServiceInvocationException e = (ServiceInvocationException) t;
                if (e.getErrorCode() == HttpURLConnection.HTTP_NOT_FOUND) {
                    return Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_UNAUTHORIZED, "bad credentials"));
                }
            }
            return Future.failedFuture(t);
        }).compose(credentialsOnRecord -> validateCredentials(deviceCredentials, credentialsOnRecord, currentSpan.context()))
        .map(device -> new DeviceUser(device.getTenantId(), device.getDeviceId()))
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
    private Future<Device> validateCredentials(
            final T deviceCredentials,
            final CredentialsObject credentialsOnRecord,
            final SpanContext spanContext) {

        final Span currentSpan = TracingHelper.buildServerChildSpan(tracer, spanContext, "validate credentials", getClass().getSimpleName())
                .withTag(MessageHelper.APP_PROPERTY_TENANT_ID, deviceCredentials.getTenantId())
                .withTag(TracingHelper.TAG_AUTH_ID.getKey(), deviceCredentials.getAuthId())
                .withTag(TracingHelper.TAG_CREDENTIALS_TYPE.getKey(), deviceCredentials.getType())
                .start();

        final Promise<Device> result = Promise.promise();
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
                .map(device -> {
                    currentSpan.log("validation of credentials succeeded");
                    currentSpan.finish();
                    return device;
                })
                .recover(t -> {
                    currentSpan.log("validation of credentials failed");
                    TracingHelper.logError(currentSpan, t);
                    currentSpan.finish();
                    return Future.failedFuture(t);
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
    protected abstract Future<Device> doValidateCredentials(
            T deviceCredentials,
            CredentialsObject credentialsOnRecord);

    @Override
    public final void authenticate(final JsonObject authInfo, final Handler<AsyncResult<User>> resultHandler) {
        final ExecutionContext executionContext = new GenericExecutionContext(
                TracingHelper.extractSpanContext(tracer, authInfo));
        authenticate(authInfo, executionContext, s -> {
            if (s.succeeded()) {
                resultHandler.handle(Future.succeededFuture(s.result()));
            } else {
                resultHandler.handle(Future.failedFuture(s.cause()));
            }
        });
    }

    @Override
    public final void authenticate(
            final JsonObject authInfo,
            final ExecutionContext executionContext,
            final Handler<AsyncResult<DeviceUser>> resultHandler) {

        Objects.requireNonNull(authInfo);
        Objects.requireNonNull(executionContext);
        Objects.requireNonNull(resultHandler);

        final T credentials = getCredentials(authInfo);
        if (credentials == null) {
            resultHandler.handle(Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_UNAUTHORIZED, "malformed credentials")));
        } else {
            authenticate(credentials, executionContext, resultHandler);
        }
    }

    /**
     * Creates device credentials from authentication information provided by a
     * device.
     * <p>
     * Subclasses need to create a concrete {@code DeviceCredentials} instance based on
     * the information contained in the JSON object.
     *
     * @param authInfo The credentials provided by the device.
     * @return The device credentials or {@code null} if the auth info does not contain
     *         the required information.
     * @throws NullPointerException if auth info is {@code null}.
     */
    protected abstract T getCredentials(JsonObject authInfo);

}
