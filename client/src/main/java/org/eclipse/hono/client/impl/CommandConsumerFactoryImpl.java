/*******************************************************************************
 * Copyright (c) 2018, 2019 Contributors to the Eclipse Foundation
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

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.hono.auth.Device;
import org.eclipse.hono.client.CommandConsumerFactory;
import org.eclipse.hono.client.CommandContext;
import org.eclipse.hono.client.CommandResponseSender;
import org.eclipse.hono.client.HonoConnection;
import org.eclipse.hono.client.MessageConsumer;
import org.eclipse.hono.client.ResourceConflictException;
import org.eclipse.hono.client.impl.CommandConsumer;

import io.vertx.core.Future;
import io.vertx.core.Handler;

/**
 * A factory for creating clients for the <em>AMQP 1.0 Messaging Network</em> to
 * receive commands and send responses.
 */
public class CommandConsumerFactoryImpl extends AbstractHonoClientFactory implements CommandConsumerFactory {

    /**
     * The minimum number of milliseconds to wait between checking a
     * command consumer link's liveness.
     */
    public static final long MIN_LIVENESS_CHECK_INTERVAL_MILLIS = 2000;

    private final CachingClientFactory<MessageConsumer> commandConsumerFactory;

    /**
     * A mapping of command consumer addresses to vert.x timer IDs which represent the
     * liveness checks for the consumers.
     */
    private final Map<String, Long> livenessChecks = new HashMap<>();

    /**
     * Creates a new factory for an existing connection.
     * 
     * @param connection The connection to use.
     */
    public CommandConsumerFactoryImpl(final HonoConnection connection) {
        super(connection);
        commandConsumerFactory = new CachingClientFactory<>(c -> true);
    }

    @Override
    protected void onDisconnect() {
        commandConsumerFactory.clearState();
    }

    private String getKey(final String tenantId, final String deviceId) {
        return Device.asAddress(tenantId, deviceId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final Future<MessageConsumer> createCommandConsumer(
            final String tenantId,
            final String deviceId,
            final Handler<CommandContext> commandHandler,
            final Handler<Void> remoteCloseHandler) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(deviceId);
        Objects.requireNonNull(commandHandler);

        return connection.executeOrRunOnContext(result -> {
            final String key = getKey(tenantId, deviceId);
            final MessageConsumer commandConsumer = commandConsumerFactory.getClient(key);
            if (commandConsumer != null) {
                log.debug("cannot create concurrent command consumer [tenant: {}, device-id: {}]", tenantId, deviceId);
                result.fail(new ResourceConflictException("message consumer already in use"));
            } else {
                commandConsumerFactory.getOrCreateClient(
                        key,
                        () -> newCommandConsumer(tenantId, deviceId, commandHandler, remoteCloseHandler),
                        result);
            }
        });
    }

    /**
     * {@inheritDoc}
     * <p>
     * The interval used for creating the periodic liveness check will be the maximum
     * of the given interval length and {@link #MIN_LIVENESS_CHECK_INTERVAL_MILLIS}.
     * 
     */
    @Override
    public final Future<MessageConsumer> createCommandConsumer(
            final String tenantId,
            final String deviceId,
            final Handler<CommandContext> commandConsumer,
            final Handler<Void> remoteCloseHandler,
            final long checkInterval) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(deviceId);
        Objects.requireNonNull(commandConsumer);
        if (checkInterval < 0) {
            throw new IllegalArgumentException("liveness check interval must be > 0");
        }

        return createCommandConsumer(tenantId, deviceId, commandConsumer, remoteCloseHandler)
                .map(c -> {

                    final String key = getKey(tenantId, deviceId);
                    final long effectiveCheckInterval = Math.max(MIN_LIVENESS_CHECK_INTERVAL_MILLIS, checkInterval);
                    final long livenessCheckId = connection.getVertx().setPeriodic(
                            effectiveCheckInterval,
                            newLivenessCheck(tenantId, deviceId, key, commandConsumer, remoteCloseHandler));
                    livenessChecks.put(key, livenessCheckId);
                    return c;
                });
    }

    Handler<Long> newLivenessCheck(
            final String tenantId,
            final String deviceId,
            final String key,
            final Handler<CommandContext> commandConsumer,
            final Handler<Void> remoteCloseHandler) {

        final AtomicBoolean recreating = new AtomicBoolean(false);
        return timerId -> {
            if (connection.isShutdown()) {
                connection.getVertx().cancelTimer(timerId);
            } else {
                connection.isConnected().map(ok -> {
                    if (commandConsumerFactory.getClient(key) == null) {
                        // when a connection is lost unexpectedly,
                        // all consumers will have been removed from the cache
                        // so we need to recreate the consumer
                        if (recreating.compareAndSet(false, true)) {
                            // set a lock in order to prevent spawning multiple attempts
                            // to re-create the consumer
                            log.debug("trying to re-create command consumer [tenant: {}, device-id: {}]",
                                    tenantId, deviceId);
                            // we try to re-create the link using the original parameters
                            // which will put the consumer into the cache again, if successful
                            createCommandConsumer(tenantId, deviceId, commandConsumer, remoteCloseHandler)
                            .map(consumer -> {
                                log.debug("successfully re-created command consumer [tenant: {}, device-id: {}]",
                                        tenantId, deviceId);
                                return consumer;
                            })
                            .otherwise(t -> {
                                log.info("failed to re-create command consumer [tenant: {}, device-id: {}]: {}",
                                        tenantId, deviceId, t.getMessage());
                                return null;
                            })
                            .setHandler(s -> recreating.compareAndSet(true, false));
                        } else {
                            log.debug("already trying to re-create command consumer [tenant: {}, device-id: {}], yielding ...",
                                    tenantId, deviceId);
                        }
                    }
                    return null;
                });
            }
        };
    }

    private Future<MessageConsumer> newCommandConsumer(
            final String tenantId,
            final String deviceId,
            final Handler<CommandContext> commandConsumer,
            final Handler<Void> remoteCloseHandler) {

        final String key = Device.asAddress(tenantId, deviceId);
        return CommandConsumer.create(
                    connection,
                    tenantId,
                    deviceId,
                    commandConsumer,
                    sourceAddress -> { // local close hook
                        // stop liveness check
                        Optional.ofNullable(livenessChecks.remove(key)).ifPresent(connection.getVertx()::cancelTimer);
                        commandConsumerFactory.removeClient(key);
                    },
                    sourceAddress -> { // remote close hook
                        commandConsumerFactory.removeClient(key);
                        remoteCloseHandler.handle(null);
                    }).map(c -> (MessageConsumer) c);
    }

    /**
     * {@inheritDoc}
     * 
     * This implementation always creates a new sender link.
     */
    @Override
    public Future<CommandResponseSender> getCommandResponseSender(
            final String tenantId,
            final String replyId) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(replyId);

        return connection.executeOrRunOnContext(result -> {
            CommandResponseSenderImpl.create(
                    connection,
                    tenantId,
                    replyId,
                    onRemoteClose -> {})
            .setHandler(result);
        });
    }

    /**
     * {@inheritDoc}
     */
    @Deprecated
    @Override
    public Future<Void> closeCommandConsumer(final String tenantId, final String deviceId) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(deviceId);

        return connection.executeOrRunOnContext(result -> {
            final String key = getKey(tenantId, deviceId);
            // stop liveness check
            Optional.ofNullable(livenessChecks.remove(key)).ifPresent(connection.getVertx()::cancelTimer);
            // close and remove link from cache 
            commandConsumerFactory.removeClient(key, consumer -> {
                consumer.close(result);
            });
        });
    }
}
