/*******************************************************************************
 * Copyright (c) 2016, 2018 Contributors to the Eclipse Foundation
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
package org.eclipse.hono.service.credentials;

import java.net.HttpURLConnection;
import java.util.Objects;
import java.util.Optional;

import org.eclipse.hono.auth.BCryptHelper;
import org.eclipse.hono.auth.HonoPasswordEncoder;
import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.util.CredentialsConstants;
import org.eclipse.hono.util.CredentialsObject;
import org.eclipse.hono.util.CredentialsResult;
import org.eclipse.hono.util.EventBusMessage;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;

/**
 * A base class for implementing {@link CompleteCredentialsService}s.
 * <p>
 * In particular, this base class provides support for receiving service invocation request messages
 * via vert.x' event bus and routing them to specific methods corresponding to the operation indicated
 * in the message.
 *
 * @param <T> The type of configuration class this service supports.
 */
public abstract class CompleteBaseCredentialsService<T> extends BaseCredentialsService<T>
    implements CompleteCredentialsService {

    private static final int DEFAULT_MAX_BCRYPT_ITERATIONS = 10;

    private HonoPasswordEncoder pwdEncoder;

    /**
     * Creates a new service instance for a password encoder.
     * 
     * @param pwdEncoder The encoder to use for hashing clear text passwords.
     * @throws NullPointerException if encoder is {@code null}.
     */
    protected CompleteBaseCredentialsService(final HonoPasswordEncoder pwdEncoder) {
        this.pwdEncoder = Objects.requireNonNull(pwdEncoder);
    }

    /**
     * Processes a Credentials API request received via the vert.x event bus.
     * <p>
     * This method validates the request parameters against the Credentials API
     * specification before invoking the corresponding {@code CredentialsService} methods.
     *
     * @param request The request message.
     * @return A future indicating the outcome of the service invocation.
     * @throws NullPointerException If the request message is {@code null}.
     */
    @Override
    public final Future<EventBusMessage> processRequest(final EventBusMessage request) {

        Objects.requireNonNull(request);

        final String operation = request.getOperation();

        switch (CredentialsConstants.CredentialsAction.from(operation)) {
            case get:
                return processGetRequest(request);
            case add:
                return processAddRequest(request);
            case update:
                return processUpdateRequest(request);
            case remove:
                return processRemoveRequest(request);
            default:
                return processCustomCredentialsMessage(request);
        }
    }

    private Future<EventBusMessage> processAddRequest(final EventBusMessage request) {

        final String tenantId = request.getTenant();
        final CredentialsObject payload = Optional.ofNullable(request.getJsonPayload())
                .map(json -> json.mapTo(CredentialsObject.class)).orElse(null);

        if (tenantId == null) {
            return Future.failedFuture(new ClientErrorException(
                    HttpURLConnection.HTTP_BAD_REQUEST,
                    "missing tenant ID"));
        } else if (payload == null) {
            return Future.failedFuture(new ClientErrorException(
                    HttpURLConnection.HTTP_BAD_REQUEST,
                    "missing payload"));
        } else {
            return hashPlainPasswords(payload).compose(credentials -> doAdd(request, tenantId, credentials));
        }
    }

    private Future<EventBusMessage> doAdd(final EventBusMessage request, final String tenantId,
            final CredentialsObject payload) {
        try {
            payload.checkValidity(this::checkSecret);
            final Future<CredentialsResult<JsonObject>> result = Future.future();
            add(tenantId, JsonObject.mapFrom(payload), result.completer());
            return result.map(res -> {
                return request.getResponse(res.getStatus())
                        .setDeviceId(payload.getDeviceId())
                        .setCacheDirective(res.getCacheDirective());
            });
        } catch (IllegalStateException e) {
            return Future.failedFuture(new ClientErrorException(
                    HttpURLConnection.HTTP_BAD_REQUEST,
                    e.getMessage()));
        }
    }

    private Future<EventBusMessage> processUpdateRequest(final EventBusMessage request) {

        final String tenantId = request.getTenant();
        final CredentialsObject payload = Optional.ofNullable(request.getJsonPayload())
                .map(json -> json.mapTo(CredentialsObject.class)).orElse(null);

        if (tenantId == null) {
            return Future.failedFuture(new ClientErrorException(
                    HttpURLConnection.HTTP_BAD_REQUEST,
                    "missing tenant ID"));
        } else if (payload == null) {
            return Future.failedFuture(new ClientErrorException(
                    HttpURLConnection.HTTP_BAD_REQUEST,
                    "missing payload"));
        } else {
            return hashPlainPasswords(payload).compose(credentials -> doUpdate(request, tenantId, credentials));
        }
    }

    private Future<EventBusMessage> doUpdate(final EventBusMessage request, final String tenantId,
            final CredentialsObject payload) {
        try {
            payload.checkValidity(this::checkSecret);
            final Future<CredentialsResult<JsonObject>> result = Future.future();
            update(tenantId, JsonObject.mapFrom(payload), result.completer());
            return result.map(res -> {
                return request.getResponse(res.getStatus())
                        .setDeviceId(payload.getDeviceId())
                        .setCacheDirective(res.getCacheDirective());
            });
        } catch (IllegalStateException e) {
            return Future.failedFuture(new ClientErrorException(
                    HttpURLConnection.HTTP_BAD_REQUEST,
                    e.getMessage()));
        }
    }

    private Future<EventBusMessage> processRemoveRequest(final EventBusMessage request) {

        final String tenantId = request.getTenant();
        final JsonObject payload = request.getJsonPayload();

        if (tenantId == null || payload == null) {
            return Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST));
        } else {
            final String type = getTypesafeValueForField(String.class, payload, CredentialsConstants.FIELD_TYPE);
            final String authId = getTypesafeValueForField(String.class, payload, CredentialsConstants.FIELD_AUTH_ID);
            final String deviceId = getTypesafeValueForField(String.class, payload,
                    CredentialsConstants.FIELD_PAYLOAD_DEVICE_ID);

            // there exist several valid combinations of parameters

            if (type == null) {
                log.debug("remove credentials request does not contain mandatory type parameter");
                return Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST));
            } else if (!type.equals(CredentialsConstants.SPECIFIER_WILDCARD) && authId != null) {
                // delete a single credentials instance
                log.debug("removing specific credentials [tenant: {}, type: {}, auth-id: {}]", tenantId, type, authId);
                final Future<CredentialsResult<JsonObject>> result = Future.future();
                remove(tenantId, type, authId, result.completer());
                return result.map(res -> {
                    return request.getResponse(res.getStatus())
                            .setCacheDirective(res.getCacheDirective());
                });
            } else if (deviceId != null && type.equals(CredentialsConstants.SPECIFIER_WILDCARD)) {
                // delete all credentials for device
                log.debug("removing all credentials for device [tenant: {}, device-id: {}]", tenantId, deviceId);
                final Future<CredentialsResult<JsonObject>> result = Future.future();
                removeAll(tenantId, deviceId, result.completer());
                return result.map(res -> {
                    return request.getResponse(res.getStatus())
                            .setDeviceId(deviceId)
                            .setCacheDirective(res.getCacheDirective());
                });
            } else {
                log.debug("remove credentials request contains invalid search criteria [type: {}, device-id: {}, auth-id: {}]",
                        type, deviceId, authId);
                return Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST));
            }
        }
    }

    /**
     * {@inheritDoc}
     *
     * This default implementation simply returns an empty result with status code 501 (Not Implemented).
     * Subclasses should override this method in order to provide a reasonable implementation.
     */
    @Override
    public void add(final String tenantId, final JsonObject otherKeys, final Handler<AsyncResult<CredentialsResult<JsonObject>>> resultHandler) {
        handleUnimplementedOperation(resultHandler);
    }

    /**
     * {@inheritDoc}
     *
     * This default implementation simply returns an empty result with status code 501 (Not Implemented).
     * Subclasses should override this method in order to provide a reasonable implementation.
     */
    @Override
    public void update(final String tenantId, final JsonObject otherKeys, final Handler<AsyncResult<CredentialsResult<JsonObject>>> resultHandler) {
        handleUnimplementedOperation(resultHandler);
    }

    /**
     * {@inheritDoc}
     *
     * This default implementation simply returns an empty result with status code 501 (Not Implemented).
     * Subclasses should override this method in order to provide a reasonable implementation.
     */
    @Override
    public void remove(final String tenantId, final String type, final String authId, final Handler<AsyncResult<CredentialsResult<JsonObject>>> resultHandler) {
        handleUnimplementedOperation(resultHandler);
    }

    /**
     * {@inheritDoc}
     *
     * This default implementation simply returns an empty result with status code 501 (Not Implemented).
     * Subclasses should override this method in order to provide a reasonable implementation.
     */
    @Override
    public void removeAll(final String tenantId, final String deviceId, final Handler<AsyncResult<CredentialsResult<JsonObject>>> resultHandler) {
        handleUnimplementedOperation(resultHandler);
    }

    private void checkSecret(final String type, final JsonObject secret) {
        switch(type) {
        case CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD:
            switch(CredentialsConstants.getHashFunction(secret)) {
            case CredentialsConstants.HASH_FUNCTION_BCRYPT:
                final String pwdHash = CredentialsConstants.getPasswordHash(secret);
                verifyBcryptPasswordHash(pwdHash);
                break;
            default:
                // pass
            }
        default:
            // pass
        }
    }

    /**
     * Gets the maximum number of iterations that should be allowed in password hashes
     * created using the <em>BCrypt</em> hash function.
     * <p>
     * This default implementation returns 10.
     * <p>
     * Subclasses should override this method in order to e.g. return a value determined
     * from a configuration property.
     * 
     * @return The number of iterations.
     */
    protected int getMaxBcryptIterations() {
        return DEFAULT_MAX_BCRYPT_ITERATIONS;
    }

    /**
     * Hashes clear text passwords contained in hashed-password credentials
     * provided by a client.
     *
     * @param credentials The credentials to hash the clear text passwords for.
     * @return A future containing the (updated) credentials.
     */
    protected final Future<CredentialsObject> hashPlainPasswords(final CredentialsObject credentials) {

        final Future<CredentialsObject> result = Future.future();
        if (CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD.equals(credentials.getType())) {
            getVertx().executeBlocking(blockingCodeHandler -> {
                log.debug("hashing password on vert.x worker thread [{}]", Thread.currentThread().getName());
                credentials.getSecrets().forEach(secret -> hashPwdAndUpdateSecret((JsonObject) secret));
                blockingCodeHandler.complete(credentials);
            }, result);
        } else {
            result.complete(credentials);
        }
        return result;
    }

    private JsonObject hashPwdAndUpdateSecret(final JsonObject secret) {

        final String pwd = Optional.ofNullable(secret.remove(CredentialsConstants.FIELD_SECRETS_PWD_PLAIN))
                .map(o -> {
                    if (o instanceof String) {
                        return (String) o;
                    } else {
                        return "";
                    }
                }).orElse("");

        if (pwd.isEmpty()) {
            return secret;
        }

        final JsonObject encodedPwd = pwdEncoder.encode(pwd);
        secret.mergeIn(encodedPwd);
        return secret;
    }

    /**
     * Verifies that a hash value is a valid BCrypt password hash.
     * <p>
     * The hash must be a version 2a hash and must not use more than the configured
     * maximum number of iterations as returned by {@link #getMaxBcryptIterations()}.
     * 
     * @param pwdHash The hash to verify.
     * @throws IllegalStateException if the secret does not match the criteria.
     */
    protected void verifyBcryptPasswordHash(final String pwdHash) {

        Objects.requireNonNull(pwdHash);
        if (BCryptHelper.getIterations(pwdHash) > getMaxBcryptIterations()) {
            throw new IllegalStateException("password hash uses too many iterations, max is " + getMaxBcryptIterations());
        }
    }
}
