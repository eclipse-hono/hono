/*******************************************************************************
 * Copyright (c) 2020, 2021 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.adapter.client.command.amqp;

import java.net.HttpURLConnection;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.hono.adapter.client.amqp.AbstractServiceClient;
import org.eclipse.hono.adapter.client.command.CommandConsumer;
import org.eclipse.hono.adapter.client.command.CommandConsumerFactory;
import org.eclipse.hono.adapter.client.command.CommandContext;
import org.eclipse.hono.adapter.client.command.CommandHandlerWrapper;
import org.eclipse.hono.adapter.client.command.CommandHandlers;
import org.eclipse.hono.adapter.client.command.CommandRouterClient;
import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.HonoConnection;
import org.eclipse.hono.client.SendMessageSampler;
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.util.CommandConstants;
import org.eclipse.hono.util.Strings;

import io.opentracing.SpanContext;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.proton.ProtonQoS;
import io.vertx.proton.ProtonReceiver;

/**
 * A vertx-proton based factory for creating consumers of command messages received via the
 * AMQP 1.0 Messaging Network.
 * <p>
 * This implementation uses the Command Router service and receives commands forwarded by the Command Router
 * component on a link with an address containing the protocol adapter instance id.
 *
 */
public class ProtonBasedCommandRouterCommandConsumerFactoryImpl extends AbstractServiceClient implements CommandConsumerFactory {

    private static final int RECREATE_CONSUMER_DELAY = 20;

    /**
     * Identifier that has to be unique to this factory instance.
     * Will be used to represent the protocol adapter instance that this factory instance is used in,
     * when registering command handlers with the command router service client.
     */
    private final String adapterInstanceId;
    private final CommandHandlers commandHandlers = new CommandHandlers();
    private final ProtonBasedAdapterInstanceCommandHandler adapterInstanceCommandHandler;
    private final AtomicBoolean recreatingConsumer = new AtomicBoolean(false);
    private final AtomicBoolean tryAgainRecreatingConsumer = new AtomicBoolean(false);

    private final CommandRouterClient commandRouterClient;
    private ProtonReceiver adapterSpecificConsumer;

    /**
     * Creates a new factory for an existing connection.
     *
     * @param connection The connection to the AMQP network.
     * @param samplerFactory The sampler factory to use.
     * @param commandRouterClient The client to use for accessing the command router service.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    public ProtonBasedCommandRouterCommandConsumerFactoryImpl(
            final HonoConnection connection,
            final SendMessageSampler.Factory samplerFactory,
            final CommandRouterClient commandRouterClient) {
        super(connection, samplerFactory, false, false);
        this.commandRouterClient = Objects.requireNonNull(commandRouterClient);

        adapterInstanceId = getAdapterInstanceId(connection.getConfig().getName());
        adapterInstanceCommandHandler = new ProtonBasedAdapterInstanceCommandHandler(connection.getTracer(), adapterInstanceId);
    }

    private static String getAdapterInstanceId(final String adapterName) {
        // replace special characters so that the id can be used in a Kafka topic name
        final String prefix = Strings.isNullOrEmpty(adapterName) ? ""
                : adapterName.replaceAll("[^a-zA-Z0-9._-]", "") + "-";
        return prefix + UUID.randomUUID();
    }

    @Override
    public Future<Void> start() {
        return super.start()
                .onComplete(v -> {
                    connection.addReconnectListener(c -> recreateConsumer());
                    // trigger creation of adapter specific consumer link (with retry if failed)
                    recreateConsumer();
                });
    }

    @Override
    protected void onDisconnect() {
        adapterSpecificConsumer = null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final Future<CommandConsumer> createCommandConsumer(
            final String tenantId,
            final String deviceId,
            final Handler<CommandContext> commandHandler,
            final Duration lifespan,
            final SpanContext context) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(deviceId);
        Objects.requireNonNull(commandHandler);

        return doCreateCommandConsumer(tenantId, deviceId, null, commandHandler, lifespan, context);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final Future<CommandConsumer> createCommandConsumer(
            final String tenantId,
            final String deviceId,
            final String gatewayId,
            final Handler<CommandContext> commandHandler,
            final Duration lifespan,
            final SpanContext context) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(deviceId);
        Objects.requireNonNull(gatewayId);
        Objects.requireNonNull(commandHandler);

        return doCreateCommandConsumer(tenantId, deviceId, gatewayId, commandHandler, lifespan, context);
    }

    private Future<CommandConsumer> doCreateCommandConsumer(
            final String tenantId,
            final String deviceId,
            final String gatewayId,
            final Handler<CommandContext> commandHandler,
            final Duration lifespan,
            final SpanContext context) {
        // lifespan greater than what can be expressed in nanoseconds (i.e. 292 years) is considered unlimited, preventing ArithmeticExceptions down the road
        final Duration sanitizedLifespan = lifespan == null || lifespan.isNegative()
                || lifespan.getSeconds() > (Long.MAX_VALUE / 1000_000_000L) ? Duration.ofSeconds(-1) : lifespan;
        log.trace("create command consumer [tenant-id: {}, device-id: {}, gateway-id: {}]", tenantId, deviceId, gatewayId);
        return connection.executeOnContext(result -> {
            // register the command handler
            final CommandHandlerWrapper commandHandlerWrapper = new CommandHandlerWrapper(
                    tenantId,
                    deviceId,
                    gatewayId,
                    commandHandler);
            final CommandHandlerWrapper replacedHandler = commandHandlers.putCommandHandler(commandHandlerWrapper);
            if (replacedHandler != null) {
                // TODO find a way to provide a notification here so that potential resources associated with the replaced consumer can be freed (maybe add a commandHandlerOverwritten Handler param to createCommandConsumer())
            }
            // associate handler with this adapter instance
            final Instant lifespanStart = Instant.now();
            registerCommandConsumer(tenantId, deviceId, sanitizedLifespan, context)
                    .map(v -> {
                        return (CommandConsumer) new CommandConsumer() {
                            @Override
                            public Future<Void> close(final SpanContext spanContext) {
                                return removeCommandConsumer(commandHandlerWrapper, sanitizedLifespan,
                                        lifespanStart, spanContext);
                            }
                        };
                    })
                    .onComplete(result);
        });
    }

    private Future<Void> registerCommandConsumer(
            final String tenantId,
            final String deviceId,
            final Duration lifespan,
            final SpanContext context) {

        return commandRouterClient.registerCommandConsumer(tenantId, deviceId, adapterInstanceId, lifespan, context)
                .recover(thr -> {
                    log.info("error registering consumer with the command router service [tenant: {}, device: {}]", tenantId,
                            deviceId, thr);
                    // handler association failed - unregister the handler
                    commandHandlers.removeCommandHandler(tenantId, deviceId);
                    return Future.failedFuture(thr);
                });
    }

    private Future<Void> removeCommandConsumer(
            final CommandHandlerWrapper commandHandlerWrapper,
            final Duration lifespan,
            final Instant lifespanStart,
            final SpanContext onCloseSpanContext) {

        final String tenantId = commandHandlerWrapper.getTenantId();
        final String deviceId = commandHandlerWrapper.getDeviceId();

        log.trace("remove command consumer [tenant-id: {}, device-id: {}]", tenantId, deviceId);
        if (!commandHandlers.removeCommandHandler(commandHandlerWrapper)) {
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
        return commandRouterClient.unregisterCommandConsumer(
                    tenantId,
                    deviceId,
                    adapterInstanceId,
                    onCloseSpanContext)
                .recover(thr -> {
                    if (ServiceInvocationException.extractStatusCode(thr) == HttpURLConnection.HTTP_PRECON_FAILED) {
                        final boolean entryMayHaveExpired = !lifespan.isNegative() && Instant.now().isAfter(lifespanStart.plus(lifespan));
                        if (entryMayHaveExpired) {
                            log.trace("ignoring 412 error when unregistering consumer with the command router service; entry may have already expired [tenant: {}, device: {}]",
                                    tenantId, deviceId);
                            return Future.succeededFuture();
                        } else {
                            // entry wasn't actually removed and entry hasn't expired (yet);
                            // This case happens when 2 consecutive command subscription requests from the same device
                            // (with no intermittent disconnect/unsubscribe - possibly because of a broken connection in between)
                            // have reached *different* protocol adapter instances/verticles. Now calling 'unregisterCommandConsumer'
                            // on the 1st subscription fails because of the non-matching adapterInstanceId parameter.
                            // Throwing an explicit exception here will enable the protocol adapter to detect this case
                            // and skip sending an (incorrect) "disconnectedTtd" event message.
                            log.debug("consumer not unregistered - not matched or already removed [tenant: {}, device: {}]",
                                    tenantId, deviceId);
                            return Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_PRECON_FAILED,
                                    "no matching command consumer mapping found to be removed"));
                        }
                    } else {
                        log.info("error unregistering consumer with the command router service [tenant: {}, device: {}]", tenantId,
                                deviceId, thr);
                        return Future.failedFuture(thr);
                    }
                });
    }

    private Future<ProtonReceiver> createAdapterSpecificConsumer() {
        log.trace("creating new adapter instance command consumer");
        final String adapterInstanceConsumerAddress = CommandConstants.INTERNAL_COMMAND_ENDPOINT + "/"
                + adapterInstanceId;
        return connection.createReceiver(
                adapterInstanceConsumerAddress,
                ProtonQoS.AT_LEAST_ONCE,
                (delivery, msg) -> adapterInstanceCommandHandler.handleCommandMessage(msg, delivery, commandHandlers),
                connection.getConfig().getInitialCredits(),
                false, // no auto-accept
                sourceAddress -> { // remote close hook
                    log.debug("command receiver link closed remotely");
                    invokeRecreateConsumerWithDelay();
                }).map(receiver -> {
                    log.debug("successfully created adapter specific command consumer");
                    adapterSpecificConsumer = receiver;
                    return receiver;
                }).recover(t -> {
                    log.error("failed to create adapter specific command consumer", t);
                    return Future.failedFuture(t);
                });
    }

    private void recreateConsumer() {
        if (recreatingConsumer.compareAndSet(false, true)) {
            connection.isConnected(getDefaultConnectionCheckTimeout())
                    .compose(res -> {
                        // recreate adapter specific consumer
                        if (adapterSpecificConsumer == null || !adapterSpecificConsumer.isOpen()) {
                            log.debug("recreate adapter specific command consumer link");
                            return createAdapterSpecificConsumer();
                        }
                        return Future.succeededFuture();
                    }).onComplete(ar -> {
                        recreatingConsumer.set(false);
                        if (tryAgainRecreatingConsumer.compareAndSet(true, false) || ar.failed()) {
                            if (ar.succeeded()) {
                                // tryAgainRecreatingConsumers was set - try again immediately
                                recreateConsumer();
                            } else {
                                invokeRecreateConsumerWithDelay();
                            }
                        }
                    });
        } else {
            // if recreateConsumer() was triggered by a remote link closing, that might have occurred after that link was dealt with above;
            // therefore be sure recreateConsumer() gets called again once the current invocation has finished.
            log.debug("already recreating consumer");
            tryAgainRecreatingConsumer.set(true);
        }
    }

    private void invokeRecreateConsumerWithDelay() {
        connection.getVertx().setTimer(RECREATE_CONSUMER_DELAY, tid -> recreateConsumer());
    }

}
