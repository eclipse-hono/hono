/**
 * Copyright (c) 2018 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 1.0 which is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * SPDX-License-Identifier: EPL-1.0
 */

package org.eclipse.hono.service.tenant;

import java.net.HttpURLConnection;
import java.util.Objects;

import org.eclipse.hono.service.EventBusService;
import org.eclipse.hono.util.EventBusMessage;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.TenantConstants;
import org.eclipse.hono.util.TenantResult;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

/**
 * Base class for implementing {@link TenantService}s.
 * <p>
 * In particular, this base class provides support for receiving service invocation request messages
 * via vert.x' event bus and route them to specific methods corresponding to the operation indicated
 * in the message.
 *
 * @param <T> The type of configuration properties this service requires.
 */
public abstract class BaseTenantService<T> extends EventBusService<T> implements TenantService {

    @Override
    protected final String getEventBusAddress() {
        return TenantConstants.EVENT_BUS_ADDRESS_TENANT_IN;
    }

    /**
     * Processes a tenant request message received via the vert.x event bus.
     * <p>
     * This method validates the request payload against the Tenant API specification
     * before invoking the corresponding {@code TenantService} methods.
     * 
     * @param requestMessage The request message.
     * @return A future indicating the outcome of the service invocation.
     * @throws NullPointerException If the request message is {@code null}.
     */
    @Override
    public final Future<EventBusMessage> processRequest(final EventBusMessage requestMessage) {

        Objects.requireNonNull(requestMessage);

        return processTenantRequest(requestMessage).map(result -> {
            return EventBusMessage.forStatusCode(result.getStatus())
                    .setTenant(requestMessage.getTenant())
                    .setJsonPayload(result.getPayload())
                    .setCacheDirective(result.getCacheDirective());
        }).recover(t -> {
            return Future.failedFuture(t);
        });
    }

    private Future<TenantResult<JsonObject>> processTenantRequest(final EventBusMessage request) {

        switch (TenantConstants.TenantAction.from(request.getOperation())) {
        case get:
            return processGetRequest(request);
        case add:
            return processAddRequest(request);
        case update:
            return processUpdateRequest(request);
        case remove:
            return processRemoveRequest(request);
        default:
            return processCustomTenantMessage(request);
        }
    }

    private Future<TenantResult<JsonObject>> processGetRequest(final EventBusMessage request) {

        final Future<TenantResult<JsonObject>> result = Future.future();
        final String tenantId = request.getTenant();
        if (tenantId == null) {
            log.debug("request does not contain mandatory property [{}]",
                    MessageHelper.APP_PROPERTY_TENANT_ID);
            result.complete(TenantResult.from(HttpURLConnection.HTTP_BAD_REQUEST));
        } else {
            log.debug("retrieving tenant [{}]", tenantId);
            get(tenantId, result.completer());
        }
        return result;
    }

    private Future<TenantResult<JsonObject>> processAddRequest(final EventBusMessage request) {

        final Future<TenantResult<JsonObject>> result = Future.future();
        final String tenantId = request.getTenant();
        final JsonObject payload = request.getJsonPayload(new JsonObject());

        if (tenantId == null) {
            log.debug("request does not contain mandatory property [{}]",
                    MessageHelper.APP_PROPERTY_TENANT_ID);
            result.complete(TenantResult.from(HttpURLConnection.HTTP_BAD_REQUEST));
        } else if (isValidRequestPayload(payload)) {
            log.debug("creating tenant [{}]", tenantId);
            addNotPresentFieldsWithDefaultValuesForTenant(payload);
            add(tenantId, payload, result.completer());
        } else {
            log.debug("request contains malformed payload");
            result.complete(TenantResult.from(HttpURLConnection.HTTP_BAD_REQUEST));
        }
        return result;
    }

    private Future<TenantResult<JsonObject>> processUpdateRequest(final EventBusMessage request) {

        final Future<TenantResult<JsonObject>> result = Future.future();
        final String tenantId = request.getTenant();
        final JsonObject payload = request.getJsonPayload(new JsonObject());

        if (tenantId == null) {
            log.debug("request does not contain mandatory property [{}]",
                    MessageHelper.APP_PROPERTY_TENANT_ID);
            result.complete(TenantResult.from(HttpURLConnection.HTTP_BAD_REQUEST));
        } else if (isValidRequestPayload(payload)) {
            log.debug("updating tenant [{}]", tenantId);
            addNotPresentFieldsWithDefaultValuesForTenant(payload);
            update(tenantId, payload, result.completer());
        } else {
            log.debug("request contains malformed payload");
            result.complete(TenantResult.from(HttpURLConnection.HTTP_BAD_REQUEST));
        }
        return result;
    }

    private Future<TenantResult<JsonObject>> processRemoveRequest(final EventBusMessage request) {

        final Future<TenantResult<JsonObject>> result = Future.future();
        final String tenantId = request.getTenant();

        if (tenantId == null) {
            log.debug("request does not contain mandatory property [{}]",
                    MessageHelper.APP_PROPERTY_TENANT_ID);
            result.complete(TenantResult.from(HttpURLConnection.HTTP_BAD_REQUEST));
        } else {
            log.debug("deleting tenant [{}]", tenantId);
            remove(tenantId, result.completer());
        }
        return result;
    }

    /**
     * Processes a request for a non-standard operation.
     * <p>
     * Subclasses should override this method in order to support additional, custom
     * operations that are not defined by Hono's Tenant API.
     * <p>
     * This default implementation simply returns a succeeded future containing a
     * result with status code <em>400 Bad Request</em>.
     *
     * @param request The request to process.
     * @return A future indicating the outcome of the service invocation.
     */
    protected Future<TenantResult<JsonObject>> processCustomTenantMessage(final EventBusMessage request) {
        log.debug("invalid operation in request message [{}]", request.getOperation());
        return Future.succeededFuture(TenantResult.from(HttpURLConnection.HTTP_BAD_REQUEST));
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
     * Wraps a given tenant ID, its properties data and its adapter configuration data into a JSON structure suitable
     * to be returned to clients as the result of a tenant API operation.
     *
     * @param tenantId The tenant ID.
     * @param data The tenant properties data.
     * @param adapterConfigurations The adapter configurations data for the tenant as JsonArray.
     * @return The JSON structure.
     */
    protected static final JsonObject getResultPayload(final String tenantId, final JsonObject data,
                                                       final JsonArray adapterConfigurations) {
        final JsonObject result = new JsonObject()
                .put(TenantConstants.FIELD_PAYLOAD_TENANT_ID, tenantId)
                .mergeIn(data);
        if (adapterConfigurations != null) {
            result.put(TenantConstants.FIELD_ADAPTERS, adapterConfigurations);
        }
        return result;
    }

    /**
     * {@inheritDoc}
     *
     * This default implementation simply returns an empty result with status code 501 (Not Implemented).
     * Subclasses should override this method in order to provide a reasonable implementation.
     */
    @Override
    public void get(final String tenantId, final Handler<AsyncResult<TenantResult<JsonObject>>> resultHandler) {
        handleUnimplementedOperation(resultHandler);
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

    /**
     * Handles an unimplemented operation by succeeding the given handler
     * with a result having a <em>501 Not Implemented</em> status code.
     * 
     * @param resultHandler The handler.
     */
    protected void handleUnimplementedOperation(final Handler<AsyncResult<TenantResult<JsonObject>>> resultHandler) {
        resultHandler.handle(Future.succeededFuture(TenantResult.from(HttpURLConnection.HTTP_NOT_IMPLEMENTED)));
    }
}
