/*******************************************************************************
 * Copyright (c) 2018, 2021 Contributors to the Eclipse Foundation
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
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.CommandContext;
import org.eclipse.hono.client.CommandResponseSender;
import org.eclipse.hono.client.CommandTargetMapper;
import org.eclipse.hono.client.HonoConnection;
import org.eclipse.hono.client.MessageConsumer;
import org.eclipse.hono.client.ProtocolAdapterCommandConsumer;
import org.eclipse.hono.client.ProtocolAdapterCommandConsumerFactory;
import org.eclipse.hono.client.SendMessageSampler;
import org.eclipse.hono.client.ServerErrorException;
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.util.AddressHelper;
import org.eclipse.hono.util.CommandConstants;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.HonoProtonHelper;
import org.eclipse.hono.util.Strings;

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
 *
 * @deprecated Use {@code org.eclipse.hono.client.command.amqp.ProtonBasedDelegatingCommandConsumerFactory} instead.
 */
@Deprecated
public class ProtocolAdapterCommandConsumerFactoryImpl extends AbstractHonoClientFactory implements ProtocolAdapterCommandConsumerFactory {

    private static final int RECREATE_CONSUMERS_DELAY = 20;

    /**
     * Cache key used here is the tenant id.
     */
    private CachingClientFactory<MessageConsumer> mappingAndDelegatingCommandConsumerFactory;

    /**
     * Identifier that has to be unique to this factory instance.
     * Will be used to represent the protocol adapter instance that this factory instance is used in,
     * when registering command handlers with the CommandHandlingAdapterInfoAccess service.
     */
    private final String adapterInstanceId;
    private final AdapterInstanceCommandHandler adapterInstanceCommandHandler;
    private final AtomicBoolean recreatingConsumers = new AtomicBoolean(false);
    private final AtomicBoolean tryAgainRecreatingConsumers = new AtomicBoolean(false);

    private CommandHandlingAdapterInfoAccess commandHandlingAdapterInfoAccessor;
    private MappingAndDelegatingCommandHandler mappingAndDelegatingCommandHandler;
    private ProtonReceiver adapterSpecificConsumer;
    private final AtomicBoolean initialized = new AtomicBoolean(false);

    /**
     * Creates a new factory for an existing connection.
     *
     * @param connection The connection to the AMQP network.
     * @param samplerFactory The sampler factory to use.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    public ProtocolAdapterCommandConsumerFactoryImpl(final HonoConnection connection, final SendMessageSampler.Factory samplerFactory) {
        super(connection, samplerFactory);

        adapterInstanceId = getAdapterInstanceId(connection.getConfig().getName());

        adapterInstanceCommandHandler = new AdapterInstanceCommandHandler(connection.getTracer(), adapterInstanceId);
    }

    private static String getAdapterInstanceId(final String adapterName) {
        // replace special characters so that the id can be used in a Kafka topic name
        final String prefix = Strings.isNullOrEmpty(adapterName) ? ""
                : adapterName.replaceAll("[^a-zA-Z0-9._-]", "") + "-";
        return prefix + UUID.randomUUID();
    }

    @Override
    public void initialize(
            final CommandTargetMapper commandTargetMapper,
            final CommandHandlingAdapterInfoAccess accessor) {

        Objects.requireNonNull(commandTargetMapper);
        this.commandHandlingAdapterInfoAccessor = Objects.requireNonNull(accessor);

        mappingAndDelegatingCommandHandler = new MappingAndDelegatingCommandHandler(connection,
                commandTargetMapper, adapterInstanceCommandHandler, adapterInstanceId, samplerFactory.create(CommandConstants.COMMAND_ENDPOINT));
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
        adapterSpecificConsumer = null;
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
                        final CommandHandlerWrapper commandHandlerWrapper = new CommandHandlerWrapper(tenantId,
                                deviceId, gatewayId, commandHandler);
                        final CommandHandlerWrapper replacedHandler = adapterInstanceCommandHandler
                                .putDeviceSpecificCommandHandler(commandHandlerWrapper);
                        if (replacedHandler != null) {
                            // TODO find a way to provide a notification here so that potential resources associated with the replaced consumer can be freed (maybe add a commandHandlerOverwritten Handler param to createCommandConsumer())
                        }
                        // associate handler with this adapter instance
                        final Instant lifespanStart = Instant.now();
                        return setCommandHandlingAdapterInstance(tenantId, deviceId, sanitizedLifespan, context)
                                .map(v -> {
                                    final Function<SpanContext, Future<Void>> onCloseAction = onCloseSpanContext -> {
                                        return removeCommandConsumer(commandHandlerWrapper, sanitizedLifespan,
                                                lifespanStart, onCloseSpanContext);
                                    };
                                    return (ProtocolAdapterCommandConsumer) new ProtocolAdapterCommandConsumerImpl(onCloseAction);
                                });
                    })
                    .onComplete(result);
        });
    }

    private Future<Void> setCommandHandlingAdapterInstance(final String tenantId, final String deviceId,
            final Duration lifespan, final SpanContext context) {
        return commandHandlingAdapterInfoAccessor.setCommandHandlingAdapterInstance(tenantId, deviceId, adapterInstanceId, lifespan, context)
                .recover(thr -> {
                    log.info("error setting command handling adapter instance [tenant: {}, device: {}]", tenantId,
                            deviceId, thr);
                    // handler association failed - unregister the handler
                    adapterInstanceCommandHandler.removeDeviceSpecificCommandHandler(tenantId, deviceId);
                    return Future.failedFuture(thr);
                });
    }

    private Future<Void> removeCommandConsumer(final CommandHandlerWrapper commandHandlerWrapper, final Duration lifespan,
            final Instant lifespanStart, final SpanContext onCloseSpanContext) {

        final String tenantId = commandHandlerWrapper.getTenantId();
        final String deviceId = commandHandlerWrapper.getDeviceId();

        log.trace("remove command consumer [tenant-id: {}, device-id: {}]", tenantId, deviceId);
        if (!adapterInstanceCommandHandler.removeDeviceSpecificCommandHandler(commandHandlerWrapper)) {
            // This case happens when trying to remove a command consumer which has been overwritten since its creation
            // via a 2nd invocation of 'createCommandConsumer' with the same device/tenant id. Since the 2nd 'createCommandConsumer'
            // invocation has registered a different 'commandHandlerWrapper' instance (and possibly already removed it),
            // trying to remove the original object will return false here.
            // On a more abstract level, this case happens when 2 consecutive command subscription requests from the
            // same device (with no intermittent disconnect/unsubscribe - possibly because of a broken connection in between) have
            // reached the *same* adapter instance and verticle, using this CommandConsumerFactory. Invoking 'removeCommandConsumer'
            // on the 1st (obsolete and overwritten) command subscription shall have no impact. Throwing an explicit exception
            // here will enable the protocol adapter to detect this case and skip an (incorrect) "disconnectedTtd" event message.
            log.debug("command consumer not removed - handler already replaced or removed [tenant: {}, device: {}]",
                    tenantId, deviceId);
            return Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_PRECON_FAILED,
                    "local command handler already replaced or removed"));
        }
        return commandHandlingAdapterInfoAccessor.removeCommandHandlingAdapterInstance(
                    tenantId,
                    deviceId,
                    adapterInstanceId,
                    onCloseSpanContext)
                .recover(thr -> {
                    if (ServiceInvocationException.extractStatusCode(thr) == HttpURLConnection.HTTP_PRECON_FAILED) {
                        final boolean entryMayHaveExpired = !lifespan.isNegative() && Instant.now().isAfter(lifespanStart.plus(lifespan));
                        if (entryMayHaveExpired) {
                            log.trace("ignoring 412 error when removing command handling adapter instance; entry may have already expired [tenant: {}, device: {}]",
                                    tenantId, deviceId);
                            return Future.succeededFuture();
                        } else {
                            // entry wasn't actually removed and entry hasn't expired (yet);
                            // This case happens when 2 consecutive command subscription requests from the same device
                            // (with no intermittent disconnect/unsubscribe - possibly because of a broken connection in between)
                            // have reached *different* protocol adapter instances/verticles. Now calling 'removeCommandHandlingAdapterInstance'
                            // on the 1st subscription fails because of the non-matching adapterInstanceId parameter.
                            // Throwing an explicit exception here will enable the protocol adapter to detect this case
                            // and skip sending an (incorrect) "disconnectedTtd" event message.
                            log.debug("command handling adapter instance not removed - not matched or already removed [tenant: {}, device: {}]",
                                    tenantId, deviceId);
                            return Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_PRECON_FAILED,
                                    "no matching command consumer mapping found to be removed"));
                        }
                    } else {
                        log.info("error removing command handling adapter instance [tenant: {}, device: {}]", tenantId,
                                deviceId, thr);
                        return Future.failedFuture(thr);
                    }
                });
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
        final String address = AddressHelper.getTargetAddress(CommandConstants.NORTHBOUND_COMMAND_REQUEST_ENDPOINT, tenantId, null, connection.getConfig());
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
                        if (!HonoProtonHelper.isLinkOpenAndConnected(adapterSpecificConsumer)) {
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
                    }).onComplete(ar -> {
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
        final List<CommandHandlerWrapper> tenantRelatedHandlers = adapterInstanceCommandHandler
                .getDeviceSpecificCommandHandlers().stream().filter(handler -> handler.getTenantId().equals(tenantId))
                .collect(Collectors.toList());
        if (tenantRelatedHandlers.isEmpty()) {
            final MessageConsumer consumer = mappingAndDelegatingCommandConsumerFactory.getClient(tenantId);
            if (consumer != null) {
                log.info("tenant timeout: closing and removing command consumer [tenant {}]", tenantId);
                consumer.close(v -> mappingAndDelegatingCommandConsumerFactory.removeClient(tenantId));
            }
        } else {
            log.debug("ignoring tenant timeout; there are still {} command handlers for tenant devices [tenant {}]",
                    tenantRelatedHandlers.size(), tenantId);
        }
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
            CommandResponseSenderImpl.create(connection, tenantId, replyId, samplerFactory.create(CommandConstants.COMMAND_RESPONSE_ENDPOINT), onRemoteClose -> {}).onComplete(result);
        });
    }

}
