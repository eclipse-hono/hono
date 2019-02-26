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

package org.eclipse.hono.adapter.mqtt;

import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

import org.apache.qpid.proton.amqp.transport.ErrorCondition;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.auth.Device;
import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.Command;
import org.eclipse.hono.client.CommandContext;
import org.eclipse.hono.client.CommandResponse;
import org.eclipse.hono.client.MessageConsumer;
import org.eclipse.hono.client.MessageSender;
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.config.ProtocolAdapterProperties;
import org.eclipse.hono.service.AbstractProtocolAdapterBase;
import org.eclipse.hono.service.auth.DeviceUser;
import org.eclipse.hono.service.auth.device.AuthHandler;
import org.eclipse.hono.service.auth.device.ChainAuthHandler;
import org.eclipse.hono.service.auth.device.TenantServiceBasedX509Authentication;
import org.eclipse.hono.service.auth.device.UsernamePasswordAuthProvider;
import org.eclipse.hono.service.auth.device.UsernamePasswordCredentials;
import org.eclipse.hono.service.auth.device.X509AuthProvider;
import org.eclipse.hono.service.metric.MetricsTags;
import org.eclipse.hono.service.metric.MetricsTags.Direction;
import org.eclipse.hono.service.metric.MetricsTags.EndpointType;
import org.eclipse.hono.service.metric.MetricsTags.ProcessingOutcome;
import org.eclipse.hono.tracing.TracingHelper;
import org.eclipse.hono.util.CommandConstants;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.ResourceIdentifier;
import org.eclipse.hono.util.TenantObject;
import org.springframework.beans.factory.annotation.Autowired;

import io.micrometer.core.instrument.Timer.Sample;
import io.netty.handler.codec.mqtt.MqttConnectReturnCode;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.opentracing.Span;
import io.opentracing.log.Fields;
import io.opentracing.tag.Tags;
import io.vertx.core.AsyncResult;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
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

    private MqttAdapterMetrics metrics = MqttAdapterMetrics.NOOP;

    private MqttServer server;
    private MqttServer insecureServer;
    private AuthHandler<MqttContext> authHandler;
    private final BiConsumer<CommandSubscription, CommandContext> afterCommandPubAckedConsumer = this::afterCommandPublished;
    private int connectionLimit = Integer.MAX_VALUE;

    /**
     * Sets the authentication handler to use for authenticating devices.
     * 
     * @param authHandler The handler to use.
     * @throws NullPointerException if handler is {@code null}.
     */
    public final void setAuthHandler(final AuthHandler<MqttContext> authHandler) {
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
     * Subclasses may either set the auth handler expicitly using
     * {@link #setAuthHandler(AuthHandler)} or override this method in order to
     * create a custom auth handler.
     * 
     * @return The handler.
     */
    protected AuthHandler<MqttContext> createAuthHandler() {

        return new ChainAuthHandler<MqttContext>()
                .append(new X509AuthHandler(
                        new TenantServiceBasedX509Authentication(getTenantServiceClient(), tracer),
                        new X509AuthProvider(getCredentialsServiceClient(), getConfig(), tracer)))
                .append(new ConnectPacketAuthHandler(
                        new UsernamePasswordAuthProvider(
                                getCredentialsServiceClient(),
                                getConfig(),
                                tracer)));
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

        connectionLimit = getConfig().isConnectionLimitConfigured() ? checkConnectionLimit() : autoconfigureConnectionLimit();

        checkPortConfiguration()
            .compose(ok -> {
                return CompositeFuture.all(bindSecureMqttServer(), bindInsecureMqttServer());
            }).compose(t -> {
                if (authHandler == null) {
                    authHandler = createAuthHandler();
                }
                startFuture.complete();
            }, startFuture);
    }

    private int autoconfigureConnectionLimit() {
        final long maxMemory = Runtime.getRuntime().maxMemory();
        final int recommendedLimit = getRecommendedConnectionLimit(maxMemory);

        LOG.info("Connection limit not configured, setting it to {} (based on {} MB memory)",
                recommendedLimit, maxMemory / 1_000_000);

        return recommendedLimit;
    }

    private int checkConnectionLimit() {
        final long maxMemory = Runtime.getRuntime().maxMemory();
        final int limit = getConfig().getMaxConnections();
        final int recommendedLimit = getRecommendedConnectionLimit(maxMemory);

        if (limit > recommendedLimit) {
            LOG.warn("Connection limit ({}) too high: with {} MB memory the limit should be set to max. {}.",
                    limit, maxMemory / 1_000_000, recommendedLimit);
        }

        return limit;
    }

    /**
     * Returns a recommended limit of concurrent connections for the given maximum amount of memory. An amount of
     * {@link #MINIMAL_MEMORY} bytes should be reserved for the startup of the adapter and
     * {@link #MEMORY_PER_CONNECTION} bytes are estimated per connection.
     * <p>
     * The value returned is set as the connection limit if no other value is configured in
     * {@link ProtocolAdapterProperties#setMaxConnections(int)}. If a value is configured and it is above the value
     * returned here, a warning is printed.
     * <p>
     * Subclasses may override this method to implement another calculation.
     * 
     * @param maxMemory The maximum amount of memory for the JVM in bytes as returned by {@link Runtime#maxMemory()}.
     * @return The recommended maximum connection limit between 0 and {@link Integer#MAX_VALUE}.
     */
    protected int getRecommendedConnectionLimit(final long maxMemory) {

        final long recommendedLimit = (maxMemory - MINIMAL_MEMORY) / MEMORY_PER_CONNECTION;

        if (recommendedLimit <= 0) {
            LOG.warn("Not enough memory. It is recommended to provide more than {} MB (currently {} MB).",
                    MINIMAL_MEMORY / 1_000_000, maxMemory / 1_000_000);
            return 0;
        } else if (recommendedLimit > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        } else {
            return (int) recommendedLimit;
        }
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
     * Invoked when a client sends its <em>CONNECT</em> packet.
     * <p>
     * Authenticates the client (if required) and registers handlers for processing messages published by the client.
     * 
     * @param endpoint The MQTT endpoint representing the client.
     */
    final void handleEndpointConnection(final MqttEndpoint endpoint) {

        LOG.debug("connection request from client [client-id: {}]", endpoint.clientIdentifier());
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

        if (isConnectionLimitExceeded()) {
            LOG.debug("Connection limit ({}) exceeded, reject connection request", connectionLimit);
            currentSpan.log(String.format("connection limit (%d) exceeded", connectionLimit));
            return Future.failedFuture(new MqttConnectionException(
                    MqttConnectReturnCode.CONNECTION_REFUSED_SERVER_UNAVAILABLE));
        }

        if (getConfig().isAuthenticationRequired()) {
            return handleEndpointConnectionWithAuthentication(endpoint, currentSpan);
        } else {
            return handleEndpointConnectionWithoutAuthentication(endpoint);
        }
    }

    private boolean isConnectionLimitExceeded() {
        return metrics.getNumberOfConnections() >= connectionLimit;
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
                            LOG.warn(
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
            LOG.debug("connection request from client [clientId: {}] rejected due to {} ",
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
     * Registers a close handler on the endpoint which invokes {@link #close(MqttEndpoint, Device, CommandHandler)}. Registers a publish
     * handler on the endpoint which invokes {@link #onPublishedMessage(MqttContext)} for each message being published
     * by the client. Accepts the connection request.
     * 
     * @param endpoint The MQTT endpoint representing the client.
     */
    private Future<Device> handleEndpointConnectionWithoutAuthentication(final MqttEndpoint endpoint) {
        registerHandlers(endpoint, null);
        LOG.debug("unauthenticated device [clientId: {}] connected", endpoint.clientIdentifier());
        return Future.succeededFuture();
    }

    private Future<Device> handleEndpointConnectionWithAuthentication(final MqttEndpoint endpoint,
            final Span currentSpan) {

        final MqttContext context = MqttContext.fromConnectPacket(endpoint);
        context.setTracingContext(currentSpan.context());
        final Future<DeviceUser> authAttempt = authenticate(context, currentSpan);
        return authAttempt
                .compose(authenticatedDevice -> CompositeFuture.all(
                            getTenantConfiguration(authenticatedDevice.getTenantId(), currentSpan.context())
                                .compose(tenant -> isAdapterEnabled(tenant)),
                            checkDeviceRegistration(authenticatedDevice, currentSpan.context()))
                        .map(ok -> authenticatedDevice))
                .compose(authenticatedDevice -> createLinks(authenticatedDevice, currentSpan))
                .compose(authenticatedDevice -> registerHandlers(endpoint, authenticatedDevice))
                .recover(t -> {
                    if (authAttempt.failed()) {
                        LOG.debug("could not authenticate device", t);
                    } else {
                        LOG.debug("cannot establish connection with device [tenant-id: {}, device-id: {}]",
                                authAttempt.result().getTenantId(), authAttempt.result().getDeviceId(), t);
                    }
                    return Future.failedFuture(t);
                });
    }

    private Future<DeviceUser> authenticate(final MqttContext connectContext, final Span currentSpan) {

        return authHandler.authenticateDevice(connectContext);
    }

    /**
     * Invoked when a device sends an MQTT <em>SUBSCRIBE</em> packet.
     * <p>
     * This method currently only supports topic filters for subscribing to
     * commands as defined by Hono's
     * <a href="https://www.eclipse.org/hono/user-guide/mqtt-adapter/#command-control">
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
     * @throws NullPointerException if any of endpoint or subscribe message are {@code null}.
     */
    @SuppressWarnings("rawtypes")
    protected final void onSubscribe(
            final MqttEndpoint endpoint,
            final Device authenticatedDevice,
            final MqttSubscribeMessage subscribeMsg,
            final CommandHandler<T> cmdHandler) {

        Objects.requireNonNull(endpoint);
        Objects.requireNonNull(subscribeMsg);

        final Map<String, Future> topicFilters = new HashMap<>();
        final Map<MqttTopicSubscription, Future> subscriptionOutcome = new LinkedHashMap<>(
                subscribeMsg.topicSubscriptions().size());

        final Span span = newSpan("SUBSCRIBE", endpoint, authenticatedDevice);

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
                    LOG.debug("cannot create subscription [filter: {}, requested QoS: {}]: unsupported topic filter",
                            subscription.topicName(), subscription.qualityOfService());
                    result = Future.failedFuture(new IllegalArgumentException("unsupported topic filter"));
                } else if (MqttQoS.EXACTLY_ONCE.equals(subscription.qualityOfService())) {
                    // we do not support subscribing to commands using QoS 2
                    result = Future.failedFuture(new IllegalArgumentException("QoS 2 not supported for command subscription"));
                } else {
                    result = createCommandConsumer(endpoint, cmdSub, cmdHandler).map(consumer -> {
                        final Map<String, Object> items = new HashMap<>(4);
                        items.put(Fields.EVENT, "accepting subscription");
                        items.put("filter", subscription.topicName());
                        items.put("requested QoS", subscription.qualityOfService());
                        items.put("granted QoS", subscription.qualityOfService());
                        span.log(items);
                        LOG.debug("created subscription [tenant: {}, device: {}, filter: {}, requested QoS: {}, granted QoS: {}]",
                                cmdSub.getTenant(), cmdSub.getDeviceId(), subscription.topicName(),
                                subscription.qualityOfService(), subscription.qualityOfService());
                        cmdHandler.addSubscription(cmdSub, consumer);
                        return cmdSub;
                    }).recover(t -> {
                        final Map<String, Object> items = new HashMap<>(4);
                        items.put(Fields.EVENT, "rejecting subscription");
                        items.put("filter", subscription.topicName());
                        items.put("requested QoS", subscription.qualityOfService());
                        items.put(Fields.MESSAGE, t.getMessage());
                        TracingHelper.logError(span, items);
                        LOG.debug("cannot create subscription [tenant: {}, device: {}, filter: {}, requested QoS: {}]",
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

    private Span newSpan(final String operationName, final MqttEndpoint endpoint, final Device authenticatedDevice) {
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
        return span;
    }

    /**
     * Invoked when a device sends an MQTT <em>UNSUBSCRIBE</em> packet.
     * <p>
     * This method currently only supports topic filters for unsubscribing from
     * commands as defined by Hono's
     * <a href="https://www.eclipse.org/hono/user-guide/mqtt-adapter/#receive-commands">
     * MQTT adapter user guide</a>.
     *
     * @param endpoint The endpoint representing the connection to the device.
     * @param authenticatedDevice The authenticated identity of the device or {@code null}
     *                            if the device has not been authenticated.
     * @param cmdHandler The commandHandler to track command subscriptions, unsubscriptions and handle PUBACKs.
     * @param unsubscribeMsg The unsubscribe request received from the device.
     */
    protected final void onUnsubscribe(
            final MqttEndpoint endpoint,
            final Device authenticatedDevice,
            final MqttUnsubscribeMessage unsubscribeMsg,
            final CommandHandler<T> cmdHandler) {

        final Span span = newSpan("UNSUBSCRIBE", endpoint, authenticatedDevice);

        unsubscribeMsg.topics().forEach(topic -> {
            final CommandSubscription cmdSub = CommandSubscription.fromTopic(topic, authenticatedDevice);
            if (cmdSub == null) {
                final Map<String, Object> items = new HashMap<>(2);
                items.put(Fields.EVENT, "ignoring unsupported topic filter");
                items.put("filter", topic);
                span.log(items);
                LOG.debug("ignoring unsubscribe request for unsupported topic filter [{}]", topic);
            } else {
                final String tenantId = cmdSub.getTenant();
                final String deviceId = cmdSub.getDeviceId();
                final Map<String, Object> items = new HashMap<>(2);
                items.put(Fields.EVENT, "unsubscribing device from topic");
                items.put("filter", topic);
                span.log(items);
                LOG.debug("unsubscribing device [tenant-id: {}, device-id: {}] from topic [{}]",
                        tenantId, deviceId, topic);
                cmdHandler.removeSubscription(topic, (tenant, device) -> {
                    final Span closeHandlerSpan = newSpan("Send Disconnected Event", endpoint, authenticatedDevice);
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

    private Future<MessageConsumer> createCommandConsumer(final MqttEndpoint mqttEndpoint, final CommandSubscription sub, final CommandHandler<T> cmdHandler) {

        // if a device does not specify a keep alive in its CONNECT packet then
        // the default value of the CommandConnection will be used
        final long livenessCheckInterval = (long) mqttEndpoint.keepAliveTimeSeconds() * 1000 / 2;

        return getCommandConnection().createCommandConsumer(
                sub.getTenant(),
                sub.getDeviceId(),
                commandContext -> {

                    Tags.COMPONENT.set(commandContext.getCurrentSpan(), getTypeName());
                    final Sample timer = metrics.startTimer();
                    final Command command = commandContext.getCommand();
                    if (command.isValid()) {
                        addMicrometerSample(commandContext, timer);
                        onCommandReceived(mqttEndpoint, sub, commandContext, cmdHandler);
                    } else {
                        // issue credit so that application(s) can send the next command
                        commandContext.reject(new ErrorCondition(Constants.AMQP_BAD_REQUEST, "malformed command message"), 1);
                        metrics.reportCommand(
                                command.isOneWay() ? Direction.ONE_WAY : Direction.REQUEST,
                                sub.getTenant(),
                                ProcessingOutcome.UNPROCESSABLE,
                                command.getPayloadSize(),
                                timer);
                    }
                },
                remoteClose -> {},
                livenessCheckInterval);
    }

    void handlePublishedMessage(final MqttContext context) {
        // there is no way to extract a SpanContext from an MQTT 3.1 message
        // so we start a new one for every message
        final MqttQoS qos = context.message().qosLevel();
        final Span span = tracer.buildSpan("PUBLISH")
            .ignoreActiveSpan()
            .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_SERVER)
            .withTag(Tags.MESSAGE_BUS_DESTINATION.getKey(), context.message().topicName())
            .withTag(TracingHelper.TAG_QOS.getKey(), qos.toString())
            .withTag(Tags.COMPONENT.getKey(), getTypeName())
            .withTag(TracingHelper.TAG_CLIENT_ID.getKey(), context.deviceEndpoint().clientIdentifier())
            .start();
        context.setTracingContext(span.context());
        context.setTimer(getMetrics().startTimer());

        checkTopic(context)
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
     * Uploads a message to Hono Messaging.
     * 
     * @param ctx The context in which the MQTT message has been published.
     * @param resource The resource that the message should be uploaded to.
     * @param message The message to send.
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
        case CONTROL:
            return uploadCommandResponseMessage(ctx, resource);
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
     * @throws IllegalArgumentException if the context does not contain a
     *              telemetry message or if the payload is empty.
     */
    public final Future<Void> uploadTelemetryMessage(
            final MqttContext ctx,
            final String tenant,
            final String deviceId,
            final Buffer payload) {

        if (ctx.endpoint() != EndpointType.TELEMETRY) {
            throw new IllegalArgumentException("context does not contain telemetry message but " +
                ctx.endpoint().getCanonicalName());
        }

        final MetricsTags.QoS qos = MetricsTags.QoS.from(ctx.message().qosLevel().value());
        return uploadMessage(
                Objects.requireNonNull(ctx),
                Objects.requireNonNull(tenant),
                Objects.requireNonNull(deviceId),
                Objects.requireNonNull(payload),
                getTelemetrySender(tenant),
                ctx.endpoint()
        ).map(success -> {
            metrics.reportTelemetry(
                    ctx.endpoint(),
                    ctx.tenant(),
                    MetricsTags.ProcessingOutcome.FORWARDED,
                    qos,
                    payload.length(),
                    ctx.getTimer());
            return success;
        }).recover(t -> {
            metrics.reportTelemetry(
                    ctx.endpoint(),
                    ctx.tenant(),
                    ProcessingOutcome.from(t),
                    qos,
                    payload.length(),
                    ctx.getTimer());
            return Future.failedFuture(t);
        });
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
     * @throws IllegalArgumentException if the context does not contain an
     *              event or if the payload is empty.
     */
    public final Future<Void> uploadEventMessage(
            final MqttContext ctx,
            final String tenant,
            final String deviceId,
            final Buffer payload) {

        if (ctx.endpoint() != EndpointType.EVENT) {
            throw new IllegalArgumentException("context does not contain event but " +
                ctx.endpoint().getCanonicalName());
        }

        final MetricsTags.QoS qos = MetricsTags.QoS.from(ctx.message().qosLevel().value());
        return uploadMessage(
                Objects.requireNonNull(ctx),
                Objects.requireNonNull(tenant),
                Objects.requireNonNull(deviceId),
                Objects.requireNonNull(payload),
                getEventSender(tenant),
                ctx.endpoint()
        ).map(success -> {
            metrics.reportTelemetry(
                    ctx.endpoint(),
                    ctx.tenant(),
                    MetricsTags.ProcessingOutcome.FORWARDED,
                    qos,
                    payload.length(),
                    ctx.getTimer());
            return (Void) null;
        }).recover(t -> {
            metrics.reportTelemetry(
                    ctx.endpoint(),
                    ctx.tenant(),
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
     * @throws NullPointerException if any of of the parameters are {@code null}.
     */
    public final Future<Void> uploadCommandResponseMessage(
            final MqttContext ctx,
            final ResourceIdentifier targetAddress) {

        Objects.requireNonNull(ctx);
        Objects.requireNonNull(targetAddress);

        final String[] addressPath = targetAddress.getResourcePath();

        if (addressPath.length <= CommandConstants.TOPIC_POSITION_RESPONSE_STATUS) {
            metrics.reportCommand(
                    Direction.RESPONSE,
                    targetAddress.getTenantId(),
                    ProcessingOutcome.UNPROCESSABLE,
                    ctx.message().payload().length(),
                    ctx.getTimer());
            return Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST, "command response topic has too few segments"));
        } else {
            try {
                final Integer status = Integer.parseInt(addressPath[CommandConstants.TOPIC_POSITION_RESPONSE_STATUS]);
                final String reqId = addressPath[CommandConstants.TOPIC_POSITION_RESPONSE_REQ_ID];
                final CommandResponse commandResponse = CommandResponse.from(reqId, targetAddress.getTenantId(),
                        targetAddress.getResourceId(), ctx.message().payload(), ctx.contentType(), status);

                if (commandResponse == null) {
                    metrics.reportCommand(
                            Direction.RESPONSE,
                            targetAddress.getTenantId(),
                            ProcessingOutcome.UNPROCESSABLE,
                            ctx.message().payload().length(),
                            ctx.getTimer());
                    return Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST, "command response topic contains invalid data"));
               } else {

                   final Span currentSpan = tracer.buildSpan("upload Command response")
                           .asChildOf(ctx.getTracingContext())
                           .ignoreActiveSpan()
                           .withTag(Tags.COMPONENT.getKey(), getTypeName())
                           .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_CLIENT)
                           .withTag(MessageHelper.APP_PROPERTY_TENANT_ID, targetAddress.getTenantId())
                           .withTag(MessageHelper.APP_PROPERTY_DEVICE_ID, targetAddress.getResourceId())
                           .withTag(Constants.HEADER_COMMAND_RESPONSE_STATUS, status)
                           .withTag(Constants.HEADER_COMMAND_REQUEST_ID, reqId)
                           .withTag(TracingHelper.TAG_AUTHENTICATED.getKey(), ctx.authenticatedDevice() != null)
                           .start();

                   return sendCommandResponse(targetAddress.getTenantId(), commandResponse, currentSpan.context())
                           .map(delivery -> {
                               LOG.trace("successfully forwarded command response from device [tenant-id: {}, device-id: {}]",
                                       targetAddress.getTenantId(), targetAddress.getResourceId());
                               metrics.reportCommand(
                                       Direction.RESPONSE,
                                       targetAddress.getTenantId(),
                                       ProcessingOutcome.FORWARDED,
                                       ctx.message().payload().length(),
                                       ctx.getTimer());
                               // check that the remote MQTT client is still connected before sending PUBACK
                               if (ctx.deviceEndpoint().isConnected() && ctx.message().qosLevel() == MqttQoS.AT_LEAST_ONCE) {
                                   ctx.deviceEndpoint().publishAcknowledge(ctx.message().messageId());
                               }
                               currentSpan.finish();
                               return (Void) null;
                           }).recover(t -> {
                               TracingHelper.logError(currentSpan, t);
                               currentSpan.finish();
                               metrics.reportCommand(
                                       Direction.RESPONSE,
                                       targetAddress.getTenantId(),
                                       ProcessingOutcome.from(t),
                                       ctx.message().payload().length(),
                                       ctx.getTimer());
                               return Future.failedFuture(t);
                           });

               }
            } catch (final NumberFormatException e) {
                metrics.reportCommand(
                        Direction.RESPONSE,
                        targetAddress.getTenantId(),
                        ProcessingOutcome.UNPROCESSABLE,
                        ctx.message().payload().length(),
                        ctx.getTimer());
                return Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST, "invalid status code"));
            }
        }
    }

    private Future<Void> uploadMessage(
            final MqttContext ctx,
            final String tenant,
            final String deviceId,
            final Buffer payload,
            final Future<? extends MessageSender> senderTracker,
            final MetricsTags.EndpointType endpoint) {

        if (!isPayloadOfIndicatedType(payload, ctx.contentType())) {
            return Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST,
                    String.format("Content-Type %s does not match payload", ctx.contentType())));
        } else {

            final Span currentSpan = tracer.buildSpan("upload " + endpoint)
                    .asChildOf(ctx.getTracingContext())
                    .ignoreActiveSpan()
                    .withTag(Tags.COMPONENT.getKey(), getTypeName())
                    .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_CLIENT)
                    .withTag(MessageHelper.APP_PROPERTY_TENANT_ID, tenant)
                    .withTag(MessageHelper.APP_PROPERTY_DEVICE_ID, deviceId)
                    .withTag(TracingHelper.TAG_AUTHENTICATED.getKey(), ctx.authenticatedDevice() != null)
                    .start();

            final Future<JsonObject> tokenTracker = getRegistrationAssertion(tenant, deviceId,
                    ctx.authenticatedDevice(), currentSpan.context());
            final Future<TenantObject> tenantEnabledTracker = getTenantConfiguration(tenant, currentSpan.context())
                    .compose(tenantObject -> isAdapterEnabled(tenantObject));

            return CompositeFuture.all(tokenTracker, tenantEnabledTracker, senderTracker).compose(ok -> {

                    final MessageSender sender = senderTracker.result();
                    final Message downstreamMessage = newMessage(
                            ResourceIdentifier.from(endpoint.getCanonicalName(), tenant, deviceId),
                            sender.isRegistrationAssertionRequired(),
                            ctx.message().topicName(),
                            ctx.contentType(),
                            payload,
                            tokenTracker.result(),
                            null);

                    addRetainAnnotation(ctx, downstreamMessage, currentSpan);
                    customizeDownstreamMessage(downstreamMessage, ctx);

                    if (ctx.isAtLeastOnce()) {
                        return sender.sendAndWaitForOutcome(downstreamMessage, currentSpan.context());
                    } else {
                        return sender.send(downstreamMessage, currentSpan.context());
                    }
            }).compose(delivery -> {

                LOG.trace("successfully processed message [topic: {}, QoS: {}] from device [tenantId: {}, deviceId: {}]",
                        ctx.message().topicName(), ctx.message().qosLevel(), tenant, deviceId);
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
                    LOG.debug("cannot process message [endpoint: {}] from device [tenantId: {}, deviceId: {}]: {} - {}",
                            endpoint, tenant, deviceId, e.getErrorCode(), e.getMessage());
                } else {
                    LOG.debug("cannot process message [endpoint: {}] from device [tenantId: {}, deviceId: {}]",
                            endpoint, tenant, deviceId, t);
                }
                TracingHelper.logError(currentSpan, t);
                currentSpan.finish();
                return Future.failedFuture(t);
            });
        }
    }

    /**
     * Closes a connection to a client.
     * 
     * @param endpoint The connection to close.
     * @param authenticatedDevice Optional authenticated device information, may be {@code null}.
     * @param cmdHandler The commandHandler to track command subscriptions, unsubscriptions and handle PUBACKs.
     */
    protected final void close(final MqttEndpoint endpoint, final Device authenticatedDevice, final CommandHandler<T> cmdHandler) {
        final Span span = newSpan("CLOSE", endpoint, authenticatedDevice);
        onClose(endpoint);
        cmdHandler.removeAllSubscriptions((tenant, device) -> {
            final Span closeHandlerSpan = newSpan("Send Disconnected Event", endpoint, authenticatedDevice);
            sendDisconnectedTtdEvent(tenant, device, authenticatedDevice, span.context())
                    .setHandler(sendAttempt -> closeHandlerSpan.finish());
        });
        sendDisconnectedEvent(endpoint.clientIdentifier(), authenticatedDevice);
        if (authenticatedDevice == null) {
            LOG.debug("connection to anonymous device [clientId: {}] closed", endpoint.clientIdentifier());
            metrics.decrementUnauthenticatedConnections();
        } else {
            LOG.debug("connection to device [tenant-id: {}, device-id: {}] closed",
                    authenticatedDevice.getTenantId(), authenticatedDevice.getDeviceId());
            metrics.decrementConnections(authenticatedDevice.getTenantId());
        }
        if (endpoint.isConnected()) {
            LOG.debug("closing connection with client [client ID: {}]", endpoint.clientIdentifier());
            endpoint.close();
        } else {
            LOG.trace("client has already closed connection");
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
     * @param endpoint The device that the command should be delivered to.
     * @param subscription The device's command subscription.
     * @param commandContext The command to be delivered.
     * @param cmdHandler The commandHandler to track command subscriptions, unsubscriptions and handle PUBACKs.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    protected final void onCommandReceived(
            final MqttEndpoint endpoint,
            final CommandSubscription subscription,
            final CommandContext commandContext,
            final CommandHandler<T> cmdHandler) {

        Objects.requireNonNull(endpoint);
        Objects.requireNonNull(subscription);
        Objects.requireNonNull(commandContext);

        TracingHelper.TAG_CLIENT_ID.set(commandContext.getCurrentSpan(), endpoint.clientIdentifier());
        final Command command = commandContext.getCommand();
        // example: control/DEFAULT_TENANT/4711/req/xyz/light
        // one-way commands have an empty requestId, like control/DEFAULT_TENANT/4711/req//light
        final String commandRequestId = (command.isOneWay() ? "" : command.getRequestId());
        final String topic = String.format("%s/%s/%s/%s/%s/%s",
                subscription.getEndpoint(),
                // no need to include tenant and device ID in topic
                // if device is authenticated,
                // i.e. publish to control///req/xyz/light
                subscription.isAuthenticated() ? "" : subscription.getTenant(),
                subscription.isAuthenticated() ? "" : subscription.getDeviceId(),
                subscription.getRequestPart(),
                commandRequestId,
                command.getName());
        LOG.debug("Publishing command to device [tenant-id: {}, device-id: {}, MQTT client-id: {}, QoS: {}]",
                subscription.getTenant(), subscription.getDeviceId(), subscription.getClientId(),
                subscription.getQos());
        final Map<String, String> items = new HashMap<>(3);
        items.put(Fields.EVENT, "Publishing command to device");
        items.put(TracingHelper.TAG_CLIENT_ID.getKey(), subscription.getClientId());
        items.put(TracingHelper.TAG_QOS.getKey(), subscription.getQos().toString());
        commandContext.getCurrentSpan().log(items);

        endpoint.publish(topic, command.getPayload(), subscription.getQos(), false, false, sentHandler -> {
            if (sentHandler.succeeded()) {
                if (MqttQoS.AT_LEAST_ONCE.equals(subscription.getQos())) {
                    cmdHandler.addToWaitingForAcknowledgement(sentHandler.result(), subscription, commandContext);
                } else {
                    afterCommandPublished(subscription, commandContext);
                }
            } else {
                LOG.debug(
                        "Error publishing command to device [tenant-id: {}, device-id: {}, MQTT client-id: {}, QoS: {}]",
                        subscription.getTenant(), subscription.getDeviceId(), endpoint.clientIdentifier(),
                        subscription.getQos(),
                        sentHandler.cause());
                TracingHelper.logError(commandContext.getCurrentSpan(), sentHandler.cause());
                metrics.reportCommand(
                        command.isOneWay() ? Direction.ONE_WAY : Direction.REQUEST,
                        subscription.getTenant(),
                        ProcessingOutcome.from(sentHandler.cause()),
                        command.getPayloadSize(),
                        getMicrometerSample(commandContext));
                commandContext.release(1);
            }
        });
    }

    private void afterCommandPublished(
            final CommandSubscription subscription,
            final CommandContext commandContext) {

        metrics.reportCommand(
                commandContext.getCommand().isOneWay() ? Direction.ONE_WAY : Direction.REQUEST,
                subscription.getTenant(),
                ProcessingOutcome.FORWARDED,
                commandContext.getCommand().getPayloadSize(),
                getMicrometerSample(commandContext));
        LOG.debug("Published command to device [tenant-id: {}, device-id: {}, MQTT client-id: {}, QoS: {}]",
                subscription.getTenant(), subscription.getDeviceId(), subscription.getClientId(),
                subscription.getQos());
        final Map<String, String> items = new HashMap<>(4);
        items.put(Fields.EVENT, "Published command to device");
        items.put(Tags.MESSAGE_BUS_DESTINATION.getKey(), subscription.getTopic());
        items.put(TracingHelper.TAG_CLIENT_ID.getKey(), subscription.getClientId());
        items.put(TracingHelper.TAG_QOS.getKey(), subscription.getQos().toString());
        commandContext.getCurrentSpan().log(items);
        commandContext.accept(1);
    }

    private static void addRetainAnnotation(final MqttContext context, final Message downstreamMessage,
            final Span currentSpan) {

        if (context.message().isRetain()) {
            currentSpan.log("device wants to retain message");
            MessageHelper.addAnnotation(downstreamMessage, MessageHelper.ANNOTATION_X_OPT_RETAIN, Boolean.TRUE);
        }
    }

    private Future<Device> createLinks(final Device authenticatedDevice, final Span currentSpan) {

        final Future<MessageSender> telemetrySender = getTelemetrySender(authenticatedDevice.getTenantId());
        final Future<MessageSender> eventSender = getEventSender(authenticatedDevice.getTenantId());

        return CompositeFuture
                .all(telemetrySender, eventSender)
                .compose(ok -> {
                    currentSpan.log("opened downstream links");
                    LOG.debug(
                            "providently opened downstream links [credit telemetry: {}, credit event: {}] for tenant [{}]",
                            telemetrySender.result().getCredit(), eventSender.result().getCredit(),
                            authenticatedDevice.getTenantId());
                    return Future.succeededFuture(authenticatedDevice);
                });
    }

    private Future<Device> registerHandlers(final MqttEndpoint endpoint, final Device authenticatedDevice) {
        final CommandHandler<T> cmdHandler = new CommandHandler<>(vertx, getConfig());
        endpoint.closeHandler(v -> close(endpoint, authenticatedDevice, cmdHandler));
        endpoint.publishHandler(
                message -> handlePublishedMessage(MqttContext.fromPublishPacket(message, endpoint, authenticatedDevice)));
        endpoint.publishAcknowledgeHandler(msgId -> cmdHandler.handlePubAck(msgId, afterCommandPubAckedConsumer));
        endpoint.subscribeHandler(subscribeMsg -> onSubscribe(endpoint, authenticatedDevice, subscribeMsg, cmdHandler));
        endpoint.unsubscribeHandler(unsubscribeMsg -> onUnsubscribe(endpoint, authenticatedDevice, unsubscribeMsg, cmdHandler));
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
