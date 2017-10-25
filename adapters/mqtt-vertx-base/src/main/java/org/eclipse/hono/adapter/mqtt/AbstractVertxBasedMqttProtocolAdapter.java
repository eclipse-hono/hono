/**
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.apache.qpid.proton.amqp.messaging.Accepted;
import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.MessageSender;
import org.eclipse.hono.config.ProtocolAdapterProperties;
import org.eclipse.hono.service.AbstractProtocolAdapterBase;
import org.eclipse.hono.service.auth.device.Device;
import org.eclipse.hono.service.auth.device.DeviceCredentials;
import org.eclipse.hono.service.auth.device.UsernamePasswordCredentials;
import org.eclipse.hono.service.registration.RegistrationAssertionHelperImpl;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.ResourceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import io.netty.handler.codec.mqtt.MqttConnectReturnCode;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.mqtt.MqttAuth;
import io.vertx.mqtt.MqttEndpoint;
import io.vertx.mqtt.MqttServer;
import io.vertx.mqtt.MqttServerOptions;
import io.vertx.mqtt.messages.MqttPublishMessage;

/**
 * A base class for implementing Vert.x based Hono protocol adapter
 * for publishing events &amp; telemetry data using MQTT.
 * 
 * @param <T> The type of configuration properties this adapter supports/requires.
 */
public abstract class AbstractVertxBasedMqttProtocolAdapter<T extends ProtocolAdapterProperties> extends AbstractProtocolAdapterBase<T> {

    /**
     * The <em>telemetry</em> endpoint name.
     */
    protected static final String TELEMETRY_ENDPOINT = "telemetry";
    /**
     * The <em>event</em> endpoint name.
     */
    protected static final String EVENT_ENDPOINT = "event";
    private static final String CONTENT_TYPE_OCTET_STREAM = "application/octet-stream";
    private static final int IANA_MQTT_PORT = 1883;
    private static final int IANA_SECURE_MQTT_PORT = 8883;

    /**
     * A logger to be used by concrete subclasses.
     */
    protected final Logger LOG = LoggerFactory.getLogger(getClass());

    private MqttServer server;
    private MqttServer insecureServer;
    private Map<MqttEndpoint, String> registrationAssertions = new HashMap<>();
    private MqttAdapterMetrics metrics;

    /**
     * Sets the metrics for this service
     *
     * @param metrics The metrics
     */
    @Autowired
    public final void setMetrics(final MqttAdapterMetrics metrics) {
        this.metrics = metrics;
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
        return (server != null ? server.actualPort() : Constants.PORT_UNCONFIGURED);
    }

    @Override
    protected final int getActualInsecurePort() {
        return (insecureServer != null ? insecureServer.actualPort() : Constants.PORT_UNCONFIGURED);
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

    private Future<MqttServer> bindSecureMqttServer() {

        if (isSecurePortEnabled()) {
            MqttServerOptions options = new MqttServerOptions();
            options
                .setHost(getConfig().getBindAddress())
                .setPort(determineSecurePort())
                .setMaxMessageSize(getConfig().getMaxPayloadSize());
            addTlsKeyCertOptions(options);
            addTlsTrustOptions(options);

            Future<MqttServer> result = Future.future();
            result.setHandler(mqttServerAsyncResult -> {
                server = mqttServerAsyncResult.result();
            });
            bindMqttServer(options, server, result);
            return result;
        } else {
            return Future.succeededFuture();
        }
    }

    private Future<MqttServer> bindInsecureMqttServer() {

        if (isInsecurePortEnabled()) {
            MqttServerOptions options = new MqttServerOptions();
            options
                .setHost(getConfig().getInsecurePortBindAddress())
                .setPort(determineInsecurePort())
                .setMaxMessageSize(getConfig().getMaxPayloadSize());

            Future<MqttServer> result = Future.future();
            result.setHandler(mqttServerAsyncResult -> {
                insecureServer = mqttServerAsyncResult.result();
            });
            bindMqttServer(options, insecureServer, result);
            return result;
        } else {
            return Future.succeededFuture();
        }
    }

    private void bindMqttServer(final MqttServerOptions options, final MqttServer mqttServer, final Future<MqttServer> result) {

        final MqttServer createdMqttServer =
                (mqttServer == null ? MqttServer.create(this.vertx, options) : mqttServer);

        createdMqttServer
            .endpointHandler(this::handleEndpointConnection)
            .listen(done -> {

                if (done.succeeded()) {
                    LOG.info("Hono MQTT protocol adapter running on {}:{}", getConfig().getBindAddress(), createdMqttServer.actualPort());
                    result.complete(createdMqttServer);
                } else {
                    LOG.error("error while starting up Hono MQTT adapter", done.cause());
                    result.fail(done.cause());
                }

            });
    }

    @Override
    public void doStart(final Future<Void> startFuture) {

        LOG.info("limiting size of inbound message payload to {} bytes", getConfig().getMaxPayloadSize());
        if (!getConfig().isAuthenticationRequired()) {
            LOG.warn("authentication of devices switched off");
        }
        checkPortConfiguration()
        .compose(v -> bindSecureMqttServer())
        .compose(s -> bindInsecureMqttServer())
        .compose(insecureServer -> {
            connectToMessaging(null);
            connectToDeviceRegistration(null);
            startFuture.complete();
        }, startFuture);
    }

    @Override
    public void doStop(final Future<Void> stopFuture) {

        Future<Void> shutdownTracker = Future.future();
        shutdownTracker.setHandler(done -> {
            if (done.succeeded()) {
                LOG.info("MQTT adapter has been shut down successfully");
                stopFuture.complete();
            } else {
                LOG.info("error while shutting down MQTT adapter", done.cause());
                stopFuture.fail(done.cause());
            }
        });

        Future<Void> serverTracker = Future.future();
        if (this.server != null) {
            this.server.close(serverTracker.completer());
        } else {
            serverTracker.complete();
        }

        Future<Void> insecureServerTracker = Future.future();
        if (this.insecureServer != null) {
            this.insecureServer.close(insecureServerTracker.completer());
        } else {
            insecureServerTracker.complete();
        }

        CompositeFuture.all(serverTracker, insecureServerTracker)
            .compose(d -> {
                closeClients(shutdownTracker.completer());
            }, shutdownTracker);
    }

    final void handleEndpointConnection(final MqttEndpoint endpoint) {

        LOG.debug("connection request from client [clientId: {}]", endpoint.clientIdentifier());

        if (!isConnected()) {
            endpoint.reject(MqttConnectReturnCode.CONNECTION_REFUSED_SERVER_UNAVAILABLE);
            LOG.debug("connection request from client [clientId: {}] rejected: {}",
                    endpoint.clientIdentifier(), MqttConnectReturnCode.CONNECTION_REFUSED_SERVER_UNAVAILABLE);

        } else {
            if (getConfig().isAuthenticationRequired()) {
                handleEndpointConnectionWithAuthentication(endpoint);
            } else {
                handleEndpointConnectionWithoutAuthentication(endpoint);
            }
        }
    }

    /**
     * Invoked when a client sends its <em>CONNECT</em> packet and client authentication has
     * been disabled by setting the {@linkplain ProtocolAdapterProperties#isAuthenticationRequired()
     * authentication required} configuration property to {@code false}.
     * <p>
     * Registers a close handler on the endpoint which first removes any registration assertion
     * cached for the client then delegates to {@link #onClose(MqttEndpoint)}.
     * Registers a publish handler on the endpoint which invokes
     * {@link #onUnauthenticatedMessage(MqttEndpoint, MqttPublishMessage)} for each message
     * being published by the client.
     * Accepts the connection request.
     * 
     * @param endpoint The MQTT endpoint representing the client.
     */
    private void handleEndpointConnectionWithoutAuthentication(final MqttEndpoint endpoint) {

        endpoint.closeHandler(v -> {
            LOG.debug("connection to unauthenticated device [clientId: {}] closed", endpoint.clientIdentifier());
            if (registrationAssertions.remove(endpoint) != null) {
                LOG.trace("removed registration assertion for device [clientId: {}]", endpoint.clientIdentifier());
            }
            onClose(endpoint);
        });
        endpoint.publishHandler(message -> {
            onUnauthenticatedMessage(endpoint, message);
        });
        LOG.debug("unauthenticated device [clientId: {}] connected", endpoint.clientIdentifier());
        endpoint.accept(false);
    }

    private void handleEndpointConnectionWithAuthentication(final MqttEndpoint endpoint) {

        if (endpoint.auth() == null) {
            LOG.trace("device did not provide credentials in CONNECT packet");
            endpoint.reject(MqttConnectReturnCode.CONNECTION_REFUSED_BAD_USER_NAME_OR_PASSWORD);
            LOG.debug("connection request from device [clientId: {}] rejected [cause: {}]",
                    endpoint.clientIdentifier(), MqttConnectReturnCode.CONNECTION_REFUSED_BAD_USER_NAME_OR_PASSWORD);

        } else {

            final DeviceCredentials credentials = getCredentials(endpoint.auth());

            if (credentials == null) {
                endpoint.reject(MqttConnectReturnCode.CONNECTION_REFUSED_BAD_USER_NAME_OR_PASSWORD);
                LOG.debug("cannot authenticate device [clientId: {}] rejected [cause: {}]",
                        endpoint.clientIdentifier(), MqttConnectReturnCode.CONNECTION_REFUSED_BAD_USER_NAME_OR_PASSWORD);
            } else {
                getCredentialsAuthProvider().authenticate(credentials, attempt -> {
                    if (attempt.failed()) {

                        LOG.debug("cannot authenticate device [tenant-id: {}, auth-id: {}, cause: {}]",
                                credentials.getTenantId(), credentials.getAuthId(), attempt.cause().getMessage());
                        endpoint.reject(MqttConnectReturnCode.CONNECTION_REFUSED_NOT_AUTHORIZED);

                    } else {

                        final Device authenticatedDevice = attempt.result();
                        LOG.debug("successfully authenticated device [tenant-id: {}, auth-id: {}, device-id: {}]",
                                authenticatedDevice.getTenantId(), credentials.getAuthId(), authenticatedDevice.getDeviceId());
                        onAuthenticationSuccess(endpoint, authenticatedDevice);
                    }
                });
                
            }
        }
    }

    private void onAuthenticationSuccess(final MqttEndpoint endpoint, final Device authenticatedDevice) {

        endpoint.closeHandler(v -> {
            LOG.debug("connection to device [tenant-id: {}, device-id: {}] closed",
                    authenticatedDevice.getTenantId(), authenticatedDevice.getDeviceId());
            if (registrationAssertions.remove(endpoint) != null) {
                LOG.trace("removed registration assertion for device [tenant-id: {}, device-id: {}]",
                        authenticatedDevice.getTenantId(), authenticatedDevice.getDeviceId());
            }
        });

        endpoint.publishHandler(message -> onAuthenticatedMessage(endpoint, message, authenticatedDevice));
        endpoint.accept(false);
    }

    /**
     * Processes a message that has been published via an unauthenticated connection.
     * <p>
     * Gets a sender for the message by means of {@link #getSender(MqttPublishMessage, ResourceIdentifier)}
     * and then delegates to the <em>publishMessage</em> method to forward the message downstream.
     * 
     * @param endpoint The connection over which the message has been received.
     * @param message The message received over the connection.
     */
    protected final void onUnauthenticatedMessage(final MqttEndpoint endpoint, final MqttPublishMessage message) {

        try {
            final ResourceIdentifier topic = ResourceIdentifier.fromString(message.topicName());
            final Future<MessageSender> sender = getSender(message, topic);
            publishMessage(endpoint, message, topic, sender, topic.getTenantId(), topic.getResourceId());
        } catch (IllegalArgumentException e) {
            LOG.debug("discarding message published to unsupported topic [{}]", message.topicName());
        }
    }

    /**
     * Processes a message that has been published via an authenticated connection.
     * <p>
     * Gets a sender for the message by means of {@link #getSender(MqttPublishMessage, ResourceIdentifier, Device)}
     * and then delegates to the <em>publishMessage</em> method to forward the message downstream.
     * 
     * @param endpoint The connection over which the message has been received.
     * @param message The message received over the connection.
     * @param authenticatedDevice The authenticated device that has published the message.
     */
    protected final void onAuthenticatedMessage(final MqttEndpoint endpoint, final MqttPublishMessage message,
            final Device authenticatedDevice) {

        try {
            final ResourceIdentifier topic = ResourceIdentifier.fromString(message.topicName());
            final Future<MessageSender> sender = getSender(message, topic, authenticatedDevice);
            publishMessage(endpoint, message, topic, sender, authenticatedDevice.getTenantId(), authenticatedDevice.getDeviceId());
        } catch (final IllegalArgumentException e) {
            LOG.debug("discarding message published to unsupported topic [{}]", message.topicName());
        }
    }

    private final void publishMessage(final MqttEndpoint endpoint, final MqttPublishMessage message,
            final ResourceIdentifier topic, final Future<MessageSender> sender, final String tenantId, final String deviceId) {

        LOG.trace("received message for device [tenantId: {}, deviceId: {}, topic: {}, QoS: {}]",
                tenantId, deviceId, message.topicName(), message.qosLevel());

        final Future<String> assertionTracker = getRegistrationAssertion(endpoint, tenantId, deviceId);

        CompositeFuture.all(assertionTracker, sender).compose(ok -> {
            return doUploadMessage(deviceId, assertionTracker.result(), endpoint, message,
                    sender.result());
        }).map(s -> {
            LOG.trace("successfully processed message for device [tenantId: {}, deviceId: {}, topic: {}, QoS: {}]",
                    tenantId, deviceId,
                    topic, message.qosLevel());
            metrics.incrementProcessedMqttMessages(topic.getEndpoint(), tenantId);
            return null;
        }).otherwise(f -> {
            LOG.debug("cannot process message for device [tenantId: {}, deviceId: {}, topic: {}, QoS: {}, cause: {}]",
                    tenantId, deviceId,
                    topic, message.qosLevel(), f.getMessage());
            if (!ClientErrorException.class.isInstance(f)) {
                metrics.incrementUndeliverableMqttMessages(topic.getEndpoint(), tenantId);
            }
            return null;
        });
    }

    Future<Void> doUploadMessage(final String deviceId, final String registrationAssertion, final MqttEndpoint endpoint, final MqttPublishMessage message,
            final MessageSender sender) {

        Future<Void> result = Future.future();
        boolean accepted = sender.send(deviceId, message.payload().getBytes(), CONTENT_TYPE_OCTET_STREAM, registrationAssertion, (messageId, delivery) -> {
            LOG.trace("delivery state updated [message ID: {}, new remote state: {}]", messageId, delivery.getRemoteState());
            if (message.qosLevel() == MqttQoS.AT_MOST_ONCE) {
                result.complete();
            } else if (message.qosLevel() == MqttQoS.AT_LEAST_ONCE) {
                if (Accepted.class.isInstance(delivery.getRemoteState())) {
                    // check that the remote MQTT client is still connected before sending PUBACK
                    if (endpoint.isConnected()) {
                        endpoint.publishAcknowledge(message.messageId());
                    }
                    result.complete();
                } else {
                    result.fail("message not accepted by remote");
                }
            }
        });
        if (!accepted) {
            result.fail("no credit available for sending message");
        }
        return result;
    }

    private Future<String> getRegistrationAssertion(final MqttEndpoint endpoint, final String tenantId, final String deviceId) {

        String token = registrationAssertions.get(endpoint);
        if (token != null && !RegistrationAssertionHelperImpl.isExpired(token, 10)) {
            return Future.succeededFuture(token);
        } else {
            registrationAssertions.remove(endpoint);
            return getRegistrationAssertion(tenantId, deviceId).map(t -> {
                // if the client closes the connection right after publishing the messages and before that
                // the registration assertion has been returned, avoid to put it into the map
                if (endpoint.isConnected()) {
                    LOG.trace("caching registration assertion for [tenantId: {}, deviceId: {}]",
                            tenantId, deviceId);
                    registrationAssertions.put(endpoint, t);
                }
                return t;
            });
        }
    }

    /**
     * Closes a connection to a client.
     * 
     * @param endpoint The connection to close.
     */
    protected final void close(final MqttEndpoint endpoint) {
        registrationAssertions.remove(endpoint);
        if (endpoint.isConnected()) {
            LOG.debug("closing connection with client [client ID: {}]", endpoint.clientIdentifier());
            endpoint.close();
        } else {
            LOG.debug("client has already closed connection");
        }
    }

    /**
     * Invoked before the connection with a client is closed.
     * <p>
     * This default implementation does nothing.
     * 
     * @param endpoint The connection to be closed.
     */
    protected void onClose(final MqttEndpoint endpoint) {
        // empty default implementation
    }

    /**
     * Extracts credentials from a client's MQTT <em>CONNECT</em> <packet.
     * <p>
     * This default implementation returns {@link UsernamePasswordCredentials} created
     * from the <em>username</em> and <em>password</em> fields of the <em>CONNECT</em> <packet.
     * <p>
     * Subclasses should override this method if the device uses credentials that do not
     * comply with the format expected by {@link UsernamePasswordCredentials}.
     * 
     * @param authInfo The authentication info provided by the device.
     * @return The credentials or {@code null} if the information provided by the device
     *         can not be processed.
     */
    protected DeviceCredentials getCredentials(final MqttAuth authInfo) {
        return UsernamePasswordCredentials.create(authInfo.userName(), authInfo.password(),
                getConfig().isSingleTenant());
    }

    /**
     * Gets a sender for forwarding a message that has been published by an unauthenticated device.
     * <p>
     * Subclasses may determine an appropriate sender based on e.g. the QoS of the message or the
     * semantics of the topic that the message has been published to.
     * 
     * @param message The message to get the sender for.
     * @param topic The topic that the message has been published to.
     * @return A future containing the sender.
     */
    protected abstract Future<MessageSender> getSender(final MqttPublishMessage message, final ResourceIdentifier topic);

    /**
     * Gets a sender for forwarding a message that has been published by an authenticated device.
     * <p>
     * Subclasses may determine an appropriate sender based on e.g. the QoS of the message or the
     * semantics of the topic that the message has been published to.
     * 
     * @param message The message to get the sender for.
     * @param topic The topic that the message has been published to.
     * @param authenticatedDevice The authenticated device that has published the message.
     * @return A future containing the sender.
     */
    protected abstract Future<MessageSender> getSender(final MqttPublishMessage message, final ResourceIdentifier topic,
            final Device authenticatedDevice);

}
