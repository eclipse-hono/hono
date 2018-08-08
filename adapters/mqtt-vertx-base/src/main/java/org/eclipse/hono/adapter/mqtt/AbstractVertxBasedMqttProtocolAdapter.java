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

package org.eclipse.hono.adapter.mqtt;

import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import io.vertx.mqtt.messages.MqttPublishMessage;
import io.vertx.mqtt.messages.MqttSubscribeMessage;
import io.vertx.mqtt.messages.MqttUnsubscribeMessage;

import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.MessageConsumer;
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
import org.eclipse.hono.service.command.Command;
import org.eclipse.hono.service.command.CommandResponse;
import org.eclipse.hono.service.command.CommandSubscription;
import org.eclipse.hono.tracing.TracingHelper;
import org.eclipse.hono.util.CommandConstants;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.EndpointType;
import org.eclipse.hono.util.EventConstants;
import org.eclipse.hono.util.ExecutionContext;
import org.eclipse.hono.util.MessageHelper;
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
                    metrics = new DropwizardBasedMqttAdapterMetrics();
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

        LOG.debug("connection request from client [client-id: {}]", endpoint.clientIdentifier());
        final Span span = tracer.buildSpan("CONNECT")
                .ignoreActiveSpan()
                .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_SERVER)
                .withTag(Tags.COMPONENT.getKey(), getTypeName())
                .withTag(TracingHelper.TAG_CLIENT_ID.getKey(), endpoint.clientIdentifier())
                .start();

        isConnected()
                .compose(v -> handleConnectionRequest(endpoint, span))
                .setHandler(result -> handleConnectionRequestResult(endpoint, span, result));

    }

    private Future<Device> handleConnectionRequest(final MqttEndpoint endpoint, final Span currentSpan) {
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

            TracingHelper.TAG_AUTHENTICATED.set(currentSpan, false);

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
            TracingHelper.logError(currentSpan, t);
        }
        currentSpan.finish();
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
        });
        endpoint.publishHandler(message -> handlePublishedMessage(new MqttContext(message, endpoint)));

        endpoint.subscribeHandler(subscribeMsg -> onSubscribe(endpoint, null, subscribeMsg));
        endpoint.unsubscribeHandler(unsubscribeMsg -> onUnsubscribe(endpoint, null, unsubscribeMsg));

        LOG.debug("unauthenticated device [clientId: {}] connected", endpoint.clientIdentifier());
        metrics.incrementUnauthenticatedConnections();
        return accepted();
    }

    private Future<Device> handleEndpointConnectionWithAuthentication(final MqttEndpoint endpoint, final Span currentSpan) {

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

                return getTenantConfiguration(credentials.getTenantId(), currentSpan.context()).compose(tenantConfig -> {
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
                    currentSpan.log(String.format("device authenticated"));
                    LOG.debug("successfully authenticated device [tenant-id: {}, auth-id: {}, device-id: {}]",
                            authenticatedDevice.getTenantId(), credentials.getAuthId(),
                            authenticatedDevice.getDeviceId());
                    return triggerLinkCreation(authenticatedDevice.getTenantId()).map(done -> {
                        currentSpan.log(String.format("opened downstream links"));
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
        });

        endpoint.publishHandler(message -> handlePublishedMessage(new MqttContext(message, endpoint, authenticatedDevice)));
        endpoint.subscribeHandler(subscribeMsg -> onSubscribe(endpoint, authenticatedDevice, subscribeMsg));
        endpoint.unsubscribeHandler(unsubscribeMsg -> onUnsubscribe(endpoint, authenticatedDevice, unsubscribeMsg));

        metrics.incrementConnections(authenticatedDevice.getTenantId());
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
     */
    @SuppressWarnings("rawtypes")
    protected final void onSubscribe(
            final MqttEndpoint endpoint,
            final Device authenticatedDevice,
            final MqttSubscribeMessage subscribeMsg) {

        final Map<String, Future> topicFilters = new HashMap<>();
        final List<Future> subscriptionOutcome = new ArrayList<>(subscribeMsg.topicSubscriptions().size());

        subscribeMsg.topicSubscriptions().forEach(subscription -> {

            Future result = topicFilters.get(subscription.topicName());
            if (result != null) {
                // according to the MQTT 3.1.1 spec (section 3.8.4) we need to
                // make sure that we process multiple filters as if they had been
                // submitted using multiple separate SUBSCRIBE packets
                // we therefore always return the same result for duplicate topic filters
                subscriptionOutcome.add(result);
            } else {

                // we currently only support subscriptions for receiving commands
                final CommandSubscription cmdSub = CommandSubscription.fromTopic(subscription.topicName(), authenticatedDevice);
                if (cmdSub == null) {
                    LOG.debug("cannot create subscription [filter: {}, requested QoS: {}]: unsupported topic filter",
                            subscription.topicName(), subscription.qualityOfService());
                    result = Future.failedFuture(new IllegalArgumentException("unsupported topic filter"));
                } else {
                    result = createCommandConsumer(endpoint, cmdSub).map(consumer -> {
                        // make sure that we close the consumer and notify downstream
                        // applications when the connection is closed
                        endpoint.closeHandler(c -> {
                            sendDisconnectedTtdEvent(cmdSub.getTenant(), cmdSub.getDeviceId(), authenticatedDevice);
                            closeCommandConsumer(cmdSub.getTenant(), cmdSub.getDeviceId());
                            close(endpoint, authenticatedDevice);
                        });
                        LOG.debug("created subscription [tenant: {}, device: {}, filter: {}, requested QoS: {}, granted QoS: {}]",
                                cmdSub.getTenant(), cmdSub.getDeviceId(), subscription.topicName(),
                                subscription.qualityOfService(), MqttQoS.AT_MOST_ONCE);
                        return cmdSub;
                    }).recover(t -> {
                        LOG.debug("cannot create subscription [tenant: {}, device: {}, filter: {}, requested QoS: {}]",
                                cmdSub.getTenant(), cmdSub.getDeviceId(), subscription.topicName(),
                                subscription.qualityOfService(), t);
                        return Future.failedFuture(t);
                    });
                }
                topicFilters.put(subscription.topicName(), result);
                subscriptionOutcome.add(result);
            }
        });

        // wait for all futures to complete before sending SUBACK
        CompositeFuture.join(subscriptionOutcome).setHandler(v -> {

            // return a status code for each topic filter contained in the SUBSCRIBE packet
            final List<MqttQoS> grantedQosLevels = subscriptionOutcome.stream()
                    .map(f -> f.failed() ? MqttQoS.FAILURE : MqttQoS.AT_MOST_ONCE)
                    .collect(Collectors.toList());

            endpoint.subscribeAcknowledge(subscribeMsg.messageId(), grantedQosLevels);

            // now that we have informed the device about the outcome
            // we can send empty notifications for succeeded command subscriptions
            // downstream
            topicFilters.values().forEach(f -> {
                if (f.succeeded() && f.result() instanceof CommandSubscription) {
                    final CommandSubscription s = (CommandSubscription) f.result();
                    sendConnectedTtdEvent(s.getTenant(), s.getDeviceId(), authenticatedDevice);
                }
            });
        });
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
     * @param unsubscribeMsg The unsubscribe request received from the device.
     */
    protected final void onUnsubscribe(
            final MqttEndpoint endpoint,
            final Device authenticatedDevice,
            final MqttUnsubscribeMessage unsubscribeMsg) {

        unsubscribeMsg.topics().forEach(topic -> {
            final CommandSubscription cmdSub = CommandSubscription.fromTopic(topic, authenticatedDevice);
            if (cmdSub == null) {
                LOG.debug("ignoring unsubscribe request for unsupported topic filter [{}]", topic);
            } else {
                final String tenantId = cmdSub.getTenant();
                final String deviceId = cmdSub.getDeviceId();
                LOG.debug("unsubscribing device [tenant-id: {}, device-id: {}] from topic [{}]",
                        tenantId, deviceId, topic);
                closeCommandConsumer(tenantId, deviceId);
                sendDisconnectedTtdEvent(tenantId, deviceId, authenticatedDevice);
            }
        });
        endpoint.unsubscribeAcknowledge(unsubscribeMsg.messageId());
    }

    private Future<MessageConsumer> createCommandConsumer(final MqttEndpoint mqttEndpoint, final CommandSubscription sub) {

        return createCommandConsumer(
                sub.getTenant(),
                sub.getDeviceId(),
                createCommandMessageConsumer(sub.getTenant(), sub.getDeviceId(),
                        command -> onCommandReceived(mqttEndpoint, sub, command)),
                close -> {
                    LOG.debug("command receiver link closed remotely for [tenant-id: {}, device-id: {}]",
                            sub.getTenant(), sub.getDeviceId());
                    closeCommandConsumer(sub.getTenant(), sub.getDeviceId());
                    // close the mqtt connection, so the device will reconnect (happens if e.g. the dispatch router is killed)
                    close(mqttEndpoint, new Device(sub.getTenant(), sub.getDeviceId()));
                });
    }

    private void closeCommandConsumer(final String tenantId, final String deviceId) {
        getCommandConnection().closeCommandConsumer(tenantId, deviceId).otherwise(t -> {
            // not an error if already closed - e.g. tries to close on unsubscribe and close
            LOG.debug("cannot close command consumer [tenant-id: {}, device-id: {}]: {}",
                    tenantId, deviceId, t.getMessage());
            return null;
        });
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

        switch (EndpointType.fromString(resource.getEndpoint())) {
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
            return uploadCommandResponseMessage(
                    ctx,
                    resource.getTenantId(),
                    resource.getResourceId(),
                    message);
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
                TelemetryConstants.TELEMETRY_ENDPOINT
        ).compose(success -> {
            metrics.incrementProcessedMessages(TelemetryConstants.TELEMETRY_ENDPOINT, tenant);
            metrics.incrementProcessedPayload(TelemetryConstants.TELEMETRY_ENDPOINT, tenant, messagePayloadSize(ctx.message()));
            return Future.succeededFuture(success);
        }).recover(error -> {
            if (!(error instanceof ClientErrorException)) {
                metrics.incrementUndeliverableMessages(TelemetryConstants.TELEMETRY_ENDPOINT, tenant);
            }
            return Future.failedFuture(error);
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
                EventConstants.EVENT_ENDPOINT
        ).compose(success -> {
            metrics.incrementProcessedMessages(EventConstants.EVENT_ENDPOINT, tenant);
            metrics.incrementProcessedPayload(EventConstants.EVENT_ENDPOINT, tenant, messagePayloadSize(ctx.message()));
            return Future.succeededFuture(success);
        }).recover(error -> {
            if (!(error instanceof ClientErrorException)) {
                metrics.incrementUndeliverableMessages(EventConstants.EVENT_ENDPOINT, tenant);
            }
            return Future.failedFuture(error);
        });
    }

    /**
     * Uploads an command response message.
     *
     * @param ctx The context in which the MQTT message has been published.
     * @param tenant The tenant of the device that has produced the data.
     * @param deviceId The id of the device that has produced the data.
     * @param message The message payload to send.
     * @return A future indicating the outcome of the operation.
     *         <p>
     *         The future will succeed if the message has been uploaded successfully. Otherwise the future will fail
     *         with a {@link ServiceInvocationException}.
     * @throws NullPointerException if any of context, tenant, device ID or message is {@code null}.
     * @throws IllegalArgumentException if the payload is empty.
     */
    public final Future<Void> uploadCommandResponseMessage(
            final MqttContext ctx,
            final String tenant,
            final String deviceId,
            final MqttPublishMessage message) {

        Objects.requireNonNull(ctx);
        Objects.requireNonNull(message);

        final String[] topicPath = message.topicName().split("\\/");
        if (topicPath.length > CommandConstants.TOPIC_POSITION_RESPONSE_STATUS) {
            final Integer status;
            try {
                status = Integer.parseInt(topicPath[CommandConstants.TOPIC_POSITION_RESPONSE_STATUS]);
            } catch(final NumberFormatException e) {
                return Future.failedFuture("invalid status code");
            }
            final CommandResponse commandResponse = CommandResponse.from(
                    topicPath[CommandConstants.TOPIC_POSITION_RESPONSE_REQ_ID], deviceId, message.payload(),
                    ctx.contentType(), status);
            return uploadMessage(
                    ctx,
                    Objects.requireNonNull(tenant),
                    Objects.requireNonNull(deviceId),
                    message.payload(),
                    createCommandResponseSender(tenant, commandResponse.getReplyToId()),
                    CommandConstants.COMMAND_ENDPOINT,
                    commandResponse.getCorrelationId(),
                    status).compose(success -> {
                        LOG.trace("command response sent from device [{}:{}]", tenant, deviceId);
                        metrics.incrementCommandResponseDeliveredToApplication(tenant);
                        return Future.succeededFuture(success);
                    });
        } else {
            return Future.failedFuture(
                    "command response need to be in the format 'control/[tenant]/[device-id]/res/<req-id>/<status>'");
        }
    }

    private Future<Void> uploadMessage(final MqttContext ctx, final String tenant, final String deviceId,
            final Buffer payload, final Future<? extends MessageSender> senderTracker, final String endpointName) {
        if (!isPayloadOfIndicatedType(payload, ctx.contentType())) {
            return Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST,
                    String.format("Content-Type %s does not match payload", ctx.contentType())));
        } else {
            return uploadMessage(ctx, tenant, deviceId, payload, senderTracker, endpointName, null, null);
        }
    }

    private Future<Void> uploadMessage(final MqttContext ctx, final String tenant, final String deviceId,
        final Buffer payload, final Future<? extends MessageSender> senderTracker, final String endpointName,
        final String correlationId, final Integer status) {

            final Span currentSpan = tracer.buildSpan("upload " + endpointName)
                    .asChildOf(getCurrentSpan(ctx))
                    .ignoreActiveSpan()
                    .withTag(Tags.COMPONENT.getKey(), getTypeName())
                    .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_CLIENT)
                    .withTag(MessageHelper.APP_PROPERTY_TENANT_ID, tenant)
                    .withTag(MessageHelper.APP_PROPERTY_DEVICE_ID, deviceId)
                    .withTag(TracingHelper.TAG_AUTHENTICATED.getKey(), ctx.authenticatedDevice() != null)
                    .start();

            final Future<JsonObject> tokenTracker = getRegistrationAssertion(tenant, deviceId,
                    ctx.authenticatedDevice(), currentSpan.context());
            final Future<TenantObject> tenantConfigTracker = getTenantConfiguration(tenant, currentSpan.context());

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

                // command response specific
                downstreamMessage.setCorrelationId(correlationId);
                if (status != null) {
                    MessageHelper.addProperty(downstreamMessage, MessageHelper.APP_PROPERTY_STATUS, status);
                }

                customizeDownstreamMessage(downstreamMessage, ctx);

                if (ctx.message().qosLevel() == MqttQoS.AT_LEAST_ONCE) {
                    return sender.sendAndWaitForOutcome(downstreamMessage, currentSpan.context());
                } else {
                    return sender.send(downstreamMessage, currentSpan.context());
                }
            } else {
                // this adapter is not enabled for the tenant
                return Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_FORBIDDEN,
                        "adapter is not enabled for tenant"));
            }

        }).compose(delivery -> {

            LOG.trace("successfully processed message [topic: {}, QoS: {}] for device [tenantId: {}, deviceId: {}]",
                    ctx.message().topicName(), ctx.message().qosLevel(), tenant, deviceId);
            onMessageSent(ctx);
            // check that the remote MQTT client is still connected before sending PUBACK
            if (ctx.deviceEndpoint().isConnected() && ctx.message().qosLevel() == MqttQoS.AT_LEAST_ONCE) {
                currentSpan.log("sending PUBACK");
                ctx.deviceEndpoint().publishAcknowledge(ctx.message().messageId());
            }
            currentSpan.finish();
            return Future.<Void> succeededFuture();

        }).recover(t -> {

            if (ClientErrorException.class.isInstance(t)) {
                final ClientErrorException e = (ClientErrorException) t;
                LOG.debug("cannot process message for device [tenantId: {}, deviceId: {}, endpoint: {}]: {} - {}",
                        tenant, deviceId, endpointName, e.getErrorCode(), e.getMessage());
            } else {
                LOG.debug("cannot process message for device [tenantId: {}, deviceId: {}, endpoint: {}]",
                        tenant, deviceId, endpointName, t);
                onMessageUndeliverable(ctx);
            }
            TracingHelper.logError(currentSpan, t);
            currentSpan.finish();
            return Future.failedFuture(t);
        });
    }

    /**
     * Measure the size of the payload for using in the metrics system.
     * <p>
     * This implementation simply counts the bytes of the MQTT payload buffer and ignores all other attributes of the
     * message.
     * 
     * @param message The message to measure the payload size. May be {@code null}.
     * @return The number of bytes of the payload or zero if any input is {@code null}.
     */
    protected long messagePayloadSize(final MqttPublishMessage message) {
        if (message == null || message.payload() == null) {
            return 0L;
        }
        return message.payload().length();
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

    /**
     * This method will be called for arriving commands for a device.
     *
     * @param endpoint The MQTT endpoint for which the command arrives.
     * @param subscription The MQTT subscription for which the command arrives.
     * @param command The received command.
     */
    protected void onCommandReceived(final MqttEndpoint endpoint, final CommandSubscription subscription, final Command command) {
        String tenantId = subscription.getTenant();
        String deviceId = subscription.getDeviceId();
        // example: control/DEFAULT_TENANT/4711/req/xyz/light
        if (subscription.isAuthenticated()) {
            tenantId = deviceId = "";
        }
        endpoint.publish(
                String.format("%s/%s/%s/%s/%s/%s", subscription.getEndpoint(), tenantId, deviceId,
                        subscription.getRequestPart(), command.getRequestId(), command.getName()),
                command.getPayload(), MqttQoS.AT_LEAST_ONCE, false, false);
        LOG.trace("Command delivered to device [{}:{}]", subscription.getTenant(), subscription.getDeviceId());
        metrics.incrementCommandDeliveredToDevice(subscription.getTenant());
    }
}
