/**
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
 */


package org.eclipse.hono.application.client.amqp;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;

import org.apache.qpid.proton.amqp.messaging.Rejected;
import org.apache.qpid.proton.amqp.messaging.Released;
import org.apache.qpid.proton.amqp.transport.DeliveryState;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.application.client.DownstreamMessage;
import org.eclipse.hono.application.client.MessageConsumer;
import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.amqp.GenericReceiverLink;
import org.eclipse.hono.client.amqp.connection.AmqpUtils;
import org.eclipse.hono.client.amqp.connection.HonoConnection;
import org.eclipse.hono.client.amqp.connection.SendMessageSampler;
import org.eclipse.hono.util.CommandConstants;
import org.eclipse.hono.util.EventConstants;
import org.eclipse.hono.util.LifecycleStatus;
import org.eclipse.hono.util.TelemetryConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.impl.NoStackTraceThrowable;
import io.vertx.proton.ProtonDelivery;
import io.vertx.proton.ProtonHelper;


/**
 * A vertx-proton based client that supports Hono's north bound operations to send commands and receive telemetry,
 * event and command response messages.
 *
 */
public class ProtonBasedApplicationClient extends ProtonBasedCommandSender implements AmqpApplicationClient {

    private static final Logger LOG = LoggerFactory.getLogger(ProtonBasedApplicationClient.class);

    /**
     * This component's current life cycle state.
     */
    protected final LifecycleStatus lifecycleStatus = new LifecycleStatus();

    private final List<Handler<Throwable>> consumerCloseHandlers = new ArrayList<>();

    /**
     * Creates a new vertx-proton based based application client.
     *
     * @param connection The connection to use for accessing Hono's north bound (AMQP 1.0 based)
     *                   API endpoints.
     * @throws NullPointerException if connection is {@code null}.
     */
    public ProtonBasedApplicationClient(final HonoConnection connection) {
        super(connection, SendMessageSampler.Factory.noop());
    }

    private String getTenantScopedLinkAddress(
            final String endpointName,
            final String tenantId) {
        return String.format("%s/%s", endpointName, tenantId);
    }

    @Override
    public void addOnClientReadyHandler(final Handler<AsyncResult<Void>> handler) {
        if (handler != null) {
            lifecycleStatus.addOnStartedHandler(handler);
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * The returned Future will be failed if this client is already started or stopping.
     */
    @Override
    public Future<Void> start() {
        if (lifecycleStatus.isStarting()) {
            return Future.succeededFuture();
        } else if (!lifecycleStatus.setStarting()) {
            return Future.failedFuture(new IllegalStateException("client is already started/stopping"));
        }
        return connectOnStart()
                .onSuccess(v -> lifecycleStatus.setStarted());
    }

    @Override
    public Future<Void> stop() {
        return lifecycleStatus.runStopAttempt(this::disconnectOnStop);
    }

    @Override
    protected void onDisconnect() {
        // consumer close handlers shall be called upon disconnect from remote peer,
        // so skip invocation here if disconnect is done during shutdown
        if (!connection.isShutdown()) {
            final Throwable error = new NoStackTraceThrowable("disconnected");
            consumerCloseHandlers.forEach(h -> h.handle(error));
            consumerCloseHandlers.clear();
        }
        super.onDisconnect();
    }

    @Override
    public final Future<HonoConnection> connect() {
        LOG.info("connecting to Hono endpoint");
        return connection.connect();
    }

    @Override
    public final void disconnect() {
        disconnect(Promise.promise());
    }

    @Override
    public final void disconnect(final Handler<AsyncResult<Void>> completionHandler) {
        LOG.info("disconnecting from Hono endpoint");
        consumerCloseHandlers.clear();
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

        final String sourceAddress = getTenantScopedLinkAddress(TelemetryConstants.TELEMETRY_ENDPOINT, tenantId);
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

        final String sourceAddress = getTenantScopedLinkAddress(EventConstants.EVENT_ENDPOINT, tenantId);
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

        final String sourceAddress = getTenantScopedLinkAddress(TelemetryConstants.TELEMETRY_ENDPOINT, tenantId);
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

        final String sourceAddress = getTenantScopedLinkAddress(EventConstants.EVENT_ENDPOINT, tenantId);
        return createAsyncConsumer(sourceAddress, messageHandler, closeHandler);
    }

    private Future<MessageConsumer> createConsumer(
            final String sourceAddress,
            final Handler<DownstreamMessage<AmqpMessageContext>> messageHandler,
            final Handler<Throwable> closeHandler) {

        return createConsumer(
                sourceAddress,
                (delivery, message) -> {
                    try {
                        final var msg = ProtonBasedDownstreamMessage.from(message, delivery);
                        messageHandler.handle(msg);
                        if (!delivery.isSettled()) {
                            LOG.debug("client provided message handler did not settle message, auto-accepting ...");
                            ProtonHelper.accepted(delivery, true);
                        }
                    } catch (final Exception e) {
                        handleMessageHandlerError(e, delivery);
                    }
                }, 
                closeHandler);
    }

    private Future<MessageConsumer> createConsumer(
            final String sourceAddress,
            final BiConsumer<ProtonDelivery, Message> messageConsumer,
            final Handler<Throwable> closeHandler) {

        // wrap the handler to ensure distinct objects are added to the consumerCloseHandlers list
        // preventing issues if the same closeHandler instance is used for multiple createConsumer() invocations
        final Handler<Throwable> wrappedCloseHandler = closeHandler != null ? closeHandler::handle : null;
        return connection
                .isConnected(getDefaultConnectionCheckTimeout())
                .compose(v -> GenericReceiverLink.create(
                        connection,
                        sourceAddress,
                        messageConsumer,
                        false,
                        s -> {
                            if (closeHandler != null) {
                                consumerCloseHandlers.remove(wrappedCloseHandler);
                                closeHandler.handle(null);
                            }
                        }))
                .onSuccess(v -> Optional.ofNullable(wrappedCloseHandler).ifPresent(consumerCloseHandlers::add))
                .map(receiverLink -> () -> {
                        Optional.ofNullable(wrappedCloseHandler).ifPresent(consumerCloseHandlers::remove);
                        return receiverLink.close();
                    });
    }

    private Future<MessageConsumer> createAsyncConsumer(
            final String sourceAddress,
            final Function<DownstreamMessage<AmqpMessageContext>, Future<Void>> messageHandler,
            final Handler<Throwable> closeHandler) {

        return createConsumer(
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
                                .onFailure(t -> handleMessageHandlerError(t, delivery));
                    } catch (final Exception e) {
                        handleMessageHandlerError(e, delivery);
                    }
                },
                closeHandler);
    }

    private void handleMessageHandlerError(final Throwable error, final ProtonDelivery delivery) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("client provided message handler threw exception [local state: {}, settled: {}]",
                    Optional.ofNullable(delivery.getLocalState()).map(s -> s.getType().name()).orElse(null),
                    delivery.isSettled(),
                    error);
        }
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
            rejected.setError(ProtonHelper.condition(AmqpUtils.AMQP_BAD_REQUEST, t.getMessage()));
            return rejected;
        } else {
            return new Released();
        }
    }
}
