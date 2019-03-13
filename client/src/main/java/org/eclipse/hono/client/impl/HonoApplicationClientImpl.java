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
package org.eclipse.hono.client.impl;

import java.util.Objects;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.CommandClient;
import org.eclipse.hono.client.HonoApplicationClient;
import org.eclipse.hono.client.MessageConsumer;
import org.eclipse.hono.client.RequestResponseClient;
import org.eclipse.hono.config.ClientConfigProperties;
import org.eclipse.hono.connection.ConnectionFactory;
import org.eclipse.hono.util.CommandConstants;
import org.eclipse.hono.util.ResourceIdentifier;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.proton.ProtonDelivery;

/**
 * A helper class for creating Vert.x based clients for Hono's APIs.
 * <p>
 * The client ensures that all interactions with the peer are performed on the
 * same vert.x {@code Context}. For this purpose the <em>connect</em> methods
 * either use the current Context or create a new Context for connecting to
 * the peer. This same Context is then used for all consecutive interactions with
 * the peer as well, e.g. when creating consumers or senders.
 * <p>
 * Closing or disconnecting the client will <em>release</em> the Context. The next
 * invocation of any of the connect methods will then use the same approach as
 * described above to determine the Context to use.
 */
public class HonoApplicationClientImpl extends HonoClientImpl implements HonoApplicationClient {

    /**
     * Creates a new client for a set of configuration properties.
     * <p>
     * This constructor creates a connection factory using
     * {@link ConnectionFactory#newConnectionFactory(Vertx, ClientConfigProperties)}.
     *
     * @param vertx The Vert.x instance to execute the client on, if {@code null} a new Vert.x instance is used.
     * @param clientConfigProperties The configuration properties to use.
     * @throws NullPointerException if clientConfigProperties is {@code null}
     */
    public HonoApplicationClientImpl(final Vertx vertx, final ClientConfigProperties clientConfigProperties) {
        this(vertx, null, clientConfigProperties);
    }

    /**
     * Creates a new client for a set of configuration properties.
     * <p>
     * <em>NB</em> Make sure to always use the same set of configuration properties for both the connection factory as
     * well as the Hono client in order to prevent unexpected behavior.
     *
     * @param vertx The Vert.x instance to execute the client on, if {@code null} a new Vert.x instance is used.
     * @param connectionFactory The factory to use for creating an AMQP connection to the Hono server.
     * @param clientConfigProperties The configuration properties to use.
     * @throws NullPointerException if clientConfigProperties is {@code null}
     */
    public HonoApplicationClientImpl(final Vertx vertx, final ConnectionFactory connectionFactory,
                                 final ClientConfigProperties clientConfigProperties) {
        super(vertx, connectionFactory, clientConfigProperties);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final Future<MessageConsumer> createTelemetryConsumer(
            final String tenantId,
            final Consumer<Message> messageConsumer,
            final Handler<Void> closeHandler) {

        return createConsumer(
                tenantId,
                () -> newTelemetryConsumer(tenantId, messageConsumer, closeHandler));
    }

    private Future<MessageConsumer> newTelemetryConsumer(
            final String tenantId,
            final Consumer<Message> messageConsumer,
            final Handler<Void> closeHandler) {

        return checkConnected().compose(con -> {
            final Future<MessageConsumer> result = Future.future();
            TelemetryConsumerImpl.create(context, clientConfigProperties, connection, tenantId,
                    connectionFactory.getPathSeparator(), messageConsumer, result.completer(),
                    closeHook -> closeHandler.handle(null));
            return result;
        });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final Future<MessageConsumer> createEventConsumer(
            final String tenantId,
            final Consumer<Message> eventConsumer,
            final Handler<Void> closeHandler) {

        return createEventConsumer(tenantId, (delivery, message) -> eventConsumer.accept(message), closeHandler);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final Future<MessageConsumer> createEventConsumer(
            final String tenantId,
            final BiConsumer<ProtonDelivery, Message> messageConsumer,
            final Handler<Void> closeHandler) {

        return createConsumer(
                tenantId,
                () -> newEventConsumer(tenantId, messageConsumer, closeHandler));
    }

    private Future<MessageConsumer> newEventConsumer(
            final String tenantId,
            final BiConsumer<ProtonDelivery, Message> messageConsumer,
            final Handler<Void> closeHandler) {

        return checkConnected().compose(con -> {
            final Future<MessageConsumer> result = Future.future();
            EventConsumerImpl.create(context, clientConfigProperties, connection, tenantId,
                    connectionFactory.getPathSeparator(), messageConsumer, result.completer(),
                    closeHook -> closeHandler.handle(null));
            return result;
        });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Future<CommandClient> getOrCreateCommandClient(final String tenantId, final String deviceId) {
        return getOrCreateCommandClient(tenantId, deviceId, UUID.randomUUID().toString());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Future<CommandClient> getOrCreateCommandClient(final String tenantId, final String deviceId,
            final String replyId) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(deviceId);
        Objects.requireNonNull(replyId);

        log.debug("get or create command client for [tenantId: {}, deviceId: {}, replyId: {}]", tenantId, deviceId,
                replyId);
        return getOrCreateRequestResponseClient(
                ResourceIdentifier.from(CommandConstants.COMMAND_ENDPOINT, tenantId, deviceId).toString(),
                () -> newCommandClient(tenantId, deviceId, replyId)).map(c -> (CommandClient) c);
    }

    private Future<RequestResponseClient> newCommandClient(final String tenantId, final String deviceId,
            final String replyId) {
        return checkConnected().compose(connected -> {
            final Future<CommandClient> result = Future.future();
            CommandClientImpl.create(
                    context,
                    clientConfigProperties,
                    connection,
                    tenantId, deviceId, replyId,
                    this::removeActiveRequestResponseClient,
                    this::removeActiveRequestResponseClient,
                    result.completer());
            return result.map(client -> (RequestResponseClient) client);
        });
    }
}
