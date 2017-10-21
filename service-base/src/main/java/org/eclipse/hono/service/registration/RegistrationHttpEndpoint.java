/**
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 */

package org.eclipse.hono.service.registration;

import static org.eclipse.hono.util.RequestResponseApiConstants.FIELD_DEVICE_ID;

import java.net.HttpURLConnection;
import java.util.Objects;
import java.util.function.BiConsumer;

import org.eclipse.hono.config.ServiceConfigProperties;
import org.eclipse.hono.service.http.AbstractHttpEndpoint;
import org.eclipse.hono.service.http.HttpUtils;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.RegistrationConstants;
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
 * An {@code HttpEndpoint} for managing device registration information.
 * <p>
 * This endpoint implements Hono's <a href="https://www.eclipse.org/hono/api/Device-Registration-API/">Device Registration API</a>.
 * It receives HTTP requests representing operation invocations and sends them to an address on the vertx
 * event bus for processing. The outcome is then returned to the peer in the HTTP response.
 */
public final class RegistrationHttpEndpoint extends AbstractHttpEndpoint<ServiceConfigProperties> {

    // path parameters for capturing parts of the URI path
    private static final String PARAM_TENANT_ID = "tenant_id";
    private static final String PARAM_DEVICE_ID = "device_id";

    /**
     * Creates an endpoint for a Vertx instance.
     * 
     * @param vertx The Vertx instance to use.
     * @throws NullPointerException if vertx is {@code null};
     */
    @Autowired
    public RegistrationHttpEndpoint(final Vertx vertx) {
        super(Objects.requireNonNull(vertx));
    }

    @Override
    public String getName() {
        return RegistrationConstants.REGISTRATION_ENDPOINT;
    }

    @Override
    public void addRoutes(final Router router) {

        final String pathWithTenant = String.format("/%s/:%s", RegistrationConstants.REGISTRATION_ENDPOINT, PARAM_TENANT_ID);
        // ADD device registration
        router.route(HttpMethod.POST, pathWithTenant).consumes(HttpUtils.CONTENT_TYPE_JSON)
                .handler(this::doRegisterDeviceJson);
        router.route(HttpMethod.POST, pathWithTenant)
                .handler(ctx -> HttpUtils.badRequest(ctx, "missing or unsupported content-type"));

        final String pathWithTenantAndDeviceId = String.format("/%s/:%s/:%s",
                RegistrationConstants.REGISTRATION_ENDPOINT, PARAM_TENANT_ID, PARAM_DEVICE_ID);
        // GET device registration
        router.route(HttpMethod.GET, pathWithTenantAndDeviceId).handler(this::doGetDevice);

        // UPDATE existing registration
        router.route(HttpMethod.PUT, pathWithTenantAndDeviceId).consumes(HttpUtils.CONTENT_TYPE_JSON)
                .handler(this::doUpdateRegistrationJson);
        router.route(HttpMethod.PUT, pathWithTenantAndDeviceId)
                .handler(ctx -> HttpUtils.badRequest(ctx, "missing or unsupported content-type"));

        // REMOVE registration
        router.route(HttpMethod.DELETE, pathWithTenantAndDeviceId).handler(this::doUnregisterDevice);
    }

    private static String getTenantParam(final RoutingContext ctx) {
        return ctx.request().getParam(PARAM_TENANT_ID);
    }

    private static String getDeviceIdParam(final RoutingContext ctx) {
        return ctx.request().getParam(PARAM_DEVICE_ID);
    }

    private static void setResponseBody(final JsonObject registrationResult, final HttpServerResponse response) {
        JsonObject msg = registrationResult.getJsonObject(RegistrationConstants.FIELD_PAYLOAD);
        if (msg != null) {
            String body = msg.encodePrettily();
            response.putHeader(HttpHeaders.CONTENT_TYPE, HttpUtils.CONTENT_TYPE_JSON_UFT8)
                .putHeader(HttpHeaders.CONTENT_LENGTH, String.valueOf(body.length()))
                .write(body);
        }
    }

    private void doGetDevice(final RoutingContext ctx) {

        final String deviceId = getDeviceIdParam(ctx);
        final String tenantId = getTenantParam(ctx);
        final HttpServerResponse response = ctx.response();
        final JsonObject requestMsg = RegistrationConstants.getServiceRequestAsJson(RegistrationConstants.ACTION_GET, tenantId, deviceId);

        doRegistrationAction(ctx, requestMsg, (status, registrationResult) -> {
            response.setStatusCode(status);
            switch (status) {
                case HttpURLConnection.HTTP_OK:
                    final String msg = registrationResult.getJsonObject(RegistrationConstants.FIELD_PAYLOAD).encodePrettily();
                    response
                            .putHeader(HttpHeaders.CONTENT_TYPE, HttpUtils.CONTENT_TYPE_JSON_UFT8)
                            .putHeader(HttpHeaders.CONTENT_LENGTH, String.valueOf(msg.length()))
                            .write(msg);
                default:
                    response.end();
                }
        });
    }

    private void doRegisterDeviceJson(final RoutingContext ctx) {
        try {
            JsonObject payload = null;
            if (ctx.getBody().length() > 0) {
                payload = ctx.getBodyAsJson();
            }
            registerDevice(ctx, payload);
        } catch (DecodeException e) {
            HttpUtils.badRequest(ctx, "body does not contain a valid JSON object");
        }
    }

    private void registerDevice(final RoutingContext ctx, final JsonObject payload) {

        if (payload == null) {
            HttpUtils.badRequest(ctx, "missing body");
        } else {
            Object deviceId = payload.remove(FIELD_DEVICE_ID);
            if (deviceId == null) {
                HttpUtils.badRequest(ctx, String.format("'%s' param is required", FIELD_DEVICE_ID));
            } else if (!(deviceId instanceof String)) {
                HttpUtils.badRequest(ctx, String.format("'%s' must be a string", FIELD_DEVICE_ID));
            } else {
                final String tenantId = getTenantParam(ctx);
                logger.debug("registering data for device [tenant: {}, device: {}, payload: {}]", tenantId, deviceId, payload);
                final HttpServerResponse response = ctx.response();
                final JsonObject requestMsg = RegistrationConstants.getServiceRequestAsJson(RegistrationConstants.ACTION_REGISTER, tenantId, (String) deviceId, payload);
                doRegistrationAction(ctx,requestMsg, (status, registrationResult) -> {
                        response.setStatusCode(status);
                        switch(status) {
                        case HttpURLConnection.HTTP_CREATED:
                            response.putHeader(
                                        HttpHeaders.LOCATION,
                                        String.format("/%s/%s/%s", RegistrationConstants.REGISTRATION_ENDPOINT, tenantId, deviceId));
                        default:
                            response.end();
                        }
                });
            }
        }
    }

    private void doUpdateRegistrationJson(final RoutingContext ctx) {

        try {
            JsonObject payload = null;
            if (ctx.getBody().length() > 0) {
                payload = ctx.getBodyAsJson();
            }
            updateRegistration(getDeviceIdParam(ctx), payload, ctx);
        } catch (DecodeException e) {
            HttpUtils.badRequest(ctx, "body does not contain a valid JSON object");
        }
    }

    private void updateRegistration(final String deviceId, final JsonObject payload, final RoutingContext ctx) {

        if (payload != null) {
            payload.remove(FIELD_DEVICE_ID);
        }
        final String tenantId = getTenantParam(ctx);
        logger.debug("updating registration data for device [tenant: {}, device: {}, payload: {}]", tenantId, deviceId, payload);
        final HttpServerResponse response = ctx.response();
        final JsonObject requestMsg = RegistrationConstants.getServiceRequestAsJson(RegistrationConstants.ACTION_UPDATE, tenantId, deviceId, payload);

        doRegistrationAction(ctx, requestMsg, (status, registrationResult) -> {
                response.setStatusCode(status);
                if (status >=400) {
                    setResponseBody(registrationResult, response);
                }
                response.end();
        });
    }

    private void doUnregisterDevice(final RoutingContext ctx) {

        final String deviceId = getDeviceIdParam(ctx);
        final String tenantId = getTenantParam(ctx);
        logger.debug("removing registration information for device [tenant: {}, device: {}]", tenantId, deviceId);
        final HttpServerResponse response = ctx.response();
        final JsonObject requestMsg = RegistrationConstants.getServiceRequestAsJson(RegistrationConstants.ACTION_DEREGISTER, tenantId, deviceId);
        doRegistrationAction(ctx, requestMsg, (status, registrationResult) -> {
                response.setStatusCode(status);
                if (status >= 400) {
                    setResponseBody(registrationResult, response);
                }
                response.end();
        });
    }

    private void doRegistrationAction(final RoutingContext ctx, final JsonObject requestMsg, final BiConsumer<Integer, JsonObject> responseHandler) {

        vertx.eventBus().send(RegistrationConstants.EVENT_BUS_ADDRESS_REGISTRATION_IN, requestMsg,
                invocation -> {
                    if (invocation.failed()) {
                        HttpUtils.serviceUnavailable(ctx, 2);
                    } else {
                        final JsonObject registrationResult = (JsonObject) invocation.result().body();
                        final Integer status = registrationResult.getInteger(MessageHelper.APP_PROPERTY_STATUS);
                        responseHandler.accept(status, registrationResult);
                    }
                });
    }
}
