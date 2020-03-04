/*******************************************************************************
 * Copyright (c) 2016, 2019 Contributors to the Eclipse Foundation
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

import io.vertx.core.AsyncResult;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.cli.AbstractCliClient;
import org.eclipse.hono.cli.client.ClientConfig;
import org.eclipse.hono.client.ApplicationClientFactory;
import org.eclipse.hono.client.HonoConnection;
import org.eclipse.hono.util.MessageHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

/**
 * The methods of the command line client for receiving messages from via Hono's north bound Telemetry and/or Event API
 * <p>
 * Messages are output to stdout.
 * <p>
 * Note that this example intentionally does not support Command &amp; Control and rather is the most simple version of a
 * receiver for downstream data. Please refer to the documentation of Command &amp; Control for the example that supports
 * it (found in the User Guide section).
 */
public class Receiver extends AbstractCliClient{
    /**
     * To signal the CLI main class of the ended execution.
     */
    CountDownLatch latch;
    /**
     * Configuration used for the connection.
     */
    private final ClientConfig clientConfig;

    /**
     * Constructor to create the config environment for the execution of the command.
     * @param clientFactory The factory with client's methods.
     * @param vertx The instance of vert.x connection.
     * @param clientConfig The class with all config parameters.
     */
    public Receiver(final ApplicationClientFactory clientFactory, final Vertx vertx, final ClientConfig clientConfig) {
        this.clientFactory = clientFactory;
        this.vertx = vertx;
        this.clientConfig = clientConfig;
    }

    /**
     * Entrypoint to start the command.
     * @param latch The handle to signal the ended execution and return to the shell.
     * @return A Future used for testing
     */
    public Future<CompositeFuture> start(final CountDownLatch latch){
        this.latch = latch;
        return clientFactory.connect()
                .compose(con -> {
                    clientFactory.addReconnectListener(this::createConsumer);
                    return createConsumer(con);
                }).setHandler(this::handleCreateConsumerStatus);
    }

    private CompositeFuture createConsumer(final HonoConnection connection) {
        final Handler<Void> closeHandler = closeHook -> {
            log.info("close handler of consumer is called");
            vertx.setTimer(clientConfig.connectionRetryInterval, reconnect -> {
                log.info("attempting to re-open the consumer link ...");
                createConsumer(connection);
            });
        };

        @SuppressWarnings("rawtypes")
        final List<Future> consumerFutures = new ArrayList<>();
        if (clientConfig.messageType.equals(ClientConfig.TYPE_EVENT) || clientConfig.messageType.equals(ClientConfig.TYPE_ALL)) {
            consumerFutures.add(
                    clientFactory.createEventConsumer(clientConfig.tenantId, msg -> handleMessage(ClientConfig.TYPE_EVENT, msg), closeHandler));
        }

        if (clientConfig.messageType.equals(ClientConfig.TYPE_TELEMETRY) ||clientConfig. messageType.equals(ClientConfig.TYPE_ALL)) {
            consumerFutures.add(
                    clientFactory.createTelemetryConsumer(clientConfig.tenantId, msg -> handleMessage(ClientConfig.TYPE_TELEMETRY, msg), closeHandler));
        }

        if (consumerFutures.isEmpty()) {
            consumerFutures.add(Future.failedFuture(
                    String.format(
                            "Invalid message type [\"%s\"]. Valid types are \"telemetry\", \"event\" or \"all\"",
                            clientConfig.messageType)));
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

    private void handleCreateConsumerStatus(final AsyncResult<CompositeFuture> startup) {
        if (startup.succeeded()) {
            log.info("Receiver [tenant: {}, mode: {}] created successfully, hit ctrl-c to exit", clientConfig.tenantId,
                    clientConfig.messageType);
        } else {
            log.error("Error occurred during initialization of receiver: {}", startup.cause().getMessage());
            latch.countDown();
        }
    }
}
