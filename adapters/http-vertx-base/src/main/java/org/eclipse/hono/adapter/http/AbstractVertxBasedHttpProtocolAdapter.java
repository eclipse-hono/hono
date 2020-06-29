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

package org.eclipse.hono.adapter.http;

import java.net.HttpURLConnection;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.qpid.proton.amqp.transport.ErrorCondition;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.auth.Device;
import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.Command;
import org.eclipse.hono.client.CommandContext;
import org.eclipse.hono.client.CommandResponse;
import org.eclipse.hono.client.DownstreamSender;
import org.eclipse.hono.client.ProtocolAdapterCommandConsumer;
import org.eclipse.hono.service.AbstractProtocolAdapterBase;
import org.eclipse.hono.service.http.ComponentMetaDataDecorator;
import org.eclipse.hono.service.http.DefaultFailureHandler;
import org.eclipse.hono.service.http.HttpContext;
import org.eclipse.hono.service.http.HttpUtils;
import org.eclipse.hono.service.http.TenantTraceSamplingHandler;
import org.eclipse.hono.service.http.TracingHandler;
import org.eclipse.hono.service.http.WebSpanDecorator;
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
import io.opentracing.tag.Tags;
import io.vertx.core.AsyncResult;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Route;
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
    public final void doStart(final Promise<Void> startPromise) {
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
            })
            .compose(ok -> {
                try {
                    onStartupSuccess();
                    return Future.succeededFuture((Void) null);
                } catch (final Exception e) {
                    log.error("error in onStartupSuccess", e);
                    return Future.failedFuture(e);
                }
            })
            .onComplete(startPromise);
    }

    private Sample getMicrometerSample(final RoutingContext ctx) {
        return ctx.get(KEY_MICROMETER_SAMPLE);
    }

    private void setTtdStatus(final RoutingContext ctx, final TtdStatus status) {
        ctx.put(TtdStatus.class.getName(), status);
    }

    private TracingHandler createTracingHandler() {
        final Map<String, String> customTags = new HashMap<>();
        customTags.put(Tags.COMPONENT.getKey(), getTypeName());
        addCustomTags(customTags);
        final List<WebSpanDecorator> decorators = new ArrayList<>();
        decorators.add(new ComponentMetaDataDecorator(customTags));
        addCustomSpanDecorators(decorators);
        return new TracingHandler(tracer, decorators);
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
     * This method creates a router instance along with a route matching all request. That route is initialized with the
     * following handlers and failure handlers:
     * <ol>
     * <li>a handler to add a Micrometer {@code Timer.Sample} to the routing context,</li>
     * <li>a handler and failure handler that creates tracing data for all server requests,</li>
     * <li>a handler to log when the connection is closed prematurely,</li>
     * <li>a default failure handler,</li>
     * <li>a handler limiting the body size of requests to the maximum payload size set in the <em>config</em>
     * properties,</li>
     * <li>(optional) a handler that applies the trace sampling priority configured for the tenant/auth-id of a
     * request.</li>
     * </ol>
     *
     * @return The newly created router (never {@code null}).
     */
    protected Router createRouter() {

        final Router router = Router.router(vertx);
        final Route matchAllRoute = router.route();
        // the handlers and failure handlers are added here in a specific order!
        // 1. handler to start the metrics timer
        matchAllRoute.handler(ctx -> {
            ctx.put(KEY_MICROMETER_SAMPLE, getMetrics().startTimer());
            ctx.next();
        });
        // 2. tracing handler
        final TracingHandler tracingHandler = createTracingHandler();
        matchAllRoute.handler(tracingHandler).failureHandler(tracingHandler);

        // 3. handler to log when the connection is closed prematurely
        matchAllRoute.handler(ctx -> {
            if (!ctx.response().closed() && !ctx.response().ended()) {
                ctx.response().closeHandler(v -> logResponseGettingClosedPrematurely(ctx));
            }
            ctx.next();
        });

        // 4. default handler for failed routes
        matchAllRoute.failureHandler(new DefaultFailureHandler());

        // 5. BodyHandler with request size limit
        log.info("limiting size of inbound request body to {} bytes", getConfig().getMaxPayloadSize());
        final BodyHandler bodyHandler = BodyHandler.create(DEFAULT_UPLOADS_DIRECTORY)
                .setBodyLimit(getConfig().getMaxPayloadSize());
        matchAllRoute.handler(bodyHandler);

        // 6. handler to set the trace sampling priority
        Optional.ofNullable(getTenantTraceSamplingHandler())
                .ifPresent(tenantTraceSamplingHandler -> matchAllRoute.handler(tenantTraceSamplingHandler));
        return router;
    }

    /**
     * Gets a handler that determines the tenant associated with a request and applies the tenant specific trace
     * sampling configuration (if set).
     * <p>
     * This method returns {@code null} by default.
     * <p>
     * Subclasses may override this method in order to return an appropriate handler.
     *
     * @return The handler or {@code null}.
     */
    protected TenantTraceSamplingHandler getTenantTraceSamplingHandler() {
        return null;
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
    protected void customizeDownstreamMessage(final Message downstreamMessage, final HttpContext ctx) {
        // this default implementation does nothing
    }

    private Future<HttpServer> bindSecureHttpServer(final Router router) {

        if (isSecurePortEnabled()) {
            final Promise<HttpServer> result = Promise.promise();
            final String bindAddress = server == null ? getConfig().getBindAddress() : "?";
            if (server == null) {
                server = vertx.createHttpServer(getHttpServerOptions());
            }
            server.requestHandler(router).listen(done -> {
                if (done.succeeded()) {
                    log.info("secure http server listening on {}:{}", bindAddress, server.actualPort());
                    result.complete(done.result());
                } else {
                    log.error("error while starting up secure http server", done.cause());
                    result.fail(done.cause());
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
            insecureServer.requestHandler(router).listen(done -> {
                if (done.succeeded()) {
                    log.info("insecure http server listening on {}:{}", bindAddress, insecureServer.actualPort());
                    result.complete(done.result());
                } else {
                    log.error("error while starting up insecure http server", done.cause());
                    result.fail(done.cause());
                }
            });
            return result.future();
        } else {
            return Future.succeededFuture();
        }
    }

    @Override
    public final void doStop(final Promise<Void> stopPromise) {

        try {
            preShutdown();
        } catch (final Exception e) {
            log.error("error in preShutdown", e);
        }

        final Promise<Void> serverStopTracker = Promise.promise();
        if (server != null) {
            server.close(serverStopTracker);
        } else {
            serverStopTracker.complete();
        }

        final Promise<Void> insecureServerStopTracker = Promise.promise();
        if (insecureServer != null) {
            insecureServer.close(insecureServerStopTracker);
        } else {
            insecureServerStopTracker.complete();
        }

        CompositeFuture.all(serverStopTracker.future(), insecureServerStopTracker.future())
        .compose(v -> postShutdown())
        .onComplete(stopPromise);
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
     * This method simply invokes {@link #uploadTelemetryMessage(HttpContext, String, String, Buffer, String)}
     * with objects retrieved from the routing context.
     *
     * @param ctx The context to retrieve the message payload and content type from.
     * @param tenant The tenant of the device that has produced the data.
     * @param deviceId The id of the device that has produced the data.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    public final void uploadTelemetryMessage(final HttpContext ctx, final String tenant, final String deviceId) {

        uploadTelemetryMessage(
                Objects.requireNonNull(ctx),
                Objects.requireNonNull(tenant),
                Objects.requireNonNull(deviceId),
                ctx.getRoutingContext().getBody(),
                ctx.getContentType());
    }

    /**
     * Uploads a telemetry message to Hono.
     * <p>
     * This method always sends a response to the device. The status code will be set
     * as specified in the
     * <a href="https://www.eclipse.org/hono/docs/user-guide/http-adapter/#publish-telemetry-data-authenticated-device">
     * HTTP adapter User Guide</a>.
     *
     * @param ctx The context to retrieve cookies and the HTTP response from.
     * @param tenant The tenant of the device that has produced the data.
     * @param deviceId The id of the device that has produced the data.
     * @param payload The message payload to send.
     * @param contentType The content type of the message payload.
     * @throws NullPointerException if any of response, tenant or device ID is {@code null}.
     */
    public final void uploadTelemetryMessage(final HttpContext ctx, final String tenant, final String deviceId,
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
     * This method simply invokes {@link #uploadEventMessage(HttpContext, String, String, Buffer, String)}
     * with objects retrieved from the routing context.
     *
     * @param ctx The context to retrieve the message payload and content type from.
     * @param tenant The tenant of the device that has produced the data.
     * @param deviceId The id of the device that has produced the data.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    public final void uploadEventMessage(final HttpContext ctx, final String tenant, final String deviceId) {

        uploadEventMessage(
                Objects.requireNonNull(ctx),
                Objects.requireNonNull(tenant),
                Objects.requireNonNull(deviceId),
                ctx.getRoutingContext().getBody(),
                ctx.getContentType());
    }

    /**
     * Uploads an event message to Hono.
     * <p>
     * This method always sends a response to the device. The status code will be set
     * as specified in the
     * <a href="https://www.eclipse.org/hono/docs/user-guide/http-adapter/#publish-an-event-authenticated-device">
     * HTTP adapter User Guide</a>.
     *
     * @param ctx The context to retrieve cookies and the HTTP response from.
     * @param tenant The tenant of the device that has produced the data.
     * @param deviceId The id of the device that has produced the data.
     * @param payload The message payload to send.
     * @param contentType The content type of the message payload.
     * @throws NullPointerException if any of response, tenant or device ID is {@code null}.
     */
    public final void uploadEventMessage(final HttpContext ctx, final String tenant, final String deviceId,
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
            final HttpContext ctx,
            final String tenant,
            final String deviceId,
            final Buffer payload,
            final String contentType,
            final Future<DownstreamSender> senderTracker,
            final MetricsTags.EndpointType endpoint) {

        if (!isPayloadOfIndicatedType(payload, contentType)) {
            HttpUtils.badRequest(ctx.getRoutingContext(), String.format("content type [%s] does not match payload", contentType));
            return;
        }
        final String qosHeaderValue = ctx.request().getHeader(Constants.HEADER_QOS_LEVEL);
        final MetricsTags.QoS qos = getQoSLevel(endpoint, qosHeaderValue);
        if (qos == MetricsTags.QoS.UNKNOWN) {
            HttpUtils.badRequest(ctx.getRoutingContext(), "unsupported QoS-Level header value");
            return;
        }

        final Device authenticatedDevice = ctx.getAuthenticatedDevice();
        final String gatewayId = authenticatedDevice != null && !deviceId.equals(authenticatedDevice.getDeviceId())
                ? authenticatedDevice.getDeviceId()
                : null;
        final Span currentSpan = TracingHelper
                .buildChildSpan(tracer, TracingHandler.serverSpanContext(ctx.getRoutingContext()),
                        "upload " + endpoint.getCanonicalName(), getTypeName())
                .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_CLIENT)
                .withTag(TracingHelper.TAG_TENANT_ID, tenant)
                .withTag(TracingHelper.TAG_DEVICE_ID, deviceId)
                .withTag(TracingHelper.TAG_AUTHENTICATED.getKey(), authenticatedDevice != null)
                .withTag(Constants.HEADER_QOS_LEVEL, qos.asTag().getValue())
                .start();

        final Promise<Void> responseReady = Promise.promise();
        final Future<JsonObject> tokenTracker = getRegistrationAssertion(
                tenant,
                deviceId,
                authenticatedDevice,
                currentSpan.context());
        final int payloadSize = Optional.ofNullable(payload)
                .map(ok -> payload.length())
                .orElse(0);
        final Future<TenantObject> tenantTracker = getTenantConfiguration(tenant, currentSpan.context());
        final Future<TenantObject> tenantValidationTracker = tenantTracker
                .compose(tenantObject -> CompositeFuture
                        .all(isAdapterEnabled(tenantObject),
                                checkMessageLimit(tenantObject, payloadSize, currentSpan.context()))
                        .map(success -> tenantObject));

        // we only need to consider TTD if the device and tenant are enabled and the adapter
        // is enabled for the tenant
        final Future<Integer> ttdTracker = CompositeFuture.all(tenantValidationTracker, tokenTracker)
                .compose(ok -> {
                    final Integer ttdParam = getTimeUntilDisconnectFromRequest(ctx);
                    return getTimeUntilDisconnect(tenantTracker.result(), ttdParam).map(effectiveTtd -> {
                        if (effectiveTtd != null) {
                            currentSpan.setTag(MessageHelper.APP_PROPERTY_DEVICE_TTD, effectiveTtd);
                        }
                        return effectiveTtd;
                    });
                });
        final Future<ProtocolAdapterCommandConsumer> commandConsumerTracker = ttdTracker
                .compose(ttd -> createCommandConsumer(
                        ttd,
                        tenantTracker.result(),
                        deviceId,
                        gatewayId,
                        ctx.getRoutingContext(),
                        responseReady,
                        currentSpan));

        // "join" used here to make sure that the "commandConsumerTracker" is completed when handling
        // a failed "senderTracker" below, allowing a contained command consumer to be closed there.
        CompositeFuture.join(senderTracker, commandConsumerTracker)
        .compose(ok -> {

            final DownstreamSender sender = senderTracker.result();

            final Integer ttd = Optional.ofNullable(commandConsumerTracker.result())
                    .map(c -> ttdTracker.result())
                    .orElse(null);
            final Message downstreamMessage = newMessage(
                    ctx.getRequestedQos(),
                    ResourceIdentifier.from(endpoint.getCanonicalName(), tenant, deviceId),
                    ctx.request().uri(),
                    contentType,
                    payload,
                    tenantTracker.result(),
                    tokenTracker.result(),
                    ttd,
                    EndpointType.EVENT.equals(endpoint) ? ctx.getTimeToLive() : null);
            customizeDownstreamMessage(downstreamMessage, ctx);

            setTtdRequestConnectionCloseHandler(ctx.getRoutingContext(), commandConsumerTracker.result(), tenant, deviceId, currentSpan);

            if (MetricsTags.QoS.AT_MOST_ONCE.equals(qos)) {
                return CompositeFuture.all(
                        sender.send(downstreamMessage, currentSpan.context()),
                        responseReady.future())
                        .map(s -> (Void) null);
            } else {
                // unsettled
                return CompositeFuture.all(
                        sender.sendAndWaitForOutcome(downstreamMessage, currentSpan.context()),
                        responseReady.future())
                        .map(s -> (Void) null);
            }
        })
        .map(proceed -> {

            if (ctx.response().closed()) {
                log.debug("failed to send http response for [{}] message from device [tenantId: {}, deviceId: {}]: response already closed",
                        endpoint, tenant, deviceId);
                TracingHelper.logError(currentSpan, "failed to send HTTP response to device: response already closed");
                currentSpan.finish();
                ctx.response().end(); // close the response here, ensuring that the TracingHandler bodyEndHandler gets called
            } else {
                final CommandContext commandContext = ctx.get(CommandContext.KEY_COMMAND_CONTEXT);
                setResponsePayload(ctx.response(), commandContext, currentSpan);
                ctx.getRoutingContext().addBodyEndHandler(ok -> {
                    log.trace("successfully processed [{}] message for device [tenantId: {}, deviceId: {}]",
                            endpoint, tenant, deviceId);
                    if (commandContext != null) {
                        commandContext.getCurrentSpan().log("forwarded command to device in HTTP response body");
                        commandContext.accept();
                        metrics.reportCommand(
                                commandContext.getCommand().isOneWay() ? Direction.ONE_WAY : Direction.REQUEST,
                                tenant,
                                tenantTracker.result(),
                                ProcessingOutcome.FORWARDED,
                                commandContext.getCommand().getPayloadSize(),
                                getMicrometerSample(commandContext));
                    }
                    metrics.reportTelemetry(
                            endpoint,
                            tenant,
                            tenantTracker.result(),
                            ProcessingOutcome.FORWARDED,
                            qos,
                            payloadSize,
                            ctx.getTtdStatus(),
                            getMicrometerSample(ctx.getRoutingContext()));
                    // Before Hono 1.2, closing the consumer needed to be done AFTER having accepted a command (consumer used for single request only);
                    // now however, this isn't needed anymore (consumer.close() doesn't actually close the link anymore). So, this could be changed here to close the consumer earlier already.
                    Optional.ofNullable(commandConsumerTracker.result()).ifPresentOrElse(
                            consumer -> consumer.close(currentSpan.context())
                                    .onComplete(res -> currentSpan.finish()),
                            currentSpan::finish);
                });
                ctx.response().exceptionHandler(t -> {
                    log.debug("failed to send http response for [{}] message from device [tenantId: {}, deviceId: {}]",
                            endpoint, tenant, deviceId, t);
                    if (commandContext != null) {
                        commandContext.getCurrentSpan().log("failed to forward command to device in HTTP response body");
                        TracingHelper.logError(commandContext.getCurrentSpan(), t);
                        commandContext.release();
                        metrics.reportCommand(
                                commandContext.getCommand().isOneWay() ? Direction.ONE_WAY : Direction.REQUEST,
                                tenant,
                                tenantTracker.result(),
                                ProcessingOutcome.UNDELIVERABLE,
                                commandContext.getCommand().getPayloadSize(),
                                getMicrometerSample(commandContext));
                    }
                    currentSpan.log("failed to send HTTP response to device");
                    TracingHelper.logError(currentSpan, t);
                    // Before Hono 1.2, closing the consumer needed to be done AFTER having released a command (consumer used for single request only);
                    // now however, this isn't needed anymore (consumer.close() doesn't actually close the link anymore). So, this could be changed here to close the consumer earlier already.
                    Optional.ofNullable(commandConsumerTracker.result()).ifPresentOrElse(
                            consumer -> consumer.close(currentSpan.context())
                                    .onComplete(res -> currentSpan.finish()),
                            currentSpan::finish);
                });
                ctx.response().end();
            }

            return proceed;

        })
        .recover(t -> {

            log.debug("cannot process [{}] message from device [tenantId: {}, deviceId: {}]",
                    endpoint, tenant, deviceId, t);
            final boolean responseClosedPrematurely = ctx.response().closed();
            final CommandContext commandContext = ctx.get(CommandContext.KEY_COMMAND_CONTEXT);
            if (commandContext != null) {
                commandContext.release();
            }
            final ProcessingOutcome outcome;
            if (ClientErrorException.class.isInstance(t)) {
                outcome = ProcessingOutcome.UNPROCESSABLE;
                ctx.fail(t);
            } else {
                outcome = ProcessingOutcome.UNDELIVERABLE;
                HttpUtils.serviceUnavailable(ctx.getRoutingContext(), 2, "temporarily unavailable");
            }
            if (responseClosedPrematurely) {
                log.debug("failed to send http response for [{}] message from device [tenantId: {}, deviceId: {}]: response already closed",
                        endpoint, tenant, deviceId);
                TracingHelper.logError(currentSpan, "failed to send HTTP response to device: response already closed");
            }
            metrics.reportTelemetry(
                    endpoint,
                    tenant,
                    tenantTracker.result(),
                    outcome,
                    qos,
                    payloadSize,
                    ctx.getTtdStatus(),
                    getMicrometerSample(ctx.getRoutingContext()));
            TracingHelper.logError(currentSpan, t);
            // Before Hono 1.2, closing the consumer needed to be done AFTER having released a command (consumer used for single request only);
            // now however, this isn't needed anymore (consumer.close() doesn't actually close the link anymore). So, this could be changed here to close the consumer earlier already.
            Optional.ofNullable(commandConsumerTracker.result()).ifPresentOrElse(
                    consumer -> consumer.close(currentSpan.context())
                            .onComplete(res -> currentSpan.finish()),
                    currentSpan::finish);
            return Future.failedFuture(t);
        });
    }

    private void logResponseGettingClosedPrematurely(final RoutingContext ctx) {
        log.trace("connection got closed before response could be sent");
        Optional.ofNullable(getRootSpan(ctx)).ifPresent(span -> {
            TracingHelper.logError(span, "connection got closed before response could be sent");
        });
    }

    private Span getRootSpan(final RoutingContext ctx) {
        final Object rootSpanObject = ctx.get(TracingHandler.CURRENT_SPAN);
        return rootSpanObject instanceof Span ? (Span) rootSpanObject : null;
    }

    /**
     * Extract the "time till disconnect" from the provided request.
     * <p>
     * The default behavior is to call {@link HttpContext#getTimeTillDisconnect()}. The method may be
     * overridden by protocol adapters that which not to use the default behavior.
     *
     * @param ctx The context to extract the TTD from.
     * @return The TTD in seconds, or {@code null} in case none is set, or it could not be parsed to an integer.
     */
    protected Integer getTimeUntilDisconnectFromRequest(final HttpContext ctx) {
        return ctx.getTimeTillDisconnect();
    }

    /**
     * Sets a handler for tidying up when a device closes the HTTP connection of a TTD request before
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
    private void setTtdRequestConnectionCloseHandler(
            final RoutingContext ctx,
            final ProtocolAdapterCommandConsumer messageConsumer,
            final String tenantId,
            final String deviceId,
            final Span currentSpan) {

        if (messageConsumer != null && !ctx.response().closed() && !ctx.response().ended()) {
            ctx.response().closeHandler(v -> {
                log.debug("device [tenant: {}, device-id: {}] closed connection before response could be sent",
                        tenantId, deviceId);
                currentSpan.log("device closed connection, stop waiting for command");
                cancelCommandReceptionTimer(ctx);
                // close command consumer
                messageConsumer.close(currentSpan.context())
                        .onComplete(r -> {
                            currentSpan.finish();
                            logResponseGettingClosedPrematurely(ctx);
                            ctx.response().end(); // close the response here, ensuring that the TracingHandler bodyEndHandler gets called
                        });
            });
        }
    }

    private void setResponsePayload(final HttpServerResponse response, final CommandContext commandContext, final Span currentSpan) {
        if (commandContext == null) {
            setEmptyResponsePayload(response, currentSpan);
        } else {
            setNonEmptyResponsePayload(response, commandContext, currentSpan);
        }
    }

    /**
     * Respond to a request with an empty command response.
     * <p>
     * The default implementation simply sets a status of {@link HttpURLConnection#HTTP_ACCEPTED}.
     *
     * @param response The response to update.
     * @param currentSpan The current tracing span.
     */
    protected void setEmptyResponsePayload(final HttpServerResponse response, final Span currentSpan) {
        response.setStatusCode(HttpURLConnection.HTTP_ACCEPTED);
    }

    /**
     * Response to a request with a non-empty command response.
     * <p>
     * The default implementation sets the command headers and the status to {@link HttpURLConnection#HTTP_OK}.
     *
     * @param response The response to update.
     * @param commandContext The command context, will not be {@code null}.
     * @param currentSpan The current tracing span.
     */
    protected void setNonEmptyResponsePayload(final HttpServerResponse response, final CommandContext commandContext,
            final Span currentSpan) {

        final Command command = commandContext.getCommand();
        response.putHeader(Constants.HEADER_COMMAND, command.getName());
        currentSpan.setTag(Constants.HEADER_COMMAND, command.getName());
        log.debug("adding command [name: {}, request-id: {}] to response for device [tenant-id: {}, device-id: {}]",
                command.getName(), command.getRequestId(), command.getTenant(), command.getDeviceId());

        if (!command.isOneWay()) {
            response.putHeader(Constants.HEADER_COMMAND_REQUEST_ID, command.getRequestId());
            currentSpan.setTag(Constants.HEADER_COMMAND_REQUEST_ID, command.getRequestId());
        }
        if (command.isTargetedAtGateway()) {
            response.putHeader(Constants.HEADER_COMMAND_TARGET_DEVICE, command.getOriginalDeviceId());
            currentSpan.setTag(Constants.HEADER_COMMAND_TARGET_DEVICE, command.getOriginalDeviceId());
        }

        response.setStatusCode(HttpURLConnection.HTTP_OK);
        HttpUtils.setResponseBody(response, command.getPayload(), command.getContentType());
    }

    /**
     * Creates a consumer for command messages to be sent to a device.
     *
     * @param ttdSecs The number of seconds the device waits for a command.
     * @param tenantObject The tenant configuration object.
     * @param deviceId The identifier of the device.
     * @param gatewayId The identifier of the gateway that is acting on behalf of the device
     *                  or {@code null} otherwise.
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
     * @throws NullPointerException if any of the parameters other than TTD or gatewayId is {@code null}.
     */
    protected final Future<ProtocolAdapterCommandConsumer> createCommandConsumer(
            final Integer ttdSecs,
            final TenantObject tenantObject,
            final String deviceId,
            final String gatewayId,
            final RoutingContext ctx,
            final Handler<AsyncResult<Void>> responseReady,
            final Span currentSpan) {

        Objects.requireNonNull(tenantObject);
        Objects.requireNonNull(deviceId);
        Objects.requireNonNull(ctx);
        Objects.requireNonNull(responseReady);
        Objects.requireNonNull(currentSpan);

        final AtomicBoolean requestProcessed = new AtomicBoolean(false);

        if (ttdSecs == null || ttdSecs <= 0) {
            // no need to wait for a command
            if (requestProcessed.compareAndSet(false, true)) {
                responseReady.handle(Future.succeededFuture());
            }
            return Future.succeededFuture();
        }

        currentSpan.setTag(MessageHelper.APP_PROPERTY_DEVICE_TTD, ttdSecs);
        final Handler<CommandContext> commandHandler = commandContext -> {

            Tags.COMPONENT.set(commandContext.getCurrentSpan(), getTypeName());
            final Command command = commandContext.getCommand();
            final Sample commandSample = getMetrics().startTimer();
            if (isCommandValid(command, currentSpan)) {

                if (requestProcessed.compareAndSet(false, true)) {
                    checkMessageLimit(tenantObject, command.getPayloadSize(), currentSpan.context())
                    .onComplete(result -> {
                        if (result.succeeded()) {
                            addMicrometerSample(commandContext, commandSample);
                            // put command context to routing context and notify
                            ctx.put(CommandContext.KEY_COMMAND_CONTEXT, commandContext);
                        } else {
                            commandContext.reject(getErrorCondition(result.cause()));
                            metrics.reportCommand(
                                    command.isOneWay() ? Direction.ONE_WAY : Direction.REQUEST,
                                    tenantObject.getTenantId(),
                                    tenantObject,
                                    ProcessingOutcome.from(result.cause()),
                                    command.getPayloadSize(),
                                    commandSample);
                        }
                        cancelCommandReceptionTimer(ctx);
                        setTtdStatus(ctx, TtdStatus.COMMAND);
                        responseReady.handle(Future.succeededFuture());
                    });
                } else {
                    // the timer has already fired, release the command
                    getMetrics().reportCommand(
                            command.isOneWay() ? Direction.ONE_WAY : Direction.REQUEST,
                            tenantObject.getTenantId(),
                            tenantObject,
                            ProcessingOutcome.UNDELIVERABLE,
                            command.getPayloadSize(),
                            commandSample);
                    log.debug("command for device has already fired [tenantId: {}, deviceId: {}]",
                            tenantObject.getTenantId(), deviceId);
                    commandContext.release();
                }

            } else {
                getMetrics().reportCommand(
                        command.isOneWay() ? Direction.ONE_WAY : Direction.REQUEST,
                        tenantObject.getTenantId(),
                        tenantObject,
                        ProcessingOutcome.UNPROCESSABLE,
                        command.getPayloadSize(),
                        commandSample);
                log.debug("command message is invalid: {}", command);
                commandContext.reject(new ErrorCondition(Constants.AMQP_BAD_REQUEST, "malformed command message"));
            }
            // we do not issue any new credit because the
            // consumer is supposed to deliver a single command
            // only per HTTP request
        };

        final Future<ProtocolAdapterCommandConsumer> commandConsumerFuture;
        if (gatewayId != null) {
            // gateway scenario
            commandConsumerFuture = getCommandConsumerFactory().createCommandConsumer(
                    tenantObject.getTenantId(),
                    deviceId,
                    gatewayId,
                    commandHandler,
                    Duration.ofSeconds(ttdSecs),
                    currentSpan.context());
        } else {
            commandConsumerFuture = getCommandConsumerFactory().createCommandConsumer(
                    tenantObject.getTenantId(),
                    deviceId,
                    commandHandler,
                    Duration.ofSeconds(ttdSecs),
                    currentSpan.context());
        }
        return commandConsumerFuture
                .map(consumer -> {
                    if (!requestProcessed.get()) {
                        // if the request was not responded already, add a timer for triggering an empty response
                        addCommandReceptionTimer(ctx, requestProcessed, responseReady, ttdSecs);
                    }
                    return consumer;
                });
    }

    /**
     * Validate if a command is valid and can be sent as response.
     * <p>
     * The default implementation will call {@link Command#isValid()}. Protocol adapters may override this, but should
     * consider calling the super method.
     *
     * @param command The command to validate, will never be {@code null}.
     * @param currentSpan The current tracing span.
     * @return {@code true} if the command is valid, {@code false} otherwise.
     */
    protected boolean isCommandValid(final Command command, final Span currentSpan) {
        return command.isValid();
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
            final AtomicBoolean requestProcessed,
            final Handler<AsyncResult<Void>> responseReady,
            final long delaySecs) {

        final Long timerId = ctx.vertx().setTimer(delaySecs * 1000L, id -> {

            log.trace("time to wait [{}s] for command expired [timer id: {}]", delaySecs, id);

            if (requestProcessed.compareAndSet(false, true)) {
                // no command to be sent,
                // send empty response
                setTtdStatus(ctx, TtdStatus.EXPIRED);
                responseReady.handle(Future.succeededFuture());
            } else {
                // a command has been sent to the device already
                log.trace("response already sent, nothing to do ...");
            }
        });

        log.trace("adding command reception timer [id: {}]", timerId);

        ctx.put(KEY_TIMER_ID, timerId);
    }

    private void cancelCommandReceptionTimer(final RoutingContext ctx) {

        final Long timerId = ctx.get(KEY_TIMER_ID);
        if (timerId != null && timerId >= 0) {
            if (ctx.vertx().cancelTimer(timerId)) {
                log.trace("Cancelled timer id {}", timerId);
            } else {
                log.debug("Could not cancel timer id {}", timerId);
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
            final HttpContext ctx,
            final String tenant,
            final String deviceId,
            final String commandRequestId,
            final Integer responseStatus) {

        Objects.requireNonNull(ctx);
        Objects.requireNonNull(tenant);
        Objects.requireNonNull(deviceId);

        final Buffer payload = ctx.getRoutingContext().getBody();
        final String contentType = ctx.getContentType();

        log.debug("processing response to command [tenantId: {}, deviceId: {}, cmd-req-id: {}, status code: {}]",
                tenant, deviceId, commandRequestId, responseStatus);

        final Device authenticatedDevice = ctx.getAuthenticatedDevice();
        final Span currentSpan = TracingHelper
                .buildChildSpan(tracer, TracingHandler.serverSpanContext(ctx.getRoutingContext()),
                        "upload Command response", getTypeName())
                .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_CLIENT)
                .withTag(TracingHelper.TAG_TENANT_ID, tenant)
                .withTag(TracingHelper.TAG_DEVICE_ID, deviceId)
                .withTag(Constants.HEADER_COMMAND_RESPONSE_STATUS, responseStatus)
                .withTag(Constants.HEADER_COMMAND_REQUEST_ID, commandRequestId)
                .withTag(TracingHelper.TAG_AUTHENTICATED.getKey(), authenticatedDevice != null)
                .start();

        final CommandResponse cmdResponseOrNull = CommandResponse.from(commandRequestId, tenant, deviceId, payload,
                contentType, responseStatus);
        final Future<TenantObject> tenantTracker = getTenantConfiguration(tenant, currentSpan.context());
        final Future<CommandResponse> commandResponseTracker = cmdResponseOrNull != null
                ? Future.succeededFuture(cmdResponseOrNull)
                : Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST,
                        String.format("command-request-id [%s] or status code [%s] is missing/invalid",
                                commandRequestId, responseStatus)));

        final int payloadSize = Optional.ofNullable(payload).map(Buffer::length).orElse(0);
        CompositeFuture.all(tenantTracker, commandResponseTracker)
                .compose(commandResponse -> {
                    final Future<JsonObject> deviceRegistrationTracker = getRegistrationAssertion(
                            tenant,
                            deviceId,
                            authenticatedDevice,
                            currentSpan.context());
                    final Future<Void> tenantValidationTracker = CompositeFuture
                            .all(isAdapterEnabled(tenantTracker.result()),
                                    checkMessageLimit(tenantTracker.result(), payloadSize, currentSpan.context()))
                            .map(ok -> null);

                    return CompositeFuture.all(tenantValidationTracker, deviceRegistrationTracker)
                            .compose(ok -> sendCommandResponse(tenant, commandResponseTracker.result(),
                                    currentSpan.context()))
                            .map(delivery -> {
                                log.trace("delivered command response [command-request-id: {}] to application",
                                        commandRequestId);
                                currentSpan.log("delivered command response to application");
                                currentSpan.finish();
                                metrics.reportCommand(
                                        Direction.RESPONSE,
                                        tenant,
                                        tenantTracker.result(),
                                        ProcessingOutcome.FORWARDED,
                                        payloadSize,
                                        getMicrometerSample(ctx.getRoutingContext()));
                                ctx.response().setStatusCode(HttpURLConnection.HTTP_ACCEPTED);
                                ctx.response().end();
                                return delivery;
                            });
                }).otherwise(t -> {
                    log.debug("could not send command response [command-request-id: {}] to application",
                            commandRequestId, t);
                    TracingHelper.logError(currentSpan, t);
                    currentSpan.finish();
                    metrics.reportCommand(
                            Direction.RESPONSE,
                            tenant,
                            tenantTracker.result(),
                            ProcessingOutcome.from(t),
                            payloadSize,
                            getMicrometerSample(ctx.getRoutingContext()));
                    ctx.fail(t);
                    return null;
                });
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
