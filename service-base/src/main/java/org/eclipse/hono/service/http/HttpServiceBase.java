/*******************************************************************************
 * Copyright (c) 2016, 2023 Contributors to the Eclipse Foundation
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.eclipse.hono.service.AbstractServiceBase;
import org.eclipse.hono.util.Constants;

import io.opentracing.tag.Tags;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.ext.healthchecks.HealthCheckHandler;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.AuthenticationHandler;

/**
 * A base class for implementing services using HTTP.
 *
 * @param <T> The type of configuration properties used by this service.
 */
public abstract class HttpServiceBase<T extends HttpServiceConfigProperties> extends AbstractServiceBase<T> {

    private static final String MATCH_ALL_ROUTE_NAME = "/*";

    private static final String KEY_MATCH_ALL_ROUTE_APPLIED = "matchAllRouteApplied";

    private final Map<String, HttpEndpoint> endpoints = new HashMap<>();

    private HttpServer server;
    private HttpServer insecureServer;

    /**
     * Auth handler to be used on the routes.
     */
    private AuthenticationHandler authHandler;

    /**
     * Adds multiple endpoints to this server.
     *
     * @param definedEndpoints The endpoints.
     */
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
     * Sets authentication handler.
     *
     * @param authHandler The handler.
     */
    public void setAuthHandler(final AuthenticationHandler authHandler) {
        this.authHandler = authHandler;
    }

    /**
     * The default port.
     * <p>
     * Subclasses should override this method to provide a service specific default value.
     *
     * @return 8443
     */
    @Override
    public int getPortDefaultValue() {
        return 8443;
    }

    /**
     * The default insecure port.
     * <p>
     * Subclasses should override this method to provide a service specific default value.
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
                     return Future.all(bindSecureHttpServer(router), bindInsecureHttpServer(router));
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
     * This method creates a router instance along with routes matching all request. The following handlers get applied:
     * <ul>
     * <li>a handler to keep track of the tracing span created for the request by means of the Vert.x/Quarkus
     * instrumentation,</li>
     * <li>the authentication handler, set via {@link #setAuthHandler(AuthenticationHandler)}.</li>
     * </ul>
     * Also a default failure handler is set.
     *
     * @return The newly created router (never {@code null}).
     */
    protected Router createRouter() {

        final Router router = Router.router(vertx);
        final var customTags = getCustomTags();
        final DefaultFailureHandler defaultFailureHandler = new DefaultFailureHandler();

        final Route matchAllRoute = router.route();
        matchAllRoute.failureHandler(ctx -> {
            if (ctx.get(KEY_MATCH_ALL_ROUTE_APPLIED) == null) {
                // handler of matchAllRoute not applied, meaning this request is invalid/failed from the start;
                // ensure span name is set to fixed string instead of the request path
                ctx.request().routed(MATCH_ALL_ROUTE_NAME);
                HttpServerSpanHelper.adoptActiveSpanIntoContext(tracer, customTags, ctx);
            }
            defaultFailureHandler.handle(ctx);
        });
        matchAllRoute.handler(ctx -> {
            // ensure span name is set to fixed string instead of the request path
            ctx.request().routed(MATCH_ALL_ROUTE_NAME);
            ctx.put(KEY_MATCH_ALL_ROUTE_APPLIED, true);
            // keep track of the tracing span created by the Vert.x/Quarkus instrumentation (set as active span there)
            HttpServerSpanHelper.adoptActiveSpanIntoContext(tracer, customTags, ctx);
            HttpUtils.nextRoute(ctx);
        });
        addAuthHandler(router);
        HttpUtils.addDefault404ErrorHandler(router);
        return router;
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

    private Map<String, String> getCustomTags() {
        final Map<String, String> customTags = new HashMap<>();
        customTags.put(Tags.COMPONENT.getKey(), getClass().getSimpleName());
        addCustomTags(customTags);
        return customTags;
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
        options.setHost(getConfig().getBindAddress())
               .setPort(getConfig().getPort(getPortDefaultValue()))
               .setMaxChunkSize(4096)
               .setIdleTimeout(getConfig().getIdleTimeout());
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
                .setPort(getConfig().getInsecurePort(getInsecurePortDefaultValue())).setMaxChunkSize(4096)
                .setIdleTimeout(getConfig().getIdleTimeout());
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
            final
            List<Future<Void>> endpointFutures = new ArrayList<>(endpoints.size());
            for (final HttpEndpoint ep : endpoints()) {
                log.info("starting endpoint [name: {}, class: {}]", ep.getName(), ep.getClass().getName());
                endpointFutures.add(ep.start());
            }
            Future.all(endpointFutures).onComplete(startup -> {
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
        final
        List<Future<Void>> endpointFutures = new ArrayList<>(endpoints.size());
        for (final HttpEndpoint ep : endpoints()) {
            log.info("stopping endpoint [name: {}, class: {}]", ep.getName(), ep.getClass().getName());
            endpointFutures.add(ep.stop());
        }
        Future.all(endpointFutures).onComplete(shutdown -> {
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
                .compose(s -> Future.all(stopServer(), stopInsecureServer()))
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
