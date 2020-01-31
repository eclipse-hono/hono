/*******************************************************************************
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.client.impl;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.qpid.proton.amqp.messaging.Rejected;
import org.apache.qpid.proton.amqp.transport.ErrorCondition;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.Command;
import org.eclipse.hono.client.CommandContext;
import org.eclipse.hono.client.HonoConnection;
import org.eclipse.hono.client.ResourceConflictException;
import org.eclipse.hono.tracing.TracingHelper;
import org.eclipse.hono.util.CommandConstants;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.ResourceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.proton.ProtonDelivery;
import io.vertx.proton.ProtonHelper;
import io.vertx.proton.ProtonQoS;
import io.vertx.proton.ProtonReceiver;

/**
 * A wrapper around an AMQP receiver link for consuming commands directed to a gateway or device.
 * <p>
 * Includes special support for gateways:
 * If a gateway creates multiple command subscriptions for specific devices connected to it, the one
 * <em>GatewayOrDeviceSpecificCommandConsumer</em> instance created for the gateway will contain
 * multiple {@link CommandHandlerWrapper} instances containing the device-specific handlers for
 * the commands.
 * <p>
 * In a non-gateway scenario, the <em>GatewayOrDeviceSpecificCommandConsumer</em> instance will
 * just contain the one {@link CommandHandlerWrapper} instance for handling commands to the device.
 */
public final class DestinationCommandConsumer extends CommandConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(DestinationCommandConsumer.class);

    private final Map<String, CommandHandlerWrapper> commandHandlers = new HashMap<>();
    private final String tenantId;
    private final String gatewayOrDeviceId;
    private final AtomicBoolean closedCalled = new AtomicBoolean();

    private DestinationCommandConsumer(final HonoConnection connection, final ProtonReceiver receiver,
            final String tenantId, final String gatewayOrDeviceId) {
        super(connection, receiver);
        this.tenantId = tenantId;
        this.gatewayOrDeviceId = Objects.requireNonNull(gatewayOrDeviceId);
    }

    /**
     * Adds a handler for commands targeted at a device that is connected either directly or via a gateway.
     *
     * @param deviceId The identifier of the device that is the target of the commands being handled.
     * @param gatewayId The identifier of the gateway that is acting on behalf of the device that is
     *                  the target of the commands being handled, or {@code null} otherwise.
     * @param commandHandler The command handler.
     * @param remoteCloseHandler The handler to be invoked when the command consumer is closed remotely. May be
     *            {@code null}.
     * @return A future indicating whether adding the handler was successful.
     *         <p>
     *         The future will be failed with a {@link ResourceConflictException}
     *         if the consumer already contains a handler with the same device id.
     *         <p>
     *         Otherwise, the future will be succeeded.
     * @throws NullPointerException If deviceId or commandHandler is {@code null}.
     * @throws IllegalArgumentException If the device or gateway id of the given handler does
     *                                  not match this command consumer.
     */
    public Future<Void> addDeviceSpecificCommandHandler(final String deviceId, final String gatewayId,
            final Handler<CommandContext> commandHandler, final Handler<Void> remoteCloseHandler) {
        return addDeviceSpecificCommandHandler(new CommandHandlerWrapper(deviceId, gatewayId, commandHandler, remoteCloseHandler));
    }

    /**
     * Adds a handler for commands targeted at a device that is connected either directly or via a gateway.
     * 
     * @param handler The consumer to add.
     * @return A future indicating whether adding the handler was successful.
     *         <p>
     *         The future will be failed with a {@link ResourceConflictException}
     *         if the consumer already contains a handler with the same device id.
     *         <p>
     *         Otherwise, the future will be succeeded.
     * @throws NullPointerException If the given handler is {@code null}.
     * @throws IllegalArgumentException If the device or gateway id of the given handler does
     *                                  not match this command consumer.
     */
    public Future<Void> addDeviceSpecificCommandHandler(final CommandHandlerWrapper handler) {
        Objects.requireNonNull(handler);
        if (!handlerIsForConsumerGatewayOrDevice(handler.getDeviceId(), handler.getGatewayId())) {
            LOG.error("cannot add handler with non-matching device/gateway id [consumer device id: {}, handler: {}",
                    gatewayOrDeviceId, handler);
            throw new IllegalArgumentException("invalid handler given");
        }
        if (commandHandlers.containsKey(handler.getDeviceId())) {
            LOG.debug("cannot create concurrent command consumer [device-id: {}]", handler.getDeviceId());
            return Future.failedFuture(new ResourceConflictException("message consumer already in use"));
        }
        commandHandlers.put(handler.getDeviceId(), handler);
        return Future.succeededFuture(null);
    }

    private boolean handlerIsForConsumerGatewayOrDevice(final String handlerDeviceId, final String handlerGatewayId) {
        if (handlerGatewayId == null) {
            // the given handler is for commands directed directly at a device (no gateway in between)
            // => then the handler must be for the device for which this consumer receives command messages
            return this.gatewayOrDeviceId.equals(handlerDeviceId);
        } else {
            // the given handler is for commands directed at a device behind a gateway
            // => then the given gateway must be the same as the one for which this consumer receives command messages
            return this.gatewayOrDeviceId.equals(handlerGatewayId);
        }
    }

    /**
     * Checks whether the Proton receiver behind this consumer is open.
     *
     * @return {@code true} if this consumer is alive.
     */
    public boolean isAlive() {
        return receiver.isOpen() && !closedCalled.get();
    }

    private void handleCommandMessage(final Message msg, final ProtonDelivery delivery) {
        // command could have been mapped to a gateway, but the original address stays the same in the message address in that case
        final String originalDeviceId = msg.getAddress() != null
                ? ResourceIdentifier.fromString(msg.getAddress()).getResourceId()
                : null;
        if (originalDeviceId == null) {
            LOG.debug("address of command message is invalid: {}", msg.getAddress());
            final Rejected rejected = new Rejected();
            rejected.setError(new ErrorCondition(Constants.AMQP_BAD_REQUEST, "invalid command target address"));
            delivery.disposition(rejected, true);
            return;
        }
        // look for a handler with the original device id first
        final CommandHandlerWrapper commandHandler = getCommandHandlerOrDefault(originalDeviceId);
        if (commandHandler != null) {
            final Command command = Command.from(msg, tenantId, gatewayOrDeviceId);
            // command.isValid() check not done here - it is to be done in the command handler
            final Tracer tracer = connection.getTracer();
            // try to extract Span context from incoming message
            final SpanContext spanContext = TracingHelper.extractSpanContext(tracer, msg);
            final String gatewayId = gatewayOrDeviceId.equals(originalDeviceId) ? null : gatewayOrDeviceId;
            final Span currentSpan = createSpan("send command", tenantId, originalDeviceId,
                    gatewayId, tracer, spanContext);
            logReceivedCommandToSpan(command, currentSpan);
            commandHandler.handleCommand(CommandContext.from(command, delivery, this.receiver, currentSpan));
        } else {
            LOG.error("no command handler found for command with device id {}, message address device id {} [tenant-id: {}]",
                    gatewayOrDeviceId, originalDeviceId, tenantId);
            ProtonHelper.released(delivery, true);
        }
    }

    /**
     * Checks whether a handler exists for the given device id.
     * 
     * @param deviceId The device id.
     * @return {@code true} if a handler exists.
     */
    public boolean containsCommandHandler(final String deviceId) {
        return commandHandlers.containsKey(deviceId);
    }

    /**
     * Gets a handler for either the given device id or for the device id of <em>this</em>
     * command consumer.
     * 
     * @param gatewayManagedDeviceId The id of a device connected to a gateway. May be {@code null}.<p>
     *                               In case the command, for which to return the appropriate handler, is
     *                               directed at a gateway-managed device, the id of this device is to be
     *                               given here. Otherwise {@code null} is to be used.<p>
     *                               If a handler for the given device exists, it will be returned here.
     *                               Otherwise the handler for the device id of <em>this</em> command consumer
     *                               will be returned (if such a handler is set).
     * @return The handler or {@code null}.
     */
    public CommandHandlerWrapper getCommandHandlerOrDefault(final String gatewayManagedDeviceId) {
        if (gatewayManagedDeviceId != null && !gatewayManagedDeviceId.equals(gatewayOrDeviceId)) {
            final CommandHandlerWrapper handler = commandHandlers.get(gatewayManagedDeviceId);
            if (handler != null) {
                LOG.trace("using device specific command handler for {} [consumer device-id: {}]",
                        gatewayManagedDeviceId, gatewayOrDeviceId);
                return handler;
            }
        }
        return commandHandlers.get(gatewayOrDeviceId);
    }

    /**
     * Gets the contained command handlers.
     *
     * @return The command handlers.
     */
    public Collection<CommandHandlerWrapper> getCommandHandlers() {
        return commandHandlers.values();
    }

    private void onRemoteClose(final Handler<String> remoteCloseHandler, final String event) {
        remoteCloseHandler.handle(event);
        commandHandlers.values().forEach(handler -> {
            handler.handleRemoteClose();
        });
    }

    /**
     * Removes the handler for the given device id and closes this consumer if there are
     * no remaining handlers left.
     *
     * @param deviceId The device id of the handler to remove.
     * @param resultHandler A handler that is called back with the result of the operation.
     */
    public void removeHandlerAndCloseConsumerIfEmpty(final String deviceId, final Handler<AsyncResult<Void>> resultHandler) {
        final CommandHandlerWrapper removedHandler = commandHandlers.remove(deviceId);
        LOG.trace("Removed handler for device {} on consumer {}: {}", deviceId, this.gatewayOrDeviceId, removedHandler != null);
        if (removedHandler != null && commandHandlers.isEmpty()) {
            LOG.trace("all command handlers removed for consumer, closing link [consumer device-id: {}, device-id of removed handler: {}]",
                    this.gatewayOrDeviceId, deviceId);
            closedCalled.set(true);
            close(resultHandler);
        } else if (resultHandler != null) {
            resultHandler.handle(Future.succeededFuture());
        }
    }

    /**
     * Creates a new command consumer.
     * <p>
     * The handler to be invoked by the created consumer will have to be subsequently added
     * via the {@link #addDeviceSpecificCommandHandler(String, String, Handler, Handler)} method.
     * <p>
     * The underlying receiver link will be created with the following properties:
     * <ul>
     * <li><em>auto accept</em> will be set to {@code true}</li>
     * <li><em>pre-fetch size</em> will be set to the number of initial credits configured
     * for the given connection.</li>
     * </ul>
     *
     * @param con The connection to the server.
     * @param tenantId The tenant to consume commands from.
     * @param gatewayOrDeviceId The device for which the commands should be consumed.
     * @param localCloseHandler A handler to be invoked after the link has been closed
     *                     at this peer's request using the {@link #close(Handler)} method.
     *                     The handler will be invoked with the link's source address <em>after</em>
     *                     the link has been closed but <em>before</em> the handler that has been
     *                     passed into the <em>close</em> method is invoked.
     * @param remoteCloseHandler A handler to be invoked after the link has been closed
     *                     at the remote peer's request. The handler will be invoked with the
     *                     link's source address.
     * @return A future indicating the outcome of the creation attempt.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    public static Future<DestinationCommandConsumer> create(
            final HonoConnection con,
            final String tenantId,
            final String gatewayOrDeviceId,
            final Handler<String> localCloseHandler,
            final Handler<String> remoteCloseHandler) {

        Objects.requireNonNull(con);
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(gatewayOrDeviceId);
        Objects.requireNonNull(localCloseHandler);
        Objects.requireNonNull(remoteCloseHandler);

        LOG.trace("creating new command consumer [tenant-id: {}, device-id: {}]", tenantId, gatewayOrDeviceId);

        final String address = ResourceIdentifier.from(CommandConstants.INTERNAL_COMMAND_ENDPOINT, tenantId, gatewayOrDeviceId).toString();

        final AtomicReference<DestinationCommandConsumer> consumerRef = new AtomicReference<>();

        return con.createReceiver(
                address,
                ProtonQoS.AT_LEAST_ONCE,
                (delivery, msg) -> {

                    final DestinationCommandConsumer consumer = consumerRef.get();
                    if (consumer == null) {
                        // sender has sent message before we have sent credit
                        LOG.error("rejecting received message received before having granted credits [tenant-id: {}, device-id: {}]",
                                tenantId, gatewayOrDeviceId);
                        ProtonHelper.released(delivery, true);
                        return;
                    }
                    consumer.handleCommandMessage(msg, delivery);
                },
                con.getConfig().getInitialCredits(),
                false, // no auto-accept
                sourceAddress -> { // remote close hook
                    LOG.debug("command receiver link [tenant-id: {}, device-id: {}] closed remotely",
                            tenantId, gatewayOrDeviceId);
                    final DestinationCommandConsumer consumer = consumerRef.get();
                    if (consumer != null) {
                        consumer.onRemoteClose(remoteCloseHandler, sourceAddress);
                    }
                }).map(receiver -> {
                    LOG.debug("successfully created command consumer [{}]", address);
                    final DestinationCommandConsumer consumer = new DestinationCommandConsumer(
                            con, receiver, tenantId, gatewayOrDeviceId);
                    consumerRef.set(consumer);
                    consumer.setLocalCloseHandler(sourceAddress -> {
                        LOG.debug("command receiver link [tenant-id: {}, device-id: {}] closed locally",
                                tenantId, gatewayOrDeviceId);
                        localCloseHandler.handle(sourceAddress);
                    });
                    return consumer;
                }).recover(t -> {
                    LOG.debug("failed to create command consumer [tenant-id: {}, device-id: {}]",
                            tenantId, gatewayOrDeviceId, t);
                    return Future.failedFuture(t);
                });
    }
}
