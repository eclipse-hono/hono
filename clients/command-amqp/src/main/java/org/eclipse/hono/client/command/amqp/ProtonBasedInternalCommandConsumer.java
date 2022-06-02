/*******************************************************************************
 * Copyright (c) 2021, 2022 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.client.command.amqp;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.qpid.proton.amqp.messaging.Rejected;
import org.apache.qpid.proton.amqp.transport.ErrorCondition;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.NoConsumerException;
import org.eclipse.hono.client.amqp.AbstractServiceClient;
import org.eclipse.hono.client.amqp.connection.AmqpUtils;
import org.eclipse.hono.client.amqp.connection.HonoConnection;
import org.eclipse.hono.client.amqp.connection.HonoProtonHelper;
import org.eclipse.hono.client.amqp.connection.SendMessageSampler;
import org.eclipse.hono.client.command.CommandContext;
import org.eclipse.hono.client.command.CommandHandlerWrapper;
import org.eclipse.hono.client.command.CommandHandlers;
import org.eclipse.hono.client.command.InternalCommandConsumer;
import org.eclipse.hono.tracing.TracingHelper;
import org.eclipse.hono.util.CommandConstants;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.vertx.core.Future;
import io.vertx.proton.ProtonDelivery;
import io.vertx.proton.ProtonQoS;
import io.vertx.proton.ProtonReceiver;

/**
 * A vertx-proton based consumer to receive commands forwarded by the Command Router on the internal command endpoint.
 */
public class ProtonBasedInternalCommandConsumer extends AbstractServiceClient implements InternalCommandConsumer {

    private static final int RECREATE_CONSUMER_DELAY = 20;

    private final String adapterInstanceId;
    private final CommandHandlers commandHandlers;
    private final AtomicBoolean recreatingConsumer = new AtomicBoolean(false);
    private final AtomicBoolean tryAgainRecreatingConsumer = new AtomicBoolean(false);
    private final Tracer tracer;

    private ProtonReceiver adapterSpecificConsumer;

    /**
     * Creates a new consumer for an existing connection.
     *
     * @param connection The connection to the AMQP network.
     * @param adapterInstanceId The adapter instance id.
     * @param commandHandlers The command handlers to choose from for handling a received command.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    public ProtonBasedInternalCommandConsumer(
            final HonoConnection connection,
            final String adapterInstanceId,
            final CommandHandlers commandHandlers) {
        super(connection, SendMessageSampler.Factory.noop());

        this.commandHandlers = Objects.requireNonNull(commandHandlers);
        this.adapterInstanceId = Objects.requireNonNull(adapterInstanceId);
        tracer = connection.getTracer();
    }

    @Override
    public Future<Void> start() {
        return connectOnStart()
                .onComplete(v -> {
                    connection.addReconnectListener(c -> recreateConsumer());
                    // trigger creation of adapter specific consumer link (with retry if failed)
                    recreateConsumer();
                });
    }

    @Override
    protected void onDisconnect() {
        adapterSpecificConsumer = null;
    }

    private Future<ProtonReceiver> createAdapterSpecificConsumer() {
        log.trace("creating new adapter instance command consumer");
        final String adapterInstanceConsumerAddress = CommandConstants.INTERNAL_COMMAND_ENDPOINT + "/"
                + adapterInstanceId;
        return connection.createReceiver(
                adapterInstanceConsumerAddress,
                ProtonQoS.AT_LEAST_ONCE,
                this::handleCommandMessage,
                connection.getConfig().getInitialCredits(),
                false, // no auto-accept
                sourceAddress -> { // remote close hook
                    log.debug("command receiver link closed remotely");
                    invokeRecreateConsumerWithDelay();
                }).map(receiver -> {
            log.debug("successfully created adapter specific command consumer");
            adapterSpecificConsumer = receiver;
            return receiver;
        }).recover(t -> {
            log.error("failed to create adapter specific command consumer", t);
            return Future.failedFuture(t);
        });
    }

    void handleCommandMessage(final ProtonDelivery delivery, final Message msg) {

        final ProtonBasedCommand command;
        try {
            command = ProtonBasedCommand.fromRoutedCommandMessage(msg);
        } catch (final IllegalArgumentException e) {
            log.debug("address of command message is invalid: {}", msg.getAddress());
            final Rejected rejected = new Rejected();
            rejected.setError(new ErrorCondition(AmqpUtils.AMQP_BAD_REQUEST, "invalid command target address"));
            delivery.disposition(rejected, true);
            return;
        }
        final CommandHandlerWrapper commandHandler = commandHandlers.getCommandHandler(command.getTenant(),
                command.getGatewayOrDeviceId());
        if (commandHandler != null && commandHandler.getGatewayId() != null) {
            // Gateway information set in command handler means a gateway has subscribed for commands for a specific device.
            // This information isn't getting set in the message (by the Command Router) and therefore has to be adopted manually here.
            command.setGatewayId(commandHandler.getGatewayId());
        }

        final SpanContext spanContext = AmqpUtils.extractSpanContext(tracer, msg);
        final SpanContext followsFromSpanContext = commandHandler != null
                ? commandHandler.getConsumerCreationSpanContext()
                : null;
        final Span currentSpan = CommandContext.createSpan(tracer, command, spanContext, followsFromSpanContext,
                getClass().getSimpleName());
        TracingHelper.TAG_ADAPTER_INSTANCE_ID.set(currentSpan, adapterInstanceId);

        final CommandContext commandContext = new ProtonBasedCommandContext(command, delivery, currentSpan);

        if (commandHandler != null) {
            log.trace("using [{}] for received command [{}]", commandHandler, command);
            // command.isValid() check not done here - it is to be done in the command handler
            commandHandler.handleCommand(commandContext);
        } else {
            log.info("no command handler found for command [{}]", command);
            commandContext.release(new NoConsumerException("no command handler found for command"));
        }
    }

    private void recreateConsumer() {
        if (recreatingConsumer.compareAndSet(false, true)) {
            connection.isConnected(getDefaultConnectionCheckTimeout())
                    .compose(res -> {
                        // recreate adapter specific consumer
                        if (!HonoProtonHelper.isLinkOpenAndConnected(adapterSpecificConsumer)) {
                            log.debug("recreate adapter specific command consumer link");
                            return createAdapterSpecificConsumer();
                        }
                        return Future.succeededFuture();
                    }).onComplete(ar -> {
                        recreatingConsumer.set(false);
                        if (tryAgainRecreatingConsumer.compareAndSet(true, false) || ar.failed()) {
                            if (ar.succeeded()) {
                                // tryAgainRecreatingConsumers was set - try again immediately
                                recreateConsumer();
                            } else {
                                invokeRecreateConsumerWithDelay();
                            }
                        }
                    });
        } else {
            // if recreateConsumer() was triggered by a remote link closing, that might have occurred after that link was dealt with above;
            // therefore be sure recreateConsumer() gets called again once the current invocation has finished.
            log.debug("already recreating consumer");
            tryAgainRecreatingConsumer.set(true);
        }
    }

    private void invokeRecreateConsumerWithDelay() {
        connection.getVertx().setTimer(RECREATE_CONSUMER_DELAY, tid -> recreateConsumer());
    }

}
