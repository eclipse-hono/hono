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

import static org.eclipse.hono.util.TenantConstants.Action;
import static org.eclipse.hono.util.TenantConstants.FIELD_TENANT_ID;

import java.net.HttpURLConnection;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

import org.eclipse.hono.config.ServiceConfigProperties;
import org.eclipse.hono.service.http.AbstractHttpEndpoint;
import org.eclipse.hono.service.http.HttpUtils;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.TenantConstants;
import org.springframework.beans.factory.annotation.Autowired;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;

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

        final BodyHandler bodyHandler = BodyHandler.create();
        bodyHandler.setBodyLimit(2048); // limit body size to 2kb

        // ADD tenant
        router.post(path).handler(bodyHandler);
        router.post(path).handler(this::extractRequiredJsonPayload);
        router.post(path).handler(this::checkPayloadForTenantId);
        router.post(path).handler(ctx -> doTenantHttpRequest(ctx, Action.ACTION_ADD,
                status -> status == HttpURLConnection.HTTP_CREATED));

        final String pathWithTenant = String.format("/%s/:%s", TenantConstants.TENANT_ENDPOINT, PARAM_TENANT_ID);

        // GET tenant
        router.get(pathWithTenant).handler(ctx -> doTenantHttpRequest(ctx, Action.ACTION_GET,
                status -> status == HttpURLConnection.HTTP_OK));

        // UPDATE tenant
        router.put(pathWithTenant).handler(bodyHandler);
        router.put(pathWithTenant).handler(this::extractRequiredJsonPayload);
        router.put(pathWithTenant).handler(ctx -> doTenantHttpRequest(ctx, Action.ACTION_UPDATE, null));

        // REMOVE tenant
        router.delete(pathWithTenant).handler(ctx -> doTenantHttpRequest(ctx, Action.ACTION_REMOVE, null));
    }

    /**
     * Check the Content-Type of the request to be 'application/json' and extract the payload if this check was
     * successful.
     * <p>
     * The payload is parsed to ensure it is valid JSON and is put to the RoutingContext ctx with the
     * key {@link #KEY_REQUEST_BODY}.
     *
     * @param ctx The routing context to retrieve the JSON request body from.
     */
    protected void checkPayloadForTenantId(final RoutingContext ctx) {

        final JsonObject payload = ctx.get(KEY_REQUEST_BODY);

        final Object tenantId = payload.getValue(FIELD_TENANT_ID);

        if (tenantId == null) {
            ctx.response().setStatusMessage(String.format("'%s' param is required", FIELD_TENANT_ID));
            ctx.fail(HttpURLConnection.HTTP_BAD_REQUEST);
        } else if (!(tenantId instanceof String)) {
            ctx.response().setStatusMessage(String.format("'%s' must be a string", FIELD_TENANT_ID));
            ctx.fail(HttpURLConnection.HTTP_BAD_REQUEST);
        }
        ctx.next();
    }

    private void doTenantHttpRequest(final RoutingContext ctx, final TenantConstants.Action action, final Predicate<Integer> sendResponseForStatus) {

        final JsonObject payload = ctx.get(KEY_REQUEST_BODY);

        final String tenantId = Optional.ofNullable(getTenantParam(ctx)).orElse(getTenantParamFromPayload(payload));
        logger.debug("http request [{}] for tenant [tenant: {}]", action, tenantId);

        final HttpServerResponse response = ctx.response();
        final JsonObject requestMsg = TenantConstants.getServiceRequestAsJson(action.toString(), tenantId, payload);

        sendTenantAction(ctx, requestMsg, (status, tenantResult) -> {
            response.setStatusCode(status);
            if (status >=400) {
                setResponseBody(tenantResult, response);
            } else if (sendResponseForStatus != null) {
                if (sendResponseForStatus.test(status)) {
                    if (action == Action.ACTION_ADD) {
                        response.putHeader(
                                HttpHeaders.LOCATION,
                                String.format("/%s/%s", TenantConstants.TENANT_ENDPOINT, tenantId));
                    }
                    setResponseBody(tenantResult, response);
                }
            }
            response.end();
        });
    }

    private static String getTenantParam(final RoutingContext ctx) {
        return ctx.request().getParam(PARAM_TENANT_ID);
    }

    private static String getTenantParamFromPayload(final JsonObject payload) {
        return (payload != null ? (String) payload.remove(TenantConstants.FIELD_TENANT_ID) : null);
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

    private void sendTenantAction(final RoutingContext ctx, final JsonObject requestMsg, final BiConsumer<Integer, JsonObject> responseHandler) {

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
