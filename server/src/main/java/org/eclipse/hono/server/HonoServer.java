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

import static org.eclipse.hono.authorization.AuthorizationConstants.AUTH_SUBJECT_FIELD;
import static org.eclipse.hono.authorization.AuthorizationConstants.EVENT_BUS_ADDRESS_AUTHORIZATION_IN;
import static org.eclipse.hono.authorization.AuthorizationConstants.PERMISSION_FIELD;
import static org.eclipse.hono.authorization.AuthorizationConstants.RESOURCE_FIELD;
import static org.eclipse.hono.telemetry.TelemetryConstants.EVENT_BUS_ADDRESS_TELEMETRY_IN;
import static org.eclipse.hono.telemetry.TelemetryConstants.TELEMETRY_ENDPOINT;

import java.util.UUID;

import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.AmqpMessage;
import org.eclipse.hono.authorization.AuthorizationConstants;
import org.eclipse.hono.authorization.Permission;
import org.eclipse.hono.telemetry.TelemetryMessageFilter;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.ResourceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
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

    private static final Logger LOG = LoggerFactory.getLogger(HonoServer.class);
    private String              host;
    private int                 port;
    private boolean             singleTenant;
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

    /**
     * Sets the port Hono will listen on for AMQP 1.0 connections.
     * <p>
     * If set to 0 Hono will bind to an arbitrary free port chosen by the operating system.
     * </p>
     *
     * @param port the port to bind to.
     */
    @Value(value = "${hono.server.port}")
    public void setPort(int port) {
        this.port = port;
    }

    /**
     * Gets the port Hono listens on for AMQP 1.0 connections.
     * <p>
     * If the port has been set to 0 Hono will bind to an arbitrary free port chosen by the operating system during
     * startup. Once Hono is up and running this method returns the <em>actual port</em> Hono has bound to.
     * </p>
     *
     * @return the port Hono listens on.
     */
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

    /**
     * @return the singleTenant
     */
    public boolean isSingleTenant() {
        return singleTenant;
    }

    /**
     * @param singleTenant the singleTenant to set
     */
    @Value(value = "${hono.single.tenant}")
    public void setSingleTenant(final boolean singleTenant) {
        this.singleTenant = singleTenant;
    }

    void helloProcessConnection(final ProtonConnection connection) {
        connection.sessionOpenHandler(session -> session.open());
        connection.receiverOpenHandler(openedReceiver -> handleReceiverOpen(connection, openedReceiver));
        connection.disconnectHandler(HonoServer::handleDisconnected);
        connection.closeHandler(HonoServer::handleConnectionClosed);
        connection.openHandler(result -> {
            LOG.debug("Client [{}:{}] connected", connection.getRemoteHostname(), connection.getRemoteContainer());
            connection.setContainer(String.format("Hono-%s:%d", this.host, server.actualPort())).open();
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
     * @param con the connection to the client.
     * @param receiver the receiver created for the link.
     */
    void handleReceiverOpen(final ProtonConnection con, final ProtonReceiver receiver) {
        LOG.debug("client wants to open a link for sending messages [address: {}]", receiver.getRemoteTarget());
        try {
            final ResourceIdentifier targetResource = getTargetResource(receiver);
            receiver.setTarget(receiver.getRemoteTarget());
            if (targetResource.getEndpoint().equals(TELEMETRY_ENDPOINT)) {
                // client wants to upload telemetry data
                handleTelemetryUpload(receiver, targetResource);
            } else {
                LOG.info("client wants to connect to unsupported endpoint [address: {}]",
                        receiver.getRemoteTarget().getAddress());
                receiver.close();
            }
        } catch (IllegalArgumentException e) {
            LOG.debug("client has provided invalid resource identifier as target address", e);
            receiver.close();
        }
    }

    private void handleTelemetryUpload(final ProtonReceiver receiver, final ResourceIdentifier targetResource) {
        isClientAuthorizedToAttachToTelemetryEndpoint(targetResource,
                authorizedToAttach -> {
                    if (!authorizedToAttach) {
                        LOG.debug(
                                "client is not authorized to upload telemetry data for tenant [id: {}], closing link",
                                targetResource.getTenantId());
                        receiver.close();
                    } else {
                        receiver.setAutoAccept(false);
                        LOG.debug("client uses QoS: {}", receiver.getRemoteQoS());
                        receiver.handler((delivery, message) -> {
                            if (TelemetryMessageFilter.verify(targetResource.getTenantId(), message)) {
                                sendTelemetryData(targetResource, delivery, message);
                            } else {
                                ProtonHelper.rejected(delivery, true);
                            }
                        }).flow(20).open();
                    }
                });
    }

    private ResourceIdentifier getTargetResource(final ProtonReceiver receiver) {
        String remoteTargetAddress = receiver.getRemoteTarget().getAddress();
        if (isSingleTenant()) {
            return ResourceIdentifier.fromStringAssumingDefaultTenant(remoteTargetAddress);
        } else {
            return ResourceIdentifier.fromString(remoteTargetAddress);
        }
    }

    private void isClientAuthorizedToAttachToTelemetryEndpoint(final ResourceIdentifier targetResource,
            final Handler<Boolean> handler) {
        final JsonObject body = new JsonObject();
        // TODO how to obtain subject information?
        body.put(AUTH_SUBJECT_FIELD, Constants.DEFAULT_SUBJECT);
        body.put(RESOURCE_FIELD, targetResource.toString());
        body.put(PERMISSION_FIELD, Permission.WRITE.toString());
        vertx.eventBus().send(EVENT_BUS_ADDRESS_AUTHORIZATION_IN, body,
                res -> handler.handle(res.succeeded() && AuthorizationConstants.ALLOWED.equals(res.result().body())));
    }

    private void sendTelemetryData(final ResourceIdentifier targetResource, final ProtonDelivery delivery,
            final Message msg) {
        final String messageId = UUID.randomUUID().toString();
        final AmqpMessage amqpMessage = AmqpMessage.of(msg, delivery);
        vertx.sharedData().getLocalMap(EVENT_BUS_ADDRESS_TELEMETRY_IN).put(messageId, amqpMessage);
        checkPermissionAndSend(messageId, targetResource, amqpMessage);
    }

    private void checkPermissionAndSend(final String messageId, final ResourceIdentifier targetResource,
            final AmqpMessage amqpMessage)
    {
        final JsonObject body = new JsonObject();
        // TODO how to obtain subject information?
        body.put(AUTH_SUBJECT_FIELD, Constants.DEFAULT_SUBJECT);
        body.put(RESOURCE_FIELD, targetResource.toString());
        body.put(PERMISSION_FIELD, Permission.WRITE.toString());

        vertx.eventBus().send(EVENT_BUS_ADDRESS_AUTHORIZATION_IN, body,
           res -> {
               if (res.succeeded() && AuthorizationConstants.ALLOWED.equals(res.result().body())) {
                   vertx.runOnContext(run -> {
                       final ProtonDelivery delivery = amqpMessage.getDelivery();
                       if (delivery.remotelySettled()) {
                           // client uses AT MOST ONCE semantics
                           sendAtMostOnce(messageId, delivery);
                       } else {
                           // client uses AT LEAST ONCE semantics
                           sendAtLeastOnce(messageId, delivery);
                       }
                   });
               } else {
                   LOG.debug("not allowed to upload telemetry data", res.cause());
               }
           });
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
