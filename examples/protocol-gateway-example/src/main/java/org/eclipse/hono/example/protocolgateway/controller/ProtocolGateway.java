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

package org.eclipse.hono.example.protocolgateway.controller;

import java.net.HttpURLConnection;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import javax.enterprise.context.Dependent;
import javax.enterprise.event.Observes;
import javax.inject.Inject;
import javax.inject.Named;

import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.amqp.connection.AmqpUtils;
import org.eclipse.hono.client.command.CommandConsumer;
import org.eclipse.hono.client.device.amqp.AmqpAdapterClientFactory;
import org.eclipse.hono.example.protocolgateway.TcpServer;
import org.eclipse.hono.util.EventConstants;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.Strings;
import org.eclipse.hono.util.TelemetryConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.NetSocket;
import io.vertx.core.parsetools.RecordParser;
import io.vertx.proton.ProtonDelivery;

/**
 * Example protocol gateway service to send messages to Hono's AMQP adapter.
 */
@Dependent
public class ProtocolGateway {

    private static final String CMD_LOGIN = "login";
    private static final String CMD_SUBSCRIBE = "subscribe";
    private static final String CMD_UNSUBSCRIBE = "unsubscribe";

    private static final String CONTENT_TYPE_BINARY_OPAQUE = "binary/opaque";

    private static final String KEY_COMMAND_CONSUMER = "command_consumer";
    private static final String KEY_DEVICE_ID = MessageHelper.APP_PROPERTY_DEVICE_ID;

    private static final Logger LOG = LoggerFactory.getLogger(ProtocolGateway.class);

    private static final String MSG_DEVICE_NOT_LOGGED_IN = "device not logged in";

    private final AmqpAdapterClientFactory amqpAdapterClientFactory;
    private final String tenant;
    private final TcpServer server;

    /**
     * Creates a new service for a client factory.
     *
     * @param factory The factory for creating clients for Hono's AMQP adapter.
     * @param server The TCP server that devices connect to.
     * @param tenant The tenant that this gateway can accept device connections for.
     */
    @Inject
    public ProtocolGateway(
            final AmqpAdapterClientFactory factory,
            final TcpServer server,
            @Named("TENANT_ID")
            final String tenant) {
        this.amqpAdapterClientFactory = factory;
        this.server = server;
        this.tenant = tenant;
    }

    /**
     * Starts up the protocol gateway.
     *
     * @param ev The startup event.
     */
    public void onStart(@Observes final StartupEvent ev) {

        server.setConnectHandler(this::handleConnect);
        amqpAdapterClientFactory.connect()
                .onSuccess(ok -> LOG.info("successfully connected to Hono's AMQP adapter"))
                .onFailure(t -> LOG.error("failed to connect to Hono's AMQP adapter"))
                .compose(ok -> server.start())
                .onSuccess(s -> LOG.info("successfully started example protocol gateway [tenant: {}]", tenant))
                .onFailure(t -> LOG.error("failed to start protocol gateway", t));
    }

    /**
     * Stops the gateway.
     *
     * @param ev The event indicating shutdown.
     */
    public void onStop(final @Observes ShutdownEvent ev) {
        server.stop().onComplete(r -> amqpAdapterClientFactory.disconnect());
    }

    void handleConnect(final NetSocket socket) {

        final Map<String, Object> dict = new HashMap<>();
        final RecordParser commandParser = RecordParser.newDelimited("\n", socket);
        commandParser.endHandler(end -> socket.close());
        commandParser.exceptionHandler(t -> {
            LOG.debug("error processing data from device", t);
            socket.close();
        });
        commandParser.handler(data -> handleData(socket, dict, data));
        socket.closeHandler(remoteClose -> {
            LOG.debug("device closed connection");
            Optional.ofNullable((CommandConsumer) dict.get(KEY_COMMAND_CONSUMER))
                .ifPresent(c -> c.close(null).onComplete(res -> LOG.debug("closed device's command consumer")));
            socket.close();
        });

        socket.write(String.format(
                "Welcome to the Protocol Gateway for devices of tenant [%s], please enter a command\n",
                tenant));
        LOG.debug("connection with client established");
    }

    private void handleData(final NetSocket socket, final Map<String, Object> dictionary, final Buffer buffer) {

        final String data = buffer.toString();
        LOG.debug("received data from device: [{}]", data);
        // split up in command token [0] and args [1]
        final String[] command = data.split(" ", 2);
        executeCommand(command, socket, dictionary)
            .onSuccess(c -> socket.write("OK\n"))
            .onFailure(t -> {
                LOG.debug("failed to process data provided by device");
                socket.write("FAILED: " + t.getMessage() + "\n");
            });
    }

    private Future<Void> executeCommand(final String[] command, final NetSocket socket, final Map<String, Object> dictionary) {

        final String commandName = command[0];
        final String args = command.length > 1 ? command[1] : null;

        LOG.debug("processing command: {}", commandName);
        switch (commandName) {
        case CMD_LOGIN:
            return login(args, socket, dictionary);
        case TelemetryConstants.TELEMETRY_ENDPOINT:
        case TelemetryConstants.TELEMETRY_ENDPOINT_SHORT:
            return sendTelemetry(args, socket, dictionary);
        case EventConstants.EVENT_ENDPOINT:
        case EventConstants.EVENT_ENDPOINT_SHORT:
            return sendEvent(args, socket, dictionary);
        case CMD_SUBSCRIBE:
            return subscribe(socket, dictionary);
        case CMD_UNSUBSCRIBE:
            return unsubscribe(socket, dictionary);
        default:
            LOG.debug("unsupported command [{}]", commandName);
            return Future.failedFuture("no such command");
        }
    }

    private Future<Void> login(final String args, final NetSocket socket, final Map<String, Object> dictionary) {
        if (Strings.isNullOrEmpty(args)) {
            return Future.failedFuture("missing device identifier");
        } else {
            final String deviceId = args;
            LOG.info("authenticating device [id: {}]", deviceId);
            dictionary.put(KEY_DEVICE_ID, deviceId);
            socket.write(String.format("device [%s] logged in\n", deviceId));
            return Future.succeededFuture();
        }
    }

    private Future<Void> sendTelemetry(final String args, final NetSocket socket, final Map<String, Object> dictionary) {

        final Promise<Void> result = Promise.promise();
        LOG.debug("Command: send Telemetry");
        Optional.ofNullable(dictionary.get(KEY_DEVICE_ID))
            .ifPresentOrElse(
                    obj -> {
                        final String deviceId = (String) obj;
                        if (Strings.isNullOrEmpty(args)) {
                            result.fail("missing params qos and payload");
                        } else {
                            final String[] params = args.split(" ", 2);
                            final String qos = params[0];
                            final var payload = Optional.ofNullable(params[1]).map(p -> Buffer.buffer(p)).orElse(null);
                            amqpAdapterClientFactory.getOrCreateTelemetrySender()
                                .compose(sender -> {
                                    if ("0".equals(qos)) {
                                        return sender.send(deviceId, payload, CONTENT_TYPE_BINARY_OPAQUE);
                                    } else {
                                        return sender.sendAndWaitForOutcome(deviceId, payload, CONTENT_TYPE_BINARY_OPAQUE);
                                    }
                                })
                                .map((Void) null)
                                .onComplete(result);
                        }
                    },
                    () -> {
                        result.fail(MSG_DEVICE_NOT_LOGGED_IN);
                    });
        return result.future();
    }

    private Future<Void> sendEvent(final String args, final NetSocket socket, final Map<String, Object> dictionary) {

        final Promise<Void> result = Promise.promise();
        LOG.debug("Command: send Event");
        Optional.ofNullable(dictionary.get(KEY_DEVICE_ID))
            .ifPresentOrElse(
                    obj -> {
                        final String deviceId = (String) obj;
                        if (Strings.isNullOrEmpty(args)) {
                            result.fail("missing payload");
                        } else {
                            final var payload = Buffer.buffer(args);
                            amqpAdapterClientFactory.getOrCreateEventSender()
                                .compose(sender -> sender.send(deviceId, payload, CONTENT_TYPE_BINARY_OPAQUE))
                                .map((Void) null)
                                .onComplete(result);
                        }
                    },
                    () -> {
                        result.fail(MSG_DEVICE_NOT_LOGGED_IN);
                    });
        return result.future();
    }

    private Future<Void> subscribe(final NetSocket socket, final Map<String, Object> dictionary) {

        final Promise<Void> result = Promise.promise();
        LOG.debug("Command: subscribe");
        Optional.ofNullable(dictionary.get(KEY_DEVICE_ID))
        .ifPresentOrElse(
                obj -> {
                    final String deviceId = (String) obj;
                    subscribe(deviceId, socket)
                        .map(consumer -> {
                            dictionary.put(KEY_COMMAND_CONSUMER, consumer);
                            return (Void) null;
                        })
                        .onComplete(result);
                },
                () -> {
                    result.fail(MSG_DEVICE_NOT_LOGGED_IN);
                });
        return result.future();
    }

    private Future<Void> unsubscribe(final NetSocket socket, final Map<String, Object> dictionary) {

        final Promise<Void> result = Promise.promise();
        LOG.debug("Command: unsubscribe");
        Optional.ofNullable(dictionary.get(KEY_COMMAND_CONSUMER))
            .ifPresentOrElse(
                    obj -> {
                        ((CommandConsumer) obj).close(null)
                                .onComplete(result);
                    },
                    () -> result.fail("device not subscribed to commands"));
        return result.future();
    }

    /**
     * Subscribes to commands for a device.
     *
     * @param deviceId The device to subscribe for.
     * @param socket The socket to use for sending commands to the device.
     * @return A future indicating the outcome.
     */
    private Future<CommandConsumer> subscribe(final String deviceId, final NetSocket socket) {

        final Consumer<Message> messageHandler = m -> {

            final String commandPayload = AmqpUtils.getPayloadAsString(m);
            final boolean isOneWay = m.getReplyTo() == null;
            if (isOneWay) {
                LOG.debug("received one-way command [name: {}]: {}", m.getSubject(), commandPayload);
                socket.write(String.format("ONE-WAY COMMAND [name: %s]: %s\n", m.getSubject(), commandPayload));
            } else {
                LOG.debug("received command [name: {}]: {}", m.getSubject(), commandPayload);
                if ("tellTime".equals(m.getSubject())) {
                    respondWithTime(m)
                        .onSuccess(delivery -> LOG.debug("sent response to command [name: {}, outcome: {}]",
                                    m.getSubject(), delivery.getRemoteState().getType()))
                        .onFailure(t -> LOG.info("failed to send response to command [name: {}]",
                                    m.getSubject(), t));
                }
            }
        };
        return amqpAdapterClientFactory.createDeviceSpecificCommandConsumer(deviceId, messageHandler);
    }

    private Future<ProtonDelivery> respondWithTime(final Message command) {

        final Buffer payload = Buffer.buffer(String.format(
                "myCurrentTime: %s",
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").format(LocalDateTime.now())));
        final String deviceId = AmqpUtils.getDeviceId(command);

        return amqpAdapterClientFactory.getOrCreateCommandResponseSender()
            .compose(responder -> responder.sendCommandResponse(
                        deviceId,
                        command.getReplyTo(),
                        (String) command.getCorrelationId(),
                        HttpURLConnection.HTTP_OK,
                        payload,
                        "text/plain"));
    }
}
