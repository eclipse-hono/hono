/*******************************************************************************
 * Copyright (c) 2016, 2018 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.adapter.http;

import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.MessageConsumer;
import org.eclipse.hono.client.MessageSender;
import org.eclipse.hono.client.ServerErrorException;
import org.eclipse.hono.service.AbstractProtocolAdapterBase;
import org.eclipse.hono.service.auth.device.Device;
import org.eclipse.hono.service.command.Command;
import org.eclipse.hono.service.command.CommandResponse;
import org.eclipse.hono.service.command.CommandResponseSender;
import org.eclipse.hono.service.http.DefaultFailureHandler;
import org.eclipse.hono.service.http.HttpUtils;
import org.eclipse.hono.tracing.TracingHelper;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.EventConstants;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.ResourceIdentifier;
import org.eclipse.hono.util.TelemetryConstants;
import org.eclipse.hono.util.TenantObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import io.opentracing.Span;
import io.opentracing.contrib.vertx.ext.web.TracingHandler;
import io.opentracing.contrib.vertx.ext.web.WebSpanDecorator;
import io.opentracing.tag.Tags;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.HttpServerResponse;
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

    private static final int AT_LEAST_ONCE = 1;
    private static final int HEADER_QOS_INVALID = -1;

    private static final String KEY_TIMER_ID = "timerId";

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
        return server != null ? server.actualPort() : Constants.PORT_UNCONFIGURED;
    }

    @Override
    protected final int getActualInsecurePort() {
        return insecureServer != null ? insecureServer.actualPort() : Constants.PORT_UNCONFIGURED;
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
                if (metrics == null) {
                    // use default implementation
                    // which simply discards all reported metrics
                    metrics = new HttpAdapterMetrics();
                }
                final Router router = createRouter();
                if (router == null) {
                    return Future.failedFuture("no router configured");
                } else {
                    addRoutes(router);
                    return CompositeFuture.all(bindSecureHttpServer(router), bindInsecureHttpServer(router));
                }
            }).compose(s -> {
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
     * Adds a handler for adding an OpenTracing Span to the routing context.
     *
     * @param router The router to add the handler to.
     * @param position The position to add the tracing handler at.
     */
    private void addTracingHandler(final Router router, final int position) {
        final Map<String, String> customTags = new HashMap<>();
        customTags.put(Tags.COMPONENT.getKey(), getTypeName());
        addCustomTags(customTags);
        final List<WebSpanDecorator> decorators = new ArrayList<>();
        decorators.add(new ComponentMetaDataDecorator(customTags));
        addCustomSpanDecorators(decorators);
        final TracingHandler tracingHandler = new TracingHandler(tracer, decorators);
        router.route().order(position).handler(tracingHandler).failureHandler(tracingHandler);
    }

    /**
     * Adds meta data about this adapter to be included in OpenTracing
     * spans that are used for tracing requests handled by this adapter.
     * <p>
     * This method is empty by default.
     *
     * @param customTags The existing custom tags to add to. The map will already
     *                 include this adapter's {@linkplain #getTypeName() type name}
     *                 under key {@link Tags#COMPONENT}.
     */
    protected void addCustomTags(final Map<String, String> customTags) {
        // empty by default
    }

    /**
     * Adds decorators to apply to the active OpenTracing span on certain
     * stages of processing requests handled by this adapter.
     * <p>
     * This method is empty by default.
     *
     * @param decorators The decorators to add to. The list will already
     *                 include a {@linkplain ComponentMetaDataDecorator decorator} for
     *                 adding standard tags and component specific tags which can be customized by
     *                 means of overriding {@link #addCustomTags(Map)}.
     */
    protected void addCustomSpanDecorators(final List<WebSpanDecorator> decorators) {
        // empty by default
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
        addTracingHandler(router, -5);
        // add default handler for failed routes
        router.route().order(-1).failureHandler(new DefaultFailureHandler());

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
     * This method returns default options with the host and port being set to the corresponding values
     * from the <em>config</em> properties and using a maximum chunk size of 4096 bytes.
     *
     * @return The http server options.
     */
    protected HttpServerOptions getInsecureHttpServerOptions() {

        final HttpServerOptions options = new HttpServerOptions();
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
            final Future<HttpServer> result = Future.future();
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
            final Future<HttpServer> result = Future.future();
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

        final Future<Void> serverStopTracker = Future.future();
        if (server != null) {
            server.close(serverStopTracker.completer());
        } else {
            serverStopTracker.complete();
        }

        final Future<Void> insecureServerStopTracker = Future.future();
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
     * Uploads the body of an HTTP request as a telemetry message to Hono.
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
     * Uploads a telemetry message to Hono.
     * <p>
     * This method always sends a response to the device. The status code will be set
     * as specified in the
     * <a href="https://www.eclipse.org/hono/user-guide/http-adapter/#publish-telemetry-data-authenticated-device">
     * HTTP adapter User Guide</a>.
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
     * Uploads the body of an HTTP request as an event message to Hono.
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
     * Uploads an event message to Hono.
     * <p>
     * This method always sends a response to the device. The status code will be set
     * as specified in the
     * <a href="https://www.eclipse.org/hono/user-guide/http-adapter/#publish-an-event-authenticated-device">
     * HTTP adapter User Guide</a>.
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

        if (!isPayloadOfIndicatedType(payload, contentType)) {
            HttpUtils.badRequest(ctx, String.format("content type [%s] does not match payload", contentType));
        } else {
            final String qosHeaderValue = ctx.request().getHeader(Constants.HEADER_QOS_LEVEL);
            final Integer qos = getQoSLevel(qosHeaderValue);
            if (qos != null && qos == HEADER_QOS_INVALID) {
                HttpUtils.badRequest(ctx, "unsupported QoS-Level header value");
            } else {

                final Device authenticatedDevice = getAuthenticatedDevice(ctx);
                final Span currentSpan = tracer.buildSpan("upload " + endpointName)
                        .asChildOf(TracingHandler.serverSpanContext(ctx))
                        .ignoreActiveSpan()
                        .withTag(Tags.COMPONENT.getKey(), getTypeName())
                        .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_CLIENT)
                        .withTag(MessageHelper.APP_PROPERTY_TENANT_ID, tenant)
                        .withTag(MessageHelper.APP_PROPERTY_DEVICE_ID, deviceId)
                        .withTag(TracingHelper.TAG_AUTHENTICATED.getKey(), authenticatedDevice != null)
                        .start();

                final Future<Void> responseReady = Future.future();
                final Future<JsonObject> tokenTracker = getRegistrationAssertion(
                        tenant,
                        deviceId,
                        authenticatedDevice,
                        currentSpan.context());
                final Future<TenantObject> tenantConfigTracker = getTenantConfiguration(tenant, currentSpan.context());
                final Future<Integer> ttdTracker = tenantConfigTracker.compose(tenantObj -> {
                    final Integer ttdParam = HttpUtils.getTimeTilDisconnect(ctx);
                    return getTimeUntilDisconnect(tenantObj, ttdParam).map(effectiveTtd -> {
                        if (effectiveTtd != null) {
                            currentSpan.setTag(MessageHelper.APP_PROPERTY_DEVICE_TTD, effectiveTtd);
                        }
                        return effectiveTtd;
                    });
                });
                final Future<MessageConsumer> commandConsumerTracker = ttdTracker
                        .compose(ttd -> createCommandConsumer(ttd, tenant, deviceId, ctx, responseReady, currentSpan));

                CompositeFuture.all(tokenTracker, senderTracker, commandConsumerTracker).compose(ok -> {

                    if (tenantConfigTracker.result().isAdapterEnabled(getTypeName())) {
                        final MessageSender sender = senderTracker.result();
                        final Message downstreamMessage = newMessage(
                                ResourceIdentifier.from(endpointName, tenant, deviceId),
                                sender.isRegistrationAssertionRequired(),
                                ctx.request().uri(),
                                contentType,
                                payload,
                                tokenTracker.result(),
                                ttdTracker.result());
                        customizeDownstreamMessage(downstreamMessage, ctx);

                        addConnectionCloseHandler(ctx, commandConsumerTracker.result(), tenant, deviceId);

                        if (qos == null) {
                            return CompositeFuture.all(sender.send(downstreamMessage, currentSpan.context()), responseReady);
                        } else {
                            currentSpan.setTag(Constants.HEADER_QOS_LEVEL, qosHeaderValue);
                            return CompositeFuture.all(sender.sendAndWaitForOutcome(downstreamMessage, currentSpan.context()), responseReady);
                        }
                    } else {
                        // this adapter is not enabled for the tenant
                        return Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_FORBIDDEN,
                                "adapter is not enabled for tenant"));
                    }
                }).compose(delivery -> {
                    if (!ctx.response().closed()) {
                        final Command command = Command.get(ctx);
                        setResponsePayload(ctx.response(), command, currentSpan);
                        ctx.addBodyEndHandler(ok -> {
                            LOG.trace("successfully processed [{}] message for device [tenantId: {}, deviceId: {}]",
                                    endpointName, tenant, deviceId);
                            metrics.incrementProcessedMessages(endpointName, tenant);
                            metrics.incrementProcessedPayload(endpointName, tenant, messagePayloadSize(ctx));
                            if (command!=null){
                                metrics.incrementCommandDeliveredToDevice(tenant);
                                LOG.trace("Command [{}] for device [tenantId: {}, deviceId: {}]", command.getPayload(), tenant, deviceId);
                            }
                            currentSpan.finish();
                        });
                        ctx.response().exceptionHandler(t -> {
                            currentSpan.log("failed to send HTTP response to device");
                            LOG.debug("failed to send http response for [{}] message from device [tenantId: {}, deviceId: {}]",
                                    endpointName, tenant, deviceId, t);
                            failCommand(command, HttpURLConnection.HTTP_UNAVAILABLE);
                            TracingHelper.logError(currentSpan, t);
                            currentSpan.finish();
                        });
                        ctx.response().end();
                    }
                    return Future.succeededFuture();

                }).recover(t -> {

                    LOG.debug("cannot process [{}] message from device [tenantId: {}, deviceId: {}]",
                            endpointName, tenant, deviceId, t);
                    failCommand(Command.get(ctx), HttpURLConnection.HTTP_UNAVAILABLE);

                    if (ClientErrorException.class.isInstance(t)) {
                        final ClientErrorException e = (ClientErrorException) t;
                        ctx.fail(e);
                    } else {
                        metrics.incrementUndeliverableMessages(endpointName, tenant);
                        HttpUtils.serviceUnavailable(ctx, 2, "temporarily unavailable");
                    }
                    TracingHelper.logError(currentSpan, t);
                    currentSpan.finish();
                    return Future.failedFuture(t);
                });
            }
        }
    }

    /**
     * Measure the size of the payload for using in the metrics system.
     * <p>
     * This implementation simply counts the bytes of the HTTP payload buffer and ignores all other attributes of the
     * HTTP request.
     *
     * @param ctx The payload to measure. May be {@code null}.
     * @return The number of bytes of the payload or zero if any input is {@code null}.
     */
    protected long messagePayloadSize(final RoutingContext ctx) {
        if (ctx == null || ctx.getBody() == null) {
            return 0L;
        }
        return ctx.getBody().length();
    }

    /**
     * Attaches a handler that is called if a command consumer was opened and the client closes the HTTP connection before a response
     * with a possible command could be sent.
     * <p>
     * In this case, the handler closes the command consumer since a command could not be added to the response anymore.
     * The application receives an {@link HttpURLConnection#HTTP_UNAVAILABLE} if trying to send the command and can repeat
     * it later.
     *
     * @param ctx The context to retrieve cookies and the HTTP response from.
     * @param messageConsumer The message consumer to receive a command. Maybe {@code null} - in this case no handler is attached.
     * @param tenantId The tenant that the device belongs to.
     * @param deviceId The identifier of the device.
     */
    private void addConnectionCloseHandler(
            final RoutingContext ctx,
            final MessageConsumer messageConsumer,
            final String tenantId,
            final String deviceId) {

        if (messageConsumer != null) {
            if (!ctx.response().closed()) {
                ctx.response().closeHandler(v -> {
                    cancelCommandReceptionTimer(ctx);
                    metrics.incrementNoCommandReceivedAndTTDExpired(tenantId);
                    LOG.debug("Connection was closed before response could be sent - closing command consumer for device [tenantId: {}, deviceId: {}]", tenantId, deviceId);

                    getCommandConnection().closeCommandConsumer(tenantId, deviceId).setHandler(result -> {
                        if (result.failed()) {
                            LOG.warn("Close command consumer failed", result.cause());
                        }
                    });
                });
            }
        }
    }

    private void setResponsePayload(final HttpServerResponse response, final Command command, final Span currentSpan) {
        if (command == null) {
            response.setStatusCode(HttpURLConnection.HTTP_ACCEPTED);
        } else {
            currentSpan.log(String.format("adding command [name: {}, request-id: {}] to response",
                    command.getName(), command.getRequestId()));
            LOG.trace("adding command [name: {}, request-id: {}] to response for device [tenant-id: {}, device-id: {}]",
                    command.getName(), command.getRequestId(), command.getTenant(), command.getDeviceId());
            response.setStatusCode(HttpURLConnection.HTTP_OK);
            response.putHeader(Constants.HEADER_COMMAND, command.getName());
            response.putHeader(Constants.HEADER_COMMAND_REQUEST_ID, command.getRequestId());

            HttpUtils.setResponseBody(response, command.getPayload());
        }
    }

    /**
     * Creates a consumer for command messages to be sent to a device.
     *
     * @param ttdSecs The number of seconds the device waits for a command.
     * @param tenantId The tenant that the device belongs to.
     * @param deviceId The identifier of the device.
     * @param ctx The device's currently executing HTTP request.
     * @param responseReady A future to complete once one of the following conditions are met:
     *              <ul>
     *              <li>the request did not include a <em>hono-ttd</em> parameter or</li>
     *              <li>a command has been received and the response ready future has not yet been
     *              completed or</li>
     *              <li>the ttd has expired</li>
     *              </ul>
     * @param currentSpan The OpenTracing Span to use for tracking the processing
     *                       of the request.
     * @return A future indicating the outcome.
     *         The future will be completed with the created message consumer or it will
     *         be failed with a {@code ServiceInvocationException} if the consumer
     *         could not be created.
     * @throws NullPointerException if any of the parameters other than TTD are {@code null}.
     */
    protected final Future<MessageConsumer> createCommandConsumer(
            final Integer ttdSecs,
            final String tenantId,
            final String deviceId,
            final RoutingContext ctx,
            final Future<Void> responseReady,
            final Span currentSpan) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(deviceId);
        Objects.requireNonNull(ctx);
        Objects.requireNonNull(responseReady);
        Objects.requireNonNull(currentSpan);

        if (ttdSecs == null || ttdSecs <= 0) {
            // no need to wait for a command
            responseReady.tryComplete();
            return Future.succeededFuture();
        } else {
            currentSpan.setTag(MessageHelper.APP_PROPERTY_DEVICE_TTD, ttdSecs);
            // get or create a command consumer - if there is one command consumer existing already, it is reused.
            // This prevents that receiver links are opened massively if a device is misbehaving by sending a huge number
            // of downstream messages with a ttd value set.
            return getCommandConnection().getOrCreateCommandConsumer(
                    tenantId,
                    deviceId,
                    createCommandMessageConsumer(tenantId, deviceId, receivedCommand -> {
                        if (responseReady.isComplete()) {
                            // the timer has already fired, release the command
                            receivedCommand.release();
                        } else {
                            // put command to routing context and notify
                            receivedCommand.put(ctx);
                            cancelCommandReceptionTimer(ctx);
                            responseReady.tryComplete();
                        }
                    }),
                    remoteDetach -> {
                        LOG.debug("peer closed command receiver link [tenant-id: {}, device-id: {}]", tenantId, deviceId);
                        // command consumer is closed by closeHandler, no explicit close necessary here
                    }).map(consumer -> {
                        if (!responseReady.isComplete()) {
                            // if the request was not responded already, add a timer for closing the command consumer
                            addCommandReceptionTimer(ctx, tenantId, deviceId, responseReady, ttdSecs);
                        }
                        return consumer;
                    });
        }
    }

    /**
     * Adds a timer that closes the command connection after a given delay.
     * In this case it additionally completes the <em>responseReady</em> Future.
     * <p>
     * The created timer's ID is put to the routing context using key {@link #KEY_TIMER_ID}.
     *
     * @param ctx The device's currently executing HTTP request.
     * @param tenantId The tenant that the device belongs to.
     * @param deviceId The identifier of the device.
     * @param responseReady A future to complete when the timer fires.
     * @param delaySecs The number of seconds after which the timer should fire.
     */
    private void addCommandReceptionTimer(
            final RoutingContext ctx,
            final String tenantId,
            final String deviceId,
            final Future<Void> responseReady,
            final long delaySecs) {

        final Long timerId = ctx.vertx().setTimer(delaySecs * 1000L, id -> {

            LOG.trace("Command Reception timer fired, id {}", id);

            if (!responseReady.isComplete()) {
                // the response hasn't been sent yet
                responseReady.tryComplete();
                getCommandConnection().closeCommandConsumer(tenantId, deviceId).setHandler(v -> {
                    if (v.failed()) {
                        LOG.warn("Close command consumer failed", v.cause());
                    }
                });
            } else {
                LOG.trace("Nothing to close for timer since response was sent already");
            }
        });

        LOG.trace("Adding command reception timer id {}", timerId);

        ctx.put(KEY_TIMER_ID, timerId);
    }

    private void cancelCommandReceptionTimer(final RoutingContext ctx) {

        final Long timerId = ctx.get(KEY_TIMER_ID);
        if (timerId != null && timerId >= 0) {
            if (ctx.vertx().cancelTimer(timerId)) {
                LOG.trace("Cancelled timer id {}", timerId);
            } else {
                LOG.debug("Could not cancel timer id {}", timerId);
            }
        }
    }

    /**
     * Uploads a command response message to Hono.
     *
     * @param ctx The routing context of the HTTP request.
     * @param tenant The tenant of the device from which the command response was received.
     * @param deviceId The device from which the command response was received.
     * @param commandRequestId The id of the command that the response has been sent in reply to.
     * @param responseStatus The HTTP status code that the device has provided in its request to indicate
     *                       the outcome of processing the command (may be {@code null}).
     * @throws NullPointerException if ctx, tenant or deviceId are {@code null}.
     */
    public final void uploadCommandResponseMessage(final RoutingContext ctx, final String tenant, final String deviceId,
                                                   final String commandRequestId, final Integer responseStatus) {
        Objects.requireNonNull(ctx);
        Objects.requireNonNull(tenant);
        Objects.requireNonNull(deviceId);

        final Buffer payload = ctx.getBody();
        final String contentType = HttpUtils.getContentType(ctx);

        LOG.debug("processing response to command [tenantId: {}, deviceId: {}, cmd-req-id: {}, status code: {}]",
                tenant, deviceId, commandRequestId, responseStatus);

        final CommandResponse commandResponse = CommandResponse.from(commandRequestId, deviceId, payload, contentType, responseStatus);

        if (commandResponse == null) {
            HttpUtils.badRequest(
                    ctx,
                    String.format("command-request-id [%s] or status code [%s] is missing/invalid",
                            commandRequestId, responseStatus));
        } else {

            // send response message to application via sender link
            final Future<CommandResponseSender> senderTracker = createCommandResponseSender(tenant, commandResponse.getReplyToId());

            senderTracker.compose(commandResponseSender -> {
                return commandResponseSender.sendCommandResponse(commandResponse);
            }).map(delivery -> {
                metrics.incrementCommandResponseDeliveredToApplication(tenant);
                LOG.trace("command response [command-request-id: {}] accepted by application", commandRequestId);
                ctx.response().setStatusCode(HttpURLConnection.HTTP_ACCEPTED);
                ctx.response().end();
                return delivery;
            }).otherwise(t -> {
                LOG.debug("could not send command response [command-request-id: {}] to application", commandRequestId, t);
                ctx.fail(new ServerErrorException(HttpURLConnection.HTTP_UNAVAILABLE, t));
                return null;
            }).setHandler(c -> {
                final CommandResponseSender sender = senderTracker.result();
                if (sender != null) {
                    sender.close(v -> {});
                }
            });

        }
    }

    private static Integer getQoSLevel(final String qosValue) {
        try {
            if (qosValue == null) {
                return null;
            } else {
                return Integer.parseInt(qosValue) != AT_LEAST_ONCE ? HEADER_QOS_INVALID : AT_LEAST_ONCE;
            }
        } catch (NumberFormatException e) {
            return HEADER_QOS_INVALID;
        }
    }
}
