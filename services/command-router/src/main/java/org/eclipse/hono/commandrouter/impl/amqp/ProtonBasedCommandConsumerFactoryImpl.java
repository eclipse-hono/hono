/*******************************************************************************
 * Copyright (c) 2020, 2023 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.commandrouter.impl.amqp;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.hono.client.amqp.AbstractServiceClient;
import org.eclipse.hono.client.amqp.config.AddressHelper;
import org.eclipse.hono.client.amqp.connection.HonoConnection;
import org.eclipse.hono.client.amqp.connection.SendMessageSampler;
import org.eclipse.hono.client.command.amqp.ProtonBasedInternalCommandSender;
import org.eclipse.hono.client.registry.TenantClient;
import org.eclipse.hono.client.util.CachingClientFactory;
import org.eclipse.hono.commandrouter.CommandConsumerFactory;
import org.eclipse.hono.commandrouter.CommandRouterMetrics;
import org.eclipse.hono.commandrouter.CommandTargetMapper;
import org.eclipse.hono.notification.NotificationEventBusSupport;
import org.eclipse.hono.notification.deviceregistry.AllDevicesOfTenantDeletedNotification;
import org.eclipse.hono.notification.deviceregistry.LifecycleChange;
import org.eclipse.hono.notification.deviceregistry.TenantChangeNotification;
import org.eclipse.hono.util.CommandConstants;

import io.opentracing.SpanContext;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.proton.ProtonQoS;

/**
 * A factory for creating clients for the <em>AMQP 1.0 Messaging Network</em> to receive commands.
 * <p>
 * The factory uses tenant-scoped links, created (if not already existing for the tenant) when
 * {@link CommandConsumerFactory#createCommandConsumer(String, SpanContext)} is invoked.
 * <p>
 * Command messages are first received on the tenant-scoped consumer address. It is then determined which protocol
 * adapter instance can handle the command. The command is then forwarded to the AMQP messaging network on
 * an address containing that adapter instance id.
 */
public class ProtonBasedCommandConsumerFactoryImpl extends AbstractServiceClient implements
        CommandConsumerFactory {

    private static final int RECREATE_CONSUMERS_DELAY = 20;

    /**
     * Cache key used here is the tenant id.
     */
    private final CachingClientFactory<CommandConsumer> mappingAndDelegatingCommandConsumerFactory;

    private final AtomicBoolean recreatingConsumers = new AtomicBoolean(false);
    private final AtomicBoolean tryAgainRecreatingConsumers = new AtomicBoolean(false);

    private final ProtonBasedCommandProcessingQueue commandQueue;
    private final ProtonBasedMappingAndDelegatingCommandHandler mappingAndDelegatingCommandHandler;
    /**
     * List of tenant ids corresponding to the tenants for which consumers have been registered.
     */
    private final Set<String> consumerLinkTenants = new HashSet<>(); 

    /**
     * Creates a new factory for an existing connection.
     *
     * @param connection The connection to the AMQP network, used for receiving command messages and forwarding them
     *                   on the 'command_internal' address.
     * @param tenantClient The Tenant service client.
     * @param commandTargetMapper The component for mapping an incoming command to the gateway (if applicable) and
     *            protocol adapter instance that can handle it. Note that no initialization of this factory will be done
     *            here, that is supposed to be done by the calling method.
     * @param metrics The component to use for reporting metrics.
     * @param samplerFactory The sampler factory to use.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    public ProtonBasedCommandConsumerFactoryImpl(
            final HonoConnection connection,
            final TenantClient tenantClient,
            final CommandTargetMapper commandTargetMapper,
            final CommandRouterMetrics metrics,
            final SendMessageSampler.Factory samplerFactory) {
        super(connection, samplerFactory);
        Objects.requireNonNull(tenantClient);
        Objects.requireNonNull(commandTargetMapper);

        final Vertx vertx = connection.getVertx();
        final var internalCommandSender = new ProtonBasedInternalCommandSender(connection);
        // connection establishment already done in the start() method of this class, therefore skip it in internalCommandSender
        internalCommandSender.setSkipConnectDisconnectOnStartStop(true);
        commandQueue = new ProtonBasedCommandProcessingQueue(vertx);
        mappingAndDelegatingCommandHandler = new ProtonBasedMappingAndDelegatingCommandHandler(vertx, tenantClient,
                commandQueue, internalCommandSender, commandTargetMapper, metrics, connection.getTracer());
        mappingAndDelegatingCommandConsumerFactory = new CachingClientFactory<>(vertx, c -> true);
    }

    @Override
    public Future<Void> start() {
        registerCloseConsumerLinkHandler();
        return Future.all(connectOnStart(), mappingAndDelegatingCommandHandler.start())
                .map((Void) null)
                .onSuccess(v -> {
                    connection.addReconnectListener(c -> recreateConsumers());
                    // trigger creation of adapter specific consumer link (with retry if failed)
                    recreateConsumers();
                });
    }

    @Override
    public Future<Void> stop() {
        return Future.join(disconnectOnStop(), mappingAndDelegatingCommandHandler.stop())
                .map((Void) null);
    }

    private void registerCloseConsumerLinkHandler() {
        NotificationEventBusSupport.registerConsumer(connection.getVertx(), AllDevicesOfTenantDeletedNotification.TYPE,
                notification -> {
                    Optional.ofNullable(mappingAndDelegatingCommandConsumerFactory
                            .getClient(notification.getTenantId()))
                            .ifPresent(CommandConsumer::close);
                });
        NotificationEventBusSupport.registerConsumer(connection.getVertx(), TenantChangeNotification.TYPE,
                notification -> {
                    if (LifecycleChange.DELETE.equals(notification.getChange())
                            || (LifecycleChange.UPDATE.equals(notification.getChange())
                                    && !notification.isTenantEnabled())) {
                        Optional.ofNullable(mappingAndDelegatingCommandConsumerFactory
                                .getClient(notification.getTenantId()))
                                .ifPresent(CommandConsumer::close);
                    }
                });
    }

    @Override
    protected void onDisconnect() {
        mappingAndDelegatingCommandConsumerFactory.onDisconnect();
        consumerLinkTenants.clear();
    }

    @Override
    public final Future<Void> createCommandConsumer(final String tenantId, final SpanContext context) {
        Objects.requireNonNull(tenantId);
        return connection.executeOnContext(result -> {
            // create the tenant-scoped consumer ("command/<tenantId>") that maps/delegates incoming commands to the right adapter-instance
            getOrCreateMappingAndDelegatingCommandConsumer(tenantId)
                    .map((Void) null)
                    .onComplete(result);
        });
    }

    private Future<CommandConsumer> getOrCreateMappingAndDelegatingCommandConsumer(final String tenantId) {
        final Future<CommandConsumer> messageConsumerFuture = connection.isConnected(getDefaultConnectionCheckTimeout())
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

    private Future<CommandConsumer> newMappingAndDelegatingCommandConsumer(final String tenantId) {
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
                    consumerLinkTenants.add(tenantId);
                    return (CommandConsumer) new CommandConsumer() {
                        @Override
                        public Future<Void> close() {
                            log.debug("MappingAndDelegatingCommandConsumer consumer [tenant-id: {}] closed locally", tenantId);
                            mappingAndDelegatingCommandConsumerFactory.removeClient(tenantId);
                            consumerLinkTenants.remove(tenantId);
                            final Promise<Void> result = Promise.promise();
                            connection.closeAndFree(receiver, receiverClosed -> result.complete());
                            commandQueue.removeEntriesForTenant(tenantId);
                            return result.future();
                        }
                    };
                }).recover(t -> {
                    log.debug("failed to create MappingAndDelegatingCommandConsumer [tenant-id: {}]", tenantId, t);
                    return Future.failedFuture(t);
                });
    }

    private void recreateConsumers() {
        if (recreatingConsumers.compareAndSet(false, true)) {
            log.debug("recreate command consumer links");
            connection.isConnected(getDefaultConnectionCheckTimeout())
                    .compose(res -> {
                        final List<Future<CommandConsumer>> consumerCreationFutures = new ArrayList<>();
                        // recreate mappingAndDelegatingCommandConsumers
                        consumerLinkTenants.forEach(tenantId -> {
                            log.debug("recreate command consumer link for tenant {}", tenantId);
                            consumerCreationFutures.add(getOrCreateMappingAndDelegatingCommandConsumer(tenantId));
                        });
                        return Future.join(consumerCreationFutures);
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

    /**
     * A command consumer representation, letting associated resources be closed via the {@link #close()} method.
     */
    private interface CommandConsumer {

        /**
         * Closes the consumer.
         *
         * @return A future indicating the outcome of the operation.
         */
        Future<Void> close();
    }
}
