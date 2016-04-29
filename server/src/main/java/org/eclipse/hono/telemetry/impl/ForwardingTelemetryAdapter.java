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

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.telemetry.TelemetryConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger  LOG = LoggerFactory.getLogger(ForwardingTelemetryAdapter.class);
    private final Map<String, ProtonSender> senders;
    private final AtomicLong           messageTagCounter;
    private ProtonConnection     downstreamConnection;
    private String               downstreamContainerHost;
    private int                  downstreamContainerPort;

    /**
     * 
     */
    public ForwardingTelemetryAdapter() {
        senders = new HashMap<>();
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

    private void getSenderForTenant(final String tenantId, final Handler<AsyncResult<ProtonSender>> resultHandler) {
        Future<ProtonSender> result = Future.future();
        ProtonSender sender = senders.get(tenantId);
        if (sender == null) {
            createSender(tenantId, resultHandler);
        } else {
            result.complete(sender);
            resultHandler.handle(result);
        }
    }

    private void createSender(final String tenantId, final Handler<AsyncResult<ProtonSender>> handler) {
        Objects.requireNonNull(tenantId);
        Future<ProtonSender> result = Future.future();
        if (downstreamConnection == null) {
            result.fail("Downstream connection must be opened before creating sender");
            handler.handle(result);
        } else {
            ProtonSender sender = downstreamConnection.createSender(TelemetryConstants.NODE_ADDRESS_TELEMETRY_PREFIX + tenantId);
            sender.setQoS(ProtonQoS.AT_MOST_ONCE);
            sender.openHandler(openAttempt -> {
                if (openAttempt.succeeded()) {
                    LOG.info("sender for downstream container [{}] open", downstreamConnection.getRemoteContainer());
                    addSender(tenantId, openAttempt.result());
                    result.complete(openAttempt.result());
                } else {
                    LOG.warn("could not open sender for downstream container [{}]",
                            downstreamConnection.getRemoteContainer());
                    result.fail(openAttempt.cause());
                }
                handler.handle(result);
            }).open();
        }
    }

    void addSender(final String tenantId, final ProtonSender sender) {
        senders.put(tenantId, sender);
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

    /*
     * (non-Javadoc)
     * 
     * @see org.eclipse.hono.telemetry.TelemetryAdapter#processTelemetryData(org.apache.qpid.proton.message.Message)
     */
    @Override
    public void processTelemetryData(final Message msg, final String tenantId, final Handler<Boolean> resultHandler) {
        LOG.debug("forwarding telemetry message [id: {}, to: {}, content-type: {}] to downstream container",
                msg.getMessageId(), msg.getAddress(), msg.getContentType());
        getSenderForTenant(tenantId, req -> {
            if (req.succeeded()) {
                ProtonSender sender = req.result();
                if (sender.isOpen()) {
                    // TODO flow control with downstream container
                    ByteBuffer b = ByteBuffer.allocate(8);
                    b.putLong(messageTagCounter.getAndIncrement());
                    b.flip();
                    sender.send(b.array(), msg);
                    resultHandler.handle(Boolean.TRUE);
                } else {
                    LOG.warn("sender for downstream container is not open");
                    resultHandler.handle(Boolean.FALSE);
                }
            } else {
                resultHandler.handle(Boolean.FALSE);
            }
        });
    }
}
