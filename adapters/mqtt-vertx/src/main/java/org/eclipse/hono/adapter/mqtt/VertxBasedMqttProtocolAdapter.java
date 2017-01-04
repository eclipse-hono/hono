/**
 * Copyright (c) 2016 Red Hat
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

import org.eclipse.hono.client.HonoClient;
import org.eclipse.hono.client.MessageSender;
import org.eclipse.hono.util.ResourceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.netty.handler.codec.mqtt.MqttConnectReturnCode;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
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
public class VertxBasedMqttProtocolAdapter extends AbstractVerticle {

    private static final Logger LOG = LoggerFactory.getLogger(VertxBasedMqttProtocolAdapter.class);

    private static final String CONTENT_TYPE_OCTET_STREAM = "application/octet-stream";
    private static final String TELEMETRY_ENDPOINT = "telemetry";
    private static final String EVENT_ENDPOINT = "event";

    @Value("${hono.mqtt.bindaddress:0.0.0.0}")
    private String bindAddress;

    @Value("${hono.mqtt.listenport:1883}")
    private int listenPort;

    @Autowired
    private HonoClient hono;
    private final BiConsumer<String, Handler<AsyncResult<MessageSender>>> eventSenderSupplier
            = (tenant, resultHandler) -> hono.getOrCreateEventSender(tenant, resultHandler);
    private final BiConsumer<String, Handler<AsyncResult<MessageSender>>> telemetrySenderSupplier
            = (tenant, resultHandler) -> hono.getOrCreateTelemetrySender(tenant, resultHandler);
    private MqttServer server;

    private void bindMqttServer(final Future<Void> startFuture) {

        MqttServerOptions options = new MqttServerOptions();
        options.setHost(this.bindAddress).setPort(this.listenPort);

        this.server = MqttServer.create(this.vertx, options);

        this.server
                .endpointHandler(this::handleEndpointConnection)
                .listen(done -> {

                    if (done.succeeded()) {
                        LOG.info("Hono MQTT adapter running on {}:{}", this.bindAddress, this.server.actualPort());
                        startFuture.complete();
                    } else {
                        LOG.error("error while starting up Hono MQTT adapter", done.cause());
                        startFuture.fail(done.cause());
                    }

                });

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
    public void start(Future<Void> startFuture) throws Exception {

        this.bindMqttServer(startFuture);
        this.connectToHono(null);
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
        serverTracker.compose(d -> {
            if (this.hono != null) {
                this.hono.shutdown(shutdownTracker.completer());
            } else {
                shutdownTracker.complete();
            }
        }, shutdownTracker);
    }

    private void handleEndpointConnection(final MqttEndpoint endpoint) {

        LOG.info("Connection request from client {}", endpoint.clientIdentifier());

        if (!this.isConnected()) {
            endpoint.reject(MqttConnectReturnCode.CONNECTION_REFUSED_SERVER_UNAVAILABLE);

        } else {

            endpoint.publishHandler(message -> {

                LOG.debug("Just received message on [{}] payload [{}] with QoS [{}]", message.topicName(), message.payload().toString(Charset.defaultCharset()), message.qosLevel());

                try {

                    ResourceIdentifier resource = ResourceIdentifier.fromString(message.topicName());

                    // if MQTT client doesn't specify device_id then closing connection (MQTT has now way for errors)
                    if (resource.getResourceId() == null) {
                        endpoint.close();
                    } else {

                        // check that MQTT client tries to publish on topic with device_id same as on connection
                        if (resource.getResourceId().equals(endpoint.clientIdentifier())) {

                            if (resource.getEndpoint().equals(TELEMETRY_ENDPOINT)) {

                                this.doUploadMessages(resource.getTenantId(), endpoint, message, this.telemetrySenderSupplier);

                            } else if (resource.getEndpoint().equals(EVENT_ENDPOINT)) {

                                this.doUploadMessages(resource.getTenantId(), endpoint, message, this.eventSenderSupplier);

                            } else {
                                // MQTT client is trying to publish on a not supported endpoint
                                endpoint.close();
                            }

                        } else {
                            // MQTT client is trying to publish on a different device_id used on connection (MQTT has now way for errors)
                            endpoint.close();
                        }

                    }

                } catch (IllegalArgumentException e) {

                    // MQTT client is trying to publish on invalid topic; it does not contain at least two segments
                    endpoint.close();
                }

            });

            endpoint.accept(false);
        }
    }

    private void doUploadMessages(final String tenant, final MqttEndpoint endpoint, final MqttPublishMessage message, final BiConsumer<String, Handler<AsyncResult<MessageSender>>> senderSupplier) {

        senderSupplier.accept(tenant, createAttempt -> {

            if (createAttempt.succeeded()) {

                MessageSender sender = createAttempt.result();

                // sending message only when the "flow" is handled and credits are available
                // otherwise send will never happen due to no credits
                if (!sender.sendQueueFull()) {
                    this.sendToHono(endpoint, sender, message);
                } else {
                    sender.sendQueueDrainHandler(v -> {
                        this.sendToHono(endpoint, sender, message);
                    });
                }

            } else {

                // we don't have a connection to Hono ? MQTT no other way to close connection
                endpoint.close();
            }

        });
    }

    private void sendToHono(final MqttEndpoint endpoint, final MessageSender sender, final MqttPublishMessage message) {

        boolean accepted = sender.send(endpoint.clientIdentifier(), message.payload().getBytes(), CONTENT_TYPE_OCTET_STREAM);
        if (accepted && message.qosLevel() == MqttQoS.AT_LEAST_ONCE) {
            endpoint.publishAcknowledge(message.messageId());
        }
    }

    private boolean isConnected() {
        return this.hono != null && this.hono.isConnected();
    }
}
