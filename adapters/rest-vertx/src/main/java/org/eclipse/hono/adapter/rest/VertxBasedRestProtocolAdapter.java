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

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;

import org.eclipse.hono.client.HonoClient;
import org.eclipse.hono.client.HonoClient.HonoClientBuilder;
import org.eclipse.hono.client.RegistrationClient;
import org.eclipse.hono.client.TelemetrySender;
import org.eclipse.hono.util.MessageHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.proton.ProtonClientOptions;

/**
 * A Vert.x based Hono protocol adapter for uploading Telemetry data using REST.
 */
@Component
@Scope("prototype")
public class VertxBasedRestProtocolAdapter extends AbstractVerticle {

    private static final Logger LOG = LoggerFactory.getLogger(VertxBasedRestProtocolAdapter.class);
    private static final String PARAM_TENANT = "tenant";
    private static final String PARAM_DEVICE_ID = "deviceid";

    @Value("${hono.http.bindaddress:127.0.0.1}")
    private String bindAddress;

    @Value("${hono.http.listenport:8080}")
    private int listenPort;

    @Value("${hono.server.host:127.0.0.1}")
    private String honoServerHost;

    @Value("${hono.server.port:5672}")
    private int honoServerPort;

    @Value("${hono.user}")
    private String honoUser;

    @Value("${hono.password}")
    private String honoPassword;

    @Value("${spring.profiles.active:prod}")
    private String activeProfiles;

    private HttpServer server;
    private HonoClient hono;
    private Map<String, TelemetrySender> telemetrySenders = new HashMap<>();
    private Map<String, RegistrationClient> registrationClients = new HashMap<>();
    private AtomicBoolean connecting = new AtomicBoolean();

    /**
     * Creates a new REST adapter instance.
     */
    public VertxBasedRestProtocolAdapter() {
    }

    @Override
    public void start(Future<Void> startFuture) throws Exception {

        Router router = Router.router(vertx);
        router.route(HttpMethod.GET, "/status").handler(this::doGetStatus);
        router.route(HttpMethod.GET, String.format("/registration/:%s/:%s", PARAM_TENANT, PARAM_DEVICE_ID)).handler(this::doGetDevice);
        router.route(HttpMethod.POST, String.format("/registration/:%s", PARAM_TENANT)).handler(this::doRegisterDevice);
        router.route(HttpMethod.DELETE, String.format("/registration/:%s/:%s", PARAM_TENANT, PARAM_DEVICE_ID)).handler(this::doUnregisterDevice);
        router.route(HttpMethod.PUT, String.format("/telemetry/:%s/:%s", PARAM_TENANT, PARAM_DEVICE_ID)).handler(this::doUploadTelemetryData);

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
        connectToHono(null);
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

    private static String getDeviceIdHeader(final RoutingContext ctx) {
        return ctx.request().getHeader(MessageHelper.APP_PROPERTY_DEVICE_ID);
    }

    private static void badRequest(final RoutingContext ctx, final String missingHeader) {
        ctx.response().setStatusCode(HTTP_BAD_REQUEST).end(missingHeader + " header is missing");
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

    private void doGetStatus(final RoutingContext ctx) {
        JsonObject result = new JsonObject()
                .put("name", "Hono REST Adapter")
                .put("connected", isConnected())
                .put("telemetry senders", telemetrySenders.size())
                .put("registration clients", registrationClients.size())
                .put("active profiles", activeProfiles);

        ctx
        .response()
        .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
        .end(result.encodePrettily());
    }

    private void doRegisterDevice(final RoutingContext ctx) {
        String deviceId = getDeviceIdHeader(ctx);
        if (deviceId == null) {
            badRequest(ctx, MessageHelper.APP_PROPERTY_DEVICE_ID);
        } else {
            doRegistrationAction(ctx, (client, response) -> {
                client.register(deviceId, result -> {
                    if (result.failed()) {
                        internalServerError(response, "could not register device");
                    } else {
                        response.setStatusCode(result.result());
                        switch(result.result()) {
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

    private void doUnregisterDevice(final RoutingContext ctx) {
        String deviceId = getDeviceIdParam(ctx);
        doRegistrationAction(ctx, (client, response) -> {
            client.deregister(deviceId, result -> {
                if (result.failed()) {
                    internalServerError(response, "could not unregister device");
                } else {
                    response.setStatusCode(result.result()).end();
                }
            });
        });
    }

    private void doGetDevice(final RoutingContext ctx) {
        String deviceId = getDeviceIdParam(ctx);
        doRegistrationAction(ctx, (client, response) -> {
            client.get(deviceId, result -> {
                if (result.failed()) {
                    internalServerError(response, "could not get device");
                } else {
                    response.setStatusCode(result.result());
                    switch(result.result()) {
                    case HTTP_OK:
                        String msg = new JsonObject().put("device-id", deviceId).encodePrettily();
                        response
                            .putHeader(HttpHeaders.CONTENT_TYPE, "application/json; charset=utf-8")
                            .putHeader(HttpHeaders.CONTENT_LENGTH, String.valueOf(msg.length()))
                            .write(msg);
                    default:
                        response.end();
                    }
                }
            });
        });
    }

    private void doRegistrationAction(final RoutingContext ctx, final BiConsumer<RegistrationClient, HttpServerResponse> action) {
        final String tenant = getTenantParam(ctx);
        final HttpServerResponse resp = ctx.response();
        getOrCreateRegistrationClient(tenant, done -> {
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
            badRequest(ctx, HttpHeaders.CONTENT_TYPE.toString());
        } else {
            ctx.request().bodyHandler(payload -> {
                getOrCreateTelemetrySender(tenant, createAttempt -> {
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
        if (connecting.compareAndSet(false, true)) {
            telemetrySenders.clear();
            registrationClients.clear();
            hono = HonoClientBuilder.newClient()
                    .vertx(vertx)
                    .host(honoServerHost)
                    .port(honoServerPort)
                    .user(honoUser)
                    .password(honoPassword)
                    .build();
            ProtonClientOptions options = new ProtonClientOptions();
            options.setReconnectAttempts(10).setReconnectInterval(500);
            hono.connect(options, connectAttempt -> {
                connecting.set(false);
                if (connectHandler != null) {
                    connectHandler.handle(connectAttempt);
                }
            });
        } else {
            LOG.debug("already trying to connect to Hono server...");
        }
    }

    private boolean isConnected() {
        return hono != null && hono.isConnected();
    }

    private void getOrCreateTelemetrySender(final String tenant, final Handler<AsyncResult<TelemetrySender>> resultHandler) {
        if (!isConnected()) {
            vertx.runOnContext(connect -> connectToHono(null));
            resultHandler.handle(Future.failedFuture("connection to Hono lost"));
        } else {
            TelemetrySender sender = telemetrySenders.get(tenant);
            if (sender !=  null) {
                resultHandler.handle(Future.succeededFuture(sender));
            } else {
                hono.createTelemetrySender(tenant, done -> {
                    if (done.succeeded()) {
                        final TelemetrySender newSender = done.result();
                        TelemetrySender existingSender = telemetrySenders.putIfAbsent(tenant, newSender);
                        if (existingSender != null) {
                            newSender.close(closed -> {});
                            resultHandler.handle(Future.succeededFuture(existingSender));
                        } else {
                            newSender.setErrorHandler(error -> {
                                // remove sender if link has been closed
                                telemetrySenders.remove(tenant);
                            });
                            resultHandler.handle(Future.succeededFuture(newSender));
                        }
                    } else {
                        resultHandler.handle(Future.failedFuture(done.cause()));
                    }
                });
            }
        }
    }

    private void getOrCreateRegistrationClient(final String tenant, final Handler<AsyncResult<RegistrationClient>> resultHandler) {
        if (!isConnected()) {
            vertx.runOnContext(connect -> connectToHono(null));
            resultHandler.handle(Future.failedFuture("connection to Hono lost"));
        } else {
            RegistrationClient client = registrationClients.get(tenant);
            if (client !=  null) {
                resultHandler.handle(Future.succeededFuture(client));
            } else {
                hono.createRegistrationClient(tenant, done -> {
                    if (done.succeeded()) {
                        RegistrationClient existingClient = registrationClients.putIfAbsent(tenant, done.result());
                        if (existingClient != null) {
                            done.result().close(closed -> {});
                            resultHandler.handle(Future.succeededFuture(existingClient));
                        } else {
                            resultHandler.handle(Future.succeededFuture(done.result()));
                        }
                    } else {
                        resultHandler.handle(Future.failedFuture(done.cause()));
                    }
                });
            }
        }
    }
}
