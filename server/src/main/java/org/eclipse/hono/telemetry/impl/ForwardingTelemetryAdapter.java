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
package org.eclipse.hono.telemetry.impl;

import static org.eclipse.hono.telemetry.TelemetryConstants.PATH_SEPARATOR;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.telemetry.SenderFactory;
import org.eclipse.hono.util.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.proton.ProtonClient;
import io.vertx.proton.ProtonClientOptions;
import io.vertx.proton.ProtonConnection;
import io.vertx.proton.ProtonSender;

/**
 * A telemetry adapter that forwards uploaded messages to another AMQP 1.0 container.
 *
 */
public final class ForwardingTelemetryAdapter extends BaseTelemetryAdapter {

    private static final Logger             LOG                       = LoggerFactory.getLogger(ForwardingTelemetryAdapter.class);
    private final Map<String, ProtonSender> activeSenders             = new HashMap<>();
    private final Map<String, List<String>> sendersPerConnection      = new HashMap<>();
    private ProtonConnection                downstreamConnection;
    private String                          downstreamContainerHost;
    private int                             downstreamContainerPort;
    private String                          pathSeparator             = PATH_SEPARATOR;
    private SenderFactory                   senderFactory;

    /**
     * Creates a new adapter instance for a sender factory.
     * 
     * @param senderFactory The factory to use for creating new senders for downstream telemetry data.
     */
    public ForwardingTelemetryAdapter(final SenderFactory senderFactory) {
        this(senderFactory, 0, 1);
    }

    /**
     * Creates a new adapter instance for a sender factory, an instance number
     * and a total number of instances.
     * 
     * @param senderFactory The factory to use for creating new senders for downstream telemetry data.
     * @param instanceNo The identifier for the created instance.
     * @param totalNoOfInstances The total number of instances created.
     */
    public ForwardingTelemetryAdapter(final SenderFactory senderFactory, final int instanceNo, final int totalNoOfInstances) {
        super(instanceNo, totalNoOfInstances);
        this.senderFactory = Objects.requireNonNull(senderFactory);
    }

    /**
     * @param host the hostname or IP address of the downstream AMQP 1.0 container to forward telemetry data to.
     * @throws NullPointerException if the host is {@code null}.
     */
    void setDownstreamContainerHost(final String host) {
        this.downstreamContainerHost = Objects.requireNonNull(host);
    }

    /**
     * @param port the port of the downstream AMQP 1.0 container to forward telemetry data to.
     * @throws IllegalArgumentException if the given port is not a valid IP port.
     */
    void setDownstreamContainerPort(final int port) {
        if (port < 1 || port >= 1 << 16) {
            throw new IllegalArgumentException("illegal port number");
        }
        this.downstreamContainerPort = port;
    }

    /**
     * @param pathSeparator the character to use for separating the segments
     *                      of message addresses. 
     * @throws NullPointerException if the given character is {@code null}.
     */
    void setPathSeparator(final String pathSeparator) {
        this.pathSeparator = Objects.requireNonNull(pathSeparator);
    }

    /**
     * {@inheritDoc}
     * 
     * @throws IllegalStateException If the downstream container host is {@code null}
     *                               or the downstream container port is 0.
     */
    @Override
    public void doStart(final Future<Void> startFuture) throws Exception {

        if (downstreamContainerHost == null) {
            throw new IllegalStateException("downstream container host is not set");
        } else if (downstreamContainerPort == 0) {
            throw new IllegalStateException("downstream container port is not set");
        } else {
            connectToDownstream(startFuture);
        }
    }

    @Override
    protected void doStop(final Future<Void> stopFuture) {
        if (downstreamConnection != null && !downstreamConnection.isDisconnected()) {
            final String container = downstreamConnection.getRemoteContainer();
            LOG.info("closing connection to downstream container [{}]", container);
            downstreamConnection.closeHandler(null).disconnectHandler(null).close();
        } else {
            LOG.debug("downstream connection already closed");
        }
        stopFuture.complete();
    }

    private ProtonClientOptions createClientOptions() {
        ProtonClientOptions options = new ProtonClientOptions();
        options.setReconnectAttempts(10)
                .setReconnectInterval(5 * 1000); // every 5 secs
        return options;
    }

    private void connectToDownstream(final Future<Void> connectHandler) {

        LOG.info("connecting to downstream container [{}:{}]...", downstreamContainerHost, downstreamContainerPort);

        ProtonClient client = ProtonClient.create(vertx);
        client.connect(createClientOptions(), downstreamContainerHost, downstreamContainerPort, conAttempt -> {
            if (conAttempt.failed()) {
                LOG.warn("can't connect to downstream AMQP 1.0 container [{}:{}]: {}", downstreamContainerHost, downstreamContainerPort, conAttempt.cause().getMessage());
                connectHandler.fail(conAttempt.cause());
            } else {
                LOG.info("connected to downstream AMQP 1.0 container [{}:{}], opening connection ...",
                        downstreamContainerHost, downstreamContainerPort);
                conAttempt.result()
                    .setContainer("Hono-TelemetryAdapter-" + instanceNo)
                    .setHostname("hono-internal")
                    .openHandler(openCon -> {
                        if (openCon.succeeded()) {
                            downstreamConnection = openCon.result();
                            LOG.info("connection to downstream container [{}] open",
                                    downstreamConnection.getRemoteContainer());
                            downstreamConnection.disconnectHandler(this::onDisconnectFromDownstreamContainer);
                            downstreamConnection.closeHandler(closedConnection -> {
                                LOG.warn("connection to downstream container [{}] is closed", downstreamConnection.getRemoteContainer());
                                downstreamConnection.close();
                            });
                            connectHandler.complete();
                        } else {
                            LOG.warn("can't open connection to downstream container [{}]",
                                    downstreamConnection.getRemoteContainer(), openCon.cause());
                            connectHandler.fail(openCon.cause());
                        }
                    }).open();
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
        activeSenders.clear();
        LOG.warn("disconnected from downstream container [{}], triggering restart ...", con.getRemoteContainer());
        vertx.eventBus().send(Constants.APPLICATION_ENDPOINT, Constants.getRestartJson());
    }

    void setDownstreamConnection(final ProtonConnection con) {
        this.downstreamConnection = con;
    }

    @Override
    protected void onLinkAttached(final String connectionId, final String linkId, final String targetAddress) {

        if (activeSenders.containsKey(linkId)) {
            LOG.info("reusing existing downstream sender [con: {}, link: {}]", connectionId, linkId);
        } else {
            createSender(targetAddress, 
                    replenishedSender -> {
                        int credits = getAvailableCredit(replenishedSender);
                        LOG.trace("downstream sender [con:{}, link: {}] has been replenished with {} credits", connectionId, linkId, credits);
                        replenishUpstreamSender(linkId, credits);
                    }, created -> {
                        if (created.succeeded()) {
                            LOG.info("created downstream sender [con: {}, link: {}]", connectionId, linkId);
                            addSender(connectionId, linkId, created.result());
                        } else {
                            LOG.warn("can't create downstream sender [con: {}, link: {}]", connectionId, linkId, created.cause());
                            sendErrorMessage(linkId, true);
                        }
                    });
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
            String address = targetAddress.replace("/", pathSeparator);
            senderFactory.createSender(downstreamConnection, address, sendQueueDrainHandler, result);
        }
    }

    void addSender(final String connectionId, final String linkId, final ProtonSender sender) {
        sender.attachments().set(Constants.KEY_CONNECTION_ID, String.class, connectionId);
        activeSenders.put(linkId, sender);
        List<String> senders = sendersPerConnection.get(connectionId);
        if (senders == null) {
            senders = new ArrayList<>();
            sendersPerConnection.put(connectionId, senders);
        }
        senders.add(linkId);
    }

    private static int getAvailableCredit(final ProtonSender sender) {
        return sender.getCredit() - sender.getQueued();
    }

    @Override
    protected void onLinkDetached(final String linkId) {
        String connectionId = closeSender(linkId);
        if (connectionId != null) {
            List<String> senders = sendersPerConnection.get(connectionId);
            if (senders != null) {
                senders.remove(linkId);
            }
        }
    }

    private String closeSender(final String linkId) {
        ProtonSender sender = activeSenders.remove(linkId);
        if (sender != null && sender.isOpen()) {
            String connectionId = Constants.getConnectionId(sender);
            LOG.info("closing downstream sender [con: {}, link: {}]", connectionId, linkId);
            sender.close();
            return connectionId;
        } else {
             return null;
        }
    }

    @Override
    protected void onConnectionClosed(final String connectionId) {
        List<String> senders = sendersPerConnection.remove(Objects.requireNonNull(connectionId));
        if (senders != null && !senders.isEmpty()) {
            LOG.info("closing {} downstream senders for connection [id: {}]", senders.size(), connectionId);
            for (String linkId : senders) {
                closeSender(linkId);
            }
        }
    }

    @Override
    public void processTelemetryData(final Message msg, final String linkId) {
        Objects.requireNonNull(msg);
        Objects.requireNonNull(linkId);
        ProtonSender sender = activeSenders.get(linkId);
        if (sender == null) {
            LOG.info("no downstream sender for link [{}] available, discarding message and closing link with client", linkId);
            sendErrorMessage(linkId, true);
        } else if (sender.isOpen()) {
            forwardMessage(sender, msg);
        } else {
            LOG.warn("downstream sender for link [{}] is not open, discarding message and closing link with client", linkId);
            sendErrorMessage(linkId, true);
            onLinkDetached(linkId);
        }
    }

    private void forwardMessage(final ProtonSender sender, final Message msg) {
        LOG.debug("forwarding telemetry message [id: {}, to: {}, content-type: {}] to downstream container",
                msg.getMessageId(), msg.getAddress(), msg.getContentType());
        sender.send(msg);
    }
}
