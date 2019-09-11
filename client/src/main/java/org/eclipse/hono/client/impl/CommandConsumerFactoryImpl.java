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
import java.util.concurrent.atomic.AtomicReference;

import org.apache.qpid.proton.amqp.messaging.Rejected;
import org.apache.qpid.proton.amqp.transport.ErrorCondition;
import org.eclipse.hono.auth.Device;
import org.eclipse.hono.client.Command;
import org.eclipse.hono.client.CommandConsumerFactory;
import org.eclipse.hono.client.CommandContext;
import org.eclipse.hono.client.CommandResponseSender;
import org.eclipse.hono.client.ConnectionLifecycle;
import org.eclipse.hono.client.DelegatedCommandSender;
import org.eclipse.hono.client.GatewayMapper;
import org.eclipse.hono.client.HonoConnection;
import org.eclipse.hono.client.MessageConsumer;
import org.eclipse.hono.client.ResourceConflictException;
import org.eclipse.hono.tracing.TracingHelper;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.ResourceIdentifier;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.vertx.core.AsyncResult;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.proton.ProtonReceiver;

/**
 * A factory for creating clients for the <em>AMQP 1.0 Messaging Network</em> to receive commands and send responses.
 * <p>
 * The <em>createCommandConsumer()</em> methods will create one tenant-scoped consumer (if not existing yet) and one
 * device-specific consumer by which the command will be eventually sent to the device.
 * <p>
 * Command messages are first received on the tenant-scoped consumer address. If applicable, the device id of a received
 * command is mapped to the id of the gateway through which the device has last sent messages. Then the command message
 * is either handled by an already existing command handler for the (mapped) device id, or the message is sent back to
 * the downstream peer to be handled by a device-specific consumer.
 */
public class CommandConsumerFactoryImpl extends AbstractHonoClientFactory implements CommandConsumerFactory {

    /**
     * The minimum number of milliseconds to wait between checking a
     * command consumer link's liveness.
     */
    public static final long MIN_LIVENESS_CHECK_INTERVAL_MILLIS = 2000;

    private final CachingClientFactory<MessageConsumer> deviceSpecificCommandConsumerFactory;

    private final CachingClientFactory<MessageConsumer> tenantScopedCommandConsumerFactory;

    private final CachingClientFactory<DelegatedCommandSender> delegatedCommandSenderFactory;
    /**
     * The handlers for the received command messages.
     * The device address is used as the key, e.g. <em>DEFAULT_TENANT/4711</em>.
     */
    private final Map<String, Handler<CommandContext>> deviceSpecificCommandHandlers = new HashMap<>();
    /**
     * A mapping of command consumer addresses to vert.x timer IDs which represent the
     * liveness checks for the consumers.
     */
    private final Map<String, Long> livenessChecks = new HashMap<>();
    private final GatewayMapper gatewayMapper;

    /**
     * Creates a new factory for an existing connection.
     * <p>
     * Note: The connection lifecycle of the given {@link GatewayMapper} instance will be managed by this
     * <em>CommandConsumerFactoryImpl</em> instance via the {@link ConnectionLifecycle#connect()} and
     * {@link ConnectionLifecycle#disconnect()} methods.
     * 
     * @param connection The connection to the AMQP network.
     * @param gatewayMapper The component mapping a command device id to the corresponding gateway device id.
     * @throws NullPointerException if connection or gatewayMapper is {@code null}.
     */
    public CommandConsumerFactoryImpl(final HonoConnection connection, final GatewayMapper gatewayMapper) {
        super(connection);
        this.gatewayMapper = Objects.requireNonNull(gatewayMapper);
        deviceSpecificCommandConsumerFactory = new CachingClientFactory<>(connection.getVertx(), c -> true);
        tenantScopedCommandConsumerFactory = new CachingClientFactory<>(connection.getVertx(), c -> true);
        delegatedCommandSenderFactory = new CachingClientFactory<>(connection.getVertx(), s -> s.isOpen());
    }

    @Override
    protected void onDisconnect() {
        deviceSpecificCommandConsumerFactory.clearState();
        tenantScopedCommandConsumerFactory.clearState();
        deviceSpecificCommandHandlers.clear();
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
            final MessageConsumer commandConsumer = deviceSpecificCommandConsumerFactory.getClient(key);
            if (commandConsumer != null) {
                log.debug("cannot create concurrent command consumer [tenant: {}, device-id: {}]", tenantId, deviceId);
                result.fail(new ResourceConflictException("message consumer already in use"));
            } else {
                // create the device specific consumer
                final Future<MessageConsumer> deviceSpecificConsumerFuture = Future.future();
                deviceSpecificCommandConsumerFactory.getOrCreateClient(
                        key,
                        () -> newDeviceSpecificCommandConsumer(tenantId, deviceId, commandHandler, remoteCloseHandler),
                        deviceSpecificConsumerFuture);
                // create the tenant-scoped consumer that delegates/maps incoming commands to the right handler/consumer
                final Future<MessageConsumer> tenantScopedCommandConsumerFuture = getOrCreateTenantScopedCommandConsumer(tenantId);
                CompositeFuture.all(deviceSpecificConsumerFuture, tenantScopedCommandConsumerFuture).map(res -> {
                    deviceSpecificCommandHandlers.put(key, commandHandler);
                    return deviceSpecificConsumerFuture.result();
                }).setHandler(result);
            }
        });
    }

    private Future<MessageConsumer> getOrCreateTenantScopedCommandConsumer(final String tenantId) {
        Objects.requireNonNull(tenantId);
        return connection.executeOrRunOnContext(result -> {
            final MessageConsumer messageConsumer = tenantScopedCommandConsumerFactory.getClient(tenantId);
            if (messageConsumer != null) {
                result.complete(messageConsumer);
            } else {
                tenantScopedCommandConsumerFactory.getOrCreateClient(tenantId,
                        () -> newTenantScopedCommandConsumer(tenantId),
                        result);
            }
        });
    }

    private Future<MessageConsumer> newTenantScopedCommandConsumer(final String tenantId) {

        final AtomicReference<ProtonReceiver> receiverRefHolder = new AtomicReference<>();

        final DelegateViaDownstreamPeerCommandHandler delegatingCommandHandler = new DelegateViaDownstreamPeerCommandHandler(
                (tenantIdParam, deviceIdParam) -> createDelegatedCommandSender(tenantIdParam, deviceIdParam));

        final GatewayMappingCommandHandler gatewayMappingCommandHandler = new GatewayMappingCommandHandler(
                gatewayMapper, commandContext -> {
                    final String deviceId = commandContext.getCommand().getDeviceId();
                    final Handler<CommandContext> commandHandler = deviceSpecificCommandHandlers
                            .get(getKey(tenantId, deviceId));
                    if (commandHandler != null) {
                        log.trace("use local command handler for device {}", deviceId);
                        commandHandler.handle(commandContext);
                    } else {
                        // delegate to matching consumer via downstream peer
                        delegatingCommandHandler.handle(commandContext);
                    }
                });

        return TenantScopedCommandConsumer.create(
                connection,
                tenantId,
                (originalMessageDelivery, message) -> {
                    final String deviceId = message.getAddress() != null ? ResourceIdentifier.fromString(message.getAddress()).getResourceId() : null;
                    if (deviceId == null) {
                        log.debug("address of command message is invalid: {}", message.getAddress());
                        final Rejected rejected = new Rejected();
                        rejected.setError(new ErrorCondition(Constants.AMQP_BAD_REQUEST, "malformed command message"));
                        originalMessageDelivery.disposition(rejected, true);
                        return;
                    }
                    final Command command = Command.from(message, tenantId, deviceId);
                    final SpanContext spanContext = TracingHelper.extractSpanContext(connection.getTracer(), message);
                    final Span currentSpan = CommandConsumer.createSpan("delegate and send command", tenantId, deviceId, connection.getTracer(), spanContext);
                    CommandConsumer.logReceivedCommandToSpan(command, currentSpan);
                    final CommandContext commandContext = CommandContext.from(command, originalMessageDelivery, receiverRefHolder.get(), currentSpan);
                    if (command.isValid()) {
                        gatewayMappingCommandHandler.handle(commandContext);
                    } else {
                        final Handler<CommandContext> commandHandler = deviceSpecificCommandHandlers.get(getKey(tenantId, deviceId));
                        if (commandHandler != null) {
                            // let the device specific handler reject the command
                            commandHandler.handle(commandContext);
                        } else {
                            log.debug("command message is invalid: {}", command);
                            commandContext.reject(new ErrorCondition(Constants.AMQP_BAD_REQUEST, "malformed command message"));
                        }
                    }
                },
                sourceAddress -> { // local close hook
                    tenantScopedCommandConsumerFactory.removeClient(tenantId);
                },
                sourceAddress -> { // remote close hook
                    tenantScopedCommandConsumerFactory.removeClient(tenantId);
                },
                receiverRefHolder)
                .map(c -> (MessageConsumer) c);
    }

    private Future<DelegatedCommandSender> createDelegatedCommandSender(final String tenantId, final String deviceId) {
        Objects.requireNonNull(tenantId);
        return connection.executeOrRunOnContext(result -> {
            delegatedCommandSenderFactory.createClient(
                    () -> DelegatedCommandSenderImpl.create(connection, tenantId, deviceId, null), result);
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
            final Handler<CommandContext> commandHandler,
            final Handler<Void> remoteCloseHandler,
            final long checkInterval) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(deviceId);
        Objects.requireNonNull(commandHandler);
        if (checkInterval < 0) {
            throw new IllegalArgumentException("liveness check interval must be > 0");
        }

        return createCommandConsumer(tenantId, deviceId, commandHandler, remoteCloseHandler)
                .map(c -> {

                    final String key = getKey(tenantId, deviceId);
                    final long effectiveCheckInterval = Math.max(MIN_LIVENESS_CHECK_INTERVAL_MILLIS, checkInterval);
                    final long livenessCheckId = connection.getVertx().setPeriodic(
                            effectiveCheckInterval,
                            newLivenessCheck(tenantId, deviceId, key, commandHandler, remoteCloseHandler));
                    livenessChecks.put(key, livenessCheckId);
                    return c;
                });
    }

    Handler<Long> newLivenessCheck(
            final String tenantId,
            final String deviceId,
            final String key,
            final Handler<CommandContext> commandHandler,
            final Handler<Void> remoteCloseHandler) {

        final AtomicBoolean recreating = new AtomicBoolean(false);
        final AtomicBoolean recreatingTenantScopedCommandConsumer = new AtomicBoolean(false);
        return timerId -> {
            if (connection.isShutdown()) {
                connection.getVertx().cancelTimer(timerId);
            } else {
                connection.isConnected().map(ok -> {
                    if (deviceSpecificCommandConsumerFactory.getClient(key) == null) {
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
                            createCommandConsumer(tenantId, deviceId, commandHandler, remoteCloseHandler)
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

                    if (tenantScopedCommandConsumerFactory.getClient(tenantId) == null) {
                        if (recreatingTenantScopedCommandConsumer.compareAndSet(false, true)) {
                            log.debug("trying to re-create tenant scoped command consumer [tenant: {}]", tenantId);
                            getOrCreateTenantScopedCommandConsumer(tenantId)
                                    .map(consumer -> {
                                        log.debug("successfully re-created tenant scoped command consumer [tenant: {}]", tenantId);
                                        return consumer;
                                    })
                                    .otherwise(t -> {
                                        log.info("failed to re-create tenant scoped command consumer [tenant: {}]: {}",
                                                tenantId, t.getMessage());
                                        return null;
                                    })
                                    .setHandler(s -> recreatingTenantScopedCommandConsumer.compareAndSet(true, false));
                        } else {
                            log.debug("already trying to re-create tenant scoped command consumer [tenant: {}], yielding ...", tenantId);
                        }
                    }
                    return null;
                });
            }
        };
    }

    private Future<MessageConsumer> newDeviceSpecificCommandConsumer(
            final String tenantId,
            final String deviceId,
            final Handler<CommandContext> commandHandler,
            final Handler<Void> remoteCloseHandler) {

        final String key = getKey(tenantId, deviceId);
        return DeviceSpecificCommandConsumer.create(
                    connection,
                    tenantId,
                    deviceId,
                    commandHandler,
                    sourceAddress -> { // local close hook
                        // stop liveness check
                        Optional.ofNullable(livenessChecks.remove(key)).ifPresent(connection.getVertx()::cancelTimer);
                        deviceSpecificCommandConsumerFactory.removeClient(key);
                        deviceSpecificCommandHandlers.remove(key);
                    },
                    sourceAddress -> { // remote close hook
                        deviceSpecificCommandConsumerFactory.removeClient(key);
                        deviceSpecificCommandHandlers.remove(key);
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
     *
     * This implementation always creates a new sender link.
     */
    @Override
    public Future<CommandResponseSender> getLegacyCommandResponseSender(
            final String tenantId,
            final String replyId) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(replyId);

        return connection.executeOrRunOnContext(result -> {
            LegacyCommandResponseSenderImpl.create(
                    connection,
                    tenantId,
                    replyId,
                    onRemoteClose -> {})
                    .setHandler(result);
        });
    }

    // ------------- Override AbstractHonoClientFactory methods to also connect/disconnect the gatewayMapper ------------

    @Override
    public Future<HonoConnection> connect() {
        final Future<HonoConnection> amqpNetworkConnectionFuture = super.connect();
        return CompositeFuture.all(amqpNetworkConnectionFuture, gatewayMapper.connect())
                .map(obj -> amqpNetworkConnectionFuture.result());
    }

    @Override
    public void disconnect() {
        super.disconnect();
        gatewayMapper.disconnect();
    }

    @Override
    public void disconnect(final Handler<AsyncResult<Void>> completionHandler) {
        final Future<Void> amqpNetworkDisconnectFuture = Future.future();
        super.disconnect(amqpNetworkDisconnectFuture);
        final Future<Void> gatewayMapperDisconnectFuture = Future.future();
        gatewayMapper.disconnect(gatewayMapperDisconnectFuture);
        CompositeFuture.all(amqpNetworkDisconnectFuture, gatewayMapperDisconnectFuture)
                .map(obj -> amqpNetworkDisconnectFuture.result()).setHandler(completionHandler);
    }
}
