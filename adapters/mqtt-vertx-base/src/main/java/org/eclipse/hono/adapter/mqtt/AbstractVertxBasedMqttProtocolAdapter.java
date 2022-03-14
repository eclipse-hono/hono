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

package org.eclipse.hono.adapter.mqtt;

import java.net.HttpURLConnection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.net.ssl.SSLSession;

import org.eclipse.hono.adapter.AbstractProtocolAdapterBase;
import org.eclipse.hono.adapter.AdapterConnectionsExceededException;
import org.eclipse.hono.adapter.AuthorizationException;
import org.eclipse.hono.adapter.auth.device.AuthHandler;
import org.eclipse.hono.adapter.auth.device.ChainAuthHandler;
import org.eclipse.hono.adapter.auth.device.CredentialsApiAuthProvider;
import org.eclipse.hono.adapter.auth.device.DeviceCredentials;
import org.eclipse.hono.adapter.auth.device.TenantServiceBasedX509Authentication;
import org.eclipse.hono.adapter.auth.device.UsernamePasswordAuthProvider;
import org.eclipse.hono.adapter.auth.device.X509AuthProvider;
import org.eclipse.hono.adapter.limiting.ConnectionLimitManager;
import org.eclipse.hono.adapter.limiting.DefaultConnectionLimitManager;
import org.eclipse.hono.adapter.limiting.MemoryBasedConnectionLimitStrategy;
import org.eclipse.hono.auth.Device;
import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.ServerErrorException;
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.client.command.CommandContext;
import org.eclipse.hono.client.command.CommandResponse;
import org.eclipse.hono.notification.NotificationEventBusSupport;
import org.eclipse.hono.notification.deviceregistry.AllDevicesOfTenantDeletedNotification;
import org.eclipse.hono.notification.deviceregistry.DeviceChangeNotification;
import org.eclipse.hono.notification.deviceregistry.LifecycleChange;
import org.eclipse.hono.notification.deviceregistry.TenantChangeNotification;
import org.eclipse.hono.service.auth.DeviceUser;
import org.eclipse.hono.service.metric.MetricsTags;
import org.eclipse.hono.service.metric.MetricsTags.ConnectionAttemptOutcome;
import org.eclipse.hono.service.metric.MetricsTags.Direction;
import org.eclipse.hono.service.metric.MetricsTags.EndpointType;
import org.eclipse.hono.service.metric.MetricsTags.ProcessingOutcome;
import org.eclipse.hono.tracing.TenantTraceSamplingHelper;
import org.eclipse.hono.tracing.TracingHelper;
import org.eclipse.hono.util.CommandConstants;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.Futures;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.RegistrationAssertion;
import org.eclipse.hono.util.TenantObject;

import io.netty.handler.codec.mqtt.MqttConnectReturnCode;
import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.tag.Tags;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
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
public abstract class AbstractVertxBasedMqttProtocolAdapter<T extends MqttProtocolAdapterProperties>
        extends AbstractProtocolAdapterBase<T> {

    /**
     * The minimum amount of memory (bytes) that the adapter requires to run on a standard Java VM.
     */
    protected static final int MINIMAL_MEMORY_JVM = 100_000_000;
    /**
     * The minimum amount of memory (bytes) that the adapter requires to run on a Substrate VM (i.e. when running
     * as a GraalVM native image).
     */
    protected static final int MINIMAL_MEMORY_SUBSTRATE = 35_000_000;
    /**
     * The amount of memory (bytes) required for each connection.
     */
    protected static final int MEMORY_PER_CONNECTION = 20_000;
    /**
     * The number of bytes added to the configured max payload size to set the max MQTT message size.
     * Accounts for the size of the variable header (including the topic name) included in the max message size
     * calculation.
     */
    protected static final int MAX_MSG_SIZE_VARIABLE_HEADER_SIZE = 128;

    private static final int IANA_MQTT_PORT = 1883;
    private static final int IANA_SECURE_MQTT_PORT = 8883;

    private final AtomicReference<Promise<Void>> stopResultPromiseRef = new AtomicReference<>();

    private MqttAdapterMetrics metrics = MqttAdapterMetrics.NOOP;

    private MqttServer server;
    private MqttServer insecureServer;
    private AuthHandler<MqttConnectContext> authHandler;

    /**
     * The endpoints representing the currently connected, authenticated MQTT devices.
     */
    private final Set<MqttDeviceEndpoint<T>> connectedAuthenticatedDeviceEndpoints = new HashSet<>();

    /**
     * Sets the authentication handler to use for authenticating devices.
     *
     * @param authHandler The handler to use.
     * @throws NullPointerException if handler is {@code null}.
     */
    public final void setAuthHandler(final AuthHandler<MqttConnectContext> authHandler) {
        this.authHandler = Objects.requireNonNull(authHandler);
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
     * Sets the metrics for this adapter.
     *
     * @param metrics The metrics.
     */
    public final void setMetrics(final MqttAdapterMetrics metrics) {
        Optional.ofNullable(metrics)
            .ifPresent(m -> log.info("reporting metrics using [{}]", metrics.getClass().getName()));
        this.metrics = metrics;
    }

    /**
     * Gets the metrics for this adapter.
     *
     * @return The metrics.
     */
    protected final MqttAdapterMetrics getMetrics() {
        return metrics;
    }

    /**
     * Gets the tracer for this adapter.
     *
     * @return The tracer.
     */
    protected final Tracer getTracer() {
        return tracer;
    }

    /**
     * Creates the default auth handler to use for authenticating devices.
     * <p>
     * This default implementation creates a {@link ChainAuthHandler} consisting of
     * an {@link X509AuthHandler} and a {@link ConnectPacketAuthHandler} instance.
     * <p>
     * Subclasses may either set the auth handler explicitly using
     * {@link #setAuthHandler(AuthHandler)} or override this method in order to
     * create a custom auth handler.
     *
     * @return The handler.
     */
    protected AuthHandler<MqttConnectContext> createAuthHandler() {

        return new ChainAuthHandler<>(this::handleBeforeCredentialsValidation)
                .append(new X509AuthHandler(
                        new TenantServiceBasedX509Authentication(getTenantClient(), tracer),
                        new X509AuthProvider(getCredentialsClient(), tracer)))
                .append(new ConnectPacketAuthHandler(
                        new UsernamePasswordAuthProvider(
                                getCredentialsClient(),
                                tracer)));
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
            final MqttConnectContext executionContext) {

        final String tenantId = credentials.getTenantId();
        final Span span = executionContext.getTracingSpan();
        final String authId = credentials.getAuthId();

        return getTenantConfiguration(tenantId, span.context())
                .recover(t -> Future.failedFuture(CredentialsApiAuthProvider.mapNotFoundToBadCredentialsException(t)))
                .map(tenantObject -> {
                    TracingHelper.setDeviceTags(span, tenantId, null, authId);
                    final OptionalInt traceSamplingPriority = TenantTraceSamplingHelper.applyTraceSamplingPriority(
                            tenantObject, authId, span);
                    executionContext.setTraceSamplingPriority(traceSamplingPriority);
                    return tenantObject;
                })
                .compose(this::isAdapterEnabled)
                .mapEmpty();
    }

    /**
     * Sets the MQTT server to use for handling secure MQTT connections.
     *
     * @param server The server.
     * @throws NullPointerException if the server is {@code null}.
     * @throws IllegalStateException if the adapter has already been started.
     */
    public void setMqttSecureServer(final MqttServer server) {
        Objects.requireNonNull(server);
        if (server.actualPort() > 0) {
            throw new IllegalStateException("MQTT server must not be started already");
        } else {
            this.server = server;
        }
    }

    /**
     * Sets the MQTT server to use for handling non-secure MQTT connections.
     *
     * @param server The server.
     * @throws NullPointerException if the server is {@code null}.
     * @throws IllegalStateException if the adapter has already been started.
     */
    public void setMqttInsecureServer(final MqttServer server) {
        Objects.requireNonNull(server);
        if (server.actualPort() > 0) {
            throw new IllegalStateException("MQTT server must not be started already");
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
                    .setMaxMessageSize(getConfig().getMaxPayloadSize() + MAX_MSG_SIZE_VARIABLE_HEADER_SIZE);
            addTlsKeyCertOptions(options);
            addTlsTrustOptions(options);

            return bindMqttServer(options, server).map(s -> {
                server = s;
                return null;
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
                    .setMaxMessageSize(getConfig().getMaxPayloadSize() + MAX_MSG_SIZE_VARIABLE_HEADER_SIZE);

            return bindMqttServer(options, insecureServer).map(createdServer -> {
                insecureServer = createdServer;
                return null;
            });
        } else {
            return Future.succeededFuture();
        }
    }

    private Future<MqttServer> bindMqttServer(final MqttServerOptions options, final MqttServer mqttServer) {

        final Promise<MqttServer> result = Promise.promise();
        final MqttServer createdMqttServer = mqttServer == null ? MqttServer.create(this.vertx, options) : mqttServer;

        createdMqttServer
                .endpointHandler(this::handleEndpointConnection)
                .listen(done -> {

                    if (done.succeeded()) {
                        log.info("MQTT server running on {}:{}", getConfig().getBindAddress(),
                                createdMqttServer.actualPort());
                        result.complete(createdMqttServer);
                    } else {
                        log.error("error while starting up MQTT server", done.cause());
                        result.fail(done.cause());
                    }
                });
        return result.future();
    }

    @Override
    protected final void doStart(final Promise<Void> startPromise) {

        registerDeviceAndTenantChangeNotificationConsumers();

        log.info("limiting size of inbound message payload to {} bytes", getConfig().getMaxPayloadSize());
        if (!getConfig().isAuthenticationRequired()) {
            log.warn("authentication of devices turned off");
        }

        final ConnectionLimitManager connectionLimitManager = Optional.ofNullable(
                getConnectionLimitManager()).orElseGet(this::createConnectionLimitManager);
        setConnectionLimitManager(connectionLimitManager);

        checkPortConfiguration()
            .compose(ok -> CompositeFuture.all(bindSecureMqttServer(), bindInsecureMqttServer()))
            .compose(ok -> {
                if (authHandler == null) {
                    authHandler = createAuthHandler();
                }
                return Future.succeededFuture((Void) null);
            })
            .onComplete(startPromise);
    }

    private void registerDeviceAndTenantChangeNotificationConsumers() {
        NotificationEventBusSupport.registerConsumer(vertx, DeviceChangeNotification.TYPE,
                notification -> {
                    if (LifecycleChange.DELETE.equals(notification.getChange())
                            || (LifecycleChange.UPDATE.equals(notification.getChange()) && !notification.isEnabled())) {
                        final String reason = LifecycleChange.DELETE.equals(notification.getChange()) ? "device deleted"
                                : "device disabled";
                        closeDeviceConnectionsOnDeviceOrTenantChange(
                                connectedDevice -> connectedDevice.getTenantId().equals(notification.getTenantId())
                                        && connectedDevice.getDeviceId().equals(notification.getDeviceId()),
                                reason);
                    }
                });
        NotificationEventBusSupport.registerConsumer(vertx, AllDevicesOfTenantDeletedNotification.TYPE,
                notification -> closeDeviceConnectionsOnDeviceOrTenantChange(
                        connectedDevice -> connectedDevice.getTenantId().equals(notification.getTenantId()),
                        "all devices of tenant deleted"));
        NotificationEventBusSupport.registerConsumer(vertx, TenantChangeNotification.TYPE,
                notification -> {
                    if (LifecycleChange.DELETE.equals(notification.getChange())
                            || (LifecycleChange.UPDATE.equals(notification.getChange()) && !notification.isEnabled())) {
                        final String reason = LifecycleChange.DELETE.equals(notification.getChange()) ? "tenant deleted"
                                : "tenant disabled";
                        closeDeviceConnectionsOnDeviceOrTenantChange(
                                connectedDevice -> connectedDevice.getTenantId().equals(notification.getTenantId()),
                                reason);
                    }
                });
    }

    private void closeDeviceConnectionsOnDeviceOrTenantChange(final Predicate<Device> deviceMatchPredicate, final String reason) {
        final List<MqttDeviceEndpoint<T>> deviceEndpoints = connectedAuthenticatedDeviceEndpoints.stream()
                .filter(ep -> deviceMatchPredicate.test(ep.getAuthenticatedDevice()))
                .collect(Collectors.toList()); // using intermediate list since connectedAuthenticatedDeviceEndpoints is modified in the close() method
        deviceEndpoints.forEach(ep -> {
            // decouple the (potentially numerous) close invocations by using context.runOnContext()
            Futures.onCurrentContextCompletionHandler(v -> {
                // sendDisconnectedEvent param false here - sending an event for a deleted/disabled tenant/device would fail
                ep.close(reason, false);
            }).handle(null);
        });
    }

    private ConnectionLimitManager createConnectionLimitManager() {
        return new DefaultConnectionLimitManager(
                new MemoryBasedConnectionLimitStrategy(
                        getConfig().isSubstrateVm() ? MINIMAL_MEMORY_SUBSTRATE : MINIMAL_MEMORY_JVM,
                        MEMORY_PER_CONNECTION),
                () -> metrics.getNumberOfConnections(), getConfig());
    }

    @Override
    protected final void doStop(final Promise<Void> stopPromise) {
        if (!stopResultPromiseRef.compareAndSet(null, stopPromise)) {
            stopResultPromiseRef.get().future().onComplete(stopPromise);
            log.trace("stop already called");
            return;
        }
        final Promise<Void> serverTracker = Promise.promise();
        if (this.server != null) {
            log.info("closing secure MQTT server ...");
            this.server.close(serverTracker);
        } else {
            serverTracker.complete();
        }

        final Promise<Void> insecureServerTracker = Promise.promise();
        if (this.insecureServer != null) {
            log.info("closing insecure MQTT server ...");
            this.insecureServer.close(insecureServerTracker);
        } else {
            insecureServerTracker.complete();
        }

        CompositeFuture.all(serverTracker.future(), insecureServerTracker.future())
            .map(ok -> (Void) null)
            .onComplete(ar -> log.info("MQTT server(s) closed"))
            .onComplete(stopPromise);
    }

    /**
     * Checks whether the {@link #stop(Promise)} method has been called.
     *
     * @return {@code true} if this adapter is (being) stopped.
     */
    protected final boolean stopCalled() {
        return stopResultPromiseRef.get() != null;
    }

    /**
     * Invoked when a client sends its <em>CONNECT</em> packet.
     * <p>
     * Authenticates the client (if required) and registers handlers for processing messages published by the client.
     *
     * @param endpoint The MQTT endpoint representing the client.
     */
    final void handleEndpointConnection(final MqttEndpoint endpoint) {

        log.debug("connection request from client [client-id: {}]", endpoint.clientIdentifier());
        final String cipherSuite = Optional.ofNullable(endpoint.sslSession()).map(SSLSession::getCipherSuite)
                .orElse(null);
        final Span span = tracer.buildSpan("CONNECT")
                .ignoreActiveSpan()
                .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_SERVER)
                .withTag(Tags.COMPONENT.getKey(), getTypeName())
                .withTag(TracingHelper.TAG_CLIENT_ID.getKey(), endpoint.clientIdentifier())
                .start();

        if (!endpoint.isCleanSession()) {
            span.log("ignoring client's intent to resume existing session");
        }
        if (endpoint.will() != null) {
            span.log("ignoring client's last will");
        }
        final AtomicBoolean connectionClosedPrematurely = new AtomicBoolean(false);
        // set a preliminary close handler, will be overwritten at the end of handleConnectionRequest()
        endpoint.closeHandler(v -> {
            log.debug("client closed connection before CONNACK got sent to client [client-id: {}]", endpoint.clientIdentifier());
            TracingHelper.logError(span, "client closed connection");
            connectionClosedPrematurely.set(true);
        });

        handleConnectionRequest(endpoint, connectionClosedPrematurely, span)
            .compose(authenticatedDevice -> handleConnectionRequestResult(endpoint, authenticatedDevice, connectionClosedPrematurely, span))
            .onSuccess(authenticatedDevice -> {
                // we NEVER maintain session state
                endpoint.accept(false);
                span.log("connection accepted");
                metrics.reportConnectionAttempt(
                        ConnectionAttemptOutcome.SUCCEEDED,
                        Optional.ofNullable(authenticatedDevice).map(Device::getTenantId).orElse(null),
                        cipherSuite);
            })
            .onFailure(t -> {
                if (!connectionClosedPrematurely.get()) {
                    log.debug("rejecting connection request from client [clientId: {}], cause:",
                            endpoint.clientIdentifier(), t);

                    final MqttConnectReturnCode code = getConnectReturnCode(t);
                    rejectConnectionRequest(endpoint, code, span);
                    TracingHelper.logError(span, t);
                }
                metrics.reportConnectionAttempt(
                        AbstractProtocolAdapterBase.getOutcome(t),
                        t instanceof ServiceInvocationException
                                ? ((ServiceInvocationException) t).getTenant()
                                : null,
                        cipherSuite);
            })
            .onComplete(result -> span.finish());

    }

    private Future<Device> handleConnectionRequest(final MqttEndpoint endpoint,
            final AtomicBoolean connectionClosedPrematurely, final Span currentSpan) {

        // the ConnectionLimitManager is null in some unit tests
        if (getConnectionLimitManager() != null && getConnectionLimitManager().isLimitExceeded()) {
            currentSpan.log("adapter's connection limit exceeded");
            return Future.failedFuture(new AdapterConnectionsExceededException(null, "adapter's connection limit is exceeded", null));
        }

        if (getConfig().isAuthenticationRequired()) {
            return handleEndpointConnectionWithAuthentication(endpoint, connectionClosedPrematurely, currentSpan);
        } else {
            return handleEndpointConnectionWithoutAuthentication(endpoint);
        }
    }

    private Future<Device> handleConnectionRequestResult(
            final MqttEndpoint endpoint,
            final Device authenticatedDevice,
            final AtomicBoolean connectionClosedPrematurely,
            final Span currentSpan) {

        TracingHelper.TAG_AUTHENTICATED.set(currentSpan, authenticatedDevice != null);
        if (authenticatedDevice != null) {
            TracingHelper.setDeviceTags(currentSpan, authenticatedDevice.getTenantId(),
                    authenticatedDevice.getDeviceId());
        }

        final Promise<Device> result = Promise.promise();

        if (connectionClosedPrematurely.get()) {
            log.debug("abort handling connection request, connection already closed [clientId: {}]",
                    endpoint.clientIdentifier());
            currentSpan.log("abort connection request processing, connection already closed");
            result.fail("connection already closed");
        } else {
            sendConnectedEvent(endpoint.clientIdentifier(), authenticatedDevice, currentSpan.context())
                .map(authenticatedDevice)
                .recover(t -> {
                    log.warn("failed to send connection event for client [clientId: {}]",
                            endpoint.clientIdentifier(), t);
                    return Future.failedFuture(new ServerErrorException(
                            HttpURLConnection.HTTP_UNAVAILABLE,
                            "failed to send connection event",
                            t));
                })
                .onComplete(result);
        }
        return result.future();
    }

    private void rejectConnectionRequest(final MqttEndpoint endpoint, final MqttConnectReturnCode errorCode,
            final Span currentSpan) {
        // MqttEndpointImpl.isClosed==true leads to an IllegalStateException here; try-catch block needed since there is no getter for that field
        try {
            endpoint.reject(errorCode);
            currentSpan.log("connection request rejected");
        } catch (final IllegalStateException ex) {
            if ("MQTT endpoint is closed".equals(ex.getMessage())) {
                log.debug("skipped rejecting connection request, connection already closed [clientId: {}]",
                        endpoint.clientIdentifier());
                currentSpan.log("skipped rejecting connection request, connection already closed");
            } else {
                log.debug("could not reject connection request from client [clientId: {}]: {}",
                        endpoint.clientIdentifier(), ex.toString());
                TracingHelper.logError(currentSpan, "could not reject connection request from client", ex);
            }
        }
    }

    /**
     * Invoked when a client sends its <em>CONNECT</em> packet and client authentication has been disabled by setting
     * the {@linkplain MqttProtocolAdapterProperties#isAuthenticationRequired() authentication required} configuration
     * property to {@code false}.
     * <p>
     * Registers a close handler on the endpoint which invokes {@link #onClose(MqttEndpoint)}.
     * Registers a publish handler on the endpoint which invokes {@link #onPublishedMessage(MqttContext)}
     * for each message being published by the client. Accepts the connection request.
     *
     * @param endpoint The MQTT endpoint representing the client.
     */
    private Future<Device> handleEndpointConnectionWithoutAuthentication(final MqttEndpoint endpoint) {

        createMqttDeviceEndpoint(endpoint, null, OptionalInt.empty());
        metrics.incrementUnauthenticatedConnections();
        log.debug("unauthenticated device [clientId: {}] connected", endpoint.clientIdentifier());
        return Future.succeededFuture(null);
    }

    private Future<Device> handleEndpointConnectionWithAuthentication(final MqttEndpoint endpoint,
            final AtomicBoolean connectionClosedPrematurely, final Span currentSpan) {

        final MqttConnectContext context = MqttConnectContext.fromConnectPacket(endpoint, currentSpan);

        final Future<DeviceUser> authAttempt = authHandler.authenticateDevice(context);
        return authAttempt
                .compose(authenticatedDevice -> CompositeFuture.all(
                        getTenantConfiguration(authenticatedDevice.getTenantId(), currentSpan.context())
                                .compose(tenantObj -> CompositeFuture.all(
                                        isAdapterEnabled(tenantObj),
                                        checkConnectionLimit(tenantObj, currentSpan.context()))),
                        checkDeviceRegistration(authenticatedDevice, currentSpan.context()))
                        .map(authenticatedDevice))
                .compose(authenticatedDevice -> {
                    if (connectionClosedPrematurely.get()) { // check whether client disconnected during credentials validation
                        return Future.failedFuture("connection already closed");
                    }
                    createMqttDeviceEndpoint(endpoint, authenticatedDevice, context.getTraceSamplingPriority());
                    metrics.incrementConnections(authenticatedDevice.getTenantId());
                    return Future.succeededFuture((Device) authenticatedDevice);
                })
                .recover(t -> {
                    if (authAttempt.failed()) {
                        log.debug("device authentication or early stage checks failed", t);
                    } else {
                        log.debug("cannot establish connection with device [tenant-id: {}, device-id: {}]",
                                authAttempt.result().getTenantId(), authAttempt.result().getDeviceId(), t);
                    }
                    return Future.failedFuture(t);
                });
    }

    /**
     * Forwards a message to the AMQP Messaging Network.
     *
     * @param ctx The context created for the MQTT message that is to be forwarded.
     * @return A future indicating the outcome of the operation.
     *         <p>
     *         The future will succeed if the message has been forwarded successfully.
     *         Otherwise the future will fail with a {@link ServiceInvocationException}.
     * @throws NullPointerException if ctx is {@code null}.
     * @throws IllegalArgumentException if the context does not contain tenant/device information.
     */
    public final Future<Void> uploadMessage(final MqttContext ctx) {
        Objects.requireNonNull(ctx);
        verifyTenantAndDeviceContextIsSet(ctx);

        switch (ctx.endpoint()) {
        case TELEMETRY:
            return uploadTelemetryMessage(ctx);
        case EVENT:
            return uploadEventMessage(ctx);
        case COMMAND:
            return uploadCommandResponseMessage(ctx);
        default:
            return Future
                    .failedFuture(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST, "unsupported endpoint"));
        }
    }

    private void verifyTenantAndDeviceContextIsSet(final MqttContext ctx) {
        if (ctx.tenant() == null || ctx.deviceId() == null) {
            throw new IllegalArgumentException("context does not contain tenant/device information");
        }
    }

    /**
     * Forwards a telemetry message to the AMQP Messaging Network.
     *
     * @param ctx The context in which the MQTT message has been published.
     * @return A future indicating the outcome of the operation.
     *         <p>
     *         The future will succeed if the message has been forwarded successfully.
     *         Otherwise the future will fail with a {@link ServiceInvocationException}.
     * @throws NullPointerException if ctx is {@code null}.
     * @throws IllegalArgumentException if the context does not contain a telemetry message
     *         or tenant/device information is not set.
     */
    public final Future<Void> uploadTelemetryMessage(final MqttContext ctx) {
        Objects.requireNonNull(ctx);

        if (ctx.endpoint() != EndpointType.TELEMETRY) {
            throw new IllegalArgumentException("context does not contain telemetry message but " +
                ctx.endpoint().getCanonicalName());
        }
        verifyTenantAndDeviceContextIsSet(ctx);

        final Buffer payload = ctx.payload();
        final MetricsTags.QoS qos = MetricsTags.QoS.from(ctx.qosLevel().value());
        final Future<TenantObject> tenantTracker = getTenantConfiguration(ctx.tenant(), ctx.getTracingContext());

        return tenantTracker
                .compose(tenantObject -> uploadMessage(ctx, tenantObject, ctx.deviceId(), payload, ctx.endpoint()))
                .compose(success -> {
                    metrics.reportTelemetry(
                            ctx.endpoint(),
                            ctx.tenant(),
                            tenantTracker.result(),
                            MetricsTags.ProcessingOutcome.FORWARDED,
                            qos,
                            payload.length(),
                            ctx.getTimer());
                    return Future.<Void> succeededFuture();
                }).recover(t -> {
                    metrics.reportTelemetry(
                            ctx.endpoint(),
                            ctx.tenant(),
                            tenantTracker.result(),
                            ProcessingOutcome.from(t),
                            qos,
                            payload.length(),
                            ctx.getTimer());
                    return Future.failedFuture(t);
                });
    }

    /**
     * Forwards an event to the AMQP Messaging Network.
     *
     * @param ctx The context in which the MQTT message has been published.
     * @return A future indicating the outcome of the operation.
     *         <p>
     *         The future will succeed if the message has been forwarded successfully.
     *         Otherwise the future will fail with a {@link ServiceInvocationException}.
     * @throws NullPointerException if any of ctx, tenant or deviceId is {@code null}.
     * @throws IllegalArgumentException if the context does not contain an event
     *         or tenant/device information is not set.
     */
    public final Future<Void> uploadEventMessage(final MqttContext ctx) {
        Objects.requireNonNull(ctx);

        if (ctx.endpoint() != EndpointType.EVENT) {
            throw new IllegalArgumentException("context does not contain event but " +
                ctx.endpoint().getCanonicalName());
        }
        verifyTenantAndDeviceContextIsSet(ctx);

        final Buffer payload = ctx.payload();
        final MetricsTags.QoS qos = MetricsTags.QoS.from(ctx.qosLevel().value());
        final Future<TenantObject> tenantTracker = getTenantConfiguration(ctx.tenant(), ctx.getTracingContext());

        return tenantTracker
                .compose(tenantObject -> uploadMessage(ctx, tenantObject, ctx.deviceId(), payload, ctx.endpoint()))
                .compose(success -> {
                    metrics.reportTelemetry(
                            ctx.endpoint(),
                            ctx.tenant(),
                            tenantTracker.result(),
                            MetricsTags.ProcessingOutcome.FORWARDED,
                            qos,
                            payload.length(),
                            ctx.getTimer());
                    return Future.<Void> succeededFuture();
                }).recover(t -> {
                    metrics.reportTelemetry(
                            ctx.endpoint(),
                            ctx.tenant(),
                            tenantTracker.result(),
                            ProcessingOutcome.from(t),
                            qos,
                            payload.length(),
                            ctx.getTimer());
                    return Future.failedFuture(t);
                });
    }

    /**
     * Uploads a command response message.
     *
     * @param ctx The context in which the MQTT message has been published.
     * @return A future indicating the outcome of the operation.
     *         <p>
     *         The future will succeed if the message has been uploaded successfully.
     *         Otherwise, the future will fail with a {@link ServiceInvocationException}.
     * @throws NullPointerException if ctx is {@code null}.
     * @throws IllegalArgumentException if the context does not contain a command response
     *         or tenant/device information is not set.
     */
    public final Future<Void> uploadCommandResponseMessage(final MqttContext ctx) {
        Objects.requireNonNull(ctx);

        if (ctx.endpoint() != EndpointType.COMMAND) {
            throw new IllegalArgumentException("context does not contain command response message but " +
                    ctx.endpoint().getCanonicalName());
        }
        verifyTenantAndDeviceContextIsSet(ctx);

        final String[] addressPath = ctx.topic().getResourcePath();
        final String tenantId = ctx.tenant();
        final String deviceId = ctx.deviceId();
        Integer status = null;
        String reqId = null;

        final Future<CommandResponse> commandResponseTracker;
        if (addressPath.length <= CommandConstants.TOPIC_POSITION_RESPONSE_STATUS) {
            commandResponseTracker = Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST,
                    "command response topic has too few segments"));
        } else {
            try {
                status = Integer.parseInt(addressPath[CommandConstants.TOPIC_POSITION_RESPONSE_STATUS]);
            } catch (final NumberFormatException e) {
                log.trace("got invalid status code [{}] [tenant-id: {}, device-id: {}]",
                        addressPath[CommandConstants.TOPIC_POSITION_RESPONSE_STATUS], tenantId, deviceId);
            }
            if (status != null) {
                reqId = addressPath[CommandConstants.TOPIC_POSITION_RESPONSE_REQ_ID];
                final CommandResponse commandResponse = CommandResponse.fromRequestId(reqId, tenantId,
                        deviceId, ctx.payload(), ctx.contentType(), status);

                commandResponseTracker = commandResponse != null ? Future.succeededFuture(commandResponse)
                        : Future.failedFuture(new ClientErrorException(
                                HttpURLConnection.HTTP_BAD_REQUEST, "command response topic contains invalid data"));
            } else {
                // status code could not be parsed
                commandResponseTracker = Future.failedFuture(
                        new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST, "invalid status code"));
            }
        }

        final Span currentSpan = TracingHelper
                .buildChildSpan(tracer, ctx.getTracingContext(), "upload Command response", getTypeName())
                .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_CLIENT)
                .withTag(TracingHelper.TAG_TENANT_ID, tenantId)
                .withTag(TracingHelper.TAG_DEVICE_ID, deviceId)
                .withTag(Constants.HEADER_COMMAND_RESPONSE_STATUS, status)
                .withTag(Constants.HEADER_COMMAND_REQUEST_ID, reqId)
                .withTag(TracingHelper.TAG_AUTHENTICATED.getKey(), ctx.authenticatedDevice() != null)
                .start();

        final int payloadSize = ctx.payload().length();
        final Future<TenantObject> tenantTracker = getTenantConfiguration(tenantId, ctx.getTracingContext());

        return CompositeFuture.all(tenantTracker, commandResponseTracker)
                .compose(success -> {
                    final Future<RegistrationAssertion> deviceRegistrationTracker = getRegistrationAssertion(
                            tenantId,
                            deviceId,
                            ctx.authenticatedDevice(),
                            currentSpan.context());
                    final Future<Void> tenantValidationTracker = CompositeFuture.all(
                            isAdapterEnabled(tenantTracker.result()),
                            checkMessageLimit(tenantTracker.result(), payloadSize, currentSpan.context()))
                            .mapEmpty();
                    return CompositeFuture.all(deviceRegistrationTracker, tenantValidationTracker)
                            .compose(ok -> sendCommandResponse(
                                    tenantTracker.result(),
                                    deviceRegistrationTracker.result(),
                                    commandResponseTracker.result(),
                                    currentSpan.context()));
                })
                .compose(delivery -> {
                    log.trace("successfully forwarded command response from device [tenant-id: {}, device-id: {}]",
                            tenantId, deviceId);
                    metrics.reportCommand(
                            Direction.RESPONSE,
                            tenantId,
                            tenantTracker.result(),
                            ProcessingOutcome.FORWARDED,
                            payloadSize,
                            ctx.getTimer());
                    // check that the remote MQTT client is still connected before sending PUBACK
                    if (ctx.isAtLeastOnce() && ctx.deviceEndpoint().isConnected()) {
                        currentSpan.log("sending PUBACK");
                        ctx.acknowledge();
                    }
                    currentSpan.finish();
                    return Future.<Void> succeededFuture();
                })
                .recover(t -> {
                    TracingHelper.logError(currentSpan, t);
                    currentSpan.finish();
                    metrics.reportCommand(
                            Direction.RESPONSE,
                            tenantId,
                            tenantTracker.result(),
                            ProcessingOutcome.from(t),
                            payloadSize,
                            ctx.getTimer());
                    return Future.failedFuture(t);
                });
    }

    private Future<Void> uploadMessage(
            final MqttContext ctx,
            final TenantObject tenantObject,
            final String deviceId,
            final Buffer payload,
            final MetricsTags.EndpointType endpoint) {

        if (!isPayloadOfIndicatedType(payload, ctx.contentType())) {
            return Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST,
                    String.format("Content-Type %s does not match payload", ctx.contentType())));
        }

        final Span currentSpan = TracingHelper.buildChildSpan(tracer, ctx.getTracingContext(),
                "upload " + endpoint, getTypeName())
                .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_CLIENT)
                .withTag(TracingHelper.TAG_TENANT_ID, tenantObject.getTenantId())
                .withTag(TracingHelper.TAG_DEVICE_ID, deviceId)
                .withTag(TracingHelper.TAG_AUTHENTICATED.getKey(), ctx.authenticatedDevice() != null)
                .start();

        final Future<RegistrationAssertion> tokenTracker = getRegistrationAssertion(
                tenantObject.getTenantId(),
                deviceId,
                ctx.authenticatedDevice(),
                currentSpan.context());
        final Future<TenantObject> tenantValidationTracker = CompositeFuture.all(
                isAdapterEnabled(tenantObject),
                checkMessageLimit(tenantObject, payload.length(), currentSpan.context()))
            .map(tenantObject);

        return CompositeFuture.all(tokenTracker, tenantValidationTracker).compose(ok -> {

            final Map<String, Object> props = getDownstreamMessageProperties(ctx);
            props.put(MessageHelper.APP_PROPERTY_QOS, ctx.getRequestedQos().ordinal());
            addRetainAnnotation(ctx, props, currentSpan);
            customizeDownstreamMessageProperties(props, ctx);

            if (endpoint == EndpointType.EVENT) {
                return getEventSender(tenantObject).sendEvent(
                        tenantObject,
                        tokenTracker.result(),
                        ctx.contentType(),
                        payload,
                        props,
                        currentSpan.context());
            } else {
                return getTelemetrySender(tenantObject).sendTelemetry(
                        tenantObject,
                        tokenTracker.result(),
                        ctx.getRequestedQos(),
                        ctx.contentType(),
                        payload,
                        props,
                        currentSpan.context());
            }
        }).map(ok -> {

            log.trace("successfully processed message [topic: {}, QoS: {}] from device [tenantId: {}, deviceId: {}]",
                    ctx.getOrigAddress(), ctx.qosLevel(), tenantObject.getTenantId(), deviceId);
            // check that the remote MQTT client is still connected before sending PUBACK
            if (ctx.isAtLeastOnce() && ctx.deviceEndpoint().isConnected()) {
                currentSpan.log("sending PUBACK");
                ctx.acknowledge();
            }
            currentSpan.finish();
            return ok;

        }).recover(t -> {

            if (ClientErrorException.class.isInstance(t)) {
                final ClientErrorException e = (ClientErrorException) t;
                log.debug("cannot process message [endpoint: {}] from device [tenantId: {}, deviceId: {}]: {} - {}",
                        endpoint, tenantObject.getTenantId(), deviceId, e.getErrorCode(), e.getMessage());
            } else {
                log.debug("cannot process message [endpoint: {}] from device [tenantId: {}, deviceId: {}]",
                        endpoint, tenantObject.getTenantId(), deviceId, t);
            }
            TracingHelper.logError(currentSpan, t);
            currentSpan.finish();
            return Future.failedFuture(t);
        });
    }

    /**
     * Performs clean-up operations concerning the given endpoint before it gets closed.
     *
     * @param endpoint The endpoint that is about to be closed.
     */
    public final void onBeforeEndpointClose(final MqttDeviceEndpoint<T> endpoint) {
        if (endpoint.getAuthenticatedDevice() != null) {
            connectedAuthenticatedDeviceEndpoints.remove(endpoint);
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
     * @param ctx The context in which the MQTT message has been published. The
     *            {@link MqttContext#topic()} method will return a non-null resource identifier
     *            for the topic that the message has been published to.
     * @return A future indicating the outcome of the operation.
     *         <p>
     *         The future will succeed if the message has been successfully uploaded. Otherwise, the future will fail
     *         with a {@link ServiceInvocationException}.
     */
    protected abstract Future<Void> onPublishedMessage(MqttContext ctx);

    /**
     * Invoked before the message is sent to the downstream peer.
     * <p>
     * Subclasses may override this method in order to customize the
     * properties used for sending the message, e.g. adding custom properties.
     *
     * @param messageProperties The properties that are being added to the
     *                          downstream message.
     * @param ctx The message processing context.
     */
    protected void customizeDownstreamMessageProperties(final Map<String, Object> messageProperties, final MqttContext ctx) {
        // this default implementation does nothing
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

    private static void addRetainAnnotation(
            final MqttContext context,
            final Map<String, Object> props,
            final Span currentSpan) {

        if (context.isRetain()) {
            currentSpan.log("device wants to retain message");
            props.put(MessageHelper.ANNOTATION_X_OPT_RETAIN, Boolean.TRUE);
        }
    }

    final MqttDeviceEndpoint<T> createMqttDeviceEndpoint(final MqttEndpoint endpoint, final Device authenticatedDevice,
            final OptionalInt traceSamplingPriority) {
        final MqttDeviceEndpoint<T> mqttDeviceEndpoint = new MqttDeviceEndpoint<>(this, endpoint, authenticatedDevice,
                traceSamplingPriority);
        if (authenticatedDevice != null) {
            connectedAuthenticatedDeviceEndpoints.add(mqttDeviceEndpoint);
        }
        return mqttDeviceEndpoint;
    }

    private static MqttConnectReturnCode getConnectReturnCode(final Throwable e) {

        if (e instanceof MqttConnectionException) {
            return ((MqttConnectionException) e).code();
        } else if (e instanceof AuthorizationException) {
            if (e instanceof AdapterConnectionsExceededException) {
                return MqttConnectReturnCode.CONNECTION_REFUSED_SERVER_UNAVAILABLE;
            } else {
                return MqttConnectReturnCode.CONNECTION_REFUSED_NOT_AUTHORIZED;
            }
        } else if (e instanceof ServiceInvocationException) {
            switch (((ServiceInvocationException) e).getErrorCode()) {
            case HttpURLConnection.HTTP_UNAUTHORIZED:
            case HttpURLConnection.HTTP_NOT_FOUND:
                return MqttConnectReturnCode.CONNECTION_REFUSED_BAD_USER_NAME_OR_PASSWORD;
            case HttpURLConnection.HTTP_UNAVAILABLE:
                return MqttConnectReturnCode.CONNECTION_REFUSED_SERVER_UNAVAILABLE;
            default:
                return MqttConnectReturnCode.CONNECTION_REFUSED_NOT_AUTHORIZED;
            }
        } else {
            return MqttConnectReturnCode.CONNECTION_REFUSED_NOT_AUTHORIZED;
        }
    }

    /**
     * Get the buffer to send as a command to the gateway/device.
     * <p>
     * Subclasses should override this method in order to overwrite the provided command.
     *
     * @param ctx The command context for this command.
     * @return A future containing the mapped buffer.
     */
    protected Future<Buffer> getCommandPayload(final CommandContext ctx) {
        return Future.succeededFuture(ctx.getCommand().getPayload());
    }
}
