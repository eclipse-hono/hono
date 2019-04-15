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
     */
    public ApplicationClientFactoryImpl(final HonoConnection connection) {
        super(connection);
        consumerFactory = new ClientFactory<>();
        commandClientFactory = new CachingClientFactory<>(c -> c.isOpen());
        asyncCommandClientFactory = new CachingClientFactory<>(c -> c.isOpen());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final Future<MessageConsumer> createTelemetryConsumer(
            final String tenantId,
            final Consumer<Message> messageConsumer,
            final Handler<Void> closeHandler) {

        return connection.executeOrRunOnContext(result -> {
            consumerFactory.createClient(
                    () -> TelemetryConsumerImpl.create(
                            connection,
                            tenantId,
                            messageConsumer,
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

        return connection.executeOrRunOnContext(result -> {
            consumerFactory.createClient(
                    () -> EventConsumerImpl.create(
                            connection,
                            tenantId,
                            messageConsumer,
                            closeHook -> closeHandler.handle(null)),
                    result);
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
    public Future<CommandClient> getOrCreateCommandClient(
            final String tenantId,
            final String deviceId,
            final String replyId) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(deviceId);
        Objects.requireNonNull(replyId);

        log.debug("get or create command client for [tenantId: {}, deviceId: {}, replyId: {}]", tenantId, deviceId,
                replyId);
        return connection.executeOrRunOnContext(result -> {
            final String targetAddress = CommandClientImpl.getTargetAddress(tenantId, deviceId);
            commandClientFactory.getOrCreateClient(
                    targetAddress,
                    () -> CommandClientImpl.create(
                            connection,
                            tenantId,
                            deviceId,
                            replyId,
                            s -> removeCommandClient(targetAddress),
                            s -> removeCommandClient(targetAddress)),
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
    public Future<AsyncCommandClient> getOrCreateAsyncCommandClient(final String tenantId, final String deviceId) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(deviceId);

        return connection.executeOrRunOnContext(result -> {
            final String targetAddress = AsyncCommandClientImpl.getTargetAddress(tenantId, deviceId);
            asyncCommandClientFactory.getOrCreateClient(
                    targetAddress,
                    () -> AsyncCommandClientImpl.create(
                            connection,
                            tenantId,
                            deviceId,
                            s -> removeAsyncCommandClient(targetAddress)),
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

        return connection.executeOrRunOnContext(result -> {
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
