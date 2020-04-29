/*******************************************************************************
 * Copyright (c) 2018, 2020 Contributors to the Eclipse Foundation
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

import java.net.HttpURLConnection;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

import org.eclipse.hono.client.BasicDeviceConnectionClientFactory;
import org.eclipse.hono.client.CommandContext;
import org.eclipse.hono.client.CommandResponseSender;
import org.eclipse.hono.client.CommandTargetMapper;
import org.eclipse.hono.client.HonoConnection;
import org.eclipse.hono.client.MessageConsumer;
import org.eclipse.hono.client.ProtocolAdapterCommandConsumer;
import org.eclipse.hono.client.ProtocolAdapterCommandConsumerFactory;
import org.eclipse.hono.client.ServerErrorException;
import org.eclipse.hono.util.CommandConstants;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.ResourceIdentifier;

import io.opentracing.SpanContext;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.Message;
import io.vertx.proton.ProtonQoS;
import io.vertx.proton.ProtonReceiver;

/**
 * A factory for creating clients for the <em>AMQP 1.0 Messaging Network</em> to receive commands and send responses.
 * <p>
 * The factory uses two kinds of consumer links to receive commands:
 * <ul>
 * <li>A single consumer link on an address containing the protocol adapter instance id.</li>
 * <li>A tenant-scoped link, created (if not already existing for that tenant) when
 * {@link #createCommandConsumer(String, String, Handler, Duration, SpanContext)} is invoked.</li>
 * </ul>
 * <p>
 * Command messages are first received on the tenant-scoped consumer address. It is then determined whether there is
 * a consumer and corresponding command handler for the command message's target device or one of the device's
 * possible gateways. If found, that handler is either invoked directly, or, if it is on another protocol adapter
 * instance, the command message is sent to that protocol adapter instance to be handled there.
 */
public class ProtocolAdapterCommandConsumerFactoryImpl extends AbstractHonoClientFactory implements ProtocolAdapterCommandConsumerFactory {

    private static final int RECREATE_CONSUMERS_DELAY = 20;

    /**
     * Cache key used here is the tenant id.
     */
    private CachingClientFactory<MessageConsumer> mappingAndDelegatingCommandConsumerFactory;

    /**
     * Identifier that has to be unique to this factory instance.
     * Will be used to represent the protocol adapter instance that this factory instance is used in, when registering
     * command handlers with the Device Connection service.
     */
    private final String adapterInstanceId;
    private final AdapterInstanceCommandHandler adapterInstanceCommandHandler;
    private final AtomicBoolean recreatingConsumers = new AtomicBoolean(false);
    private final AtomicBoolean tryAgainRecreatingConsumers = new AtomicBoolean(false);

    private BasicDeviceConnectionClientFactory deviceConnectionClientFactory;
    private MappingAndDelegatingCommandHandler mappingAndDelegatingCommandHandler;
    private ProtonReceiver adapterSpecificConsumer;
    private final AtomicBoolean initialized = new AtomicBoolean(false);

    /**
     * Creates a new factory for an existing connection.
     * 
     * @param connection The connection to the AMQP network.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    public ProtocolAdapterCommandConsumerFactoryImpl(final HonoConnection connection) {
        super(connection);
        // the container id contains a UUID therefore it can be used as a unique adapter instance id
        adapterInstanceId = connection.getContainerId();

        adapterInstanceCommandHandler = new AdapterInstanceCommandHandler(connection.getTracer(), adapterInstanceId);
    }

    @Override
    public void initialize(final CommandTargetMapper commandTargetMapper,
            final BasicDeviceConnectionClientFactory deviceConnectionClientFactory) {
        Objects.requireNonNull(commandTargetMapper);
        this.deviceConnectionClientFactory = Objects.requireNonNull(deviceConnectionClientFactory);

        mappingAndDelegatingCommandHandler = new MappingAndDelegatingCommandHandler(connection,
                commandTargetMapper, adapterInstanceCommandHandler, adapterInstanceId);
        mappingAndDelegatingCommandConsumerFactory = new CachingClientFactory<>(connection.getVertx(), c -> true);

        connection.getVertx().eventBus().consumer(Constants.EVENT_BUS_ADDRESS_TENANT_TIMED_OUT,
                this::handleTenantTimeout);
        connection.addReconnectListener(c -> recreateConsumers());
        // trigger creation of adapter specific consumer link (with retry if failed)
        recreateConsumers();
        initialized.set(true);
    }

    @Override
    protected void onDisconnect() {
        if (adapterSpecificConsumer != null) {
            connection.closeAndFree(adapterSpecificConsumer, v -> {});
        }
        mappingAndDelegatingCommandConsumerFactory.clearState();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final Future<ProtocolAdapterCommandConsumer> createCommandConsumer(final String tenantId, final String deviceId,
            final Handler<CommandContext> commandHandler, final Duration lifespan, final SpanContext context) {
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(deviceId);
        Objects.requireNonNull(commandHandler);
        return doCreateCommandConsumer(tenantId, deviceId, null, commandHandler, lifespan, context);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final Future<ProtocolAdapterCommandConsumer> createCommandConsumer(final String tenantId,
            final String deviceId, final String gatewayId, final Handler<CommandContext> commandHandler,
            final Duration lifespan, final SpanContext context) {
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(deviceId);
        Objects.requireNonNull(gatewayId);
        Objects.requireNonNull(commandHandler);
        return doCreateCommandConsumer(tenantId, deviceId, gatewayId, commandHandler, lifespan, context);
    }

    private Future<ProtocolAdapterCommandConsumer> doCreateCommandConsumer(final String tenantId, final String deviceId,
            final String gatewayId, final Handler<CommandContext> commandHandler, final Duration lifespan,
            final SpanContext context) {
        if (!initialized.get()) {
            log.error("not initialized");
            return Future.failedFuture(new ServerErrorException(HttpURLConnection.HTTP_INTERNAL_ERROR));
        }
        // lifespan greater than what can be expressed in nanoseconds (i.e. 292 years) is considered unlimited, preventing ArithmeticExceptions down the road
        final Duration sanitizedLifespan = lifespan == null || lifespan.isNegative()
                || lifespan.getSeconds() > (Long.MAX_VALUE / 1000_000_000L) ? Duration.ofSeconds(-1) : lifespan;
        log.trace("create command consumer [tenant-id: {}, device-id: {}, gateway-id: {}]", tenantId, deviceId, gatewayId);
        return connection.executeOnContext(result -> {
            // create the tenant-scoped consumer ("command/<tenantId>") that maps/delegates incoming commands to the right handler/adapter-instance
            getOrCreateMappingAndDelegatingCommandConsumer(tenantId)
                    .compose(res -> {
                        // register the command handler
                        final CommandHandlerWrapper replacedHandler = adapterInstanceCommandHandler
                                .putDeviceSpecificCommandHandler(tenantId, deviceId, gatewayId, commandHandler);
                        if (replacedHandler != null) {
                            // TODO find a way to provide a notification here so that potential resources associated with the replaced consumer can be freed (maybe add a commandHandlerOverwritten Handler param to createCommandConsumer())
                        }
                        // associate handler with this adapter instance
                        return setCommandHandlingAdapterInstance(tenantId, deviceId, sanitizedLifespan, context);
                    })
                    .map(res -> {
                        final Function<SpanContext, Future<Void>> onCloseAction = onCloseSpanContext -> removeCommandConsumer(tenantId, deviceId,
                                onCloseSpanContext);
                        return (ProtocolAdapterCommandConsumer) new ProtocolAdapterCommandConsumerImpl(onCloseAction);
                    })
                    .setHandler(result);
        });
    }

    private Future<Void> setCommandHandlingAdapterInstance(final String tenantId, final String deviceId,
            final Duration lifespan, final SpanContext context) {
        return deviceConnectionClientFactory.getOrCreateDeviceConnectionClient(tenantId)
                .compose(client -> client.setCommandHandlingAdapterInstance(deviceId, adapterInstanceId, lifespan,
                        false, context))
                .recover(thr -> {
                    log.info("error setting command handling adapter instance [tenant: {}, device: {}]", tenantId,
                            deviceId, thr);
                    // handler association failed - unregister the handler
                    adapterInstanceCommandHandler.removeDeviceSpecificCommandHandler(tenantId, deviceId);
                    return Future.failedFuture(thr);
                });
    }

    private Future<Void> removeCommandConsumer(final String tenantId, final String deviceId,
            final SpanContext onCloseSpanContext) {
        log.trace("remove command consumer [tenant-id: {}, device-id: {}]", tenantId, deviceId);
        adapterInstanceCommandHandler.removeDeviceSpecificCommandHandler(tenantId, deviceId);

        return deviceConnectionClientFactory.getOrCreateDeviceConnectionClient(tenantId)
                .compose(client -> client.removeCommandHandlingAdapterInstance(deviceId, adapterInstanceId,
                        onCloseSpanContext))
                .recover(thr -> {
                    log.warn("error removing command handling adapter instance [tenant: {}, device: {}]", tenantId,
                            deviceId, thr);
                    return Future.failedFuture(thr);
                })
                .mapEmpty();
    }

    private Future<MessageConsumer> getOrCreateMappingAndDelegatingCommandConsumer(final String tenantId) {
        final Future<MessageConsumer> messageConsumerFuture = connection.isConnected(getDefaultConnectionCheckTimeout())
                .compose(v -> connection.executeOnContext(result -> {
                    mappingAndDelegatingCommandConsumerFactory.getOrCreateClient(tenantId,
                            () -> newMappingAndDelegatingCommandConsumer(tenantId),
                            result);
                }));
        return messageConsumerFuture.recover(thr -> {
            log.debug("failed to create mappingAndDelegatingCommandConsumer for tenant {}", tenantId, thr);
            return Future.failedFuture(thr);
        });
    }

    private Future<MessageConsumer> newMappingAndDelegatingCommandConsumer(final String tenantId) {
        log.trace("creating new MappingAndDelegatingCommandConsumer [tenant-id: {}]", tenantId);
        final String address = ResourceIdentifier.from(CommandConstants.NORTHBOUND_COMMAND_REQUEST_ENDPOINT, tenantId, null).toString();
        return connection.createReceiver(
                address,
                ProtonQoS.AT_LEAST_ONCE,
                (delivery, message) -> {
                    mappingAndDelegatingCommandHandler.mapAndDelegateIncomingCommandMessage(tenantId, delivery, message);
                },
                connection.getConfig().getInitialCredits(),
                false, // no auto-accept
                sourceAddress -> { // remote close hook
                    log.debug("MappingAndDelegatingCommandConsumer receiver link [tenant-id: {}] closed remotely", tenantId);
                    mappingAndDelegatingCommandConsumerFactory.removeClient(tenantId);
                    invokeRecreateConsumersWithDelay();
                }).map(receiver -> {
                    log.debug("successfully created MappingAndDelegatingCommandConsumer [{}]", address);
                    final CommandConsumer consumer = new CommandConsumer(connection, receiver);
                    consumer.setLocalCloseHandler(sourceAddress -> {
                        log.debug("MappingAndDelegatingCommandConsumer receiver link [tenant-id: {}] closed locally", tenantId);
                        mappingAndDelegatingCommandConsumerFactory.removeClient(tenantId);
                    });
                    return (MessageConsumer) consumer;
                }).recover(t -> {
                    log.debug("failed to create MappingAndDelegatingCommandConsumer [tenant-id: {}]", tenantId, t);
                    return Future.failedFuture(t);
                });
    }

    private Future<ProtonReceiver> createAdapterSpecificConsumer() {
        log.trace("creating new adapter instance command consumer");
        final String adapterInstanceConsumerAddress = CommandConstants.INTERNAL_COMMAND_ENDPOINT + "/"
                + adapterInstanceId;
        return connection.createReceiver(
                adapterInstanceConsumerAddress,
                ProtonQoS.AT_LEAST_ONCE,
                (delivery, msg) -> adapterInstanceCommandHandler.handleCommandMessage(msg, delivery),
                connection.getConfig().getInitialCredits(),
                false, // no auto-accept
                sourceAddress -> { // remote close hook
                    log.debug("command receiver link closed remotely");
                    invokeRecreateConsumersWithDelay();
                }).map(receiver -> {
                    log.debug("successfully created adapter specific command consumer");
                    adapterSpecificConsumer = receiver;
                    return receiver;
                }).recover(t -> {
                    log.error("failed to create adapter specific command consumer", t);
                    return Future.failedFuture(t);
                });
    }

    private void recreateConsumers() {
        if (recreatingConsumers.compareAndSet(false, true)) {
            log.debug("recreate command consumer links");
            connection.isConnected(getDefaultConnectionCheckTimeout())
                    .compose(res -> {
                        @SuppressWarnings("rawtypes")
                        final List<Future> consumerCreationFutures = new ArrayList<>();
                        // recreate adapter specific consumer
                        if (adapterSpecificConsumer == null || !adapterSpecificConsumer.isOpen()) {
                            log.debug("recreate adapter specific command consumer link");
                            consumerCreationFutures.add(createAdapterSpecificConsumer());
                        }
                        // recreate mappingAndDelegatingCommandConsumers
                        adapterInstanceCommandHandler.getDeviceSpecificCommandHandlers().stream()
                                .map(CommandHandlerWrapper::getTenantId).distinct().forEach(tenantId -> {
                                    log.debug("recreate command consumer link for tenant {}", tenantId);
                                    consumerCreationFutures.add(
                                            getOrCreateMappingAndDelegatingCommandConsumer(tenantId));
                                });
                        return CompositeFuture.join(consumerCreationFutures);
                    }).setHandler(ar -> {
                        recreatingConsumers.set(false);
                        if (tryAgainRecreatingConsumers.compareAndSet(true, false) || ar.failed()) {
                            if (ar.succeeded()) {
                                // tryAgainRecreatingConsumers was set - try again immediately
                                recreateConsumers();
                            } else {
                                invokeRecreateConsumersWithDelay();
                            }
                        }
                    });
        } else {
            // if recreateConsumers() was triggered by a remote link closing, that might have occurred after that link was dealt with above;
            // therefore be sure recreateConsumers() gets called again once the current invocation has finished.
            log.debug("already recreating consumers");
            tryAgainRecreatingConsumers.set(true);
        }
    }

    private void invokeRecreateConsumersWithDelay() {
        connection.getVertx().setTimer(RECREATE_CONSUMERS_DELAY, tid -> recreateConsumers());
    }

    private void handleTenantTimeout(final Message<String> msg) {
        final String tenantId = msg.body();
        final MessageConsumer consumer = mappingAndDelegatingCommandConsumerFactory.getClient(tenantId);
        if (consumer != null) {
            log.info("timeout of tenant {}: closing and removing command consumer", tenantId);
            consumer.close(v -> mappingAndDelegatingCommandConsumerFactory.removeClient(tenantId));
        }
        adapterInstanceCommandHandler
                .getDeviceSpecificCommandHandlers().stream().filter(handler -> handler.getTenantId().equals(tenantId))
                .forEach(handler -> {
                    log.info("timeout of tenant {}: removing command handler for device {}", tenantId, handler.getDeviceId());
                    adapterInstanceCommandHandler.removeDeviceSpecificCommandHandler(handler.getTenantId(), handler.getDeviceId());
                });
    }

    /**
     * {@inheritDoc}
     * 
     * This implementation always creates a new sender link.
     */
    @Override
    public Future<CommandResponseSender> getCommandResponseSender(final String tenantId, final String replyId) {
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(replyId);
        return connection.executeOnContext(result -> {
            CommandResponseSenderImpl.create(connection, tenantId, replyId, onRemoteClose -> {}).setHandler(result);
        });
    }

}
