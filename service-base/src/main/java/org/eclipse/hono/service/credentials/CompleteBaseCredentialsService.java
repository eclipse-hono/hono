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

/**
 * A base class for implementing {@link CompleteCredentialsService}s.
 * <p>
 * In particular, this base class provides support for receiving service invocation request messages
 * via vert.x' event bus and routing them to specific methods corresponding to the operation indicated
 * in the message.
 *
 * @param <T> The type of configuration class this service supports.
 */
public abstract class CompleteBaseCredentialsService<T> extends BaseCredentialsService<T> implements CompleteCredentialsService {

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

        if (tenantId == null || payload == null) {
            return Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST));
        } else if (payload.isValid()) {
            final Future<CredentialsResult<JsonObject>> result = Future.future();
            add(tenantId, JsonObject.mapFrom(payload), result.completer());
            return result.map(res -> {
                return request.getResponse(res.getStatus())
                        .setDeviceId(payload.getDeviceId())
                        .setCacheDirective(res.getCacheDirective());
            });
        } else {
            return Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST));
        }
    }

    private Future<EventBusMessage> processUpdateRequest(final EventBusMessage request) {

        final String tenantId = request.getTenant();
        final CredentialsObject payload = Optional.ofNullable(request.getJsonPayload())
                .map(json -> json.mapTo(CredentialsObject.class)).orElse(null);

        if (tenantId == null || payload == null) {
            return Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST));
        } else if (payload.isValid()) {
            final Future<CredentialsResult<JsonObject>> result = Future.future();
            update(tenantId, JsonObject.mapFrom(payload), result.completer());
            return result.map(res -> {
                return request.getResponse(res.getStatus())
                        .setDeviceId(payload.getDeviceId())
                        .setCacheDirective(res.getCacheDirective());
            });
        } else {
            return Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST));
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
