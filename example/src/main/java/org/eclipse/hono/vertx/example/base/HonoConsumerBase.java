/**
 * Copyright (c) 2017, 2018 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 1.0 which is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * SPDX-License-Identifier: EPL-1.0
 */

package org.eclipse.hono.vertx.example.base;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import org.apache.qpid.proton.amqp.messaging.Data;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.HonoClient;
import org.eclipse.hono.client.MessageConsumer;
import org.eclipse.hono.client.impl.HonoClientImpl;
import org.eclipse.hono.config.ClientConfigProperties;
import org.eclipse.hono.util.MessageTap;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.TimeUntilDisconnectNotification;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.proton.ProtonClientOptions;


/**
 * Example base class for consuming data from Hono.
 * <p>
 * This class implements all necessary code to get Hono's messaging consumer client running.
 * The code consumes data until it receives
 * any input on it's console (which finishes it and closes vertx).
 * <p>
 * By default, this class consumes telemetry data. This can be changed to event data by setting
 * {@link HonoConsumerBase#setMode(MODE)} to {@link MODE#EVENT}.
 */
public class HonoConsumerBase {
    public static final String HONO_CLIENT_USER = "consumer@HONO";
    public static final String HONO_CLIENT_PASSWORD = "verysecret";

    private final Vertx vertx = Vertx.vertx();
    private final HonoClient honoClient;

    /**
     * Enum to define from which source messages are consumed.
     * <p>
     * Instead of using a {@code Boolean} this enum allows for more modes in future extensions (e.g. to receive messages
     * from both channels).
     */
    public enum MODE {
        TELEMETRY, EVENT
    }

    private MODE mode = MODE.TELEMETRY;

    /**
     * The consumer needs one connection to the AMQP 1.0 messaging network from which it can consume data.
     * <p>
     * The client for receiving data is instantiated here.
     * <p>
     * NB: if you want to integrate this code with your own software, it might be necessary to copy the truststore to
     * your project as well and adopt the file path.
     */
    public HonoConsumerBase() {

        final ClientConfigProperties props = new ClientConfigProperties();
        props.setHost(HonoExampleConstants.HONO_AMQP_CONSUMER_HOST);
        props.setPort(HonoExampleConstants.HONO_AMQP_CONSUMER_PORT);
        props.setUsername(HONO_CLIENT_USER);
        props.setPassword(HONO_CLIENT_PASSWORD);
        props.setTrustStorePath("target/config/hono-demo-certs-jar/trusted-certs.pem");
        props.setHostnameVerificationRequired(false);

        honoClient = new HonoClientImpl(vertx, props);
    }

    /**
     * Initiate the connection and set the message handling method to treat data that is received.
     *
     * @throws Exception Thrown if the latch is interrupted during waiting or if the read from System.in throws an IOException.
     */
    protected void consumeData() throws Exception {

        final CountDownLatch latch = new CountDownLatch(1);
        final Future<MessageConsumer> consumerFuture = Future.future();

        consumerFuture.setHandler(result -> {
            if (!result.succeeded()) {
                System.err.println("honoClient could not create telemetry consumer for " + HonoExampleConstants.HONO_AMQP_CONSUMER_HOST
                        + ":" + HonoExampleConstants.HONO_AMQP_CONSUMER_PORT + " : " + result.cause());
            }
            latch.countDown();
        });

        honoClient.connect(new ProtonClientOptions()).compose(connectedClient -> {
            switch (mode) {
            case EVENT:
                // create the eventHandler by using the helper functionality for demultiplexing messages to callbacks
                final Consumer<Message> eventHandler = MessageTap.getConsumer(
                        this::handleEventMessage, this::handleCommandReadinessNotification);
                return connectedClient.createEventConsumer(HonoExampleConstants.TENANT_ID,
                        eventHandler,
                        closeHook -> System.err.println("remotely detached consumer link"));
            case TELEMETRY:
                // create the telemetryHandler by using the helper functionality for demultiplexing messages to callbacks
                final Consumer<Message> telemetryHandler = MessageTap.getConsumer(
                        this::handleTelemetryMessage, this::handleCommandReadinessNotification);
                return connectedClient.createTelemetryConsumer(HonoExampleConstants.TENANT_ID,
                        telemetryHandler, closeHook -> System.err.println("remotely detached consumer link"));
            default:
                return null;
            }
        }).setHandler(consumerFuture.completer());

        latch.await();

        if (consumerFuture.succeeded()) {
            System.in.read();
        }
        vertx.close();
    }

    private void printMessage(final String tenantId, final Message msg, final String messageType) {
        final String content = ((Data) msg.getBody()).getValue().toString();
        final String deviceId = MessageHelper.getDeviceId(msg);

        final StringBuilder sb = new StringBuilder("received ").
                append(messageType).
                append(" [tenant: ").append(tenantId).
                append(", device: ").append(deviceId).
                append(", content-type: ").append(msg.getContentType()).
                append(" ]: ").append(content);

        System.out.println(sb.toString());
    }

    /**
     * Handler method for a <em>device ready for command</em> notification (by an explicit event or contained implicit in
     * another message).
     * <p>
     * The code creates a simple command in JSON
     *
     * @param notification The notification containing the tenantId, deviceId and the Instant (that
     *                     defines until when this notification is valid). See {@link TimeUntilDisconnectNotification}.
     */
    private void handleCommandReadinessNotification(final TimeUntilDisconnectNotification notification) {

        System.out.println(String.format("Device is ready to receive a command : <%s>.", notification.toString()));

        final String tenantId = notification.getTenantId();
        final String deviceId = notification.getDeviceId();

        honoClient.getOrCreateCommandClient(tenantId, deviceId).map(commandClient -> {
            final JsonObject jsonCmd = new JsonObject().put("brightness", (int)(Math.random() * 100));
            final Buffer commandBuffer = Buffer.buffer(jsonCmd.encodePrettily());

            // send the command upstream to the device
            commandClient.sendCommand("setBrightness", commandBuffer).map(result -> {
                System.out.println("Successfully sent command and received response");
                return result;
            }).otherwise(t -> {
                System.out.println(String.format("Could not send command or did not receive a response : %s", t.getMessage()));
                return (Buffer) null;
            });
            return commandClient;
        }).otherwise(t -> {
            System.err.println(String.format("Could not create command client : %s", t.getMessage()));
            return null;
        });
    }

    /**
     * Handler method for a Message from Hono that was received as telemetry data.
     * <p>
     * The tenant, the device, the payload, the content-type, the creation-time and the application properties will be printed to stdout.
     *
     * @param msg The message that was received.
     */
    private void handleTelemetryMessage(final Message msg) {
        printMessage(HonoExampleConstants.TENANT_ID, msg, "telemetry");
    }

    /**
     * Handler method for a Message from Hono that was received as event data.
     * <p>
     * The tenant, the device, the payload, the content-type, the creation-time and the application properties will be printed to stdout.
     *
     * @param msg The message that was received.
     */
    private void handleEventMessage(final Message msg) {
        printMessage(HonoExampleConstants.TENANT_ID, msg, "event");
    }

    /**
     * Gets the {@link MODE} of this consumer: event data or telemetry data.
     *
     * @return The mode of this consumer.
     */
    public MODE getMode() {
        return mode;
    }

    /**
     * Sets the consumer to consume event data or telemetry data.
     *
     * @param value The new {@link MODE} value.
     */
    public void setMode(final MODE value) {
        this.mode = value;
    }
}
