/**
 * Copyright (c) 2017, 2018 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 1.0 which is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * SPDX-License-Identifier: EPL-1.0
 */

package org.eclipse.hono.adapter.mqtt;

import java.net.HttpURLConnection;
import java.util.Objects;
import java.util.Optional;

import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.MessageSender;
import org.eclipse.hono.client.ServerErrorException;
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.config.ProtocolAdapterProperties;
import org.eclipse.hono.service.AbstractProtocolAdapterBase;
import org.eclipse.hono.service.auth.device.Device;
import org.eclipse.hono.service.auth.device.DeviceCredentials;
import org.eclipse.hono.service.auth.device.HonoClientBasedAuthProvider;
import org.eclipse.hono.service.auth.device.UsernamePasswordAuthProvider;
import org.eclipse.hono.service.auth.device.UsernamePasswordCredentials;
import org.eclipse.hono.tracing.TracingHelper;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.EndpointType;
import org.eclipse.hono.util.EventConstants;
import org.eclipse.hono.util.ExecutionContext;
import org.eclipse.hono.util.ResourceIdentifier;
import org.eclipse.hono.util.TelemetryConstants;
import org.eclipse.hono.util.TenantObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import io.netty.handler.codec.mqtt.MqttConnectReturnCode;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.opentracing.Span;
import io.opentracing.tag.Tags;
import io.vertx.core.AsyncResult;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.mqtt.MqttAuth;
import io.vertx.mqtt.MqttConnectionException;
import io.vertx.mqtt.MqttEndpoint;
import io.vertx.mqtt.MqttServer;
import io.vertx.mqtt.MqttServerOptions;

/**
 * A base class for implementing Vert.x based Hono protocol adapters for publishing events &amp; telemetry data using
 * MQTT.
 * 
 * @param <T> The type of configuration properties this adapter supports/requires.
 */
public abstract class AbstractVertxBasedMqttProtocolAdapter<T extends ProtocolAdapterProperties>
        extends AbstractProtocolAdapterBase<T> {

    /**
     * The name of the property that holds the current span.
     */
    public static final String KEY_CURRENT_SPAN = MqttContext.class.getName() + ".serverSpan";

    private static final int IANA_MQTT_PORT = 1883;
    private static final int IANA_SECURE_MQTT_PORT = 8883;

    /**
     * A logger to be used by concrete subclasses.
     */
    protected final Logger LOG = LoggerFactory.getLogger(getClass());

    private MqttAdapterMetrics metrics;

    private MqttServer server;
    private MqttServer insecureServer;
    private HonoClientBasedAuthProvider usernamePasswordAuthProvider;

    /**
     * Sets the provider to use for authenticating devices based on a username and password.
     * 
     * @param provider The provider to use.
     * @throws NullPointerException if provider is {@code null}.
     */
    public final void setUsernamePasswordAuthProvider(final HonoClientBasedAuthProvider provider) {
        this.usernamePasswordAuthProvider = Objects.requireNonNull(provider);
    }

    @Override
    public int getPortDefaultValue() {
        return IANA_SECURE_MQTT_PORT;
    }

    @Override
    public int getInsecurePortDefaultValue() {
        return IANA_MQTT_PORT;
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
     * Sets the metrics for this service.
     *
     * @param metrics The metrics
     */
    @Autowired
    public final void setMetrics(final MqttAdapterMetrics metrics) {
        this.metrics = metrics;
    }

    /**
     * Sets the MQTT server to use for handling secure MQTT connections.
     * 
     * @param server The server.
     * @throws NullPointerException if the server is {@code null}.
     */
    public void setMqttSecureServer(final MqttServer server) {
        Objects.requireNonNull(server);
        if (server.actualPort() > 0) {
            throw new IllegalArgumentException("MQTT server must not be started already");
        } else {
            this.server = server;
        }
    }

    /**
     * Sets the MQTT server to use for handling non-secure MQTT connections.
     * 
     * @param server The server.
     * @throws NullPointerException if the server is {@code null}.
     */
    public void setMqttInsecureServer(final MqttServer server) {
        Objects.requireNonNull(server);
        if (server.actualPort() > 0) {
            throw new IllegalArgumentException("MQTT server must not be started already");
        } else {
            this.insecureServer = server;
        }
    }

    private Future<Void> bindSecureMqttServer() {

        if (isSecurePortEnabled()) {
            final MqttServerOptions options = new MqttServerOptions();
            options
                    .setHost(getConfig().getBindAddress())
                    .setPort(determineSecurePort())
                    .setMaxMessageSize(getConfig().getMaxPayloadSize());
            addTlsKeyCertOptions(options);
            addTlsTrustOptions(options);

            return bindMqttServer(options, server).map(s -> {
                server = s;
                return (Void) null;
            }).recover(t -> {
                return Future.failedFuture(t);
            });
        } else {
            return Future.succeededFuture();
        }
    }

    private Future<Void> bindInsecureMqttServer() {

        if (isInsecurePortEnabled()) {
            final MqttServerOptions options = new MqttServerOptions();
            options
                    .setHost(getConfig().getInsecurePortBindAddress())
                    .setPort(determineInsecurePort())
                    .setMaxMessageSize(getConfig().getMaxPayloadSize());

            return bindMqttServer(options, insecureServer).map(server -> {
                insecureServer = server;
                return (Void) null;
            }).recover(t -> {
                return Future.failedFuture(t);
            });
        } else {
            return Future.succeededFuture();
        }
    }

    private Future<MqttServer> bindMqttServer(final MqttServerOptions options, final MqttServer mqttServer) {

        final Future<MqttServer> result = Future.future();
        final MqttServer createdMqttServer = mqttServer == null ? MqttServer.create(this.vertx, options) : mqttServer;

        createdMqttServer
                .endpointHandler(this::handleEndpointConnection)
                .listen(done -> {

                    if (done.succeeded()) {
                        LOG.info("MQTT server running on {}:{}", getConfig().getBindAddress(),
                                createdMqttServer.actualPort());
                        result.complete(createdMqttServer);
                    } else {
                        LOG.error("error while starting up MQTT server", done.cause());
                        result.fail(done.cause());
                    }
                });
        return result;
    }

    @Override
    public void doStart(final Future<Void> startFuture) {

        LOG.info("limiting size of inbound message payload to {} bytes", getConfig().getMaxPayloadSize());
        if (!getConfig().isAuthenticationRequired()) {
            LOG.warn("authentication of devices turned off");
        }
        checkPortConfiguration()
            .compose(ok -> {
                if (metrics == null) {
                    // use default implementation
                    // which simply discards all reported metrics
                    metrics = new MqttAdapterMetrics();
                }
                return CompositeFuture.all(bindSecureMqttServer(), bindInsecureMqttServer());
            }).compose(t -> {
                if (usernamePasswordAuthProvider == null) {
                    usernamePasswordAuthProvider = new UsernamePasswordAuthProvider(getCredentialsServiceClient(),
                            getConfig());
                }
                startFuture.complete();
            }, startFuture);
    }

    @Override
    public void doStop(final Future<Void> stopFuture) {

        final Future<Void> serverTracker = Future.future();
        if (this.server != null) {
            this.server.close(serverTracker.completer());
        } else {
            serverTracker.complete();
        }

        final Future<Void> insecureServerTracker = Future.future();
        if (this.insecureServer != null) {
            this.insecureServer.close(insecureServerTracker.completer());
        } else {
            insecureServerTracker.complete();
        }

        CompositeFuture.all(serverTracker, insecureServerTracker)
                .compose(d -> stopFuture.complete(), stopFuture);
    }

    /**
     * Create a future indicating a rejected connection.
     * 
     * @param returnCode The error code to return.
     * @param <T> The type of the returned future.
     * @return A future indicating a rejected connection.
     */
    protected static <T> Future<T> rejected(final MqttConnectReturnCode returnCode) {
        return Future.failedFuture(new MqttConnectionException(
                returnCode != null ? returnCode : MqttConnectReturnCode.CONNECTION_REFUSED_SERVER_UNAVAILABLE));
    }

    /**
     * Create a future indicating an accepted connection with an authenticated device.
     * 
     * @param authenticatedDevice The device that was authenticated, may be {@code null}
     * @return A future indicating an accepted connection.
     */
    protected static Future<Device> accepted(final Device authenticatedDevice) {
        return Future.succeededFuture(authenticatedDevice);
    }

    /**
     * Create a future indicating an accepted connection without an authenticated device.
     * 
     * @return A future indicating an accepted connection.
     */
    protected static Future<Device> accepted() {
        return Future.succeededFuture(null);
    }

    /**
     * Invoked when a client sends its <em>CONNECT</em> packet.
     * <p>
     * Authenticates the client (if required) and registers handlers for processing messages published by the client.
     * 
     * @param endpoint The MQTT endpoint representing the client.
     */
    final void handleEndpointConnection(final MqttEndpoint endpoint) {

        LOG.debug("connection request from client [clientId: {}]", endpoint.clientIdentifier());

        isConnected()
                .compose(v -> handleConnectionRequest(endpoint))
                .setHandler(result -> handleConnectionRequestResult(endpoint, result));

    }

    private Future<Device> handleConnectionRequest(final MqttEndpoint endpoint) {
        if (getConfig().isAuthenticationRequired()) {
            return handleEndpointConnectionWithAuthentication(endpoint);
        } else {
            return handleEndpointConnectionWithoutAuthentication(endpoint);
        }
    }

    private void handleConnectionRequestResult(final MqttEndpoint endpoint,
            final AsyncResult<Device> authenticationAttempt) {

        if (authenticationAttempt.succeeded()) {

            sendConnectedEvent(endpoint.clientIdentifier(), authenticationAttempt.result())
                    .setHandler(sendAttempt -> {
                        if (sendAttempt.succeeded()) {
                            endpoint.accept(false);
                        } else {
                            LOG.warn(
                                    "connection request from client [clientId: {}] rejected due to connection event "
                                            + "failure: {}",
                                    endpoint.clientIdentifier(),
                                    MqttConnectReturnCode.CONNECTION_REFUSED_SERVER_UNAVAILABLE,
                                    sendAttempt.cause());
                            endpoint.reject(MqttConnectReturnCode.CONNECTION_REFUSED_SERVER_UNAVAILABLE);
                        }
                    });

        } else {

            final Throwable t = authenticationAttempt.cause();
            if (t instanceof MqttConnectionException) {
                final MqttConnectReturnCode code = ((MqttConnectionException) t).code();
                LOG.debug("connection request from client [clientId: {}] rejected with code: {}",
                        endpoint.clientIdentifier(), code);
                endpoint.reject(code);
            } else {
                LOG.debug("connection request from client [clientId: {}] rejected: {}",
                        endpoint.clientIdentifier(), MqttConnectReturnCode.CONNECTION_REFUSED_SERVER_UNAVAILABLE);
                endpoint.reject(MqttConnectReturnCode.CONNECTION_REFUSED_SERVER_UNAVAILABLE);
            }

        }
    }

    /**
     * Invoked when a client sends its <em>CONNECT</em> packet and client authentication has been disabled by setting
     * the {@linkplain ProtocolAdapterProperties#isAuthenticationRequired() authentication required} configuration
     * property to {@code false}.
     * <p>
     * Registers a close handler on the endpoint which invokes {@link #close(MqttEndpoint, Device)}. Registers a publish
     * handler on the endpoint which invokes {@link #onPublishedMessage(MqttContext)} for each message being published
     * by the client. Accepts the connection request.
     * 
     * @param endpoint The MQTT endpoint representing the client.
     */
    private Future<Device> handleEndpointConnectionWithoutAuthentication(final MqttEndpoint endpoint) {

        endpoint.closeHandler(v -> {
            close(endpoint, null);
            LOG.debug("connection to unauthenticated device [clientId: {}] closed", endpoint.clientIdentifier());
            metrics.decrementUnauthenticatedMqttConnections();
        });
        endpoint.publishHandler(message -> handlePublishedMessage(new MqttContext(message, endpoint)));

        LOG.debug("unauthenticated device [clientId: {}] connected", endpoint.clientIdentifier());
        metrics.incrementUnauthenticatedMqttConnections();
        return accepted();
    }

    private Future<Device> handleEndpointConnectionWithAuthentication(final MqttEndpoint endpoint) {

        if (endpoint.auth() == null) {

            LOG.debug("connection request from device [clientId: {}] rejected: {}",
                    endpoint.clientIdentifier(), "device did not provide credentials in CONNECT packet");

            return rejected(MqttConnectReturnCode.CONNECTION_REFUSED_BAD_USER_NAME_OR_PASSWORD);

        } else {

            final DeviceCredentials credentials = getCredentials(endpoint.auth());

            if (credentials == null) {

                LOG.debug("connection request from device [clientId: {}] rejected: {}",
                        endpoint.clientIdentifier(), "device provided malformed credentials in CONNECT packet");
                return rejected(MqttConnectReturnCode.CONNECTION_REFUSED_BAD_USER_NAME_OR_PASSWORD);

            } else {

                return getTenantConfiguration(credentials.getTenantId()).compose(tenantConfig -> {
                    if (tenantConfig.isAdapterEnabled(getTypeName())) {
                        LOG.debug("protocol adapter [{}] is enabled for tenant [{}]",
                                getTypeName(), credentials.getTenantId());
                        return Future.succeededFuture(tenantConfig);
                    } else {
                        LOG.debug("protocol adapter [{}] is disabled for tenant [{}]",
                                getTypeName(), credentials.getTenantId());
                        return Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_FORBIDDEN,
                                "adapter disabled for tenant"));
                    }
                }).compose(tenantConfig -> {
                    final Future<Device> result = Future.future();
                    usernamePasswordAuthProvider.authenticate(credentials, result.completer());
                    return result;
                }).compose(authenticatedDevice -> {
                    LOG.debug("successfully authenticated device [tenant-id: {}, auth-id: {}, device-id: {}]",
                            authenticatedDevice.getTenantId(), credentials.getAuthId(),
                            authenticatedDevice.getDeviceId());
                    return triggerLinkCreation(authenticatedDevice.getTenantId()).map(done -> {
                        onAuthenticationSuccess(endpoint, authenticatedDevice);
                        return null;
                    }).compose(ok -> accepted(authenticatedDevice));
                }).recover(t -> {
                    LOG.debug("cannot establish connection with device [tenant-id: {}, auth-id: {}]",
                            credentials.getTenantId(), credentials.getAuthId(), t);
                    if (t instanceof ServerErrorException) {
                        // one of the services we depend on might not be available (yet)
                        return rejected(MqttConnectReturnCode.CONNECTION_REFUSED_SERVER_UNAVAILABLE);
                    } else {
                        // validation of credentials has failed
                        return rejected(MqttConnectReturnCode.CONNECTION_REFUSED_NOT_AUTHORIZED);
                    }
                });

            }
        }
    }

    private void onAuthenticationSuccess(final MqttEndpoint endpoint, final Device authenticatedDevice) {

        endpoint.closeHandler(v -> {
            close(endpoint, authenticatedDevice);
            LOG.debug("connection to device [tenant-id: {}, device-id: {}] closed",
                    authenticatedDevice.getTenantId(), authenticatedDevice.getDeviceId());
            metrics.decrementMqttConnections(authenticatedDevice.getTenantId());
        });

        endpoint.publishHandler(message -> handlePublishedMessage(new MqttContext(message, endpoint, authenticatedDevice)));
        metrics.incrementMqttConnections(authenticatedDevice.getTenantId());
    }

    private Future<Void> triggerLinkCreation(final String tenantId) {

        final Future<MessageSender> telemetrySender = getTelemetrySender(tenantId);
        final Future<MessageSender> eventSender = getEventSender(tenantId);
        return CompositeFuture.all(getRegistrationClient(tenantId), telemetrySender, eventSender).map(ok -> {
            LOG.debug("providently opened downstream links [credit telemetry: {}, credit event: {}] for tenant [{}]",
                    telemetrySender.result().getCredit(), eventSender.result().getCredit(), tenantId);
            return null;
        });
    }

    private void handlePublishedMessage(final MqttContext context) {
        // there is is no way to extract a SpanContext from an MQTT 3.1 message
        // so we start a new one for every message
        final Span span = tracer.buildSpan("upload message")
            .ignoreActiveSpan()
            .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_SERVER)
            .withTag(Tags.MESSAGE_BUS_DESTINATION.getKey(), context.message().topicName())
            .withTag(TracingHelper.TAG_QOS.getKey(), context.message().qosLevel().toString())
            .withTag(Tags.COMPONENT.getKey(), getTypeName())
            .start();
        context.put(KEY_CURRENT_SPAN, span);
        onPublishedMessage(context).setHandler(processing -> {
            if (processing.succeeded()) {
                span.setTag(Tags.HTTP_STATUS.getKey(), HttpURLConnection.HTTP_ACCEPTED);
            } else {
                TracingHelper.logError(span, processing.cause());
            }
            span.finish();
        });
    }

    /**
     * Uploads a message to Hono Messaging.
     * 
     * @param ctx The context in which the MQTT message has been published.
     * @param resource The resource that the message should be uploaded to.
     * @param payload The message payload to send.
     * @return A future indicating the outcome of the operation.
     *         <p>
     *         The future will succeed if the message has been uploaded successfully. Otherwise the future will fail
     *         with a {@link ServiceInvocationException}.
     * @throws NullPointerException if any of context, resource or payload is {@code null}.
     * @throws IllegalArgumentException if the payload is empty.
     */
    public final Future<Void> uploadMessage(
            final MqttContext ctx,
            final ResourceIdentifier resource,
            final Buffer payload) {

        Objects.requireNonNull(ctx);
        Objects.requireNonNull(resource);
        Objects.requireNonNull(payload);

        switch (EndpointType.fromString(resource.getEndpoint())) {
        case TELEMETRY:
            return uploadTelemetryMessage(
                    ctx,
                    resource.getTenantId(),
                    resource.getResourceId(),
                    payload);
        case EVENT:
            return uploadEventMessage(
                    ctx,
                    resource.getTenantId(),
                    resource.getResourceId(),
                    payload);
        default:
            return Future
                    .failedFuture(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST, "unsupported endpoint"));
        }

    }

    /**
     * Uploads a telemetry message to Hono Messaging.
     * 
     * @param ctx The context in which the MQTT message has been published.
     * @param tenant The tenant of the device that has produced the data.
     * @param deviceId The id of the device that has produced the data.
     * @param payload The message payload to send.
     * @return A future indicating the outcome of the operation.
     *         <p>
     *         The future will succeed if the message has been uploaded successfully. Otherwise the future will fail
     *         with a {@link ServiceInvocationException}.
     * @throws NullPointerException if any of context, tenant, device ID or payload is {@code null}.
     * @throws IllegalArgumentException if the payload is empty.
     */
    public final Future<Void> uploadTelemetryMessage(
            final MqttContext ctx,
            final String tenant,
            final String deviceId,
            final Buffer payload) {

        return uploadMessage(
                Objects.requireNonNull(ctx),
                Objects.requireNonNull(tenant),
                Objects.requireNonNull(deviceId),
                Objects.requireNonNull(payload),
                getTelemetrySender(tenant),
                TelemetryConstants.TELEMETRY_ENDPOINT);
    }

    /**
     * Uploads an event message to Hono Messaging.
     * 
     * @param ctx The context in which the MQTT message has been published.
     * @param tenant The tenant of the device that has produced the data.
     * @param deviceId The id of the device that has produced the data.
     * @param payload The message payload to send.
     * @return A future indicating the outcome of the operation.
     *         <p>
     *         The future will succeed if the message has been uploaded successfully. Otherwise the future will fail
     *         with a {@link ServiceInvocationException}.
     * @throws NullPointerException if any of context, tenant, device ID or payload is {@code null}.
     * @throws IllegalArgumentException if the payload is empty.
     */
    public final Future<Void> uploadEventMessage(
            final MqttContext ctx,
            final String tenant,
            final String deviceId,
            final Buffer payload) {

        return uploadMessage(
                Objects.requireNonNull(ctx),
                Objects.requireNonNull(tenant),
                Objects.requireNonNull(deviceId),
                Objects.requireNonNull(payload),
                getEventSender(tenant),
                EventConstants.EVENT_ENDPOINT);
    }

    private Future<Void> uploadMessage(final MqttContext ctx, final String tenant, final String deviceId,
            final Buffer payload, final Future<MessageSender> senderTracker, final String endpointName) {

        if (!isPayloadOfIndicatedType(payload, ctx.contentType())) {
            return Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST,
                    String.format("Content-Type %s does not match with payload", ctx.contentType())));
        } else {

            Optional.ofNullable(getCurrentSpan(ctx)).map(span -> {
                span.setOperationName("upload " + endpointName);
                TracingHelper.TAG_AUTHENTICATED.set(span, ctx.authenticatedDevice() != null);
                return span.context();
            }).orElse(null);

            final Future<JsonObject> tokenTracker = getRegistrationAssertion(tenant, deviceId,
                    ctx.authenticatedDevice());
            final Future<TenantObject> tenantConfigTracker = getTenantConfiguration(tenant);

            return CompositeFuture.all(tokenTracker, tenantConfigTracker, senderTracker).compose(ok -> {

                if (tenantConfigTracker.result().isAdapterEnabled(getTypeName())) {

                    final MessageSender sender = senderTracker.result();
                    final Message downstreamMessage = newMessage(
                            ResourceIdentifier.from(endpointName, tenant, deviceId),
                            sender.isRegistrationAssertionRequired(),
                            ctx.message().topicName(),
                            ctx.contentType(),
                            payload,
                            tokenTracker.result(),
                            null);
                    customizeDownstreamMessage(downstreamMessage, ctx);

                    if (ctx.message().qosLevel() == MqttQoS.AT_LEAST_ONCE) {
                        return sender.sendAndWaitForOutcome(downstreamMessage);
                    } else {
                        return sender.send(downstreamMessage);
                    }
                } else {
                    // this adapter is not enabled for the tenant
                    return Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_FORBIDDEN,
                            "adapter is not enabled for tenant"));
                }

            }).compose(delivery -> {

                LOG.trace("successfully processed message [topic: {}, QoS: {}] for device [tenantId: {}, deviceId: {}]",
                        ctx.message().topicName(), ctx.message().qosLevel(), tenant, deviceId);
                metrics.incrementProcessedMqttMessages(endpointName, tenant);
                onMessageSent(ctx);
                // check that the remote MQTT client is still connected before sending PUBACK
                if (ctx.deviceEndpoint().isConnected() && ctx.message().qosLevel() == MqttQoS.AT_LEAST_ONCE) {
                    ctx.deviceEndpoint().publishAcknowledge(ctx.message().messageId());
                }
                return Future.<Void> succeededFuture();

            }).recover(t -> {

                if (ClientErrorException.class.isInstance(t)) {
                    final ClientErrorException e = (ClientErrorException) t;
                    LOG.debug("cannot process message for device [tenantId: {}, deviceId: {}, endpoint: {}]: {} - {}",
                            tenant, deviceId, endpointName, e.getErrorCode(), e.getMessage());
                } else {
                    LOG.debug("cannot process message for device [tenantId: {}, deviceId: {}, endpoint: {}]",
                            tenant, deviceId, endpointName, t);
                    metrics.incrementUndeliverableMqttMessages(endpointName, tenant);
                    onMessageUndeliverable(ctx);
                }
                return Future.failedFuture(t);
            });
        }
    }

    /**
     * Closes a connection to a client.
     * 
     * @param endpoint The connection to close.
     * @param authenticatedDevice Optional authenticated device information, may be {@code null}.
     */
    protected final void close(final MqttEndpoint endpoint, final Device authenticatedDevice) {
        onClose(endpoint);
        sendDisconnectedEvent(endpoint.clientIdentifier(), authenticatedDevice);
        if (endpoint.isConnected()) {
            LOG.debug("closing connection with client [client ID: {}]", endpoint.clientIdentifier());
            endpoint.close();
        } else {
            LOG.trace("client has already closed connection");
        }
    }

    /**
     * Gets the current span from an execution context.
     * 
     * @param ctx The context.
     * @return The span or {@code null} if the context does not
     *         contain a span.
     */
    protected final Span getCurrentSpan(final ExecutionContext ctx) {
        if (ctx == null) {
            return null;
        } else {
            return ctx.get(KEY_CURRENT_SPAN);
        }
    }

    /**
     * Sets the current span on an execution context.
     * 
     * @param ctx The context.
     * @param span The current span.
     */
    protected final void setCurrentSpan(final ExecutionContext ctx, final Span span) {

        Objects.requireNonNull(ctx);
        if (span != null) {
            ctx.put(KEY_CURRENT_SPAN, span);
        }
    }

    /**
     * Invoked before the connection with a device is closed.
     * <p>
     * Subclasses should override this method in order to release any device specific resources.
     * <p>
     * This default implementation does nothing.
     * 
     * @param endpoint The connection to be closed.
     */
    protected void onClose(final MqttEndpoint endpoint) {
    }

    /**
     * Extracts credentials from a client's MQTT <em>CONNECT</em> packet.
     * <p>
     * This default implementation returns {@link UsernamePasswordCredentials} created from the <em>username</em> and
     * <em>password</em> fields of the <em>CONNECT</em> packet.
     * <p>
     * Subclasses should override this method if the device uses credentials that do not comply with the format expected
     * by {@link UsernamePasswordCredentials}.
     * 
     * @param authInfo The authentication info provided by the device.
     * @return The credentials or {@code null} if the information provided by the device can not be processed.
     */
    protected DeviceCredentials getCredentials(final MqttAuth authInfo) {
        if (authInfo.userName() == null || authInfo.password() == null) {
            return null;
        } else {
            return UsernamePasswordCredentials.create(authInfo.userName(), authInfo.password(),
                    getConfig().isSingleTenant());
        }
    }

    /**
     * Processes an MQTT message that has been published by a device.
     * <p>
     * Subclasses should determine
     * <ul>
     * <li>the tenant and identifier of the device that has published the message</li>
     * <li>the payload to send downstream</li>
     * <li>the content type of the payload</li>
     * </ul>
     * and then invoke one of the <em>upload*</em> methods to send the message downstream.
     * 
     * @param ctx The context in which the MQTT message has been published.
     * @return A future indicating the outcome of the operation.
     *         <p>
     *         The future will succeed if the message has been successfully uploaded. Otherwise, the future will fail
     *         with a {@link ServiceInvocationException}.
     */
    protected abstract Future<Void> onPublishedMessage(MqttContext ctx);

    /**
     * Invoked before the message is sent to the downstream peer.
     * <p>
     * This default implementation does nothing.
     * <p>
     * Subclasses may override this method in order to customize the message before it is sent, e.g. adding custom
     * properties.
     * 
     * @param downstreamMessage The message that will be sent downstream.
     * @param ctx The context in which the MQTT message has been published.
     */
    protected void customizeDownstreamMessage(final Message downstreamMessage, final MqttContext ctx) {
    }

    /**
     * Invoked when a message has been forwarded downstream successfully.
     * <p>
     * This default implementation does nothing.
     * <p>
     * Subclasses should override this method in order to e.g. update metrics counters.
     * 
     * @param ctx The context in which the MQTT message has been published.
     */
    protected void onMessageSent(final MqttContext ctx) {
    }

    /**
     * Invoked when a message could not be forwarded downstream.
     * <p>
     * This method will only be invoked if the failure to forward the message has not been caused by the device that
     * published the message. In particular, this method will not be invoked for messages that cannot be authorized or
     * that are published to an unsupported/unrecognized topic. Such messages will be silently discarded.
     * <p>
     * This default implementation does nothing.
     * <p>
     * Subclasses should override this method in order to e.g. update metrics counters.
     * 
     * @param ctx The context in which the MQTT message has been published.
     */
    protected void onMessageUndeliverable(final MqttContext ctx) {
    }
}
