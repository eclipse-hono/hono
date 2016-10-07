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

import static java.net.HttpURLConnection.*;

import java.util.Map.Entry;
import java.util.function.BiConsumer;

import org.eclipse.hono.client.HonoClient;
import org.eclipse.hono.client.HonoClient.HonoClientBuilder;
import org.eclipse.hono.client.HonoClientConfigProperties;
import org.eclipse.hono.client.RegistrationClient;
import org.eclipse.hono.util.RegistrationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.proton.ProtonClientOptions;

/**
 * A Vert.x based Hono protocol adapter for accessing Hono's Telemetry &amp; Registration API using REST.
 */
@Component
@Scope("prototype")
public class VertxBasedRestProtocolAdapter extends AbstractVerticle {

    private static final String CONTENT_TYPE_JSON = "application/json";
    private static final String CONTENT_TYPE_JSON_UFT8 = "application/json; charset=utf-8";
    private static final Logger LOG = LoggerFactory.getLogger(VertxBasedRestProtocolAdapter.class);
    private static final String PARAM_TENANT = "tenant";
    private static final String PARAM_DEVICE_ID = "device_id";
    private static final String NAME = "Hono REST Adapter";

    @Value("${hono.http.bindaddress:127.0.0.1}")
    private String bindAddress;

    @Value("${hono.http.listenport:8080}")
    private int listenPort;

    @Autowired
    private HonoClientConfigProperties honoClientConfig;

    @Value("${spring.profiles.active:prod}")
    private String activeProfiles;

    private HttpServer server;
    private HonoClient hono;

    @Override
    public void start(Future<Void> startFuture) throws Exception {

        bindHttpServer(createRouter(), startFuture);
        connectToHono(null);
    }

    /**
     * Creates the router for handling requests.
     * 
     * @return The router.
     */
    Router createRouter() {
        Router router = Router.router(vertx);
        router.route().handler(BodyHandler.create().setBodyLimit(2048));

        router.route(HttpMethod.GET, "/status").handler(this::doGetStatus);

        addTelemetryApiRoutes(router);
        addRegistrationApiRoutes(router);
        return router;
    }

    private void addRegistrationApiRoutes(final Router router) {

        // ADD device registration
        router.route(HttpMethod.POST, String.format("/registration/:%s", PARAM_TENANT))
            .consumes(CONTENT_TYPE_JSON)
            .handler(this::doRegisterDeviceJson)
            .produces(CONTENT_TYPE_JSON);
        router.route(HttpMethod.POST, String.format("/registration/:%s", PARAM_TENANT))
            .consumes(HttpHeaders.APPLICATION_X_WWW_FORM_URLENCODED.toString())
            .handler(this::doRegisterDeviceForm)
            .produces(CONTENT_TYPE_JSON);

        // GET or FIND device registration
        router.route(HttpMethod.GET, String.format("/registration/:%s/:%s", PARAM_TENANT, PARAM_DEVICE_ID))
            .handler(this::doGetDevice)
            .produces(CONTENT_TYPE_JSON);
        router.route(HttpMethod.POST, String.format("/registration/:%s/find", PARAM_TENANT))
            .consumes(HttpHeaders.APPLICATION_X_WWW_FORM_URLENCODED.toString())
            .handler(this::doFindDevice)
            .produces(CONTENT_TYPE_JSON);

        // UPDATE existing registration
        router.route(HttpMethod.PUT, String.format("/registration/:%s/:%s", PARAM_TENANT, PARAM_DEVICE_ID))
            .consumes(CONTENT_TYPE_JSON)
            .handler(this::doUpdateRegistrationJson);
        router.route(HttpMethod.PUT, String.format("/registration/:%s/:%s", PARAM_TENANT, PARAM_DEVICE_ID))
            .consumes(HttpHeaders.APPLICATION_X_WWW_FORM_URLENCODED.toString())
            .handler(this::doUpdateRegistrationForm);

        // REMOVE registration
        router.route(HttpMethod.DELETE, String.format("/registration/:%s/:%s", PARAM_TENANT, PARAM_DEVICE_ID))
            .handler(this::doUnregisterDevice)
            .produces(CONTENT_TYPE_JSON);
    }

    private void addTelemetryApiRoutes(final Router router) {

        // route for uploading telemetry data
        router.route(HttpMethod.PUT, String.format("/telemetry/:%s/:%s", PARAM_TENANT, PARAM_DEVICE_ID))
            .handler(this::doUploadTelemetryData);
    }

    private void bindHttpServer(final Router router, Future<Void> startFuture) {
        HttpServerOptions options = new HttpServerOptions();
        options.setHost(bindAddress).setPort(listenPort).setMaxChunkSize(4096);
        server = vertx.createHttpServer(options);
        server.requestHandler(router::accept).listen(done -> {
            if (done.succeeded()) {
                LOG.info("Hono REST adapter running on {}:{}", bindAddress, server.actualPort());
                startFuture.complete();
            } else {
                LOG.error("error while starting up Hono REST adapter", done.cause());
                startFuture.fail(done.cause());
            }
        });
    }

    @Override
    public void stop(Future<Void> stopFuture) throws Exception {

        Future<Void> shutdownTracker = Future.future();
        shutdownTracker.setHandler(done -> {
            if (done.succeeded()) {
                LOG.info("REST adapter has been shut down successfully");
                stopFuture.complete();
            } else {
                LOG.info("error while shutting down REST adapter", done.cause());
                stopFuture.fail(done.cause());
            }
        });

        Future<Void> serverTracker = Future.future();
        if (server != null) {
            server.close(serverTracker.completer());
        } else {
            serverTracker.complete();
        }
        serverTracker.compose(d -> {
            if (hono != null) {
                hono.shutdown(shutdownTracker.completer());
            } else {
                shutdownTracker.complete();
            }
        }, shutdownTracker);
    }

    private static String getTenantParam(final RoutingContext ctx) {
        return ctx.request().getParam(PARAM_TENANT);
    }

    private static String getDeviceIdParam(final RoutingContext ctx) {
        return ctx.request().getParam(PARAM_DEVICE_ID);
    }

    private static void badRequest(final RoutingContext ctx, final String msg) {
        ctx.response().setStatusCode(HTTP_BAD_REQUEST).end(msg);
    }

    private static void internalServerError(final HttpServerResponse response, final String msg) {
        response
            .setStatusCode(HTTP_BAD_REQUEST)
            .putHeader(HttpHeaders.CONTENT_TYPE, "text/plain; charset=utf-8")
            .end(msg);
    }

    private static void serviceUnavailable(final HttpServerResponse response, final int retryAfterSeconds) {
        response
            .setStatusCode(HTTP_UNAVAILABLE)
            .putHeader(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds))
            .end();
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

    private void doGetStatus(final RoutingContext ctx) {
        JsonObject result = new JsonObject()
                .put("name", NAME)
                .put("connected", isConnected())
                .put("active profiles", activeProfiles);

        ctx.response()
            .putHeader(HttpHeaders.CONTENT_TYPE, CONTENT_TYPE_JSON)
            .end(result.encodePrettily());
    }

    private void doRegisterDeviceJson(final RoutingContext ctx) {

        registerDevice(ctx, ctx.getBodyAsJson());
    }

    private void doRegisterDeviceForm(final RoutingContext ctx) {

        registerDevice(ctx, getPayloadForParams(ctx.request()));
    }

    private void registerDevice(final RoutingContext ctx, final JsonObject payload) {

        if (payload == null) {
            badRequest(ctx, "payload is missing");
        } else {
            Object deviceId = payload.remove(PARAM_DEVICE_ID);
            if (deviceId == null) {
                badRequest(ctx, String.format("'%s' param is required", PARAM_DEVICE_ID));
            } else if (!(deviceId instanceof String)) {
                badRequest(ctx, String.format("'%s' must be a string", PARAM_DEVICE_ID));
            } else {
                LOG.debug("registering data for device: {}", payload);
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
                        };
                    });
                });
            }
        }
    }

    private void doUpdateRegistrationJson(final RoutingContext ctx) {

        updateRegistration(getDeviceIdParam(ctx), ctx.getBodyAsJson(), ctx);
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
                };
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
            badRequest(ctx, "query param is missing");
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
        hono.getOrCreateRegistrationClient(tenant, done -> {
            if (done.succeeded()) {
                action.accept(done.result(), resp);
            } else {
                // we don't have a connection to Hono
                serviceUnavailable(resp, 2);
            }
        });
    }

    private void doUploadTelemetryData(final RoutingContext ctx) {
        final String tenant = getTenantParam(ctx);
        final String deviceId = getDeviceIdParam(ctx);
        final String contentType = ctx.request().getHeader(HttpHeaders.CONTENT_TYPE);
        if (contentType == null) {
            badRequest(ctx, String.format("%s header is missing", HttpHeaders.CONTENT_TYPE));
        } else {
            ctx.request().bodyHandler(payload -> {
                hono.getOrCreateTelemetrySender(tenant, createAttempt -> {
                    if (createAttempt.succeeded()) {
                        boolean accepted = createAttempt.result().send(deviceId, payload.getBytes(), contentType);
                        if (accepted) {
                            ctx.response().setStatusCode(HTTP_ACCEPTED).end();
                        } else {
                            // we currently have no credit for uploading data to Hono's Telemetry endpoint
                            serviceUnavailable(ctx.response(), 2);
                        }
                    } else {
                        // we don't have a connection to Hono
                        serviceUnavailable(ctx.response(), 2);
                    }
                });
            });
        }
    }

    private void connectToHono(final Handler<AsyncResult<HonoClient>> connectHandler) {

        // make sure that we are not trying to connect multiple times in parallel
        hono = HonoClientBuilder.newClient()
                .vertx(vertx)
                .name(NAME)
                .host(honoClientConfig.getHost())
                .port(honoClientConfig.getPort())
                .user(honoClientConfig.getUsername())
                .password(honoClientConfig.getPassword())
                .build();
        ProtonClientOptions options = new ProtonClientOptions()
                .setReconnectAttempts(-1)
                .setReconnectInterval(200); // try to re-connect every 200 ms
        hono.connect(options, connectAttempt -> {
            if (connectHandler != null) {
                connectHandler.handle(connectAttempt);
            }
        });
    }

    private boolean isConnected() {
        return hono != null && hono.isConnected();
    }
}
