/*******************************************************************************
 * Copyright (c) 2016, 2022 Contributors to the Eclipse Foundation
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
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

import org.eclipse.hono.adapter.AbstractProtocolAdapterBase;
import org.eclipse.hono.adapter.HttpContext;
import org.eclipse.hono.adapter.auth.device.CredentialsApiAuthProvider;
import org.eclipse.hono.adapter.auth.device.DeviceCredentials;
import org.eclipse.hono.auth.Device;
import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.ServerErrorException;
import org.eclipse.hono.client.command.Command;
import org.eclipse.hono.client.command.CommandConsumer;
import org.eclipse.hono.client.command.CommandContext;
import org.eclipse.hono.client.command.CommandResponse;
import org.eclipse.hono.service.http.DefaultFailureHandler;
import org.eclipse.hono.service.http.HttpServerSpanHelper;
import org.eclipse.hono.service.http.HttpUtils;
import org.eclipse.hono.service.metric.MetricsTags;
import org.eclipse.hono.service.metric.MetricsTags.Direction;
import org.eclipse.hono.service.metric.MetricsTags.EndpointType;
import org.eclipse.hono.service.metric.MetricsTags.ProcessingOutcome;
import org.eclipse.hono.service.metric.MetricsTags.TtdStatus;
import org.eclipse.hono.tracing.TenantTraceSamplingHelper;
import org.eclipse.hono.tracing.TracingHelper;
import org.eclipse.hono.util.CommandConstants;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.RegistrationAssertion;
import org.eclipse.hono.util.Strings;
import org.eclipse.hono.util.TenantObject;

import io.micrometer.core.instrument.Timer.Sample;
import io.opentracing.References;
import io.opentracing.Span;
import io.opentracing.noop.NoopSpan;
import io.opentracing.tag.Tags;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.HttpServerResponse;
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
    private static final String MATCH_ALL_ROUTE_NAME = "/*";

    private static final String KEY_MATCH_ALL_ROUTE_APPLIED = "matchAllRouteApplied";

    private HttpAdapterMetrics metrics = HttpAdapterMetrics.NOOP;
    private HttpServer server;
    private HttpServer insecureServer;

    /**
     * Sets the metrics for this service.
     *
     * @param metrics The metrics
     */
    public final void setMetrics(final HttpAdapterMetrics metrics) {
        Optional.ofNullable(metrics)
            .ifPresent(m -> log.info("reporting metrics using [{}]", metrics.getClass().getName()));
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

    private Map<String, String> getCustomTags() {
        final Map<String, String> customTags = new HashMap<>();
        customTags.put(Tags.COMPONENT.getKey(), getTypeName());
        addCustomTags(customTags);
        return customTags;
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
     * This method creates a router instance along with a route matching all requests. The handler set
     * on that route makes sure that
     * <ol>
     * <li>the tracing span created for the request by means of the Vert.x/Quarkus
     * instrumentation is available via {@link HttpServerSpanHelper#serverSpan(RoutingContext)},</li>
     * <li>a Micrometer {@code Timer.Sample} is added to the routing context,</li>
     * <li>there is log output when the connection is closed prematurely.</li>
     * </ol>
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
            // start the metrics timer
            ctx.put(KEY_MICROMETER_SAMPLE, getMetrics().startTimer());
            // log when the connection is closed prematurely
            if (!ctx.response().closed() && !ctx.response().ended()) {
                ctx.response().closeHandler(v -> logResponseGettingClosedPrematurely(ctx));
            }
            HttpUtils.nextRoute(ctx);
        });
        HttpUtils.addDefault404ErrorHandler(router);
        return router;
    }

    /**
     * Handles any operations that should be invoked as part of the authentication process after the credentials got
     * determined and before they get validated. Can be used to perform checks using the credentials and tenant
     * information before the potentially expensive credentials validation is done
     * <p>
     * The default implementation updates the trace sampling priority in the execution context tracing span.
     * It also verifies that the tenant provided via the credentials is enabled and that the adapter is enabled for
     * that tenant, failing the returned future if either is not the case.
     * <p>
     * Subclasses should override this method in order to perform additional operations after calling this super method.
     *
     * @param credentials The credentials.
     * @param executionContext The execution context, including the TenantObject.
     * @return A future indicating the outcome of the operation. A failed future will fail the authentication attempt.
     */
    protected Future<Void> handleBeforeCredentialsValidation(final DeviceCredentials credentials,
            final HttpContext executionContext) {

        final String tenantId = credentials.getTenantId();
        final String authId = credentials.getAuthId();
        final Span span = Optional.ofNullable(executionContext.getTracingSpan())
                .orElseGet(() -> {
                    log.warn("handleBeforeCredentialsValidation: no span context set in httpContext");
                    return NoopSpan.INSTANCE;
                });
        return getTenantConfiguration(tenantId, span.context())
                .recover(t -> Future.failedFuture(CredentialsApiAuthProvider.mapNotFoundToBadCredentialsException(t)))
                .map(tenantObject -> {
                    TracingHelper.setDeviceTags(span, tenantId, null, authId);
                    TenantTraceSamplingHelper.applyTraceSamplingPriority(tenantObject, authId, span);
                    return tenantObject;
                })
                .compose(this::isAdapterEnabled)
                .mapEmpty();
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
     * This method returns default options with the host and port being set to the corresponding values
     * from the <em>config</em> properties and using a maximum chunk size of 4096 bytes.
     *
     * @return The http server options.
     */
    protected HttpServerOptions getInsecureHttpServerOptions() {

        final HttpServerOptions options = new HttpServerOptions();
        options.setHost(getConfig().getInsecurePortBindAddress())
               .setPort(getConfig().getInsecurePort(getInsecurePortDefaultValue()))
               .setMaxChunkSize(4096)
               .setIdleTimeout(getConfig().getIdleTimeout());
        return options;
    }

    /**
     * Invoked before the message is sent to the downstream peer.
     * <p>
     * Subclasses may override this method in order to customize the
     * properties used for sending the message, e.g. adding custom properties.
     *
     * @param messageProperties The properties that are being added to the
     *                          downstream message.
     * @param ctx The routing context.
     */
    protected void customizeDownstreamMessageProperties(final Map<String, Object> messageProperties, final HttpContext ctx) {
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
    protected final void uploadTelemetryMessage(final HttpContext ctx, final String tenant, final String deviceId,
            final Buffer payload, final String contentType) {

        doUploadMessage(
                Objects.requireNonNull(ctx),
                Objects.requireNonNull(tenant),
                Objects.requireNonNull(deviceId),
                payload,
                contentType,
                MetricsTags.EndpointType.TELEMETRY);
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
    protected final void uploadEventMessage(final HttpContext ctx, final String tenant, final String deviceId,
            final Buffer payload, final String contentType) {

        doUploadMessage(
                Objects.requireNonNull(ctx),
                Objects.requireNonNull(tenant),
                Objects.requireNonNull(deviceId),
                payload,
                contentType,
                MetricsTags.EndpointType.EVENT);
    }

    /**
     * Uploads a telemetry/event message to Hono.
     * <p>
     * This method always sends a response to the device. The status code will be set
     * as specified in the
     * <a href="https://www.eclipse.org/hono/docs/user-guide/http-adapter/#publish-an-event-authenticated-device">
     * HTTP adapter User Guide</a>.
     *
     * @param ctx The context to retrieve cookies and the HTTP response from.
     * @param tenant The tenant of the device that has produced the data.
     * @param deviceId The id of the device that has produced the data.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    protected void doUploadMessage(
            final HttpContext ctx,
            final String tenant,
            final String deviceId) {
        doUploadMessage(
                ctx,
                tenant,
                deviceId,
                ctx.getRoutingContext().getBody(),
                ctx.getContentType(),
                MetricsTags.EndpointType.fromString(ctx.getRequestedResource().getEndpoint()));
    }

    // this method validate methods, check tenant and limits and delegates processing to the next do upload method
    // tracks the overall upload span
    private void doUploadMessage(
            final HttpContext ctx,
            final String tenant,
            final String deviceId,
            final Buffer payload,
            final String contentType,
            final MetricsTags.EndpointType endpoint) {

        // request validation
        if (!ctx.hasValidQoS()) {
            HttpUtils.badRequest(ctx.getRoutingContext(), "unsupported QoS-Level header value");
            return;
        }
        if (!isPayloadOfIndicatedType(payload, contentType)) {
            HttpUtils.badRequest(ctx.getRoutingContext(), String.format("content type [%s] does not match payload", contentType));
            return;
        }

        final MetricsTags.QoS qos = getQoSLevel(endpoint, ctx.getRequestedQos());
        final Device authenticatedDevice = ctx.getAuthenticatedDevice();
        final String gatewayId = authenticatedDevice != null && !deviceId.equals(authenticatedDevice.getDeviceId())
                ? authenticatedDevice.getDeviceId()
                : null;

        final Span currentSpan = TracingHelper
                .buildChildSpan(tracer, ctx.getTracingContext(), "upload " + endpoint.getCanonicalName(), getTypeName())
                .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_CLIENT)
                .withTag(TracingHelper.TAG_TENANT_ID, tenant)
                .withTag(TracingHelper.TAG_DEVICE_ID, deviceId)
                .withTag(TracingHelper.TAG_AUTHENTICATED.getKey(), authenticatedDevice != null)
                .withTag(TracingHelper.TAG_QOS, qos.name())
                .start();

        // tenant and limits validation
        final Future<RegistrationAssertion> tokenTracker = getRegistrationAssertion(
                tenant,
                deviceId,
                authenticatedDevice,
                currentSpan.context());
        final int payloadSize = Optional.ofNullable(payload)
            .map(ok -> payload.length())
            .orElse(0);
        final Future<TenantObject> tenantTracker = getTenantConfiguration(tenant, currentSpan.context());
        final Future<TenantObject> tenantValidationTracker = tenantTracker.compose(tenantObject -> CompositeFuture
                        .all(isAdapterEnabled(tenantObject),
                                checkMessageLimit(tenantObject, payloadSize, currentSpan.context()))
                        .map(success -> tenantObject));

        // we only need to consider TTD if the device and tenant are enabled and the adapter
        // is enabled for the tenant
        final Future<Integer> ttdTracker = CompositeFuture.all(tenantValidationTracker, tokenTracker)
                .compose(ok -> {
                    final Integer ttdParam = getTimeUntilDisconnectFromRequest(ctx);
                    return getTimeUntilDisconnect(tenantValidationTracker.result(), ttdParam)
                            .onSuccess(effectiveTtd -> Optional.ofNullable(effectiveTtd)
                                    .ifPresent(v -> TracingHelper.TAG_DEVICE_TTD.set(currentSpan, v)));
                });

        // do upload
        ttdTracker
            .compose(ttd -> // forwards the message and wait for command (if ttd > 0)
                doUploadMessage(
                    ctx, tenant, deviceId, gatewayId, payload, payloadSize, contentType, endpoint, qos,
                    tokenTracker.result(), tenantValidationTracker.result(), ttd, currentSpan))
            .compose(commandContext -> { // successful forward - send response
                metrics.reportTelemetry(
                    endpoint,
                    tenant,
                    tenantTracker.result(),
                    ProcessingOutcome.FORWARDED,
                    qos,
                    payloadSize,
                    ctx.getTtdStatus(),
                    getMicrometerSample(ctx.getRoutingContext()));
                return sendResponse(ctx, tenant, tenantTracker.result(), deviceId, endpoint, commandContext, currentSpan)
                    .recover(t -> Future.succeededFuture()); // prevent fall-though to recover
            })
            .recover(t -> { // failed forward - send error response
                log.debug("cannot process [{}] message from device [tenantId: {}, deviceId: {}]",
                    endpoint, tenant, deviceId, t);
                TracingHelper.logError(currentSpan, t);
                final ProcessingOutcome outcome;
                if (ClientErrorException.class.isInstance(t)) {
                    outcome = ProcessingOutcome.UNPROCESSABLE;
                    ctx.fail(t);
                } else {
                    outcome = ProcessingOutcome.UNDELIVERABLE;
                    final String errorMessage = t instanceof ServerErrorException ? ((ServerErrorException) t).getClientFacingMessage() : null;
                    HttpUtils.serviceUnavailable(ctx.getRoutingContext(), 2,
                        Strings.isNullOrEmpty(errorMessage) ? "temporarily unavailable" : errorMessage);
                }
                if (tenantTracker.succeeded()) {
                    metrics.reportTelemetry(
                        endpoint,
                        tenant,
                        tenantTracker.result(),
                        outcome,
                        qos,
                        payloadSize,
                        ctx.getTtdStatus(),
                        getMicrometerSample(ctx.getRoutingContext()));
                }
                return Future.failedFuture(t);
            })
            .onComplete(ar -> currentSpan.finish());
    }

    private Future<CommandContext> doUploadMessage(
        final HttpContext ctx,
        final String tenant,
        final String deviceId,
        final String gatewayId,
        final Buffer payload,
        final int payloadSize,
        final String contentType,
        final EndpointType endpoint,
        final MetricsTags.QoS qos,

        final RegistrationAssertion regAssertion,
        final TenantObject tenantObject,
        final Integer ttd,
        final Span currentSpan) {

        final Promise<Void> responseReady = Promise.promise();
        // register (if ttd >0 and command consume is successfully created) a command handler
        return createCommandHandler(
            ttd,
            tenant,
            tenantObject,
            deviceId,
            gatewayId,
            ctx.getRoutingContext(),
            currentSpan)
         .compose(commandHandler ->
                // forwards message
                sendMessageDownstream(
                    ctx, tenant, tenantObject, regAssertion, payload, payloadSize, contentType,
                    ttd == null || ttd <= 0 ? Optional.empty() : Optional.of(ttd),
                    endpoint,
                    qos,
                    currentSpan).compose(v ->
                        // wait for command
                        commandHandler.waitForCommand()
                        .recover(t -> Future.succeededFuture()))); // discard error - the response shall reflect message forwarding
    }

    // Sends message as event or telemetry downstream
    private Future<Void> sendMessageDownstream(
        final HttpContext ctx,
        final String tenant,
        final TenantObject tenantObject,
        final RegistrationAssertion regAssertion,
        final Buffer payload,
        final int payloadSize,
        final String contentType,
        final Optional<Integer> ttd,
        final EndpointType endpoint,
        final MetricsTags.QoS qos,
        final Span currentSpan) {

        final Map<String, Object> props = getDownstreamMessageProperties(ctx);
        ttd.ifPresent(ttdSec -> props.put(CommandConstants.MSG_PROPERTY_DEVICE_TTD, ttdSec));
        props.put(MessageHelper.APP_PROPERTY_QOS, ctx.getRequestedQos().ordinal());
        customizeDownstreamMessageProperties(props, ctx);

        final Future<Void> sendTracker;
        if (EndpointType.EVENT.equals(endpoint)) {
            ctx.getTimeToLive()
                .ifPresent(ttl -> props.put(MessageHelper.SYS_HEADER_PROPERTY_TTL, ttl.toSeconds()));
            sendTracker = getEventSender(tenantObject).sendEvent(
                tenantObject,
                regAssertion,
                contentType,
                payload,
                props,
                currentSpan.context());
        } else {
            // unsettled
            sendTracker = getTelemetrySender(tenantObject).sendTelemetry(
                tenantObject,
                regAssertion,
                ctx.getRequestedQos(),
                contentType,
                payload,
                props,
                currentSpan.context());
        }

        return sendTracker;
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
     * @param uploadMessageSpan The OpenTracing Span used for tracking the processing
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
    private Future<CommandHandler> createCommandHandler(
        final Integer ttdSecs,
        final String tenantId,
        final TenantObject tenantObject,
        final String deviceId,
        final String gatewayId,
        final RoutingContext ctx,
        final Span uploadMessageSpan) {

        if (ttdSecs == null || ttdSecs <= 0) {
            // returns NOP command handler - no command will be awaited for
            return CommandHandler.nop();
        }

        // try to return command handler that really waits for command
        TracingHelper.TAG_DEVICE_TTD.set(uploadMessageSpan, ttdSecs);

        final Span waitForCommandSpan = TracingHelper
            .buildChildSpan(tracer, uploadMessageSpan.context(),
                "create consumer and wait for command", getTypeName())
            .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_CLIENT)
            .start();
        TracingHelper.setDeviceTags(waitForCommandSpan, tenantObject.getTenantId(), deviceId);

        // this promise will always be completed. Completion occurs when:
        // 1. command is received and validated
        // 2. ttd/timeout has been expired
        // 3. response is closed or ended before setting up the close handler
        // 4. close handler receive event that connection is not alive anymore
        final Promise<CommandContext> commandTracker = Promise.promise();
        final Function<CommandContext, Future<Void>> commandHandler =
            commandHandler(ctx, tenantObject, deviceId, commandTracker, waitForCommandSpan);
        final Future<CommandConsumer> commandConsumerFuture;
        if (gatewayId != null) {
            // gateway scenario
            commandConsumerFuture = getCommandConsumerFactory().createCommandConsumer(
                tenantObject.getTenantId(),
                deviceId,
                gatewayId,
                commandHandler,
                Duration.ofSeconds(ttdSecs),
                waitForCommandSpan.context());
        } else {
            commandConsumerFuture = getCommandConsumerFactory().createCommandConsumer(
                tenantObject.getTenantId(),
                deviceId,
                commandHandler,
                Duration.ofSeconds(ttdSecs),
                waitForCommandSpan.context());
        }

        return commandConsumerFuture
            .<CommandHandler>map(consumer -> {
                if (ctx.response().closed() || ctx.response().ended()) {
                    waitForCommandSpan.log("device closed connection before creating command consumer, stop waiting for command");
                    setTtdStatus(ctx, TtdStatus.NONE);
                    logResponseGettingClosedPrematurely(ctx);

                    commandTracker.complete(); // response is not available anymore - can't send command anyway
                } else {
                    // if the request was not responded already, add a timer for triggering an empty response
                    addCommandReceptionTimerAndResponseCloseHandler(ctx, tenantId, deviceId, ttdSecs, commandTracker, waitForCommandSpan);
                }

                return new CommandHandler() {

                    private final Future<CommandContext> command = commandTracker.future().compose(this::close);

                    @Override
                    public Future<CommandContext> waitForCommand() {
                        return command;
                    }

                    private Future<CommandContext> close(final CommandContext commandContext) {
                        waitForCommandSpan.finish();

                        final Span closeConsumerSpan = TracingHelper
                            .buildChildSpan(tracer, uploadMessageSpan.context(), "close command consumer",
                                getTypeName())
                            .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_CLIENT)
                            .start();
                        TracingHelper.setDeviceTags(closeConsumerSpan, tenantObject.getTenantId(), deviceId);
                        return consumer.close(closeConsumerSpan.context())
                            .onFailure(thr -> TracingHelper.logError(closeConsumerSpan, thr))
                            .onComplete(ar -> closeConsumerSpan.finish()).map(commandContext);
                    }
                };
            }); // in case of failure to create command consume error will be returned to device and telemetry will be skipped (?)
    }

    private Function<CommandContext, Future<Void>> commandHandler(
        final RoutingContext ctx,
        final TenantObject tenantObject,
        final String deviceId,
        final Promise<CommandContext> commandTracker,
        final Span waitForCommandSpan) {

        final AtomicBoolean commandGot = new AtomicBoolean(false);
        return commandContext -> {

            final Span processCommandSpan = TracingHelper
                .buildFollowsFromSpan(tracer, waitForCommandSpan.context(), "process received command")
                .withTag(Tags.COMPONENT.getKey(), getTypeName())
                .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_CLIENT)
                // add reference to the trace started in the command router when the command was first received
                .addReference(References.FOLLOWS_FROM, commandContext.getTracingContext())
                .start();
            TracingHelper.setDeviceTags(processCommandSpan, tenantObject.getTenantId(), deviceId);

            Tags.COMPONENT.set(commandContext.getTracingSpan(), getTypeName());
            commandContext.logCommandToSpan(processCommandSpan);
            final Command command = commandContext.getCommand();
            final Sample commandSample = getMetrics().startTimer();
            if (isCommandValid(command, processCommandSpan)) {
                final Promise<Void> commandHandlerDonePromise = Promise.promise();
                // check if not already received and not set by timer
                if (commandGot.compareAndSet(false, true) && !commandTracker.future().isComplete()) {
                    waitForCommandSpan.finish();
                    checkMessageLimit(tenantObject, command.getPayloadSize(), processCommandSpan.context())
                        .onComplete(result -> {
                            if (result.succeeded() && commandTracker.tryComplete(commandContext)) {
                                addMicrometerSample(commandContext, commandSample);
                            } else {
                                final Throwable cause = result.failed() ? result.cause() : new TimeoutException("Expired!");
                                commandContext.reject(cause);
                                TracingHelper.logError(processCommandSpan, "rejected command for device", cause);
                                metrics.reportCommand(
                                    command.isOneWay() ? Direction.ONE_WAY : Direction.REQUEST,
                                    tenantObject.getTenantId(),
                                    tenantObject,
                                    ProcessingOutcome.from(cause),
                                    command.getPayloadSize(),
                                    commandSample);
                                commandTracker.tryFail(cause);
                            }
                            cancelCommandReceptionTimer(ctx);
                            setTtdStatus(ctx, TtdStatus.COMMAND);
                            processCommandSpan.finish();
                        });
                } else {
                    final String errorMsg = "waiting time for command has elapsed or another command has already been processed";
                    log.debug("{} [tenantId: {}, deviceId: {}]", errorMsg, tenantObject.getTenantId(), deviceId);
                    getMetrics().reportCommand(
                        command.isOneWay() ? Direction.ONE_WAY : Direction.REQUEST,
                        tenantObject.getTenantId(),
                        tenantObject,
                        ProcessingOutcome.UNDELIVERABLE,
                        command.getPayloadSize(),
                        commandSample);
                    final var exception = new ServerErrorException(HttpURLConnection.HTTP_UNAVAILABLE, errorMsg);
                    commandContext.release(exception);
                    TracingHelper.logError(processCommandSpan, errorMsg);
                    commandTracker.fail(exception);
                    processCommandSpan.finish();
                }

                return commandHandlerDonePromise.future();
            } else {
                log.debug("command message is invalid: {}", command);
                commandContext.reject("malformed command message");
                getMetrics().reportCommand(
                    command.isOneWay() ? Direction.ONE_WAY : Direction.REQUEST,
                    tenantObject.getTenantId(),
                    tenantObject,
                    ProcessingOutcome.UNPROCESSABLE,
                    command.getPayloadSize(),
                    commandSample);
                TracingHelper.logError(processCommandSpan, "malformed command message");
                processCommandSpan.finish();
                return Future.failedFuture("malformed command message");
            }
        };
    }

    private Future<Void> sendResponse(
        final HttpContext ctx,
        final String tenant, final TenantObject tenantObject,
        final String deviceId,
        final EndpointType endpoint,
        final CommandContext commandContext,
        final Span currentSpan) {

        if (ctx.response().closed()) {
            log.debug("failed to send http response for [{}] message from device [tenantId: {}, deviceId: {}]: response already closed",
                endpoint, tenant, deviceId);
            TracingHelper.logError(currentSpan, "failed to send HTTP response to device: response already closed");
            ctx.response().end(); // close the response here, ensuring that the TracingHandler bodyEndHandler gets called
            return Future.failedFuture("Response is already closed!");
        }

        setResponsePayload(ctx.response(), commandContext, currentSpan);
        final Promise<Void> sentTracker = Promise.promise();
        ctx.getRoutingContext().addBodyEndHandler(ok -> {
            log.trace("successfully processed [{}] message for device [tenantId: {}, deviceId: {}]",
                endpoint, tenant, deviceId);
            sentTracker.complete();
        });
        ctx.response().exceptionHandler(t -> {
            log.debug("failed to send http response for [{}] message from device [tenantId: {}, deviceId: {}]",
                endpoint, tenant, deviceId, t);
            sentTracker.fail(t);
        });
        ctx.response().end();
        return sentTracker.future().onComplete(ar -> {
            if (commandContext != null) {
                if (ar.succeeded()) {
                    commandContext.getTracingSpan().log("forwarded command to device in HTTP response body");
                    commandContext.accept();
                } else {
                    final Throwable t = ar.cause();
                    TracingHelper.logError(commandContext.getTracingSpan(),
                        "failed to forward command to device in HTTP response body", t);
                    currentSpan.log("failed to send HTTP response to device");
                    TracingHelper.logError(currentSpan, t);
                    commandContext.release(t);
                }
                metrics.reportCommand(
                    commandContext.getCommand().isOneWay() ? Direction.ONE_WAY : Direction.REQUEST,
                    tenant,
                    tenantObject,
                    ar.succeeded() ? ProcessingOutcome.FORWARDED : ProcessingOutcome.UNDELIVERABLE,
                    commandContext.getCommand().getPayloadSize(),
                    getMicrometerSample(commandContext));
            }
        });
    }

    private void logResponseGettingClosedPrematurely(final RoutingContext ctx) {
        log.trace("connection got closed before response could be sent");
        Optional.ofNullable(HttpServerSpanHelper.serverSpan(ctx)).ifPresent(span ->
            TracingHelper.logError(span, "connection got closed before response could be sent")
        );
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
     * {@inheritDoc}
     * <p>
     * This implementation limits the returned time period to at most 80 % of the configured idle timeout.
     *
     * @param tenant The tenant that the device belongs to.
     * @param deviceTtd The TTD value provided by the device in seconds or {@code null} if the device did not
     *                  provide a TTD value.
     * @return A succeeded future that contains {@code null} if device TTD is {@code null}, or otherwise the lesser of
     *         device TTD, the value returned by {@link TenantObject#getMaxTimeUntilDisconnect(String)} and 80% of the
     *         configured idle timeout.
     * @throws NullPointerException if tenant is {@code null}.
     */
    @Override
    public Future<Integer> getTimeUntilDisconnect(final TenantObject tenant, final Integer deviceTtd) {
        Objects.requireNonNull(tenant);

        if (deviceTtd == null) {
            return Future.succeededFuture();
        }
        final int ttdWithTenantMaxApplied = Math.min(tenant.getMaxTimeUntilDisconnect(getTypeName()), deviceTtd);
        // apply 80% of configured idle timeout as overall upper limit
        final int effectiveTtd = Math.min(getConfig().getIdleTimeout() * 80 / 100, ttdWithTenantMaxApplied);
        return Future.succeededFuture(effectiveTtd);
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
                command.getName(), command.getRequestId(), command.getTenant(), command.getGatewayOrDeviceId());

        if (!command.isOneWay()) {
            response.putHeader(Constants.HEADER_COMMAND_REQUEST_ID, command.getRequestId());
            currentSpan.setTag(Constants.HEADER_COMMAND_REQUEST_ID, command.getRequestId());
        }
        if (command.isTargetedAtGateway()) {
            response.putHeader(Constants.HEADER_COMMAND_TARGET_DEVICE, command.getDeviceId());
            currentSpan.setTag(Constants.HEADER_COMMAND_TARGET_DEVICE, command.getDeviceId());
        }

        response.setStatusCode(HttpURLConnection.HTTP_OK);
        HttpUtils.setResponseBody(response, command.getPayload(), command.getContentType());
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
     * @param ctx                The device's currently executing HTTP request.
     * @param tenantId           The tenant id
     * @param deviceId           The device id
     * @param delaySecs          The number of seconds to wait for a command.
     * @param waitForCommandSpan The span tracking the command reception.
     */
    private void addCommandReceptionTimerAndResponseCloseHandler(
        final RoutingContext ctx,
        final String tenantId,
        final String deviceId,
        final long delaySecs,
        final Promise<CommandContext> commandTracker,
        final Span waitForCommandSpan) {

        final Long timerId = ctx.vertx().setTimer(delaySecs * 1000L, id -> {

            log.trace("time to wait [{}s] for command expired [timer id: {}]", delaySecs, id);

            if (commandTracker.tryComplete()) {
                // no command to be sent,
                // send empty response
                setTtdStatus(ctx, TtdStatus.EXPIRED);
                waitForCommandSpan.log(String.format("time to wait for command expired (%ds)", delaySecs));
            } else {
                // a command has been sent to the device already
                log.trace("response already sent, nothing to do ...");
            }
        });
        log.trace("adding command reception timer [id: {}]", timerId);
        ctx.put(KEY_TIMER_ID, timerId);

        ctx.response().closeHandler(v -> {
            cancelCommandReceptionTimer(ctx);

            if (commandTracker.tryFail("Connection closed prematurely!")) {
                log.debug("device [tenant: {}, device-id: {}] closed connection before response could be sent",
                    tenantId, deviceId);
                waitForCommandSpan.log("device closed connection, stop waiting for command");
                setTtdStatus(ctx, TtdStatus.NONE);
                logResponseGettingClosedPrematurely(ctx);
            }
        });
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
                .buildChildSpan(tracer, ctx.getTracingContext(), "upload Command response", getTypeName())
                .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_CLIENT)
                .withTag(TracingHelper.TAG_TENANT_ID, tenant)
                .withTag(TracingHelper.TAG_DEVICE_ID, deviceId)
                .withTag(Constants.HEADER_COMMAND_RESPONSE_STATUS, responseStatus)
                .withTag(Constants.HEADER_COMMAND_REQUEST_ID, commandRequestId)
                .withTag(TracingHelper.TAG_AUTHENTICATED.getKey(), authenticatedDevice != null)
                .start();

        final CommandResponse cmdResponseOrNull = CommandResponse.fromRequestId(commandRequestId, tenant, deviceId, payload,
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
                    final Future<RegistrationAssertion> deviceRegistrationTracker = getRegistrationAssertion(
                            tenant,
                            deviceId,
                            authenticatedDevice,
                            currentSpan.context());
                    final Future<Void> tenantValidationTracker = CompositeFuture
                            .all(isAdapterEnabled(tenantTracker.result()),
                                    checkMessageLimit(tenantTracker.result(), payloadSize, currentSpan.context()))
                            .map(ok -> null);

                    return CompositeFuture.all(tenantValidationTracker, deviceRegistrationTracker)
                            .compose(ok -> sendCommandResponse(
                                    tenantTracker.result(),
                                    deviceRegistrationTracker.result(),
                                    commandResponseTracker.result(),
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

    private static MetricsTags.QoS getQoSLevel(
            final EndpointType endpoint,
            final org.eclipse.hono.util.QoS requestedQos) {

        if (endpoint == EndpointType.EVENT) {
            return MetricsTags.QoS.AT_LEAST_ONCE;
        } else if (requestedQos == null) {
            return MetricsTags.QoS.UNKNOWN;
        } else if (requestedQos == org.eclipse.hono.util.QoS.AT_MOST_ONCE) {
            return MetricsTags.QoS.AT_MOST_ONCE;
        } else {
            return MetricsTags.QoS.AT_LEAST_ONCE;
        }
    }

    /**
     * Gets body handler and sets the maximum body size.
     * <p>This method sets body limit size from the configuration.
     *
     * @return The body handler.
     */
    protected BodyHandler getBodyHandler() {
        final BodyHandler bodyHandler = BodyHandler.create(DEFAULT_UPLOADS_DIRECTORY);
        bodyHandler.setBodyLimit(getConfig().getMaxPayloadSize());
        return bodyHandler;
    }

    // handles responding to HTTP request - command or empty message
    private interface CommandHandler {

        Future<CommandContext> waitForCommand();

        static Future<CommandHandler> nop() {
            return Future.succeededFuture(
                new CommandHandler() {

                    @Override
                    public Future<CommandContext> waitForCommand() {
                        return Future.succeededFuture();
                    }
                });
        }
    }
}
