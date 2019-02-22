/*******************************************************************************
 * Copyright (c) 2016, 2019 Contributors to the Eclipse Foundation
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

import org.apache.qpid.proton.amqp.transport.ErrorCondition;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.auth.Device;
import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.Command;
import org.eclipse.hono.client.CommandContext;
import org.eclipse.hono.client.CommandResponse;
import org.eclipse.hono.client.MessageConsumer;
import org.eclipse.hono.client.MessageSender;
import org.eclipse.hono.client.ResourceConflictException;
import org.eclipse.hono.service.AbstractProtocolAdapterBase;
import org.eclipse.hono.service.auth.DeviceUser;
import org.eclipse.hono.service.http.DefaultFailureHandler;
import org.eclipse.hono.service.http.HttpUtils;
import org.eclipse.hono.service.metric.MetricsTags;
import org.eclipse.hono.service.metric.MetricsTags.Direction;
import org.eclipse.hono.service.metric.MetricsTags.EndpointType;
import org.eclipse.hono.service.metric.MetricsTags.ProcessingOutcome;
import org.eclipse.hono.service.metric.MetricsTags.QoS;
import org.eclipse.hono.service.metric.MetricsTags.TtdStatus;
import org.eclipse.hono.tracing.TracingHelper;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.ResourceIdentifier;
import org.eclipse.hono.util.TenantObject;
import org.springframework.beans.factory.annotation.Autowired;

import io.micrometer.core.instrument.Timer.Sample;
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

    private static final String KEY_TIMER_ID = "timerId";

    private HttpServer server;
    private HttpServer insecureServer;
    private HttpAdapterMetrics metrics = HttpAdapterMetrics.NOOP;

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
     * Gets the metrics for this service.
     *
     * @return The metrics
     */
    protected final HttpAdapterMetrics getMetrics() {
        return metrics;
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
                } catch (final Exception e) {
                    LOG.error("error in onStartupSuccess", e);
                    startFuture.fail(e);
                }
            }, startFuture);
    }

    /**
     * Adds a handler for adding an OpenTracing {@code Span}
     * and a Micrometer {@code Timer.Sample} to the routing context.
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
        router.route().order(position - 1).handler(ctx -> {
            ctx.put(KEY_MICROMETER_SAMPLE, getMetrics().startTimer());
            ctx.next();
        });
    }

    private Sample getMicrometerSample(final RoutingContext ctx) {
        return ctx.get(KEY_MICROMETER_SAMPLE);
    }

    private void setTtdStatus(final RoutingContext ctx, final TtdStatus status) {
        ctx.put(TtdStatus.class.getName(), status);
    }

    private TtdStatus getTtdStatus(final RoutingContext ctx) {
        return Optional.ofNullable((TtdStatus) ctx.get(TtdStatus.class.getName()))
                .orElse(TtdStatus.NONE);
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
            if (DeviceUser.class.isInstance(user)) {
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
            server.requestHandler(router).listen(done -> {
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
            insecureServer.requestHandler(router).listen(done -> {
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
        } catch (final Exception e) {
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
                MetricsTags.EndpointType.TELEMETRY);
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
                MetricsTags.EndpointType.EVENT);
    }

    private void doUploadMessage(
            final RoutingContext ctx,
            final String tenant,
            final String deviceId,
            final Buffer payload,
            final String contentType,
            final Future<MessageSender> senderTracker,
            final MetricsTags.EndpointType endpoint) {

        if (!isPayloadOfIndicatedType(payload, contentType)) {
            HttpUtils.badRequest(ctx, String.format("content type [%s] does not match payload", contentType));
        } else {
            final String qosHeaderValue = ctx.request().getHeader(Constants.HEADER_QOS_LEVEL);
            final MetricsTags.QoS qos = getQoSLevel(endpoint, qosHeaderValue);
            if (qos == MetricsTags.QoS.UNKNOWN) {
                HttpUtils.badRequest(ctx, "unsupported QoS-Level header value");
            } else {

                final Device authenticatedDevice = getAuthenticatedDevice(ctx);
                final Span currentSpan = tracer.buildSpan("upload " + endpoint.getCanonicalName())
                        .asChildOf(TracingHandler.serverSpanContext(ctx))
                        .ignoreActiveSpan()
                        .withTag(Tags.COMPONENT.getKey(), getTypeName())
                        .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_CLIENT)
                        .withTag(MessageHelper.APP_PROPERTY_TENANT_ID, tenant)
                        .withTag(MessageHelper.APP_PROPERTY_DEVICE_ID, deviceId)
                        .withTag(TracingHelper.TAG_AUTHENTICATED.getKey(), authenticatedDevice != null)
                        .withTag(Constants.HEADER_QOS_LEVEL, qos.asTag().getValue())
                        .start();

                final Future<Void> responseReady = Future.future();
                final Future<JsonObject> tokenTracker = getRegistrationAssertion(
                        tenant,
                        deviceId,
                        authenticatedDevice,
                        currentSpan.context());
                final Future<TenantObject> tenantTracker = getTenantConfiguration(tenant, currentSpan.context())
                        .compose(tenantObject -> isAdapterEnabled(tenantObject));

                // we only need to consider TTD if the device and tenant are enabled and the adapter
                // is enabled for the tenant
                final Future<Integer> ttdTracker = CompositeFuture.all(tokenTracker, tenantTracker)
                        .compose(ok -> {
                            final Integer ttdParam = HttpUtils.getTimeTilDisconnect(ctx);
                            return getTimeUntilDisconnect(tenantTracker.result(), ttdParam).map(effectiveTtd -> {
                                if (effectiveTtd != null) {
                                    currentSpan.setTag(MessageHelper.APP_PROPERTY_DEVICE_TTD, effectiveTtd);
                                }
                                return effectiveTtd;
                            });
                        });
                final Future<MessageConsumer> commandConsumerTracker = ttdTracker
                        .compose(ttd -> createCommandConsumer(ttd, tenant, deviceId, ctx, responseReady, currentSpan));

                CompositeFuture.all(senderTracker, commandConsumerTracker)
                .compose(ok -> {

                        final MessageSender sender = senderTracker.result();

                        final Integer ttd = Optional.ofNullable(commandConsumerTracker.result()).map(c -> ttdTracker.result())
                                .orElse(null);
                        final Message downstreamMessage = newMessage(
                                ResourceIdentifier.from(endpoint.getCanonicalName(), tenant, deviceId),
                                sender.isRegistrationAssertionRequired(),
                                ctx.request().uri(),
                                contentType,
                                payload,
                                tokenTracker.result(),
                                ttd);
                        customizeDownstreamMessage(downstreamMessage, ctx);

                        addConnectionCloseHandler(ctx, commandConsumerTracker.result(), tenant, deviceId, currentSpan);

                        if (MetricsTags.QoS.AT_MOST_ONCE.equals(qos)) {
                            return CompositeFuture.all(
                                    sender.send(downstreamMessage, currentSpan.context()),
                                    responseReady)
                                    .map(s -> (Void) null);
                        } else {
                            // unsettled
                            return CompositeFuture.all(
                                    sender.sendAndWaitForOutcome(downstreamMessage, currentSpan.context()),
                                    responseReady)
                                    .map(s -> (Void) null);
                        }
                }).recover(t -> {
                    if (t instanceof ResourceConflictException) {
                        // simply return an empty response
                        LOG.debug("ignoring empty notification [tenant: {}, device-id: {}], command consumer is already in use",
                                tenant, deviceId);
                        return Future.succeededFuture();
                    } else {
                        return Future.failedFuture(t);
                    }
                }).map(proceed -> {

                    if (ctx.response().closed()) {
                        LOG.debug("failed to send http response for [{}] message from device [tenantId: {}, deviceId: {}]: response already closed",
                                endpoint, tenant, deviceId);
                        TracingHelper.logError(currentSpan, "failed to send HTTP response to device: response already closed");
                        currentSpan.finish();
                    } else {
                        final CommandContext commandContext = ctx.get(CommandContext.KEY_COMMAND_CONTEXT);
                        setResponsePayload(ctx.response(), commandContext, currentSpan);
                        ctx.addBodyEndHandler(ok -> {
                            LOG.trace("successfully processed [{}] message for device [tenantId: {}, deviceId: {}]",
                                    endpoint, tenant, deviceId);
                            if (commandContext != null) {
                                commandContext.getCurrentSpan().log("forwarded command to device in HTTP response body");
                                commandContext.accept();
                                metrics.reportCommand(
                                        commandContext.getCommand().isOneWay() ? Direction.ONE_WAY : Direction.REQUEST,
                                        tenant,
                                        ProcessingOutcome.FORWARDED,
                                        commandContext.getCommand().getPayloadSize(),
                                        getMicrometerSample(commandContext));
                            }
                            metrics.reportTelemetry(
                                    endpoint,
                                    tenant,
                                    ProcessingOutcome.FORWARDED,
                                    qos,
                                    payload.length(),
                                    getTtdStatus(ctx),
                                    getMicrometerSample(ctx));
                            currentSpan.finish();
                            // the command consumer is used for a single request only
                            // we can close the consumer only AFTER we have accepted a
                            // potential command
                            Optional.ofNullable(commandConsumerTracker.result()).ifPresent(consumer -> consumer.close(null));
                        });
                        ctx.response().exceptionHandler(t -> {
                            LOG.debug("failed to send http response for [{}] message from device [tenantId: {}, deviceId: {}]",
                                    endpoint, tenant, deviceId, t);
                            if (commandContext != null) {
                                commandContext.getCurrentSpan().log("failed to forward command to device in HTTP response body");
                                TracingHelper.logError(commandContext.getCurrentSpan(), t);
                                commandContext.release();
                                metrics.reportCommand(
                                        commandContext.getCommand().isOneWay() ? Direction.ONE_WAY : Direction.REQUEST,
                                        tenant,
                                        ProcessingOutcome.UNDELIVERABLE,
                                        commandContext.getCommand().getPayloadSize(),
                                        getMicrometerSample(commandContext));
                            }
                            currentSpan.log("failed to send HTTP response to device");
                            TracingHelper.logError(currentSpan, t);
                            currentSpan.finish();
                            // the command consumer is used for a single request only
                            // we can close the consumer only AFTER we have released a
                            // potential command
                            Optional.ofNullable(commandConsumerTracker.result()).ifPresent(consumer -> consumer.close(null));
                        });
                        ctx.response().end();
                    }

                    return proceed;

                }).recover(t -> {

                    LOG.debug("cannot process [{}] message from device [tenantId: {}, deviceId: {}]",
                            endpoint, tenant, deviceId, t);
                    final CommandContext commandContext = ctx.get(CommandContext.KEY_COMMAND_CONTEXT);
                    if (commandContext != null) {
                        commandContext.release();
                    }
                    // the command consumer is used for a single request only
                    // we can close the consumer only AFTER we have released a
                    // potential command
                    Optional.ofNullable(commandConsumerTracker.result()).ifPresent(consumer -> consumer.close(null));

                    final ProcessingOutcome outcome;
                    if (ClientErrorException.class.isInstance(t)) {
                        outcome = ProcessingOutcome.UNPROCESSABLE;
                        ctx.fail(t);
                    } else {
                        outcome = ProcessingOutcome.UNDELIVERABLE;
                        HttpUtils.serviceUnavailable(ctx, 2, "temporarily unavailable");
                    }
                    metrics.reportTelemetry(
                            endpoint,
                            tenant,
                            outcome,
                            qos,
                            payload.length(),
                            getTtdStatus(ctx),
                            getMicrometerSample(ctx));
                    TracingHelper.logError(currentSpan, t);
                    currentSpan.finish();
                    return Future.failedFuture(t);
                });
            }
        }
    }

    /**
     * Adds a handler for tidying up when a device closes the HTTP connection before
     * a response could be sent.
     * <p>
     * The handler will close the message consumer and increment the metric for expired TTDs.
     * 
     * @param ctx The context to retrieve cookies and the HTTP response from.
     * @param messageConsumer The message consumer to receive a command. If {@code null}, no handler is added.
     * @param tenantId The tenant that the device belongs to.
     * @param deviceId The identifier of the device.
     * @param currentSpan The <em>OpenTracing</em> Span used for tracking the processing of the request.
     */
    private void addConnectionCloseHandler(
            final RoutingContext ctx,
            final MessageConsumer messageConsumer,
            final String tenantId,
            final String deviceId,
            final Span currentSpan) {

        if (messageConsumer != null && !ctx.response().closed()) {
            ctx.response().closeHandler(v -> {
                LOG.debug("device [tenant: {}, device-id: {}] closed connection before response could be sent",
                        tenantId, deviceId);
                currentSpan.log("device closed connection");
                cancelCommandReceptionTimer(ctx);
                messageConsumer.close(null);
            });
        }
    }

    private void setResponsePayload(final HttpServerResponse response, final CommandContext commandContext, final Span currentSpan) {
        if (commandContext == null) {
            response.setStatusCode(HttpURLConnection.HTTP_ACCEPTED);
        } else {
            final Command command = commandContext.getCommand();
            response.putHeader(Constants.HEADER_COMMAND, command.getName());
            currentSpan.setTag(Constants.HEADER_COMMAND, command.getName());
            LOG.trace("adding command [name: {}, request-id: {}] to response for device [tenant-id: {}, device-id: {}]",
                    command.getName(), command.getRequestId(), command.getTenant(), command.getDeviceId());
            if (!command.isOneWay()) {
                response.putHeader(Constants.HEADER_COMMAND_REQUEST_ID, command.getRequestId());
                currentSpan.setTag(Constants.HEADER_COMMAND_REQUEST_ID, command.getRequestId());
            }

            response.setStatusCode(HttpURLConnection.HTTP_OK);
            HttpUtils.setResponseBody(response, command.getPayload(), command.getContentType());
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
     * @return A future indicating the outcome of the operation.
     *         <p>
     *         The future will be completed with the created message consumer or {@code null}, if
     *         the response can be sent back to the device without waiting for a command.
     *         <p>
     *         The future will be failed with a {@code ServiceInvocationException} if the
     *         message consumer could not be created.
     *         The future will be failed with a {@code ResourceConflictException} if the
     *         message consumer for the device is already in use and the request contains
     *         an empty notification (which does not need to be forwarded downstream).
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
            return getCommandConnection().createCommandConsumer(
                    tenantId,
                    deviceId,
                    commandContext -> {

                        Tags.COMPONENT.set(commandContext.getCurrentSpan(), getTypeName());
                        final Command command = commandContext.getCommand();
                        final Sample commandSample = getMetrics().startTimer();
                        if (command.isValid()) {
                            if (responseReady.isComplete()) {
                                // the timer has already fired, release the command
                                getMetrics().reportCommand(
                                        command.isOneWay() ? Direction.ONE_WAY : Direction.REQUEST,
                                        tenantId,
                                        ProcessingOutcome.UNDELIVERABLE,
                                        command.getPayloadSize(),
                                        commandSample);
                                commandContext.release();
                            } else {
                                addMicrometerSample(commandContext, commandSample);
                                // put command context to routing context and notify
                                ctx.put(CommandContext.KEY_COMMAND_CONTEXT, commandContext);
                                cancelCommandReceptionTimer(ctx);
                                setTtdStatus(ctx, TtdStatus.COMMAND);
                                responseReady.tryComplete();
                            }
                        } else {
                            getMetrics().reportCommand(
                                    command.isOneWay() ? Direction.ONE_WAY : Direction.REQUEST,
                                    tenantId,
                                    ProcessingOutcome.UNPROCESSABLE,
                                    command.getPayloadSize(),
                                    commandSample);
                            commandContext.reject(new ErrorCondition(Constants.AMQP_BAD_REQUEST, "malformed command message"));
                        }
                        // we do not issue any new credit because the
                        // consumer is supposed to deliver a single command
                        // only per HTTP request
                    },
                    remoteDetach -> {
                        LOG.debug("peer closed command receiver link [tenant-id: {}, device-id: {}]", tenantId, deviceId);
                        // command consumer is closed by closeHandler, no explicit close necessary here
                    }).map(consumer -> {
                        if (!responseReady.isComplete()) {
                            // if the request was not responded already, add a timer for triggering an empty response
                            addCommandReceptionTimer(ctx, responseReady, ttdSecs);
                        }
                        return consumer;
                    }).recover(t -> {
                        if (t instanceof ResourceConflictException) {
                            // another request from the same device that contains
                            // a TTD value is already being processed
                            if (HttpUtils.isEmptyNotification(ctx)) {
                                // no need to forward message downstream
                                return Future.failedFuture(t);
                            } else {
                                // let the other request handle the command (if any)
                                responseReady.tryComplete();
                                return Future.succeededFuture();
                            }
                        } else {
                            return Future.failedFuture(t);
                        }
                    });
        }
    }

    /**
     * Sets a timer to trigger the sending of a (empty) response to a device
     * if no command has been received from an application within a
     * given amount of time.
     * <p>
     * The created timer's ID is put to the routing context using key {@link #KEY_TIMER_ID}.
     *
     * @param ctx The device's currently executing HTTP request.
     * @param responseReady The future to complete when the time has expired.
     * @param delaySecs The number of seconds to wait for a command.
     */
    private void addCommandReceptionTimer(
            final RoutingContext ctx,
            final Future<Void> responseReady,
            final long delaySecs) {

        final Long timerId = ctx.vertx().setTimer(delaySecs * 1000L, id -> {

            LOG.trace("time to wait [{}s] for command expired [timer id: {}]", delaySecs, id);

            if (responseReady.isComplete()) {
                // a command has been sent to the device already
                LOG.trace("response already sent, nothing to do ...");
            } else {
                // no command to be sent,
                // send empty response
                setTtdStatus(ctx, TtdStatus.EXPIRED);
                responseReady.complete();
            }
        });

        LOG.trace("adding command reception timer [id: {}]", timerId);

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
    public final void uploadCommandResponseMessage(
            final RoutingContext ctx,
            final String tenant,
            final String deviceId,
            final String commandRequestId,
            final Integer responseStatus) {

        Objects.requireNonNull(ctx);
        Objects.requireNonNull(tenant);
        Objects.requireNonNull(deviceId);

        final Buffer payload = ctx.getBody();
        final String contentType = HttpUtils.getContentType(ctx);

        LOG.debug("processing response to command [tenantId: {}, deviceId: {}, cmd-req-id: {}, status code: {}]",
                tenant, deviceId, commandRequestId, responseStatus);

        final CommandResponse commandResponse = CommandResponse.from(commandRequestId, tenant, deviceId, payload,
                contentType, responseStatus);

        if (commandResponse == null) {
            metrics.reportCommand(
                    Direction.RESPONSE,
                    tenant,
                    ProcessingOutcome.UNPROCESSABLE,
                    payload.length(),
                    getMicrometerSample(ctx));
            HttpUtils.badRequest(
                    ctx,
                    String.format("command-request-id [%s] or status code [%s] is missing/invalid",
                            commandRequestId, responseStatus));
        } else {

            final Device authenticatedDevice = getAuthenticatedDevice(ctx);
            final Span currentSpan = tracer.buildSpan("upload Command response")
                    .asChildOf(TracingHandler.serverSpanContext(ctx))
                    .ignoreActiveSpan()
                    .withTag(Tags.COMPONENT.getKey(), getTypeName())
                    .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_CLIENT)
                    .withTag(MessageHelper.APP_PROPERTY_TENANT_ID, tenant)
                    .withTag(MessageHelper.APP_PROPERTY_DEVICE_ID, deviceId)
                    .withTag(Constants.HEADER_COMMAND_RESPONSE_STATUS, responseStatus)
                    .withTag(Constants.HEADER_COMMAND_REQUEST_ID, commandRequestId)
                    .withTag(TracingHelper.TAG_AUTHENTICATED.getKey(), authenticatedDevice != null)
                    .start();
            final Future<JsonObject> deviceRegistrationTracker = getRegistrationAssertion(
                    tenant,
                    deviceId,
                    authenticatedDevice,
                    currentSpan.context());
            final Future<Void> tenantEnabledTracker = getTenantConfiguration(tenant, currentSpan.context())
                    .compose(tenantObject -> isAdapterEnabled(tenantObject).map(ok -> null));
            CompositeFuture.all(deviceRegistrationTracker, tenantEnabledTracker)
                    .compose(ok -> sendCommandResponse(tenant, commandResponse, currentSpan.context()))
                    .map(delivery -> {
                        LOG.trace("delivered command response [command-request-id: {}] to application",
                                commandRequestId);
                        currentSpan.log("delivered command response to application");
                        metrics.reportCommand(
                                Direction.RESPONSE,
                                tenant,
                                ProcessingOutcome.FORWARDED,
                                payload.length(),
                                getMicrometerSample(ctx));
                        ctx.response().setStatusCode(HttpURLConnection.HTTP_ACCEPTED);
                        ctx.response().end();
                        return delivery;
                    }).otherwise(t -> {
                        LOG.debug("could not send command response [command-request-id: {}] to application",
                                commandRequestId, t);
                        TracingHelper.logError(currentSpan, t);
                        currentSpan.finish();
                        final ProcessingOutcome outcome = t instanceof ClientErrorException ?
                                ProcessingOutcome.UNPROCESSABLE : ProcessingOutcome.UNDELIVERABLE;
                        metrics.reportCommand(
                                Direction.RESPONSE,
                                tenant,
                                outcome,
                                payload.length(),
                                getMicrometerSample(ctx));
                        ctx.fail(t);
                        return null;
                    });
        }
    }

    private static MetricsTags.QoS getQoSLevel(final EndpointType endpoint, final String qosValue) {

        if (endpoint == EndpointType.EVENT) {
            return QoS.AT_LEAST_ONCE;
        } else if (qosValue == null) {
            return MetricsTags.QoS.AT_MOST_ONCE;
        } else {
            try {
                return MetricsTags.QoS.from(Integer.parseInt(qosValue));
            } catch (final NumberFormatException e) {
                return MetricsTags.QoS.UNKNOWN;
            }
        }
    }
}
