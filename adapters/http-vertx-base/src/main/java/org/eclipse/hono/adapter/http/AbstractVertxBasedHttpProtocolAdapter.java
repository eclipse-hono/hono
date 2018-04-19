/**
 * Copyright (c) 2016, 2018 Bosch Software Innovations GmbH.
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

import java.net.HttpURLConnection;
import java.util.Objects;
import java.util.Optional;

import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.MessageSender;
import org.eclipse.hono.service.AbstractProtocolAdapterBase;
import org.eclipse.hono.service.auth.device.Device;
import org.eclipse.hono.service.http.HttpUtils;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.EventConstants;
import org.eclipse.hono.util.TelemetryConstants;
import org.eclipse.hono.util.TenantObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;

/**
 * Base class for a Vert.x based Hono protocol adapter that uses the HTTP protocol. 
 * It provides access to the Telemetry and Event API.
 * 
 * @param <T> The type of configuration properties used by this service.
 */
public abstract class AbstractVertxBasedHttpProtocolAdapter<T extends HttpProtocolAdapterProperties> extends AbstractProtocolAdapterBase<T> {

    /**
     * Default file uploads directory used by Vert.x Web.
     */
    protected static final String DEFAULT_UPLOADS_DIRECTORY = "/tmp";

    private static final Logger LOG = LoggerFactory.getLogger(AbstractVertxBasedHttpProtocolAdapter.class);

    private HttpServer         server;
    private HttpServer         insecureServer;
    private HttpAdapterMetrics metrics;

    /**
     * Sets the metrics for this service.
     *
     * @param metrics The metrics
     */
    @Autowired
    public final void setMetrics(final HttpAdapterMetrics metrics) {
        this.metrics = metrics;
    }

    /**
     * @return 8443
     */
    @Override
    public final int getPortDefaultValue() {
        return 8443;
    }

    /**
     * @return 8080
     */
    @Override
    public final int getInsecurePortDefaultValue() {
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
     * Sets the http server instance configured to serve requests over a TLS secured socket.
     * <p>
     * If no server is set using this method, then a server instance is created during
     * startup of this adapter based on the <em>config</em> properties and the server options
     * returned by {@link #getHttpServerOptions()}.
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
     * Sets the http server instance configured to serve requests over a plain socket.
     * <p>
     * If no server is set using this method, then a server instance is created during
     * startup of this adapter based on the <em>config</em> properties and the server options
     * returned by {@link #getInsecureHttpServerOptions()}.
     * 
     * @param server The http server.
     * @throws NullPointerException if server is {@code null}.
     * @throws IllegalArgumentException if the server is already started and listening on an address/port.
     */
    @Autowired(required = false)
    public final void setInsecureHttpServer(final HttpServer server) {
        Objects.requireNonNull(server);
        if (server.actualPort() > 0) {
            throw new IllegalArgumentException("http server must not be started already");
        } else {
            this.insecureServer = server;
        }
    }

    @Override
    public final void doStart(final Future<Void> startFuture) {

        checkPortConfiguration()
            .compose(s -> preStartup())
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
                    onStartupSuccess();
                    startFuture.complete();
                } catch (Exception e) {
                    LOG.error("error in onStartupSuccess", e);
                    startFuture.fail(e);
                }
            }, startFuture);
    }

    /**
     * Invoked before the http server is started.
     * <p>
     * May be overridden by sub-classes to provide additional startup handling.
     * 
     * @return A future indicating the outcome of the operation. The start up process fails if the returned future fails.
     */
    protected Future<Void> preStartup() {

        return Future.succeededFuture();
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
     * <li>A default route limiting the body size of requests to the maximum payload size set in the <em>config</em> properties.</li>
     * </ol>
     * 
     * @return The newly created router (never {@code null}).
     */
    protected Router createRouter() {

        final Router router = Router.router(vertx);
        LOG.info("limiting size of inbound request body to {} bytes", getConfig().getMaxPayloadSize());
        router.route().handler(BodyHandler.create(DEFAULT_UPLOADS_DIRECTORY).setBodyLimit(getConfig().getMaxPayloadSize()));

        return router;
    }

    /**
     * Adds custom routes for handling requests.
     * <p>
     * This method is invoked right before the http server is started with the value returned by
     * {@link AbstractVertxBasedHttpProtocolAdapter#createRouter()}.
     * 
     * @param router The router to add the custom routes to.
     */
    protected abstract void addRoutes(Router router);

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
        options.setHost(getConfig().getBindAddress()).setPort(getConfig().getPort(getPortDefaultValue())).setMaxChunkSize(4096);
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
        options.setHost(getConfig().getInsecurePortBindAddress()).setPort(getConfig().getInsecurePort(getInsecurePortDefaultValue())).setMaxChunkSize(4096);
        return options;
    }

    /**
     * Invoked before the message is sent to the downstream peer.
     * <p>
     * Subclasses may override this method in order to customize the message
     * before it is sent, e.g. adding custom properties.
     * 
     * @param downstreamMessage The message that will be sent downstream.
     * @param ctx The routing context.
     */
    protected void customizeDownstreamMessage(final Message downstreamMessage, final RoutingContext ctx) {
        // this default implementation does nothing
    }

    /**
     * Gets the authenticated device identity from the routing context.
     * 
     * @param ctx The routing context.
     * @return The device or {@code null} if the device has not been authenticated.
     */
    protected final Device getAuthenticatedDevice(final RoutingContext ctx) {

        return Optional.ofNullable(ctx.user()).map(user -> {
            if (Device.class.isInstance(user)) {
                return (Device) user;
            } else {
                return null;
            }
        }).orElse(null);
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
    public final void doStop(final Future<Void> stopFuture) {

        try {
            preShutdown();
        } catch (Exception e) {
            LOG.error("error in preShutdown", e);
        }

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
            .compose(v -> postShutdown())
            .compose(s -> stopFuture.complete(), stopFuture);
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
     * @return A future that has to be completed when this operation is finished.
     */
    protected Future<Void> postShutdown() {
        return Future.succeededFuture();
    }

    /**
     * Uploads the body of an HTTP request as a telemetry message to the Hono server.
     * <p>
     * This method simply invokes {@link #uploadTelemetryMessage(RoutingContext, String, String, Buffer, String)}
     * with objects retrieved from the routing context.
     *
     * @param ctx The context to retrieve the message payload and content type from.
     * @param tenant The tenant of the device that has produced the data.
     * @param deviceId The id of the device that has produced the data.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    public final void uploadTelemetryMessage(final RoutingContext ctx, final String tenant, final String deviceId) {

        uploadTelemetryMessage(
                Objects.requireNonNull(ctx),
                Objects.requireNonNull(tenant),
                Objects.requireNonNull(deviceId),
                ctx.getBody(),
                HttpUtils.getContentType(ctx));
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
     * @param ctx The context to retrieve cookies and the HTTP response from.
     * @param tenant The tenant of the device that has produced the data.
     * @param deviceId The id of the device that has produced the data.
     * @param payload The message payload to send.
     * @param contentType The content type of the message payload.
     * @throws NullPointerException if any of response, tenant or device ID is {@code null}.
     */
    public final void uploadTelemetryMessage(final RoutingContext ctx, final String tenant, final String deviceId,
            final Buffer payload, final String contentType) {

        doUploadMessage(
                Objects.requireNonNull(ctx),
                Objects.requireNonNull(tenant),
                Objects.requireNonNull(deviceId),
                payload,
                contentType,
                getTelemetrySender(tenant),
                TelemetryConstants.TELEMETRY_ENDPOINT);
    }

    /**
     * Uploads the body of an HTTP request as an event message to the Hono server.
     * <p>
     * This method simply invokes {@link #uploadEventMessage(RoutingContext, String, String, Buffer, String)}
     * with objects retrieved from the routing context.
     *
     * @param ctx The context to retrieve the message payload and content type from.
     * @param tenant The tenant of the device that has produced the data.
     * @param deviceId The id of the device that has produced the data.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    public final void uploadEventMessage(final RoutingContext ctx, final String tenant, final String deviceId) {

        uploadEventMessage(
                Objects.requireNonNull(ctx),
                Objects.requireNonNull(tenant),
                Objects.requireNonNull(deviceId),
                ctx.getBody(),
                HttpUtils.getContentType(ctx));
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
     * @param ctx The context to retrieve cookies and the HTTP response from.
     * @param tenant The tenant of the device that has produced the data.
     * @param deviceId The id of the device that has produced the data.
     * @param payload The message payload to send.
     * @param contentType The content type of the message payload.
     * @throws NullPointerException if any of response, tenant or device ID is {@code null}.
     */
    public final void uploadEventMessage(final RoutingContext ctx, final String tenant, final String deviceId,
            final Buffer payload, final String contentType) {

        doUploadMessage(
                Objects.requireNonNull(ctx),
                Objects.requireNonNull(tenant),
                Objects.requireNonNull(deviceId),
                payload,
                contentType,
                getEventSender(tenant),
                EventConstants.EVENT_ENDPOINT);
    }

    private void doUploadMessage(final RoutingContext ctx, final String tenant, final String deviceId,
            final Buffer payload, final String contentType, final Future<MessageSender> senderTracker, final String endpointName) {

        if (contentType == null) {
            HttpUtils.badRequest(ctx, String.format("%s header is missing", HttpHeaders.CONTENT_TYPE));
        } else if (payload == null || payload.length() == 0) {
            HttpUtils.badRequest(ctx, "missing body");
        } else {

            final Device authenticatedDevice = getAuthenticatedDevice(ctx);
            final Future<JsonObject> tokenTracker = getRegistrationAssertion(tenant, deviceId, authenticatedDevice);
            final Future<TenantObject> tenantConfigTracker = getTenantConfiguration(tenant);

            CompositeFuture.all(tokenTracker, tenantConfigTracker, senderTracker).compose(ok -> {

                if (tenantConfigTracker.result().isAdapterEnabled(getTypeName())) {
                    final Message downstreamMessage = newMessage(
                            String.format("%s/%s", endpointName, tenant),
                            deviceId,
                            ctx.request().uri(),
                            contentType,
                            payload,
                            tokenTracker.result());
                    customizeDownstreamMessage(downstreamMessage, ctx);
                    return senderTracker.result().send(downstreamMessage);
                } else {
                    // this adapter is not enabled for the tenant
                    return Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_FORBIDDEN));
                }
            }).compose(delivery -> {
                LOG.trace("successfully processed message for device [tenantId: {}, deviceId: {}, endpoint: {}]",
                        tenant, deviceId, endpointName);
                metrics.incrementProcessedHttpMessages(endpointName, tenant);
                ctx.response().setStatusCode(HttpURLConnection.HTTP_ACCEPTED).end();
                return Future.succeededFuture();
            }).recover(t -> {
                if (ClientErrorException.class.isInstance(t)) {
                    ClientErrorException e = (ClientErrorException) t;
                    LOG.debug("cannot process message for device [tenantId: {}, deviceId: {}, endpoint: {}]: {} - {}",
                            tenant, deviceId, endpointName, e.getErrorCode(), e.getMessage());
                    ctx.fail(e.getErrorCode());
                } else {
                    LOG.debug("cannot process message for device [tenantId: {}, deviceId: {}, endpoint: {}]: {}",
                            tenant, deviceId, endpointName, t.getMessage());
                    metrics.incrementUndeliverableHttpMessages(endpointName, tenant);
                    HttpUtils.serviceUnavailable(ctx, 2);
                }
                return Future.failedFuture(t);
            });
        }
    }
}
