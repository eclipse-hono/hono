/*******************************************************************************
 * Copyright (c) 2020, 2022 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.client.command;

import java.net.HttpURLConnection;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.client.amqp.connection.ConnectionLifecycle;
import org.eclipse.hono.client.util.ServiceClient;
import org.eclipse.hono.util.CommandConstants;
import org.eclipse.hono.util.Lifecycle;
import org.eclipse.hono.util.TenantConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opentracing.SpanContext;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.ext.healthchecks.HealthCheckHandler;

/**
 * A factory for creating consumers of command messages.
 * <p>
 * This implementation uses the Command Router service and receives commands forwarded by the Command Router
 * on the internal command endpoint.
 */
public class CommandRouterCommandConsumerFactory implements CommandConsumerFactory, ServiceClient {

    private static final Logger LOG = LoggerFactory.getLogger(CommandRouterCommandConsumerFactory.class);

    private static final AtomicInteger ADAPTER_INSTANCE_ID_COUNTER = new AtomicInteger();

    /**
     * Identifier that has to be unique to this factory instance.
     * Will be used to represent the protocol adapter (verticle) instance that this factory instance is used in,
     * when registering command handlers with the command router service client.
     */
    private final String adapterInstanceId;
    private final CommandHandlers commandHandlers = new CommandHandlers();
    private final CommandRouterClient commandRouterClient;
    private final List<InternalCommandConsumer> internalCommandConsumers = new ArrayList<>();

    private int maxTenantIdsPerRequest = 100;

    /**
     * Creates a new factory.
     *
     * @param commandRouterClient The client to use for accessing the command router service.
     * @param adapterName The name of the protocol adapter.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    public CommandRouterCommandConsumerFactory(final CommandRouterClient commandRouterClient, final String adapterName) {
        this.commandRouterClient = Objects.requireNonNull(commandRouterClient);
        Objects.requireNonNull(adapterName);

        this.adapterInstanceId = CommandConstants.getNewAdapterInstanceId(adapterName, ADAPTER_INSTANCE_ID_COUNTER.getAndIncrement());
        if (commandRouterClient instanceof ConnectionLifecycle<?>) {
            ((ConnectionLifecycle<?>) commandRouterClient).addReconnectListener(con -> reenableCommandRouting());
        }
    }

    void setMaxTenantIdsPerRequest(final int count) {
        this.maxTenantIdsPerRequest = count;
    }

    private void reenableCommandRouting() {
        final List<String> tenantIds = commandHandlers.getCommandHandlers().stream()
                .map(CommandHandlerWrapper::getTenantId)
                .distinct()
                .collect(Collectors.toList());

        int idx = 0;
        // re-enable routing of commands in chunks of tenant IDs
        while (idx < tenantIds.size()) {
            final int from = idx;
            final int to = from + Math.min(maxTenantIdsPerRequest, tenantIds.size() - idx);
            final List<String> chunk = tenantIds.subList(from, to);
            commandRouterClient.enableCommandRouting(chunk, null);
            idx = to;
        }
    }

    /**
     * Registers the command consumer receiving commands on the internal command endpoint of the protocol adapter.
     * That is the endpoint that the Command Router forwards received commands to.
     * <p>
     * Note that this method needs to be called before invoking {@link #start()}. The {@link #start()} and
     * {@link #stop()} methods of this factory will invoke the corresponding methods on the internal command consumer.
     *
     * @param internalCommandConsumerSupplier Function that returns the consumer. Parameters are the adapter instance
     *            id that identifies the internal command endpoint and the command handlers to choose from when handling
     *            a received command.
     */
    public void registerInternalCommandConsumer(
            final BiFunction<String, CommandHandlers, InternalCommandConsumer> internalCommandConsumerSupplier) {
        final InternalCommandConsumer consumer = internalCommandConsumerSupplier.apply(adapterInstanceId,
                commandHandlers);
        LOG.info("register internal command consumer {}", consumer.getClass().getSimpleName());
        internalCommandConsumers.add(consumer);
    }

    /**
     * Starts the registered internal command consumer(s) so that commands forwarded by the Command Router
     * are received.
     * <p>
     * A failed future is returned if no internal command consumer has been registered yet.
     *
     * @return A future indicating the outcome of the startup process.
     */
    @Override
    public Future<Void> start() {
        @SuppressWarnings("rawtypes")
        final List<Future> futures = internalCommandConsumers.stream()
                .map(Lifecycle::start)
                .collect(Collectors.toList());
        if (futures.isEmpty()) {
            return Future.failedFuture("no command consumer registered");
        }
        return CompositeFuture.all(futures).mapEmpty();
    }

    @Override
    public Future<Void> stop() {
        @SuppressWarnings("rawtypes")
        final List<Future> futures = internalCommandConsumers.stream()
                .map(Lifecycle::stop)
                .collect(Collectors.toList());
        return CompositeFuture.all(futures).mapEmpty();
    }

    @Override
    public void registerReadinessChecks(final HealthCheckHandler readinessHandler) {
        internalCommandConsumers.forEach(consumer -> consumer.registerReadinessChecks(readinessHandler));
    }

    @Override public void registerLivenessChecks(final HealthCheckHandler livenessHandler) {
        // no liveness checks to be added
    }

    @Override
    public final Future<CommandConsumer> createCommandConsumer(
            final String tenantId,
            final String deviceId,
            final Function<CommandContext, Future<Void>> commandHandler,
            final Duration lifespan,
            final SpanContext context) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(deviceId);
        Objects.requireNonNull(commandHandler);

        return doCreateCommandConsumer(tenantId, deviceId, null, commandHandler, lifespan, context);
    }

    @Override
    public final Future<CommandConsumer> createCommandConsumer(
            final String tenantId,
            final String deviceId,
            final String gatewayId,
            final Function<CommandContext, Future<Void>> commandHandler,
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
            final Function<CommandContext, Future<Void>> commandHandler,
            final Duration lifespan,
            final SpanContext context) {
        // lifespan greater than what can be expressed in nanoseconds (i.e. 292 years) is considered unlimited, preventing ArithmeticExceptions down the road
        final Duration sanitizedLifespan = lifespan == null || lifespan.isNegative()
                || lifespan.getSeconds() > (Long.MAX_VALUE / 1000_000_000L) ? Duration.ofSeconds(-1) : lifespan;
        LOG.trace("create command consumer [tenant-id: {}, device-id: {}, gateway-id: {}]", tenantId, deviceId, gatewayId);

        // register the command handler
        // for short-lived command consumers, let the consumer creation span context be used as reference in the command span
        final SpanContext consumerCreationContextToUse = !sanitizedLifespan.isNegative()
                && sanitizedLifespan.toSeconds() <= TenantConstants.DEFAULT_MAX_TTD ? context : null;
        final CommandHandlerWrapper commandHandlerWrapper = new CommandHandlerWrapper(tenantId, deviceId, gatewayId,
                commandHandler, Vertx.currentContext(), consumerCreationContextToUse);
        commandHandlers.putCommandHandler(commandHandlerWrapper);
        final Instant lifespanStart = Instant.now();

        return commandRouterClient
                .registerCommandConsumer(tenantId, deviceId, adapterInstanceId, sanitizedLifespan, context)
                .onFailure(thr -> {
                    LOG.info("error registering consumer with the command router service [tenant: {}, device: {}]",
                            tenantId, deviceId, thr);
                    // handler association failed - unregister the handler
                    commandHandlers.removeCommandHandler(tenantId, deviceId);
                })
                .map(v -> {
                    return new CommandConsumer() {
                        @Override
                        public Future<Void> close(final SpanContext spanContext) {
                            return removeCommandConsumer(commandHandlerWrapper, sanitizedLifespan,
                                    lifespanStart, spanContext);
                        }
                    };
                });
    }

    private Future<Void> removeCommandConsumer(
            final CommandHandlerWrapper commandHandlerWrapper,
            final Duration lifespan,
            final Instant lifespanStart,
            final SpanContext onCloseSpanContext) {

        final String tenantId = commandHandlerWrapper.getTenantId();
        final String deviceId = commandHandlerWrapper.getDeviceId();

        LOG.trace("remove command consumer [tenant-id: {}, device-id: {}]", tenantId, deviceId);
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
            LOG.debug("command consumer not removed - handler already replaced or removed [tenant: {}, device: {}]",
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
                            LOG.trace("ignoring 412 error when unregistering consumer with the command router service; entry may have already expired [tenant: {}, device: {}]",
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
                            LOG.debug("consumer not unregistered - not matched or already removed [tenant: {}, device: {}]",
                                    tenantId, deviceId);
                            return Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_PRECON_FAILED,
                                    "no matching command consumer mapping found to be removed"));
                        }
                    } else {
                        LOG.info("error unregistering consumer with the command router service [tenant: {}, device: {}]", tenantId,
                                deviceId, thr);
                        return Future.failedFuture(thr);
                    }
                });
    }
}
