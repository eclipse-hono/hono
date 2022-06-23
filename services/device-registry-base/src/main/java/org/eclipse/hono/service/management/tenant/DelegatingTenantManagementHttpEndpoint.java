/*******************************************************************************
 * Copyright (c) 2020, 2021 Contributors to the Eclipse Foundation
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
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.config.ServiceConfigProperties;
import org.eclipse.hono.service.http.TracingHandler;
import org.eclipse.hono.service.management.AbstractDelegatingRegistryHttpEndpoint;
import org.eclipse.hono.service.management.Filter;
import org.eclipse.hono.service.management.Id;
import org.eclipse.hono.service.management.Sort;
import org.eclipse.hono.tracing.TracingHelper;
import org.eclipse.hono.util.RegistryManagementConstants;

import io.opentracing.Span;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.DecodeException;
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
 *
 * @param <S> The type of service this endpoint delegates to.
 */
public class DelegatingTenantManagementHttpEndpoint<S extends TenantManagementService> extends AbstractDelegatingRegistryHttpEndpoint<S, ServiceConfigProperties> {

    static final int DEFAULT_PAGE_OFFSET = 0;
    static final int DEFAULT_PAGE_SIZE = 30;
    static final int MAX_PAGE_SIZE = 200;
    static final int MIN_PAGE_OFFSET = 0;
    static final int MIN_PAGE_SIZE = 0;

    private static final String SPAN_NAME_GET_TENANT = "get Tenant from management API";
    private static final String SPAN_NAME_CREATE_TENANT = "create Tenant from management API";
    private static final String SPAN_NAME_UPDATE_TENANT = "update Tenant from management API";
    private static final String SPAN_NAME_REMOVE_TENANT = "remove Tenant from management API";
    private static final String SPAN_NAME_SEARCH_TENANT = "search Tenants from management API";

    private static final String TENANT_MANAGEMENT_ENDPOINT_NAME = String.format("%s/%s",
            RegistryManagementConstants.API_VERSION,
            RegistryManagementConstants.TENANT_HTTP_ENDPOINT);

    /**
     * Creates an endpoint for a service instance.
     *
     * @param vertx The vert.x instance to use.
     * @param service The service to delegate to.
     * @throws NullPointerException if any of the parameters are {@code null};
     */
    public DelegatingTenantManagementHttpEndpoint(final Vertx vertx, final S service) {
        super(vertx, service);
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
        router.route(path).handler(createCorsHandler(config.getCorsAllowedOrigin(), Set.of(HttpMethod.POST)));
        router.route(pathWithTenant).handler(createDefaultCorsHandler(config.getCorsAllowedOrigin()));

        final BodyHandler bodyHandler = BodyHandler.create(DEFAULT_UPLOADS_DIRECTORY);
        bodyHandler.setBodyLimit(config.getMaxPayloadSize());

        // ADD tenant with auto-generated ID
        router.post(path)
                .handler(bodyHandler)
                .handler(this::extractOptionalJsonPayload)
                .handler(this::createTenant);

        // ADD tenant
        router.post(pathWithTenant)
                .handler(bodyHandler)
                .handler(this::extractOptionalJsonPayload)
                .handler(this::createTenant);

        // GET tenant
        router.get(pathWithTenant)
                .handler(this::getTenant);

        // UPDATE tenant
        router.put(pathWithTenant)
                .handler(bodyHandler)
                .handler(this::extractRequiredJsonPayload)
                .handler(this::extractIfMatchVersionParam)
                .handler(this::updateTenant);

        // REMOVE tenant
        router.delete(pathWithTenant)
                .handler(this::extractIfMatchVersionParam)
                .handler(this::deleteTenant);

        // SEARCH tenants
        router.get(path)
                .handler(this::searchTenants);
    }

    private void getTenant(final RoutingContext ctx) {

        final Span span = TracingHelper.buildServerChildSpan(
                tracer,
                TracingHandler.serverSpanContext(ctx),
                SPAN_NAME_GET_TENANT,
                getClass().getSimpleName()
        ).start();

        getRequestParameter(ctx, PARAM_TENANT_ID, getPredicate(config.getTenantIdPattern(), false))
            .compose(tenantId -> {
                TracingHelper.TAG_TENANT_ID.set(span, tenantId);
                logger.debug("retrieving tenant [id: {}]", tenantId);
                return getService().readTenant(tenantId, span);
            })
            .onSuccess(operationResult -> writeResponse(ctx, operationResult, span))
            .onFailure(t -> failRequest(ctx, t, span))
            .onComplete(s -> span.finish());
    }

    private void createTenant(final RoutingContext ctx) {

        final Span span = TracingHelper.buildServerChildSpan(
                tracer,
                TracingHandler.serverSpanContext(ctx),
                SPAN_NAME_CREATE_TENANT,
                getClass().getSimpleName()
        ).start();

        final Future<String> tenantId = getRequestParameter(ctx, PARAM_TENANT_ID, getPredicate(config.getTenantIdPattern(), true));
        final Future<Tenant> payload = fromPayload(ctx);

        CompositeFuture.all(tenantId, payload)
            .compose(ok -> {
                final Optional<String> tid = Optional.ofNullable(tenantId.result());
                tid.ifPresent(s -> TracingHelper.TAG_TENANT_ID.set(span, s));

                logger.debug("creating tenant [{}]", tid.orElse("<auto>"));
                return getService().createTenant(tid, payload.result(), span);
            })
            .onSuccess(operationResult -> writeResponse(ctx, operationResult, (responseHeaders, status) -> {
                if (status == HttpURLConnection.HTTP_CREATED) {
                    Optional.ofNullable(operationResult.getPayload())
                        .map(Id::getId)
                        .ifPresent(id -> {
                            final String location = String.format("/%s/%s", getName(), id);
                            responseHeaders.set(HttpHeaders.LOCATION, location);
                        });
                }
            }, span))
            .onFailure(t -> failRequest(ctx, t, span))
            .onComplete(s -> span.finish());
    }

    private void updateTenant(final RoutingContext ctx) {

        final Span span = TracingHelper.buildServerChildSpan(
                tracer,
                TracingHandler.serverSpanContext(ctx),
                SPAN_NAME_UPDATE_TENANT,
                getClass().getSimpleName()
        ).start();

        final Future<String> tenantId = getRequestParameter(ctx, PARAM_TENANT_ID, getPredicate(config.getTenantIdPattern(), false));
        final Future<Tenant> payload = fromPayload(ctx);

        CompositeFuture.all(tenantId, payload)
            .compose(tenant -> {
                TracingHelper.TAG_TENANT_ID.set(span, tenantId.result());
                logger.debug("updating tenant [{}]", tenantId.result());
                return getService().updateTenant(tenantId.result(), payload.result(), Optional.ofNullable(ctx.get(KEY_RESOURCE_VERSION)), span);
            })
            .onSuccess(operationResult -> writeResponse(ctx, operationResult, span))
            .onFailure(t -> failRequest(ctx, t, span))
            .onComplete(s -> span.finish());
    }

    private void deleteTenant(final RoutingContext ctx) {

        final Span span = TracingHelper.buildServerChildSpan(
                tracer,
                TracingHandler.serverSpanContext(ctx),
                SPAN_NAME_REMOVE_TENANT,
                getClass().getSimpleName()
        ).start();

        getRequestParameter(ctx, PARAM_TENANT_ID, getPredicate(config.getTenantIdPattern(), false))
            .compose(tenantId -> {
                TracingHelper.TAG_TENANT_ID.set(span, tenantId);
                logger.debug("removing tenant [{}]", tenantId);
                final Optional<String> resourceVersion = Optional.ofNullable(ctx.get(KEY_RESOURCE_VERSION));
                return getService().deleteTenant(tenantId, resourceVersion, span);
            })
            .onSuccess(result -> writeResponse(ctx, result, span))
            .onFailure(t -> failRequest(ctx, t, span))
            .onComplete(s -> span.finish());
    }

    private void searchTenants(final RoutingContext ctx) {
        final Span span = TracingHelper.buildServerChildSpan(
                tracer,
                TracingHandler.serverSpanContext(ctx),
                SPAN_NAME_SEARCH_TENANT,
                getClass().getSimpleName()).start();

        final Future<Integer> pageSize = getRequestParameter(
                ctx,
                RegistryManagementConstants.PARAM_PAGE_SIZE,
                DEFAULT_PAGE_SIZE,
                CONVERTER_INT,
                value -> value >= MIN_PAGE_SIZE && value <= MAX_PAGE_SIZE);
        final Future<Integer> pageOffset = getRequestParameter(
                ctx,
                RegistryManagementConstants.PARAM_PAGE_OFFSET,
                DEFAULT_PAGE_OFFSET,
                CONVERTER_INT,
                value -> value >= MIN_PAGE_OFFSET);
        final Future<List<Filter>> filters = decodeJsonFromRequestParameter(ctx,
                RegistryManagementConstants.PARAM_FILTER_JSON, Filter.class);
        final Future<List<Sort>> sortOptions = decodeJsonFromRequestParameter(ctx,
                RegistryManagementConstants.PARAM_SORT_JSON, Sort.class);

        CompositeFuture.all(pageSize, pageOffset, filters, sortOptions)
                .compose(ok -> getService().searchTenants(
                        pageSize.result(),
                        pageOffset.result(),
                        filters.result(),
                        sortOptions.result(),
                        span))
                .onSuccess(operationResult -> writeResponse(ctx, operationResult, span))
                .onFailure(t -> failRequest(ctx, t, span))
                .onComplete(s -> span.finish());
    }

    /**
     * Gets the tenant from the request body.
     *
     * @param ctx The context to retrieve the request body from.
     * @return A future indicating the outcome of the operation.
     *         The future will be succeeded if the request body is either empty or contains a JSON
     *         object that complies with the Device Registry Management API's Tenant object definition.
     *         Otherwise, the future will be failed with a {@link org.eclipse.hono.client.ClientErrorException}
     *         containing a corresponding status code.
     * @throws NullPointerException If the context is {@code null}.
     */
    private static Future<Tenant> fromPayload(final RoutingContext ctx) {

        Objects.requireNonNull(ctx);

        final Promise<Tenant> result = Promise.promise();
        Optional.ofNullable(ctx.get(KEY_REQUEST_BODY))
            .map(JsonObject.class::cast)
            .ifPresentOrElse(
                    // validate payload
                    json -> {
                        try {
                            final Tenant tenant = json.mapTo(Tenant.class);
                            if (tenant.isValid()) {
                                result.complete(tenant);
                            } else {
                                result.fail(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST,
                                        "request does not contain a valid Tenant object"));
                            }
                        } catch (final DecodeException | IllegalArgumentException e) {
                            result.fail(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST,
                                    "request does not contain a valid Tenant object", e));
                        }
                    },
                    // payload was empty
                    () -> result.complete(new Tenant()));
        return result.future();
    }
}
