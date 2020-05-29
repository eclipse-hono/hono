/*******************************************************************************
 * Copyright (c) 2016, 2020 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/

package org.eclipse.hono.cli.application;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.function.BiConsumer;

import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.cli.AbstractCommand;
import org.eclipse.hono.client.ApplicationClientFactory;
import org.eclipse.hono.client.HonoConnection;
import org.eclipse.hono.util.MessageHelper;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;


/**
 * The methods of the command line client for receiving messages from via Hono's north bound Telemetry and/or Event API
 * <p>
 * Messages are output to stdout.
 * <p>
 * Note that this example intentionally does not support Command &amp; Control and rather is the most simple version of a
 * receiver for downstream data. Please refer to the documentation of Command &amp; Control for the example that supports
 * it (found in the User Guide section).
 */
public class Receiver extends AbstractCommand {

    private final ApplicationClientFactory clientFactory;
    private final String tenantId;
    private final String messageType;
    private final int connectionRetryInterval;

    /**
     * Bi consumer to handle messages based on endpoint.
     */
    private BiConsumer<String, Message> messageHandler = (endpoint, msg) -> handleMessage(endpoint, msg);

    /**
     * Constructor to create the config environment for the execution of the command.
     *
     * @param clientFactory The factory with client's methods.
     * @param vertx The instance of vert.x connection.
     * @param tenantId The tenant which the user want to connect.
     * @param messageType The message type which the user want to receive.
     * @param connectionRetryInterval The time to wait before retry to connect.
     */
    public Receiver(final ApplicationClientFactory clientFactory, final Vertx vertx, final String tenantId, final String messageType, final int connectionRetryInterval) {
        this.clientFactory = clientFactory;
        this.vertx = vertx;
        this.tenantId = tenantId;
        this.messageType = messageType;
        this.connectionRetryInterval = connectionRetryInterval;
    }

    /**
     * Set message handler for processing adaption.
     *
     * @param messageHandler message handler.
     * @throws NullPointerException if message handlerr is {@code null}.
     */
    public void setMessageHandler(final BiConsumer<String, Message> messageHandler) {
        this.messageHandler = Objects.requireNonNull(messageHandler);
    }

    /**
     * Entrypoint to start the command.
     *
     * @param latch The handle to signal the ended execution and return to the shell.
     * @param con The Hono connection instance, already connected.
     */
    public void start(final CountDownLatch latch, final HonoConnection con) {
        this.latch = latch;
        clientFactory.isConnected().onComplete(connectionStatus -> {
            if (connectionStatus.succeeded()) {
                clientFactory.addReconnectListener(this::createConsumer);
                createConsumer(con);
            } else {
                close(connectionStatus.cause());
            }
        });
    }

    private CompositeFuture createConsumer(final HonoConnection connection) {
        final Handler<Void> closeHandler = closeHook -> {
            log.info("close handler of consumer is called");
            vertx.setTimer(connectionRetryInterval, reconnect -> {
                log.info("attempting to re-open the consumer link ...");
                createConsumer(connection);
            });
        };

        @SuppressWarnings("rawtypes")
        final List<Future> consumerFutures = new ArrayList<>();
        if (messageType.equals(ApplicationProfile.TYPE_EVENT) || messageType.equals(ApplicationProfile.TYPE_ALL)) {
            consumerFutures.add(
                    clientFactory.createEventConsumer(tenantId, msg -> messageHandler.accept(ApplicationProfile.TYPE_EVENT, msg), closeHandler));
        }

        if (messageType.equals(ApplicationProfile.TYPE_TELEMETRY) || messageType.equals(ApplicationProfile.TYPE_ALL)) {
            consumerFutures.add(
                    clientFactory.createTelemetryConsumer(tenantId, msg -> messageHandler.accept(ApplicationProfile.TYPE_TELEMETRY, msg), closeHandler));
        }

        if (consumerFutures.isEmpty()) {
            consumerFutures.add(Future.failedFuture(
                    String.format(
                            "Invalid message type [\"%s\"]. Valid types are \"telemetry\", \"event\" or \"all\"",
                            messageType)));
        }
        return CompositeFuture.all(consumerFutures);
    }

    private void handleMessage(final String endpoint, final Message msg) {
        final String deviceId = MessageHelper.getDeviceId(msg);

        final Buffer payload = MessageHelper.getPayload(msg);

        log.info("received {} message [device: {}, content-type: {}]: {}", endpoint, deviceId, msg.getContentType(),
                payload);

        if (msg.getApplicationProperties() != null) {
            log.info("... with application properties: {}", msg.getApplicationProperties().getValue());
        }
    }

    private void close(final Throwable t) {
        log.error("Connection not established: {}", t.getMessage());
        latch.countDown();
    }
}
