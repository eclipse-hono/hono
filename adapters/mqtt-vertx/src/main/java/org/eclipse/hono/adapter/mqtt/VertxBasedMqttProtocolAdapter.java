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
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.hono.client.HonoClient;
import org.eclipse.hono.client.MessageSender;
import org.eclipse.hono.config.HonoConfigProperties;
import org.eclipse.hono.util.ResourceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
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
@Scope("prototype")
public class VertxBasedMqttProtocolAdapter extends AbstractVerticle {

    private static final Logger LOG = LoggerFactory.getLogger(VertxBasedMqttProtocolAdapter.class);

    private static final String CONTENT_TYPE_OCTET_STREAM = "application/octet-stream";
    private static final String TELEMETRY_ENDPOINT = "telemetry";
    private static final String EVENT_ENDPOINT = "event";

    private HonoClient hono;
    private HonoConfigProperties config;
    private final BiConsumer<String, Handler<AsyncResult<MessageSender>>> eventSenderSupplier
            = (tenant, resultHandler) -> hono.getOrCreateEventSender(tenant, resultHandler);
    private final BiConsumer<String, Handler<AsyncResult<MessageSender>>> telemetrySenderSupplier
            = (tenant, resultHandler) -> hono.getOrCreateTelemetrySender(tenant, resultHandler);
    private MqttServer server;

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

    /**
     * Sets configuration properties.
     * 
     * @param properties The configuration properties to use.
     * @throws NullPointerException if properties is {@code null}.
     */
    @Autowired
    public void setConfig(final HonoConfigProperties properties) {
        this.config = Objects.requireNonNull(properties);
    }

    private void bindMqttServer(final Future<Void> startFuture) {

        MqttServerOptions options = new MqttServerOptions();
        options.setHost(config.getBindAddress()).setPort(config.getPort());

        this.server = MqttServer.create(this.vertx, options);

        this.server
                .endpointHandler(this::handleEndpointConnection)
                .listen(done -> {

                    if (done.succeeded()) {
                        LOG.info("Hono MQTT adapter running on {}:{}", config.getBindAddress(), this.server.actualPort());
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

        if (hono == null) {
            startFuture.fail("Hono client must be set");
        } else {
            if (LOG.isWarnEnabled()) {
                StringBuilder b = new StringBuilder()
                        .append("MQTT protocol adapter does not yet support limiting the incoming message size ")
                        .append("via the maxPayloadSize property. Default max payload size is 8kb.");
                LOG.warn(b.toString());
            }
            this.bindMqttServer(startFuture);
            this.connectToHono(null);
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
            final AtomicBoolean clientConnected = new AtomicBoolean(false);
            endpoint
              .closeHandler(clientDisconnected -> {
                  clientConnected.compareAndSet(true, false);
              })
              .publishHandler(message -> {

                LOG.debug("Just received message on [{}] payload [{}] with QoS [{}]", message.topicName(), message.payload().toString(Charset.defaultCharset()), message.qosLevel());

                try {

                    ResourceIdentifier resource = ResourceIdentifier.fromString(message.topicName());

                    // if MQTT client doesn't specify device_id then closing connection (MQTT has now way for errors)
                    if (resource.getResourceId() == null) {
                        close(endpoint, clientConnected);
                    } else {

                        // check that MQTT client tries to publish on topic with device_id same as on connection
                        if (resource.getResourceId().equals(endpoint.clientIdentifier())) {

                            if (resource.getEndpoint().equals(TELEMETRY_ENDPOINT)) {

                                this.doUploadMessages(resource.getTenantId(), endpoint, message,
                                        this.telemetrySenderSupplier, clientConnected);

                            } else if (resource.getEndpoint().equals(EVENT_ENDPOINT)) {

                                this.doUploadMessages(resource.getTenantId(), endpoint, message,
                                        this.eventSenderSupplier, clientConnected);

                            } else {
                                // MQTT client is trying to publish on a not supported endpoint
                                LOG.debug("no such endpoint [{}]", resource.getEndpoint());
                                close(endpoint, clientConnected);
                            }

                        } else {
                            // MQTT client is trying to publish on a different device_id used on connection (MQTT has now way for errors)
                            LOG.debug("client [ID: {}] not authorized to publish data for device [{}]", endpoint.clientIdentifier(), resource.getResourceId());
                            close(endpoint, clientConnected);
                        }

                    }

                } catch (IllegalArgumentException e) {

                    // MQTT client is trying to publish on invalid topic; it does not contain at least two segments
                    LOG.debug("client [ID: {}] tries to publish on unsupported topic", endpoint.clientIdentifier());
                    close(endpoint, clientConnected);
                }

            });
            clientConnected.compareAndSet(false, true);
            endpoint.accept(false);
        }
    }

    private static void close(final MqttEndpoint endpoint, final AtomicBoolean clientConnected) {
        if (clientConnected.get()) {
            LOG.debug("closing connection with client [client ID: {}]", endpoint.clientIdentifier());
            endpoint.close();
        } else {
            LOG.debug("client has already closed connection");
        }
    }

    private void doUploadMessages(final String tenant, final MqttEndpoint endpoint, final MqttPublishMessage message,
            final BiConsumer<String, Handler<AsyncResult<MessageSender>>> senderSupplier, final AtomicBoolean clientConnected) {

        senderSupplier.accept(tenant, createAttempt -> {

            if (createAttempt.succeeded()) {

                MessageSender sender = createAttempt.result();

                boolean accepted = sender.send(endpoint.clientIdentifier(), message.payload().getBytes(), CONTENT_TYPE_OCTET_STREAM);
                if (accepted) {
                    if (message.qosLevel() == MqttQoS.AT_LEAST_ONCE && clientConnected.get()) {
                        endpoint.publishAcknowledge(message.messageId());
                    }
                } else {
                    LOG.debug("no credit available for sending message");
                    close(endpoint, clientConnected);
                }

            } else {

                // we don't have a connection to Hono ? MQTT no other way to close connection
                LOG.debug("no connection to Hono server");
                close(endpoint, clientConnected);
            }

        });
    }

    private boolean isConnected() {
        return this.hono != null && this.hono.isConnected();
    }
}
