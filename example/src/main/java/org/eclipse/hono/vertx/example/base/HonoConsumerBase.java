/*******************************************************************************
 * Copyright (c) 2016, 2018 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.vertx.example.base;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;

import org.apache.qpid.proton.amqp.messaging.Data;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.CommandClient;
import org.eclipse.hono.client.HonoClient;
import org.eclipse.hono.client.MessageConsumer;
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.client.impl.HonoClientImpl;
import org.eclipse.hono.config.ClientConfigProperties;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.MessageTap;
import org.eclipse.hono.util.TimeUntilDisconnectNotification;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.proton.ProtonConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Example base class for consuming telemetry and event data from devices connected to Hono and sending commands to these devices.
 * <p>
 * This class implements all necessary code to get Hono's messaging consumer client and Hono's command client running.
 * <p>
 * The code consumes data until it receives
 * any input on it's console (which finishes it and closes vertx).
 */
public class HonoConsumerBase {
    public static final Boolean USE_PLAIN_CONNECTION = Boolean.valueOf(System.getProperty("plain.connection", "false"));
    public static final String HONO_CLIENT_USER = System.getProperty("username", "consumer@HONO");
    public static final String HONO_CLIENT_PASSWORD = System.getProperty("password", "verysecret");

    protected final int DEFAULT_CONNECT_TIMEOUT_MILLIS = 1000;

    private final Vertx vertx = Vertx.vertx();
    private final HonoClient honoClient;

    private final Map<String, Handler<Void>> periodicCommandSenderTimerCancelerMap = new HashMap<>();

    private static final Logger LOG = LoggerFactory.getLogger(HonoConsumerBase.class);
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
        if (!USE_PLAIN_CONNECTION) {
            props.setUsername(HONO_CLIENT_USER);
            props.setPassword(HONO_CLIENT_PASSWORD);
            props.setTrustStorePath("target/config/hono-demo-certs-jar/trusted-certs.pem");
            props.setHostnameVerificationRequired(false);
        }

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
                LOG.error("honoClient could not create downstream consumer for [{}:{}]",
                        HonoExampleConstants.HONO_AMQP_CONSUMER_HOST,
                        HonoExampleConstants.HONO_AMQP_CONSUMER_PORT, result.cause());
            }
            latch.countDown();
        });

        honoClient.connect(this::onDisconnect).
                compose(connectedClient -> createConsumer()).
                setHandler(consumerFuture.completer());

        latch.await();

        if (consumerFuture.succeeded()) {
            System.in.read();
        }
        vertx.close();
    }

    /**
     * Create the message consumer that handles the downstream messages and invokes the notification callback
     * {@link #handleCommandReadinessNotification(TimeUntilDisconnectNotification)} if the message indicates that it
     * stays connected for a specified time. Supported are telemetry and event MessageConsumer.
     *
     * @return Future A succeeded future that contains the MessageConsumer if the creation was successful, a failed
     *         Future otherwise.
     */
    private Future<MessageConsumer> createConsumer() {
        // create the eventHandler by using the helper functionality for demultiplexing messages to callbacks
        final Consumer<Message> eventHandler = MessageTap.getConsumer(
                this::handleEventMessage, this::handleCommandReadinessNotification);

        // create the telemetryHandler by using the helper functionality for demultiplexing messages to
        // callbacks
        final Consumer<Message> telemetryHandler = MessageTap.getConsumer(
                this::handleTelemetryMessage, this::handleCommandReadinessNotification);

        return honoClient.createEventConsumer(HonoExampleConstants.TENANT_ID,
                eventHandler,
                closeHook -> LOG.error("remotely detached consumer link")
        ).compose(messageConsumer -> honoClient.createTelemetryConsumer(HonoExampleConstants.TENANT_ID,
                telemetryHandler, closeHook -> LOG.error("remotely detached consumer link")
        ).compose(telemetryMessageConsumer -> {
            LOG.info("Consumer ready for telemetry and event messages.");
            return Future.succeededFuture(telemetryMessageConsumer);
        }).recover(t ->
                Future.failedFuture(t)
        ));
    }

    /**
     * Method to act as a disconnect handler. If called, it tries to reconnect to Hono by creating the MessageConsumer
     * again and continue to wait for incoming downstream messages.
     * <p>
     * The reconnection attempt is delayed by {@link #DEFAULT_CONNECT_TIMEOUT_MILLIS} milliseconds and set this method
     * as disconnect handler again (for potential future disconnect handling).
     *
     * @param con The ProtonConnection for that the connection was lost.
     */
    private void onDisconnect(final ProtonConnection con) {

        // give Vert.x some time to clean up NetClient
        vertx.setTimer(DEFAULT_CONNECT_TIMEOUT_MILLIS, reconnect -> {
            LOG.info("attempting to re-connect to Hono ...");
            honoClient.connect(this::onDisconnect).
                    compose(connectedClient -> createConsumer()).
                    map(messageConsumer -> {
                        LOG.info("Reconnected to Hono.");
                        return null;
                    });
        });
    }

    private void printMessage(final String tenantId, final Message msg, final String messageType) {
        if (LOG.isDebugEnabled()) {
            final Data body = (Data) msg.getBody();
            final String content = body != null ? body.getValue().toString() : "";
            final String deviceId = MessageHelper.getDeviceId(msg);

            final StringBuilder sb = new StringBuilder("received ").
                    append(messageType).
                    append(" [tenant: ").append(tenantId).
                    append(", device: ").append(deviceId).
                    append(", content-type: ").append(msg.getContentType()).
                    append(" ]: [").append(content).append("].");

            LOG.debug(sb.toString());
        }
    }

    /**
     * Handler method for a <em>device ready for command</em> notification (by an explicit event or contained implicitly in
     * another message).
     * <p>
     * The code creates a simple command in JSON.
     *
     * @param notification The notification containing the tenantId, deviceId and the Instant (that
     *                     defines until when this notification is valid). See {@link TimeUntilDisconnectNotification}.
     */
    private void handleCommandReadinessNotification(final TimeUntilDisconnectNotification notification) {
        if (notification.getMillisecondsUntilExpiry() == 0) {
            LOG.info("Device notified as not being ready to receive a command (anymore) : [{}].", notification.toString());
            cancelPeriodicCommandSender(notification);
        } else {
            LOG.info("Device is ready to receive a command : [{}].", notification.toString());
            createCommandClientAndSendCommand(notification);
        }
    }

    /**
     * Create a command client for the device for that a {@link TimeUntilDisconnectNotification} was received.
     * <p>
     * If the contained <em>ttd</em> is set to -1, a command will be sent periodically every
     * {@link HonoExampleConstants#COMMAND_INTERVAL_FOR_DEVICES_CONNECTED_WITH_UNLIMITED_EXPIRY} seconds to the device
     * until a new notification was received with a <em>ttd</em> set to 0. See {@link #handleCommandReadinessNotification(TimeUntilDisconnectNotification)}
     * that cancels the periodic timer again.
     * @param notification The notification that was received for the device.
     */
    private void createCommandClientAndSendCommand(final TimeUntilDisconnectNotification notification) {
        honoClient.getOrCreateCommandClient(notification.getTenantId(), notification.getDeviceId()).map(commandClient -> {
            commandClient.setRequestTimeout(calculateCommandTimeout(notification));

            // send the command upstream to the device
            if (notification.getTtd() == -1) {
                // cancel a still existing timer for this device (if found)
                cancelPeriodicCommandSender(notification);

                sendCommandToAdapter(commandClient, notification);

                // for devices that stay connected, start a periodic timer now that repeatedly sends a command to the device
                final long timerId = vertx.setPeriodic(HonoExampleConstants.COMMAND_INTERVAL_FOR_DEVICES_CONNECTED_WITH_UNLIMITED_EXPIRY*1000, id -> {
                    sendCommandToAdapter(commandClient, notification);
                });
                // register a canceler for this timer directly after it was created
                setPeriodicCommandSenderTimerCanceler(timerId, notification, commandClient);
            } else {
                sendCommandToAdapter(commandClient, notification);
            }
            return commandClient;
        }).otherwise(t -> {
            LOG.error("Could not create command client", t);
            return null;
        });
    }

    /**
     * Calculate the timeout for a command that is tried to be sent to a device for which a {@link TimeUntilDisconnectNotification}
     * was received.
     *
     * @param notification The notification that was received for the device.
     * @return The timeout to be set for the command.
     */
    private long calculateCommandTimeout(final TimeUntilDisconnectNotification notification) {
        if (notification.getTtd() == -1) {
            // let the command expire directly before the next periodic timer is started
            return HonoExampleConstants.COMMAND_INTERVAL_FOR_DEVICES_CONNECTED_WITH_UNLIMITED_EXPIRY*1000;
        } else {
            // let the command expire when the notification expires
            return notification.getMillisecondsUntilExpiry();
        }
    }

    private void setPeriodicCommandSenderTimerCanceler(final Long timerId, final TimeUntilDisconnectNotification notification, final CommandClient commandClient) {
        this.periodicCommandSenderTimerCancelerMap.put(notification.getTenantAndDeviceId(), v -> {
            commandClient.close(ignore -> {
                if (LOG.isDebugEnabled()) {
                    LOG.trace("Closed commandClient for [{}].", notification.getTenantAndDeviceId());
                }
            });
            vertx.cancelTimer(timerId);
            periodicCommandSenderTimerCancelerMap.remove(notification.getTenantAndDeviceId());
        });
    }

    private void cancelPeriodicCommandSender(final TimeUntilDisconnectNotification notification) {
        if (isDeviceConnectedForCommands(notification)) {
            periodicCommandSenderTimerCancelerMap.get(notification.getTenantAndDeviceId()).handle(null);
        }
    }

    private boolean isDeviceConnectedForCommands(final TimeUntilDisconnectNotification notification) {
        return (periodicCommandSenderTimerCancelerMap.containsKey(notification.getTenantAndDeviceId()));
    }

    /**
     * Send a command to the device for which a {@link TimeUntilDisconnectNotification} was received.
     * <p>
     * If the contained <em>ttd</em> is set to a value @gt; 0, the commandClient will be closed after a response was received.
     * If the contained <em>ttd</em> is set to -1, the commandClient will remain open for further commands to be sent.
     * @param commandClient The command client to be used for sending the command to the device.
     * @param notification The notification that was received for the device.
     */
    private void sendCommandToAdapter(final CommandClient commandClient, final TimeUntilDisconnectNotification notification) {
        final Buffer commandBuffer = buildCommandPayload();
        final String command = "setBrightness";

        if (LOG.isDebugEnabled()) {
            LOG.debug("Sending command [{}] to [{}].", command, notification.getTenantAndDeviceId());
        }

        commandClient.sendCommand(command, commandBuffer).map(result -> {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Successfully sent command payload: [{}].", commandBuffer.toString());
                LOG.debug("And received response: [{}].", Optional.ofNullable(result.getPayload()).orElse(Buffer.buffer()).toString());
            }

            if (notification.getTtd() != -1) {
                // do not close the command client if device stays connected
                commandClient.close(v -> {
                    if (LOG.isDebugEnabled()) {
                        LOG.trace("Closed commandClient for [{}].", notification.getTenantAndDeviceId());
                    }
                });
            } else {
                // cancel periodic timer and send a command again
                cancelPeriodicCommandSender(notification);
                createCommandClientAndSendCommand(notification);
            }
            return result;
        }).otherwise(t -> {
            if (t instanceof ServiceInvocationException) {
                final int errorCode = ((ServiceInvocationException) t).getErrorCode();
                LOG.debug("Command was replied with error code [{}].", errorCode);
            } else {
                LOG.debug("Could not send command : {}.", t.getMessage());
            }

            if (notification.getTtd() != -1) {
                // do not close the command client if device stays connected
                commandClient.close(v -> {
                    if (LOG.isDebugEnabled()) {
                        LOG.trace("Closed commandClient for [{}].", notification.getTenantAndDeviceId());
                    }
                });
            }
            return null;
        });
    }

    private Buffer buildCommandPayload() {
        final JsonObject jsonCmd = new JsonObject().put("brightness", (int) (Math.random() * 100));
        return Buffer.buffer(jsonCmd.encodePrettily());
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
}
