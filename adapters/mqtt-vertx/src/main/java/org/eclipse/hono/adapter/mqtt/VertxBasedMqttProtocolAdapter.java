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
import java.util.function.BiConsumer;
import java.util.Objects;

import org.eclipse.hono.client.HonoClient;
import org.eclipse.hono.client.MessageSender;
import org.eclipse.hono.service.AbstractServiceBase;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.ResourceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import io.netty.handler.codec.mqtt.MqttConnectReturnCode;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.vertx.core.AsyncResult;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.mqtt.MqttEndpoint;
import io.vertx.mqtt.MqttServer;
import io.vertx.mqtt.MqttServerOptions;
import io.vertx.mqtt.messages.MqttPublishMessage;
import io.vertx.proton.ProtonClientOptions;

/**
 * A Vert.x based Hono protocol adapter for accessing Hono's Telemetry API using MQTT.
 */
@Component
@Scope("prototype")
public class VertxBasedMqttProtocolAdapter extends AbstractServiceBase {

    private static final Logger LOG = LoggerFactory.getLogger(VertxBasedMqttProtocolAdapter.class);

    private static final String CONTENT_TYPE_OCTET_STREAM = "application/octet-stream";
    private static final String TELEMETRY_ENDPOINT = "telemetry";
    private static final String EVENT_ENDPOINT = "event";
    private static final int IANA_MQTT_PORT = 1883;
    private static final int IANA_SECURE_MQTT_PORT = 8883;

    private HonoClient hono;
    private final BiConsumer<String, Handler<AsyncResult<MessageSender>>> eventSenderSupplier
            = (tenant, resultHandler) -> hono.getOrCreateEventSender(tenant, resultHandler);
    private final BiConsumer<String, Handler<AsyncResult<MessageSender>>> telemetrySenderSupplier
            = (tenant, resultHandler) -> hono.getOrCreateTelemetrySender(tenant, resultHandler);
    private MqttServer server;
    private MqttServer insecureServer;

    @Override
    public int getPortDefaultValue() {
        return IANA_SECURE_MQTT_PORT;
    }

    public int getInsecurePortDefaultValue() {
        return IANA_MQTT_PORT;
    }

    @Override
    public int getPort() {
        if (server != null) {
            return server.actualPort();
        } else if (isSecurePortEnabled()) {
            return getConfig().getPort(getPortDefaultValue());
        } else {
            return Constants.PORT_UNCONFIGURED;
        }
    }

    @Override
    public int getInsecurePort() {
        if (insecureServer != null) {
            return insecureServer.actualPort();
        } else if (isInsecurePortEnabled()) {
            return getConfig().getInsecurePort(getInsecurePortDefaultValue());
        } else {
            return Constants.PORT_UNCONFIGURED;
        }
    }

    /**
     * Sets the client to use for connecting to the Hono server.
     * 
     * @param honoClient The client.
     * @throws NullPointerException if hono client is {@code null}.
     */
    @Autowired
    public void setHonoClient(final HonoClient honoClient) {
        this.hono = Objects.requireNonNull(honoClient);
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

    private void connectToHono(final Handler<AsyncResult<HonoClient>> connectHandler) {

        ProtonClientOptions options = new ProtonClientOptions()
                .setReconnectAttempts(-1)
                .setReconnectInterval(200); // try to re-connect every 200 ms
        this.hono.connect(options, connectAttempt -> {
            if (connectHandler != null) {
                connectHandler.handle(connectAttempt);
            }
        });
    }

    @Override
    public void start(final Future<Void> startFuture) throws Exception {

        if (hono == null) {
            startFuture.fail("Hono client must be set");
        } else {
            LOG.info("limiting size of inbound message payload to {} bytes", getConfig().getMaxPayloadSize());
            checkPortConfiguration()
                .compose(s -> bindSecureMqttServer())
                .compose(secureServer -> {
                    this.server = secureServer;
                    return bindInsecureMqttServer();
                })
                .compose(insecureServer -> {
                    this.insecureServer = insecureServer;
                    this.connectToHono(null);
                    startFuture.complete();
                }, startFuture);
        }
    }

    @Override
    public void stop(Future<Void> stopFuture) throws Exception {

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
                if (this.hono != null) {
                    this.hono.shutdown(shutdownTracker.completer());
                } else {
                    shutdownTracker.complete();
                }
            }, shutdownTracker);
    }

    private void handleEndpointConnection(final MqttEndpoint endpoint) {

        LOG.info("Connection request from client {}", endpoint.clientIdentifier());

        if (!hono.isConnected()) {
            endpoint.reject(MqttConnectReturnCode.CONNECTION_REFUSED_SERVER_UNAVAILABLE);

        } else {
            endpoint.publishHandler(message -> {

                LOG.debug("Just received message on [{}] payload [{}] with QoS [{}]", message.topicName(), message.payload().toString(Charset.defaultCharset()), message.qosLevel());

                try {

                    ResourceIdentifier resource = ResourceIdentifier.fromString(message.topicName());

                    // if MQTT client doesn't specify device_id then closing connection (MQTT has now way for errors)
                    if (resource.getResourceId() == null) {
                        close(endpoint);
                    } else {

                        // check that MQTT client tries to publish on topic with device_id same as on connection
                        if (resource.getResourceId().equals(endpoint.clientIdentifier())) {

                            if (resource.getEndpoint().equals(TELEMETRY_ENDPOINT)) {

                                this.doUploadMessages(resource.getTenantId(), endpoint, message, this.telemetrySenderSupplier);

                            } else if (resource.getEndpoint().equals(EVENT_ENDPOINT)) {

                                this.doUploadMessages(resource.getTenantId(), endpoint, message, this.eventSenderSupplier);

                            } else {
                                // MQTT client is trying to publish on a not supported endpoint
                                LOG.debug("no such endpoint [{}]", resource.getEndpoint());
                                close(endpoint);
                            }

                        } else {
                            // MQTT client is trying to publish on a different device_id used on connection (MQTT has now way for errors)
                            LOG.debug("client [ID: {}] not authorized to publish data for device [{}]", endpoint.clientIdentifier(), resource.getResourceId());
                            close(endpoint);
                        }

                    }

                } catch (IllegalArgumentException e) {

                    // MQTT client is trying to publish on invalid topic; it does not contain at least two segments
                    LOG.debug("client [ID: {}] tries to publish on unsupported topic", endpoint.clientIdentifier());
                    close(endpoint);
                }

            });
            endpoint.accept(false);
        }
    }

    private static void close(final MqttEndpoint endpoint) {
        if (endpoint.isConnected()) {
            LOG.debug("closing connection with client [client ID: {}]", endpoint.clientIdentifier());
            endpoint.close();
        } else {
            LOG.debug("client has already closed connection");
        }
    }

    private void doUploadMessages(final String tenant, final MqttEndpoint endpoint, final MqttPublishMessage message,
            final BiConsumer<String, Handler<AsyncResult<MessageSender>>> senderSupplier) {

        senderSupplier.accept(tenant, createAttempt -> {

            if (createAttempt.succeeded()) {

                MessageSender sender = createAttempt.result();

                boolean accepted = sender.send(endpoint.clientIdentifier(), message.payload().getBytes(), CONTENT_TYPE_OCTET_STREAM);
                if (accepted) {
                    if (message.qosLevel() == MqttQoS.AT_LEAST_ONCE && endpoint.isConnected()) {
                        endpoint.publishAcknowledge(message.messageId());
                    }
                } else {
                    LOG.debug("no credit available for sending message");
                    close(endpoint);
                }

            } else {

                // we don't have a connection to Hono ? MQTT no other way to close connection
                LOG.debug("no connection to Hono server");
                close(endpoint);
            }

        });
    }
}
