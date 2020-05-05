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

package org.eclipse.hono.adapter.mqtt;

import java.net.HttpURLConnection;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.auth.Device;
import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.Command;
import org.eclipse.hono.client.CommandContext;
import org.eclipse.hono.client.CommandResponse;
import org.eclipse.hono.client.DownstreamSender;
import org.eclipse.hono.client.MessageConsumer;
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.service.AbstractProtocolAdapterBase;
import org.eclipse.hono.service.auth.DeviceUser;
import org.eclipse.hono.service.auth.device.AuthHandler;
import org.eclipse.hono.service.auth.device.ChainAuthHandler;
import org.eclipse.hono.service.auth.device.TenantServiceBasedX509Authentication;
import org.eclipse.hono.service.auth.device.UsernamePasswordAuthProvider;
import org.eclipse.hono.service.auth.device.UsernamePasswordCredentials;
import org.eclipse.hono.service.auth.device.X509AuthProvider;
import org.eclipse.hono.service.limiting.ConnectionLimitManager;
import org.eclipse.hono.service.limiting.DefaultConnectionLimitManager;
import org.eclipse.hono.service.limiting.MemoryBasedConnectionLimitStrategy;
import org.eclipse.hono.service.metric.MetricsTags;
import org.eclipse.hono.service.metric.MetricsTags.Direction;
import org.eclipse.hono.service.metric.MetricsTags.EndpointType;
import org.eclipse.hono.service.metric.MetricsTags.ProcessingOutcome;
import org.eclipse.hono.tracing.TenantTraceSamplingHelper;
import org.eclipse.hono.tracing.TracingHelper;
import org.eclipse.hono.util.CommandConstants;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.ExecutionContextTenantAndAuthIdProvider;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.ResourceIdentifier;
import org.eclipse.hono.util.TenantObject;
import org.springframework.beans.factory.annotation.Autowired;

import io.micrometer.core.instrument.Timer.Sample;
import io.netty.handler.codec.mqtt.MqttConnectReturnCode;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.log.Fields;
import io.opentracing.tag.Tags;
import io.vertx.core.AsyncResult;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.mqtt.MqttConnectionException;
import io.vertx.mqtt.MqttEndpoint;
import io.vertx.mqtt.MqttServer;
import io.vertx.mqtt.MqttServerOptions;
import io.vertx.mqtt.MqttTopicSubscription;
import io.vertx.mqtt.messages.MqttPublishMessage;
import io.vertx.mqtt.messages.MqttSubscribeMessage;
import io.vertx.mqtt.messages.MqttUnsubscribeMessage;

/**
 * A base class for implementing Vert.x based Hono protocol adapters for publishing events &amp; telemetry data using
 * MQTT.
 * 
 * @param <T> The type of configuration properties this adapter supports/requires.
 */
public abstract class AbstractVertxBasedMqttProtocolAdapter<T extends MqttProtocolAdapterProperties>
        extends AbstractProtocolAdapterBase<T> {

    /**
     * The minimum amount of memory that the adapter requires to run.
     */
    protected static final int MINIMAL_MEMORY = 100_000_000; // 100MB: minimal memory necessary for startup
    /**
     * The amount of memory required for each connection.
     */
    protected static final int MEMORY_PER_CONNECTION = 20_000; // 20KB: expected avg. memory consumption per connection

    private static final int IANA_MQTT_PORT = 1883;
    private static final int IANA_SECURE_MQTT_PORT = 8883;
    private static final String KEY_TOPIC_FILTER = "filter";

    private MqttAdapterMetrics metrics = MqttAdapterMetrics.NOOP;

    private MqttServer server;
    private MqttServer insecureServer;
    private AuthHandler<MqttContext> authHandler;
    private final Function<TenantObject, BiConsumer<CommandSubscription, CommandContext>> afterCommandPublished = tenantObject -> (
            subscription, commandContext) -> afterCommandPublished(tenantObject, subscription, commandContext);
    private ExecutionContextTenantAndAuthIdProvider<MqttContext> tenantObjectWithAuthIdProvider;

    /**
     * Sets the authentication handler to use for authenticating devices.
     * 
     * @param authHandler The handler to use.
     * @throws NullPointerException if handler is {@code null}.
     */
    public final void setAuthHandler(final AuthHandler<MqttContext> authHandler) {
        this.authHandler = Objects.requireNonNull(authHandler);
    }

    /**
     * Sets the provider that determines the tenant and auth-id associated with a request.
     *
     * @param tenantObjectWithAuthIdProvider the provider.
     * @throws NullPointerException if tenantObjectWithAuthIdProvider is {@code null}.
     */
    public void setTenantObjectWithAuthIdProvider(
            final ExecutionContextTenantAndAuthIdProvider<MqttContext> tenantObjectWithAuthIdProvider) {
        this.tenantObjectWithAuthIdProvider = Objects.requireNonNull(tenantObjectWithAuthIdProvider);
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
     * Gets the metrics for this service.
     *
     * @return The metrics
     */
    protected final MqttAdapterMetrics getMetrics() {
        return metrics;
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
    protected AuthHandler<MqttContext> createAuthHandler() {

        return new ChainAuthHandler<MqttContext>()
                .append(new X509AuthHandler(
                        new TenantServiceBasedX509Authentication(getTenantClientFactory(), tracer),
                        new X509AuthProvider(getCredentialsClientFactory(), getConfig(), tracer)))
                .append(new ConnectPacketAuthHandler(
                        new UsernamePasswordAuthProvider(
                                getCredentialsClientFactory(),
                                getConfig(),
                                tracer), tracer));
    }

    /**
     * Creates the provider that determines the tenant and auth-id associated with a request.
     * <p>
     * This default implementation creates a {@link MqttContextTenantAndAuthIdProvider}.
     * <p>
     * Subclasses may override this method in order to return a different implementation.
     *
     * @return The provider.
     */
    protected ExecutionContextTenantAndAuthIdProvider<MqttContext> createTenantAndAuthIdProvider() {
        return new MqttContextTenantAndAuthIdProvider(getConfig(), getTenantClientFactory());
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

    /**
     * {@inheritDoc}
     */
    @Override
    protected final void doStart(final Promise<Void> startPromise) {

        log.info("limiting size of inbound message payload to {} bytes", getConfig().getMaxPayloadSize());
        if (!getConfig().isAuthenticationRequired()) {
            log.warn("authentication of devices turned off");
        }

        final ConnectionLimitManager connectionLimitManager = Optional.ofNullable(
                getConnectionLimitManager()).orElse(createConnectionLimitManager());
        setConnectionLimitManager(connectionLimitManager);

        checkPortConfiguration()
            .compose(ok -> CompositeFuture.all(bindSecureMqttServer(), bindInsecureMqttServer()))
            .compose(ok -> {
                if (authHandler == null) {
                    authHandler = createAuthHandler();
                }
                if (tenantObjectWithAuthIdProvider == null) {
                    tenantObjectWithAuthIdProvider = createTenantAndAuthIdProvider();
                }
                return Future.succeededFuture((Void) null);
            })
            .setHandler(startPromise);
    }

    private ConnectionLimitManager createConnectionLimitManager() {
        return new DefaultConnectionLimitManager(
                new MemoryBasedConnectionLimitStrategy(MINIMAL_MEMORY, MEMORY_PER_CONNECTION),
                () -> metrics.getNumberOfConnections(), getConfig());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected final void doStop(final Promise<Void> stopPromise) {

        final Promise<Void> serverTracker = Promise.promise();
        if (this.server != null) {
            this.server.close(serverTracker);
        } else {
            serverTracker.complete();
        }

        final Promise<Void> insecureServerTracker = Promise.promise();
        if (this.insecureServer != null) {
            this.insecureServer.close(insecureServerTracker);
        } else {
            insecureServerTracker.complete();
        }

        CompositeFuture.all(serverTracker.future(), insecureServerTracker.future())
            .map(ok -> (Void) null)
            .setHandler(stopPromise);
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

        isConnected()
                .compose(v -> handleConnectionRequest(endpoint, span))
                .setHandler(result -> handleConnectionRequestResult(endpoint, span, result));

    }

    private Future<Device> handleConnectionRequest(final MqttEndpoint endpoint, final Span currentSpan) {

        // the ConnectionLimitManager is null in some unit tests
        if (getConnectionLimitManager() != null && getConnectionLimitManager().isLimitExceeded()) {
            currentSpan.log("connection limit exceeded, reject connection request");
            return Future.failedFuture(new MqttConnectionException(
                    MqttConnectReturnCode.CONNECTION_REFUSED_SERVER_UNAVAILABLE));
        }

        if (getConfig().isAuthenticationRequired()) {
            return handleEndpointConnectionWithAuthentication(endpoint, currentSpan);
        } else {
            return handleEndpointConnectionWithoutAuthentication(endpoint);
        }
    }

    private void handleConnectionRequestResult(final MqttEndpoint endpoint,
            final Span currentSpan,
            final AsyncResult<Device> authenticationAttempt) {

        if (authenticationAttempt.succeeded()) {

            final Device authenticatedDevice = authenticationAttempt.result();
            TracingHelper.TAG_AUTHENTICATED.set(currentSpan, authenticatedDevice != null);

            sendConnectedEvent(endpoint.clientIdentifier(), authenticatedDevice)
                    .setHandler(sendAttempt -> {
                        if (sendAttempt.succeeded()) {
                            // we NEVER maintain session state
                            endpoint.accept(false);
                            if (authenticatedDevice != null) {
                                currentSpan.setTag(MessageHelper.APP_PROPERTY_TENANT_ID, authenticationAttempt.result().getTenantId());
                                currentSpan.setTag(MessageHelper.APP_PROPERTY_DEVICE_ID, authenticationAttempt.result().getDeviceId());
                            }
                            currentSpan.log("connection accepted");
                        } else {
                            log.warn(
                                    "connection request from client [clientId: {}] rejected due to connection event "
                                            + "failure: {}",
                                    endpoint.clientIdentifier(),
                                    MqttConnectReturnCode.CONNECTION_REFUSED_SERVER_UNAVAILABLE,
                                    sendAttempt.cause());
                            endpoint.reject(MqttConnectReturnCode.CONNECTION_REFUSED_SERVER_UNAVAILABLE);
                            TracingHelper.logError(currentSpan, sendAttempt.cause());
                        }
                    });

        } else {

            final Throwable t = authenticationAttempt.cause();
            TracingHelper.TAG_AUTHENTICATED.set(currentSpan, false);
            log.debug("connection request from client [clientId: {}] rejected due to {} ",
                    endpoint.clientIdentifier(), t.getMessage());

            final MqttConnectReturnCode code = getConnectReturnCode(t);
            endpoint.reject(code);
            currentSpan.log("connection rejected");
            TracingHelper.logError(currentSpan, authenticationAttempt.cause());
        }
        currentSpan.finish();
    }

    /**
     * Invoked when a client sends its <em>CONNECT</em> packet and client authentication has been disabled by setting
     * the {@linkplain MqttProtocolAdapterProperties#isAuthenticationRequired() authentication required} configuration
     * property to {@code false}.
     * <p>
     * Registers a close handler on the endpoint which invokes {@link #close(MqttEndpoint, Device, CommandHandler, OptionalInt)}. Registers a publish
     * handler on the endpoint which invokes {@link #onPublishedMessage(MqttContext)} for each message being published
     * by the client. Accepts the connection request.
     * 
     * @param endpoint The MQTT endpoint representing the client.
     */
    private Future<Device> handleEndpointConnectionWithoutAuthentication(final MqttEndpoint endpoint) {
        registerHandlers(endpoint, null, OptionalInt.empty());
        log.debug("unauthenticated device [clientId: {}] connected", endpoint.clientIdentifier());
        return Future.succeededFuture();
    }

    private Future<Device> handleEndpointConnectionWithAuthentication(final MqttEndpoint endpoint,
            final Span currentSpan) {

        final MqttContext context = MqttContext.fromConnectPacket(endpoint);
        final Future<OptionalInt> traceSamplingPriority = applyTenantTraceSamplingPriority(context, currentSpan);
        final Future<DeviceUser> authAttempt = traceSamplingPriority.compose(v -> {
            context.setTracingContext(currentSpan.context());
            return authenticate(context);
        });
        return authAttempt
                .compose(authenticatedDevice -> CompositeFuture.all(
                        getTenantConfiguration(authenticatedDevice.getTenantId(), currentSpan.context())
                            .compose(tenantObj -> CompositeFuture.all(
                                    isAdapterEnabled(tenantObj),
                                    checkConnectionLimit(tenantObj, currentSpan.context()))),
                        checkDeviceRegistration(authenticatedDevice, currentSpan.context()))
                        .map(ok -> authenticatedDevice))
                .compose(authenticatedDevice -> createLinks(authenticatedDevice, currentSpan))
                .compose(authenticatedDevice -> registerHandlers(endpoint, authenticatedDevice, traceSamplingPriority.result()))
                .recover(t -> {
                    if (authAttempt.failed()) {
                        log.debug("could not authenticate device", t);
                    } else {
                        log.debug("cannot establish connection with device [tenant-id: {}, device-id: {}]",
                                authAttempt.result().getTenantId(), authAttempt.result().getDeviceId(), t);
                    }
                    return Future.failedFuture(t);
                });
    }

    /**
     * Applies the trace sampling priority configured for the tenant derived from the given context to the given span.
     *
     * @param context The execution context.
     * @param currentSpan The span to apply the configuration to.
     * @return A succeeded future indicating the outcome of the operation. Its value will be an <em>OptionalInt</em>
     *         with the applied sampling priority or an empty <em>OptionalInt</em> if no priority was applied.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    protected final Future<OptionalInt> applyTenantTraceSamplingPriority(final MqttContext context, final Span currentSpan) {
        Objects.requireNonNull(context);
        Objects.requireNonNull(currentSpan);
        return tenantObjectWithAuthIdProvider.get(context, currentSpan.context())
                .map(tenantObjectWithAuthId -> TenantTraceSamplingHelper
                        .applyTraceSamplingPriority(tenantObjectWithAuthId, currentSpan))
                .recover(t -> {
                    return Future.succeededFuture(OptionalInt.empty());
                });
    }

    private Future<DeviceUser> authenticate(final MqttContext connectContext) {

        return authHandler.authenticateDevice(connectContext);
    }

    /**
     * Invoked when a device sends an MQTT <em>SUBSCRIBE</em> packet.
     * <p>
     * This method currently only supports topic filters for subscribing to
     * commands as defined by Hono's
     * <a href="https://www.eclipse.org/hono/docs/user-guide/mqtt-adapter/#command-control">
     * MQTT adapter user guide</a>.
     * <p>
     * When a device subscribes to a command topic filter, this method opens a
     * command consumer for receiving commands from applications for the device and
     * sends an empty notification downstream, indicating that the device will be
     * ready to receive commands until further notice.
     *
     * @param endpoint The endpoint representing the connection to the device.
     * @param authenticatedDevice The authenticated identity of the device or {@code null}
     *                            if the device has not been authenticated.
     * @param subscribeMsg The subscribe request received from the device.
     * @param cmdHandler The commandHandler to track command subscriptions, unsubscriptions and handle PUBACKs.
     * @param traceSamplingPriority The sampling priority to be applied on the <em>OpenTracing</em> span
     *                              created for this operation.
     * @throws NullPointerException if any of the parameters except authenticatedDevice is {@code null}.
     */
    @SuppressWarnings("rawtypes")
    protected final void onSubscribe(
            final MqttEndpoint endpoint,
            final Device authenticatedDevice,
            final MqttSubscribeMessage subscribeMsg,
            final CommandHandler<T> cmdHandler,
            final OptionalInt traceSamplingPriority) {

        Objects.requireNonNull(endpoint);
        Objects.requireNonNull(subscribeMsg);
        Objects.requireNonNull(cmdHandler);
        Objects.requireNonNull(traceSamplingPriority);

        final Map<String, Future> topicFilters = new HashMap<>();
        final Map<MqttTopicSubscription, Future> subscriptionOutcome = new LinkedHashMap<>(
                subscribeMsg.topicSubscriptions().size());

        final Span span = newSpan("SUBSCRIBE", endpoint, authenticatedDevice, traceSamplingPriority);

        subscribeMsg.topicSubscriptions().forEach(subscription -> {

            Future result = topicFilters.get(subscription.topicName());
            if (result != null) {

                // according to the MQTT 3.1.1 spec (section 3.8.4) we need to
                // make sure that we process multiple filters as if they had been
                // submitted using multiple separate SUBSCRIBE packets
                // we therefore always return the same result for duplicate topic filters
                subscriptionOutcome.put(subscription, result);

            } else {

                // we currently only support subscriptions for receiving commands
                final CommandSubscription cmdSub = CommandSubscription.fromTopic(subscription, authenticatedDevice,
                        endpoint.clientIdentifier());
                if (cmdSub == null) {
                    span.log(String.format("ignoring unsupported topic filter [%s]", subscription.topicName()));
                    log.debug("cannot create subscription [filter: {}, requested QoS: {}]: unsupported topic filter",
                            subscription.topicName(), subscription.qualityOfService());
                    result = Future.failedFuture(new IllegalArgumentException("unsupported topic filter"));
                } else if (MqttQoS.EXACTLY_ONCE.equals(subscription.qualityOfService())) {
                    // we do not support subscribing to commands using QoS 2
                    result = Future.failedFuture(new IllegalArgumentException("QoS 2 not supported for command subscription"));
                } else {
                    result = createCommandConsumer(endpoint, cmdSub, cmdHandler, span).map(consumer -> {
                        final Map<String, Object> items = new HashMap<>(4);
                        items.put(Fields.EVENT, "accepting subscription");
                        items.put(KEY_TOPIC_FILTER, subscription.topicName());
                        items.put("requested QoS", subscription.qualityOfService());
                        items.put("granted QoS", subscription.qualityOfService());
                        span.log(items);
                        log.debug("created subscription [tenant: {}, device: {}, filter: {}, requested QoS: {}, granted QoS: {}]",
                                cmdSub.getTenant(), cmdSub.getDeviceId(), subscription.topicName(),
                                subscription.qualityOfService(), subscription.qualityOfService());
                        cmdHandler.addSubscription(cmdSub, consumer);
                        return cmdSub;
                    }).recover(t -> {
                        final Map<String, Object> items = new HashMap<>(4);
                        items.put(Fields.EVENT, Tags.ERROR.getKey());
                        items.put(KEY_TOPIC_FILTER, subscription.topicName());
                        items.put("requested QoS", subscription.qualityOfService());
                        items.put(Fields.MESSAGE, "rejecting subscription: " + t.getMessage());
                        TracingHelper.logError(span, items);
                        log.debug("cannot create subscription [tenant: {}, device: {}, filter: {}, requested QoS: {}]",
                                cmdSub.getTenant(), cmdSub.getDeviceId(), subscription.topicName(),
                                subscription.qualityOfService(), t);
                        return Future.failedFuture(t);
                    });
                }
                topicFilters.put(subscription.topicName(), result);
                subscriptionOutcome.put(subscription, result);
            }
        });

        // wait for all futures to complete before sending SUBACK
        CompositeFuture.join(new ArrayList<>(subscriptionOutcome.values())).setHandler(v -> {

            // return a status code for each topic filter contained in the SUBSCRIBE packet
            final List<MqttQoS> grantedQosLevels = subscriptionOutcome.entrySet().stream()
                    .map(subscriptionOutcomeEntry -> subscriptionOutcomeEntry.getValue().failed() ? MqttQoS.FAILURE
                            : subscriptionOutcomeEntry.getKey().qualityOfService())
                    .collect(Collectors.toList());

            if (endpoint.isConnected()) {
                endpoint.subscribeAcknowledge(subscribeMsg.messageId(), grantedQosLevels);
            }

            // now that we have informed the device about the outcome
            // we can send empty notifications for succeeded command subscriptions
            // downstream
            topicFilters.values().forEach(f -> {
                if (f.succeeded() && f.result() instanceof CommandSubscription) {
                    final CommandSubscription s = (CommandSubscription) f.result();
                    sendConnectedTtdEvent(s.getTenant(), s.getDeviceId(), authenticatedDevice, span.context());
                }
            });
            span.finish();
        });
    }

    private Span newSpan(final String operationName, final MqttEndpoint endpoint, final Device authenticatedDevice,
            final OptionalInt traceSamplingPriority) {
        final Span span = tracer.buildSpan(operationName)
                .ignoreActiveSpan()
                .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_SERVER)
                .withTag(Tags.COMPONENT.getKey(), getTypeName())
                .withTag(TracingHelper.TAG_CLIENT_ID.getKey(), endpoint.clientIdentifier())
                .withTag(TracingHelper.TAG_AUTHENTICATED.getKey(), authenticatedDevice != null)
                .start();

        if (authenticatedDevice != null) {
            span.setTag(MessageHelper.APP_PROPERTY_TENANT_ID, authenticatedDevice.getTenantId());
            span.setTag(MessageHelper.APP_PROPERTY_DEVICE_ID, authenticatedDevice.getDeviceId());
        }
        traceSamplingPriority.ifPresent(prio -> {
            TracingHelper.setTraceSamplingPriority(span, prio);
        });
        return span;
    }

    /**
     * Invoked when a device sends an MQTT <em>UNSUBSCRIBE</em> packet.
     * <p>
     * This method currently only supports topic filters for unsubscribing from
     * commands as defined by Hono's
     * <a href="https://www.eclipse.org/hono/docs/user-guide/mqtt-adapter/#command-control">
     * MQTT adapter user guide</a>.
     *
     * @param endpoint The endpoint representing the connection to the device.
     * @param authenticatedDevice The authenticated identity of the device or {@code null}
     *                            if the device has not been authenticated.
     * @param unsubscribeMsg The unsubscribe request received from the device.
     * @param cmdHandler The commandHandler to track command subscriptions, unsubscriptions and handle PUBACKs.
     * @param traceSamplingPriority The sampling priority to be applied on the <em>OpenTracing</em> span
     *                              created for this operation.
     * @throws NullPointerException if any of the parameters except authenticatedDevice is {@code null}.
     */
    protected final void onUnsubscribe(
            final MqttEndpoint endpoint,
            final Device authenticatedDevice,
            final MqttUnsubscribeMessage unsubscribeMsg,
            final CommandHandler<T> cmdHandler,
            final OptionalInt traceSamplingPriority) {

        Objects.requireNonNull(endpoint);
        Objects.requireNonNull(unsubscribeMsg);
        Objects.requireNonNull(cmdHandler);
        Objects.requireNonNull(traceSamplingPriority);

        final Span span = newSpan("UNSUBSCRIBE", endpoint, authenticatedDevice, traceSamplingPriority);

        unsubscribeMsg.topics().forEach(topic -> {
            final CommandSubscription cmdSub = CommandSubscription.fromTopic(topic, authenticatedDevice);
            if (cmdSub == null) {
                final Map<String, Object> items = new HashMap<>(2);
                items.put(Fields.EVENT, "ignoring unsupported topic filter");
                items.put(KEY_TOPIC_FILTER, topic);
                span.log(items);
                log.debug("ignoring unsubscribe request for unsupported topic filter [{}]", topic);
            } else {
                final String tenantId = cmdSub.getTenant();
                final String deviceId = cmdSub.getDeviceId();
                final Map<String, Object> items = new HashMap<>(2);
                items.put(Fields.EVENT, "unsubscribing device from topic");
                items.put(KEY_TOPIC_FILTER, topic);
                span.log(items);
                log.debug("unsubscribing device [tenant-id: {}, device-id: {}] from topic [{}]",
                        tenantId, deviceId, topic);
                cmdHandler.removeSubscription(topic, (tenant, device) -> {
                    final Span closeHandlerSpan = newSpan("Send Disconnected Event", endpoint, authenticatedDevice,
                            traceSamplingPriority);
                    sendDisconnectedTtdEvent(tenant, device, authenticatedDevice, span.context())
                            .setHandler(sendAttempt -> closeHandlerSpan.finish());
                });
            }
        });
        if (endpoint.isConnected()) {
            endpoint.unsubscribeAcknowledge(unsubscribeMsg.messageId());
        }
        span.finish();
    }

    private Future<MessageConsumer> createCommandConsumer(final MqttEndpoint mqttEndpoint,
            final CommandSubscription sub, final CommandHandler<T> cmdHandler, final Span span) {

        final Handler<CommandContext> commandHandler = commandContext -> {

            Tags.COMPONENT.set(commandContext.getCurrentSpan(), getTypeName());
            final Sample timer = metrics.startTimer();
            final Command command = commandContext.getCommand();
            final Future<TenantObject> tenantTracker = getTenantConfiguration(sub.getTenant(),
                    commandContext.getTracingContext());

            tenantTracker.compose(tenantObject -> {
                if (!command.isValid()) {
                    return Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST,
                            "malformed command message"));
                }
                return checkMessageLimit(tenantObject, command.getPayloadSize(), commandContext.getTracingContext());
            }).compose(success -> {
                addMicrometerSample(commandContext, timer);
                onCommandReceived(tenantTracker.result(), mqttEndpoint, sub, commandContext, cmdHandler);
                return Future.succeededFuture();
            }).otherwise(failure -> {
                if (failure instanceof ClientErrorException) {
                    commandContext.reject(getErrorCondition(failure));
                } else {
                    commandContext.release();
                }
                metrics.reportCommand(
                        command.isOneWay() ? Direction.ONE_WAY : Direction.REQUEST,
                        sub.getTenant(),
                        tenantTracker.result(),
                        ProcessingOutcome.from(failure),
                        command.getPayloadSize(),
                        timer);
                return null;
            });
        };
        if (sub.isGatewaySubscriptionForSpecificDevice()) {
            // gateway scenario
            return getCommandConsumerFactory().createCommandConsumer(sub.getTenant(), sub.getDeviceId(),
                    sub.getAuthenticatedDeviceId(), commandHandler, null, span.context());
        } else {
            return getCommandConsumerFactory().createCommandConsumer(sub.getTenant(), sub.getDeviceId(), commandHandler,
                    null, span.context());
        }
    }

    void handlePublishedMessage(final MqttContext context) {
        // try to extract a SpanContext from the property bag of the message's topic (if set)
        final SpanContext spanContext = Optional.ofNullable(context.propertyBag())
                .map(propertyBag -> TracingHelper.extractSpanContext(tracer, propertyBag::getPropertiesIterator))
                .orElse(null);
        final MqttQoS qos = context.message().qosLevel();
        final Span span = TracingHelper.buildChildSpan(tracer, spanContext, "PUBLISH", getTypeName())
            .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_SERVER)
            .withTag(Tags.MESSAGE_BUS_DESTINATION.getKey(), context.message().topicName())
            .withTag(TracingHelper.TAG_QOS.getKey(), qos.toString())
            .withTag(TracingHelper.TAG_CLIENT_ID.getKey(), context.deviceEndpoint().clientIdentifier())
            .start();
        context.setTimer(getMetrics().startTimer());

        applyTenantTraceSamplingPriority(context, span)
                .compose(v -> {
                    context.setTracingContext(span.context());
                    return checkTopic(context);
                })
                .compose(ok -> onPublishedMessage(context))
                .setHandler(processing -> {
                    if (processing.succeeded()) {
                        Tags.HTTP_STATUS.set(span, HttpURLConnection.HTTP_ACCEPTED);
                        onMessageSent(context);
                    } else {
                        if (processing.cause() instanceof ServiceInvocationException) {
                            final ServiceInvocationException sie = (ServiceInvocationException) processing.cause();
                            Tags.HTTP_STATUS.set(span, sie.getErrorCode());
                        } else {
                            Tags.HTTP_STATUS.set(span, HttpURLConnection.HTTP_INTERNAL_ERROR);
                        }
                        if (processing.cause() instanceof ClientErrorException) {
                            // nothing to do
                        } else {
                            onMessageUndeliverable(context);
                        }
                        TracingHelper.logError(span, processing.cause());
                        if (context.deviceEndpoint().isConnected()) {
                            span.log("closing connection to device");
                            context.deviceEndpoint().close();
                        }
                    }
                    span.finish();
                });
    }

    private Future<Void> checkTopic(final MqttContext context) {
        if (context.topic() == null) {
            return Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST, "malformed topic name"));
        } else {
            return Future.succeededFuture();
        }
    }

    /**
     * Forwards a message to the AMQP Messaging Network.
     * 
     * @param ctx The context in which the MQTT message has been published.
     * @param resource The resource that the message should be forwarded to.
     * @param message The message to send.
     * @return A future indicating the outcome of the operation.
     *         <p>
     *         The future will succeed if the message has been forwarded successfully.
     *         Otherwise the future will fail with a {@link ServiceInvocationException}.
     * @throws NullPointerException if any of context, resource or payload is {@code null}.
     * @throws IllegalArgumentException if the payload is empty.
     */
    public final Future<Void> uploadMessage(
            final MqttContext ctx,
            final ResourceIdentifier resource,
            final MqttPublishMessage message) {

        Objects.requireNonNull(ctx);
        Objects.requireNonNull(resource);
        Objects.requireNonNull(message);

        switch (MetricsTags.EndpointType.fromString(resource.getEndpoint())) {
        case TELEMETRY:
            return uploadTelemetryMessage(
                    ctx,
                    resource.getTenantId(),
                    resource.getResourceId(),
                    message.payload());
        case EVENT:
            return uploadEventMessage(
                    ctx,
                    resource.getTenantId(),
                    resource.getResourceId(),
                    message.payload());
        case COMMAND:
            return uploadCommandResponseMessage(ctx, resource);
        default:
            return Future
                    .failedFuture(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST, "unsupported endpoint"));
        }

    }

    /**
     * Forwards a telemetry message to the AMQP Messaging Network.
     * 
     * @param ctx The context in which the MQTT message has been published.
     * @param tenant The tenant of the device that has produced the data.
     * @param deviceId The id of the device that has produced the data.
     * @param payload The message payload to send.
     * @return A future indicating the outcome of the operation.
     *         <p>
     *         The future will succeed if the message has been forwarded successfully.
     *         Otherwise the future will fail with a {@link ServiceInvocationException}.
     * @throws NullPointerException if any of context, tenant, device ID or payload is {@code null}.
     * @throws IllegalArgumentException if the context does not contain a
     *              telemetry message or if the payload is empty.
     */
    public final Future<Void> uploadTelemetryMessage(
            final MqttContext ctx,
            final String tenant,
            final String deviceId,
            final Buffer payload) {

        Objects.requireNonNull(ctx);
        Objects.requireNonNull(tenant);
        Objects.requireNonNull(deviceId);
        Objects.requireNonNull(payload);

        if (ctx.endpoint() != EndpointType.TELEMETRY) {
            throw new IllegalArgumentException("context does not contain telemetry message but " +
                ctx.endpoint().getCanonicalName());
        }

        final MetricsTags.QoS qos = MetricsTags.QoS.from(ctx.message().qosLevel().value());
        final Future<TenantObject> tenantTracker = getTenantConfiguration(tenant, ctx.getTracingContext());

        return tenantTracker
                .compose(tenantObject -> uploadMessage(ctx, tenantObject, deviceId, payload, getTelemetrySender(tenant),
                        ctx.endpoint()))
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
     * @param tenant The tenant of the device that has produced the data.
     * @param deviceId The id of the device that has produced the data.
     * @param payload The message payload to send.
     * @return A future indicating the outcome of the operation.
     *         <p>
     *         The future will succeed if the message has been forwarded successfully.
     *         Otherwise the future will fail with a {@link ServiceInvocationException}.
     * @throws NullPointerException if any of context, tenant, device ID or payload is {@code null}.
     * @throws IllegalArgumentException if the context does not contain an
     *              event or if the payload is empty.
     */
    public final Future<Void> uploadEventMessage(
            final MqttContext ctx,
            final String tenant,
            final String deviceId,
            final Buffer payload) {

        Objects.requireNonNull(ctx);
        Objects.requireNonNull(tenant);
        Objects.requireNonNull(deviceId);
        Objects.requireNonNull(payload);

        if (ctx.endpoint() != EndpointType.EVENT) {
            throw new IllegalArgumentException("context does not contain event but " +
                ctx.endpoint().getCanonicalName());
        }

        final MetricsTags.QoS qos = MetricsTags.QoS.from(ctx.message().qosLevel().value());
        final Future<TenantObject> tenantTracker = getTenantConfiguration(tenant, ctx.getTracingContext());

        return tenantTracker
                .compose(tenantObject -> uploadMessage(ctx, tenantObject, deviceId, payload, getEventSender(tenant),
                        ctx.endpoint()))
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
     * @param targetAddress The address that the response should be forwarded to.
     * @return A future indicating the outcome of the operation.
     *         <p>
     *         The future will succeed if the message has been uploaded successfully.
     *         Otherwise, the future will fail with a {@link ServiceInvocationException}.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    public final Future<Void> uploadCommandResponseMessage(
            final MqttContext ctx,
            final ResourceIdentifier targetAddress) {

        Objects.requireNonNull(ctx);
        Objects.requireNonNull(targetAddress);

        final String[] addressPath = targetAddress.getResourcePath();
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
                        addressPath[CommandConstants.TOPIC_POSITION_RESPONSE_STATUS], targetAddress.getTenantId(), targetAddress.getResourceId());
            }
            if (status != null) {
                reqId = addressPath[CommandConstants.TOPIC_POSITION_RESPONSE_REQ_ID];
                final CommandResponse commandResponse = CommandResponse.from(reqId, targetAddress.getTenantId(),
                        targetAddress.getResourceId(), ctx.message().payload(), ctx.contentType(), status);

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
                .withTag(MessageHelper.APP_PROPERTY_TENANT_ID, targetAddress.getTenantId())
                .withTag(MessageHelper.APP_PROPERTY_DEVICE_ID, targetAddress.getResourceId())
                .withTag(Constants.HEADER_COMMAND_RESPONSE_STATUS, status)
                .withTag(Constants.HEADER_COMMAND_REQUEST_ID, reqId)
                .withTag(TracingHelper.TAG_AUTHENTICATED.getKey(), ctx.authenticatedDevice() != null)
                .start();

            final int payloadSize = Optional.ofNullable(ctx.message().payload()).map(Buffer::length).orElse(0);
            final Future<JsonObject> tokenTracker = getRegistrationAssertion(targetAddress.getTenantId(),
                    targetAddress.getResourceId(), ctx.authenticatedDevice(), currentSpan.context());
            final Future<TenantObject> tenantTracker = getTenantConfiguration(targetAddress.getTenantId(), ctx.getTracingContext());
            final Future<TenantObject> tenantValidationTracker = CompositeFuture.all(
                                    isAdapterEnabled(tenantTracker.result()),
                                    checkMessageLimit(tenantTracker.result(), payloadSize, currentSpan.context()))
                                    .map(success -> tenantTracker.result());

        return CompositeFuture.all(tenantTracker, commandResponseTracker)
                .compose(success -> CompositeFuture.all(tokenTracker, tenantValidationTracker))
                .compose(ok -> sendCommandResponse(targetAddress.getTenantId(), commandResponseTracker.result(),
                        currentSpan.context()))
                .compose(delivery -> {
                    log.trace("successfully forwarded command response from device [tenant-id: {}, device-id: {}]",
                            targetAddress.getTenantId(), targetAddress.getResourceId());
                    metrics.reportCommand(
                            Direction.RESPONSE,
                            targetAddress.getTenantId(),
                            tenantTracker.result(),
                            ProcessingOutcome.FORWARDED,
                            payloadSize,
                            ctx.getTimer());
                    // check that the remote MQTT client is still connected before sending PUBACK
                    if (ctx.deviceEndpoint().isConnected() && ctx.message().qosLevel() == MqttQoS.AT_LEAST_ONCE) {
                        ctx.deviceEndpoint().publishAcknowledge(ctx.message().messageId());
                    }
                    currentSpan.finish();
                    return Future.<Void> succeededFuture();
                })
                .recover(t -> {
                    TracingHelper.logError(currentSpan, t);
                    currentSpan.finish();
                    metrics.reportCommand(
                            Direction.RESPONSE,
                            targetAddress.getTenantId(),
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
            final Future<DownstreamSender> senderTracker,
            final MetricsTags.EndpointType endpoint) {

        if (!isPayloadOfIndicatedType(payload, ctx.contentType())) {
            return Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST,
                    String.format("Content-Type %s does not match payload", ctx.contentType())));
        }

        final Span currentSpan = TracingHelper.buildChildSpan(tracer, ctx.getTracingContext(),
                "upload " + endpoint, getTypeName())
                .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_CLIENT)
                .withTag(MessageHelper.APP_PROPERTY_TENANT_ID, tenantObject.getTenantId())
                .withTag(MessageHelper.APP_PROPERTY_DEVICE_ID, deviceId)
                .withTag(TracingHelper.TAG_AUTHENTICATED.getKey(), ctx.authenticatedDevice() != null)
                .start();

        final Future<JsonObject> tokenTracker = getRegistrationAssertion(tenantObject.getTenantId(), deviceId,
                ctx.authenticatedDevice(), currentSpan.context());
        final Future<?> tenantValidationTracker = CompositeFuture.all(
                isAdapterEnabled(tenantObject),
                checkMessageLimit(tenantObject, payload.length(), currentSpan.context()));

        return CompositeFuture.all(tokenTracker, tenantValidationTracker, senderTracker).compose(ok -> {

            final DownstreamSender sender = senderTracker.result();
            final Message downstreamMessage = newMessage(
                    ResourceIdentifier.from(endpoint.getCanonicalName(), tenantObject.getTenantId(), deviceId),
                    ctx.message().topicName(),
                    ctx.contentType(),
                    payload,
                    tenantObject,
                    tokenTracker.result(),
                    null,
                    EndpointType.EVENT.equals(endpoint) ? getTimeToLive(ctx.propertyBag()) : null);

            addRetainAnnotation(ctx, downstreamMessage, currentSpan);
            customizeDownstreamMessage(downstreamMessage, ctx);

            if (ctx.isAtLeastOnce()) {
                return sender.sendAndWaitForOutcome(downstreamMessage, currentSpan.context());
            } else {
                return sender.send(downstreamMessage, currentSpan.context());
            }
        }).compose(delivery -> {

            log.trace("successfully processed message [topic: {}, QoS: {}] from device [tenantId: {}, deviceId: {}]",
                    ctx.message().topicName(), ctx.message().qosLevel(), tenantObject.getTenantId(), deviceId);
            // check that the remote MQTT client is still connected before sending PUBACK
            if (ctx.isAtLeastOnce() && ctx.deviceEndpoint().isConnected()) {
                currentSpan.log("sending PUBACK");
                ctx.acknowledge();
            }
            currentSpan.finish();
            return Future.<Void> succeededFuture();

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
     * Closes a connection to a client.
     * 
     * @param endpoint The connection to close.
     * @param authenticatedDevice Optional authenticated device information, may be {@code null}.
     * @param cmdHandler The commandHandler to track command subscriptions, unsubscriptions and handle PUBACKs.
     * @param traceSamplingPriority The sampling priority to be applied on the <em>OpenTracing</em> span
     *                              created for this operation.
     * @throws NullPointerException if any of the parameters except authenticatedDevice is {@code null}.
     */
    protected final void close(final MqttEndpoint endpoint, final Device authenticatedDevice,
            final CommandHandler<T> cmdHandler, final OptionalInt traceSamplingPriority) {

        Objects.requireNonNull(endpoint);
        Objects.requireNonNull(cmdHandler);
        Objects.requireNonNull(traceSamplingPriority);

        final Span span = newSpan("CLOSE", endpoint, authenticatedDevice, traceSamplingPriority);
        onClose(endpoint);
        cmdHandler.removeAllSubscriptions((tenant, device) -> {
            final Span closeHandlerSpan = newSpan("Send Disconnected Event", endpoint, authenticatedDevice,
                    traceSamplingPriority);
            sendDisconnectedTtdEvent(tenant, device, authenticatedDevice, span.context())
                    .setHandler(sendAttempt -> closeHandlerSpan.finish());
        });
        sendDisconnectedEvent(endpoint.clientIdentifier(), authenticatedDevice);
        if (authenticatedDevice == null) {
            log.debug("connection to anonymous device [clientId: {}] closed", endpoint.clientIdentifier());
            metrics.decrementUnauthenticatedConnections();
        } else {
            log.debug("connection to device [tenant-id: {}, device-id: {}] closed",
                    authenticatedDevice.getTenantId(), authenticatedDevice.getDeviceId());
            metrics.decrementConnections(authenticatedDevice.getTenantId());
        }
        if (endpoint.isConnected()) {
            log.debug("closing connection with client [client ID: {}]", endpoint.clientIdentifier());
            endpoint.close();
        } else {
            log.trace("client has already closed connection");
        }
        span.finish();
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
     * This default implementation returns a future with {@link UsernamePasswordCredentials} created from the
     * <em>username</em> and <em>password</em> fields of the <em>CONNECT</em> packet.
     * <p>
     * Subclasses should override this method if the device uses credentials that do not comply with the format expected
     * by {@link UsernamePasswordCredentials}.
     *
     * @param endpoint The MQTT endpoint representing the client.
     * @return A future indicating the outcome of the operation.
     *         The future will succeed with the client's credentials extracted from the CONNECT packet
     *         or it will fail with a {@link ServiceInvocationException} indicating the cause of the failure.
     */
    protected final Future<UsernamePasswordCredentials> getCredentials(final MqttEndpoint endpoint) {

        if (endpoint.auth() == null) {
            return Future.failedFuture(new ClientErrorException(
                    HttpURLConnection.HTTP_UNAUTHORIZED,
                    "device did not provide credentials in CONNECT packet"));
        }

        if (endpoint.auth().getUsername() == null || endpoint.auth().getPassword() == null) {
            return Future.failedFuture(new ClientErrorException(
                    HttpURLConnection.HTTP_UNAUTHORIZED,
                    "device provided malformed credentials in CONNECT packet"));
        }

        final UsernamePasswordCredentials credentials = UsernamePasswordCredentials
                .create(endpoint.auth().getUsername(), endpoint.auth().getPassword(), getConfig().isSingleTenant());

        if (credentials == null) {
            return Future.failedFuture(new ClientErrorException(
                    HttpURLConnection.HTTP_UNAUTHORIZED,
                    "device provided malformed credentials in CONNECT packet"));
        } else {
            return Future.succeededFuture(credentials);
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

    /**
     * Called for a command to be delivered to a device.
     *
     * @param tenantObject The tenant configuration object.
     * @param endpoint The device that the command should be delivered to.
     * @param subscription The device's command subscription.
     * @param commandContext The command to be delivered.
     * @param cmdHandler The commandHandler to track command subscriptions, unsubscriptions and handle PUBACKs.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    protected final void onCommandReceived(
            final TenantObject tenantObject,
            final MqttEndpoint endpoint,
            final CommandSubscription subscription,
            final CommandContext commandContext,
            final CommandHandler<T> cmdHandler) {

        Objects.requireNonNull(endpoint);
        Objects.requireNonNull(subscription);
        Objects.requireNonNull(commandContext);

        TracingHelper.TAG_CLIENT_ID.set(commandContext.getCurrentSpan(), endpoint.clientIdentifier());
        final Command command = commandContext.getCommand();

        // build topic string; examples:
        // command///req/xyz/light (authenticated device)
        // command///req//light (authenticated device, one-way)
        // command/DEFAULT_TENANT/4711/req/xyz/light (unauthenticated device)
        // command//4712/req/xyz/light (authenticated gateway)

        final String topicTenantId = subscription.isAuthenticated() ? "" : subscription.getTenant();
        final String topicDeviceId = command.isTargetedAtGateway() ? command.getOriginalDeviceId()
                : subscription.isAuthenticated() ? "" : subscription.getDeviceId();
        final String topicCommandRequestId = command.isOneWay() ? "" : command.getRequestId();

        final String topic = String.format("%s/%s/%s/%s/%s/%s",
                subscription.getEndpoint(), topicTenantId, topicDeviceId, subscription.getRequestPart(),
                topicCommandRequestId, command.getName());

        if (command.isTargetedAtGateway()) {
            log.debug("Publishing command to gateway [tenant-id: {}, gateway-id: {}, device-id: {}, MQTT client-id: {}, QoS: {}]",
                    subscription.getTenant(), subscription.getDeviceId(), command.getOriginalDeviceId(),
                    subscription.getClientId(), subscription.getQos());
        } else {
            log.debug("Publishing command to device [tenant-id: {}, device-id: {}, MQTT client-id: {}, QoS: {}]",
                    subscription.getTenant(), subscription.getDeviceId(), subscription.getClientId(),
                    subscription.getQos());
        }
        final Map<String, String> items = new HashMap<>(3);
        items.put(Fields.EVENT, "Publishing command to device");
        items.put(TracingHelper.TAG_CLIENT_ID.getKey(), subscription.getClientId());
        items.put(TracingHelper.TAG_QOS.getKey(), subscription.getQos().toString());
        commandContext.getCurrentSpan().log(items);

        endpoint.publish(topic, command.getPayload(), subscription.getQos(), false, false, sentHandler -> {
            if (sentHandler.succeeded()) {
                if (MqttQoS.AT_LEAST_ONCE.equals(subscription.getQos())) {
                    cmdHandler.addToWaitingForAcknowledgement(sentHandler.result(), tenantObject, subscription,
                            commandContext);
                } else {
                    afterCommandPublished(tenantObject, subscription, commandContext);
                }
            } else {
                log.debug(
                        "Error publishing command to device [tenant-id: {}, device-id: {}, MQTT client-id: {}, QoS: {}]",
                        subscription.getTenant(), subscription.getDeviceId(), endpoint.clientIdentifier(),
                        subscription.getQos(),
                        sentHandler.cause());
                TracingHelper.logError(commandContext.getCurrentSpan(), sentHandler.cause());
                metrics.reportCommand(
                        command.isOneWay() ? Direction.ONE_WAY : Direction.REQUEST,
                        subscription.getTenant(),
                        tenantObject,
                        ProcessingOutcome.from(sentHandler.cause()),
                        command.getPayloadSize(),
                        getMicrometerSample(commandContext));
                commandContext.release();
            }
        });
    }

    /**
     * Gets the <em>time-to-live</em> duration from the given property bag object.
     *
     * @param propertyBag The property bag object.
     * @return The <em>time-to-live</em> duration or {@code null} if
     * <ul>
     *     <li>the given property bag object is {@code null.}</li>
     *     <li>no property with id {@link org.eclipse.hono.util.Constants#HEADER_TIME_TO_LIVE} exists.</li>
     *     <li>the contained value cannot be parsed as a Long.</li>
     *     <li>the contained value is negative.</li>
     * </ul>
     */
    protected final Duration getTimeToLive(final PropertyBag propertyBag) {
        try {
            return Optional.ofNullable(propertyBag)
                    .map(propBag -> propBag.getProperty(Constants.HEADER_TIME_TO_LIVE))
                    .map(Long::parseLong)
                    .map(ttl -> ttl < 0 ? null : Duration.ofSeconds(ttl))
                    .orElse(null);
        } catch (final NumberFormatException e) {
            return null;
        }
    }

    private void afterCommandPublished(
            final TenantObject tenantObject,
            final CommandSubscription subscription,
            final CommandContext commandContext) {

        metrics.reportCommand(
                commandContext.getCommand().isOneWay() ? Direction.ONE_WAY : Direction.REQUEST,
                subscription.getTenant(),
                tenantObject,
                ProcessingOutcome.FORWARDED,
                commandContext.getCommand().getPayloadSize(),
                getMicrometerSample(commandContext));
        log.debug("Published command to device [tenant-id: {}, device-id: {}, MQTT client-id: {}, QoS: {}]",
                subscription.getTenant(), subscription.getDeviceId(), subscription.getClientId(),
                subscription.getQos());
        final Map<String, String> items = new HashMap<>(4);
        items.put(Fields.EVENT, "Published command to device");
        items.put(Tags.MESSAGE_BUS_DESTINATION.getKey(), subscription.getTopic());
        items.put(TracingHelper.TAG_CLIENT_ID.getKey(), subscription.getClientId());
        items.put(TracingHelper.TAG_QOS.getKey(), subscription.getQos().toString());
        commandContext.getCurrentSpan().log(items);
        commandContext.accept();
    }

    private static void addRetainAnnotation(final MqttContext context, final Message downstreamMessage,
            final Span currentSpan) {

        if (context.message().isRetain()) {
            currentSpan.log("device wants to retain message");
            MessageHelper.addAnnotation(downstreamMessage, MessageHelper.ANNOTATION_X_OPT_RETAIN, Boolean.TRUE);
        }
    }

    private Future<Device> createLinks(final Device authenticatedDevice, final Span currentSpan) {

        final Future<DownstreamSender> telemetrySender = getTelemetrySender(authenticatedDevice.getTenantId());
        final Future<DownstreamSender> eventSender = getEventSender(authenticatedDevice.getTenantId());

        return CompositeFuture
                .all(telemetrySender, eventSender)
                .compose(ok -> {
                    currentSpan.log("opened downstream links");
                    log.debug(
                            "providently opened downstream links [credit telemetry: {}, credit event: {}] for tenant [{}]",
                            telemetrySender.result().getCredit(), eventSender.result().getCredit(),
                            authenticatedDevice.getTenantId());
                    return Future.succeededFuture(authenticatedDevice);
                });
    }

    private Future<Device> registerHandlers(final MqttEndpoint endpoint, final Device authenticatedDevice,
            final OptionalInt traceSamplingPriority) {
        final CommandHandler<T> cmdHandler = new CommandHandler<>(vertx, getConfig());
        endpoint.closeHandler(v -> close(endpoint, authenticatedDevice, cmdHandler, traceSamplingPriority));
        endpoint.publishHandler(
                message -> handlePublishedMessage(MqttContext.fromPublishPacket(message, endpoint, authenticatedDevice)));
        endpoint.publishAcknowledgeHandler(msgId -> cmdHandler.handlePubAck(msgId, afterCommandPublished));
        endpoint.subscribeHandler(subscribeMsg -> onSubscribe(endpoint, authenticatedDevice, subscribeMsg, cmdHandler,
                traceSamplingPriority));
        endpoint.unsubscribeHandler(unsubscribeMsg -> onUnsubscribe(endpoint, authenticatedDevice, unsubscribeMsg,
                cmdHandler, traceSamplingPriority));
        if (authenticatedDevice == null) {
            metrics.incrementUnauthenticatedConnections();
        } else {
            metrics.incrementConnections(authenticatedDevice.getTenantId());
        }
        return Future.succeededFuture(authenticatedDevice);
    }

    private static MqttConnectReturnCode getConnectReturnCode(final Throwable e) {

        if (e instanceof MqttConnectionException) {
            return ((MqttConnectionException) e).code();
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
}
