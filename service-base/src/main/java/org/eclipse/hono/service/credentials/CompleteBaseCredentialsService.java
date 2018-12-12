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

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.util.CredentialsConstants;
import org.eclipse.hono.util.CredentialsObject;
import org.eclipse.hono.util.CredentialsResult;
import org.eclipse.hono.util.EventBusMessage;

import java.net.HttpURLConnection;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    private static final Pattern BCRYPT_PATTERN = Pattern.compile("\\A\\$2a\\$(\\d{1,2})\\$[./0-9A-Za-z]{53}");
    private static final int DEFAULT_MAX_BCRYPT_ITERATIONS = 10;
    /**
     * The default hash function for hashing plain text passwords.
     */
    // TODO make configurable.
    public static final String DEFAULT_HASH_FUNCTION = CredentialsConstants.HASH_FUNCTION_BCRYPT; 

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
            final Future<EventBusMessage> requestTracker = Future.future();
            getVertx().executeBlocking(blockingCodeHandler -> {
                log.debug("hashing password on vert.x worker thread [{}]", Thread.currentThread().getName());
                hashPlainPasswords(payload);
                try {
                    payload.checkValidity(this::checkSecret);
                    final Future<CredentialsResult<JsonObject>> result = Future.future();
                    add(tenantId, JsonObject.mapFrom(payload), result.completer());
                    blockingCodeHandler.complete(result.map(res -> {
                        return request.getResponse(res.getStatus())
                                .setDeviceId(payload.getDeviceId())
                                .setCacheDirective(res.getCacheDirective());
                    }));
                } catch (IllegalStateException e) {
                    throw new ClientErrorException(
                            HttpURLConnection.HTTP_BAD_REQUEST,
                            e.getMessage());
                }
            }, ar -> {
                if (ar.succeeded()) {
                    requestTracker.complete(((Future<EventBusMessage>) ar.result()).result());
                } else {
                    requestTracker.fail(ar.cause());
                }
            });
            return requestTracker;
        }
    }

    private void checkSecret(final String type, final JsonObject secret) {
        switch(type) {
        case CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD:
            final String hashFunction = secret.getString(CredentialsConstants.FIELD_SECRETS_HASH_FUNCTION);
            switch(hashFunction) {
            case CredentialsConstants.HASH_FUNCTION_BCRYPT:
                verifyBcryptPasswordHash(secret);
                break;
            default:
            }
        default:
        }
    }

    /**
     * Hashes plaintext passwords in the given {@link CredentialsObject} if present.
     *
     * @param credentialsObject The credentials to be updated with hashed passwords.
     */
    private void hashPlainPasswords(final CredentialsObject credentialsObject) {
        if (CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD.equals(credentialsObject.getType())) {
            credentialsObject.hashPlainPasswords(getHashFunction());
        }
    }

    /**
     * Invoked as part of payload validation when adding or updating <em>hashed password</em>
     * credentials using the <em>bcrypt</em> hash algorithm.
     * <p>
     * Verifies that the hashed password matches the bcrypt hash pattern and doesn't use more
     * than the configured maximum number of iterations as returned by {@link #getMaxBcryptIterations()}.
     * 
     * @param secret The secret to verify.
     * @throws IllegalArgumentException if the password hash is invalid.
     */
    protected void verifyBcryptPasswordHash(final JsonObject secret) {

        final String pwdHash = ((JsonObject) secret).getString(CredentialsConstants.FIELD_SECRETS_PWD_HASH);
        final Matcher matcher = BCRYPT_PATTERN.matcher(pwdHash);
        if (matcher.matches()) {
            // check that hash doesn't use more iterations than configured maximum
            final int iterations = Integer.valueOf(matcher.group(1));
            if (iterations > getMaxBcryptIterations()) {
                throw new IllegalArgumentException("max number of BCrypt iterations exceeded");
            }
        } else {
            // not a valid bcrypt hash
            throw new IllegalArgumentException("not a BCrypt hash");
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
     * Gets the hash function to be used to hash plaintext passwords.
     * <p>
     * This default implementation returns "bcrypt".
     * <p>
     * Subclasses should override this method in order to e.g. return a value determined from a configuration property.
     * For supported values see {@link CredentialsObject#hashPwdAndUpdateSecret(JsonObject, String)}.
     * <p>
     * 
     * @return The name of the hash function.
     */
    protected String getHashFunction() {
        return DEFAULT_HASH_FUNCTION;
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
            final Future<EventBusMessage> requestTracker = Future.future();
            getVertx().executeBlocking(blockingCodeHandler -> {
                log.debug("hashing password on vert.x worker thread [{}]", Thread.currentThread().getName());
                hashPlainPasswords(payload);
                try {
                    payload.checkValidity(this::checkSecret);
                    final Future<CredentialsResult<JsonObject>> result = Future.future();
                    update(tenantId, JsonObject.mapFrom(payload), result.completer());
                    blockingCodeHandler.complete(result.map(res -> {
                        return request.getResponse(res.getStatus())
                                .setDeviceId(payload.getDeviceId())
                                .setCacheDirective(res.getCacheDirective());
                    }));
                } catch (IllegalStateException e) {
                    throw new ClientErrorException(
                            HttpURLConnection.HTTP_BAD_REQUEST,
                            e.getMessage());
                }
            }, ar -> {
                if (ar.succeeded()) {
                    requestTracker.complete(((Future<EventBusMessage>) ar.result()).result());
                } else {
                    requestTracker.fail(ar.cause());
                }
            });
            return requestTracker;
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
}
