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

package org.eclipse.hono.cli.device;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

import org.eclipse.hono.cli.AbstractCommand;
import org.eclipse.hono.client.ServerErrorException;
import org.eclipse.hono.client.device.amqp.AmqpAdapterClientFactory;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

/**
 * A Client for accessing Hono's Telemetry and Event APIs to send
 * telemetry data and events via the AMQP adapter.
 */
public class TelemetryAndEvent extends AbstractCommand {
    /**
     * Methods factory instantiated with the connection parameters.
     */
    private final AmqpAdapterClientFactory clientFactory;

    private final String deviceId;
    private final String messageType;
    private final String payload;
    private final Map<String, String> customApplicationProperties;

    /**
     * Constructor to create the config environment for the execution of the command.
     *
     * @param clientFactory The device's methods factory.
     * @param vertx The instance of vert.x connection.
     * @param deviceId The ID of the device to use to connect to the AMQP Adapter.
     * @param messageType The type of the message to send.
     * @param payload The payload which the user want to send.
     */
    public TelemetryAndEvent(final AmqpAdapterClientFactory clientFactory, final Vertx vertx, final String deviceId, final String messageType, final String payload) {
        customApplicationProperties = new HashMap<>();
        customApplicationProperties.put("device_type", "amqp_example_device");
        this.clientFactory = clientFactory;
        this.vertx = vertx;
        this.deviceId = deviceId;
        this.messageType = messageType;
        this.payload = payload;
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
                        switch (messageType) {
                            case "QOS1": {
                                sendTelemetryMessageWithQos1();
                            } break;
                            case "QOS2": {
                                sendTelemetryMessageWithQos2();
                            } break;
                            case "EVENT": {
                                sendEvent();
                            } break;
                        }
                    } else {
                        System.out.println("Connection not established: " + connectionStatus.cause());
                        latch.countDown();
                    }
                });
    }

    private void sendTelemetryMessageWithQos1() {
        System.out.println();
        clientFactory.getOrCreateTelemetrySender()
                .compose(telemetrySender -> telemetrySender.send(deviceId, payload.getBytes(), "application/json",
                        customApplicationProperties))
                .setHandler(connectAttempt -> {
                    if (connectAttempt.succeeded()) {
                        System.out.println("Telemetry message with QoS 'AT_MOST_ONCE' sent: " + payload);
                    } else {
                        System.out.println("Sending telemetry message with QoS 'AT_MOST_ONCE' failed: " + connectAttempt.cause());
                    }
                });
    }

    private void sendTelemetryMessageWithQos2() {
        final JsonObject JSONpayload = new JsonObject().put("data", payload);
        clientFactory.getOrCreateTelemetrySender()
                .compose(telemetrySender -> telemetrySender.sendAndWaitForOutcome(deviceId,
                        JSONpayload.toBuffer().getBytes(), "application/json", customApplicationProperties))
                .setHandler(ar -> {
                    if (ar.succeeded()) {
                        System.out.println("Telemetry message with QoS 'AT_LEAST_ONCE' sent: " + payload);
                    } else {
                        final Throwable cause = ar.cause();
                        String hint = "";
                        if (cause instanceof ServerErrorException
                                && ((ServerErrorException) cause).getErrorCode() == 503) {
                            hint = " (Is there a consumer connected?)";
                        }
                        System.out
                                .println("Sending telemetry message with QoS 'AT_LEAST_ONCE' failed: " + cause + hint);
                    }
                });
    }

    private void sendEvent() {
        final JsonObject payload = new JsonObject().put("threshold", "exceeded");
        clientFactory.getOrCreateEventSender()
                .compose(
                        eventSender -> eventSender.send(deviceId, payload.toBuffer().getBytes(), "application/json",
                                customApplicationProperties))
                .setHandler(ar -> {
                    if (ar.succeeded()) {
                        System.out.println("Event sent: " + payload);
                    } else {
                        System.out.println("Sending event failed: " + ar.cause());
                    }
                });
    }


}
