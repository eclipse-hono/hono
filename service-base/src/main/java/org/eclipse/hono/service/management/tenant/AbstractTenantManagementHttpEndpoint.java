/*******************************************************************************
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;

import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.config.ServiceConfigProperties;
import org.eclipse.hono.service.http.AbstractHttpEndpoint;
import org.eclipse.hono.service.http.HttpUtils;
import org.eclipse.hono.service.http.TracingHandler;
import org.eclipse.hono.service.management.Id;
import org.eclipse.hono.service.management.OperationResult;
import org.eclipse.hono.service.management.Result;
import org.eclipse.hono.tracing.TracingHelper;
import org.eclipse.hono.util.RegistryManagementConstants;
import org.springframework.beans.factory.annotation.Autowired;

import io.opentracing.Span;
import io.opentracing.tag.Tags;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;

/**
 * An {@code HttpEndpoint} for managing tenant information.
 * <p>
 * This endpoint implements the <em>tenant</em> resources of Hono's
 * <a href="https://www.eclipse.org/hono/docs/api/management/">Device Registry Management API</a>.
 * It receives HTTP requests representing operation invocations and executes the matching service implementation methods.
 * The outcome is then returned to the peer in the HTTP response.
 */
public abstract class AbstractTenantManagementHttpEndpoint extends AbstractHttpEndpoint<ServiceConfigProperties> {

    private static final String SPAN_NAME_GET_TENANT = "get Tenant from management API";
    private static final String SPAN_NAME_CREATE_TENANT = "create Tenant from management API";
    private static final String SPAN_NAME_UPDATE_TENANT = "update Tenant from management API";
    private static final String SPAN_NAME_REMOVE_TENANT = "remove Tenant from management API";

    private static final String TENANT_MANAGEMENT_ENDPOINT_NAME = String.format("%s/%s",
            RegistryManagementConstants.API_VERSION,
            RegistryManagementConstants.TENANT_HTTP_ENDPOINT);

    /**
     * Creates an endpoint for a Vertx instance.
     *
     * @param vertx The Vertx instance to use.
     * @throws NullPointerException if vertx is {@code null};
     */
    @Autowired
    public AbstractTenantManagementHttpEndpoint(final Vertx vertx) {
        super(Objects.requireNonNull(vertx));
    }

    @Override
    public String getName() {
        return TENANT_MANAGEMENT_ENDPOINT_NAME;
    }

    @Override
    public void addRoutes(final Router router) {

        final String path = String.format("/%s", getName());
        final String pathWithTenant = String.format("/%s/:%s", getName(), PARAM_TENANT_ID);

        // Add CORS handler
        router.route(path).handler(createCorsHandler(config.getCorsAllowedOrigin(), EnumSet.of(HttpMethod.POST)));
        router.route(pathWithTenant).handler(createDefaultCorsHandler(config.getCorsAllowedOrigin()));

        final BodyHandler bodyHandler = BodyHandler.create();
        bodyHandler.setBodyLimit(config.getMaxPayloadSize());

        // ADD tenant with auto-generated ID
        router.post(path).handler(bodyHandler);
        router.post(path).handler(this::extractOptionalJsonPayload);
        router.post(path).handler(this::createTenant);

        // ADD tenant
        router.post(pathWithTenant).handler(bodyHandler);
        router.post(pathWithTenant).handler(this::extractOptionalJsonPayload);
        router.post(pathWithTenant).handler(this::updatePayloadWithTenantId);
        router.post(pathWithTenant).handler(this::createTenant);

        // GET tenant
        router.get(pathWithTenant).handler(this::getTenant);

        // UPDATE tenant
        router.put(pathWithTenant).handler(bodyHandler);
        router.put(pathWithTenant).handler(this::extractRequiredJsonPayload);
        router.put(pathWithTenant).handler(this::extractIfMatchVersionParam);
        router.put(pathWithTenant).handler(this::updateTenant);

        // REMOVE tenant
        router.delete(pathWithTenant).handler(this::extractIfMatchVersionParam);
        router.delete(pathWithTenant).handler(this::deleteTenant);
    }

    /**
     * The service to forward requests to.
     *
     * @return The service to bind to, must never return {@code null}.
     */
    protected abstract TenantManagementService getService();

    /**
     * Check that the tenantId value is not blank then
     * update the payload (that was put to the RoutingContext ctx with the
     * key {@link #KEY_REQUEST_BODY}) with the tenant value retrieved from the RoutingContext.
     * The tenantId value is associated with the key {@link RegistryManagementConstants#FIELD_PAYLOAD_TENANT_ID}.
     *
     * @param ctx The routing context to retrieve the JSON request body from.
     */
    protected final void updatePayloadWithTenantId(final RoutingContext ctx) {

        final JsonObject payload = ctx.get(KEY_REQUEST_BODY);
        final String tenantId = getTenantIdFromContext(ctx);

        if (tenantId.isBlank()) {
            ctx.fail(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST,
                    String.format("'%s' param cannot be empty", RegistryManagementConstants.FIELD_PAYLOAD_TENANT_ID)));
        }

        payload.put(RegistryManagementConstants.FIELD_PAYLOAD_TENANT_ID, tenantId);
        ctx.put(KEY_REQUEST_BODY, payload);
        ctx.next();
    }

    private String getTenantIdFromContext(final RoutingContext ctx) {
        final JsonObject payload = ctx.get(KEY_REQUEST_BODY);
        return Optional.ofNullable(getTenantParam(ctx)).orElse(getTenantParamFromPayload(payload));
    }

    private void createTenant(final RoutingContext ctx) {

        final Span span = newChildSpan(SPAN_NAME_CREATE_TENANT, TracingHandler.serverSpanContext(ctx), tracer, getClass().getSimpleName());

        final String tenantId = getRequestParam(PARAM_TENANT_ID, ctx, span, true);

        final JsonObject payload = getRequestPayload(ctx.get(KEY_REQUEST_BODY));

        if (isValidRequestPayload(payload)) {
            logger.debug("creating tenant [{}]", Optional.ofNullable(tenantId).orElse("<auto>"));

            addNotPresentFieldsWithDefaultValuesForTenant(payload);

            final Promise<OperationResult<Id>> result = Promise.promise();
            result.future().setHandler(handler -> {
                final OperationResult<Id> operationResult = handler.result();

                final String createdTenantId = Optional.ofNullable(operationResult.getPayload()).map(Id::getId).orElse(null);
                writeOperationResponse(
                        ctx,
                        operationResult,
                        (response) -> response.putHeader(
                                HttpHeaders.LOCATION,
                                String.format("/%s/%s", getName(), createdTenantId)),
                        span);
            });

            getService().createTenant(Optional.ofNullable(tenantId), payload.mapTo(Tenant.class), span, result);
        } else {
            final String msg = "request contains malformed payload";
            logger.debug(msg);
            TracingHelper.logError(span, msg);
            Tags.HTTP_STATUS.set(span, HttpURLConnection.HTTP_BAD_REQUEST);
            HttpUtils.badRequest(ctx, msg);
            span.finish();
        }
    }

    private void getTenant(final RoutingContext ctx) {

        final Span span = newChildSpan(SPAN_NAME_GET_TENANT, TracingHandler.serverSpanContext(ctx), tracer, getClass().getSimpleName());
        final String tenantId = getMandatoryRequestParam(PARAM_TENANT_ID, ctx, span);

        final HttpServerResponse response = ctx.response();

        logger.debug("retrieving tenant [id: {}]", tenantId);
        final Promise<OperationResult<Tenant>> result = Promise.promise();
        result.future().setHandler(handler -> {
            final OperationResult<Tenant> operationResult = handler.result();
            final int status = operationResult.getStatus();
            response.setStatusCode(status);
            switch (status) {
                case HttpURLConnection.HTTP_OK:
                    operationResult.getResourceVersion().ifPresent(v -> response.putHeader(HttpHeaders.ETAG, v));
                    HttpUtils.setResponseBody(response, JsonObject.mapFrom(operationResult.getPayload()));
                    // falls through intentionally
                default:
                    Tags.HTTP_STATUS.set(span, status);
                    span.finish();
                    response.end();
            }
        });
        getService().readTenant(tenantId, span, result);
    }

    private void updateTenant(final RoutingContext ctx) {

        final Span span = newChildSpan(SPAN_NAME_UPDATE_TENANT, TracingHandler.serverSpanContext(ctx), tracer, getClass().getSimpleName());

        final String tenantId = getMandatoryRequestParam(PARAM_TENANT_ID, ctx, span);
        final JsonObject payload = getRequestPayload(ctx.get(KEY_REQUEST_BODY));
        if (payload != null) {
            payload.remove(RegistryManagementConstants.FIELD_PAYLOAD_TENANT_ID);
        }

        if (isValidRequestPayload(payload)) {
            logger.debug("updating tenant [{}]", tenantId);

            addNotPresentFieldsWithDefaultValuesForTenant(payload);

            final Optional<String> resourceVersion = Optional.ofNullable(ctx.get(KEY_RESOURCE_VERSION));

            final Promise<OperationResult<Void>> result = Promise.promise();

            result.future().setHandler(handler -> {
                writeOperationResponse(ctx, handler.result(), null, span);
            });

            getService().updateTenant(tenantId, payload.mapTo(Tenant.class), resourceVersion, span, result);
        } else {
            final String msg = "request contains malformed payload";
            logger.debug(msg);
            TracingHelper.logError(span, msg);
            Tags.HTTP_STATUS.set(span, HttpURLConnection.HTTP_BAD_REQUEST);
            HttpUtils.badRequest(ctx, msg);
            span.finish();
        }
    }

    private void deleteTenant(final RoutingContext ctx) {

        final Span span = newChildSpan(SPAN_NAME_REMOVE_TENANT, TracingHandler.serverSpanContext(ctx), tracer, getClass().getSimpleName());

        final String tenantId = getMandatoryRequestParam(PARAM_TENANT_ID, ctx, span);

        logger.debug("removing tenant [{}]", tenantId);

        final Optional<String> resourceVersion = Optional.ofNullable(ctx.get(KEY_RESOURCE_VERSION));

        final Promise<Result<Void>> result = Promise.promise();

        result.future().setHandler(handler -> {
                    writeResponse(ctx, handler.result(), null, span);
                });

        getService().deleteTenant(tenantId, resourceVersion, span, result);
    }

    private static String getTenantParamFromPayload(final JsonObject payload) {
        return (payload != null ? (String) payload.remove(RegistryManagementConstants.FIELD_PAYLOAD_TENANT_ID) : null);
    }

    /**
     * Checks the request payload for validity.
     *
     * @param payload The payload to check.
     * @return boolean The result of the check : {@link Boolean#TRUE} if the payload is valid, {@link Boolean#FALSE} otherwise.
     * @throws NullPointerException If the payload is {@code null}.
     */
    protected final boolean isValidRequestPayload(final JsonObject payload) {

        Objects.requireNonNull(payload);

        try {
            return payload.mapTo(Tenant.class).isValid();
        } catch (final IllegalArgumentException e) {
            logger.debug("Error parsing payload of tenant request", e);
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
        final JsonArray adapters = checkedPayload.getJsonArray(RegistryManagementConstants.FIELD_ADAPTERS);
        if (adapters != null) {
            adapters.forEach(elem -> addNotPresentFieldsWithDefaultValuesForAdapter((JsonObject) elem));
        }
    }

    private void addNotPresentFieldsWithDefaultValuesForAdapter(final JsonObject adapter) {
        if (!adapter.containsKey(RegistryManagementConstants.FIELD_ENABLED)) {
            logger.trace("adding 'enabled' key to payload");
            adapter.put(RegistryManagementConstants.FIELD_ENABLED, Boolean.TRUE);
        }

        if (!adapter.containsKey(RegistryManagementConstants.FIELD_ADAPTERS_DEVICE_AUTHENTICATION_REQUIRED)) {
            logger.trace("adding 'device-authentication-required' key to adapter payload");
            adapter.put(RegistryManagementConstants.FIELD_ADAPTERS_DEVICE_AUTHENTICATION_REQUIRED, Boolean.TRUE);
        }
    }
}
