/**
 * Copyright (c) 2017, 2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 */
package org.eclipse.hono.service.credentials;

import java.net.HttpURLConnection;
import java.util.Objects;
import java.util.Optional;

import org.eclipse.hono.service.EventBusService;
import org.eclipse.hono.util.CredentialsConstants;
import org.eclipse.hono.util.CredentialsObject;
import org.eclipse.hono.util.CredentialsResult;
import org.eclipse.hono.util.EventBusMessage;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;

/**
 * Base class for implementing {@link CredentialsService}s.
 * <p>
 * In particular, this base class provides support for receiving service invocation request messages
 * via vert.x' event bus and route them to specific methods corresponding to the operation indicated
 * in the message.
 * 
 * @param <T> The type of configuration class this service supports.
 */
public abstract class BaseCredentialsService<T> extends EventBusService<T> implements CredentialsService {

    @Override
    protected String getEventBusAddress() {
        return CredentialsConstants.EVENT_BUS_ADDRESS_CREDENTIALS_IN;
    }

    /**
     * Processes a Credentials API request received via the vert.x event bus.
     * <p>
     * This method validates the request parameters against the Credentials API
     * specification before invoking the corresponding {@code CredentialsService} methods.
     * 
     * @param requestMessage The request message.
     * @return A future indicating the outcome of the service invocation.
     * @throws NullPointerException If the request message is {@code null}.
     */
    @Override
    public final Future<EventBusMessage> processRequest(final EventBusMessage requestMessage) {

        Objects.requireNonNull(requestMessage);

        if (log.isTraceEnabled()) {
            log.trace("received request: {}", requestMessage.toJson());
        }

        return processCredentialsRequest(requestMessage).map(result -> {
            return EventBusMessage.forStatusCode(result.getStatus())
                    .setTenant(requestMessage.getTenant())
                    .setDeviceId(requestMessage.getDeviceId())
                    .setJsonPayload(result.getPayload())
                    .setCacheDirective(result.getCacheDirective());
        }).recover(t -> {
            return Future.failedFuture(t);
        });
    }

    private Future<CredentialsResult<JsonObject>> processCredentialsRequest(final EventBusMessage request) {

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

    private Future<CredentialsResult<JsonObject>> processGetRequest(final EventBusMessage request) {

        final Future<CredentialsResult<JsonObject>> result = Future.future();
        final String tenantId = request.getTenant();
        final JsonObject payload = request.getJsonPayload();

        if (tenantId == null || payload == null) {
            result.complete(CredentialsResult.from(HttpURLConnection.HTTP_BAD_REQUEST));
        } else {
            final String type = getTypesafeValueForField(payload, CredentialsConstants.FIELD_TYPE);
            final String authId = getTypesafeValueForField(payload, CredentialsConstants.FIELD_AUTH_ID);
            final String deviceId = getTypesafeValueForField(payload, CredentialsConstants.FIELD_PAYLOAD_DEVICE_ID);

            if (type == null) {
                result.complete(CredentialsResult.from(HttpURLConnection.HTTP_BAD_REQUEST));
            } else if (authId != null && deviceId == null) {
                log.debug("getting credentials [tenant: {}, type: {}, auth-id: {}]", tenantId, type, authId);
                get(tenantId, type, authId, result.completer());
            } else if (deviceId != null && authId == null) {
                log.debug("getting credentials for device [tenant: {}, device-id: {}]", tenantId, deviceId);
                getAll(tenantId, deviceId, result.completer());
            } else {
                log.debug("get credentials request contains invalid search criteria [type: {}, device-id: {}, auth-id: {}]",
                        type, deviceId, authId);
                result.complete(CredentialsResult.from(HttpURLConnection.HTTP_BAD_REQUEST));
            }
        }
        return result;
    }

    private Future<CredentialsResult<JsonObject>> processAddRequest(final EventBusMessage request) {

        final Future<CredentialsResult<JsonObject>> result = Future.future();
        final String tenantId = request.getTenant();
        final CredentialsObject payload = Optional.ofNullable(request.getJsonPayload())
                .map(json -> json.mapTo(CredentialsObject.class)).orElse(null);

        if (tenantId == null || payload == null) {
            result.complete(CredentialsResult.from(HttpURLConnection.HTTP_BAD_REQUEST));
        } else if (payload.isValid()) {
            add(tenantId, JsonObject.mapFrom(payload), result.completer());
        } else {
            result.complete(CredentialsResult.from(HttpURLConnection.HTTP_BAD_REQUEST));
        }
        return result;
    }

    private Future<CredentialsResult<JsonObject>> processUpdateRequest(final EventBusMessage request) {

        final Future<CredentialsResult<JsonObject>> result = Future.future();
        final String tenantId = request.getTenant();
        final CredentialsObject payload = Optional.ofNullable(request.getJsonPayload())
                .map(json -> json.mapTo(CredentialsObject.class)).orElse(null);

        if (tenantId == null || payload == null) {
            result.complete(CredentialsResult.from(HttpURLConnection.HTTP_BAD_REQUEST));
        } else if (payload.isValid()) {
            update(tenantId, JsonObject.mapFrom(payload), result.completer()); 
        } else {
            result.complete(CredentialsResult.from(HttpURLConnection.HTTP_BAD_REQUEST));
        }
        return result;
    }

    private Future<CredentialsResult<JsonObject>> processRemoveRequest(final EventBusMessage request) {

        final Future<CredentialsResult<JsonObject>> result = Future.future();
        final String tenantId = request.getTenant();
        final JsonObject payload = request.getJsonPayload();

        if (tenantId == null || payload == null) {
            result.complete(CredentialsResult.from(HttpURLConnection.HTTP_BAD_REQUEST));
        } else {
            final String type = getTypesafeValueForField(payload, CredentialsConstants.FIELD_TYPE);
            final String authId = getTypesafeValueForField(payload, CredentialsConstants.FIELD_AUTH_ID);
            final String deviceId = getTypesafeValueForField(payload, CredentialsConstants.FIELD_PAYLOAD_DEVICE_ID);

            // there exist several valid combinations of parameters

            if (type == null) {
                log.debug("remove credentials request does not contain mandatory type parameter");
                result.complete(CredentialsResult.from(HttpURLConnection.HTTP_BAD_REQUEST));
            } else if (!type.equals(CredentialsConstants.SPECIFIER_WILDCARD) && authId != null) {
                // delete a single credentials instance
                log.debug("removing specific credentials [tenant: {}, type: {}, auth-id: {}]", tenantId, type, authId);
                remove(tenantId, type, authId, result.completer());
            } else if (deviceId != null && type.equals(CredentialsConstants.SPECIFIER_WILDCARD)) {
                // delete all credentials for device
                log.debug("removing all credentials for device [tenant: {}, device-id: {}]", tenantId, deviceId);
                removeAll(tenantId, deviceId, result.completer());
            } else {
                log.debug("remove credentials request contains invalid search criteria [type: {}, device-id: {}, auth-id: {}]",
                        type, deviceId, authId);
                result.complete(CredentialsResult.from(HttpURLConnection.HTTP_BAD_REQUEST));
            }
        }
        return result;
    }

    /**
     * Processes a request for a non-standard operation.
     * <p>
     * Subclasses should override this method in order to support additional, custom
     * operations that are not defined by Hono's Credentials API.
     * <p>
     * This default implementation simply returns a succeeded future containing a
     * result with status code <em>400 Bad Request</em>.
     *
     * @param request The request to process.
     * @return A future indicating the outcome of the service invocation.
     */
    protected Future<CredentialsResult<JsonObject>> processCustomCredentialsMessage(final EventBusMessage request) {
        log.debug("invalid operation in request message [{}]", request.getOperation());
        return Future.succeededFuture(CredentialsResult.from(HttpURLConnection.HTTP_BAD_REQUEST));
    };

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
    public void get(final String tenantId, final String type, final String authId, final Handler<AsyncResult<CredentialsResult<JsonObject>>> resultHandler) {
        handleUnimplementedOperation(resultHandler);
    }

    /**
     * {@inheritDoc}
     * 
     * This default implementation simply returns an empty result with status code 501 (Not Implemented).
     * Subclasses should override this method in order to provide a reasonable implementation.
     */
    @Override
    public void getAll(final String tenantId, final String deviceId, final Handler<AsyncResult<CredentialsResult<JsonObject>>> resultHandler) {
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

    /**
     * Handles an unimplemented operation by succeeding the given handler
     * with a result having a <em>501 Not Implemented</em> status code.
     * 
     * @param resultHandler The handler.
     */
    protected void handleUnimplementedOperation(final Handler<AsyncResult<CredentialsResult<JsonObject>>> resultHandler) {
        resultHandler.handle(Future.succeededFuture(CredentialsResult.from(HttpURLConnection.HTTP_NOT_IMPLEMENTED)));
    }
}
