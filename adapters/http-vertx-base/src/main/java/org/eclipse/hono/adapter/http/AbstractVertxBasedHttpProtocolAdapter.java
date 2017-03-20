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

package org.eclipse.hono.adapter.http;

import static java.net.HttpURLConnection.HTTP_ACCEPTED;
import static java.net.HttpURLConnection.HTTP_BAD_REQUEST;
import static java.net.HttpURLConnection.HTTP_INTERNAL_ERROR;
import static java.net.HttpURLConnection.HTTP_UNAVAILABLE;

import java.util.Objects;
import java.util.function.BiConsumer;

import org.eclipse.hono.client.HonoClient;
import org.eclipse.hono.client.MessageSender;
import org.eclipse.hono.config.HonoConfigProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.proton.ProtonClientOptions;

/**
 * Base class for a Vert.x based Hono protocol adapter that uses the HTTP protocol. 
 * It provides access to the Telemetry and Event API. 
 */
public abstract class AbstractVertxBasedHttpProtocolAdapter extends AbstractVerticle {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractVertxBasedHttpProtocolAdapter.class);

    protected static final String CONTENT_TYPE_JSON = "application/json";
    protected static final String CONTENT_TYPE_JSON_UFT8 = "application/json; charset=utf-8";

    @Value("${spring.profiles.active:prod}")
    private String activeProfiles;

    private HttpServer server;
    private HonoClient hono;
    private HonoConfigProperties config;

    private BiConsumer<String, Handler<AsyncResult<MessageSender>>> eventSenderSupplier;
    private BiConsumer<String, Handler<AsyncResult<MessageSender>>> telemetrySenderSupplier;

    /**
     * Sets the client to use for connecting to the Hono server.
     * 
     * @param honoClient The client.
     * @throws NullPointerException if hono client is {@code null}.
     */
    @Autowired
    public final void setHonoClient(final HonoClient honoClient) {
        this.hono = Objects.requireNonNull(honoClient);
    }

    public final HonoClient getHonoClient() {
        return hono;
    }

    /**
     * Sets configuration properties.
     * 
     * @param properties The configuration properties to use.
     * @throws NullPointerException if properties is {@code null}.
     */
    @Autowired
    public final void setConfig(final HonoConfigProperties properties) {
        this.config = Objects.requireNonNull(properties);
    }

    public final HonoConfigProperties getConfig() {
        return config;
    }
    
    @Override
    public final void start(Future<Void> startFuture) throws Exception {

        if (hono == null) {
            startFuture.fail("Hono client must be set");
        } else {
            eventSenderSupplier = (tenant, resultHandler) -> getHonoClient().getOrCreateEventSender(tenant, resultHandler);
            telemetrySenderSupplier = (tenant, resultHandler) -> getHonoClient().getOrCreateTelemetrySender(tenant, resultHandler);

            Future<Void> preStartupFuture = Future.future();
            preStartup(preStartupFuture);
            preStartupFuture.compose(v -> {
                Future<Void> httpServerStartupFuture = Future.future();
                bindHttpServer(createRouter(), httpServerStartupFuture);
                connectToHono(null);
                return httpServerStartupFuture; 
            }).compose(ar -> {
                startFuture.complete();
                try {
                    onStartupSuccess();
                } catch (Exception e) {
                    LOG.error("error in onStartupSuccess", e);
                }
            }, startFuture);
        }
    }

    /**
     * Invoked before the Http server is started.
     * May be overridden by sub-classes to provide additional startup handling.
     * 
     * @param startFuture  a future that has to be completed when this operation is finished
     */
    protected void preStartup(final Future<Void> startFuture) {
        startFuture.complete();
    }

    /**
     * Invoked after startup of this Adapter is successful.
     * May be overridden by sub-classes.
     */
    protected void onStartupSuccess() {
        // empty
    }

    /**
     * Creates the router for handling requests.
     * 
     * @return The router.
     */
    private Router createRouter() {

        final Router router = Router.router(vertx);
        LOG.info("limiting size of inbound request body to {} bytes", config.getMaxPayloadSize());
        router.route().handler(BodyHandler.create().setBodyLimit(config.getMaxPayloadSize()));

        String statusResourcePath = getStatusResourcePath();
        if (statusResourcePath != null) {
            router.route(HttpMethod.GET, statusResourcePath).handler(this::doGetStatus);
        }

        addRoutes(router);
        return router;
    }

    /**
     * Returns the path for the status resource. By default, this is "/status".
     * Subclasses may override this method to return a different path or to return null, 
     * in which case the status resource will be disabled.
     * 
     * @return path or null
     */
    protected String getStatusResourcePath() {
        return "/status";
    }

    /**
     * Adds the routes for handling requests to this adapter.
     * 
     * The handler of each route is to invoke one of the uploadTelemetryMessage() 
     * or uploadEventMessage() methods.
     * 
     * @param router router to add the routes to
     */
    protected abstract void addRoutes(final Router router);

    private void bindHttpServer(final Router router, final Future<Void> startFuture) {
        HttpServerOptions options = new HttpServerOptions();
        options.setHost(config.getBindAddress()).setPort(config.getPort()).setMaxChunkSize(4096);
        server = vertx.createHttpServer(options);
        server.requestHandler(router::accept).listen(done -> {
            if (done.succeeded()) {
                LOG.info("adapter running on {}:{}", config.getBindAddress(), server.actualPort());
                startFuture.complete();
            } else {
                LOG.error("error while starting up adapter", done.cause());
                startFuture.fail(done.cause());
            }
        });
    }

    @Override
    public final void stop(Future<Void> stopFuture) throws Exception {
        try {
            preShutdown();
        } catch (Exception e) {
            LOG.error("error in preShutdown", e);
        }

        Future<Void> shutdownTracker = Future.future();
        shutdownTracker.setHandler(done -> {
            if (done.succeeded()) {
                LOG.info("adapter has been shut down successfully");
                stopFuture.complete();
            } else {
                LOG.info("error while shutting down adapter", done.cause());
                stopFuture.fail(done.cause());
            }
        });

        Future<Void> serverStopTracker = Future.future();
        if (server != null) {
            server.close(serverStopTracker.completer());
        } else {
            serverStopTracker.complete();
        }
        serverStopTracker.compose(v -> {
            Future<Void> honoClientStopTracker = Future.future();
            if (hono != null) {
                hono.shutdown(honoClientStopTracker.completer());
            } else {
                honoClientStopTracker.complete();
            }
            return honoClientStopTracker;
        }).compose(v -> {
            postShutdown(shutdownTracker);
        }, shutdownTracker);
    }

    /**
     * Invoked before the Http server is shut down.
     * May be overridden by sub-classes.
     */
    protected void preShutdown() {
        // empty
    }

    /**
     * Invoked after the Adapter has been shutdown successfully.
     * May be overridden by sub-classes to provide further shutdown handling.
     * 
     * @param stopFuture  a future that has to be completed when this operation is finished
     */
    protected void postShutdown(final Future<Void> stopFuture) {
        stopFuture.complete();
    }

    /**
     * Ends the given response with status code HTTP_BAD_REQUEST and the given message.
     * The content type of the message will be "text/plain".
     * 
     * @param response response
     * @param msg message to write in the response
     */
    protected static void badRequest(final HttpServerResponse response, final String msg) {
        badRequest(response, msg, "text/plain");
    }

    /**
     * Ends the given response with status code HTTP_BAD_REQUEST and the given message.
     *
     * @param response response
     * @param msg message to write in the response
     * @param contentType content type of the message
     */
    protected static void badRequest(final HttpServerResponse response, final String msg, final String contentType) {
        response
            .putHeader(HttpHeaders.CONTENT_TYPE, contentType)
            .setStatusCode(HTTP_BAD_REQUEST).end(msg);
    }

    /**
     * Ends the given response with status code HTTP_INTERNAL_ERROR and the given message.
     *
     * @param response response
     * @param msg message to write in the response
     */
    protected static void internalServerError(final HttpServerResponse response, final String msg) {
        response
            .setStatusCode(HTTP_INTERNAL_ERROR)
            .putHeader(HttpHeaders.CONTENT_TYPE, "text/plain; charset=utf-8")
            .end(msg);
    }

    /**
     * Ends the given response with status code HTTP_UNAVAILABLE and sets a RETRY_AFTER header with the given number of seconds.
     * 
     * @param response response
     * @param retryAfterSeconds number of seconds to set in the RETRY_AFTER header
     */
    protected static void serviceUnavailable(final HttpServerResponse response, final int retryAfterSeconds) {
        serviceUnavailable(response, retryAfterSeconds, null, null);
    }

    /**
     * Ends the given response with status code HTTP_UNAVAILABLE and sets a RETRY_AFTER header with the given number of seconds.
     * Additionally sets the given message and content type.
     * 
     * @param response response
     * @param retryAfterSeconds number of seconds to set in the RETRY_AFTER header
     * @param msg message to write in the response
     * @param contentType content type of the message
     */
    protected static void serviceUnavailable(final HttpServerResponse response, final int retryAfterSeconds,
            final String msg, final String contentType) {
        response
            .setStatusCode(HTTP_UNAVAILABLE)
            .putHeader(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds));
        if (msg != null) {
            response
                .putHeader(HttpHeaders.CONTENT_TYPE, contentType)
                .end(msg);
        } else {
            response.end();
        }
    }

    private void doGetStatus(final RoutingContext ctx) {
        JsonObject result = new JsonObject(hono.getConnectionStatus());
        result.put("active profiles", activeProfiles);
        result.put("senders", hono.getSenderStatus());
        adaptStatusResource(result);
        ctx.response()
            .putHeader(HttpHeaders.CONTENT_TYPE, CONTENT_TYPE_JSON)
            .end(result.encodePrettily());
    }

    /**
     * Adapts the JsonObject returned on a status request.
     * Subclasses can add their own properties here.
     * 
     * @param status status object to be adapted
     */
    protected void adaptStatusResource(final JsonObject status) {
        // empty
    }

    protected static String getContentType(final RoutingContext ctx) {
        return ctx.request().getHeader(HttpHeaders.CONTENT_TYPE);
    }

    /**
     * Uploads a telemetry message to the Hono server.
     * Payload and content type of the message are taken from the current request.
     *
     * @param ctx the request context
     * @param tenant tenant for the message
     * @param deviceId device id for the message
     */
    public final void uploadTelemetryMessage(final RoutingContext ctx, final String tenant, final String deviceId) {
        doUploadMessage(ctx, tenant, deviceId, telemetrySenderSupplier);
    }
    
    /**
     * Uploads a telemetry message to the Hono server.
     *
     * @param response the response in which the upload result will be set
     * @param tenant tenant for the message
     * @param deviceId device id for the message
     * @param payload payload for the message
     * @param contentType content type of the message payload
     */
    public final void uploadTelemetryMessage(final HttpServerResponse response, final String tenant, final String deviceId,
            final Buffer payload, final String contentType) {
        doUploadMessage(response, tenant, deviceId, payload, contentType, telemetrySenderSupplier);
    }

    /**
     * Uploads an event message to the Hono server.
     * Payload and content type of the message are taken from the current request.
     *
     * @param ctx the request context
     * @param tenant tenant for the message
     * @param deviceId device id for the message
     */
    public final void uploadEventMessage(final RoutingContext ctx, final String tenant, final String deviceId) {
        doUploadMessage(ctx, tenant, deviceId, eventSenderSupplier);
    }

    /**
     * Uploads an event message to the Hono server.
     * 
     * @param response the response in which the upload result will be set
     * @param tenant tenant for the message
     * @param deviceId device id for the message
     * @param payload payload for the message
     * @param contentType content type of the message payload 
     */
    public final void uploadEventMessage(final HttpServerResponse response, final String tenant, final String deviceId,
            final Buffer payload, final String contentType) {
        doUploadMessage(response, tenant, deviceId, payload, contentType, eventSenderSupplier);
    }

    private void doUploadMessage(final RoutingContext ctx, final String tenant, final String deviceId,
                                 final BiConsumer<String, Handler<AsyncResult<MessageSender>>> senderSupplier) {

        final String contentType = getContentType(ctx);
        if (contentType == null) {
            badRequest(ctx.response(), String.format("%s header is missing", HttpHeaders.CONTENT_TYPE));
        } else if (ctx.getBody().length() == 0) {
            badRequest(ctx.response(), "missing body");
        } else {
            doUploadMessage(ctx.response(), tenant, deviceId, ctx.getBody(), contentType, senderSupplier);
        }
    }
    
    private void doUploadMessage(final HttpServerResponse response, final String tenant, final String deviceId,
            final Buffer payload, final String contentType, final BiConsumer<String, Handler<AsyncResult<MessageSender>>> senderSupplier) {

        if (contentType == null) {
            badRequest(response, "content type is not set");
        } else if (payload == null || payload.length() == 0) {
            badRequest(response, "payload is empty");
        } else {
            senderSupplier.accept(tenant, createAttempt -> {
                if (createAttempt.succeeded()) {
                    final MessageSender sender = createAttempt.result();

                    sendToHono(response, deviceId, payload, contentType, sender);
                } else {
                    // we don't have a connection to Hono
                    serviceUnavailable(response, 5);
                }
            });
        }
    }

    private void sendToHono(final HttpServerResponse response, final String deviceId, final Buffer payload,
            final String contentType, final MessageSender sender) {

        boolean accepted = sender.send(deviceId, payload.getBytes(), contentType);
        if (accepted) {
            response.setStatusCode(HTTP_ACCEPTED).end();
        } else {
            // we currently have no credit for uploading data to Hono's Telemetry endpoint
            serviceUnavailable(response, 2,
                    "resource limit exceeded, please try again later",
                    "text/plain");
        }
    }

    private void connectToHono(final Handler<AsyncResult<HonoClient>> connectHandler) {

        ProtonClientOptions options = new ProtonClientOptions()
                .setReconnectAttempts(-1)
                .setReconnectInterval(200); // try to re-connect every 200 ms
        hono.connect(options, connectAttempt -> {
            if (connectHandler != null) {
                connectHandler.handle(connectAttempt);
            }
        });
    }
}
