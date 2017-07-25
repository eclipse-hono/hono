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

package org.eclipse.hono.service.http;

import static java.net.HttpURLConnection.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.eclipse.hono.config.ServiceConfigProperties;
import org.eclipse.hono.service.AbstractServiceBase;
import org.eclipse.hono.util.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.http.*;
import io.vertx.ext.healthchecks.HealthCheckHandler;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;

/**
 * A base class for implementing services using HTTP.
 *
 * @param <T> The type of configuration properties used by this service.
 */
public abstract class HttpServiceBase<T extends ServiceConfigProperties> extends AbstractServiceBase<T> {

    /**
     * The <em>application/json</em> content type.
     */
    protected static final String CONTENT_TYPE_JSON = "application/json";

    /**
     * The <em>application/json; charset=utf-8</em> content type.
     */
    protected static final String CONTENT_TYPE_JSON_UFT8 = "application/json; charset=utf-8";

    /**
     * Default file uploads directory used by Vert.x Web
     */
    protected static final String DEFAULT_UPLOADS_DIRECTORY = "/tmp";

    private static final Logger LOG = LoggerFactory.getLogger(HttpServiceBase.class);

    @Value("${spring.profiles.active:}")
    private String activeProfiles;

    private HttpServer server;
    private HttpServer insecureServer;

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
        LOG.debug("Bad request: {}", msg);
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
        LOG.debug("Internal server error: {}", msg);
        endWithStatus(response, HTTP_INTERNAL_ERROR, null, msg, null);
    }

    /**
     * Ends a response with HTTP status code 503 (Service Unavailable) and sets the <em>Retry-After</em> HTTP header to
     * a given number of seconds.
     *
     * @param response The HTTP response to write to.
     * @param retryAfterSeconds The number of seconds to set in the header.
     */
    protected static void serviceUnavailable(final HttpServerResponse response, final int retryAfterSeconds) {
        serviceUnavailable(response, retryAfterSeconds, null, null);
    }

    /**
     * Ends a response with HTTP status code 503 (Service Unavailable) and sets the <em>Retry-After</em> HTTP header to
     * a given number of seconds.
     *
     * @param response The HTTP response to write to.
     * @param retryAfterSeconds The number of seconds to set in the header.
     * @param detail The message to write to the response's body (may be {@code null}).
     * @param contentType The content type of the message (if {@code null}, then <em>text/plain</em> is used}.
     * @throws NullPointerException if response is {@code null}.
     */
    protected static void serviceUnavailable(final HttpServerResponse response, final int retryAfterSeconds,
                                             final String detail, final String contentType) {

        LOG.debug("Service unavailable: {}", detail);
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
            for (Map.Entry<CharSequence, CharSequence> header : headers.entrySet()) {
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

    /**
     * Gets the value of the <em>Content-Type</em> HTTP header for a request.
     *
     * @param ctx The routing context containing the HTTP request.
     * @return The content type or {@code null} if the request doesn't contain a <em>Content-Type</em> header.
     * @throws NullPointerException if context is {@code null}.
     */
    protected static String getContentType(final RoutingContext ctx) {

        return Objects.requireNonNull(ctx).request().getHeader(HttpHeaders.CONTENT_TYPE);
    }

    /**
     * The default port.
     *
     * Subclasses should override this method to provide a service specific default value.
     * <p>
     *
     * @return 8443
     */
    @Override
    public int getPortDefaultValue() {
        return 8443;
    }

    /**
     * The default insecure port.
     *
     * Subclasses should override this method to provide a service specific default value.
     * <p>
     *
     * @return 8080
     */
    @Override
    public int getInsecurePortDefaultValue() {
        return 8080;
    }

    @Override
    protected final int getActualPort() {
        return (server != null ? server.actualPort() : Constants.PORT_UNCONFIGURED);
    }

    @Override
    protected final int getActualInsecurePort() {
        return (insecureServer != null ? insecureServer.actualPort() : Constants.PORT_UNCONFIGURED);
    }

    /**
     * Invoked before the http server is started.
     * <p>
     * Subclasses may override this method to do any kind of initialization work.
     *
     * @return A future indicating the outcome of the operation. The start up process fails if the returned future
     *         fails.
     */
    protected Future<Void> preStartServers() {
        return Future.succeededFuture();
    }

    @Override
    protected final Future<Void> startInternal() {

        return preStartServers()
                .compose(s -> checkPortConfiguration())
                .compose(s -> {
                    Router router = createRouter();
                    if (router == null) {
                        return Future.failedFuture("no router configured");
                    } else {
                        addRoutes(router);
                        return CompositeFuture.all(bindSecureHttpServer(router), bindInsecureHttpServer(router));
                    }
                })
                .compose(s -> onStartupSuccess());
    }

    /**
     * Invoked after the http server has been started successfully.
     * <p>
     * May be overridden by sub-classes.
     *
     * @return A future indicating the outcome of the operation. The start up process fails if the returned future
     *         fails.
     */
    protected Future<Void> onStartupSuccess() {

        return Future.succeededFuture();
    }

    /**
     * Creates the router for handling requests.
     * <p>
     * This method creates a router instance with a default route limiting the body size of requests to the maximum
     * payload size set in the <em>config</em> properties.
     *
     * @return The newly created router (never {@code null}).
     */
    protected Router createRouter() {

        final Router router = Router.router(vertx);
        LOG.info("limiting size of inbound request body to {} bytes", getConfig().getMaxPayloadSize());
        router.route().handler(BodyHandler.create().setBodyLimit(getConfig().getMaxPayloadSize())
                .setUploadsDirectory(DEFAULT_UPLOADS_DIRECTORY));

        return router;
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
     * This method returns default options with the host and port being set to the corresponding values from the
     * <em>config</em> properties and using a maximum chunk size of 4096 bytes.
     *
     * @return The http server options.
     */
    protected HttpServerOptions getHttpServerOptions() {

        HttpServerOptions options = new HttpServerOptions();
        options.setHost(getConfig().getBindAddress()).setPort(getConfig().getPort(getPortDefaultValue()))
                .setMaxChunkSize(4096);
        addTlsKeyCertOptions(options);
        addTlsTrustOptions(options);
        return options;
    }

    /**
     * Gets the options to use for creating the insecure http server.
     * <p>
     * Subclasses may override this method in order to customize the server.
     * <p>
     * This method returns default options with the host and port being set to the corresponding values from the
     * <em>config</em> properties and using a maximum chunk size of 4096 bytes.
     *
     * @return The http server options.
     */
    protected HttpServerOptions getInsecureHttpServerOptions() {

        HttpServerOptions options = new HttpServerOptions();
        options.setHost(getConfig().getInsecurePortBindAddress())
                .setPort(getConfig().getInsecurePort(getInsecurePortDefaultValue())).setMaxChunkSize(4096);
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

    @Override
    protected final Future<Void> stopInternal() {

        return preShutdown()
                .compose(s -> stopServer())
                .compose(s -> stopInsecureServer())
                .compose(v -> postShutdown());
    }

    private Future<Void> stopServer() {
        Future<Void> serverStopTracker = Future.future();
        if (server != null) {
            server.close(serverStopTracker.completer());
        } else {
            serverStopTracker.complete();
        }
        return serverStopTracker;
    }

    private Future<Void> stopInsecureServer() {
        Future<Void> insecureServerStopTracker = Future.future();
        if (insecureServer != null) {
            insecureServer.close(insecureServerStopTracker.completer());
        } else {
            insecureServerStopTracker.complete();
        }
        return insecureServerStopTracker;
    }

    /**
     * Invoked before the http server is shut down.
     * <p>
     * Subclasses may override this method.
     *
     * @return A future indicating the outcome of the operation.
     */
    protected Future<Void> preShutdown() {
        return Future.succeededFuture();
    }

    /**
     * Invoked after the http server has been shutdown successfully.
     * <p>
     * May be overridden by sub-classes.
     *
     * @return A future that has to be completed when this operation is finished.
     */
    protected Future<Void> postShutdown() {
        return Future.succeededFuture();
    }

}
