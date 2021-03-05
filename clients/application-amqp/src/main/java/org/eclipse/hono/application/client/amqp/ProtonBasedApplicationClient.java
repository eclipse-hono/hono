/**
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */


package org.eclipse.hono.application.client.amqp;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

import org.apache.qpid.proton.amqp.messaging.Rejected;
import org.apache.qpid.proton.amqp.messaging.Released;
import org.apache.qpid.proton.amqp.transport.DeliveryState;
import org.eclipse.hono.application.client.DownstreamMessage;
import org.eclipse.hono.application.client.MessageConsumer;
import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.HonoConnection;
import org.eclipse.hono.client.SendMessageSampler;
import org.eclipse.hono.client.amqp.GenericReceiverLink;
import org.eclipse.hono.util.CommandConstants;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.EventConstants;
import org.eclipse.hono.util.TelemetryConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.proton.ProtonDelivery;
import io.vertx.proton.ProtonHelper;


/**
 * A vertx-proton based client that supports Hono's north bound operations to send commands and receive telemetry,
 * event and command response messages.
 *
 */
public class ProtonBasedApplicationClient extends ProtonBasedCommandSender implements AmqpApplicationClient {

    private static final Logger LOG = LoggerFactory.getLogger(ProtonBasedApplicationClient.class);

    private final HonoConnection connection;

    /**
     * Creates a new vertx-proton based based application client.
     *
     * @param connection The connection to use for accessing Hono's north bound (AMQP 1.0 based)
     *                   API endpoints.
     * @throws NullPointerException if connection is {@code null}.
     */
    public ProtonBasedApplicationClient(final HonoConnection connection) {
        super(connection, SendMessageSampler.Factory.noop());
        this.connection = connection;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final Future<HonoConnection> connect() {
        LOG.info("connecting to Hono endpoint");
        return connection.connect();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final void disconnect() {
        disconnect(Promise.promise());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final void disconnect(final Handler<AsyncResult<Void>> completionHandler) {
        LOG.info("disconnecting from Hono endpoint");
        connection.disconnect(completionHandler);
    }

    /**
     * {@inheritDoc}
     * <p>
     * The {@link DownstreamMessage#getMessageContext()} method of the message passed in to the
     * provided handler will return an {@link AmqpMessageContext} instance.
     * Its {@link AmqpMessageContext#getDelivery()} method can be used to manually update
     * the underlying AMQP transfer's disposition.
     * <p>
     * Otherwise, the transfer will be settled
     * <ul>
     * <li>with the <em>rejected</em> outcome if the {@link Handler#handle(Object)} method throws
     * a {@link ClientErrorException},</li>
     * <li>with the <em>released</em> outcome if the {@link Handler#handle(Object)} method throws
     * an exception other than {@link ClientErrorException} or</li>
     * <li>with the <em>accepted</em> outcome otherwise.</li>
     * </ul>
     */
    @Override
    public final Future<MessageConsumer> createTelemetryConsumer(
            final String tenantId,
            final Handler<DownstreamMessage<AmqpMessageContext>> messageHandler,
            final Handler<Throwable> closeHandler) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(messageHandler);

        final String sourceAddress = String.format("%s/%s", TelemetryConstants.TELEMETRY_ENDPOINT, tenantId);
        return createConsumer(sourceAddress, messageHandler, closeHandler);
    }

    /**
     * {@inheritDoc}
     * <p>
     * The {@link DownstreamMessage#getMessageContext()} method of the message passed in to the
     * provided handler will return an {@link AmqpMessageContext} instance.
     * Its {@link AmqpMessageContext#getDelivery()} method can be used to manually update
     * the underlying AMQP transfer's disposition.
     * <p>
     * Otherwise, the transfer will be settled
     * <ul>
     * <li>with the <em>rejected</em> outcome if the {@link Handler#handle(Object)} method throws
     * a {@link ClientErrorException},</li>
     * <li>with the <em>released</em> outcome if the {@link Handler#handle(Object)} method throws
     * an exception other than {@link ClientErrorException} or</li>
     * <li>with the <em>accepted</em> outcome otherwise.</li>
     * </ul>
     */
    @Override
    public final Future<MessageConsumer> createEventConsumer(
            final String tenantId,
            final Handler<DownstreamMessage<AmqpMessageContext>> messageHandler,
            final Handler<Throwable> closeHandler) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(messageHandler);

        final String sourceAddress = String.format("%s/%s", EventConstants.EVENT_ENDPOINT, tenantId);
        return createConsumer(sourceAddress, messageHandler, closeHandler);
    }


    /**
     * {@inheritDoc}
     * <p>
     * The {@link DownstreamMessage#getMessageContext()} method of the message passed in to the
     * provided handler will return an {@link AmqpMessageContext} instance.
     * Its {@link AmqpMessageContext#getDelivery()} method can be used to manually update
     * the underlying AMQP transfer's disposition.
     * <p>
     * Otherwise, the transfer will be settled
     * <ul>
     * <li>with the <em>rejected</em> outcome if the {@link Handler#handle(Object)} method throws
     * a {@link ClientErrorException},</li>
     * <li>with the <em>released</em> outcome if the {@link Handler#handle(Object)} method throws
     * an exception other than {@link ClientErrorException} or</li>
     * <li>with the <em>accepted</em> outcome otherwise.</li>
     * </ul>
     */
    @Override
    public Future<MessageConsumer> createCommandResponseConsumer(final String tenantId, final String replyId,
            final Handler<DownstreamMessage<AmqpMessageContext>> messageHandler,
            final Handler<Throwable> closeHandler) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(replyId);
        Objects.requireNonNull(messageHandler);

        final String sourceAddress = String.format("%s/%s/%s", CommandConstants.COMMAND_RESPONSE_ENDPOINT, tenantId, replyId);
        return createConsumer(sourceAddress, messageHandler, closeHandler);
    }

    /**
     * {@inheritDoc}
     * <p>
     * The {@link DownstreamMessage#getMessageContext()} method of the message passed in to the
     * provided handler will return an {@link AmqpMessageContext} instance.
     * Its {@link AmqpMessageContext#getDelivery()} method can be used to manually update
     * the underlying AMQP transfer's disposition.
     * <p>
     * Otherwise, the transfer will be settled
     * <ul>
     * <li>with the <em>rejected</em> outcome if the returned future is failed with
     * a {@link ClientErrorException},</li>
     * <li>with the <em>released</em> outcome if the returned future is failed with
     * an exception other than {@link ClientErrorException} or</li>
     * <li>with the <em>accepted</em> outcome if the returned future is succeeded.</li>
     * </ul>
     */
    @Override
    public final Future<MessageConsumer> createTelemetryConsumer(
            final String tenantId,
            final Function<DownstreamMessage<AmqpMessageContext>, Future<Void>> messageHandler,
            final Handler<Throwable> closeHandler) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(messageHandler);

        final String sourceAddress = String.format("%s/%s", TelemetryConstants.TELEMETRY_ENDPOINT, tenantId);
        return createAsyncConsumer(sourceAddress, messageHandler, closeHandler);
    }

    /**
     * {@inheritDoc}
     * <p>
     * The {@link DownstreamMessage#getMessageContext()} method of the message passed in to the
     * provided handler will return an {@link AmqpMessageContext} instance.
     * Its {@link AmqpMessageContext#getDelivery()} method can be used to manually update
     * the underlying AMQP transfer's disposition.
     * <p>
     * Otherwise, the transfer will be settled
     * <ul>
     * <li>with the <em>rejected</em> outcome if the returned future is failed with
     * a {@link ClientErrorException},</li>
     * <li>with the <em>released</em> outcome if the returned future is failed with
     * an exception other than {@link ClientErrorException} or</li>
     * <li>with the <em>accepted</em> outcome if the returned future is succeeded.</li>
     * </ul>
     */
    @Override
    public final Future<MessageConsumer> createEventConsumer(
            final String tenantId,
            final Function<DownstreamMessage<AmqpMessageContext>, Future<Void>> messageHandler,
            final Handler<Throwable> closeHandler) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(messageHandler);

        final String sourceAddress = String.format("%s/%s", EventConstants.EVENT_ENDPOINT, tenantId);
        return createAsyncConsumer(sourceAddress, messageHandler, closeHandler);
    }

    private Future<MessageConsumer> createConsumer(
            final String sourceAddress,
            final Handler<DownstreamMessage<AmqpMessageContext>> messageHandler,
            final Handler<Throwable> closeHandler) {

        return GenericReceiverLink.create(
                connection,
                sourceAddress,
                (delivery, message) -> {
                    try {
                        final var msg = ProtonBasedDownstreamMessage.from(message, delivery);
                        messageHandler.handle(msg);
                        if (!delivery.isSettled()) {
                            LOG.debug("client provided message handler did not settle message, auto-accepting ...");
                            ProtonHelper.accepted(delivery, true);
                        }
                    } catch (final Throwable t) {
                        handleException(t, delivery);
                    }
                },
                false,
                s -> Optional.ofNullable(closeHandler).ifPresent(h -> h.handle(null)))
            .map(recv -> new MessageConsumer() {
                public Future<Void> close() {
                    return recv.close();
                };
            });
    }

    private Future<MessageConsumer> createAsyncConsumer(
            final String sourceAddress,
            final Function<DownstreamMessage<AmqpMessageContext>, Future<Void>> messageHandler,
            final Handler<Throwable> closeHandler) {

        return GenericReceiverLink.create(
                connection,
                sourceAddress,
                (delivery, message) -> {
                    try {
                        final var msg = ProtonBasedDownstreamMessage.from(message, delivery);
                        messageHandler.apply(msg)
                            .onSuccess(ok -> {
                                if (!delivery.isSettled()) {
                                    LOG.debug("client provided message handler did not settle message, auto-accepting ...");
                                    ProtonHelper.accepted(delivery, true);
                                }
                            })
                            .onFailure(t -> {
                                handleException(t, delivery);
                            });
                    } catch (final Throwable t) {
                        handleException(t, delivery);
                    }
                },
                false,
                s -> Optional.ofNullable(closeHandler).ifPresent(h -> h.handle(null)))
            .map(recv -> new MessageConsumer() {
                public Future<Void> close() {
                    return recv.close();
                };
            });
    }

    private void handleException(final Throwable error, final ProtonDelivery delivery) {
        LOG.debug("client provided message handler threw exception [local state: {}, settled: {}]",
                Optional.ofNullable(delivery.getLocalState()).map(s -> s.getType().name()).orElse(null),
                delivery.isSettled(),
                error);
        if (!delivery.isSettled()) {
            // settle with outcome based on thrown exception
            final var localState = getDeliveryState(error);
            LOG.debug("settling transfer [local state: {}]", localState.getType().name());
            delivery.disposition(localState, true);
        }
    }

    private DeliveryState getDeliveryState(final Throwable t) {
        if (t instanceof ClientErrorException) {
            final var rejected = new Rejected();
            rejected.setError(ProtonHelper.condition(Constants.AMQP_BAD_REQUEST, t.getMessage()));
            return rejected;
        } else {
            return new Released();
        }
    }
}
