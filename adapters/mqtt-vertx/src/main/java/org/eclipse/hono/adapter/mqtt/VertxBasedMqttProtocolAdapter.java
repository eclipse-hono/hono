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

import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;

import io.vertx.core.Handler;
import org.apache.qpid.proton.amqp.messaging.Accepted;
import org.eclipse.hono.adapter.mqtt.credentials.MqttUsernamePassword;
import org.eclipse.hono.client.MessageSender;
import org.eclipse.hono.config.ServiceConfigProperties;
import org.eclipse.hono.service.AbstractProtocolAdapterBase;
import org.eclipse.hono.service.registration.RegistrationAssertionHelperImpl;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.ResourceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.handler.codec.mqtt.MqttConnectReturnCode;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.mqtt.MqttEndpoint;
import io.vertx.mqtt.MqttServer;
import io.vertx.mqtt.MqttServerOptions;
import io.vertx.mqtt.messages.MqttPublishMessage;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * A Vert.x based Hono protocol adapter for accessing Hono's Telemetry API using MQTT.
 */
public class VertxBasedMqttProtocolAdapter extends AbstractProtocolAdapterBase<ServiceConfigProperties> {

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

    private Future<MqttServer> bindSecureMqttServer() {

        if (isSecurePortEnabled()) {
            MqttServerOptions options = new MqttServerOptions();
            options
                .setHost(getConfig().getBindAddress())
                .setPort(determineSecurePort())
                .setMaxMessageSize(getConfig().getMaxPayloadSize());
            addTlsKeyCertOptions(options);
            addTlsTrustOptions(options);
            return bindMqttServer(options);
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
            return bindMqttServer(options);
        } else {
            return Future.succeededFuture();
        }
    }

    private Future<MqttServer> bindMqttServer(final MqttServerOptions options) {

        final Future<MqttServer> result = Future.future();
        MqttServer server = MqttServer.create(this.vertx, options);

        server
            .endpointHandler(this::handleEndpointConnection)
            .listen(done -> {

                if (done.succeeded()) {
                    LOG.info("Hono MQTT protocol adapter running on {}:{}", getConfig().getBindAddress(), server.actualPort());
                    result.complete(server);
                } else {
                    LOG.error("error while starting up Hono MQTT adapter", done.cause());
                    result.fail(done.cause());
                }

            });
        return result;
    }

    @Override
    public void doStart(final Future<Void> startFuture) {

        LOG.info("limiting size of inbound message payload to {} bytes", getConfig().getMaxPayloadSize());
        checkPortConfiguration()
        .compose(s -> bindSecureMqttServer())
        .compose(secureServer -> {
            this.server = secureServer;
            return bindInsecureMqttServer();
        }).compose(insecureServer -> {
            this.insecureServer = insecureServer;
            connectToMessaging(null);
            connectToDeviceRegistration(null);
            connectToCredentialsService(null);
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

    private void handleEndpointConnection(final MqttEndpoint endpoint) {

        LOG.info("connection request from client {}", endpoint.clientIdentifier());

        if (!isConnected()) {
            endpoint.reject(MqttConnectReturnCode.CONNECTION_REFUSED_SERVER_UNAVAILABLE);

        } else {
            endpoint.publishHandler(createMqttPublishMessageHandler(endpoint));

            endpoint.closeHandler(v -> {
                LOG.debug("connection closed with client [{}]", endpoint.clientIdentifier());
                if (registrationAssertions.remove(endpoint) != null)
                    LOG.trace("removed registration assertion for client [{}]", endpoint.clientIdentifier());
            });

            // check credentials for valid authentication
            // so far, only hashed-password supported, more to follow
            try {

                MqttUsernamePassword authObject = MqttUsernamePassword.create(endpoint,
                        getConfig().isSingleTenant());

                Future<Void> validationTracker = Future.future();
                validationTracker.setHandler(result -> {
                    if (result.failed()) {
                        endpoint.reject(MqttConnectReturnCode.CONNECTION_REFUSED_NOT_AUTHORIZED);
                    }
                });
                validateCredentialsForDevice(authObject.getTenantId(), authObject.getType(), authObject.getAuthId(),
                        authObject.getPassword()).compose(
                                deviceId -> {
                                    LOG.trace("successfully authenticated device id <{}>", deviceId);
                                    endpoint.accept(false);
                                }, validationTracker);
            } catch (IllegalArgumentException e) {
                LOG.warn(e.getMessage());
                endpoint.reject(MqttConnectReturnCode.CONNECTION_REFUSED_BAD_USER_NAME_OR_PASSWORD);
            }
        }
    }

    private Handler<MqttPublishMessage> createMqttPublishMessageHandler(final MqttEndpoint endpoint) {
        return message -> {

            LOG.trace("received message [client ID: {}, topic: {}, QoS: {}, payload {}]", endpoint.clientIdentifier(), message.topicName(),
                    message.qosLevel(), message.payload().toString(Charset.defaultCharset()));

            try {

                final ResourceIdentifier resource = ResourceIdentifier.fromString(message.topicName());

                // if MQTT client doesn't specify device_id then closing connection (MQTT has now way for errors)
                if (resource.getResourceId() == null) {
                    close(endpoint);
                } else {

                    Future<Void> messageTracker = Future.future();
                    messageTracker.setHandler(s -> {
                        if (s.failed()) {
                            LOG.debug("cannot process message [client ID: {}, topic: {}, QoS: {}]: {}", endpoint.clientIdentifier(),
                                    resource, message.qosLevel(), s.cause().getMessage());
                            metrics.incrementUndeliverableMqttMessages(resource.getEndpoint(), resource.getTenantId());
                            close(endpoint);
                        } else {
                            LOG.trace("successfully processed message [client ID: {}, topic: {}, QoS: {}]", endpoint.clientIdentifier(),
                                    resource, message.qosLevel());
                            metrics.incrementProcessedMqttMessages(resource.getEndpoint(), resource.getTenantId());
                        }
                    });

                    // check that MQTT client tries to publish on topic with device_id same as on connection
                    if (resource.getResourceId().equals(endpoint.clientIdentifier())) {

                        Future<String> assertionTracker = getRegistrationAssertion(endpoint, resource);
                        Future<MessageSender> senderTracker = getSenderTracker(message, resource);

                        CompositeFuture.all(assertionTracker, senderTracker).compose(ok -> {
                            doUploadMessage(resource.getTenantId(), assertionTracker.result(), endpoint, message,
                                    senderTracker.result(), messageTracker);
                        }, messageTracker);
                    } else {
                        // MQTT client is trying to publish on a different device_id used on connection (MQTT has no way for errors)
                        messageTracker.fail("client not authorized");
                    }
                }

            } catch (IllegalArgumentException e) {

                // MQTT client is trying to publish on invalid topic; it does not contain at least two segments
                LOG.debug("client [ID: {}] tries to publish on unsupported topic", endpoint.clientIdentifier());
                close(endpoint);
            }
        };
    }

    private Future<MessageSender> getSenderTracker(final MqttPublishMessage message, final ResourceIdentifier resource) {

        if (resource.getEndpoint().equals(TELEMETRY_ENDPOINT)) {
            if (!MqttQoS.AT_MOST_ONCE.equals(message.qosLevel())) {
                // client tries to send telemetry message using QoS 1 or 2
                return Future.failedFuture("Only QoS 0 supported for telemetry messages");
            } else {
                return getTelemetrySender(resource.getTenantId());
            }
        } else if (resource.getEndpoint().equals(EVENT_ENDPOINT)) {
            if (!MqttQoS.AT_LEAST_ONCE.equals(message.qosLevel())) {
                // client tries to send event message using QoS 0 or 2
                return Future.failedFuture("Only QoS 1 supported for event messages");
            } else {
                return getEventSender(resource.getTenantId());
            }
        } else {
            // MQTT client is trying to publish on a not supported endpoint
            LOG.debug("no such endpoint [{}]", resource.getEndpoint());
            return Future.failedFuture("no such endpoint");
        }
    }

    private Future<String> getRegistrationAssertion(final MqttEndpoint endpoint, final ResourceIdentifier address) {
        String token = registrationAssertions.get(endpoint);
        if (token != null && !RegistrationAssertionHelperImpl.isExpired(token, 10)) {
            return Future.succeededFuture(token);
        } else {
            registrationAssertions.remove(endpoint);
            Future<String> result = Future.future();
            getRegistrationAssertion(address.getTenantId(), address.getResourceId()).compose(t -> {
                // if the client closes the connection right after publishing the messages and before that
                // the registration assertion has been returned, avoid to put it into the map
                if (endpoint.isConnected()) {
                    LOG.trace("caching registration assertion for client [{}]", endpoint.clientIdentifier());
                    registrationAssertions.put(endpoint, t);
                }
                result.complete(t);
            }, result);
            return result;
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

    private void doUploadMessage(final String tenant, final String registrationAssertion, final MqttEndpoint endpoint, final MqttPublishMessage message,
            final MessageSender sender, final Future<Void> uploadHandler) {

        boolean accepted = sender.send(endpoint.clientIdentifier(), message.payload().getBytes(), CONTENT_TYPE_OCTET_STREAM, registrationAssertion, (messageId, delivery) -> {
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
