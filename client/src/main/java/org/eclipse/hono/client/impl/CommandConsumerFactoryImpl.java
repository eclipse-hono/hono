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

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import org.apache.qpid.proton.amqp.messaging.ApplicationProperties;
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
import org.eclipse.hono.util.MessageHelper;
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
 * gateway or device specific consumer by which the command will be eventually sent to the device.
 * <p>
 * Command messages are first received on the tenant-scoped consumer address. If applicable, the device id of a received
 * command is mapped to the id of the gateway through which the device has last sent messages. Then the command message
 * is either handled by an already existing command handler for the (mapped) gateway id, or the message is sent back to
 * the downstream peer to be handled by a gateway specific consumer.
 */
public class CommandConsumerFactoryImpl extends AbstractHonoClientFactory implements CommandConsumerFactory {

    /**
     * The minimum number of milliseconds to wait between checking a
     * command consumer link's liveness.
     */
    public static final long MIN_LIVENESS_CHECK_INTERVAL_MILLIS = 2000;

    /**
     * Used for integration tests (with only a single instance of each protocol adapter):
     * <p>
     * System property value defining whether incoming command messages on the tenant
     * scoped consumer may be rerouted via the AMQP messaging network to a device-specific
     * consumer even if there is a local handler for the command.<p>
     * The second condition for the rerouting to take place is that the command message
     * contains a {@link #FORCE_COMMAND_REROUTING_APPLICATION_PROPERTY} application
     * property with a {@code true} value.
     */
    private static final Boolean FORCED_COMMAND_REROUTING_ENABLED = Boolean
            .valueOf(System.getProperty("enableForcedCommandRerouting", "false"));
    /**
     * Name of the boolean command message application property with which commands are
     * forced to be rerouted via the AMQP messaging network to a device-specific consumer.
     * Precondition is that the {@link #FORCED_COMMAND_REROUTING_ENABLED} system property
     * is set to {@code true}.
     */
    private static final String FORCE_COMMAND_REROUTING_APPLICATION_PROPERTY = "force-command-rerouting";

    /**
     * Cache key used here is the address returned by {@link #getGatewayOrDeviceKey(String, String, String)}.
     */
    private final CachingClientFactory<DestinationCommandConsumer> destinationCommandConsumerFactory;

    /**
     * Cache key used here is the tenant id.
     */
    private final CachingClientFactory<MessageConsumer> mappingAndDelegatingCommandConsumerFactory;

    private final CachingClientFactory<DelegatedCommandSender> delegatedCommandSenderFactory;
    /**
     * A mapping of the address returned by {@link #getGatewayOrDeviceKey(String, String, String)}
     * to the object representing the liveness check for a destination command consumer.
     */
    private final Map<String, LivenessCheckData> destinationCommandConsumerLivenessChecks = new HashMap<>();

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
        destinationCommandConsumerFactory = new CachingClientFactory<>(connection.getVertx(), c -> c.isAlive());
        mappingAndDelegatingCommandConsumerFactory = new CachingClientFactory<>(connection.getVertx(), c -> true);
        delegatedCommandSenderFactory = new CachingClientFactory<>(connection.getVertx(), s -> s.isOpen());
    }

    @Override
    protected void onDisconnect() {
        destinationCommandConsumerFactory.clearState();
        mappingAndDelegatingCommandConsumerFactory.clearState();
    }

    private String getGatewayOrDeviceKey(final String tenantId, final String deviceId, final String gatewayId) {
        return Device.asAddress(tenantId, gatewayId != null ? gatewayId : deviceId);
    }

    private String getDeviceKey(final String tenantId, final String deviceId) {
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

        return doCreateCommandConsumer(tenantId, deviceId, null, commandHandler, remoteCloseHandler, null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final Future<MessageConsumer> createCommandConsumer(
            final String tenantId,
            final String deviceId,
            final String gatewayId,
            final Handler<CommandContext> commandHandler,
            final Handler<Void> remoteCloseHandler) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(deviceId);
        Objects.requireNonNull(gatewayId);
        Objects.requireNonNull(commandHandler);

        return doCreateCommandConsumer(tenantId, deviceId, gatewayId, commandHandler, remoteCloseHandler, null);
    }

    /**
     * {@inheritDoc}
     * <p>
     * The interval used for creating the periodic liveness check will be the maximum
     * of the given interval length and {@link #MIN_LIVENESS_CHECK_INTERVAL_MILLIS}.
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

        return doCreateCommandConsumer(tenantId, deviceId, null, commandHandler, remoteCloseHandler, checkInterval);
    }

    /**
     * {@inheritDoc}
     * <p>
     * The interval used for creating the periodic liveness check will be the maximum
     * of the given interval length and {@link #MIN_LIVENESS_CHECK_INTERVAL_MILLIS}.
     */
    @Override
    public final Future<MessageConsumer> createCommandConsumer(
            final String tenantId,
            final String deviceId,
            final String gatewayId,
            final Handler<CommandContext> commandHandler,
            final Handler<Void> remoteCloseHandler,
            final long checkInterval) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(deviceId);
        Objects.requireNonNull(gatewayId);
        Objects.requireNonNull(commandHandler);
        if (checkInterval < 0) {
            throw new IllegalArgumentException("liveness check interval must be > 0");
        }

        return doCreateCommandConsumer(tenantId, deviceId, gatewayId, commandHandler, remoteCloseHandler, checkInterval);
    }

    private Future<MessageConsumer> doCreateCommandConsumer(
            final String tenantId,
            final String deviceId,
            final String gatewayId,
            final Handler<CommandContext> commandHandler,
            final Handler<Void> remoteCloseHandler,
            final Long checkInterval) {
        log.trace("create command consumer [tenant-id: {}, device-id: {}, gateway-id: {}]", tenantId, deviceId, gatewayId);
        return connection.executeOrRunOnContext(result -> {
            final String gatewayOrDeviceId = gatewayId != null ? gatewayId : deviceId;
            final String gatewayOrDeviceKey = getGatewayOrDeviceKey(tenantId, deviceId, gatewayId);

            ensureNoConflictingConsumerExists(tenantId, deviceId, gatewayId, gatewayOrDeviceKey, result);

            if (!result.isComplete()) {
                final Future<DestinationCommandConsumer> destinationCommandConsumerFuture = Future.future();
                // create the gateway or device specific destination consumer
                destinationCommandConsumerFactory.getOrCreateClient(
                        gatewayOrDeviceKey,
                        () -> newDestinationCommandConsumer(tenantId, gatewayOrDeviceId),
                        destinationCommandConsumerFuture);

                // create the device specific consumer to be returned by this method
                final Future<MessageConsumer> deviceSpecificConsumerFuture = destinationCommandConsumerFuture
                        .compose(c -> {
                            return c.addDeviceSpecificCommandHandler(deviceId, gatewayId,
                                    commandHandler, remoteCloseHandler);
                        }).map(c -> {
                            return new DeviceSpecificCommandConsumer(() -> {
                                return destinationCommandConsumerFactory.getClient(gatewayOrDeviceKey);
                            }, deviceId);
                        });

                // create the tenant-scoped consumer that maps/delegates incoming commands to the right device-scoped handler/consumer
                final Future<MessageConsumer> mappingAndDelegatingCommandConsumer = getOrCreateMappingAndDelegatingCommandConsumer(tenantId);
                CompositeFuture.all(deviceSpecificConsumerFuture, mappingAndDelegatingCommandConsumer).map(res -> {
                    if (checkInterval != null) {
                        final DestinationCommandConsumer destinationCommandConsumer = destinationCommandConsumerFuture
                                .result();
                        registerLivenessCheck(tenantId, gatewayOrDeviceId,
                                () -> destinationCommandConsumer.getCommandHandlers(), checkInterval);
                    }
                    return deviceSpecificConsumerFuture.result();
                }).setHandler(result);
            }
        });
    }

    private void ensureNoConflictingConsumerExists(final String tenantId, final String deviceId, final String gatewayId,
            final String gatewayOrDeviceKey, final Future<MessageConsumer> result) {
        final DestinationCommandConsumer commandConsumer = destinationCommandConsumerFactory.getClient(gatewayOrDeviceKey);
        if (commandConsumer != null) {
            if (!commandConsumer.isAlive()) {
                log.debug("cannot create command consumer, existing consumer not properly closed yet [tenant: {}, device-id: {}]",
                        tenantId, deviceId);
                result.fail(new ResourceConflictException("message consumer already in use"));
            } else if (commandConsumer.containsCommandHandler(deviceId)) {
                log.debug("cannot create concurrent command consumer [tenant: {}, device-id: {}]", tenantId, deviceId);
                result.fail(new ResourceConflictException("message consumer already in use"));
            } else if (gatewayId != null) {
                log.trace("gateway command consumer already exists, will add device handler to that [tenant: {}, gateway-id: {}, device-id: {}]",
                        tenantId, gatewayId, deviceId);
            } else {
                log.trace("gateway command consumer with a device specific handler already exists, will add handler for all gateway devices [tenant: {}, gateway-id: {}]",
                        tenantId, deviceId);
            }
        }
    }

    private Future<DestinationCommandConsumer> newDestinationCommandConsumer(
            final String tenantId,
            final String gatewayOrDeviceId) {

        final String gatewayOrDeviceKey = getDeviceKey(tenantId, gatewayOrDeviceId);
        return DestinationCommandConsumer.create(
                connection,
                tenantId,
                gatewayOrDeviceId,
                sourceAddress -> { // local close hook
                    // stop liveness check
                    Optional.ofNullable(destinationCommandConsumerLivenessChecks.remove(gatewayOrDeviceKey))
                            .ifPresent(livenessCheck -> connection.getVertx().cancelTimer(livenessCheck.getTimerId()));
                    destinationCommandConsumerFactory.removeClient(gatewayOrDeviceKey);
                },
                sourceAddress -> { // remote close hook
                    destinationCommandConsumerFactory.removeClient(gatewayOrDeviceKey);
                });
    }

    private Future<MessageConsumer> getOrCreateMappingAndDelegatingCommandConsumer(final String tenantId) {
        Objects.requireNonNull(tenantId);
        return connection.executeOrRunOnContext(result -> {
            final MessageConsumer messageConsumer = mappingAndDelegatingCommandConsumerFactory.getClient(tenantId);
            if (messageConsumer != null) {
                result.complete(messageConsumer);
            } else {
                mappingAndDelegatingCommandConsumerFactory.getOrCreateClient(tenantId,
                        () -> newMappingAndDelegatingCommandConsumer(tenantId),
                        result);
            }
        });
    }

    private Future<MessageConsumer> newMappingAndDelegatingCommandConsumer(final String tenantId) {

        final AtomicReference<ProtonReceiver> receiverRefHolder = new AtomicReference<>();

        final DelegateViaDownstreamPeerCommandHandler delegatingCommandHandler = new DelegateViaDownstreamPeerCommandHandler(
                (tenantIdParam, deviceIdParam) -> createDelegatedCommandSender(tenantIdParam, deviceIdParam));

        final GatewayMappingCommandHandler gatewayMappingCommandHandler = new GatewayMappingCommandHandler(
                gatewayMapper, commandContext -> { // handler following the gateway mapping
                    final String gatewayOrDeviceId = commandContext.getCommand().getDeviceId();
                    final String gatewayOrDeviceKey = getDeviceKey(tenantId, gatewayOrDeviceId);

                    CommandHandlerWrapper commandHandler = null;
                    final DestinationCommandConsumer consumer = destinationCommandConsumerFactory
                            .getClient(gatewayOrDeviceKey);
                    if (consumer != null) {
                        commandHandler = consumer
                                .getCommandHandlerOrDefault(commandContext.getCommand().getOriginalDeviceId());
                    }

                    if (commandHandler != null && isForcedCommandReroutingSet(commandContext)) { // used for integration tests
                        log.debug("forced command rerouting is set, skip usage of local {} for {}",
                                commandHandler, commandContext.getCommand());
                        commandHandler = null;
                    }

                    if (commandHandler != null) {
                        log.trace("use local {} for {}", commandHandler, commandContext.getCommand());
                        commandHandler.handleCommand(commandContext);
                    } else {
                        // delegate to matching consumer via downstream peer
                        delegatingCommandHandler.handle(commandContext);
                    }
                });

        return MappingAndDelegatingCommandConsumer.create(
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
                        // command is invalid, try to find local command handler to let it reject the command (and also report metrics for that), otherwise reject it directly
                        gatewayMapper.getMappedGatewayDevice(tenantId, deviceId, spanContext)
                                .setHandler(ar -> {
                                    final String gatewayOrDeviceId = ar.succeeded() ? ar.result() : deviceId;
                                    final DestinationCommandConsumer consumer = destinationCommandConsumerFactory
                                            .getClient(getDeviceKey(tenantId, gatewayOrDeviceId));
                                    CommandHandlerWrapper commandHandler = null;
                                    if (consumer != null) {
                                        commandHandler = consumer.getCommandHandlerOrDefault(deviceId);
                                    }
                                    if (commandHandler != null) {
                                        // let the device specific handler reject the command
                                        commandHandler.handleCommand(commandContext);
                                    } else {
                                        log.debug("command message is invalid: {}", command);
                                        commandContext.reject(new ErrorCondition(Constants.AMQP_BAD_REQUEST,
                                                "malformed command message"));
                                    }
                                });

                    }
                },
                sourceAddress -> { // local close hook
                    mappingAndDelegatingCommandConsumerFactory.removeClient(tenantId);
                },
                sourceAddress -> { // remote close hook
                    mappingAndDelegatingCommandConsumerFactory.removeClient(tenantId);
                },
                receiverRefHolder)
                .map(c -> (MessageConsumer) c);
    }

    private boolean isForcedCommandReroutingSet(final CommandContext commandContext) {
        if (!FORCED_COMMAND_REROUTING_ENABLED || !commandContext.getCommand().isValid()) {
            return false;
        }
        final ApplicationProperties applicationProperties = commandContext.getCommand().getCommandMessage()
                .getApplicationProperties();
        return Boolean.TRUE.equals(MessageHelper.getApplicationProperty(applicationProperties,
                FORCE_COMMAND_REROUTING_APPLICATION_PROPERTY, Boolean.class));
    }

    private Future<DelegatedCommandSender> createDelegatedCommandSender(final String tenantId, final String deviceId) {
        Objects.requireNonNull(tenantId);
        return connection.executeOrRunOnContext(result -> {
            delegatedCommandSenderFactory.createClient(
                    () -> DelegatedCommandSenderImpl.create(connection, tenantId, deviceId, null), result);
        });
    }

    private void registerLivenessCheck(final String tenantId, final String gatewayOrDeviceId,
            final Supplier<Collection<CommandHandlerWrapper>> commandHandlersSupplier, final long checkInterval) {
        final String gatewayOrDeviceKey = getDeviceKey(tenantId, gatewayOrDeviceId);

        destinationCommandConsumerLivenessChecks.compute(gatewayOrDeviceKey, (key, existingLivenessCheckData) -> {
            if (existingLivenessCheckData != null) {
                existingLivenessCheckData.setCommandHandlersSupplier(commandHandlersSupplier);
                return existingLivenessCheckData;
            }
            final long effectiveCheckInterval = Math.max(MIN_LIVENESS_CHECK_INTERVAL_MILLIS, checkInterval);
            final long timerId = connection.getVertx().setPeriodic(effectiveCheckInterval,
                    newLivenessCheck(tenantId, gatewayOrDeviceId));
            return new LivenessCheckData(timerId, commandHandlersSupplier);
        });
    }

    Handler<Long> newLivenessCheck(final String tenantId, final String gatewayOrDeviceId) {

        final String gatewayOrDeviceKey = getDeviceKey(tenantId, gatewayOrDeviceId);
        final AtomicBoolean recreatingDestinationConsumer = new AtomicBoolean(false);
        final AtomicBoolean recreatingMappingAndDelegatingConsumer = new AtomicBoolean(false);
        return timerId -> {
            final LivenessCheckData livenessCheck = destinationCommandConsumerLivenessChecks.get(gatewayOrDeviceKey);
            if (connection.isShutdown() || livenessCheck == null) {
                connection.getVertx().cancelTimer(timerId);
            } else {
                connection.isConnected().map(ok -> {
                    if (destinationCommandConsumerFactory.getClient(gatewayOrDeviceKey) == null) {
                        // when a connection is lost unexpectedly,
                        // all consumers will have been removed from the cache
                        // so we need to recreate the consumer
                        if (recreatingDestinationConsumer.compareAndSet(false, true)) {
                            // set a lock in order to prevent spawning multiple attempts
                            // to re-create the consumer
                            log.debug("trying to re-create destination command consumer [tenant: {}, device-id: {}]",
                                    tenantId, gatewayOrDeviceId);
                            final Future<DestinationCommandConsumer> destinationCommandConsumerFuture = Future.future();
                            destinationCommandConsumerFactory.getOrCreateClient(
                                    gatewayOrDeviceKey,
                                    () -> newDestinationCommandConsumer(tenantId, gatewayOrDeviceId),
                                    destinationCommandConsumerFuture);
                            destinationCommandConsumerFuture.map(consumer -> {
                                livenessCheck.getCommandHandlers().forEach(handler -> {
                                    log.debug("adding {} to created destination command consumer [tenant: {}, device-id: {}]",
                                            handler, tenantId, gatewayOrDeviceId);
                                    consumer.addDeviceSpecificCommandHandler(handler);
                                });
                                livenessCheck.setCommandHandlersSupplier(() -> consumer.getCommandHandlers());
                                recreatingDestinationConsumer.compareAndSet(true, false);
                                return consumer;
                            })
                            .otherwise(t -> {
                                log.info("failed to re-create destination command consumer [tenant: {}, device-id: {}]: {}",
                                        tenantId, gatewayOrDeviceId, t.getMessage());
                                return null;
                            })
                            .setHandler(s -> recreatingDestinationConsumer.compareAndSet(true, false));
                        } else {
                            log.debug("already trying to re-create destination command consumer [tenant: {}, device-id: {}], yielding ...",
                                    tenantId, gatewayOrDeviceId);
                        }
                    }

                    if (mappingAndDelegatingCommandConsumerFactory.getClient(tenantId) == null) {
                        if (recreatingMappingAndDelegatingConsumer.compareAndSet(false, true)) {
                            log.debug("trying to re-create MappingAndDelegatingCommandConsumer [tenant: {}]", tenantId);
                            getOrCreateMappingAndDelegatingCommandConsumer(tenantId)
                                    .map(consumer -> {
                                        log.debug("successfully re-created MappingAndDelegatingCommandConsumer [tenant: {}]", tenantId);
                                        return consumer;
                                    })
                                    .otherwise(t -> {
                                        log.info("failed to re-create MappingAndDelegatingCommandConsumer [tenant: {}]: {}",
                                                tenantId, t.getMessage());
                                        return null;
                                    })
                                    .setHandler(s -> recreatingMappingAndDelegatingConsumer.compareAndSet(true, false));
                        } else {
                            log.debug("already trying to re-create MappingAndDelegatingCommandConsumer [tenant: {}], yielding ...", tenantId);
                        }
                    }
                    return null;
                });
            }
        };
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

    /**
     * Only used for testing.
     */
    Map<String, LivenessCheckData> getDestinationCommandConsumerLivenessChecks() {
        return destinationCommandConsumerLivenessChecks;
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

    /**
     * Represents a liveness check corresponding to a destination command consumer.
     * Contains the id of the Vert.x timer that invokes the liveness check.
     */
    static class LivenessCheckData {
        private final long timerId;
        private Supplier<Collection<CommandHandlerWrapper>> commandHandlersSupplier;

        LivenessCheckData(final long timerId, final Supplier<Collection<CommandHandlerWrapper>> commandHandlersSupplier) {
            this.timerId = Objects.requireNonNull(timerId);
            this.commandHandlersSupplier = Objects.requireNonNull(commandHandlersSupplier);
        }

        /**
         * Gets the id of the Vert.x timer that invokes the liveness check.
         *
         * @return The timer id.
         */
        public long getTimerId() {
            return timerId;
        }

        /**
         * Gets the command handlers to be re-registered once the liveness check has
         * determined the command consumer has to be recreated.
         *
         * @return The command handlers.
         */
        public Collection<CommandHandlerWrapper> getCommandHandlers() {
            return commandHandlersSupplier.get();
        }

        /**
         * Sets the supplier that provides the command handlers to be re-registered
         * once the liveness check has determined the command consumer has to be recreated.
         *
         * @param commandHandlersSupplier Provides the command handlers.
         * @throws NullPointerException if commandHandlersSupplier is {@code null}.
         */
        public void setCommandHandlersSupplier(final Supplier<Collection<CommandHandlerWrapper>> commandHandlersSupplier) {
            this.commandHandlersSupplier = Objects.requireNonNull(commandHandlersSupplier);
        }
    }
}
