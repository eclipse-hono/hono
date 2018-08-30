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

package org.eclipse.hono.service.tenant;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.eclipse.hono.client.ClientErrorException;

import org.eclipse.hono.util.EventBusMessage;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.TenantConstants;
import org.eclipse.hono.util.TenantResult;

import java.net.HttpURLConnection;
import java.util.Objects;

/**
 * A base class for implementing {@link CompleteTenantService}.
 * <p>
 * In particular, this base class provides support for receiving service invocation request messages
 * via vert.x' event bus and route them to specific methods corresponding to the operation indicated
 * in the message.
 *
 * @param <T> The type of configuration properties this service requires.
 */
public abstract class CompleteBaseTenantService<T> extends BaseTenantService<T> implements CompleteTenantService {

    /**
     * Processes a Tenant API request message received via the vert.x event bus.
     * <p>
     * This method validates the request payload against the Tenant API specification
     * before invoking the corresponding {@code TenantService} methods.
     * 
     * @param request The request message.
     * @return A future indicating the outcome of the service invocation.
     * @throws NullPointerException If the request message is {@code null}.
     */
    @Override
    public final Future<EventBusMessage> processRequest(final EventBusMessage request) {

        Objects.requireNonNull(request);

        switch (TenantConstants.TenantAction.from(request.getOperation())) {
        case add:
            return processAddRequest(request);
        case get:
            return processGetRequest(request);
        case update:
            return processUpdateRequest(request);
        case remove:
            return processRemoveRequest(request);
        default:
            return processCustomTenantMessage(request);
        }
    }


    private Future<EventBusMessage> processAddRequest(final EventBusMessage request) {

        final String tenantId = request.getTenant();
        final JsonObject payload = getRequestPayload(request.getJsonPayload());

        if (tenantId == null) {
            log.debug("request does not contain mandatory property [{}]",
                    MessageHelper.APP_PROPERTY_TENANT_ID);
            return Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST));
        } else if (isValidRequestPayload(payload)) {
            log.debug("creating tenant [{}]", tenantId);
            final Future<TenantResult<JsonObject>> addResult = Future.future();
            addNotPresentFieldsWithDefaultValuesForTenant(payload);
            add(tenantId, payload, addResult.completer());
            return addResult.map(tr -> {
                return request.getResponse(tr.getStatus())
                        .setJsonPayload(tr.getPayload())
                        .setCacheDirective(tr.getCacheDirective());
            });
        } else {
            log.debug("request contains malformed payload");
            return Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST));
        }
    }

    private Future<EventBusMessage> processUpdateRequest(final EventBusMessage request) {

        final String tenantId = request.getTenant();
        final JsonObject payload = getRequestPayload(request.getJsonPayload());

        if (tenantId == null) {
            log.debug("request does not contain mandatory property [{}]",
                    MessageHelper.APP_PROPERTY_TENANT_ID);
            return Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST));
        } else if (isValidRequestPayload(payload)) {
            log.debug("updating tenant [{}]", tenantId);
            final Future<TenantResult<JsonObject>> updateResult = Future.future();
            addNotPresentFieldsWithDefaultValuesForTenant(payload);
            update(tenantId, payload, updateResult.completer());
            return updateResult.map(tr -> {
                return request.getResponse(tr.getStatus())
                        .setJsonPayload(tr.getPayload())
                        .setCacheDirective(tr.getCacheDirective());
            });
        } else {
            log.debug("request contains malformed payload");
            return Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST));
        }
    }

    private Future<EventBusMessage> processRemoveRequest(final EventBusMessage request) {

        final String tenantId = request.getTenant();

        if (tenantId == null) {
            log.debug("request does not contain mandatory property [{}]",
                    MessageHelper.APP_PROPERTY_TENANT_ID);
            return Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST));
        } else {
            log.debug("deleting tenant [{}]", tenantId);
            final Future<TenantResult<JsonObject>> removeResult = Future.future();
            remove(tenantId, removeResult.completer());
            return removeResult.map(tr -> {
                return request.getResponse(tr.getStatus())
                        .setJsonPayload(tr.getPayload())
                        .setCacheDirective(tr.getCacheDirective());
            });
        }
    }

    /**
     * Check the request payload for validity.
     *
     * @param payload The payload to check.
     * @return boolean The result of the check : {@link Boolean#TRUE} if the payload is valid, {@link Boolean#FALSE} otherwise.
     * @throws NullPointerException If the payload is {@code null}.
     */
    private boolean isValidRequestPayload(final JsonObject payload) {

        final Object adaptersObj = payload.getValue(TenantConstants.FIELD_ADAPTERS);
        if (adaptersObj == null) {
            // all adapters enabled with default config
            return true;
        } else if (adaptersObj instanceof JsonArray) {

            final JsonArray adapters = (JsonArray) adaptersObj;
            if (adapters.size() == 0) {
                // if given, adapters config array must not be empty
                return false;
            } else {
                return !adapters.stream()
                        .anyMatch(obj -> {
                            return !(obj instanceof JsonObject) ||
                                    !((JsonObject) obj).containsKey(TenantConstants.FIELD_ADAPTERS_TYPE);
                        });
            }
        } else {
            // malformed payload
            return false;
        }
    }

    /**
     * Add default values for optional fields that are not filled in the payload.
     * <p>
     * Payload should be checked for validity first, there is no error handling inside this method anymore.
     * </p>
     *
     * @param checkedPayload The checked payload to add optional fields to.
     * @throws ClassCastException If the {@link TenantConstants#FIELD_ADAPTERS_TYPE} element is not a {@link JsonArray}
     *       or the JsonArray contains elements that are not of type {@link JsonObject}.
     */
    protected final void addNotPresentFieldsWithDefaultValuesForTenant(final JsonObject checkedPayload) {
        if (!checkedPayload.containsKey(TenantConstants.FIELD_ENABLED)) {
            log.trace("adding 'enabled' key to payload");
            checkedPayload.put(TenantConstants.FIELD_ENABLED, Boolean.TRUE);
        }

        final JsonArray adapters = checkedPayload.getJsonArray(TenantConstants.FIELD_ADAPTERS);
        if (adapters != null) {
            adapters.forEach(elem -> addNotPresentFieldsWithDefaultValuesForAdapter((JsonObject) elem));
        }
    }

    private void addNotPresentFieldsWithDefaultValuesForAdapter(final JsonObject adapter) {
        if (!adapter.containsKey(TenantConstants.FIELD_ENABLED)) {
            log.trace("adding 'enabled' key to payload");
            adapter.put(TenantConstants.FIELD_ENABLED, Boolean.TRUE);
        }

        if (!adapter.containsKey(TenantConstants.FIELD_ADAPTERS_DEVICE_AUTHENTICATION_REQUIRED)) {
            log.trace("adding 'device-authentication-required' key to adapter payload");
            adapter.put(TenantConstants.FIELD_ADAPTERS_DEVICE_AUTHENTICATION_REQUIRED, Boolean.TRUE);
        }
    }

    /**
     * {@inheritDoc}
     *
     * This default implementation simply returns an empty result with status code 501 (Not Implemented).
     * Subclasses should override this method in order to provide a reasonable implementation.
     */
    @Override
    public void add(final String tenantId, final JsonObject tenantObj, final Handler<AsyncResult<TenantResult<JsonObject>>> resultHandler) {
        handleUnimplementedOperation(resultHandler);
    }

    /**
     * {@inheritDoc}
     *
     * This default implementation simply returns an empty result with status code 501 (Not Implemented).
     * Subclasses should override this method in order to provide a reasonable implementation.
     */
    @Override
    public void update(final String tenantId, final JsonObject tenantObj, final Handler<AsyncResult<TenantResult<JsonObject>>> resultHandler) {
        handleUnimplementedOperation(resultHandler);
    }

    /**
     * {@inheritDoc}
     *
     * This default implementation simply returns an empty result with status code 501 (Not Implemented).
     * Subclasses should override this method in order to provide a reasonable implementation.
     */
    @Override
    public void remove(final String tenantId, final Handler<AsyncResult<TenantResult<JsonObject>>> resultHandler) {
        handleUnimplementedOperation(resultHandler);
    }
}
