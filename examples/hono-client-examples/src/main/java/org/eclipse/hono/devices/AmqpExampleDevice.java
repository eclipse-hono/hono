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

package org.eclipse.hono.devices;

import java.util.HashMap;
import java.util.Map;

import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.HonoConnection;
import org.eclipse.hono.client.ServerErrorException;
import org.eclipse.hono.client.device.amqp.AmqpAdapterClientFactory;
import org.eclipse.hono.config.ClientConfigProperties;
import org.eclipse.hono.util.MessageHelper;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

/**
 * A simple example device implementation to show the usage of the AMQP Adapter Client. It connects to the
 * <a href="https://www.eclipse.org/hono/sandbox/">Hono Sandbox</a> and performs the following operations with a delay
 * of 1 second between each:
 * <ol>
 * <li>Send a telemetry message with QoS "AT_MOST_ONCE"</li>
 * <li>Send a telemetry message with QoS "AT_LEAST_ONCE"</li>
 * <li>Send an event message</li>
 * <li>Subscribe and wait for for commands</li>
 * </ol>
 *
 * If it receives a request-response command, it sends a response.
 * <p>
 * <b>The used tenant, device and credentials need to be registered and enabled.</b> For details on how to do this,
 * refer to the <a href="https://www.eclipse.org/hono/getting-started/">Getting Started Guide</a>.
 *
 * @see AmqpAdapterClientFactory
 * @see <a href="https://www.eclipse.org/hono/docs/dev-guide/amqp_adapter_client/">The AMQP Adapter Client
 *      documentation</a>
 */
public class AmqpExampleDevice {

    private static final String HOST = "hono.eclipseprojects.io";
    private static final int PORT = 5671;
    private static final boolean TLS_ENABLED = true;
    private static final int RECONNECT_ATTEMPTS = 1;

    private static final String TENANT_ID = "DEFAULT_TENANT";
    private static final String DEVICE_ID = "4711";
    private static final String AUTH_ID = "sensor1";
    private static final String PASSWORD = "hono-secret";

    private final Vertx VERTX = Vertx.vertx();
    private final ClientConfigProperties config = new ClientConfigProperties();
    private final Map<String, String> customApplicationProperties = new HashMap<>();
    private AmqpAdapterClientFactory factory;

    private AmqpExampleDevice() {
        config.setHost(HOST);
        config.setPort(PORT);
        config.setTlsEnabled(TLS_ENABLED);
        config.setUsername(AUTH_ID + "@" + TENANT_ID);
        config.setPassword(PASSWORD);
        config.setReconnectAttempts(RECONNECT_ATTEMPTS);

        customApplicationProperties.put("device_type", "amqp_example_device");

    }

    public static void main(final String[] args) {

        final AmqpExampleDevice amqpExampleDevice = new AmqpExampleDevice();
        amqpExampleDevice.connect();
    }

    private void connect() {
        final HonoConnection connection = HonoConnection.newConnection(VERTX, config);
        connection.connect()
                .onComplete(ar -> {
                    if (ar.succeeded()) {
                        startDevice(ar.result());
                    } else {
                        System.out.println("Connection failed: " + ar.cause());
                        System.exit(1);
                    }
                });
    }

    private void startDevice(final HonoConnection connection) {
        factory = AmqpAdapterClientFactory.create(connection, TENANT_ID);

        sendTelemetryMessageWithQos1();

        VERTX.setTimer(1000, l -> sendTelemetryMessageWithQos2());

        VERTX.setTimer(2000, l -> sendEvent());

        VERTX.setTimer(3000, l -> {
            System.out.println("Waiting for commands... (Press Ctrl+C to stop)");
            factory.createCommandConsumer(this::handleCommand);
        });
    }

    private void sendTelemetryMessageWithQos1() {
        System.out.println();

        final String payload = "42";
        factory.getOrCreateTelemetrySender()
                .compose(telemetrySender -> telemetrySender.send(DEVICE_ID, payload.getBytes(), "text/plain",
                        customApplicationProperties))
                .onComplete(ar -> {
                    if (ar.succeeded()) {
                        System.out.println("Telemetry message with QoS 'AT_MOST_ONCE' sent: " + payload);
                    } else {
                        System.out.println("Sending telemetry message with QoS 'AT_MOST_ONCE' failed: " + ar.cause());
                    }
                });
    }

    private void sendTelemetryMessageWithQos2() {
        final JsonObject payload = new JsonObject().put("weather", "cloudy");
        factory.getOrCreateTelemetrySender()
                .compose(telemetrySender -> telemetrySender.sendAndWaitForOutcome(DEVICE_ID,
                        payload.toBuffer().getBytes(), "application/json", customApplicationProperties))
                .onComplete(ar -> {
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
        factory.getOrCreateEventSender()
                .compose(
                        eventSender -> eventSender.send(DEVICE_ID, payload.toBuffer().getBytes(), "application/json",
                                customApplicationProperties))
                .onComplete(ar -> {
                    if (ar.succeeded()) {
                        System.out.println("Event sent: " + payload);
                    } else {
                        System.out.println("Sending event failed: " + ar.cause());
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
        factory.getOrCreateCommandResponseSender()
                .compose(commandResponder -> commandResponder.sendCommandResponse(DEVICE_ID, command.getReplyTo(),
                        command.getCorrelationId().toString(), 200, payload.toBuffer().getBytes(), "application/json",
                        customApplicationProperties))
                .onComplete(ar -> {
                    if (ar.succeeded()) {
                        System.out.println("Command response sent: " + payload);
                    } else {
                        System.out.println("Sending command response failed: " + ar.cause());
                    }
                });
    }

}
