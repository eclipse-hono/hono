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
import java.util.concurrent.atomic.AtomicLong;

import org.apache.qpid.proton.message.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import io.vertx.core.Future;
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
@Profile("forwarding-telemetry")
public final class ForwardingTelemetryAdapter extends BaseTelemetryAdapter {

    private static final Logger LOG = LoggerFactory.getLogger(ForwardingTelemetryAdapter.class);
    private ProtonConnection    downstreamConnection;
    private ProtonSender        downstreamSender;
    private String              downstreamContainerHost;
    private int                 downstreamContainerPort;
    private AtomicLong          messageTagCounter;

    /**
     * 
     */
    public ForwardingTelemetryAdapter() {
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
                        createSender(startFuture);
                    } else {
                        startFuture.fail(result.cause());
                    }
                }).open();
            } else {
                startFuture.fail(conAttempt.cause());
            }
        });
    }

    private void createSender(final Future<Void> startFuture) {
        if (downstreamConnection == null) {
            startFuture.fail("Downstream connection must be opened before creating sender");
        } else {
            ProtonSender sender = downstreamConnection.createSender(null);
            sender.setQoS(ProtonQoS.AT_MOST_ONCE);
            sender.openHandler(openAttempt -> {
                if (openAttempt.succeeded()) {
                    LOG.info("sender for downstream container [{}] open", downstreamConnection.getRemoteContainer());
                    setSender(openAttempt.result());
                    startFuture.complete();
                } else {
                    LOG.warn("could not open sender for downstream container [{}]",
                            downstreamConnection.getRemoteContainer());
                    startFuture.fail(openAttempt.cause());
                }
            }).open();
        }
    }

    void setSender(final ProtonSender sender) {
        this.downstreamSender = sender;
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
    public boolean processTelemetryData(final Message telemetryData) {
        if (downstreamSender != null && downstreamSender.isOpen()) {
            // TODO flow control with downstream container
            ByteBuffer b = ByteBuffer.allocate(8);
            b.putLong(messageTagCounter.getAndIncrement());
            b.flip();
            downstreamSender.send(b.array(), telemetryData);
            return true;
        } else {
            LOG.warn("sender for downstream container is not open");
            return false;
        }
    }
}
