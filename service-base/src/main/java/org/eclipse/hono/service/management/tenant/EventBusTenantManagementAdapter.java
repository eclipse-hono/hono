/*******************************************************************************
* Copyright (c) 2019, 2020 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.service.management.tenant;

import java.net.HttpURLConnection;
import java.util.Objects;
import java.util.Optional;

import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.service.EventBusService;
import org.eclipse.hono.service.management.Id;
import org.eclipse.hono.service.management.OperationResult;
import org.eclipse.hono.service.management.Result;
import org.eclipse.hono.service.management.Util;
import org.eclipse.hono.tracing.TracingHelper;
import org.eclipse.hono.util.EventBusMessage;
import org.eclipse.hono.util.RegistryManagementConstants;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

/**
 * A base class for implementing {@link TenantManagementService}.
 * <p>
 * In particular, this base class provides support for receiving service invocation request messages
 * via vert.x' event bus and route them to specific methods corresponding to the operation indicated
 * in the message.
 * @deprecated This class will be removed in future versions as HTTP endpoint does not use event bus anymore.
 *             Please use {@link org.eclipse.hono.service.management.tenant.AbstractTenantManagementHttpEndpoint} based implementation in the future.
 */
@Deprecated(forRemoval = true)
public abstract class EventBusTenantManagementAdapter extends EventBusService {

    private static final String SPAN_NAME_GET_TENANT = "get Tenant from management API";
    private static final String SPAN_NAME_CREATE_TENANT = "create Tenant from management API";
    private static final String SPAN_NAME_UPDATE_TENANT = "update Tenant from management API";
    private static final String SPAN_NAME_REMOVE_TENANT= "remove Tenant from management API";

    /**
     * The service to forward requests to.
     * 
     * @return The service to bind to, must never return {@code null}.
     */
    protected abstract TenantManagementService getService();

    @Override
    protected final String getEventBusAddress() {
        return RegistryManagementConstants.EVENT_BUS_ADDRESS_TENANT_MANAGEMENT_IN;
    }

    /**
     * Processes a Tenant API request message received via the vert.x event bus.
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

        switch (requestMessage.getOperation()) {
            case RegistryManagementConstants.ACTION_CREATE:
                return processCreateRequest(requestMessage);
            case RegistryManagementConstants.ACTION_GET:
                return processGetRequest(requestMessage);
            case RegistryManagementConstants.ACTION_UPDATE:
                return processUpdateRequest(requestMessage);
            case RegistryManagementConstants.ACTION_DELETE:
                return processDeleteRequest(requestMessage);
            default:
                return processCustomTenantMessage(requestMessage);
        }

    }


    private Future<EventBusMessage> processCreateRequest(final EventBusMessage request) {

        final Optional<String> tenantId = Optional.ofNullable(request.getTenant());
        final JsonObject payload = getRequestPayload(request.getJsonPayload());
        final SpanContext spanContext = request.getSpanContext();

        final Span span = Util.newChildSpan(SPAN_NAME_CREATE_TENANT, spanContext, tracer, tenantId.orElse("<auto>"), getClass().getSimpleName());

        final Future<EventBusMessage> resultFuture;
        if (isValidRequestPayload(payload)) {
            log.debug("creating tenant [{}]", tenantId.orElse("<auto>"));

            addNotPresentFieldsWithDefaultValuesForTenant(payload);

            final Promise<OperationResult<Id>> addResult = Promise.promise();
            getService().add(tenantId, payload, span, addResult);
            resultFuture = addResult.future().map(res -> {
                final String createdTenantId = Optional.ofNullable(res.getPayload()).map(Id::getId).orElse(null);
                return res.createResponse(request, JsonObject::mapFrom).setTenant(createdTenantId);
            });
        } else {
            log.debug("request contains malformed payload");
            TracingHelper.logError(span, "request contains malformed payload");
            resultFuture = Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST));
        }
        return finishSpanOnFutureCompletion(span, resultFuture);
    }

    private Future<EventBusMessage> processUpdateRequest(final EventBusMessage request) {

        final String tenantId = request.getTenant();
        final JsonObject payload = getRequestPayload(request.getJsonPayload());
        final Optional<String> resourceVersion = Optional.ofNullable(request.getResourceVersion());
        final SpanContext spanContext = request.getSpanContext();

        final Span span = Util.newChildSpan(SPAN_NAME_UPDATE_TENANT, spanContext, tracer, tenantId, getClass().getSimpleName());

        final Future<EventBusMessage> resultFuture;
        if (tenantId == null) {
            log.debug("missing tenant ID");
            TracingHelper.logError(span, "missing tenant ID");
            resultFuture = Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST));
        } else if (isValidRequestPayload(payload)) {
            log.debug("updating tenant [{}]", tenantId);

            addNotPresentFieldsWithDefaultValuesForTenant(payload);

            final Promise<OperationResult<Void>> updateResult = Promise.promise();
            getService().update(tenantId, payload, resourceVersion, span, updateResult);
            resultFuture = updateResult.future()
                    .map(res -> res.createResponse(request, JsonObject::mapFrom).setTenant(tenantId));
        } else {
            log.debug("request contains malformed payload");
            TracingHelper.logError(span, "request contains malformed payload");
            resultFuture = Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST));
        }
        return finishSpanOnFutureCompletion(span, resultFuture);
    }

    private Future<EventBusMessage> processDeleteRequest(final EventBusMessage request) {

        final String tenantId = request.getTenant();
        final Optional<String> resourceVersion = Optional.ofNullable(request.getResourceVersion());
        final SpanContext spanContext = request.getSpanContext();

        final Span span = Util.newChildSpan(SPAN_NAME_REMOVE_TENANT, spanContext, tracer, tenantId, getClass().getSimpleName());

        final Future<EventBusMessage> resultFuture;
        if (tenantId == null) {
            log.debug("missing tenant ID");
            TracingHelper.logError(span, "missing tenant ID");
            resultFuture = Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST));
        } else {
            log.debug("deleting tenant [{}]", tenantId);

            final Promise<Result<Void>> removeResult = Promise.promise();
            getService().remove(tenantId, resourceVersion, span, removeResult);
            resultFuture = removeResult.future()
                    .map(res -> res.createResponse(request, JsonObject::mapFrom).setTenant(tenantId));
        }
        return finishSpanOnFutureCompletion(span, resultFuture);
    }

    private Future<EventBusMessage> processGetRequest(final EventBusMessage request) {

        final String tenantId = request.getTenant();
        final SpanContext spanContext = request.getSpanContext();

        final Span span = Util.newChildSpan(SPAN_NAME_GET_TENANT, spanContext, tracer, tenantId, getClass().getSimpleName());

        final Future<EventBusMessage> resultFuture;
        if (tenantId == null ) {
            log.debug("missing tenant ID");
            TracingHelper.logError(span, "missing tenant ID");
            resultFuture = Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST));
        } else {
            log.debug("retrieving tenant [id: {}]", tenantId);
            final Promise<OperationResult<Tenant>> readResult = Promise.promise();
            getService().read(tenantId, span, readResult);
            resultFuture = readResult.future()
                    .map(res -> res.createResponse(request, JsonObject::mapFrom).setTenant(tenantId));
        }
        return finishSpanOnFutureCompletion(span, resultFuture);
    }

    /**
     * Checks the request payload for validity.
     *
     * @param payload The payload to check.
     * @return boolean The result of the check : {@link Boolean#TRUE} if the payload is valid, {@link Boolean#FALSE} otherwise.
     * @throws NullPointerException If the payload is {@code null}.
     */
    boolean isValidRequestPayload(final JsonObject payload) {

        Objects.requireNonNull(payload);

        try {
            return payload.mapTo(Tenant.class).isValid();
        } catch (final IllegalArgumentException e) {
            log.debug("Error parsing payload of tenant request", e);
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
     * @throws ClassCastException If the {@link RegistryManagementConstants#FIELD_ADAPTERS_TYPE} element is not a {@link JsonArray}
     *       or the JsonArray contains elements that are not of type {@link JsonObject}.
     */
    protected final void addNotPresentFieldsWithDefaultValuesForTenant(final JsonObject checkedPayload) {
        if (!checkedPayload.containsKey(RegistryManagementConstants.FIELD_ENABLED)) {
            log.trace("adding 'enabled' key to payload");
            checkedPayload.put(RegistryManagementConstants.FIELD_ENABLED, Boolean.TRUE);
        }

        final JsonArray adapters = checkedPayload.getJsonArray(RegistryManagementConstants.FIELD_ADAPTERS);
        if (adapters != null) {
            adapters.forEach(elem -> addNotPresentFieldsWithDefaultValuesForAdapter((JsonObject) elem));
        }
    }

    private void addNotPresentFieldsWithDefaultValuesForAdapter(final JsonObject adapter) {
        if (!adapter.containsKey(RegistryManagementConstants.FIELD_ENABLED)) {
            log.trace("adding 'enabled' key to payload");
            adapter.put(RegistryManagementConstants.FIELD_ENABLED, Boolean.TRUE);
        }

        if (!adapter.containsKey(RegistryManagementConstants.FIELD_ADAPTERS_DEVICE_AUTHENTICATION_REQUIRED)) {
            log.trace("adding 'device-authentication-required' key to adapter payload");
            adapter.put(RegistryManagementConstants.FIELD_ADAPTERS_DEVICE_AUTHENTICATION_REQUIRED, Boolean.TRUE);
        }
    }

    /**
     * Processes a request for a non-standard operation.
     * <p>
     * Subclasses should override this method in order to support additional, custom
     * operations that are not defined by Hono's Tenant API.
     * <p>
     * This default implementation simply returns a future that is failed with a
     * {@link ClientErrorException} with an error code <em>400 Bad Request</em>.
     *
     * @param request The request to process.
     * @return A future indicating the outcome of the service invocation.
     */
    protected Future<EventBusMessage> processCustomTenantMessage(final EventBusMessage request) {
        log.debug("invalid operation in request message [{}]", request.getOperation());
        return Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST));
    }
}
