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

import static org.eclipse.hono.telemetry.TelemetryConstants.*;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.telemetry.SenderFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

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
@Service
@Profile({"forwarding-telemetry", "activemq"})
public final class ForwardingTelemetryAdapter extends BaseTelemetryAdapter {

    private static final Logger             LOG                       = LoggerFactory.getLogger(ForwardingTelemetryAdapter.class);
    private final Map<String, ProtonSender> activeSenders;
    private final AtomicLong                messageTagCounter;
    private ProtonConnection                downstreamConnection;
    private String                          downstreamContainerHost;
    private int                             downstreamContainerPort;
    private String                          pathSeparator             = PATH_SEPARATOR;
    private SenderFactory                   senderFactory;

    /**
     * 
     */
    @Autowired
    public ForwardingTelemetryAdapter(final SenderFactory senderFactory) {
        this.senderFactory = Objects.requireNonNull(senderFactory);
        activeSenders = new HashMap<>();
        messageTagCounter = new AtomicLong();
    }

    /**
     * @param host the hostname of the downstream AMQP 1.0 container to forward telemetry data to.
     */
    @Value(value = "${hono.telemetry.downstream.host}")
    public void setDownstreamContainerHost(final String host) {
        this.downstreamContainerHost = host;
    }

    /**
     * @param port the port of the downstream AMQP 1.0 container to forward telemetry data to.
     */
    @Value(value = "${hono.telemetry.downstream.port}")
    public void setDownstreamContainerPort(final int port) {
        this.downstreamContainerPort = port;
    }

    @Override
    public void doStart(final Future<Void> startFuture) throws Exception {
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
                        startFuture.fail(result.cause());
                    }
                }).open();
            } else {
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
                        sendFlowControlMessage(linkId, false);
                    }
                } else {
                    sendErrorMessage(linkId, true);
                }
            });
        }
    }

    private void createSender(final String targetAddress, final Handler<AsyncResult<ProtonSender>> handler) {
        Future<ProtonSender> result = Future.future();
        result.setHandler(handler);
        if (downstreamConnection == null) {
            result.fail("Downstream connection must be opened before creating sender");
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
            LOG.debug("downstream sender queue for link [{}] is full, registering drain handler ...", linkId);
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
            LOG.debug("no downstream sender for link [{}] available, discarding message and closing link with client", linkId);
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

    public String getPathSeparator() {
        return pathSeparator;
    }

    @Value(value = "${hono.telemetry.pathSeparator:/}")
    public void setPathSeparator(String pathSeparator) {
        this.pathSeparator = pathSeparator;
    }
}
