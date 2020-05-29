/*******************************************************************************
 * Copyright (c) 2019, 2020 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.cli.device;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.cli.AbstractCommand;
import org.eclipse.hono.client.device.amqp.AmqpAdapterClientFactory;
import org.eclipse.hono.util.MessageHelper;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

/**
 * A connection for accessing Hono's Command and Control APIs to
 * receive commands and sends command response messages via the AMQP adapter.
 */
public class CommandAndControl extends AbstractCommand {
    /**
     * Methods factory instantiated with the connection parameters.
     */
    private final AmqpAdapterClientFactory clientFactory;

    private final String deviceId;
    private final Map<String, String> customApplicationProperties;

    /**
     * Constructor to create the config environment for the execution of the command.
     *
     * @param clientFactory The device's methods factory.
     * @param vertx The instance of vert.x connection.
     * @param deviceId The ID of the device to use to connect to the AMQP Adapter.
     */
    public CommandAndControl(final AmqpAdapterClientFactory clientFactory, final Vertx vertx, final String deviceId) {
        customApplicationProperties = new HashMap<>();
        customApplicationProperties.put("device_type", "amqp_example_device");
        this.clientFactory = clientFactory;
        this.vertx = vertx;
        this.deviceId = deviceId;
    }

    /**
     * Entrypoint to start the command.
     *
     * @param latch The handle to signal the ended execution and return to the shell.
     */
    public void start(final CountDownLatch latch) {
        this.latch = latch;
        clientFactory.isConnected()
                .onComplete(connectionStatus -> {
                    if (connectionStatus.succeeded()) {
                        clientFactory.createCommandConsumer(this::handleCommand);
                    } else {
                        System.out.println("Connection not established: " + connectionStatus.cause());
                        latch.countDown();
                    }
                });
    }

    private void handleCommand(final Message commandMessage) {

        final String subject = commandMessage.getSubject();
        final String commandPayload = MessageHelper.getPayloadAsString(commandMessage);

        if (commandMessage.getReplyTo() == null || commandMessage.getCorrelationId() == null) {
            // one-way command
            System.out.println(String.format("Received one-way command [name: %s]: %s", subject, commandPayload));
        } else {
            // request-response command
            System.out.println(String.format("Received command [name: %s]: %s", subject, commandPayload));
            sendCommandResponse(commandMessage);
        }
    }

    private void sendCommandResponse(final Message command) {
        final JsonObject payload = new JsonObject().put("outcome", "success");
        clientFactory.getOrCreateCommandResponseSender()
                .compose(commandResponder -> commandResponder.sendCommandResponse(deviceId, command.getReplyTo(),
                        command.getCorrelationId().toString(), 200, payload.toBuffer().getBytes(), "application/json",
                        customApplicationProperties))
                .setHandler(ar -> {
                    if (ar.succeeded()) {
                        System.out.println("Command response sent: " + payload);
                    } else {
                        System.out.println("Sending command response failed: " + ar.cause());
                    }
                });
    }
}
