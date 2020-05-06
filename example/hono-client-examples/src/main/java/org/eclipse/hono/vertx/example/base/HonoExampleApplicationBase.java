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

package org.eclipse.hono.vertx.example.base;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;

import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.ApplicationClientFactory;
import org.eclipse.hono.client.CommandClient;
import org.eclipse.hono.client.HonoConnection;
import org.eclipse.hono.client.MessageConsumer;
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.config.ClientConfigProperties;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.MessageTap;
import org.eclipse.hono.util.TimeUntilDisconnectNotification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;


/**
 * Example base class for consuming telemetry and event data from devices connected to Hono and sending commands to these devices.
 * <p>
 * This class implements all necessary code to get Hono's messaging consumer client and Hono's command client running.
 * <p>
 * The code consumes data until it receives any input on its console (which finishes it and closes vertx).
 */
public class HonoExampleApplicationBase {

    public static final String HONO_CLIENT_USER = System.getProperty("username", "consumer@HONO");
    public static final String HONO_CLIENT_PASSWORD = System.getProperty("password", "verysecret");
    public static final Boolean USE_PLAIN_CONNECTION = Boolean.valueOf(System.getProperty("plain.connection", "false"));
    public static final Boolean SEND_ONE_WAY_COMMANDS = Boolean.valueOf(System.getProperty("sendOneWayCommands", "false"));

    private static final Logger LOG = LoggerFactory.getLogger(HonoExampleApplicationBase.class);

    protected final int DEFAULT_CONNECT_TIMEOUT_MILLIS = 1000;

    private final Vertx vertx = Vertx.vertx();
    private final ApplicationClientFactory clientFactory;

    /**
     * A map holding a handler to cancel a timer that was started to send commands periodically to a device.
     * Only affects devices that use a connection oriented protocol like MQTT.
     */
    private final Map<String, Handler<Void>> periodicCommandSenderTimerCancelerMap = new HashMap<>();
    /**
     * A map holding the last reported notification for a device being connected. Will be emptied as soon as the notification
     * is handled.
     * Only affects devices that use a connection oriented protocol like MQTT.
     */
    private final Map<String, TimeUntilDisconnectNotification> pendingTtdNotification = new HashMap<>();

    /**
     * The consumer needs one connection to the AMQP 1.0 messaging network from which it can consume data.
     * <p>
     * The client for receiving data is instantiated here.
     * <p>
     * NB: if you want to integrate this code with your own software, it might be necessary to copy the trust
     * store to your project as well and adopt the file path.
     */
    public HonoExampleApplicationBase() {

        final ClientConfigProperties props = new ClientConfigProperties();
        props.setHost(HonoExampleConstants.HONO_AMQP_CONSUMER_HOST);
        props.setPort(HonoExampleConstants.HONO_AMQP_CONSUMER_PORT);
        if (!USE_PLAIN_CONNECTION) {
            props.setUsername(HONO_CLIENT_USER);
            props.setPassword(HONO_CLIENT_PASSWORD);
            props.setTrustStorePath("target/config/hono-demo-certs-jar/trusted-certs.pem");
            props.setHostnameVerificationRequired(false);
        }

        clientFactory = ApplicationClientFactory.create(HonoConnection.newConnection(vertx, props));
    }

    /**
     * Initiate the connection and set the message handling method to treat data that is received.
     *
     * @throws Exception Thrown if the latch is interrupted during waiting or if the read from System.in throws an IOException.
     */
    protected void consumeData() throws Exception {

        final CountDownLatch latch = new CountDownLatch(1);
        final Promise<MessageConsumer> consumerPromise = Promise.promise();

        consumerPromise.future()
        .onComplete(result -> {
            if (!result.succeeded()) {
                LOG.error("clientFactory could not create downstream consumer for [{}:{}]",
                        HonoExampleConstants.HONO_AMQP_CONSUMER_HOST,
                        HonoExampleConstants.HONO_AMQP_CONSUMER_PORT, result.cause());
            }
            latch.countDown();
        });

        clientFactory.connect()
        .compose(connectedClient -> {
            clientFactory.addDisconnectListener(c -> {
                LOG.info("lost connection to Hono, trying to reconnect ...");
            });
            clientFactory.addReconnectListener(c -> {
                LOG.info("reconnected to Hono");
            });
            return createConsumer();
        })
        .onComplete(consumerPromise);

        latch.await();

        if (consumerPromise.future().succeeded()) {
            System.in.read();
        }
        vertx.close();
    }

    /**
     * Create the message consumer that handles the downstream messages and invokes the notification callback
     * {@link #handleCommandReadinessNotification(TimeUntilDisconnectNotification)} if the message indicates that it
     * stays connected for a specified time. Supported are telemetry and event MessageConsumer.
     *
     * @return A succeeded future that contains the MessageConsumer if the creation was successful, a failed
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

        return clientFactory.createEventConsumer(HonoExampleConstants.TENANT_ID,
                eventHandler,
                closeHook -> LOG.error("remotely detached consumer link")
        ).compose(messageConsumer -> clientFactory.createTelemetryConsumer(HonoExampleConstants.TENANT_ID,
                telemetryHandler, closeHook -> LOG.error("remotely detached consumer link")
        ).compose(telemetryMessageConsumer -> {
            LOG.info("Consumer ready for telemetry and event messages.");
            return Future.succeededFuture(telemetryMessageConsumer);
        }).recover(t ->
                Future.failedFuture(t)
        ));
    }

    private void printMessage(final String tenantId, final Message msg, final String messageType) {
        if (LOG.isDebugEnabled()) {
            final String content = MessageHelper.getPayloadAsString(msg);
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
     * For notifications with a positive ttd value (as usual for request-response protocols), the
     * code creates a simple command in JSON.
     * <p>
     * For notifications signalling a connection oriented protocol, the handling is delegated to
     * {@link #handlePermanentlyConnectedCommandReadinessNotification(TimeUntilDisconnectNotification)}.
     *
     * @param notification The notification containing the tenantId, deviceId and the Instant (that
     *                     defines until when this notification is valid). See {@link TimeUntilDisconnectNotification}.
     */
    private void handleCommandReadinessNotification(final TimeUntilDisconnectNotification notification) {
        if (notification.getTtd() <= 0) {
            handlePermanentlyConnectedCommandReadinessNotification(notification);
        } else {
            LOG.info("Device is ready to receive a command : [{}].", notification.toString());
            createCommandClientAndSendCommand(notification);
        }
    }

    /**
     * Handle a ttd notification for permanently connected devices.
     * <p>
     * Instead of immediately handling the notification, it is first put to a map and a timer is started to handle it later.
     * Notifications for the same device that are received before the timer expired, will overwrite the original notification.
     * By this an <em>event flickering</em> (like it could occur when starting the app while several notifications were persisted
     * in the AMQP network) is handled correctly.
     * <p>
     * If the contained <em>ttd</em> is set to -1, a command will be sent periodically every
     * {@link HonoExampleConstants#COMMAND_INTERVAL_FOR_DEVICES_CONNECTED_WITH_UNLIMITED_EXPIRY} seconds to the device
     * until a new notification was received with a <em>ttd</em> set to 0.
     *
     * @param notification The notification of a permanently connected device to handle.
     */
    private void handlePermanentlyConnectedCommandReadinessNotification(final TimeUntilDisconnectNotification notification) {
        final String keyForDevice = notification.getTenantAndDeviceId();

        Optional.ofNullable(pendingTtdNotification.get(keyForDevice)).map(previousNotification -> {
            if (notification.getCreationTime().isAfter(previousNotification.getCreationTime())) {
                LOG.info("Set new ttd value [{}] of notification for [{}]", notification.getTtd(), notification.getTenantAndDeviceId());
                pendingTtdNotification.put(keyForDevice, notification);
            } else {
                LOG.trace("Received notification for [{}] that was already superseded by newer [{}]", notification, previousNotification);
            }
            return false;
        }).orElseGet(() -> {
            pendingTtdNotification.put(keyForDevice, notification);
            // there was no notification available already, so start a handler now
            vertx.setTimer(1000, timerId -> {
                LOG.debug("Handle device notification for [{}].", notification.getTenantAndDeviceId());
                // now take the notification from the pending map and handle it
                Optional.ofNullable(pendingTtdNotification.remove(keyForDevice)).map(notificationToHandle -> {
                    if (notificationToHandle.getTtd() == -1) {
                        LOG.info("Device notified as being ready to receive a command until further notice : [{}].", notificationToHandle.toString());

                        // cancel a still existing timer for this device (if found)
                        cancelPeriodicCommandSender(notification);
                        // immediately send the first command
                        createCommandClientAndSendCommand(notificationToHandle);

                        // for devices that stay connected, start a periodic timer now that repeatedly sends a command
                        // to the device
                        vertx.setPeriodic(
                                (long) HonoExampleConstants.COMMAND_INTERVAL_FOR_DEVICES_CONNECTED_WITH_UNLIMITED_EXPIRY
                                        * 1000,
                                id -> {
                                    createCommandClientAndSendCommand(notificationToHandle).map(commandClient -> {
                                        // register a canceler for this timer directly after it was created
                                        setPeriodicCommandSenderTimerCanceler(id, notification, commandClient);
                                        return null;
                                    });
                                });
                    } else {
                        LOG.info("Device notified as not being ready to receive a command (anymore) : [{}].", notification.toString());
                        cancelPeriodicCommandSender(notificationToHandle);
                        LOG.debug("Device will not receive further commands : [{}].", notification.getTenantAndDeviceId());
                    }
                    return null;
                });
            });

            return true;
        });
    }

    /**
     * Create a command client for the device for that a {@link TimeUntilDisconnectNotification} was received, if no such
     * command client is already active.
     * @param notification The notification that was received for the device.
     */
    private Future<CommandClient> createCommandClientAndSendCommand(final TimeUntilDisconnectNotification notification) {
        return clientFactory.getOrCreateCommandClient(notification.getTenantId())
                .map(commandClient -> {
                    commandClient.setRequestTimeout(calculateCommandTimeout(notification));

                    // send the command upstream to the device
                    if (SEND_ONE_WAY_COMMANDS) {
                        sendOneWayCommandToAdapter(notification.getDeviceId(), commandClient, notification);
                    } else {
                        sendCommandToAdapter(notification.getDeviceId(), commandClient, notification);
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
            return (long) HonoExampleConstants.COMMAND_INTERVAL_FOR_DEVICES_CONNECTED_WITH_UNLIMITED_EXPIRY * 1000;
        } else {
            // let the command expire when the notification expires
            return notification.getMillisecondsUntilExpiry();
        }
    }

    private void setPeriodicCommandSenderTimerCanceler(final Long timerId, final TimeUntilDisconnectNotification ttdNotification, final CommandClient commandClient) {
        this.periodicCommandSenderTimerCancelerMap.put(ttdNotification.getTenantAndDeviceId(), v -> {
            closeCommandClient(commandClient, ttdNotification);
            vertx.cancelTimer(timerId);
            periodicCommandSenderTimerCancelerMap.remove(ttdNotification.getTenantAndDeviceId());
        });
    }

    private boolean cancelPeriodicCommandSender(final TimeUntilDisconnectNotification notification) {
        if (isPeriodicCommandSenderActiveForDevice(notification)) {
            LOG.debug("Cancelling periodic sender for {}", notification.getTenantAndDeviceId());
            periodicCommandSenderTimerCancelerMap.get(notification.getTenantAndDeviceId()).handle(null);
            return true;
        } else {
            LOG.debug("Wanted to cancel periodic sender for {}, but could not find one", notification.getTenantAndDeviceId());
        }
        return false;
    }

    private boolean isPeriodicCommandSenderActiveForDevice(final TimeUntilDisconnectNotification notification) {
        return (periodicCommandSenderTimerCancelerMap.containsKey(notification.getTenantAndDeviceId()));
    }

    /**
     * Send a command to the device for which a {@link TimeUntilDisconnectNotification} was received.
     * <p>
     * If the contained <em>ttd</em> is set to a value @gt; 0, the commandClient will be closed after a response was received.
     * If the contained <em>ttd</em> is set to -1, the commandClient will remain open for further commands to be sent.
     * @param commandClient The command client to be used for sending the command to the device.
     * @param ttdNotification The ttd notification that was received for the device.
     */
    private void sendCommandToAdapter(final String deviceId, final CommandClient commandClient, final TimeUntilDisconnectNotification ttdNotification) {
        final Buffer commandBuffer = buildCommandPayload();
        final String command = "setBrightness";
        if (LOG.isDebugEnabled()) {
            LOG.debug("Sending command [{}] to [{}].", command, ttdNotification.getTenantAndDeviceId());
        }

        commandClient.sendCommand(deviceId, command, "application/json", commandBuffer, buildCommandProperties()).map(result -> {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Successfully sent command payload: [{}].", commandBuffer.toString());
                LOG.debug("And received response: [{}].", Optional.ofNullable(result.getPayload()).orElse(Buffer.buffer()).toString());
            }

            if (ttdNotification.getTtd() != -1) {
                // do not close the command client if device stays connected
                closeCommandClient(commandClient, ttdNotification);
            }
            return result;
        }).otherwise(t -> {
            if (t instanceof ServiceInvocationException) {
                final int errorCode = ((ServiceInvocationException) t).getErrorCode();
                LOG.debug("Command was replied with error code [{}].", errorCode);
            } else {
                LOG.debug("Could not send command : {}.", t.getMessage());
            }

            if (ttdNotification.getTtd() != -1) {
                // do not close the command client if device stays connected
                closeCommandClient(commandClient, ttdNotification);
            }
            return null;
        });
    }

    /**
     * Send a one way command to the device for which a {@link TimeUntilDisconnectNotification} was received.
     * <p>
     * If the contained <em>ttd</em> is set to a value @gt; 0, the commandClient will be closed after a response was received.
     * If the contained <em>ttd</em> is set to -1, the commandClient will remain open for further commands to be sent.
     * @param commandClient The command client to be used for sending the notification command to the device.
     * @param ttdNotification The ttd notification that was received for the device.
     */
    private void sendOneWayCommandToAdapter(final String deviceId, final CommandClient commandClient, final TimeUntilDisconnectNotification ttdNotification) {
        final Buffer commandBuffer = buildOneWayCommandPayload();
        final String command = "sendLifecycleInfo";

        if (LOG.isDebugEnabled()) {
            LOG.debug("Sending one-way command [{}] to [{}].", command, ttdNotification.getTenantAndDeviceId());
        }

        commandClient.sendOneWayCommand(deviceId, command, commandBuffer).map(statusResult -> {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Successfully sent one-way command payload: [{}] and received status [{}].", commandBuffer.toString(), statusResult);
            }

            if (ttdNotification.getTtd() != -1) {
                // do not close the command client if device stays connected
                closeCommandClient(commandClient, ttdNotification);
            }
            return statusResult;
        }).otherwise(t -> {
            if (t instanceof ServiceInvocationException) {
                final int errorCode = ((ServiceInvocationException) t).getErrorCode();
                LOG.debug("One-way command was replied with error code [{}].", errorCode);
            } else {
                LOG.debug("Could not send one-way command : {}.", t.getMessage());
            }

            if (ttdNotification.getTtd() != -1) {
                // do not close the command client if device stays connected
                closeCommandClient(commandClient, ttdNotification);
            }
            return null;
        });
    }

    private void closeCommandClient(final CommandClient commandClient, final TimeUntilDisconnectNotification ttdNotification) {
        // do not close the command client if device stays connected
        commandClient.close(v -> {
            if (LOG.isDebugEnabled()) {
                LOG.trace("Closed commandClient for [{}].", ttdNotification.getTenantAndDeviceId());
            }
        });
    }

    /**
     * Provides an application property that is suitable to be sent with the command upstream.
     *
     * @return Map The application property map.
     */
    private Map<String, Object> buildCommandProperties() {
        final Map<String, Object> applicationProperties = new HashMap<>(1);
        applicationProperties.put("appId", "example#1");
        return applicationProperties;
    }

    private Buffer buildCommandPayload() {
        final JsonObject jsonCmd = new JsonObject().put("brightness", (int) (Math.random() * 100));
        return Buffer.buffer(jsonCmd.encodePrettily());
    }

    private Buffer buildOneWayCommandPayload() {
        final JsonObject jsonCmd = new JsonObject().put("info", "app restarted.");
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
