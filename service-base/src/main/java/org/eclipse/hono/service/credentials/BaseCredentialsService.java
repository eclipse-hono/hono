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

import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.service.EventBusService;
import org.eclipse.hono.util.CredentialsConstants;
import org.eclipse.hono.util.CredentialsResult;
import org.eclipse.hono.util.EventBusMessage;
import org.eclipse.hono.util.TenantConstants;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;

/**
 * Base class for implementing {@link CredentialsService}s.
 * Only implements the mandatory operations of the API.
 * See {@link CompleteCredentialsService} and {@link CompleteBaseCredentialsService}
 * for the optional API operations.
 * <p>
 * This base class provides support for receiving "Get" request messages
 * via vert.x' event bus and route them to specific methods corresponding to the operation indicated
 * in the message.
 *
 * Note: Subclasses <b>MUST</b> implement {@link #getAll(String, String, Handler)}, as it is used
 * by the routing mechanism (see {@link #processGetRequest(EventBusMessage)}).
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
     * @param request The request message.
     * @return A future indicating the outcome of the service invocation.
     * @throws NullPointerException If the request message is {@code null}.
     */
    @Override
    public Future<EventBusMessage> processRequest(final EventBusMessage request) {

        Objects.requireNonNull(request);

        final String operation = request.getOperation();

        switch (CredentialsConstants.CredentialsAction.from(operation)) {
            case get:
                return processGetRequest(request);
            default:
                return processCustomCredentialsMessage(request);
        }
    }

    Future<EventBusMessage> processGetRequest(final EventBusMessage request) {

        final String tenantId = request.getTenant();
        final JsonObject payload = request.getJsonPayload();

        if (tenantId == null || payload == null) {
            return Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST));
        } else {
            final String type = removeTypesafeValueForField(String.class, payload, CredentialsConstants.FIELD_TYPE);
            final String authId = removeTypesafeValueForField(String.class, payload,
                    CredentialsConstants.FIELD_AUTH_ID);
            final String deviceId = removeTypesafeValueForField(String.class, payload,
                    CredentialsConstants.FIELD_PAYLOAD_DEVICE_ID);

            if (type == null) {
                return Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST));
            } else if (authId != null && deviceId == null) {
                log.debug("getting credentials [tenant: {}, type: {}, auth-id: {}]", tenantId, type, authId);
                final Future<CredentialsResult<JsonObject>> result = Future.future();
                get(tenantId, type, authId, payload, result.completer());
                return result.map(res -> {
                    final String deviceIdFromPayload = Optional.ofNullable(res.getPayload())
                            .map(p -> getTypesafeValueForField(String.class, p,
                                    TenantConstants.FIELD_PAYLOAD_DEVICE_ID))
                            .orElse(null);
                    return request.getResponse(res.getStatus())
                            .setDeviceId(deviceIdFromPayload)
                            .setJsonPayload(res.getPayload())
                            .setCacheDirective(res.getCacheDirective());
                });
            } else if (deviceId != null && authId == null) {
                log.debug("getting credentials for device [tenant: {}, device-id: {}]", tenantId, deviceId);
                final Future<CredentialsResult<JsonObject>> result = Future.future();
                getAll(tenantId, deviceId, result.completer());
                return result.map(res -> {
                    return request.getResponse(res.getStatus())
                            .setDeviceId(deviceId)
                            .setJsonPayload(res.getPayload())
                            .setCacheDirective(res.getCacheDirective());
                });
            } else {
                log.debug("get credentials request contains invalid search criteria [type: {}, device-id: {}, auth-id: {}]",
                        type, deviceId, authId);
                return Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST));
            }
        }
    }

    /**
     * Processes a request for a non-standard operation.
     * <p>
     * Subclasses should override this method in order to support additional, custom
     * operations that are not defined by Hono's Credentials API.
     * <p>
     * This default implementation simply returns a future that is failed with a
     * {@link ClientErrorException} with an error code <em>400 Bad Request</em>.
     *
     * @param request The request to process.
     * @return A future indicating the outcome of the service invocation.
     */
    protected Future<EventBusMessage> processCustomCredentialsMessage(final EventBusMessage request) {
        log.debug("invalid operation in request message [{}]", request.getOperation());
        return Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST));
    };

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
    public void get(final String tenantId, final String type, final String authId, final JsonObject clientContext, final Handler<AsyncResult<CredentialsResult<JsonObject>>> resultHandler) {
        handleUnimplementedOperation(resultHandler);
    }

    /**
     * {@inheritDoc}
     *
     * Subclasses MUST implement this method in order to build a working Credential Service.
     */
    public abstract void getAll(String tenantId, String deviceId, Handler<AsyncResult<CredentialsResult<JsonObject>>> resultHandler);

    /**
     * Handles an unimplemented operation by failing the given handler
     * with a {@link ClientErrorException} having a <em>501 Not Implemented</em> status code.
     *
     * @param resultHandler The handler.
     */
    protected void handleUnimplementedOperation(final Handler<AsyncResult<CredentialsResult<JsonObject>>> resultHandler) {
        resultHandler.handle(Future.succeededFuture(CredentialsResult.from(HttpURLConnection.HTTP_NOT_IMPLEMENTED)));
    }
}
