/**
 * Copyright (c) 2016 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 */

package org.eclipse.hono.adapter.rest;

import static java.net.HttpURLConnection.HTTP_CREATED;
import static java.net.HttpURLConnection.HTTP_OK;

import java.util.Map.Entry;
import java.util.function.BiConsumer;

import org.eclipse.hono.adapter.http.AbstractVertxBasedHttpProtocolAdapter;
import org.eclipse.hono.client.RegistrationClient;
import org.eclipse.hono.config.ServiceConfigProperties;
import org.eclipse.hono.util.RegistrationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

/**
 * A Vert.x based Hono protocol adapter for accessing Hono's Telemetry &amp; Registration API using REST.
 */
@Component
@Scope("prototype")
public class VertxBasedRestProtocolAdapter extends AbstractVertxBasedHttpProtocolAdapter<ServiceConfigProperties> {

    private static final Logger LOG = LoggerFactory.getLogger(VertxBasedRestProtocolAdapter.class);

    private static final String PARAM_TENANT = "tenant";
    private static final String PARAM_DEVICE_ID = "device_id";

    @Override
    protected void addRoutes(final Router router) {
        addTelemetryApiRoutes(router);
        addEventApiRoutes(router);
        addRegistrationApiRoutes(router);
    }

    private void addRegistrationApiRoutes(final Router router) {

        // ADD device registration
        router.route(HttpMethod.POST, String.format("/registration/:%s", PARAM_TENANT))
            .consumes(CONTENT_TYPE_JSON)
            .handler(this::doRegisterDeviceJson);
        router.route(HttpMethod.POST, String.format("/registration/:%s", PARAM_TENANT))
            .consumes(HttpHeaders.APPLICATION_X_WWW_FORM_URLENCODED.toString())
            .handler(this::doRegisterDeviceForm);
        router.route(HttpMethod.POST, "/registration/*/*").handler(ctx -> {
            badRequest(ctx.response(), "missing or unsupported content-type");
        });

        // GET or FIND device registration
        router.route(HttpMethod.GET, String.format("/registration/:%s/:%s", PARAM_TENANT, PARAM_DEVICE_ID))
            .handler(this::doGetDevice);
        router.route(HttpMethod.POST, String.format("/registration/:%s/find", PARAM_TENANT))
            .consumes(HttpHeaders.APPLICATION_X_WWW_FORM_URLENCODED.toString())
            .handler(this::doFindDevice);

        // UPDATE existing registration
        router.route(HttpMethod.PUT, String.format("/registration/:%s/:%s", PARAM_TENANT, PARAM_DEVICE_ID))
            .consumes(CONTENT_TYPE_JSON)
            .handler(this::doUpdateRegistrationJson);
        router.route(HttpMethod.PUT, String.format("/registration/:%s/:%s", PARAM_TENANT, PARAM_DEVICE_ID))
            .consumes(HttpHeaders.APPLICATION_X_WWW_FORM_URLENCODED.toString())
            .handler(this::doUpdateRegistrationForm);
        router.route(HttpMethod.PUT, "/registration/*/*").handler(ctx -> {
            badRequest(ctx.response(), "missing or unsupported content-type");
        });

        // REMOVE registration
        router.route(HttpMethod.DELETE, String.format("/registration/:%s/:%s", PARAM_TENANT, PARAM_DEVICE_ID))
            .handler(this::doUnregisterDevice);
    }

    private void addTelemetryApiRoutes(final Router router) {

        // route for uploading telemetry data
        router.route(HttpMethod.PUT, String.format("/telemetry/:%s/:%s", PARAM_TENANT, PARAM_DEVICE_ID))
            .handler(ctx -> uploadTelemetryMessage(ctx, getTenantParam(ctx), getDeviceIdParam(ctx)));
    }

    private void addEventApiRoutes(final Router router) {

        // route for sending event messages
        router.route(HttpMethod.PUT, String.format("/event/:%s/:%s", PARAM_TENANT, PARAM_DEVICE_ID))
            .handler(ctx -> uploadEventMessage(ctx, getTenantParam(ctx), getDeviceIdParam(ctx)));
    }

    private static String getTenantParam(final RoutingContext ctx) {
        return ctx.request().getParam(PARAM_TENANT);
    }

    private static String getDeviceIdParam(final RoutingContext ctx) {
        return ctx.request().getParam(PARAM_DEVICE_ID);
    }

    private static JsonObject getPayloadForParams(final HttpServerRequest request) {
        JsonObject payload = new JsonObject();
        for (Entry<String, String> param : request.params()) {
            // filter out tenant param captured from URI path
            if (!PARAM_TENANT.equalsIgnoreCase(param.getKey())) {
                payload.put(param.getKey(), param.getValue());
            }
        }
        return payload;
    }

    private void doRegisterDeviceJson(final RoutingContext ctx) {
        try {
            JsonObject payload = null;
            if (ctx.getBody().length() > 0) {
                payload = ctx.getBodyAsJson();
            }
            registerDevice(ctx, payload);
        } catch (DecodeException e) {
            badRequest(ctx.response(), "body does not contain a valid JSON object");
        }
    }

    private void doRegisterDeviceForm(final RoutingContext ctx) {

        registerDevice(ctx, getPayloadForParams(ctx.request()));
    }

    private void registerDevice(final RoutingContext ctx, final JsonObject payload) {

        if (payload == null) {
            badRequest(ctx.response(), "missing body");
        } else {
            Object deviceId = payload.remove(PARAM_DEVICE_ID);
            if (deviceId == null) {
                badRequest(ctx.response(), String.format("'%s' param is required", PARAM_DEVICE_ID));
            } else if (!(deviceId instanceof String)) {
                badRequest(ctx.response(), String.format("'%s' must be a string", PARAM_DEVICE_ID));
            } else {
                LOG.debug("registering data for device: {}, {}", deviceId, payload);
                doRegistrationAction(ctx, (client, response) -> {
                    client.register((String) deviceId, payload, result -> {
                        if (result.failed()) {
                            internalServerError(response, "could not register device");
                        } else {
                            RegistrationResult registerResult = result.result();
                            response.setStatusCode(registerResult.getStatus());
                            switch(registerResult.getStatus()) {
                            case HTTP_CREATED:
                                response
                                    .putHeader(
                                            HttpHeaders.LOCATION,
                                            String.format("/registration/%s/%s", getTenantParam(ctx), deviceId));
                            default:
                                response.end();
                            }
                        }
                    });
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
            badRequest(ctx.response(), "body does not contain a valid JSON object");
        }
    }

    private void doUpdateRegistrationForm(final RoutingContext ctx) {

        updateRegistration(getDeviceIdParam(ctx), getPayloadForParams(ctx.request()), ctx);
    }

    private void updateRegistration(final String deviceId, final JsonObject payload, final RoutingContext ctx) {

        if (payload != null) {
            payload.remove(PARAM_DEVICE_ID);
        }
        doRegistrationAction(ctx, (client, response) -> {
            client.update(deviceId, payload, updateRequest -> {
                if (updateRequest.failed()) {
                    internalServerError(response, "could not update device registration");
                } else {
                    RegistrationResult updateResult = updateRequest.result();
                    response.setStatusCode(updateResult.getStatus());
                    switch(updateResult.getStatus()) {
                    case HTTP_OK:
                        String msg = updateResult.getPayload().encodePrettily();
                        response
                            .putHeader(HttpHeaders.CONTENT_TYPE, CONTENT_TYPE_JSON_UFT8)
                            .putHeader(HttpHeaders.CONTENT_LENGTH, String.valueOf(msg.length()))
                            .write(msg);
                    default:
                        response.end();
                    }
                }
            });
        });
    }

    private void doUnregisterDevice(final RoutingContext ctx) {
        String deviceId = getDeviceIdParam(ctx);
        doRegistrationAction(ctx, (client, response) -> {
            client.deregister(deviceId, deregisterRequest -> {
                if (deregisterRequest.failed()) {
                    internalServerError(response, "could not unregister device");
                } else {
                    RegistrationResult deregisterResult = deregisterRequest.result();
                    response.setStatusCode(deregisterResult.getStatus());
                    switch(deregisterResult.getStatus()) {
                    case HTTP_OK:
                        String msg = deregisterResult.getPayload().encodePrettily();
                        response
                            .putHeader(HttpHeaders.CONTENT_TYPE, CONTENT_TYPE_JSON_UFT8)
                            .putHeader(HttpHeaders.CONTENT_LENGTH, String.valueOf(msg.length()))
                            .write(msg);
                    default:
                        response.end();
                    }
                }
            });
        });
    }

    private void doGetDevice(final RoutingContext ctx) {
        String deviceId = getDeviceIdParam(ctx);
        doRegistrationAction(ctx, (client, response) -> {
            client.get(deviceId, getRequest -> {
                if (getRequest.failed()) {
                    internalServerError(response, "could not get device");
                } else {
                    RegistrationResult getResult = getRequest.result();
                    response.setStatusCode(getResult.getStatus());
                    switch(getResult.getStatus()) {
                    case HTTP_OK:
                        String msg = getResult.getPayload().encodePrettily();
                        response
                            .putHeader(HttpHeaders.CONTENT_TYPE, CONTENT_TYPE_JSON_UFT8)
                            .putHeader(HttpHeaders.CONTENT_LENGTH, String.valueOf(msg.length()))
                            .write(msg);
                    default:
                        response.end();
                    }
                }
            });
        });
    }

    private void doFindDevice(final RoutingContext ctx) {
        String key = null;
        String value = null;
        for (Entry<String, String> param : ctx.request().params()) {
            if (!PARAM_TENANT.equals(param.getKey())) {
                key = param.getKey();
                value = param.getValue();
            }
        }
        findDevice(key, value, ctx);
    }

    private void findDevice(final String key, final String value, final RoutingContext ctx) {
        if (key == null || value == null) {
            badRequest(ctx.response(), "query param is missing");
        } else {
            doRegistrationAction(ctx, (client, response) -> {
                client.find(key, value, getRequest -> {
                    if (getRequest.failed()) {
                        internalServerError(response, "could not get device");
                    } else {
                        RegistrationResult getResult = getRequest.result();
                        response.setStatusCode(getResult.getStatus());
                        switch(getResult.getStatus()) {
                        case HTTP_OK:
                            String msg = getResult.getPayload().encodePrettily();
                            response
                                .putHeader(HttpHeaders.CONTENT_TYPE, CONTENT_TYPE_JSON_UFT8)
                                .putHeader(HttpHeaders.CONTENT_LENGTH, String.valueOf(msg.length()))
                                .write(msg);
                        default:
                            response.end();
                        }
                    }
                });
            });
        }
    }

    private void doRegistrationAction(final RoutingContext ctx, final BiConsumer<RegistrationClient, HttpServerResponse> action) {
        final String tenant = getTenantParam(ctx);
        final HttpServerResponse resp = ctx.response();
        getRegistrationServiceClient().getOrCreateRegistrationClient(tenant, done -> {
            if (done.succeeded()) {
                action.accept(done.result(), resp);
            } else {
                LOG.debug("can't connect to Device Registration service: {}", done.cause());
                // we don't have a connection to Hono
                serviceUnavailable(resp, 2);
            }
        });
    }
}
