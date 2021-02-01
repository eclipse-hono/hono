/*******************************************************************************
 * Copyright (c) 2016, 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.hono.cli.app;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;

import javax.annotation.PostConstruct;

import org.eclipse.hono.application.client.ApplicationClientFactory;
import org.eclipse.hono.application.client.DownstreamMessage;
import org.eclipse.hono.application.client.MessageContext;
import org.eclipse.hono.application.client.amqp.AmqpApplicationClientFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import io.vertx.core.AsyncResult;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;

/**
 * A command line client for receiving messages from via Hono's north bound Telemetry and/or Event APIs.
 * <p>
 * Messages are output to stdout.
 * <p>
 * Note that this example intentionally does not support Command &amp; Control and rather is the most simple version of a
 * receiver for downstream data. Please refer to the documentation of Command &amp; Control for the example that supports
 * it (found in the User Guide section).
 */
@Component
@Profile("receiver")
public class Receiver extends AbstractApplicationClient {

    private static final String TYPE_TELEMETRY = "telemetry";
    private static final String TYPE_EVENT = "event";
    private static final String TYPE_ALL = "all";
    /**
     * The type of messages to create a consumer for.
     */
    @Value(value = "${message.type}")
    protected String messageType = TYPE_ALL;

    /**
     * Bi consumer to handle messages based on endpoint.
     */
    private BiConsumer<String, DownstreamMessage<? extends MessageContext>> messageHandler = this::handleMessage;

    // TODO remove the client factory from here and use the one from parent once
    //  org.eclipse.hono.application.client.ApplicationClientFactory supports C&C
    private ApplicationClientFactory<? extends MessageContext> clientFactory;

    @Autowired
    public final void setProtocolAgnosticApplicationClientFactory(
            final ApplicationClientFactory<? extends MessageContext> factory) {
        this.clientFactory = Objects.requireNonNull(factory);
    }

    /**
     * Set message handler for processing adaption.
     *
     * @param messageHandler message handler.
     * @throws NullPointerException if message handler is {@code null}.
     */
    void setMessageHandler(final BiConsumer<String, DownstreamMessage<? extends MessageContext>> messageHandler) {
        this.messageHandler = Objects.requireNonNull(messageHandler);
    }

    /**
     * Starts this component.
     * <p>
     *
     * @return A future indicating the outcome of the startup process.
     */
    @PostConstruct
    Future<CompositeFuture> start() {

        final Future<Void> startFuture;
        if (clientFactory instanceof AmqpApplicationClientFactory) {
            final AmqpApplicationClientFactory amqpClientFactory = (AmqpApplicationClientFactory) clientFactory;
            startFuture = amqpClientFactory.connect()
                    .onComplete(con -> amqpClientFactory.addReconnectListener(client -> createConsumer()))
                    .mapEmpty();
        } else {
            startFuture = Future.succeededFuture();
        }

        return startFuture
                .compose(c -> createConsumer())
                .onComplete(this::handleCreateConsumerStatus);
    }

    private CompositeFuture createConsumer() {

        final Handler<Throwable> closeHandler = cause -> {
            log.info("close handler of consumer is called", cause);
            vertx.setTimer(connectionRetryInterval, reconnect -> {
                log.info("attempting to re-create the consumer ...");
                createConsumer();
            });
        };

        @SuppressWarnings("rawtypes")
        final List<Future> consumerFutures = new ArrayList<>();
        if (messageType.equals(TYPE_EVENT) || messageType.equals(TYPE_ALL)) {
            consumerFutures.add(
                    clientFactory.createEventConsumer(tenantId, msg -> {
                        messageHandler.accept(TYPE_EVENT, msg);
                    }, closeHandler));
        }

        if (messageType.equals(TYPE_TELEMETRY) || messageType.equals(TYPE_ALL)) {
            consumerFutures.add(
                    clientFactory.createTelemetryConsumer(tenantId, msg -> {
                        messageHandler.accept(TYPE_TELEMETRY, msg);
                    }, closeHandler));
        }

        if (consumerFutures.isEmpty()) {
            consumerFutures.add(Future.failedFuture(
                    String.format(
                            "Invalid message type [\"%s\"]. Valid types are \"telemetry\", \"event\" or \"all\"",
                            messageType)));
        }
        return CompositeFuture.all(consumerFutures);
    }

    /**
     * Handle received message.
     *
     * Write log messages to stdout.
     *
     * @param endpoint receiving endpoint, "telemetry" or "event".
     * @param msg received message
     */
    private void handleMessage(final String endpoint, final DownstreamMessage<? extends MessageContext> msg) {
        final String deviceId = msg.getDeviceId();

        final Buffer payload = msg.getPayload();

        log.info("received {} message [device: {}, content-type: {}]: {}", endpoint, deviceId, msg.getContentType(),
                payload);

        log.info("... with properties: {}", msg.getProperties().getPropertiesMap());
    }

    private void handleCreateConsumerStatus(final AsyncResult<CompositeFuture> startup) {
        if (startup.succeeded()) {
            log.info("Receiver [tenant: {}, mode: {}] created successfully, hit ctrl-c to exit", tenantId,
                    messageType);
        } else {
            log.error("Error occurred during initialization of receiver: {}", startup.cause().getMessage());
            vertx.close();
        }
    }
}
