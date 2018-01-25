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

import static org.eclipse.hono.util.TenantConstants.FIELD_TENANT_ID;
import static org.eclipse.hono.util.TenantConstants.Action;

import java.net.HttpURLConnection;
import java.util.Objects;
import java.util.function.BiConsumer;

import org.eclipse.hono.config.ServiceConfigProperties;
import org.eclipse.hono.service.http.AbstractHttpEndpoint;
import org.eclipse.hono.service.http.HttpUtils;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.TenantConstants;
import org.springframework.beans.factory.annotation.Autowired;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

/**
 * An {@code HttpEndpoint} for managing tenant information.
 * <p>
 * This endpoint implements Hono's <a href="https://www.eclipse.org/hono/api/tenant-api/">Tenant API</a>.
 * It receives HTTP requests representing operation invocations and sends them to an address on the vertx
 * event bus for processing. The outcome is then returned to the peer in the HTTP response.
 */
public final class TenantHttpEndpoint extends AbstractHttpEndpoint<ServiceConfigProperties> {

    // path parameters for capturing parts of the URI path
    private static final String PARAM_TENANT_ID = "tenant_id";

    /**
     * Creates an endpoint for a Vertx instance.
     *
     * @param vertx The Vertx instance to use.
     * @throws NullPointerException if vertx is {@code null};
     */
    @Autowired
    public TenantHttpEndpoint(final Vertx vertx) {
        super(Objects.requireNonNull(vertx));
    }

    @Override
    public String getName() {
        return TenantConstants.TENANT_ENDPOINT;
    }

    @Override
    public void addRoutes(final Router router) {

        final String path = String.format("/%s", TenantConstants.TENANT_ENDPOINT);
        // ADD tenant
        router.route(HttpMethod.POST, path).consumes(HttpUtils.CONTENT_TYPE_JSON)
                .handler(this::doAddTenantJson);
        router.route(HttpMethod.POST, path)
                .handler(ctx -> HttpUtils.badRequest(ctx, "missing or unsupported content-type"));

        final String pathWithTenant = String.format("/%s/:%s", TenantConstants.TENANT_ENDPOINT, PARAM_TENANT_ID);
        // GET tenant
        router.route(HttpMethod.GET, pathWithTenant).handler(this::doGetTenant);

        // UPDATE tenant
        router.route(HttpMethod.PUT, pathWithTenant).consumes(HttpUtils.CONTENT_TYPE_JSON)
                .handler(this::doUpdateTenantJson);
        router.route(HttpMethod.PUT, pathWithTenant)
                .handler(ctx -> HttpUtils.badRequest(ctx, "missing or unsupported content-type"));

        // REMOVE tenant
        router.route(HttpMethod.DELETE, pathWithTenant).handler(this::doRemoveTenant);
    }

    private static String getTenantParam(final RoutingContext ctx) {
        return ctx.request().getParam(PARAM_TENANT_ID);
    }

    private static void setResponseBody(final JsonObject tenantResult, final HttpServerResponse response) {
        final JsonObject msg = tenantResult.getJsonObject(TenantConstants.FIELD_PAYLOAD);
        if (msg != null) {
            final String body = msg.encodePrettily();
            response.putHeader(HttpHeaders.CONTENT_TYPE, HttpUtils.CONTENT_TYPE_JSON_UFT8)
                .putHeader(HttpHeaders.CONTENT_LENGTH, String.valueOf(body.length()))
                .write(body);
        }
    }

    private void doGetTenant(final RoutingContext ctx) {

        final String tenantId = getTenantParam(ctx);
        final HttpServerResponse response = ctx.response();
        final JsonObject requestMsg = TenantConstants.getServiceRequestAsJson(Action.ACTION_GET.toString(), tenantId);

        doTenantAction(ctx, requestMsg, (status, tenantResult) -> {
            response.setStatusCode(status);
            switch (status) {
                case HttpURLConnection.HTTP_OK:
                    final String msg = tenantResult.getJsonObject(TenantConstants.FIELD_PAYLOAD).encodePrettily();
                    response
                            .putHeader(HttpHeaders.CONTENT_TYPE, HttpUtils.CONTENT_TYPE_JSON_UFT8)
                            .putHeader(HttpHeaders.CONTENT_LENGTH, String.valueOf(msg.length()))
                            .write(msg);
                default:
                    response.end();
                }
        });
    }

    private void doAddTenantJson(final RoutingContext ctx) {
        try {
            JsonObject payload = null;
            if (ctx.getBody().length() > 0) {
                payload = ctx.getBodyAsJson();
            }
            addTenant(ctx, payload);
        } catch (final DecodeException e) {
            HttpUtils.badRequest(ctx, "body does not contain a valid JSON object");
        }
    }

    private void addTenant(final RoutingContext ctx, final JsonObject payload) {

        if (payload == null) {
            HttpUtils.badRequest(ctx, "missing body");
        } else {
            final Object tenantId = payload.remove(FIELD_TENANT_ID);
            if (tenantId == null) {
                HttpUtils.badRequest(ctx, String.format("'%s' param is required", FIELD_TENANT_ID));
            } else if (!(tenantId instanceof String)) {
                HttpUtils.badRequest(ctx, String.format("'%s' must be a string", FIELD_TENANT_ID));
            } else {
                logger.debug("adding tenant [tenant: {}, payload: {}]", tenantId, payload);
                final HttpServerResponse response = ctx.response();
                final JsonObject requestMsg = TenantConstants.getServiceRequestAsJson(Action.ACTION_ADD.toString(),
                        (String) tenantId, payload);
                doTenantAction(ctx, requestMsg, (status, tenantResult) -> {
                    response.setStatusCode(status);
                    switch(status) {
                        case HttpURLConnection.HTTP_CREATED:
                            response.putHeader(
                                    HttpHeaders.LOCATION,
                                    String.format("/%s/%s", TenantConstants.TENANT_ENDPOINT, tenantId));
                        default:
                            if (status >= 400) {
                                setResponseBody(tenantResult, response);
                            }
                            response.end();
                    }
                });
            }
        }
    }

    private void doUpdateTenantJson(final RoutingContext ctx) {

        try {
            JsonObject payload = null;
            if (ctx.getBody().length() > 0) {
                payload = ctx.getBodyAsJson();
            }
            updateTenant(payload, ctx);
        } catch (final DecodeException e) {
            HttpUtils.badRequest(ctx, "body does not contain a valid JSON object");
        }
    }

    private void updateTenant(final JsonObject payload, final RoutingContext ctx) {

        final String tenantId = getTenantParam(ctx);
        logger.debug("updating tenant [tenant: {}, payload: {}]", tenantId, payload);
        if (payload != null) {
            payload.remove(FIELD_TENANT_ID);
        }
        final HttpServerResponse response = ctx.response();
        final JsonObject requestMsg = TenantConstants.getServiceRequestAsJson(Action.ACTION_UPDATE.toString(),
                tenantId, payload);

        doTenantAction(ctx, requestMsg, (status, tenantResult) -> {
                response.setStatusCode(status);
                if (status >=400) {
                    setResponseBody(tenantResult, response);
                }
                response.end();
        });
    }

    private void doRemoveTenant(final RoutingContext ctx) {

        final String tenantId = getTenantParam(ctx);
        logger.debug("removing tenant [tenant: {}]", tenantId);
        final HttpServerResponse response = ctx.response();
        final JsonObject requestMsg = TenantConstants.getServiceRequestAsJson(Action.ACTION_REMOVE.toString(), tenantId);
        doTenantAction(ctx, requestMsg, (status, tenantResult) -> {
                response.setStatusCode(status);
                if (status >= 400) {
                    setResponseBody(tenantResult, response);
                }
                response.end();
        });
    }

    private void doTenantAction(final RoutingContext ctx, final JsonObject requestMsg, final BiConsumer<Integer, JsonObject> responseHandler) {

        vertx.eventBus().send(TenantConstants.EVENT_BUS_ADDRESS_TENANT_IN, requestMsg,
                invocation -> {
                    if (invocation.failed()) {
                        HttpUtils.serviceUnavailable(ctx, 2);
                    } else {
                        final JsonObject tenantResult = (JsonObject) invocation.result().body();
                        final Integer status = tenantResult.getInteger(MessageHelper.APP_PROPERTY_STATUS);
                        responseHandler.accept(status, tenantResult);
                    }
                });
    }
}
