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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;

import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.MessageConsumer;
import org.eclipse.hono.client.MessageSender;
import org.eclipse.hono.service.AbstractProtocolAdapterBase;
import org.eclipse.hono.service.auth.device.Device;
import org.eclipse.hono.service.command.CommandResponseSender;
import org.eclipse.hono.service.http.HttpUtils;
import org.eclipse.hono.tracing.TracingHelper;
import org.eclipse.hono.util.CommandConstants;
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
import io.opentracing.SpanContext;
import io.opentracing.contrib.vertx.ext.web.TracingHandler;
import io.opentracing.contrib.vertx.ext.web.WebSpanDecorator;
import io.opentracing.tag.Tags;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.proton.ProtonDelivery;

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
                    addTracingHandler(router);
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
     * @param router The router.
     */
    private void addTracingHandler(final Router router) {
        final Map<String, String> customTags = new HashMap<>();
        customTags.put(Tags.COMPONENT.getKey(), getTypeName());
        addCustomTags(customTags);
        final List<WebSpanDecorator> decorators = new ArrayList<>();
        decorators.add(new ComponentMetaDataDecorator(customTags));
        addCustomSpanDecorators(decorators);
        final TracingHandler tracingHandler = new TracingHandler(tracer, decorators);
        router.route().order(-1).handler(tracingHandler).failureHandler(tracingHandler);
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

        if (!isPayloadOfIndicatedType(payload, contentType)) {
            HttpUtils.badRequest(ctx, String.format("Content-Type %s does not match with the payload", contentType));
        } else {
            final Integer qosHeader = getQoSLevel(ctx.request().getHeader(Constants.HEADER_QOS_LEVEL));
            if (contentType == null) {
                HttpUtils.badRequest(ctx, String.format("%s header is missing", HttpHeaders.CONTENT_TYPE));
            } else if (qosHeader != null && qosHeader == HEADER_QOS_INVALID) {
                HttpUtils.badRequest(ctx, "Bad QoS Header Value");
            } else {

                final Device authenticatedDevice = getAuthenticatedDevice(ctx);
                final SpanContext currentSpan = Optional.ofNullable((Span) ctx.get(TracingHandler.CURRENT_SPAN)).map(span -> {
                    span.setOperationName("upload " + endpointName);
                    TracingHelper.TAG_TLS.set(span, ctx.request().isSSL());
                    TracingHelper.TAG_AUTHENTICATED.set(span, authenticatedDevice != null);
                    return span.context();
                }).orElse(null);

                final Future<JsonObject> tokenTracker = getRegistrationAssertion(tenant, deviceId, authenticatedDevice);
                final Future<TenantObject> tenantConfigTracker = getTenantConfiguration(tenant);

                // AtomicBoolean to control if the downstream message was sent successfully
                final AtomicBoolean downstreamMessageSent = new AtomicBoolean(false);
                // AtomicReference to a Handler to be called to close an open command receiver link.
                final AtomicReference<Handler<Void>> closeLinkAndTimerHandlerRef = new AtomicReference<>();

                // Handler to be called with a received command. If the timer expired, null is provided as command.
                final Handler<Message> commandReceivedHandler = commandMessage -> {
                    // reset the closeHandler reference, since it is not valid anymore at this time.
                    closeLinkAndTimerHandlerRef.set(null);
                    if (downstreamMessageSent.get()) {
                        // finish the request, since the response is now complete (command was added)
                        if (!ctx.response().closed()) {
                            ctx.response().end();
                        }
                    }
                };

                CompositeFuture.all(tokenTracker, tenantConfigTracker, senderTracker).compose(ok -> {

                    if (tenantConfigTracker.result().isAdapterEnabled(getTypeName())) {
                        final MessageSender sender = senderTracker.result();
                        final Message downstreamMessage = newMessage(
                                ResourceIdentifier.from(endpointName, tenant, deviceId),
                                sender.isRegistrationAssertionRequired(),
                                ctx.request().uri(),
                                contentType,
                                payload,
                                tokenTracker.result(),
                                HttpUtils.getTimeTilDisconnect(ctx));
                        customizeDownstreamMessage(downstreamMessage, ctx);

                        // first open the command receiver link (if needed)
                        return openCommandReceiverLink(ctx, tenant, deviceId, commandReceivedHandler).compose(closeLinkAndTimerHandler -> {
                            closeLinkAndTimerHandlerRef.set(closeLinkAndTimerHandler);

                            if (qosHeader == null) {
                                return sender.send(downstreamMessage, currentSpan);
                            } else {
                                return sender.sendAndWaitForOutcome(downstreamMessage, currentSpan);
                            }
                        });
                    } else {
                        // this adapter is not enabled for the tenant
                        return Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_FORBIDDEN,
                                "adapter is not enabled for tenant"));
                    }
                }).compose(delivery -> {
                    LOG.trace("successfully processed message for device [tenantId: {}, deviceId: {}, endpoint: {}]",
                            tenant, deviceId, endpointName);
                    metrics.incrementProcessedHttpMessages(endpointName, tenant);
                    ctx.response().setStatusCode(HttpURLConnection.HTTP_ACCEPTED);
                    downstreamMessageSent.set(true);

                    // if no command timer was created, the request now can be responded
                    if (closeLinkAndTimerHandlerRef.get() == null) {
                        ctx.response().end();
                    }

                    return Future.succeededFuture();

                }).recover(t -> {

                    LOG.debug("cannot process message for device [tenantId: {}, deviceId: {}, endpoint: {}]",
                            tenant, deviceId, endpointName, t);

                    cancelResponseTimer(closeLinkAndTimerHandlerRef);

                    if (ClientErrorException.class.isInstance(t)) {
                        final ClientErrorException e = (ClientErrorException) t;
                        ctx.fail(e.getErrorCode());
                    } else {
                        metrics.incrementUndeliverableHttpMessages(endpointName, tenant);
                        HttpUtils.serviceUnavailable(ctx, 2);
                    }
                    return Future.failedFuture(t);
                });
            }
        }
    }

    private void cancelResponseTimer(final AtomicReference<Handler<Void>> cancelTimerHandlerRef) {
        Optional.ofNullable(cancelTimerHandlerRef.get()).map(cancelTimerHandler -> {
            cancelTimerHandler.handle(null);
            return null;
        });
    }

    /**
     * Create a consumer for a command message that can be used by the command and control consumer.
     *
     * @param ctx The routing context of the HTTP request.
     * @param tenant The tenant of the device for that a command may be received.
     * @param deviceId The id of the device for that a command may be received.
     * @param commandReceivedHandler A handler that is invoked after a command message was received.
     * @return The BiConsumer to pass to the command and control consumer.
     */
    private BiConsumer<ProtonDelivery, Message> createCommandMessageConsumer(final RoutingContext ctx,
            final String tenant, final String deviceId, final Handler<Message> commandReceivedHandler) {
        return (delivery, commandMessage) -> {

            final Optional<String> commandSubject = Optional.ofNullable(commandMessage.getProperties().getSubject());
            final Optional<String> commandRequestId = validateAndGenerateCommandRequestId(tenant, deviceId,
                    commandMessage);

            if (commandSubject.isPresent() && commandRequestId.isPresent()) {

                ctx.response().putHeader(Constants.HEADER_COMMAND, commandSubject.get());
                ctx.response().putHeader(Constants.HEADER_COMMAND_REQUEST_ID, commandRequestId.get());

                HttpUtils.setResponseBody(ctx.response(), MessageHelper.getPayload(commandMessage));

                commandReceivedHandler.handle(commandMessage);

            } else {
                LOG.info("Received command with invalid reply-to endpoint for device [tenantId: {}, deviceId: {}] - ignoring.",
                        tenant, deviceId);
            }
        };

    }

    /**
     * Opens a command receiver link for a device by creating a command and control consumer.
     *
     * @param ctx The routing context of the HTTP request.
     * @param tenant The tenant of the device for that a command may be received.
     * @param deviceId The id of the device for that a command may be received.
     * @param commandReceivedHandler Handler to be called after a command was received or the timer expired.
     *                               The link was closed at this time already.
     *                               If the timer expired, the passed command message is null, otherwise the received command message is passed.
     *
     * @return Optional An optional handler that cancels a timer that might have been started to close the receiver link again.
     */
    private Future<Handler<Void>> openCommandReceiverLink(final RoutingContext ctx, final String tenant,
            final String deviceId,  final Handler<Message> commandReceivedHandler) {

        final Future<Handler<Void>> resultWithLinkCloseHandler = Future.future();

        final AtomicReference<MessageConsumer> messageConsumerRef = new AtomicReference<>(null);

        final Integer timeToDelayResponse = Optional.ofNullable(HttpUtils.getTimeTilDisconnect(ctx)).orElse(-1);

        if (timeToDelayResponse > 0) {
            // create a handler for being invoked if a command was received
            final Handler<Message> commandHandler = commandMessage -> {
                // if a link close handler was set, invoke it
                Optional.ofNullable(resultWithLinkCloseHandler.result()).map(linkCloseHandler -> {
                    linkCloseHandler.handle(null);
                    return null;
                });

                // if desired, now invoke the passed commandReceivedHandler and pass the message
                Optional.ofNullable(commandReceivedHandler).map(h -> {
                    h.handle(commandMessage);
                    return null;
                });

                final Optional<String> replyIdOpt = getReplyToIdFromCommand(tenant, deviceId, commandMessage);
                if (!replyIdOpt.isPresent()) {
                    // from Java 9 on: switch to opt.ifPresentOrElse
                    LOG.debug("Received command without valid replyId for device [tenantId: {}, deviceId: {}] - no reply will be sent to the application",
                            tenant, deviceId);
                }
            };

            // create the commandMessageConsumer that handles an incoming command message
            final BiConsumer<ProtonDelivery, Message> commandMessageConsumer = createCommandMessageConsumer(ctx, tenant,
                    deviceId, commandHandler);

            createCommandConsumer(tenant, deviceId, commandMessageConsumer, v ->
                    onCloseCommandConsumer(tenant, deviceId, commandMessageConsumer)
            ).map(messageConsumer -> {
                        // remember message consumer for later usage
                        messageConsumerRef.set(messageConsumer);
                        // let only one command reach the adapter (may change in the future)
                        messageConsumer.flow(1);

                        // create a timer that is invoked if no command was received until timeToDelayResponse is expired
                        final long timerId = getVertx().setTimer(timeToDelayResponse * 1000L,
                                delay -> {
                                    getCommandConnection().closeCommandConsumer(tenant, deviceId);
                                    // command finished, invoke handler
                                    Optional.ofNullable(commandReceivedHandler).map(h -> {
                                        h.handle(null);
                                        return null;
                                    });
                                });
                        // define the cancel code as closure
                        resultWithLinkCloseHandler.complete(v -> {
                            getVertx().cancelTimer(timerId);
                            getCommandConnection().closeCommandConsumer(tenant, deviceId);
                        });

                        return messageConsumer;
                    }).recover(t -> {
                        getCommandConnection().closeCommandConsumer(tenant, deviceId);
                        resultWithLinkCloseHandler.fail(t);
                        return Future.failedFuture(t);
                    });

        } else {
            resultWithLinkCloseHandler.complete();
        }

        return resultWithLinkCloseHandler;
    }

    /**
     * Uploads a command response message to the Hono server.
     *
     * @param ctx The routing context of the HTTP request.
     * @param tenant The tenant of the device from that a command response was received.
     * @param deviceId The id of the device from that a command response was received.
     * @param commandRequestId The id of the command that is responded.
     * @param commandRequestStatus The status of the command that is responded by the device.
     * @throws NullPointerException if ctx, tenant or deviceId is {@code null}.
     * @throws IllegalArgumentException if the commandRequestId cannot be processed since it is invalid, or if the commandRequestStatus
     *          does not contain a valid status code.
     */
    public final void uploadCommandResponseMessage(final RoutingContext ctx, final String tenant, final String deviceId,
                                                   final String commandRequestId, final Integer commandRequestStatus) {
        Objects.requireNonNull(ctx);
        Objects.requireNonNull(tenant);
        Objects.requireNonNull(deviceId);

        final Buffer payload = ctx.getBody();
        final String contentType = HttpUtils.getContentType(ctx);

        LOG.debug("uploadCommandResponseMessage: [tenantId: {}, deviceId: {}, commandRequestId: {}, commandRequestStatus: {}]",
                tenant, deviceId, commandRequestId, commandRequestStatus);

        CommandConstants.validateCommandResponseStatusCode(commandRequestStatus).map(statusCode ->
                getCorrelationIdAndReplyToFromCommandRequestId(commandRequestId).map(commandRequestIdParts -> {
                    final String correlationId = commandRequestIdParts[0];
                    final String replyId = commandRequestIdParts[1];

                    // send answer to caller via sender link
                    final Future<CommandResponseSender> responseSender = createCommandResponseSender(tenant, deviceId, replyId);

                    responseSender.compose(commandResponseSender ->
                            commandResponseSender.sendCommandResponse(correlationId, contentType, payload, null, statusCode)
                    ).map(delivery -> {
                        if (delivery.remotelySettled()) {
                            LOG.debug("Command response [command-request-id: {}] acknowledged to sender.", commandRequestId);
                            ctx.response().setStatusCode(HttpURLConnection.HTTP_ACCEPTED);
                        } else {
                            LOG.debug("Command response [command-request-id: {}] failed - not remotely settled by sender.", commandRequestId);
                            ctx.response().setStatusCode(HttpURLConnection.HTTP_UNAVAILABLE);
                        }
                        responseSender.result().close(v -> {
                        });
                        ctx.response().end();
                        return delivery;
                    }).otherwise(t -> {
                        LOG.debug("Command response [command-request-id: {}] failed", commandRequestId, t);
                        Optional.ofNullable(responseSender.result()).map(r -> {
                            r.close(v -> {
                            });
                            return r;
                        });
                        ctx.response().setStatusCode(HttpURLConnection.HTTP_UNAVAILABLE);
                        ctx.response().end();
                        return null;
                    });

                    return commandRequestIdParts;
                }).orElseGet(() -> {
                    HttpUtils.badRequest(ctx, String.format("Cannot process command response message - command-request-id %s invalid", commandRequestId));
                    return null;
                })
        ).orElseGet(() -> {
            HttpUtils.badRequest(ctx, String.format("Cannot process command response message - status code %s invalid", commandRequestStatus));
            return null;
        });

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
