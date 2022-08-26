/*******************************************************************************
 * Copyright (c) 2020, 2022 Contributors to the Eclipse Foundation
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

import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.client.amqp.config.ClientConfigProperties;
import org.eclipse.hono.client.amqp.connection.AmqpUtils;
import org.eclipse.hono.client.amqp.connection.HonoConnection;
import org.eclipse.hono.client.device.amqp.AmqpAdapterClient;
import org.eclipse.hono.util.QoS;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
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
 * @see AmqpAdapterClient
 * @see <a href="https://www.eclipse.org/hono/docs/dev-guide/amqp_adapter_client/">The AMQP Adapter Client
 *      documentation</a>
 */
@SuppressFBWarnings(
        value = "HARD_CODE_PASSWORD",
        justification = """
                We use the default passwords of the Hono Sandbox installation throughout this class
                for ease of use. The passwords are publicly documented and do not affect any
                private installations of Hono.
                """)
public class AmqpExampleDevice {

    private static final String HOST = "hono.eclipseprojects.io";
    private static final int PORT = 5671;
    private static final boolean TLS_ENABLED = true;
    private static final int RECONNECT_ATTEMPTS = 1;

    private static final String TENANT_ID = "DEFAULT_TENANT";
//    private static final String DEVICE_ID = "4711";
    private static final String AUTH_ID = "sensor1";
    private static final String PASSWORD = "hono-secret";

    private final Vertx vertx = Vertx.vertx();
    private final ClientConfigProperties config = new ClientConfigProperties();
    private AmqpAdapterClient client;

    private AmqpExampleDevice() {
        config.setHost(HOST);
        config.setPort(PORT);
        config.setTlsEnabled(TLS_ENABLED);
        config.setUsername(AUTH_ID + "@" + TENANT_ID);
        config.setPassword(PASSWORD);
        config.setReconnectAttempts(RECONNECT_ATTEMPTS);
    }

    /**
     * Runs the example device.
     *
     * @param args Ignored.
     */
    public static void main(final String[] args) {

        final AmqpExampleDevice amqpExampleDevice = new AmqpExampleDevice();
        amqpExampleDevice.connect();
    }

    private void connect() {
        final HonoConnection connection = HonoConnection.newConnection(vertx, config);
        connection.connect()
            .onSuccess(this::startDevice)
            .onFailure(t -> {
                System.err.println("Failed to establish connection: " + t);
                System.exit(1);
            });
    }

    private void startDevice(final HonoConnection connection) {
        client = AmqpAdapterClient.create(connection);

        sendTelemetryMessageWithQos0();

        vertx.setTimer(1000, l -> sendTelemetryMessageWithQos1());

        vertx.setTimer(2000, l -> sendEvent());

        vertx.setTimer(3000, l -> {
            System.out.println("Waiting for commands... (Press Ctrl+C to stop)");
            client.createCommandConsumer(this::handleCommand);
        });
    }

    private void sendTelemetryMessageWithQos0() {
        System.out.println();

        final var payload = "42";
        client.sendTelemetry(QoS.AT_MOST_ONCE, Buffer.buffer(payload), "text/plain", null, null, null)
            .onSuccess(delivery -> System.out.println("Telemetry message with QoS 'AT_MOST_ONCE' sent: " + payload))
            .onFailure(t -> System.err.println("Sending telemetry message with QoS 'AT_MOST_ONCE' failed: " + t));
    }

    private void sendTelemetryMessageWithQos1() {
        final var payload = new JsonObject().put("weather", "cloudy");
        client.sendTelemetry(QoS.AT_LEAST_ONCE, payload.toBuffer(), "application/json", null, null, null)
            .onSuccess(delivery -> System.out.println("Telemetry message with QoS 'AT_LEAST_ONCE' sent: " + payload))
            .onFailure(t -> {
                String hint = "";
                if (ServiceInvocationException.extractStatusCode(t) == 503) {
                    hint = " (Is there a consumer connected?)";
                }
                System.err.println("Sending telemetry message with QoS 'AT_LEAST_ONCE' failed: " + t + hint);
            });
    }

    private void sendEvent() {
        final var payload = new JsonObject().put("threshold", "exceeded");
        client.sendEvent(payload.toBuffer(), "application/json", null, null, null)
            .onSuccess(delivery -> System.out.println("Event sent: " + payload))
            .onFailure(t -> System.err.println("Sending event failed: " + t));
    }

    private void handleCommand(final Message commandMessage) {

        final String subject = commandMessage.getSubject();
        final String commandPayload = AmqpUtils.getPayloadAsString(commandMessage);

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
        client.sendCommandResponse(
                    command.getReplyTo(),
                    command.getCorrelationId().toString(),
                    200,
                    payload.toBuffer(),
                    "application/json",
                    null)
            .onSuccess(delivery -> System.out.println("Command response sent: " + payload))
            .onFailure(t -> System.err.println("Sending command response failed: " + t));
    }
}
