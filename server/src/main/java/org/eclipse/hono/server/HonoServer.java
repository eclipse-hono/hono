/**
 * Copyright (c) 2016 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 */
package org.eclipse.hono.server;

import java.util.Objects;
import java.util.UUID;

import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.AmqpMessage;
import org.eclipse.hono.telemetry.TelemetryMessageFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.proton.ProtonConnection;
import io.vertx.proton.ProtonDelivery;
import io.vertx.proton.ProtonHelper;
import io.vertx.proton.ProtonReceiver;
import io.vertx.proton.ProtonServer;
import io.vertx.proton.ProtonServerOptions;

/**
 * The Hono server is an AMQP 1.0 container that provides endpoints for the <em>Telemetry</em>,
 * <em>Command &amp; Control</em> and <em>Device Registration</em> APIs that <em>Protocol Adapters</em> and
 * <em>Solutions</em> use to interact with devices.
 */
@Component
public final class HonoServer extends AbstractVerticle {

    /**
     * 
     */
    public static final String  EVENT_BUS_ADDRESS_TELEMETRY_IN = "telemetry.in";
    public static final String  NODE_ADDRESS_TELEMETRY_UPLOAD = "telemetry/upload";
    private static final Logger LOG                           = LoggerFactory.getLogger(HonoServer.class);
    private String              host;
    private int                 port;
    private ProtonServer        server;

    @Override
    public void start(final Future<Void> startupHandler) {
        final ProtonServerOptions options = createServerOptions();
        server = ProtonServer.create(vertx, options)
                .connectHandler(this::helloProcessConnection)
                .listen(port, host, bindAttempt -> {
                    if (bindAttempt.succeeded()) {
                        this.port = bindAttempt.result().actualPort();
                        LOG.info("HonoServer running at [{}:{}]", host, this.port);
                        startupHandler.complete();
                    } else {
                        LOG.error("Cannot start up HonoServer", bindAttempt.cause());
                        startupHandler.fail(bindAttempt.cause());
                    }
                });
    }

    ProtonServerOptions createServerOptions() {
        ProtonServerOptions options = new ProtonServerOptions();
        options.setIdleTimeout(0);
        options.setReceiveBufferSize(32 * 1024); // 32kb
        options.setSendBufferSize(32 * 1024); // 32kb
        options.setReuseAddress(false);
        return options;
    }

    @Override
    public void stop(Future<Void> shutdownHandler) {
        if (server != null) {
            server.close(done -> shutdownHandler.complete());
        }
    }

    @Value(value = "${hono.server.port}")
    public void setPort(int port) {
        this.port = port;
    }

    public int getPort() {
        return this.port;
    }

    @Value(value = "${hono.server.bindaddress}")
    public void setHost(final String host) {
        this.host = host;
    }

    public String getHost() {
        return host;
    }

    void helloProcessConnection(final ProtonConnection connection) {
        connection.sessionOpenHandler(session -> session.open());
        connection.receiverOpenHandler(this::handleReceiverOpen);
        connection.disconnectHandler(HonoServer::handleDisconnected);
        connection.closeHandler(HonoServer::handleConnectionClosed);
        connection.openHandler(result -> {
            LOG.debug("Client [{}:{}] connected", connection.getRemoteHostname(), connection.getRemoteContainer());
            connection.setContainer("Hono").open();
        });

    }

    private static void handleConnectionClosed(AsyncResult<ProtonConnection> res) {
        if (res.succeeded()) {
            ProtonConnection con = res.result();
            LOG.debug("Client [{}:{}] closed connection", con.getRemoteHostname(), con.getRemoteContainer());
            con.close();
        }
    }

    private static void handleDisconnected(ProtonConnection connection) {
        LOG.debug("Client [{}:{}] disconnected", connection.getRemoteHostname(), connection.getRemoteContainer());
    }

    /**
     * Handles a request from a client to establish a link for sending messages to this server.
     * 
     * @param receiver the receiver created for the link.
     */
    void handleReceiverOpen(final ProtonReceiver receiver) {
        LOG.debug("client wants to upload data [address: {}]", receiver.getRemoteTarget());
        receiver.setTarget(receiver.getRemoteTarget());
        if (NODE_ADDRESS_TELEMETRY_UPLOAD.equals(receiver.getRemoteTarget().getAddress())) {
            // client wants to upload telemetry data
            handleTelemetryUpload(receiver);
        } else {
            LOG.info("client wants to connect to unsupported endpoint [address: {}]",
                    receiver.getRemoteTarget().getAddress());
            receiver.close();
        }
    }

    private void handleTelemetryUpload(final ProtonReceiver receiver) {
        Objects.requireNonNull(receiver);
        receiver.setAutoAccept(false);
        LOG.debug("client uses QoS: {}", receiver.getRemoteQoS());
        receiver.handler((delivery, message) -> {
            LOG.trace("received message [id: {}]", message.getMessageId());
            if (TelemetryMessageFilter.verify(message)) {
                sendTelemetryData(delivery, message);
            } else {
                ProtonHelper.rejected(delivery, true);
            }
        }).flow(20).open();
    }

    private void sendTelemetryData(final ProtonDelivery delivery, final Message msg) {
        String messageId = UUID.randomUUID().toString();
        vertx.sharedData().getLocalMap(EVENT_BUS_ADDRESS_TELEMETRY_IN).put(messageId, AmqpMessage.of(msg, delivery));
        if (delivery.remotelySettled()) {
            // client uses AT MOST ONCE semantics
            sendAtMostOnce(messageId, delivery);
        } else {
            // client uses AT LEAST ONCE semantics
            sendAtLeastOnce(messageId, delivery);
        }
    }

    private void sendAtMostOnce(final String messageId, final ProtonDelivery delivery) {
        vertx.eventBus().send(EVENT_BUS_ADDRESS_TELEMETRY_IN, messageId);
        ProtonHelper.accepted(delivery, true);
    }

    private void sendAtLeastOnce(final String messageId, final ProtonDelivery delivery) {
        vertx.eventBus().send(EVENT_BUS_ADDRESS_TELEMETRY_IN, messageId,
                res -> {
            if (res.succeeded() && "accepted".equals(res.result().body())) {
                vertx.runOnContext(run -> ProtonHelper.accepted(delivery, true));
            } else {
                LOG.debug("did not receive response for telemetry data message", res.cause());
                vertx.runOnContext(run -> ProtonHelper.rejected(delivery, true));
            }
        });
    }
}
