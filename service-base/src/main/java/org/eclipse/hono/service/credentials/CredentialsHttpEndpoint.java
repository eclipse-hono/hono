/**
 * Copyright (c) 2016, 2017 Red Hat and others
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Red Hat - initial creation
 */

package org.eclipse.hono.service.credentials;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import org.eclipse.hono.config.ServiceConfigProperties;
import org.eclipse.hono.service.http.AbstractHttpEndpoint;
import org.eclipse.hono.service.http.HttpEndpointUtils;
import org.eclipse.hono.util.CredentialsConstants;
import org.springframework.beans.factory.annotation.Autowired;

import java.net.HttpURLConnection;
import java.util.Objects;
import java.util.function.BiConsumer;

import static org.eclipse.hono.util.RequestResponseApiConstants.FIELD_DEVICE_ID;

/**
 * An {@code HttpEndpoint} for managing device credentials.
 * <p>
 * This endpoint implements Hono's <a href="https://www.eclipse.org/hono/api/Credentials-API//">Credentials API</a>.
 * It receives HTTP requests representing operation invocations and sends them to an address on the vertx
 * event bus for processing. The outcome is then returned to the peer in the HTTP response.
 */
public final class CredentialsHttpEndpoint extends AbstractHttpEndpoint<ServiceConfigProperties> {

    // path parameters for capturing parts of the URI path
    private static final String PARAM_TENANT_ID = "tenant_id";

    /**
     * Creates an endpoint for a Vertx instance.
     *
     * @param vertx The Vertx instance to use.
     * @throws NullPointerException if vertx is {@code null};
     */
    @Autowired
    public CredentialsHttpEndpoint(final Vertx vertx) {
        super(Objects.requireNonNull(vertx));
    }

    @Override
    public void addRoutes(final Router router) {

        final String pathWithTenant = String.format("/%s/:%s", CredentialsConstants.CREDENTIALS_ENDPOINT, PARAM_TENANT_ID);
        // ADD credentials
        router.route(HttpMethod.POST, pathWithTenant).consumes(HttpEndpointUtils.CONTENT_TYPE_JSON)
                .handler(this::doAddCredentialsJson);
        router.route(HttpMethod.POST, pathWithTenant)
                .handler(ctx -> HttpEndpointUtils.badRequest(ctx.response(), "missing or unsupported content-type"));
    }

    private static String getTenantParam(final RoutingContext ctx) {
        return ctx.request().getParam(PARAM_TENANT_ID);
    }

    @Override
    public String getName() {
        return CredentialsConstants.CREDENTIALS_ENDPOINT;
    }

    private void doAddCredentialsJson(final RoutingContext ctx) {
        try {
            JsonObject payload = null;
            if (ctx.getBody().length() > 0) {
                payload = ctx.getBodyAsJson();
            }
            addCredentials(ctx, payload);
        } catch (DecodeException e) {
            HttpEndpointUtils.badRequest(ctx.response(), "body does not contain a valid JSON object");
        }
    }

    private void addCredentials(final RoutingContext ctx, final JsonObject payload) {

        if (payload == null) {
            HttpEndpointUtils.badRequest(ctx.response(), "missing body");
        } else {
            final Object deviceId = payload.getValue(FIELD_DEVICE_ID);
            final String authId = payload.getString(CredentialsConstants.FIELD_AUTH_ID);
            final String type = payload.getString(CredentialsConstants.FIELD_TYPE);
            if (deviceId == null) {
                HttpEndpointUtils.badRequest(ctx.response(), String.format("'%s' param is required", FIELD_DEVICE_ID));
            } else if (!(deviceId instanceof String)) {
                HttpEndpointUtils.badRequest(ctx.response(), String.format("'%s' must be a string", FIELD_DEVICE_ID));
            } else if (authId == null) {
                HttpEndpointUtils.badRequest(ctx.response(), String.format("'%s' param is required", CredentialsConstants.FIELD_AUTH_ID));
            } else if (type == null) {
                HttpEndpointUtils.badRequest(ctx.response(), String.format("'%s' param is required", CredentialsConstants.FIELD_TYPE));
            } else {
                final String tenantId = getTenantParam(ctx);
                logger.debug("adding credentials for device [tenant: {}, device: {}, payload: {}]", tenantId, deviceId, payload);
                final HttpServerResponse response = ctx.response();
                final JsonObject requestMsg = CredentialsConstants.getServiceRequestAsJson(CredentialsConstants.OPERATION_ADD, tenantId, (String) deviceId, payload);
                doAddCredentialsAction(ctx, requestMsg, (status, addCredentialsResult) -> {
                    response.setStatusCode(status);
                    switch(status) {
                        case HttpURLConnection.HTTP_CREATED:
                        response
                                .putHeader(
                                        HttpHeaders.LOCATION,
                                        String.format("/%s/%s/%s/%s", CredentialsConstants.CREDENTIALS_ENDPOINT, tenantId, authId, type));
                        default:
                            response.end();
                    }
                });
            }
        }
    }

    private void doAddCredentialsAction(final RoutingContext ctx, final JsonObject requestMsg, final BiConsumer<Integer, JsonObject> responseHandler) {

        vertx.eventBus().send(CredentialsConstants.EVENT_BUS_ADDRESS_CREDENTIALS_IN, requestMsg,
                invocation -> {
                    HttpServerResponse response = ctx.response();
                    if (invocation.failed()) {
                        HttpEndpointUtils.serviceUnavailable(response, 2);
                    } else {
                        final JsonObject addCredentialsResult = (JsonObject) invocation.result().body();
                        final Integer status = Integer.valueOf(addCredentialsResult.getString("status"));
                        responseHandler.accept(status, addCredentialsResult);
                    }
                });
    }
}
