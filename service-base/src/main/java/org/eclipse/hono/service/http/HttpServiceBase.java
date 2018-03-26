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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.eclipse.hono.config.ServiceConfigProperties;
import org.eclipse.hono.service.AbstractServiceBase;
import org.eclipse.hono.util.Constants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.ext.healthchecks.HealthCheckHandler;
import io.vertx.ext.web.Router;
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

    private final Set<HttpEndpoint> endpoints = new HashSet<>();

    @Value("${spring.profiles.active:}")
    private String activeProfiles;
    private HttpServer server;
    private HttpServer insecureServer;

    /**
     * Adds multiple endpoints to this server.
     *
     * @param definedEndpoints The endpoints.
     */
    @Autowired(required = false)
    public final void addEndpoints(final Set<HttpEndpoint> definedEndpoints) {
        endpoints.addAll(Objects.requireNonNull(definedEndpoints));
    }

    /**
     * Adds an endpoint to this server.
     *
     * @param ep The endpoint.
     */
    public final void addEndpoint(final HttpEndpoint ep) {
        LOG.debug("registering endpoint [{}]", ep.getName());
        endpoints.add(Objects.requireNonNull(ep));
    }


    @Autowired
    @Qualifier(Constants.QUALIFIER_REST)
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

    private void addEndpointRoutes(final Router router) {
        for (HttpEndpoint ep : endpoints) {
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
            server.requestHandler(router::accept).listen(bindAttempt -> {
                if (bindAttempt.succeeded()) {
                    if (getPort() == getPortDefaultValue()) {
                        LOG.info("server listens on standard secure port [{}:{}]", bindAddress, server.actualPort());
                    } else {
                        LOG.warn("server listens on non-standard secure port [{}:{}], default is {}", bindAddress,
                                server.actualPort(), getPortDefaultValue());
                    }
                    result.complete(bindAttempt.result());
                } else {
                    LOG.error("cannot bind to secure port", bindAttempt.cause());
                    result.fail(bindAttempt.cause());
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
            insecureServer.requestHandler(router::accept).listen(bindAttempt -> {
                if (bindAttempt.succeeded()) {
                    if (getInsecurePort() == getInsecurePortDefaultValue()) {
                        LOG.info("server listens on standard insecure port [{}:{}]", bindAddress, insecureServer.actualPort());
                    } else {
                        LOG.warn("server listens on non-standard insecure port [{}:{}], default is {}", bindAddress,
                                insecureServer.actualPort(), getInsecurePortDefaultValue());
                    }
                    result.complete(bindAttempt.result());
                } else {
                    LOG.error("cannot bind to insecure port", bindAttempt.cause());
                    result.fail(bindAttempt.cause());
                }
            });
            return result;
        } else {
            return Future.succeededFuture();
        }
    }

    private Future<Router> startEndpoints() {

        final Future<Router> startFuture = Future.future();
        final Router router = createRouter();
        if (router == null) {
            startFuture.fail("no router configured");
        } else {
            addEndpointRoutes(router);
            addCustomRoutes(router);
            @SuppressWarnings("rawtypes")
            List<Future> endpointFutures = new ArrayList<>(endpoints.size());
            for (HttpEndpoint ep : endpoints) {
                LOG.info("starting endpoint [name: {}, class: {}]", ep.getName(), ep.getClass().getName());
                endpointFutures.add(ep.start());
            }
            CompositeFuture.all(endpointFutures).setHandler(startup -> {
                if (startup.succeeded()) {
                    startFuture.complete(router);
                } else {
                    startFuture.fail(startup.cause());
                }
            });
        }
        return startFuture;
    }

    private Future<Void> stopEndpoints() {

        final Future<Void> stopFuture = Future.future();
        @SuppressWarnings("rawtypes")
        List<Future> endpointFutures = new ArrayList<>(endpoints.size());
        for (HttpEndpoint ep : endpoints) {
            LOG.info("stopping endpoint [name: {}, class: {}]", ep.getName(), ep.getClass().getName());
            endpointFutures.add(ep.stop());
        }
        CompositeFuture.all(endpointFutures).setHandler(shutdown -> {
            if (shutdown.succeeded()) {
                stopFuture.complete();
            } else {
                stopFuture.fail(shutdown.cause());
            }
        });
        return stopFuture;

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
        Future<Void> serverStopTracker = Future.future();
        if (server != null) {
            LOG.info("stopping secure HTTP server [{}:{}]", getBindAddress(), getActualPort());
            server.close(serverStopTracker.completer());
        } else {
            serverStopTracker.complete();
        }
        return serverStopTracker;
    }

    private Future<Void> stopInsecureServer() {
        Future<Void> insecureServerStopTracker = Future.future();
        if (insecureServer != null) {
            LOG.info("stopping insecure HTTP server [{}:{}]", getInsecurePortBindAddress(), getActualInsecurePort());
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

    /**
     * Iterates over all endpoints and registers their readiness checks with the handler.
     * <p>
     * Subclasses may override this method in order to register other/additional checks.
     * 
     * @param handler The handler to register the checks with.
     */
    @Override
    public void registerReadinessChecks(final HealthCheckHandler handler) {
        for (HttpEndpoint ep : endpoints) {
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
        for (HttpEndpoint ep : endpoints) {
            ep.registerLivenessChecks(handler);
        }
    }

}
