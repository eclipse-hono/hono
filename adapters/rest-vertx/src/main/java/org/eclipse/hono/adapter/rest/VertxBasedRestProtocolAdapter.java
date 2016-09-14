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

import java.net.HttpURLConnection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

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
        HttpServerOptions options = new HttpServerOptions();
        options.setHost(bindAddress);
        options.setPort(listenPort);
        server = vertx.createHttpServer(options);
        Router router = Router.router(vertx);
        router.route(HttpMethod.GET, "/status").handler(this::doGetStatus);
        router.route(HttpMethod.POST, String.format("/registration/:%s", PARAM_TENANT)).handler(this::doRegisterDevice);
        router.route(HttpMethod.PUT, String.format("/telemetry/:%s", PARAM_TENANT)).handler(this::doUploadTelemetryData);

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

    private static String getTenant(final RoutingContext ctx) {
        return ctx.request().getParam(PARAM_TENANT);
    }

    private static String getDeviceId(final RoutingContext ctx) {
        return ctx.request().getHeader(MessageHelper.APP_PROPERTY_DEVICE_ID);
    }

    private void doGetStatus(final RoutingContext ctx) {
        JsonObject result = new JsonObject()
                .put("name", "Hono REST Adapter")
                .put("connected", isConnected())
                .put("telemetry senders", telemetrySenders.size())
                .put("registration clients", registrationClients.size());

        ctx
        .response()
        .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
        .end(result.encodePrettily());
    }

    private void doRegisterDevice(final RoutingContext ctx) {
        final String tenant = getTenant(ctx);
        final String deviceId = getDeviceId(ctx);
        if (deviceId == null) {
            badRequest(ctx, MessageHelper.APP_PROPERTY_DEVICE_ID);
        } else {
            getOrCreateRegistrationClient(tenant, done -> {
                if (done.succeeded()) {
                    done.result().register(deviceId, registration -> {
                        ctx.response().setStatusCode(registration.result()).end();
                    });
                } else {
                    ctx.response().setStatusCode(HttpURLConnection.HTTP_INTERNAL_ERROR).end("no connection to Hono server");
                }
            });
        }
    }

    private void doUploadTelemetryData(final RoutingContext ctx) {
        final String tenant = getTenant(ctx);
        final String deviceId = getDeviceId(ctx);
        final String contentType = ctx.request().getHeader(HttpHeaders.CONTENT_TYPE);
        if (deviceId == null) {
            badRequest(ctx, MessageHelper.APP_PROPERTY_DEVICE_ID);
        } else if (contentType == null) {
            badRequest(ctx, HttpHeaders.CONTENT_TYPE.toString());
        } else {
            ctx.request().bodyHandler(payload -> {
                getOrCreateTelemetrySender(tenant, done -> {
                    if (done.succeeded()) {
                        done.result().send(deviceId, payload.getBytes(), contentType);
                        ctx.response().setStatusCode(HttpURLConnection.HTTP_ACCEPTED).end();
                    } else {
                        ctx.response().setStatusCode(HttpURLConnection.HTTP_INTERNAL_ERROR).end("no connection to Hono server");
                    }
                });
            });
        }
    }

    private void badRequest(final RoutingContext ctx, final String missingHeader) {
        ctx.response().setStatusCode(HttpURLConnection.HTTP_BAD_REQUEST).end(missingHeader + " header is missing");
    }

    private void connectToHono(final Handler<AsyncResult<HonoClient>> connectHandler) {
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
                        TelemetrySender existingSender = telemetrySenders.putIfAbsent(tenant, done.result());
                        if (existingSender != null) {
                            done.result().close(closed -> {});
                            resultHandler.handle(Future.succeededFuture(existingSender));
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
