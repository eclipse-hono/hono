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

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.telemetry.SenderFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.proton.ProtonClient;
import io.vertx.proton.ProtonClientOptions;
import io.vertx.proton.ProtonConnection;
import io.vertx.proton.ProtonQoS;
import io.vertx.proton.ProtonSender;

/**
 * A telemetry adapter that forwards uploaded messages to another AMQP 1.0 container.
 *
 */
public final class ForwardingTelemetryAdapter extends BaseTelemetryAdapter {

    private static final Logger             LOG                       = LoggerFactory.getLogger(ForwardingTelemetryAdapter.class);
    private final Map<String, ProtonSender> activeSenders;
    private final AtomicLong                messageTagCounter;
    private ProtonConnection                downstreamConnection;
    private String                          downstreamContainerHost;
    private int                             downstreamContainerPort;
    private String                          pathSeparator             = PATH_SEPARATOR;
    private SenderFactory                   senderFactory;

    public ForwardingTelemetryAdapter(final SenderFactory senderFactory) {
        this(senderFactory, 0, 1);
    }

    /**
     * Creates a new adapter instance for a {@code ProtonSender} factory, an instance number
     * and a total number of instances.
     */
    public ForwardingTelemetryAdapter(final SenderFactory senderFactory, final int instanceNo, final int totalNoOfInstances) {
        super(instanceNo, totalNoOfInstances);
        this.senderFactory = Objects.requireNonNull(senderFactory);
        activeSenders = new HashMap<>();
        messageTagCounter = new AtomicLong();
    }

    /**
     * @param host the hostname of the downstream AMQP 1.0 container to forward telemetry data to.
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

    @Override
    public void doStart(final Future<Void> startFuture) throws Exception {
        if (downstreamContainerHost == null) {
            throw new IllegalStateException("downstream container host is not set");
        } else if (downstreamContainerPort == 0) {
            throw new IllegalStateException("downstream container port is not set");
        }
        ProtonClient client = ProtonClient.create(vertx);
        client.connect(createClientOptions(), downstreamContainerHost, downstreamContainerPort, conAttempt -> {
            if (conAttempt.succeeded()) {
                LOG.info("connected to downstream AMQP 1.0 container [{}:{}]", downstreamContainerHost,
                        downstreamContainerPort);
                conAttempt.result().openHandler(result -> {
                    if (result.succeeded()) {
                        downstreamConnection = result.result();
                        LOG.info("connection to downstream container [{}] open",
                                downstreamConnection.getRemoteContainer());
                        startFuture.complete();
                    } else {
                        LOG.warn("Can't connect to downstream {}: {}", downstreamContainerHost + ":" + downstreamContainerPort, result.cause().getMessage());
                        startFuture.fail(result.cause());
                    }
                }).open();
            } else {
                LOG.warn("Can't connect to downstream {}: {}", downstreamContainerHost + ":" + downstreamContainerPort, conAttempt.cause().getMessage());
                startFuture.fail(conAttempt.cause());
            }
        });
    }

    void setDownstreamConnection(final ProtonConnection con) {
        this.downstreamConnection = con;
    }

    @Override
    protected void linkAttached(final String linkId, final String targetAddress) {
        ProtonSender sender = activeSenders.get(linkId);
        if (sender != null) {
            LOG.info("reusing existing downstream sender for link [{}]", linkId);
            // make sure client gets notified once more credit is available
            isSendQueueFull(linkId, sender);
        } else {
            createSender(targetAddress, created -> {
                if (created.succeeded()) {
                    ProtonSender createdSender = created.result();
                    createdSender.setQoS(ProtonQoS.AT_MOST_ONCE);
                    addSender(linkId, createdSender);
                    if (!isSendQueueFull(linkId, createdSender)) {
                        // if we have been granted some credit from downstream container
                        // notify endpoint about it so that client can start to send messages
                        sendFlowControlMessage(linkId, false);
                    }
                } else {
                    LOG.warn("Can't create sender for link {}: {}", linkId, created.cause().getMessage());
                    sendErrorMessage(linkId, true);
                }
            });
        }
    }

    private void createSender(final String targetAddress, final Handler<AsyncResult<ProtonSender>> handler) {
        Future<ProtonSender> result = Future.future();
        result.setHandler(handler);
        if (downstreamConnection == null) {
            result.fail("downstream connection must be opened before creating sender");
        } else {
            String address = targetAddress.replace("/", pathSeparator);
            senderFactory.createSender(downstreamConnection, address, result);
        }
    }

    void addSender(final String linkId, final ProtonSender sender) {
        activeSenders.put(linkId, sender);
    }

    private boolean isSendQueueFull(final String linkId, final ProtonSender sender) {
        if (sender.sendQueueFull()) {
            sendFlowControlMessage(linkId, true);
            LOG.debug("downstream send queue for link [{}] is full, registering drain handler ...", linkId);
            sender.sendQueueDrainHandler(replenish -> {
                LOG.debug("downstream sender for link [{}] has been replenished with credit, notfying telemetry endpoint...", linkId);
                sendFlowControlMessage(linkId, false);});
            return true;
        } else {
            return false;
        }
    }

    @Override
    protected void linkDetached(final String linkId) {
        LOG.debug("closing downstream sender for link [{}]", linkId);
        ProtonSender sender = activeSenders.remove(linkId);
        if (sender != null) {
            sender.close();
        }
    }

    @Override
    protected void doStop(final Future<Void> stopFuture) {
        if (downstreamConnection != null) {
            final String container = downstreamConnection.getRemoteContainer();
            LOG.info("closing connection to downstream container [{}]", container);
            downstreamConnection.closeHandler(closeAttempt -> {
                if (closeAttempt.succeeded()) {
                    LOG.info("connection to downstream container [{}] closed", container);
                    stopFuture.complete();
                } else {
                    stopFuture.fail(closeAttempt.cause());
                }
            }).close();
        } else {
            stopFuture.complete();
        }
    }

    private ProtonClientOptions createClientOptions() {
        ProtonClientOptions options = new ProtonClientOptions();
        options.setReconnectAttempts(10)
                .setReconnectInterval(5 * 1000); // every 5 secs
        return options;
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
            // check if sender has credit left
            isSendQueueFull(linkId, sender);
        } else {
            LOG.warn("downstream sender for link [{}] is not open, discarding message and closing link with client", linkId);
            sendErrorMessage(linkId, true);
            linkDetached(linkId);
        }
    }

    private void forwardMessage(final ProtonSender sender, final Message msg) {
        LOG.debug("forwarding telemetry message [id: {}, to: {}, content-type: {}] to downstream container",
                msg.getMessageId(), msg.getAddress(), msg.getContentType());
        ByteBuffer b = ByteBuffer.allocate(8);
        b.putLong(messageTagCounter.getAndIncrement());
        b.flip();
        sender.send(b.array(), msg);
    }
}
