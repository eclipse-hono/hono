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

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
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

    /**
     * The <em>application/json</em> content type.
     */
    protected static final String CONTENT_TYPE_JSON = "application/json";
    /**
     * The <em>application/json; charset=utf-8</em> content type.
     */
    protected static final String CONTENT_TYPE_JSON_UFT8 = "application/json; charset=utf-8";

    private static final Logger LOG = LoggerFactory.getLogger(AbstractVertxBasedHttpProtocolAdapter.class);

    @Value("${spring.profiles.active:}")
    private String activeProfiles;

    private HttpServer server;
    private HonoClient hono;
    private HonoConfigProperties config;

    private BiConsumer<String, Handler<AsyncResult<MessageSender>>> eventSenderSupplier;
    private BiConsumer<String, Handler<AsyncResult<MessageSender>>> telemetrySenderSupplier;

    /**
     * Sets the http server instance to use for this adapter.
     * <p>
     * If no server is set using this method, then a server instance is created during
     * startup of this adapter based on the <em>config</em> properties and the server options
     * returned by the <em>getHttpServerOptions</em> method.
     * 
     * @param server The http server.
     * @throws NullPointerException if server is {@code null}.
     * @throws IllegalArgumentException if the server is already started and listening on an address/port.
     */
    @Autowired(required = false)
    public final void setHttpServer(final HttpServer server) {
        Objects.requireNonNull(server);
        if (server.actualPort() > 0) {
            throw new IllegalArgumentException("http server must not be started already");
        } else {
            this.server = server;
        }
    }

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

    /**
     * Gets the client for interacting with the Hono server.
     * 
     * @return The client.
     */
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

    /**
     * Gets the properties of the Hono server this adapter is interacting with.
     * 
     * @return The configuration properties.
     */
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
                Future<Void> httpServerTracker = Future.future();
                Router router = createRouter();
                if (router == null) {
                    httpServerTracker.fail("no router configured");
                } else {
                    addRoutes(router);
                    bindHttpServer(router, httpServerTracker);
                    connectToHono(null);
                }
                return httpServerTracker; 
            }).compose(ar -> {
                try {
                    onStartupSuccess();
                } catch (Exception e) {
                    LOG.error("error in onStartupSuccess", e);
                }
                startFuture.complete();
            }, startFuture);
        }
    }

    /**
     * Invoked before the http server is started.
     * <p>
     * May be overridden by sub-classes to provide additional startup handling.
     * 
     * @param startFuture The future that needs to complete for the startup process to proceed. If this future
     *                    is failed, the adapter does not start up.
     */
    protected void preStartup(final Future<Void> startFuture) {

        startFuture.complete();
    }

    /**
     * Invoked after this adapter has started up successfully.
     * <p>
     * May be overridden by sub-classes.
     */
    protected void onStartupSuccess() {
        // empty
    }

    /**
     * Creates the router for handling requests.
     * <p>
     * This method creates a router instance with the following routes:
     * <ol>
     * <li>A default route limiting the body size of requests to the maximum payload size set in the
     * <em>config</em> properties.</li>
     * <li>A route for retrieving this adapter's current status from the resource path returned by
     * {@link #getStatusResourcePath()} (if not {@code null}).</li>
     * </ol>
     * 
     * @return The newly created router (never {@code null}).
     */
    protected Router createRouter() {

        final Router router = Router.router(vertx);
        LOG.info("limiting size of inbound request body to {} bytes", config.getMaxPayloadSize());
        router.route().handler(BodyHandler.create().setBodyLimit(config.getMaxPayloadSize()));

        String statusResourcePath = getStatusResourcePath();
        if (statusResourcePath != null) {
            router.route(HttpMethod.GET, statusResourcePath).handler(this::doGetStatus);
        }

        return router;
    }

    /**
     * Returns the path for the status resource.
     * <p>
     * By default, this method returns {@code /status}.
     * Subclasses may override this method to return a different path or {@code null},
     * in which case the status resource will be disabled.
     * 
     * @return The resource path or {@code null}.
     */
    protected String getStatusResourcePath() {
        return "/status";
    }

    /**
     * Adds custom routes for handling requests.
     * <p>
     * This method is invoked right before the http server is started with the value returned by
     * {@link AbstractVertxBasedHttpProtocolAdapter#createRouter()}.
     * 
     * @param router The router to add the custom routes to.
     */
    protected abstract void addRoutes(final Router router);

    /**
     * Gets the options to use for creating the http server.
     * <p>
     * Subclasses may override this method in order to customize the http server.
     * <p>
     * This method returns default options with the host and port being set to the corresponding values
     * from the <em>config</em> properties and using a maximum chunk size of 4096 bytes.
     * 
     * @return The http server options.
     */
    protected HttpServerOptions getHttpServerOptions() {

        HttpServerOptions options = new HttpServerOptions();
        options.setHost(config.getBindAddress()).setPort(config.getPort()).setMaxChunkSize(4096);
        return options;
    }

    private void bindHttpServer(final Router router, final Future<Void> startFuture) {

        if (server == null) {
            server = vertx.createHttpServer(getHttpServerOptions());
        }
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
     * Ends a response with HTTP status code 400 (Bad Request) and an optional message.
     * <p>
     * The content type of the message will be <em>text/plain</em>.
     * 
     * @param response The HTTP response to write to.
     * @param msg The message to write to the response's body (may be {@code null}).
     * @throws NullPointerException if response is {@code null}.
     */
    protected static void badRequest(final HttpServerResponse response, final String msg) {
        badRequest(response, msg, null);
    }

    /**
     * Ends a response with HTTP status code 400 (Bad Request) and an optional message.
     *
     * @param response The HTTP response to write to.
     * @param msg The message to write to the response's body (may be {@code null}).
     * @param contentType The content type of the message (if {@code null}, then <em>text/plain</em> is used}.
     * @throws NullPointerException if response is {@code null}.
     */
    protected static void badRequest(final HttpServerResponse response, final String msg, final String contentType) {

        endWithStatus(response, HTTP_BAD_REQUEST, null, msg, contentType);
    }

    /**
     * Ends a response with HTTP status code 500 (Internal Error) and an optional message.
     * <p>
     * The content type of the message will be <em>text/plain</em>.
     *
     * @param response The HTTP response to write to.
     * @param msg The message to write to the response's body (may be {@code null}).
     * @throws NullPointerException if response is {@code null}.
     */
    protected static void internalServerError(final HttpServerResponse response, final String msg) {

        endWithStatus(response, HTTP_INTERNAL_ERROR, null, msg, null);
    }

    /**
     * Ends a response with HTTP status code 503 (Service Unavailable) and sets the <em>Retry-After</em> HTTP header
     * to a given number of seconds.
     * 
     * @param response The HTTP response to write to.
     * @param retryAfterSeconds The number of seconds to set in the header.
     */
    protected static void serviceUnavailable(final HttpServerResponse response, final int retryAfterSeconds) {
        serviceUnavailable(response, retryAfterSeconds, null, null);
    }

    /**
     * Ends a response with HTTP status code 503 (Service Unavailable) and sets the <em>Retry-After</em> HTTP header
     * to a given number of seconds.
     * 
     * @param response The HTTP response to write to.
     * @param retryAfterSeconds The number of seconds to set in the header.
     * @param detail The message to write to the response's body (may be {@code null}).
     * @param contentType The content type of the message (if {@code null}, then <em>text/plain</em> is used}.
     * @throws NullPointerException if response is {@code null}.
     */
    protected static void serviceUnavailable(final HttpServerResponse response, final int retryAfterSeconds,
            final String detail, final String contentType) {

        Map<CharSequence, CharSequence> headers = new HashMap<>(2);
        headers.put(HttpHeaders.CONTENT_TYPE, contentType != null ? contentType : "text/plain");
        headers.put(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds));
        endWithStatus(response, HTTP_UNAVAILABLE, headers, detail, contentType);
    }

    /**
     * Ends a response with a given HTTP status code and detail message.
     *
     * @param response The HTTP response to write to.
     * @param status The status code to write to the response.
     * @param headers HTTP headers to set on the response (may be {@code null}).
     * @param detail The message to write to the response's body (may be {@code null}).
     * @param contentType The content type of the message (if {@code null}, then <em>text/plain</em> is used}.
     * @throws NullPointerException if response is {@code null}.
     */
    protected static void endWithStatus(final HttpServerResponse response, final int status,
            Map<CharSequence, CharSequence> headers, final String detail, final String contentType) {

        Objects.requireNonNull(response);
        response.setStatusCode(status);
        if (headers != null) {
            for (Entry<CharSequence, CharSequence> header : headers.entrySet()) {
                response.putHeader(header.getKey(), header.getValue());
            }
        }
        if (detail != null) {
            if (contentType != null) {
                response.putHeader(HttpHeaders.CONTENT_TYPE, contentType);
            } else {
                response.putHeader(HttpHeaders.CONTENT_TYPE, "text/plain");
            }
            response.end(detail);
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

    /**
     * Gets the value of the <em>Content-Type</em> HTTP header for a request.
     * 
     * @param ctx The routing context containing the HTTP request.
     * @return The content type or {@code null} if the request doesn't contain a
     *         <em>Content-Type</em> header.
     * @throws NullPointerException if context is {@code null}.
     */
    protected static String getContentType(final RoutingContext ctx) {

        return Objects.requireNonNull(ctx).request().getHeader(HttpHeaders.CONTENT_TYPE);
    }

    /**
     * Uploads the body of an HTTP request as a telemetry message to the Hono server.
     * <p>
     * This method simply invokes {@link #uploadTelemetryMessage(HttpServerResponse, String, String, Buffer, String)}
     * with objects retrieved from the routing context.
     *
     * @param ctx The context to retrieve the message payload and content type from.
     * @param tenant The tenant of the device that has produced the data.
     * @param deviceId The id of the device that has produced the data.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    public final void uploadTelemetryMessage(final RoutingContext ctx, final String tenant, final String deviceId) {

        uploadTelemetryMessage(
                Objects.requireNonNull(ctx).response(),
                Objects.requireNonNull(tenant),
                Objects.requireNonNull(deviceId),
                ctx.getBody(),
                getContentType(ctx));
    }

    /**
     * Uploads a telemetry message to the Hono server.
     * <p>
     * Depending on the outcome of the attempt to upload the message to Hono, the HTTP response's code is
     * set as follows:
     * <ul>
     * <li>202 (Accepted) - if the telemetry message has been sent to the Hono server.</li>
     * <li>400 (Bad Request) - if the message payload is {@code null} or empty or if the content type is {@code null}.</li>
     * <li>503 (Service Unavailable) - if the message could not be sent to the Hono server, e.g. due to lack of connection or credit.</li>
     * </ul>
     * 
     * @param response The HTTP response to write the result of the operation to.
     * @param tenant The tenant of the device that has produced the data.
     * @param deviceId The id of the device that has produced the data.
     * @param payload The message payload to send.
     * @param contentType The content type of the message payload.
     * @throws NullPointerException if any of response, tenant or device ID is {@code null}.
     */
    public final void uploadTelemetryMessage(final HttpServerResponse response, final String tenant, final String deviceId,
            final Buffer payload, final String contentType) {

        doUploadMessage(
                Objects.requireNonNull(response),
                Objects.requireNonNull(tenant),
                Objects.requireNonNull(deviceId),
                payload,
                contentType,
                telemetrySenderSupplier);
    }

    /**
     * Uploads the body of an HTTP request as an event message to the Hono server.
     * <p>
     * This method simply invokes {@link #uploadEventMessage(HttpServerResponse, String, String, Buffer, String)}
     * with objects retrieved from the routing context.
     *
     * @param ctx The context to retrieve the message payload and content type from.
     * @param tenant The tenant of the device that has produced the data.
     * @param deviceId The id of the device that has produced the data.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    public final void uploadEventMessage(final RoutingContext ctx, final String tenant, final String deviceId) {

        uploadEventMessage(
                Objects.requireNonNull(ctx).response(),
                Objects.requireNonNull(tenant),
                Objects.requireNonNull(deviceId),
                ctx.getBody(),
                getContentType(ctx));
    }

    /**
     * Uploads an event message to the Hono server.
     * <p>
     * Depending on the outcome of the attempt to upload the message to Hono, the HTTP response's code is
     * set as follows:
     * <ul>
     * <li>202 (Accepted) - if the telemetry message has been sent to the Hono server.</li>
     * <li>400 (Bad Request) - if the message payload is {@code null} or empty or if the content type is {@code null}.</li>
     * <li>503 (Service Unavailable) - if the message could not be sent to the Hono server, e.g. due to lack of connection or credit.</li>
     * </ul>
     * 
     * @param response The HTTP response to write the result of the operation to.
     * @param tenant The tenant of the device that has produced the data.
     * @param deviceId The id of the device that has produced the data.
     * @param payload The message payload to send.
     * @param contentType The content type of the message payload.
     * @throws NullPointerException if any of response, tenant or device ID is {@code null}.
     */
    public final void uploadEventMessage(final HttpServerResponse response, final String tenant, final String deviceId,
            final Buffer payload, final String contentType) {

        doUploadMessage(
                Objects.requireNonNull(response),
                Objects.requireNonNull(tenant),
                Objects.requireNonNull(deviceId),
                payload,
                contentType,
                eventSenderSupplier);
    }

    private void doUploadMessage(final HttpServerResponse response, final String tenant, final String deviceId,
            final Buffer payload, final String contentType, final BiConsumer<String, Handler<AsyncResult<MessageSender>>> senderSupplier) {

        if (contentType == null) {
            badRequest(response, String.format("%s header is missing", HttpHeaders.CONTENT_TYPE));
        } else if (payload == null || payload.length() == 0) {
            badRequest(response, "missing body");
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
