/*******************************************************************************
 * Copyright (c) 2016, 2020 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/

package org.eclipse.hono.service.http;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.eclipse.hono.config.ServiceConfigProperties;
import org.eclipse.hono.service.AbstractServiceBase;
import org.eclipse.hono.util.Constants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import io.opentracing.tag.Tags;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.ext.healthchecks.HealthCheckHandler;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.AuthHandler;
import io.vertx.ext.web.handler.BodyHandler;

/**
 * A base class for implementing services using HTTP.
 *
 * @param <T> The type of configuration properties used by this service.
 */
public abstract class HttpServiceBase<T extends ServiceConfigProperties> extends AbstractServiceBase<T> {

    /**
     * Default file uploads directory used by Vert.x Web.
     */
    protected static final String DEFAULT_UPLOADS_DIRECTORY = "/tmp";

    private final Map<String, HttpEndpoint> endpoints = new HashMap<>();

    private HttpServer server;
    private HttpServer insecureServer;

    /**
     * Auth handler to be used on the routes.
     */
    private AuthHandler authHandler;

    /**
     * Adds multiple endpoints to this server.
     *
     * @param definedEndpoints The endpoints.
     */
    @Autowired(required = false)
    public final void addEndpoints(final Set<HttpEndpoint> definedEndpoints) {
        Objects.requireNonNull(definedEndpoints);
        for (final HttpEndpoint ep : definedEndpoints) {
            addEndpoint(ep);
        }
    }

    /**
     * Adds an endpoint to this server.
     *
     * @param ep The endpoint.
     */
    public final void addEndpoint(final HttpEndpoint ep) {
        if (endpoints.putIfAbsent(ep.getName(), ep) != null) {
            log.warn("multiple endpoints defined with name [{}]", ep.getName());
        } else {
            log.debug("registering endpoint [{}]", ep.getName());
        }
    }

    /**
     * Iterates over the endpoints registered with this service.
     *
     * @return The endpoints.
     */
    protected final Iterable<HttpEndpoint> endpoints() {
        return endpoints.values();
    }

    /**
     * Sets auth handler.
     *
     * @param authHandler The handler.
     */
    @Autowired(required = false)
    public void setAuthHandler(final AuthHandler authHandler) {
        this.authHandler = authHandler;
    }

    @Autowired
    @Qualifier(Constants.QUALIFIER_HTTP)
    @Override
    public final void setConfig(final T configuration) {
        setSpecificConfig(configuration);
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
        return server != null ? server.actualPort() : Constants.PORT_UNCONFIGURED;
    }

    @Override
    protected final int getActualInsecurePort() {
        return insecureServer != null ? insecureServer.actualPort() : Constants.PORT_UNCONFIGURED;
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
                .compose(s -> startEndpoints())
                .compose(router -> {
                     return CompositeFuture.all(bindSecureHttpServer(router), bindInsecureHttpServer(router));
                }).compose(s -> onStartupSuccess());
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
     * This method creates a router instance along with a route matching all request. That route is initialized with the
     * following handlers and failure handlers:
     * <ul>
     * <li>a handler and failure handler that creates tracing data for all server requests,</li>
     * <li>a default failure handler,</li>
     * <li>a handler limiting the body size of requests to the maximum payload size set in the <em>config</em>
     * properties.</li>
     * </ul>
     *
     * @return The newly created router (never {@code null}).
     */
    protected Router createRouter() {

        final Router router = Router.router(vertx);
        final Route matchAllRoute = router.route();
        // the handlers and failure handlers are added here in a specific order!
        // 1. tracing handler
        final TracingHandler tracingHandler = createTracingHandler();
        matchAllRoute.handler(tracingHandler).failureHandler(tracingHandler);
        // 2. default handler for failed routes
        matchAllRoute.failureHandler(new DefaultFailureHandler());
        // 3. BodyHandler with request size limit
        log.info("limiting size of inbound request body to {} bytes", getConfig().getMaxPayloadSize());
        matchAllRoute.handler(BodyHandler.create().setUploadsDirectory(DEFAULT_UPLOADS_DIRECTORY)
                .setBodyLimit(getConfig().getMaxPayloadSize()));
        //4. AuthHandler
        addAuthHandler(router);
        return router;
    }

    private TracingHandler createTracingHandler() {
        final Map<String, String> customTags = new HashMap<>();
        customTags.put(Tags.COMPONENT.getKey(), getClass().getSimpleName());
        addCustomTags(customTags);
        final List<WebSpanDecorator> decorators = Collections.singletonList(new ComponentMetaDataDecorator(customTags));
        return new TracingHandler(tracer, decorators);
    }

    /**
     * Add authentication handler to the router if needed.
     *
     * @param router The router.
     */
    protected void addAuthHandler(final Router router) {
        if (authHandler != null) {
            final Route matchAllRoute = router.route();
            matchAllRoute.handler(authHandler);
        }
    }

    /**
     * Adds meta data about this service to be included in OpenTracing
     * spans that are used for tracing requests handled by this service.
     * <p>
     * This method is empty by default.
     *
     * @param customTags The existing custom tags to add to. The map will already
     *                 include this service's simple class name under key {@link Tags#COMPONENT}.
     */
    protected void addCustomTags(final Map<String, String> customTags) {
        // empty by default
    }

    private void addEndpointRoutes(final Router router) {
        for (final HttpEndpoint ep : endpoints()) {
            ep.addRoutes(router);
        }
    }

    /**
     * Adds custom routes for handling requests that are not handled by the registered endpoints.
     * <p>
     * This method is invoked right before the http server is started with the value returned by
     * {@link #createRouter()}.
     * <p>
     * This default implementation does not register any routes. Subclasses should override this
     * method in order to register any routes in addition to the ones added by the endpoints.
     *
     * @param router The router to add the custom routes to.
     */
    protected void addCustomRoutes(final Router router) {
        // empty default implementation
    }

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

        final HttpServerOptions options = new HttpServerOptions();
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

        final HttpServerOptions options = new HttpServerOptions();
        options.setHost(getConfig().getInsecurePortBindAddress())
                .setPort(getConfig().getInsecurePort(getInsecurePortDefaultValue())).setMaxChunkSize(4096);
        return options;
    }

    private Future<HttpServer> bindSecureHttpServer(final Router router) {

        if (isSecurePortEnabled()) {
            final Promise<HttpServer> result = Promise.promise();
            final String bindAddress = server == null ? getConfig().getBindAddress() : "?";
            if (server == null) {
                server = vertx.createHttpServer(getHttpServerOptions());
            }
            server.requestHandler(router).listen(bindAttempt -> {
                if (bindAttempt.succeeded()) {
                    if (getPort() == getPortDefaultValue()) {
                        log.info("server listens on standard secure port [{}:{}]", bindAddress, server.actualPort());
                    } else {
                        log.warn("server listens on non-standard secure port [{}:{}], default is {}", bindAddress,
                                server.actualPort(), getPortDefaultValue());
                    }
                    result.complete(bindAttempt.result());
                } else {
                    log.error("cannot bind to secure port", bindAttempt.cause());
                    result.fail(bindAttempt.cause());
                }
            });
            return result.future();
        } else {
            return Future.succeededFuture();
        }
    }

    private Future<HttpServer> bindInsecureHttpServer(final Router router) {

        if (isInsecurePortEnabled()) {
            final Promise<HttpServer> result = Promise.promise();
            final String bindAddress = insecureServer == null ? getConfig().getInsecurePortBindAddress() : "?";
            if (insecureServer == null) {
                insecureServer = vertx.createHttpServer(getInsecureHttpServerOptions());
            }
            insecureServer.requestHandler(router).listen(bindAttempt -> {
                if (bindAttempt.succeeded()) {
                    if (getInsecurePort() == getInsecurePortDefaultValue()) {
                        log.info("server listens on standard insecure port [{}:{}]", bindAddress, insecureServer.actualPort());
                    } else {
                        log.warn("server listens on non-standard insecure port [{}:{}], default is {}", bindAddress,
                                insecureServer.actualPort(), getInsecurePortDefaultValue());
                    }
                    result.complete(bindAttempt.result());
                } else {
                    log.error("cannot bind to insecure port", bindAttempt.cause());
                    result.fail(bindAttempt.cause());
                }
            });
            return result.future();
        } else {
            return Future.succeededFuture();
        }
    }

    private Future<Router> startEndpoints() {

        final Promise<Router> startPromise = Promise.promise();
        final Router router = createRouter();
        if (router == null) {
            startPromise.fail("no router configured");
        } else {
            addEndpointRoutes(router);
            addCustomRoutes(router);
            @SuppressWarnings("rawtypes")
            final
            List<Future> endpointFutures = new ArrayList<>(endpoints.size());
            for (final HttpEndpoint ep : endpoints()) {
                log.info("starting endpoint [name: {}, class: {}]", ep.getName(), ep.getClass().getName());
                endpointFutures.add(ep.start());
            }
            CompositeFuture.all(endpointFutures).onComplete(startup -> {
                if (startup.succeeded()) {
                    startPromise.complete(router);
                } else {
                    startPromise.fail(startup.cause());
                }
            });
        }
        return startPromise.future();
    }

    private Future<Void> stopEndpoints() {

        final Promise<Void> stopPromise = Promise.promise();
        @SuppressWarnings("rawtypes")
        final
        List<Future> endpointFutures = new ArrayList<>(endpoints.size());
        for (final HttpEndpoint ep : endpoints()) {
            log.info("stopping endpoint [name: {}, class: {}]", ep.getName(), ep.getClass().getName());
            endpointFutures.add(ep.stop());
        }
        CompositeFuture.all(endpointFutures).onComplete(shutdown -> {
            if (shutdown.succeeded()) {
                stopPromise.complete();
            } else {
                stopPromise.fail(shutdown.cause());
            }
        });
        return stopPromise.future();

    }

    @Override
    protected final Future<Void> stopInternal() {

        return preShutdown()
                .compose(s -> {
                    return CompositeFuture.all(stopServer(), stopInsecureServer());
                })
                .compose(s -> stopEndpoints())
                .compose(v -> postShutdown());
    }

    private Future<Void> stopServer() {
        final Promise<Void> serverStopTracker = Promise.promise();
        if (server != null) {
            log.info("stopping secure HTTP server [{}:{}]", getBindAddress(), getActualPort());
            server.close(serverStopTracker);
        } else {
            serverStopTracker.complete();
        }
        return serverStopTracker.future();
    }

    private Future<Void> stopInsecureServer() {
        final Promise<Void> insecureServerStopTracker = Promise.promise();
        if (insecureServer != null) {
            log.info("stopping insecure HTTP server [{}:{}]", getInsecurePortBindAddress(), getActualInsecurePort());
            insecureServer.close(insecureServerStopTracker);
        } else {
            insecureServerStopTracker.complete();
        }
        return insecureServerStopTracker.future();
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

    /**
     * Iterates over all endpoints and registers their readiness checks with the handler.
     * <p>
     * Subclasses may override this method in order to register other/additional checks.
     *
     * @param handler The handler to register the checks with.
     */
    @Override
    public void registerReadinessChecks(final HealthCheckHandler handler) {
        for (final HttpEndpoint ep : endpoints()) {
            ep.registerReadinessChecks(handler);
        }
    }

    /**
     * Iterates over all endpoints and registers their liveness checks with the handler.
     * <p>
     * Subclasses may override this method in order to register other/additional checks.
     *
     * @param handler The handler to register the checks with.
     */
    @Override
    public void registerLivenessChecks(final HealthCheckHandler handler) {
        for (final HttpEndpoint ep : endpoints()) {
            ep.registerLivenessChecks(handler);
        }
    }
}
