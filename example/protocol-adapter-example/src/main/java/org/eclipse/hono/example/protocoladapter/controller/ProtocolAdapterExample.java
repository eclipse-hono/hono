/*******************************************************************************
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.example.protocoladapter.controller;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.proton.ProtonDelivery;
import org.apache.qpid.proton.amqp.transport.DeliveryState;
import org.eclipse.hono.example.protocoladapter.adapter.CommandAndControlReceiver;
import org.eclipse.hono.example.protocoladapter.adapter.TelemetryAndEventSender;
import org.eclipse.hono.example.protocoladapter.interfaces.CommandHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * Example protocol adapter service to send AMQP messages to Hono amqp adapter using Hono cli module classes
 */
@Service
public class ProtocolAdapterExample {

    private static final Logger log = LoggerFactory.getLogger(ProtocolAdapterExample.class);

    private final TelemetryAndEventSender telemetryAndEventSender;
    private final CommandAndControlReceiver commandAndControlReceiver;

    @Autowired
    public ProtocolAdapterExample(final TelemetryAndEventSender telemetryAndEventSender,
                                  final CommandAndControlReceiver commandAndControlReceiver) {
        this.telemetryAndEventSender = telemetryAndEventSender;
        this.commandAndControlReceiver = commandAndControlReceiver;
    }

    /**
     * Sets AMQP client properties and command handler {@link CommandHandler}
     *
     * @param host           AMQP Hono adapter IP address
     * @param port           AMQP Hono adapter port
     * @param username       username consists of DEVICE_ID@TENANT_ID
     * @param password       device credentials
     * @param commandHandler function to process incoming commands
     */
    public void setAMQPClientProps(final String host, final int port, final String username, final String password, final CommandHandler commandHandler) {
        telemetryAndEventSender.setAMQPClientProps(host, port, username, password);
        commandAndControlReceiver.setAMQPClientProps(host, port, username, password, commandHandler);
    }

    /**
     * Sends AMQP message to Hono AMQP adapter
     * <p>
     * Connection properties have to be set with {@link #setAMQPClientProps(String, int, String, String, CommandHandler) } beforehand
     *
     * @param messagePayload Message payload
     * @param messageAddress "telemetry" ("t") or "event" ("e")
     * @return response from Hono AMQP adapter
     */
    public Future<String> sendAMQPMessage(final String messagePayload, final String messageAddress) {
        final CompletableFuture<ProtonDelivery> messageSent = new CompletableFuture<>();
        final Promise<String> messageResponse = Promise.promise();

        try {
            telemetryAndEventSender.sendMessage(messagePayload, messageAddress, messageSent);
        } catch (final IllegalArgumentException e) {
            messageResponse.fail(e.getCause());
            log.error(String.format("Sending message failed [reason: %s] %n", e.getMessage()));
            return messageResponse.future();
        }

        try {
            final ProtonDelivery delivery = messageSent.join();
            // Logs the delivery state to the console
            final DeliveryState state = delivery.getRemoteState();
            messageResponse.complete(state.getType().toString());
            log.info(String.format("Delivery State: %s", state.getType()));
        } catch (final CompletionException e) {
            log.error(String.format("Sending message failed [reason: %s] %n", e.getMessage()));
            messageResponse.fail(e.getCause());
        } catch (final CancellationException e) {
            // do-nothing
            messageResponse.fail(e.getCause());
        }
        return messageResponse.future();
    }

    /**
     * Start listening for commands
     * <p>
     * Connection properties have to be set with {@link #setAMQPClientProps(String, int, String, String, CommandHandler) } beforehand
     */
    public void listenCommands() {
        commandAndControlReceiver.listenCommands();
    }

}
