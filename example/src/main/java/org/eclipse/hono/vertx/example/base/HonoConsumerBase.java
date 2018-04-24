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

import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;

import io.vertx.core.json.JsonObject;
import org.apache.qpid.proton.amqp.messaging.Data;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.HonoClient;
import org.eclipse.hono.client.MessageConsumer;
import org.eclipse.hono.client.impl.HonoClientImpl;
import org.eclipse.hono.config.ClientConfigProperties;
import org.eclipse.hono.util.EventDemultiplexer;
import org.eclipse.hono.util.NotificationConstants;
import org.eclipse.hono.util.NotificationDeviceCommandReadyConstants;
import org.eclipse.hono.util.MessageHelper;

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
 * {@link HonoConsumerBase#setEventMode(boolean)} to true.
 */
public class HonoConsumerBase {
    public static final String HONO_CLIENT_USER = "consumer@HONO";
    public static final String HONO_CLIENT_PASSWORD = "verysecret";

    private final Vertx vertx = Vertx.vertx();
    private final HonoClient honoClient;

    private boolean eventMode = false;

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
            if (eventMode) {
                // create the eventHandler by using the helper functionality of EventDemultiplexer - this diversifies
                // the generic event types and notifications and invokes the appropriate handlers
                final Consumer<Message> eventHandler = EventDemultiplexer.createEventHandler(
                        this::handleMessage, this::handleNotification);
                return connectedClient.createEventConsumer(HonoExampleConstants.TENANT_ID,
                        eventHandler,
                        closeHook -> System.err.println("remotely detached consumer link"));
            } else {
                return connectedClient.createTelemetryConsumer(HonoExampleConstants.TENANT_ID,
                        this::handleMessage, closeHook -> System.err.println("remotely detached consumer link"));
            }
        }).setHandler(consumerFuture.completer());

        latch.await();

        if (consumerFuture.succeeded()) {
            System.in.read();
        }
        vertx.close();
    }



    private void printMessage(final Message msg, final String messageType) {
        final String content = ((Data) msg.getBody()).getValue().toString();
        final String deviceId = MessageHelper.getDeviceIdAnnotation(msg);
        final String tenantId = MessageHelper.getTenantIdAnnotation(msg);

        final StringBuilder sb = new StringBuilder("received ").
                append(messageType).
                append(" [tenant: ").append(tenantId).
                append(", device: ").append(deviceId).
                append(", content-type: ").append(msg.getContentType()).
                append(" ]: ").append(content);

        System.out.println(sb.toString());
    }

    /**
     * Handler method for a notification from Hono that was received as event.
     * <p>
     * The tenant, the device, the payload, the content-type and the creation-time will be printed to stdout.
     * <p>
     *
     * @param msg The message that was received.
     */
    private void handleNotification(final Message msg) {
        printMessage(msg, "notification");

        if (NotificationDeviceCommandReadyConstants.CONTENT_TYPE_DEVICE_COMMAND_READINESS_NOTIFICATION.equals(msg.getContentType())) {
            System.out.println("Device command readiness event received.");

            // either try directly to send a command
            this.handleCommandReadinessNotification(msg);
        } else if (NotificationConstants.CONTENT_TYPE_DEVICE_CONNECTION_NOTIFICATION.equals(msg.getContentType())) {
            System.out.println("Device connection event received.");

            this.handleDeviceConnectionNotification(msg);
        }
    }
    /**
     * Handler method for a <em>device ready for command</em> notification from Hono that was received as event.
     *
     * @param msg The message that was received.
     */
    private void handleCommandReadinessNotification(final Message msg) {
        // or make further investigations of the event first:
        final JsonObject notificationPayload = new JsonObject(((Data)msg.getBody()).getValue().toString());
        if (NotificationDeviceCommandReadyConstants.isDeviceCurrentlyReadyForCommands(notificationPayload, msg.getCreationTime())) {
            System.out.println("Device is ready to receive a command.");
            // fill in specific code to e.g. try to send a command here
        }
    }

    /**
     * Handler method for a <em>device connection</em> notification from Hono that was received as event.
     *
     * @param msg The message that was received.
     */
    private void handleDeviceConnectionNotification(final Message msg) {
        // or make further investigations of the event first:
        final JsonObject notificationPayload = new JsonObject(((Data)msg.getBody()).getValue().toString());

        if (NotificationDeviceCommandReadyConstants.isDeviceCurrentlyReadyForCommands(notificationPayload, msg.getCreationTime())) {
            System.out.println("Device is ready to receive a command.");
            // fill in specific code to bind any code to a connected/disconnected event
        }
    }

    /**
     * Handler method for a Message from Hono that was received as telemetry or event data.
     * <p>
     * The tenant, the device, the payload, the content-type, the creation-time and the application properties will be printed to stdout.
     * <p>
     * Notifications are handled by a separated method {@link #handleNotification(Message)}.
     *
     * @param msg The message that was received.
     */
    private void handleMessage(final Message msg) {
        printMessage(msg, eventMode ? "event" : "message");
    }

    /**
     * Gets if event data or telemetry data is consumed.
     *
     * @return True if only event data is consumed, false if only telemetry data is consumed.
     */
    public boolean isEventMode() {
        return eventMode;
    }

    /**
     * Sets the consumer to consume event data or telemetry data.
     *
     * @param value The new value for the event mode.
     */
    public void setEventMode(final boolean value) {
        this.eventMode = value;
    }
}
