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
import org.eclipse.hono.client.MessageConsumer;
import org.eclipse.hono.client.ResourceConflictException;
import org.eclipse.hono.config.ClientConfigProperties;
import org.eclipse.hono.connection.ConnectionFactory;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;

/**
 * Implements a connection between an Adapter and the AMQP 1.0 network to receive commands and send a response.
 */
public class CommandConsumerFactoryImpl extends HonoConnectionImpl implements CommandConsumerFactory {

    /**
     * The minimum number of milliseconds to wait between checking a
     * command consumer link's liveness.
     */
    public static final long MIN_LIVENESS_CHECK_INTERVAL_MILLIS = 2000;

    /**
     * The consumers that can be used to receive command messages.
     * The device, which belongs to a tenant is used as the key, e.g. <em>DEFAULT_TENANT/4711</em>.
     */
    private final Map<String, MessageConsumer> commandConsumers = new HashMap<>();
    /**
     * A mapping of command consumer addresses to vert.x timer IDs which represent the
     * liveness checks for the consumers.
     */
    private final Map<String, Long> livenessChecks = new HashMap<>();

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
    public CommandConsumerFactoryImpl(final Vertx vertx, final ClientConfigProperties clientConfigProperties) {
        super(vertx, clientConfigProperties);
    }

    /**
     * Creates a new client for a set of configuration properties.
     * <p>
     * This constructor creates a connection factory using
     * {@link ConnectionFactory#newConnectionFactory(Vertx, ClientConfigProperties)}.
     *
     * @param vertx The Vert.x instance to execute the client on, if {@code null} a new Vert.x instance is used.
     * @param connectionFactory Factory to invoke for a new connection.
     * @param clientConfigProperties The configuration properties to use.
     * @throws NullPointerException if clientConfigProperties is {@code null}
     */
    public CommandConsumerFactoryImpl(final Vertx vertx, final ConnectionFactory connectionFactory, final ClientConfigProperties clientConfigProperties) {
        super(vertx, connectionFactory, clientConfigProperties);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void clearState() {
        super.clearState();
        commandConsumers.clear();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final Future<MessageConsumer> createCommandConsumer(
            final String tenantId,
            final String deviceId,
            final Handler<CommandContext> commandConsumer,
            final Handler<Void> remoteCloseHandler) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(deviceId);
        Objects.requireNonNull(commandConsumer);

        return executeOrRunOnContext(result -> {
            final String key = Device.asAddress(tenantId, deviceId);
            final MessageConsumer messageConsumer = commandConsumers.get(key);
            if (messageConsumer != null) {
                log.debug("cannot create concurrent command consumer [tenant: {}, device-id: {}]", tenantId, deviceId);
                result.fail(new ResourceConflictException("message consumer already in use"));
            } else {
                createConsumer(
                        tenantId,
                        () -> newCommandConsumer(tenantId, deviceId, commandConsumer, remoteCloseHandler))
                .map(consumer -> {
                    commandConsumers.put(key, consumer);
                    return consumer;
                })
                .setHandler(result);
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

        return createCommandConsumer(tenantId, deviceId, commandConsumer, remoteCloseHandler).map(c -> {

            final String key = Device.asAddress(tenantId, deviceId);
            final long effectiveCheckInterval = Math.max(MIN_LIVENESS_CHECK_INTERVAL_MILLIS, checkInterval);
            final long livenessCheckId = vertx.setPeriodic(
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
            if (isShutdown()) {
                vertx.cancelTimer(timerId);
            } else if (isConnectedInternal() && !commandConsumers.containsKey(key)) {
                // when a connection is lost unexpectedly,
                // all consumers will be removed from the cache
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
        };
    }

    private Future<MessageConsumer> newCommandConsumer(
            final String tenantId,
            final String deviceId,
            final Handler<CommandContext> commandConsumer,
            final Handler<Void> remoteCloseHandler) {

        return checkConnected().compose(con -> {
            final String key = Device.asAddress(tenantId, deviceId);
            return CommandConsumer.create(
                    this,
                    tenantId,
                    deviceId,
                    commandConsumer,
                    sourceAddress -> { // local close hook
                        // stop liveness check
                        Optional.ofNullable(livenessChecks.remove(key)).ifPresent(vertx::cancelTimer);
                        commandConsumers.remove(key);
                    },
                    sourceAddress -> { // remote close hook
                        commandConsumers.remove(key);
                        remoteCloseHandler.handle(null);
                    })
            .map(c -> (MessageConsumer) c);
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

        return executeOrRunOnContext(result -> {
            final String deviceAddress = Device.asAddress(tenantId, deviceId);
            // stop liveness check
            Optional.ofNullable(livenessChecks.remove(deviceAddress)).ifPresent(vertx::cancelTimer);
            // close and remove link from cache 
            Optional.ofNullable(commandConsumers.remove(deviceAddress)).ifPresent(consumer -> {
                consumer.close(result);
            });
        });
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

        return executeOrRunOnContext(result -> {
            checkConnected()
            .compose(ok -> CommandResponseSenderImpl.create(this, tenantId, replyId, onSenderClosed -> {}))
            .setHandler(result);
        });
    }
}
