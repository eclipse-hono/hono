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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.config.HonoConfigProperties;
import org.eclipse.hono.util.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.proton.ProtonClient;
import io.vertx.proton.ProtonClientOptions;
import io.vertx.proton.ProtonConnection;
import io.vertx.proton.ProtonDelivery;
import io.vertx.proton.ProtonQoS;
import io.vertx.proton.ProtonSender;

/**
 * A downstream adapter that provides support for sending messages to an AMQP 1.0 container.
 *
 */
@Component
public abstract class ForwardingDownstreamAdapter implements DownstreamAdapter {

    protected Logger                        logger                    = LoggerFactory.getLogger(getClass());
    protected String                        downstreamContainerHost;
    protected int                           downstreamContainerPort;
    protected HonoConfigProperties          honoConfig                = new HonoConfigProperties();
    private final Map<String, ProtonSender> activeSenders             = new HashMap<>();
    private final Map<String, List<String>> sendersPerConnection      = new HashMap<>();
    private final Vertx                     vertx;
    private ProtonConnection                downstreamConnection;
    private SenderFactory                   senderFactory;

    /**
     * Creates a new adapter instance for a sender factory.
     * 
     * @param vertx The Vert.x instance to run on.
     * @param senderFactory The factory to use for creating new senders for downstream telemetry data.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    protected ForwardingDownstreamAdapter(final Vertx vertx, final SenderFactory senderFactory) {
        this.vertx = Objects.requireNonNull(vertx);
        this.senderFactory = Objects.requireNonNull(senderFactory);
    }

    /**
     * Sets the global Hono configuration properties.
     * 
     * @param props The properties.
     * @throws NullPointerException if props is {@code null}.
     */
    @Autowired(required = false)
    public void setHonoConfiguration(final HonoConfigProperties props) {
        this.honoConfig = Objects.requireNonNull(props);
    }

    /**
     * Sets the name or IP address of the downstream AMQP 1.0 container to forward telemetry data to.
     * 
     * @param host The hostname or IP address.
     * @throws NullPointerException if the host is {@code null}.
     */
    @Value("${hono.telemetry.downstream.host:localhost}")
    public final void setDownstreamContainerHost(final String host) {
        this.downstreamContainerHost = Objects.requireNonNull(host);
    }

    /**
     * Sets the port of the downstream AMQP 1.0 container to forward telemetry data to.
     * 
     * @param port The port number.
     * @throws IllegalArgumentException if the given port is not a valid IP port.
     */
    @Value("${hono.telemetry.downstream.port:15672}")
    public final void setDownstreamContainerPort(final int port) {
        if (port < 1 || port >= 1 << 16) {
            throw new IllegalArgumentException("illegal port number");
        }
        this.downstreamContainerPort = port;
    }

    /**
     * Connects to the downstream container.
     * 
     * @param startFuture The result of the connection attempt.
     * @throws IllegalStateException If the downstream container host is {@code null}
     *                               or the downstream container port is 0.
     */
    public final void start(final Future<Void> startFuture) {

        if (downstreamContainerHost == null) {
            throw new IllegalStateException("downstream container host is not set");
        } else if (downstreamContainerPort == 0) {
            throw new IllegalStateException("downstream container port is not set");
        } else {
            if (honoConfig.isWaitForDownstreamConnectionEnabled()) {
                logger.info("waiting for connection to downstream container");
                connectToDownstream(createClientOptions(), startFuture);
            } else {
                connectToDownstream(createClientOptions());
                startFuture.complete();
            }
        }
    }

    /**
     * Closes the connection with the downstream container.
     * 
     * @param stopFuture Always succeeds.
     */
    public final void stop(final Future<Void> stopFuture) {

        if (downstreamConnection != null && !downstreamConnection.isDisconnected()) {
            final String container = downstreamConnection.getRemoteContainer();
            logger.info("closing connection to downstream container [{}]", container);
            downstreamConnection.closeHandler(null).disconnectHandler(null).close();
        } else {
            logger.debug("downstream connection already closed");
        }
        stopFuture.complete();
    }

    protected ProtonClientOptions createClientOptions() {
        return new ProtonClientOptions()
                .setConnectTimeout(100)
                .setReconnectAttempts(-1).setReconnectInterval(200); // reconnect forever, every 200 millisecs
    }

    private void connectToDownstream(final ProtonClientOptions options) {
        connectToDownstream(options, Future.future());
    }

    private void connectToDownstream(final ProtonClientOptions options, final Future<Void> connectFuture) {

        logger.info("connecting to downstream container [{}:{}]...", downstreamContainerHost, downstreamContainerPort);

        ProtonClient client = ProtonClient.create(vertx);
        client.connect(options, downstreamContainerHost, downstreamContainerPort, conAttempt -> {
            if (conAttempt.failed()) {
                logger.warn("can't connect to downstream AMQP 1.0 container [{}:{}]: {}", downstreamContainerHost, downstreamContainerPort, conAttempt.cause().getMessage());
            } else {
                logger.info("connected to downstream AMQP 1.0 container [{}:{}], opening connection ...",
                        downstreamContainerHost, downstreamContainerPort);
                conAttempt.result()
                    .setContainer("Hono-TelemetryAdapter" + UUID.randomUUID())
                    .setHostname("hono-internal")
                    .openHandler(openCon -> {
                        if (openCon.succeeded()) {
                            downstreamConnection = openCon.result();
                            logger.info("connection to downstream container [{}] open",
                                    downstreamConnection.getRemoteContainer());
                            downstreamConnection.disconnectHandler(this::onDisconnectFromDownstreamContainer);
                            downstreamConnection.closeHandler(closedConnection -> {
                                logger.info("connection to downstream container [{}] is closed", downstreamConnection.getRemoteContainer());
                                downstreamConnection.close();
                            });
                            connectFuture.complete();
                        } else {
                            logger.warn("can't open connection to downstream container [{}]",
                                    downstreamConnection.getRemoteContainer(), openCon.cause());
                            connectFuture.fail(openCon.cause());
                        }
                    })
                    .closeHandler(closedCon -> logger.debug("Connection to [{}:{}] closed: {}", downstreamContainerHost, downstreamContainerPort))
                    .open();
            }
        });
    }

    /**
     * Handles unexpected disconnection from downstream container.
     * 
     * @param con the failed connection
     */
    private void onDisconnectFromDownstreamContainer(final ProtonConnection con) {
        // all links to downstream host will now be stale and unusable
        logger.warn("lost connection to downstream container [{}]", downstreamContainerHost);
        activeSenders.clear();
        con.disconnectHandler(null);
        con.disconnect();
        ProtonClientOptions clientOptions = createClientOptions();
        if (clientOptions.getReconnectAttempts() != 0) {
            vertx.setTimer(300, reconnect -> {
                logger.info("attempting to re-connect to downstream container [{}]", downstreamContainerHost);
                connectToDownstream(clientOptions);
            });
        }
    }

    public final void setDownstreamConnection(final ProtonConnection con) {
        this.downstreamConnection = con;
    }

    @Override
    public final void onClientAttach(final UpstreamReceiver client, final Handler<AsyncResult<Void>> resultHandler) {

        if (activeSenders.containsKey(client.getLinkId())) {
            logger.info("reusing existing downstream sender [con: {}, link: {}]", client.getConnectionId(), client.getLinkId());
        } else {
            createSender(
                    client.getTargetAddress(),
                    replenishedSender -> handleFlow(replenishedSender, client),
                    creationAttempt -> {
                        if (creationAttempt.succeeded()) {
                            logger.info("created downstream sender [con: {}, link: {}]", client.getConnectionId(), client.getLinkId());
                            addSender(client.getConnectionId(), client.getLinkId(), creationAttempt.result());
                            resultHandler.handle(Future.succeededFuture());
                        } else {
                            resultHandler.handle(Future.failedFuture(creationAttempt.cause()));
                            logger.warn("can't create downstream sender [con: {}, link: {}]", client.getConnectionId(), client.getLinkId(), creationAttempt.cause());
                        }
                    });
        }
    }

    public final void handleFlow(
            final ProtonSender replenishedSender,
            final UpstreamReceiver client) {

        int credits = getAvailableCredit(replenishedSender);
        logger.trace("received FLOW from downstream sender for upstream client [con:{}, link: {}, credits: {}, drain: {}",
                client.getConnectionId(), client.getLinkId(), credits, replenishedSender.getDrain());
        if (replenishedSender.getDrain()) {
            // send drain request upstream and act upon result of request to drain upstream client
            client.drain(10000, drainAttempt -> {
                if (drainAttempt.succeeded()) {
                    replenishedSender.drained();
                }
            });
        } else {
            client.replenish(credits);
        }
    }

    private void createSender(
            final String targetAddress,
            final Handler<ProtonSender> sendQueueDrainHandler,
            final Handler<AsyncResult<ProtonSender>> handler) {

        Future<ProtonSender> result = Future.future();
        result.setHandler(handler);
        if (downstreamConnection == null || downstreamConnection.isDisconnected()) {
            result.fail("downstream connection must be opened before creating sender");
        } else {
            String address = targetAddress.replace(Constants.DEFAULT_PATH_SEPARATOR, honoConfig.getPathSeparator());
            senderFactory.createSender(downstreamConnection, address, getDownstreamQos(), sendQueueDrainHandler, result);
        }
    }

    public final void addSender(final String connectionId, final String linkId, final ProtonSender sender) {
        sender.attachments().set(Constants.KEY_CONNECTION_ID, String.class, connectionId);
        sender.setAutoDrained(false); // we need to propagate drain requests upstream and wait for the result
        activeSenders.put(linkId, sender);
        List<String> senders = sendersPerConnection.get(connectionId);
        if (senders == null) {
            senders = new ArrayList<>();
            sendersPerConnection.put(connectionId, senders);
        }
        senders.add(linkId);
    }

    private static int getAvailableCredit(final ProtonSender sender) {
        // TODO: is it correct to subtract the queued messages?
        return sender.getCredit() - sender.getQueued();
    }

    @Override
    public final void onClientDetach(final UpstreamReceiver client) {
        String connectionId = closeSender(client.getLinkId());
        if (connectionId != null) {
            List<String> senders = sendersPerConnection.get(connectionId);
            if (senders != null) {
                senders.remove(client.getLinkId());
            }
        }
    }

    private String closeSender(final String linkId) {
        ProtonSender sender = activeSenders.remove(linkId);
        if (sender != null && sender.isOpen()) {
            String connectionId = Constants.getConnectionId(sender);
            logger.info("closing downstream sender [con: {}, link: {}]", connectionId, linkId);
            sender.close();
            return connectionId;
        } else {
             return null;
        }
    }

    @Override
    public final void onClientDisconnect(final String connectionId) {
        List<String> senders = sendersPerConnection.remove(Objects.requireNonNull(connectionId));
        if (senders != null && !senders.isEmpty()) {
            logger.info("closing {} downstream senders for connection [id: {}]", senders.size(), connectionId);
            for (String linkId : senders) {
                closeSender(linkId);
            }
        }
    }

    @Override
    public final void processMessage(final UpstreamReceiver client, final ProtonDelivery delivery, final Message msg) {

        Objects.requireNonNull(client);
        Objects.requireNonNull(msg);
        Objects.requireNonNull(delivery);
        ProtonSender sender = activeSenders.get(client.getLinkId());
        if (sender == null) {
            logger.info("no downstream sender for link [{}] available, discarding message and closing link with client", client.getLinkId());
            client.close(ErrorConditions.ERROR_NO_DOWNSTREAM_CONSUMER);
        } else if (sender.isOpen()) {
            forwardMessage(sender, msg, delivery);
        } else {
            logger.warn("downstream sender for link [{}] is not open, discarding message and closing link with client", client.getLinkId());
            client.close(ErrorConditions.ERROR_NO_DOWNSTREAM_CONSUMER);
            onClientDetach(client);
        }
    }

    /**
     * Forwards the message to the downstream container.
     * <p>
     * It is the implementer's responsibility to handle message disposition and settlement with the upstream
     * client using the <em>delivery</em> object.
     * 
     * @param sender The link to the downstream container.
     * @param msg The message to send.
     * @param delivery The handle for settling the message with the client.
     */
    protected abstract void forwardMessage(final ProtonSender sender, final Message msg, final ProtonDelivery delivery);

    /**
     * Gets the Quality-of-Service type used for the link with the downstream container.
     * 
     * @return The QoS.
     */
    protected abstract ProtonQoS getDownstreamQos();
}
