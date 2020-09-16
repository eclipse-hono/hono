/**
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
 */


package org.eclipse.hono.client.impl;

import java.util.Objects;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.ApplicationClientFactory;
import org.eclipse.hono.client.AsyncCommandClient;
import org.eclipse.hono.client.CommandClient;
import org.eclipse.hono.client.HonoConnection;
import org.eclipse.hono.client.MessageConsumer;
import org.eclipse.hono.client.SendMessageSampler;
import org.eclipse.hono.util.CommandConstants;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.proton.ProtonDelivery;


/**
 * A factory for clients of Hono's north bound APIs.
 *
 */
public class ApplicationClientFactoryImpl extends AbstractHonoClientFactory implements ApplicationClientFactory {

    private final ClientFactory<MessageConsumer> consumerFactory;
    private final CachingClientFactory<CommandClient> commandClientFactory;
    private final CachingClientFactory<AsyncCommandClient> asyncCommandClientFactory;

    /**
     * Creates a new factory for an existing connection.
     *
     * @param connection The connection to use.
     * @param samplerFactory The sampler factory to use.
     */
    public ApplicationClientFactoryImpl(final HonoConnection connection, final SendMessageSampler.Factory samplerFactory) {
        super(connection, samplerFactory);
        consumerFactory = new ClientFactory<>();
        commandClientFactory = new CachingClientFactory<>(connection.getVertx(), c -> c.isOpen());
        asyncCommandClientFactory = new CachingClientFactory<>(connection.getVertx(), c -> c.isOpen());
    }

    @Override
    public Future<MessageConsumer> createTelemetryConsumer(final String tenantId,
            final BiConsumer<ProtonDelivery, Message> telemetryConsumer,
            final boolean autoAccept,
            final Handler<Void> closeHandler) {

        return connection.executeOnContext(result -> {
            consumerFactory.createClient(
                    () -> TelemetryConsumerImpl.create(
                            connection,
                            tenantId,
                            telemetryConsumer,
                            autoAccept,
                            closeHook -> closeHandler.handle(null)),
                    result);
        });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final Future<MessageConsumer> createTelemetryConsumer(
            final String tenantId,
            final Consumer<Message> telemetryConsumer,
            final Handler<Void> closeHandler) {

        return createTelemetryConsumer(
                tenantId,
                (delivery, message) -> telemetryConsumer.accept(message),
                true,
                closeHandler);
    }

    @Override
    public Future<MessageConsumer> createEventConsumer(final String tenantId,
            final BiConsumer<ProtonDelivery, Message> eventConsumer,
            final boolean autoAccept,
            final Handler<Void> closeHandler) {

        return connection.executeOnContext(result -> {
            consumerFactory.createClient(
                    () -> EventConsumerImpl.create(
                            connection,
                            tenantId,
                            eventConsumer,
                            autoAccept,
                            closeHook -> closeHandler.handle(null)),
                    result);
        });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final Future<MessageConsumer> createEventConsumer(
            final String tenantId,
            final BiConsumer<ProtonDelivery, Message> eventConsumer,
            final Handler<Void> closeHandler) {

        return createEventConsumer(tenantId, eventConsumer, true, closeHandler);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final Future<MessageConsumer> createEventConsumer(
            final String tenantId,
            final Consumer<Message> eventConsumer,
            final Handler<Void> closeHandler) {

        return createEventConsumer(
                tenantId,
                (delivery, message) -> eventConsumer.accept(message),
                closeHandler);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Future<CommandClient> getOrCreateCommandClient(final String tenantId) {
        Objects.requireNonNull(tenantId);

        final String cacheKey = String.format("%s/%s", CommandConstants.NORTHBOUND_COMMAND_REQUEST_ENDPOINT, tenantId);
        return getOrCreateCommandClient(tenantId, UUID.randomUUID().toString(), cacheKey);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Future<CommandClient> getOrCreateCommandClient(final String tenantId, final String replyId) {
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(replyId);

        final String cacheKey = String.format("%s/%s/%s", CommandConstants.NORTHBOUND_COMMAND_REQUEST_ENDPOINT, tenantId, replyId);
        return getOrCreateCommandClient(tenantId, replyId, cacheKey);
    }

    private Future<CommandClient> getOrCreateCommandClient(final String tenantId, final String replyId,
            final String cacheKey) {
        log.debug("get or create command client for [tenantId: {}, replyId: {}]", tenantId, replyId);
        return connection.executeOnContext(result -> {
            commandClientFactory.getOrCreateClient(
                    cacheKey,
                    () -> CommandClientImpl.create(
                            connection,
                            tenantId,
                            replyId,
                            samplerFactory.create(CommandConstants.COMMAND_ENDPOINT),
                            s -> removeCommandClient(cacheKey),
                            s -> removeCommandClient(cacheKey)),
                    result);
        });
    }

    private void removeCommandClient(final String key) {
        commandClientFactory.removeClient(key, client -> {
            client.close(s -> {});
            log.debug("closed and removed client for [{}]", key);
        });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Future<AsyncCommandClient> getOrCreateAsyncCommandClient(final String tenantId) {

        Objects.requireNonNull(tenantId);

        return connection.executeOnContext(result -> {
            final String key = String.format("%s/%s", CommandConstants.NORTHBOUND_COMMAND_REQUEST_ENDPOINT, tenantId);
            asyncCommandClientFactory.getOrCreateClient(
                    key,
                    () -> AsyncCommandClientImpl.create(
                            connection,
                            tenantId,
                            samplerFactory.create(CommandConstants.NORTHBOUND_COMMAND_REQUEST_ENDPOINT),
                            s -> removeAsyncCommandClient(key)),
                    result);
        });
    }

    private void removeAsyncCommandClient(final String key) {
        asyncCommandClientFactory.removeClient(key, client -> {
            client.close(s -> {});
            log.debug("closed and removed client for [{}]", key);
        });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Future<MessageConsumer> createAsyncCommandResponseConsumer(
            final String tenantId,
            final String replyId,
            final BiConsumer<ProtonDelivery, Message> consumer,
            final Handler<Void> closeHandler) {

        return connection.executeOnContext(result -> {
            consumerFactory.createClient(
                    () -> AsyncCommandResponseConsumerImpl.create(
                            connection,
                            tenantId,
                            replyId,
                            consumer,
                            closeHook -> closeHandler.handle(null)),
                    result);
        });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Future<MessageConsumer> createAsyncCommandResponseConsumer(
            final String tenantId,
            final String replyId,
            final Consumer<Message> consumer,
            final Handler<Void> closeHandler) {

        return createAsyncCommandResponseConsumer(tenantId, replyId, (delivery, msg) -> consumer.accept(msg), closeHandler);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onDisconnect() {
        asyncCommandClientFactory.clearState();
        commandClientFactory.clearState();
        consumerFactory.clearState();
    }
}
