/**
 * Copyright (c) 2016 Bosch Software Innovations GmbH.
 * <p>
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * <p>
 * Contributors:
 * Bosch Software Innovations GmbH - initial creation
 */

package org.eclipse.hono.service.registration;

import static java.net.HttpURLConnection.HTTP_INTERNAL_ERROR;
import static java.net.HttpURLConnection.HTTP_OK;
import static org.eclipse.hono.util.RegistrationConstants.ACTION_GET;

import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;

import org.eclipse.hono.config.ServiceConfigProperties;
import org.eclipse.hono.util.RegistrationConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.http.*;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;

/**
 * A server that provides a REST interface to register devices. It's parent additionally provides the
 * <a href="https://www.eclipse.org/hono/api/Device-Registration-API/">Device Registration API</a> and
 * <a href="https://www.eclipse.org/hono/api/Credentials-API/">Credentials API</a> by means of AMQP.
 * <p>
 * It contains code copied from {@code VertxBasedRestProtocolAdapter}.
 * This is only a transition step to keep changes small.
 * Currently only the "GET" operation is implemented.
 *
 * TODO: add all operations of the Registration API.
 * TODO: refactor to remove code duplication.
 */
public class BaseDeviceRegistryRestServer<T extends ServiceConfigProperties> extends BaseDeviceRegistryServer<T> {

    private static final Logger LOG = LoggerFactory.getLogger(BaseDeviceRegistryRestServer.class);

    private static final String PARAM_TENANT    = "tenant";
    private static final String PARAM_DEVICE_ID = "device_id";

    private AbstractVertxBasedHttpServer vertxBasedHttpServer;

    @Override
    protected Future<Void> preStartServers() {
        vertxBasedHttpServer = new AbstractVertxBasedHttpServer() {

            @Override
            protected void addRoutes(Router router) {
                addRegistrationApiRoutes(router);
            }
        };
        return vertxBasedHttpServer.doStart();
    }

    private void addRegistrationApiRoutes(final Router router) {
        router.route(HttpMethod.GET, String.format("/registration/:%s/:%s", PARAM_TENANT, PARAM_DEVICE_ID))
                .handler(this::doGetDevice);
    }

    private static String getTenantParam(final RoutingContext ctx) {
        return ctx.request().getParam(PARAM_TENANT);
    }

    private static String getDeviceIdParam(final RoutingContext ctx) {
        return ctx.request().getParam(PARAM_DEVICE_ID);
    }

    private void doGetDevice(final RoutingContext ctx) {

        final String deviceId = getDeviceIdParam(ctx);
        final String tenantId = getTenantParam(ctx);

        final JsonObject requestMsg = RegistrationConstants.getServiceRequestAsJson(ACTION_GET, tenantId, deviceId);

        vertx.eventBus().send(RegistrationConstants.EVENT_BUS_ADDRESS_REGISTRATION_IN, requestMsg,
                result -> {
                    HttpServerResponse response = ctx.response();
                    if (result.failed()) {
                        vertxBasedHttpServer.internalServerError(response, "could not get device");
                    } else {
                        JsonObject registrationResult = (JsonObject) result.result().body();
                        Integer status = Integer.valueOf(registrationResult.getString("status"));
                        response.setStatusCode(status);
                        switch (status) {
                        case HTTP_OK:
                            String msg = registrationResult.encodePrettily();
                            response
                                    .putHeader(HttpHeaders.CONTENT_TYPE, AbstractVertxBasedHttpServer.CONTENT_TYPE_JSON_UFT8)
                                    .putHeader(HttpHeaders.CONTENT_LENGTH, String.valueOf(msg.length()))
                                    .write(msg);
                        default:
                            response.end();
                        }
                    }
                });
    }

    /**
     * Provides an internal HTTP server. 
     * To start it invoke {@link #doStart()}.
     * <p>
     * It contains code copied from {@code AbstractVertxBasedHttpProtocolAdapter}.
     * This is only a transition step to keep changes small.
     * A solution is needed for components that provide AMQP <em>and</em> HTTP APIs.
     */
    private abstract class AbstractVertxBasedHttpServer {

        // TODO: introduce configuration properties for HTTP server later.
        private static final int TMP_HARDCODED_HTTP_PORT = 8432;

        // TODO: introduce configuration properties for HTTPS server later.
        private static final int TMP_HARDCODED_HTTPS_PORT = 8431;


        /**
         * The <em>application/json; charset=utf-8</em> content type.
         */
        protected static final String CONTENT_TYPE_JSON_UFT8 = "application/json; charset=utf-8";

        /**
         * Default file uploads directory used by Vert.x Web
         */
        protected static final String DEFAULT_UPLOADS_DIRECTORY = "/tmp";


        private HttpServer server;
        private HttpServer insecureServer;

        /**
         * Starts the HTTP server.
         *
         * @return A future indicating the outcome of the operation.
         */
        public final Future<Void> doStart() {
            Future<Void> startFuture = Future.future();

            checkPortConfiguration()
                    .compose(s -> {
                        Router router = createRouter();
                        if (router == null) {
                            return Future.failedFuture("no router configured");
                        } else {
                            addRoutes(router);
                            return CompositeFuture.all(bindSecureHttpServer(router), bindInsecureHttpServer(router));
                        }
                    })
                    .compose(s -> {
                        try {
                            startFuture.complete();
                        } catch (Exception e) {
                            LOG.error("error in onStartupSuccess", e);
                            startFuture.fail(e);
                        }
                    }, startFuture);
            return startFuture;
        }

        /**
         * Creates the router for handling requests.
         * <p>
         * This method creates a router instance with the following routes:
         * <ol>
         * <li>A default route limiting the body size of requests to the maximum payload size set in the <em>config</em> properties.</li>
         * <li>A route for retrieving this adapter's current status from the resource path returned by
         * {@link #getStatusResourcePath()} (if not {@code null}).</li>
         * </ol>
         *
         * @return The newly created router (never {@code null}).
         */
        protected Router createRouter() {

            final Router router = Router.router(vertx);
            LOG.info("limiting size of inbound request body to {} bytes", getConfig().getMaxPayloadSize());
            router.route().handler(BodyHandler.create().setBodyLimit(getConfig().getMaxPayloadSize()).setUploadsDirectory(DEFAULT_UPLOADS_DIRECTORY));

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
         * {@link #createRouter()}.
         *
         * @param router The router to add the custom routes to.
         */
        protected abstract void addRoutes(final Router router);
    
        /**
         * Gets the options to use for creating the TLS secured http server.
         * <p>
         * Subclasses may override this method in order to customize the server.
         * <p>
         * This method returns default options with the host and port being set to the corresponding values
         * from the <em>config</em> properties and using a maximum chunk size of 4096 bytes.
         *
         * @return The http server options.
         */
        protected HttpServerOptions getHttpServerOptions() {

            HttpServerOptions options = new HttpServerOptions();
            options.setHost(getConfig().getBindAddress()).setPort(TMP_HARDCODED_HTTPS_PORT).setMaxChunkSize(4096);
            addTlsKeyCertOptions(options);
            addTlsTrustOptions(options);
            return options;
        }

        /**
         * Gets the options to use for creating the insecure http server.
         * <p>
         * Subclasses may override this method in order to customize the server.
         * <p>
         * This method returns default options with the host and port being set to the corresponding values
         * from the <em>config</em> properties and using a maximum chunk size of 4096 bytes.
         *
         * @return The http server options.
         */
        protected HttpServerOptions getInsecureHttpServerOptions() {

            HttpServerOptions options = new HttpServerOptions();
            options.setHost(getConfig().getInsecurePortBindAddress()).setPort(TMP_HARDCODED_HTTP_PORT).setMaxChunkSize(4096);
            return options;
        }

        private Future<HttpServer> bindSecureHttpServer(final Router router) {

            if (isSecurePortEnabled()) {
                Future<HttpServer> result = Future.future();
                final String bindAddress = server == null ? getConfig().getBindAddress() : "?";
                if (server == null) {
                    server = vertx.createHttpServer(getHttpServerOptions());
                }
                server.requestHandler(router::accept).listen(done -> {
                    if (done.succeeded()) {
                        LOG.info("secure http server listening on {}:{}", bindAddress, server.actualPort());
                        result.complete(done.result());
                    } else {
                        LOG.error("error while starting up secure http server", done.cause());
                        result.fail(done.cause());
                    }
                });
                return result;
            } else {
                return Future.succeededFuture();
            }
        }

        private Future<HttpServer> bindInsecureHttpServer(final Router router) {

            if (isInsecurePortEnabled()) {
                Future<HttpServer> result = Future.future();
                final String bindAddress = insecureServer == null ? getConfig().getInsecurePortBindAddress() : "?";
                if (insecureServer == null) {
                    insecureServer = vertx.createHttpServer(getInsecureHttpServerOptions());
                }
                insecureServer.requestHandler(router::accept).listen(done -> {
                    if (done.succeeded()) {
                        LOG.info("insecure http server listening on {}:{}", bindAddress, insecureServer.actualPort());
                        result.complete(done.result());
                    } else {
                        LOG.error("error while starting up insecure http server", done.cause());
                        result.fail(done.cause());
                    }
                });
                return result;
            } else {
                return Future.succeededFuture();
            }
        }

        public final void doStop(Future<Void> stopFuture) {

            Future<Void> shutdownTracker = Future.future();
            shutdownTracker.setHandler(done -> {
                if (done.succeeded()) {
                    LOG.info("HTTP adapter has been shut down successfully");
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

            Future<Void> insecureServerStopTracker = Future.future();
            if (insecureServer != null) {
                insecureServer.close(insecureServerStopTracker.completer());
            } else {
                insecureServerStopTracker.complete();
            }

            CompositeFuture.all(serverStopTracker, insecureServerStopTracker)
                    .compose(s -> shutdownTracker.complete(), shutdownTracker);
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
        protected void internalServerError(final HttpServerResponse response, final String msg) {
            LOG.debug("Internal server error: {}", msg);
            endWithStatus(response, HTTP_INTERNAL_ERROR, null, msg, null);
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
        protected void endWithStatus(final HttpServerResponse response, final int status,
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
            // TODO: add status
        }

    }
}
