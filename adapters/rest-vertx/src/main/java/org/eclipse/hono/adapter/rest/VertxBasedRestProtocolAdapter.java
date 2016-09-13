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

import org.eclipse.hono.client.HonoClient;
import org.eclipse.hono.client.HonoClient.HonoClientBuilder;
import org.eclipse.hono.client.RegistrationClient;
import org.eclipse.hono.client.TelemetrySender;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.proton.ProtonClientOptions;

/**
 * A Vert.x based Hono protocol adapter for uploading Telemetry data using REST.
 */
@Component
@Scope("prototype")
public class VertxBasedRestProtocolAdapter extends AbstractVerticle {

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
        router.get().handler(this::doGet);
        router.route(HttpMethod.POST, "/registration/:tenant").handler(this::doRegisterDevice);
        router.route(HttpMethod.PUT, "/telemetry/:tenant").handler(this::doUploadTelemetryData);

        Future<HttpServer> serverTracker = Future.future();
        Future<HonoClient> honoTracker = Future.future();
        CompositeFuture.all(serverTracker, honoTracker).setHandler(done -> {
            if (done.succeeded()) {
                startFuture.complete();
            } else {
                startFuture.fail(done.cause());
            }
        });

        server.requestHandler(router::accept).listen(serverTracker.completer());
        connectToHono(honoTracker.completer());
    }

    @Override
    public void stop(Future<Void> stopFuture) throws Exception {
        if (hono != null) {
            hono.shutdown();
        }
        if (server != null) {
            server.close(done -> {
                if (done.succeeded()) {
                    stopFuture.succeeded();
                } else {
                    stopFuture.fail(done.cause());
                }
            });
        }
    }

    private void doGet(final RoutingContext ctx) {
        ctx.response().end("thanks");
    }

    private void doRegisterDevice(final RoutingContext ctx) {
        final String tenant = ctx.request().getParam("tenant");
        final String deviceId = ctx.request().getHeader("device-id");
        getOrCreateRegistrationClient(tenant, done -> {
            if (done.succeeded()) {
                done.result().register(deviceId, registration -> {
                    if (registration.succeeded()) {
                        ctx.response().setStatusCode(registration.result()).end();
                    } else {
                        ctx.response().setStatusCode(HttpURLConnection.HTTP_INTERNAL_ERROR).end();
                    }
                });
            } else {
                ctx.response().setStatusCode(HttpURLConnection.HTTP_INTERNAL_ERROR).end();
            }
        });
    }

    private void doUploadTelemetryData(final RoutingContext ctx) {
        final String tenant = ctx.request().getParam("tenant");
        final String deviceId = ctx.request().getHeader("device-id");
        ctx.request().bodyHandler(payload -> {
            getOrCreateTelemetrySender(tenant, done -> {
                if (done.succeeded()) {
                    done.result().send(deviceId, payload.getBytes(), ctx.request().getHeader(HttpHeaders.CONTENT_TYPE));
                    ctx.response().setStatusCode(HttpURLConnection.HTTP_ACCEPTED).end();
                } else {
                    ctx.response().setStatusCode(HttpURLConnection.HTTP_INTERNAL_ERROR).end();
                }
            });
        });
    }

    private void connectToHono(final Handler<AsyncResult<HonoClient>> connectHandler) {
        hono = HonoClientBuilder.newClient()
                .vertx(vertx)
                .host(honoServerHost)
                .port(honoServerPort)
                .user(honoUser)
                .password(honoPassword)
                .build();
        hono.connect(new ProtonClientOptions().setReconnectAttempts(10).setReconnectInterval(1000), connectHandler);
    }

    private void getOrCreateTelemetrySender(final String tenant, final Handler<AsyncResult<TelemetrySender>> resultHandler) {
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

    private void getOrCreateRegistrationClient(final String tenant, final Handler<AsyncResult<RegistrationClient>> resultHandler) {
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
