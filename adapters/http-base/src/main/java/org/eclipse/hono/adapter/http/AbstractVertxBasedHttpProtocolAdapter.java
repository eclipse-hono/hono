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

package org.eclipse.hono.adapter.http;

import java.net.HttpURLConnection;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

import org.eclipse.hono.adapter.AbstractProtocolAdapterBase;
import org.eclipse.hono.adapter.HttpContext;
import org.eclipse.hono.adapter.auth.device.CredentialsApiAuthProvider;
import org.eclipse.hono.adapter.auth.device.DeviceCredentials;
import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.ServerErrorException;
import org.eclipse.hono.client.command.Command;
import org.eclipse.hono.client.command.CommandContext;
import org.eclipse.hono.client.command.CommandResponse;
import org.eclipse.hono.client.command.ProtocolAdapterCommandConsumer;
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
import io.vertx.core.Future;
import io.vertx.core.Handler;
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
                    // ensure route order check is disabled, allowing routes with auth handler added before body handler
                    System.setProperty(HttpUtils.SYSTEM_PROPERTY_ROUTER_SETUP_LENIENT, "true");

                    addRoutes(router);
                    return Future.all(bindSecureHttpServer(router), bindInsecureHttpServer(router));
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
                .compose(tenantObject -> isAdapterEnabled(tenantObject))
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

        Future.all(serverStopTracker.future(), insecureServerStopTracker.future())
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
                ctx.getRoutingContext().body().buffer(),
                ctx.getContentType(),
                MetricsTags.EndpointType.fromString(ctx.getRequestedResource().getEndpoint()));
    }

    private void doUploadMessage(
            final HttpContext ctx,
            final String tenant,
            final String deviceId,
            final Buffer payload,
            final String contentType,
            final MetricsTags.EndpointType endpoint) {

        if (!ctx.hasValidQoS()) {
            HttpUtils.badRequest(ctx.getRoutingContext(), "unsupported QoS-Level header value");
            return;
        }
        if (!isPayloadOfIndicatedType(payload, contentType)) {
            HttpUtils.badRequest(ctx.getRoutingContext(), String.format("content type [%s] does not match payload", contentType));
            return;
        }

        final MetricsTags.QoS qos = getQoSLevel(endpoint, ctx.getRequestedQos());
        final var authenticatedDevice = ctx.getAuthenticatedDevice();
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

        final Future<RegistrationAssertion> tokenTracker = getRegistrationAssertion(
                tenant,
                deviceId,
                authenticatedDevice,
                currentSpan.context());
        final int payloadSize = Optional.ofNullable(payload)
                .map(ok -> payload.length())
                .orElse(0);
        final Future<TenantObject> tenantTracker = getTenantConfiguration(tenant, currentSpan.context());
        final Future<TenantObject> tenantValidationTracker = tenantTracker
                .compose(tenantObject -> Future
                        .all(isAdapterEnabled(tenantObject),
                                checkMessageLimit(tenantObject, payloadSize, currentSpan.context()))
                        .map(success -> tenantObject));

        // we only need to consider TTD if the device and tenant are enabled and the adapter
        // is enabled for the tenant
        final Future<Integer> ttdTracker = Future.all(tenantValidationTracker, tokenTracker)
                .compose(ok -> {
                    final Integer ttdParam = getTimeUntilDisconnectFromRequest(ctx);
                    return getTimeUntilDisconnect(tenantTracker.result(), ttdParam)
                            .onSuccess(effectiveTtd -> Optional.ofNullable(effectiveTtd)
                                    .ifPresent(v -> TracingHelper.TAG_DEVICE_TTD.set(currentSpan, v)));
                })
                .map(ttd -> ttd == null || ttd <= 0 ? null : ttd); // non positive ttd are made null - no ttd

        ttdTracker
        .compose(ttd ->
            ttd == null ?
              ResponseReadyTracker.nop() :
              createCommandConsumer(
                ttd,
                tenantTracker.result(),
                deviceId,
                gatewayId,
                ctx.getRoutingContext(),
                currentSpan))
        .compose(responseReadyTracker -> {

            final Map<String, Object> props = getDownstreamMessageProperties(ctx);
            Optional.ofNullable(ttdTracker.result())
                    .ifPresent(ttd -> props.put(CommandConstants.MSG_PROPERTY_DEVICE_TTD, ttd));
            props.put(MessageHelper.APP_PROPERTY_QOS, ctx.getRequestedQos().ordinal());

            customizeDownstreamMessageProperties(props, ctx);

            if (EndpointType.EVENT.equals(endpoint)) {
                ctx.getTimeToLive()
                    .ifPresent(ttl -> props.put(MessageHelper.SYS_HEADER_PROPERTY_TTL, ttl.toSeconds()));
                return Future.all(
                        getEventSender(tenantValidationTracker.result()).sendEvent(
                                tenantTracker.result(),
                                tokenTracker.result(),
                                contentType,
                                payload,
                                props,
                                currentSpan.context()).onFailure(thr -> responseReadyTracker.cancel("send event failed", null)),
                        responseReadyTracker.future())
                        .map(s -> (Void) null);
            } else {
                // unsettled
                return Future.all(
                        getTelemetrySender(tenantValidationTracker.result()).sendTelemetry(
                                tenantTracker.result(),
                                tokenTracker.result(),
                                ctx.getRequestedQos(),
                                contentType,
                                payload,
                                props,
                                currentSpan.context()).onFailure(thr -> responseReadyTracker.cancel("send telemetry failed", null)),
                        responseReadyTracker.future())
                        .map(s -> (Void) null);
            }
        })
        .map(proceed -> {

            metrics.reportTelemetry(
                endpoint,
                tenant,
                tenantTracker.result(),
                ProcessingOutcome.FORWARDED,
                qos,
                payloadSize,
                ctx.getTtdStatus(),
                getMicrometerSample(ctx.getRoutingContext()));

            if (ctx.response().closed()) {
                log.debug("failed to send http response for [{}] message from device [tenantId: {}, deviceId: {}]: response already closed",
                        endpoint, tenant, deviceId);
                TracingHelper.logError(currentSpan, "failed to send HTTP response to device: response already closed");
                currentSpan.finish();
                ctx.response().end(); // close the response here, ensuring that bodyEndHandlers get called
            } else {
                final CommandContext commandContext = ctx.get(CommandContext.KEY_COMMAND_CONTEXT);
                setResponsePayload(ctx.response(), commandContext, currentSpan);
                ctx.getRoutingContext().addBodyEndHandler(ok -> {
                    log.trace("successfully processed [{}] message for device [tenantId: {}, deviceId: {}]",
                            endpoint, tenant, deviceId);
                    if (commandContext != null) {
                        commandContext.getTracingSpan().log("forwarded command to device in HTTP response body");
                        commandContext.accept();
                        metrics.reportCommand(
                                commandContext.getCommand().isOneWay() ? Direction.ONE_WAY : Direction.REQUEST,
                                tenant,
                                tenantTracker.result(),
                                ProcessingOutcome.FORWARDED,
                                commandContext.getCommand().getPayloadSize(),
                                getMicrometerSample(commandContext));
                    }
                    currentSpan.finish();
                });
                ctx.response().exceptionHandler(t -> {
                    log.debug("failed to send http response for [{}] message from device [tenantId: {}, deviceId: {}]",
                            endpoint, tenant, deviceId, t);
                    if (commandContext != null) {
                        TracingHelper.logError(commandContext.getTracingSpan(),
                                "failed to forward command to device in HTTP response body", t);
                        commandContext.release(t);
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
                    currentSpan.finish();
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
                TracingHelper.logError(commandContext.getTracingSpan(),
                        "command won't be forwarded to device in HTTP response body, HTTP request handling failed", t);
                commandContext.release(t);
                currentSpan.log("released command for device");
            }
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
            currentSpan.finish();
            return Future.failedFuture(t);
        });
    }

    private void logResponseGettingClosedPrematurely(final RoutingContext ctx) {
        log.trace("connection got closed before response could be sent");
        Optional.ofNullable(HttpServerSpanHelper.serverSpan(ctx)).ifPresent(span -> {
            TracingHelper.logError(span, "connection got closed before response could be sent");
        });
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

    /**
     * Sets a handler for cleaning up when a device closes the HTTP connection of a TTD request before
     * a response could be sent.
     * <p>
     * The handler will cancel the {@link ResponseReadyTracker}.
     *
     * @param ctx The context to retrieve cookies and the HTTP response from.
     * @param responseReadyTracker The response ready tracker.
     * @param tenantId The tenant that the device belongs to.
     * @param deviceId The identifier of the device.
     */
    private void setTtdRequestConnectionCloseHandler(
            final RoutingContext ctx,
            final ResponseReadyTracker responseReadyTracker,
            final String tenantId,
            final String deviceId) {

        if (!ctx.response().closed() && !ctx.response().ended()) {
            ctx.response().closeHandler(v ->
                responseReadyTracker.cancel("device closed connection, stop waiting for command", canceled -> {
                    if (canceled) {
                        log.debug("device [tenant: {}, device-id: {}] closed connection before response could be sent",
                            tenantId, deviceId);
                        cancelCommandReceptionTimer(ctx);
                    }
                })
            );
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
     *         In case of success the future will be completed with a {@link ResponseReadyTracker} whose
     *         future will be completed when a command is received, the ttd period has elapsed or the
     *         client connection was closed.
     *         <p>
     *         The allocated resources shall be released by the {@link ResponseReadyTracker} itself
     *         without any caller interactions.
     *         <p>
     *         The future will be failed with a {@code ServiceInvocationException} if the
     *         message consumer could not be created.
     * @throws NullPointerException if any of the parameters other than gatewayId is {@code null}.
     */
    protected final Future<ResponseReadyTracker> createCommandConsumer(
            final Integer ttdSecs,
            final TenantObject tenantObject,
            final String deviceId,
            final String gatewayId,
            final RoutingContext ctx,
            final Span uploadMessageSpan) {

        Objects.requireNonNull(ttdSecs);
        Objects.requireNonNull(tenantObject);
        Objects.requireNonNull(deviceId);
        Objects.requireNonNull(ctx);
        Objects.requireNonNull(uploadMessageSpan);

        final AtomicBoolean requestProcessed = new AtomicBoolean(false);

        // A promise used when command consumer is created successfully
        // In that case it would always be completed on one (first) of the following conditions
        // - a command has been received
        // - the ttd has expired
        // - device has closed connection while waiting for command
        final Promise<Void> responseReady = Promise.promise();
        TracingHelper.TAG_DEVICE_TTD.set(uploadMessageSpan, ttdSecs);

        final Span waitForCommandSpan = TracingHelper
                .buildChildSpan(tracer, uploadMessageSpan.context(),
                        "create consumer and wait for command", getTypeName())
                .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_CLIENT)
                .start();
        TracingHelper.setDeviceTags(waitForCommandSpan, tenantObject.getTenantId(), deviceId);

        final Function<CommandContext, Future<Void>> commandHandler = commandContext -> {

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
                if (requestProcessed.compareAndSet(false, true)) {
                    waitForCommandSpan.finish();
                    commandHandlerDonePromise.future().onComplete(responseReady);
                    checkMessageLimit(tenantObject, command.getPayloadSize(), processCommandSpan.context())
                    .onComplete(result -> {
                        if (result.succeeded()) {
                            addMicrometerSample(commandContext, commandSample);
                            // put command context to routing context and notify
                            ctx.put(CommandContext.KEY_COMMAND_CONTEXT, commandContext);
                            commandHandlerDonePromise.complete();
                        } else {
                            commandContext.reject(result.cause());
                            TracingHelper.logError(processCommandSpan, "rejected command for device", result.cause());
                            metrics.reportCommand(
                                    command.isOneWay() ? Direction.ONE_WAY : Direction.REQUEST,
                                    tenantObject.getTenantId(),
                                    tenantObject,
                                    ProcessingOutcome.from(result.cause()),
                                    command.getPayloadSize(),
                                    commandSample);
                            commandHandlerDonePromise.fail(result.cause());
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
                    processCommandSpan.finish();
                    commandHandlerDonePromise.fail(exception);
                }

                return commandHandlerDonePromise.future();
            } else {
                getMetrics().reportCommand(
                        command.isOneWay() ? Direction.ONE_WAY : Direction.REQUEST,
                        tenantObject.getTenantId(),
                        tenantObject,
                        ProcessingOutcome.UNPROCESSABLE,
                        command.getPayloadSize(),
                        commandSample);
                log.debug("command message is invalid: {}", command);
                commandContext.reject("malformed command message");
                TracingHelper.logError(processCommandSpan, "malformed command message");
                processCommandSpan.finish();
                return Future.failedFuture("malformed command message");
            }
        };

        final Future<ProtocolAdapterCommandConsumer> commandConsumerFuture;
        if (gatewayId != null) {
            // gateway scenario
            commandConsumerFuture = getCommandConsumerFactory().createCommandConsumer(
                    tenantObject.getTenantId(),
                    deviceId,
                    gatewayId,
                    false,
                    commandHandler,
                    Duration.ofSeconds(ttdSecs),
                    waitForCommandSpan.context());
        } else {
            commandConsumerFuture = getCommandConsumerFactory().createCommandConsumer(
                    tenantObject.getTenantId(),
                    deviceId,
                    false,
                    commandHandler,
                    Duration.ofSeconds(ttdSecs),
                    waitForCommandSpan.context());
        }
        return commandConsumerFuture
                .onFailure(thr -> {
                    TracingHelper.logError(waitForCommandSpan, thr);
                    waitForCommandSpan.finish();
                })
                .map(consumer -> {
                    final ResponseReadyTracker responseReadyTracker = new ResponseReadyTracker() {

                        // responseReady is completed when command was received, ttd has timed out or connection was closed
                        // we wait for the CommandConsumer having been closed before delivering the response to the
                        // device in order to prevent a race condition when the device immediately sends a new
                        // request and the CommandConsumer from the current request has not been closed yet
                        private final Future<Void> waitForCommandFuture = responseReady.future()
                                // always invoke closeCommandConsumer but ignore a failure there,
                                // completing waitForCommandFuture with the responseReady.future() result/failure
                                .eventually(v -> closeCommandConsumer());

                        @Override
                        public Future<Void> future() {
                            return waitForCommandFuture;
                        }

                        @Override
                        public void cancel(final String reason, final Handler<Boolean> handler) {
                            if (requestProcessed.compareAndSet(false, true)) {
                                if (reason != null) {
                                    waitForCommandSpan.log(String.format("canceled: %s", reason));
                                }

                                if (handler != null) {
                                    handler.handle(true);
                                }

                                waitForCommandSpan.finish();
                                responseReady.complete();
                            } else {
                                if (handler != null) {
                                    handler.handle(false);
                                }
                            }
                        }

                        private Future<Void> closeCommandConsumer() {
                            final Span closeConsumerSpan = TracingHelper
                                    .buildChildSpan(tracer, uploadMessageSpan.context(), "close command consumer",
                                            getTypeName())
                                    .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_CLIENT)
                                    .start();
                            TracingHelper.setDeviceTags(closeConsumerSpan, tenantObject.getTenantId(), deviceId);
                            return consumer.close(false, closeConsumerSpan.context())
                                    .onComplete(ar -> {
                                        if (ar.failed()) {
                                            TracingHelper.logError(closeConsumerSpan, ar.cause());
                                        }
                                        closeConsumerSpan.finish();
                                    });
                        }
                    };

                    if (!requestProcessed.get()) {
                        // if the request was not responded already, add a timer for triggering an empty response
                        addCommandReceptionTimer(ctx, responseReadyTracker, ttdSecs);
                        setTtdRequestConnectionCloseHandler(ctx, responseReadyTracker, tenantObject.getTenantId(), deviceId);
                    } // otherwise the responseReady has already been completed

                    return responseReadyTracker;
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
     * @param responseReadyTracker The response ready tracker.
     * @param delaySecs The number of seconds to wait for a command.
     */
    private void addCommandReceptionTimer(
            final RoutingContext ctx,
            final ResponseReadyTracker responseReadyTracker,
            final long delaySecs) {

        final Long timerId = ctx.vertx().setTimer(delaySecs * 1000L, id -> {

            log.trace("time to wait [{}s] for command expired [timer id: {}]", delaySecs, id);

            responseReadyTracker.cancel(String.format("time to wait for command expired (%ds)", delaySecs), canceled -> {
                if (canceled) {
                    // no command to be sent, send empty response
                    setTtdStatus(ctx, TtdStatus.EXPIRED);
                } else {
                    // a command has been sent to the device already
                    log.trace("response already sent, nothing to do ...");
                }
            });
        });

        log.trace("adding command reception timer [id: {}]", timerId);

        ctx.put(KEY_TIMER_ID, timerId);
    }

    private void cancelCommandReceptionTimer(final RoutingContext ctx) {

        final Long timerId = ctx.get(KEY_TIMER_ID);
        if (timerId != null) {
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

        final Buffer payload = ctx.getRoutingContext().body().buffer();
        final String contentType = ctx.getContentType();

        log.debug("processing response to command [tenantId: {}, deviceId: {}, cmd-req-id: {}, status code: {}]",
                tenant, deviceId, commandRequestId, responseStatus);

        final var authenticatedDevice = ctx.getAuthenticatedDevice();
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
        Future.all(tenantTracker, commandResponseTracker)
                .compose(commandResponse -> {
                    final Future<RegistrationAssertion> deviceRegistrationTracker = getRegistrationAssertion(
                            tenant,
                            deviceId,
                            authenticatedDevice,
                            currentSpan.context());
                    final Future<Void> tenantValidationTracker = Future
                            .all(isAdapterEnabled(tenantTracker.result()),
                                    checkMessageLimit(tenantTracker.result(), payloadSize, currentSpan.context()))
                            .map(ok -> null);

                    return Future.all(tenantValidationTracker, deviceRegistrationTracker)
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

    /**
     * Tracks readiness to send back a response to the device,
     * waiting for a command to include in the response if needed.
     */
    protected interface ResponseReadyTracker {

        /**
         * This method returns a {@link Future} that tracks readiness to send back a response to the device.
         * If there is a TTD set, it will wait for a command otherwise the future will be completed directly.
         *
         * @return The response readiness future.
         */
        Future<Void> future();

        /**
         * Abort/Cancel waiting for command because of certain condition (e.g. TTD expired
         * or device has closed connection).
         * <p>
         * This will complete the future returned by {@link #future()}.
         *
         * @param reason The cancellation reason.
         * @param handler The handler to receive notification if the cancel operation succeeded or {@link ResponseReadyTracker}
         *                has already been completed. This handler will be invoked before the future returned by
         *                {@link #future()} is completed.
         */
        void cancel(String reason, Handler<Boolean> handler);

        /**
         * Return a {@link ResponseReadyTracker} that doesn't wait for any command. It is used
         * in case no TTD was set or TTD is less or equal 0.
         *
         * @return No operation {@link ResponseReadyTracker}.
         */
        static Future<ResponseReadyTracker> nop() {
            return Future.succeededFuture(
                new ResponseReadyTracker() {

                    @Override
                    public Future<Void> future() {
                        return Future.succeededFuture();
                    }

                    @Override
                    public void cancel(final String reason, final Handler<Boolean> handler) {
                        if (handler != null) {
                            handler.handle(false);
                        }
                    }
                });
        }
    }
}
