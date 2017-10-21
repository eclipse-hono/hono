/**
 * Copyright (c) 2016, 2017 Red Hat and others
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Red Hat - initial creation
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
import io.vertx.core.AsyncResult;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.mqtt.MqttEndpoint;
import io.vertx.mqtt.MqttServer;
import io.vertx.mqtt.MqttServerOptions;
import io.vertx.mqtt.messages.MqttPublishMessage;

/**
 * A Vert.x based Hono protocol adapter for accessing Hono's Telemetry API using MQTT.
 */
public class VertxBasedMqttProtocolAdapter extends AbstractProtocolAdapterBase<ProtocolAdapterProperties> {

    private static final Logger LOG = LoggerFactory.getLogger(VertxBasedMqttProtocolAdapter.class);

    private static final String CONTENT_TYPE_OCTET_STREAM = "application/octet-stream";
    private static final String TELEMETRY_ENDPOINT = "telemetry";
    private static final String EVENT_ENDPOINT = "event";
    private static final int IANA_MQTT_PORT = 1883;
    private static final int IANA_SECURE_MQTT_PORT = 8883;

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

    void handleEndpointConnection(final MqttEndpoint endpoint) {

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

    private void handleEndpointConnectionWithoutAuthentication(final MqttEndpoint endpoint) {
        endpoint.closeHandler(v -> {
            LOG.debug("connection closed with client [clientId: {}]", endpoint.clientIdentifier());
            if (registrationAssertions.remove(endpoint) != null)
                LOG.trace("removed registration assertion for client [clientId: {}]", endpoint.clientIdentifier());
        });
        endpoint.publishHandler(message -> {
            final ResourceIdentifier resource = ResourceIdentifier.fromString(message.topicName());

            if (resource.getResourceId() == null) {
                // if MQTT client doesn't specify device-id then closing connection (MQTT has no way for errors)
                close(endpoint);
            } else if (resource.getTenantId() == null) {
                // if MQTT client doesn't specify tenant-id then closing connection (MQTT has no way for errors)
                close(endpoint);
            } else {
                publishMessage(endpoint, resource.getTenantId(), resource.getResourceId(), message, resource);
            }
        });
        LOG.debug("successfully connected with client [clientId: {}]", endpoint.clientIdentifier());
        endpoint.accept(false);
    }

    private void handleEndpointConnectionWithAuthentication(final MqttEndpoint endpoint) {
        // check credentials for valid authentication
        // so far, only hashed-password supported, more to follow
        if (endpoint.auth() == null) {
            LOG.trace("no auth information in endpoint found");
            endpoint.reject(MqttConnectReturnCode.CONNECTION_REFUSED_BAD_USER_NAME_OR_PASSWORD);
            LOG.debug("connection request from client [clientId: {}] rejected {}",
                    endpoint.clientIdentifier(), MqttConnectReturnCode.CONNECTION_REFUSED_BAD_USER_NAME_OR_PASSWORD);
            return;
        }

        UsernamePasswordCredentials credentials = UsernamePasswordCredentials.create(endpoint.auth().userName(),
                endpoint.auth().password(), getConfig().isSingleTenant());

        if (credentials == null) {
            endpoint.reject(MqttConnectReturnCode.CONNECTION_REFUSED_BAD_USER_NAME_OR_PASSWORD);
            LOG.debug("connection request from client [clientId: {}] rejected [cause: {}]",
                    endpoint.clientIdentifier(), MqttConnectReturnCode.CONNECTION_REFUSED_BAD_USER_NAME_OR_PASSWORD);
        } else {
            getCredentialsAuthProvider().authenticate(credentials, attempt -> handleCredentialsResult(attempt, endpoint, credentials));
        }

    }

    private void handleCredentialsResult(final AsyncResult<Device> attempt, final MqttEndpoint endpoint, final DeviceCredentials credentials) {

        if (attempt.succeeded()) {
            final String deviceId = attempt.result().getDeviceId();
            LOG.debug("successfully authenticated device [tenant-id: {}, auth-id: {}, device-id: {}]",
                    credentials.getTenantId(), credentials.getAuthId(), deviceId);

            endpoint.publishHandler(message -> {

                try {
                    final ResourceIdentifier resource = ResourceIdentifier.fromString(message.topicName());

                    // check that the device publishes to its device-id
                    if (!validateCredentialsWithTopicStructure(resource, credentials.getTenantId(), deviceId)) {

                        // if MQTT client does not conform to the topic structure, close the connection (MQTT has no way for errors)
                        endpoint.close();
                    } else {
                        // the payload is ALWAYS published to the deviceId downstream
                        publishMessage(endpoint, credentials.getTenantId(), deviceId, message, resource);
                    }
                } catch (final IllegalArgumentException e) {
                    LOG.debug("discarding message with malformed topic ...");
                }
            });

            endpoint.closeHandler(v -> {
                LOG.debug("connection closed to device [tenant-id: {}, auth-id: {}, device-id: {}]",
                        credentials.getTenantId(), credentials.getAuthId(), deviceId);
                if (registrationAssertions.remove(endpoint) != null) {
                    LOG.trace("removed registration assertion for device [tenant-id: {}, auth-id: {}, device-id: {}]",
                            credentials.getTenantId(), credentials.getAuthId(), deviceId);
                }
            });

            endpoint.accept(false);

        } else {
            LOG.debug("authentication failed for device [tenant-id: {}, auth-id: {}, cause: {}]",
                    credentials.getTenantId(), credentials.getAuthId(), attempt.cause().getMessage());
            endpoint.reject(MqttConnectReturnCode.CONNECTION_REFUSED_NOT_AUTHORIZED);
        }
    }

    private void publishMessage(final MqttEndpoint endpoint, final String tenantId, final String deviceId,
            final MqttPublishMessage message, final ResourceIdentifier resource) {

        LOG.trace("received message for device [tenantId: {}, deviceId: {}, topic: {}, QoS: {}]",
                tenantId, deviceId, message.topicName(), message.qosLevel());

        try {
            final Future<String> assertionTracker = getRegistrationAssertion(endpoint, tenantId, deviceId);
            final Future<MessageSender> senderTracker = getSenderTracker(message, resource, tenantId);

            Future<Void> messageTracker = Future.future();
            messageTracker.setHandler(s -> {
                if (s.failed()) {
                    LOG.debug("cannot process message for device [tenantId: {}, deviceId: {}, topic: {}, QoS: {}, cause: {}]",
                            tenantId, deviceId,
                            resource, message.qosLevel(), s.cause().getMessage());
                    if (!ClientErrorException.class.isInstance(s.cause())) {
                        metrics.incrementUndeliverableMqttMessages(resource.getEndpoint(), tenantId);
                    }
                } else {
                    LOG.trace("successfully processed message for device [tenantId: {}, deviceId: {}, topic: {}, QoS: {}]",
                            tenantId, deviceId,
                            resource, message.qosLevel());
                    metrics.incrementProcessedMqttMessages(resource.getEndpoint(), tenantId);
                }
            });

            CompositeFuture.all(assertionTracker, senderTracker).compose(ok -> {
                doUploadMessage(deviceId, assertionTracker.result(), endpoint, message,
                        senderTracker.result(), messageTracker);
            }, messageTracker);

        } catch (IllegalArgumentException e) {

            // MQTT client is trying to publish on invalid topic; it does not contain at least two segments
            LOG.debug("client for [tenantId: {}, deviceId: {}] tries to publish on unsupported topic",
                    tenantId, deviceId);
            close(endpoint);
        }
    }

    private Future<MessageSender> getSenderTracker(final MqttPublishMessage message, final ResourceIdentifier resource,
                                                   final String tenantId) {

        if (resource.getEndpoint().equals(TELEMETRY_ENDPOINT)) {
            if (!MqttQoS.AT_MOST_ONCE.equals(message.qosLevel())) {
                // client tries to send telemetry message using QoS 1 or 2
                return Future.failedFuture("Only QoS 0 supported for telemetry messages");
            } else {
                return getTelemetrySender(tenantId);
            }
        } else if (resource.getEndpoint().equals(EVENT_ENDPOINT)) {
            if (!MqttQoS.AT_LEAST_ONCE.equals(message.qosLevel())) {
                // client tries to send event message using QoS 0 or 2
                return Future.failedFuture("Only QoS 1 supported for event messages");
            } else {
                return getEventSender(tenantId);
            }
        } else {
            // MQTT client is trying to publish on a not supported endpoint
            LOG.debug("no such endpoint [{}]", resource.getEndpoint());
            return Future.failedFuture("no such endpoint");
        }
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

    private void close(final MqttEndpoint endpoint) {
        registrationAssertions.remove(endpoint);
        if (endpoint.isConnected()) {
            LOG.debug("closing connection with client [client ID: {}]", endpoint.clientIdentifier());
            endpoint.close();
        } else {
            LOG.debug("client has already closed connection");
        }
    }

    void doUploadMessage(final String deviceId, final String registrationAssertion, final MqttEndpoint endpoint, final MqttPublishMessage message,
            final MessageSender sender, final Future<Void> uploadHandler) {

        boolean accepted = sender.send(deviceId, message.payload().getBytes(), CONTENT_TYPE_OCTET_STREAM, registrationAssertion, (messageId, delivery) -> {
            LOG.trace("delivery state updated [message ID: {}, new remote state: {}]", messageId, delivery.getRemoteState());
            if (message.qosLevel() == MqttQoS.AT_MOST_ONCE) {
                uploadHandler.complete();
            } else if (message.qosLevel() == MqttQoS.AT_LEAST_ONCE) {
                if (Accepted.class.isInstance(delivery.getRemoteState())) {
                    // check that the remote MQTT client is still connected before sending PUBACK
                    if (endpoint.isConnected()) {
                        endpoint.publishAcknowledge(message.messageId());
                    }
                    uploadHandler.complete();
                } else {
                    uploadHandler.fail("message not accepted by remote");
                }
            }
        });
        if (!accepted) {
            uploadHandler.fail("no credit available for sending message");
        }
    }
}
