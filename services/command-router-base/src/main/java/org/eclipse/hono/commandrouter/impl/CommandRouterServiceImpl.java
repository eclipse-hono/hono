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

package org.eclipse.hono.commandrouter.impl;

import java.net.HttpURLConnection;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.eclipse.hono.client.ServerErrorException;
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.client.registry.DeviceRegistrationClient;
import org.eclipse.hono.client.registry.TenantClient;
import org.eclipse.hono.client.util.MessagingClient;
import org.eclipse.hono.client.util.ServiceClient;
import org.eclipse.hono.commandrouter.CommandConsumerFactory;
import org.eclipse.hono.commandrouter.CommandRouterServiceConfigProperties;
import org.eclipse.hono.deviceconnection.infinispan.client.DeviceConnectionInfo;
import org.eclipse.hono.service.HealthCheckProvider;
import org.eclipse.hono.service.commandrouter.CommandRouterResult;
import org.eclipse.hono.service.commandrouter.CommandRouterService;
import org.eclipse.hono.tracing.TracingHelper;
import org.eclipse.hono.util.Lifecycle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opentracing.References;
import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.log.Fields;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.impl.ConcurrentHashSet;
import io.vertx.ext.healthchecks.HealthCheckHandler;
import io.vertx.ext.healthchecks.Status;

/**
 * An implementation of Hono's <em>Command Router</em> API.
 */
public class CommandRouterServiceImpl implements CommandRouterService, HealthCheckProvider, Lifecycle {

    private static final Logger LOG = LoggerFactory.getLogger(CommandRouterServiceImpl.class);

    private final CommandRouterServiceConfigProperties config;
    private final DeviceRegistrationClient registrationClient;
    private final TenantClient tenantClient;
    private final DeviceConnectionInfo deviceConnectionInfo;
    private final MessagingClient<CommandConsumerFactory> commandConsumerFactories;
    private final Tracer tracer;
    private final Deque<String> tenantsToEnable = new LinkedList<>();

    /**
     * Vert.x context that this service has been started in.
     */
    private Context context;


    /**
     * Creates a new CommandRouterServiceImpl.
     *
     * @param config The command router service configuration.
     * @param registrationClient The device registration client.
     * @param tenantClient The tenant client.
     * @param deviceConnectionInfo The client for accessing device connection data.
     * @param commandConsumerFactories The factories to use for creating clients to receive commands.
     * @param tracer The Open Tracing tracer to use for tracking processing of requests.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    public CommandRouterServiceImpl(
            final CommandRouterServiceConfigProperties config,
            final DeviceRegistrationClient registrationClient,
            final TenantClient tenantClient,
            final DeviceConnectionInfo deviceConnectionInfo,
            final MessagingClient<CommandConsumerFactory> commandConsumerFactories,
            final Tracer tracer) {

        this.config = Objects.requireNonNull(config);
        this.registrationClient = Objects.requireNonNull(registrationClient);
        this.tenantClient = Objects.requireNonNull(tenantClient);
        this.deviceConnectionInfo = Objects.requireNonNull(deviceConnectionInfo);
        this.commandConsumerFactories = Objects.requireNonNull(commandConsumerFactories);
        this.tracer = Objects.requireNonNull(tracer);
    }

    /**
     * Sets the vert.x context to run on.
     * <p>
     * If not set explicitly, the context is determined during startup.
     *
     * @param context The context.
     */
    void setContext(final Context context) {
        this.context = Objects.requireNonNull(context);
    }

    @Override
    public Future<Void> start() {
        if (context == null) {
            context = Vertx.currentContext();
            if (context == null) {
                return Future.failedFuture(new IllegalStateException("Service must be started in a Vert.x context"));
            }
        }
        if (!commandConsumerFactories.containsImplementations()) {
            return Future.failedFuture("no command consumer factories set");
        }

        registrationClient.start();
        tenantClient.start();
        if (deviceConnectionInfo instanceof Lifecycle) {
            ((Lifecycle) deviceConnectionInfo).start();
        }
        commandConsumerFactories.start();

        return Future.succeededFuture();
    }

    @Override
    public Future<Void> stop() {
        LOG.info("stopping command router");

        @SuppressWarnings("rawtypes")
        final List<Future> results = new ArrayList<>();
        results.add(registrationClient.stop());
        results.add(tenantClient.stop());
        if (deviceConnectionInfo instanceof Lifecycle) {
            results.add(((Lifecycle) deviceConnectionInfo).stop());
        }
        results.add(commandConsumerFactories.stop());

        return CompositeFuture.all(results)
                .recover(t -> {
                    LOG.info("error while stopping command router", t);
                    return Future.failedFuture(t);
                })
                .map(ok -> {
                    LOG.info("successfully stopped command router");
                    return (Void) null;
                });
    }

    @Override
    public Future<CommandRouterResult> setLastKnownGatewayForDevice(final String tenantId, final String deviceId,
            final String gatewayId, final Span span) {

        return deviceConnectionInfo.setLastKnownGatewayForDevice(tenantId, deviceId, gatewayId, span)
                .map(ok -> CommandRouterResult.from(HttpURLConnection.HTTP_NO_CONTENT));
    }

    @Override
    public Future<CommandRouterResult> registerCommandConsumer(final String tenantId,
            final String deviceId, final String adapterInstanceId, final Duration lifespan,
            final Span span) {

        return tenantClient.get(tenantId, span.context())
                .compose(tenantObject -> commandConsumerFactories.getClient(tenantObject)
                        .createCommandConsumer(tenantId, span.context()))
                .compose(v -> deviceConnectionInfo
                        .setCommandHandlingAdapterInstance(tenantId, deviceId, adapterInstanceId, getSanitizedLifespan(lifespan), span)
                        .recover(thr -> {
                            LOG.info("error setting command handling adapter instance [tenant: {}, device: {}]", tenantId, deviceId, thr);
                            return Future.failedFuture(thr);
                        }))
                .map(v -> CommandRouterResult.from(HttpURLConnection.HTTP_NO_CONTENT))
                .otherwise(t -> CommandRouterResult.from(ServiceInvocationException.extractStatusCode(t)));
    }

    private Duration getSanitizedLifespan(final Duration lifespan) {
        // lifespan greater than what can be expressed in nanoseconds (i.e. 292 years) is considered unlimited, preventing ArithmeticExceptions down the road
        return lifespan == null || lifespan.isNegative()
                || lifespan.getSeconds() > (Long.MAX_VALUE / 1000_000_000L) ? Duration.ofSeconds(-1) : lifespan;
    }

    @Override
    public Future<CommandRouterResult> unregisterCommandConsumer(final String tenantId, final String deviceId,
            final String adapterInstanceId, final Span span) {

        return deviceConnectionInfo
                .removeCommandHandlingAdapterInstance(tenantId, deviceId, adapterInstanceId, span)
                .recover(thr -> {
                    if (ServiceInvocationException.extractStatusCode(thr) != HttpURLConnection.HTTP_PRECON_FAILED) {
                        LOG.info("error removing command handling adapter instance [tenant: {}, device: {}]", tenantId, deviceId, thr);
                    }
                    return Future.failedFuture(thr);
                })
                .map(v -> CommandRouterResult.from(HttpURLConnection.HTTP_NO_CONTENT))
                .otherwise(t -> CommandRouterResult.from(ServiceInvocationException.extractStatusCode(t)));
    }

    @Override
    public Future<CommandRouterResult> enableCommandRouting(final List<String> tenantIds, final Span span) {
        Objects.requireNonNull(tenantIds);
        final boolean isProcessingRequired = tenantsToEnable.isEmpty();
        tenantsToEnable.addAll(tenantIds);
        if (isProcessingRequired) {
            final Span processingSpan = tracer.buildSpan("re-enable command routing for tenants")
                    .addReference(References.FOLLOWS_FROM, span.context())
                    .start();
            processTenantQueue(new ConcurrentHashSet<>(), processingSpan);
        }
        return Future.succeededFuture(CommandRouterResult.from(HttpURLConnection.HTTP_NO_CONTENT));
    }

    private void processTenantQueue(final Set<String> processedTenants, final Span parentSpan) {
        final String tenantId = tenantsToEnable.poll();
        if (tenantId == null) {
            parentSpan.finish();
            return;
        }
        context.runOnContext(go -> {
            if (processedTenants.contains(tenantId)) {
                parentSpan.log(Map.of(
                        Fields.MESSAGE, "skipping tenant, already processed ...",
                        TracingHelper.TAG_TENANT_ID.getKey(), tenantId));
            } else {
                final Span span = tracer.buildSpan("re-enable command routing for tenant")
                        .addReference(References.CHILD_OF, parentSpan.context())
                        .withTag(TracingHelper.TAG_TENANT_ID, tenantId)
                        .start();
                tenantClient.get(tenantId, span.context())
                    .map(tenantObject -> commandConsumerFactories.getClient(tenantObject))
                    .map(factory -> factory.createCommandConsumer(tenantId, span.context()))
                    .onSuccess(ok -> {
                        span.log("successfully created command consumer");
                        processedTenants.add(tenantId);
                    })
                    .onFailure(t -> {
                        TracingHelper.logError(span, "failed to create command consumer", t);
                        if (t instanceof ServerErrorException) {
                            // add to end of queue in order to retry at a later time
                            span.log("marking tenant for later re-try to create command consumer");
                            tenantsToEnable.add(tenantId);
                        }
                    })
                    .onComplete(r -> {
                        span.finish();
                    });
            }
            processTenantQueue(processedTenants, parentSpan);
        });
    }

    @Override
    public void registerReadinessChecks(final HealthCheckHandler handler) {
        if (registrationClient instanceof ServiceClient) {
            ((ServiceClient) registrationClient).registerReadinessChecks(handler);
        }
        if (tenantClient instanceof ServiceClient) {
            ((ServiceClient) tenantClient).registerReadinessChecks(handler);
        }
        if (deviceConnectionInfo instanceof ServiceClient) {
            ((ServiceClient) deviceConnectionInfo).registerReadinessChecks(handler);
        }
        commandConsumerFactories.registerReadinessChecks(handler);
    }

    @Override
    public void registerLivenessChecks(final HealthCheckHandler handler) {
        registerEventLoopBlockedCheck(handler);
        if (registrationClient instanceof ServiceClient) {
            ((ServiceClient) registrationClient).registerLivenessChecks(handler);
        }
        if (tenantClient instanceof ServiceClient) {
            ((ServiceClient) tenantClient).registerLivenessChecks(handler);
        }
        if (deviceConnectionInfo instanceof ServiceClient) {
            ((ServiceClient) deviceConnectionInfo).registerLivenessChecks(handler);
        }
        commandConsumerFactories.registerLivenessChecks(handler);
    }

    /**
     * Registers a health check which tries to run an action on the context that this service was started in.
     * <p>
     * If the vert.x event loop of that context is blocked, the health check procedure will not complete
     * with OK status within the defined timeout.
     *
     * @param handler The health check handler to register the checks with.
     */
    protected void registerEventLoopBlockedCheck(final HealthCheckHandler handler) {

        handler.register(
                "event-loop-blocked-check",
                config.getEventLoopBlockedCheckTimeout(),
                procedure -> {
                    final Context currentContext = Vertx.currentContext();

                    if (currentContext != context) {
                        context.runOnContext(action -> {
                            procedure.tryComplete(Status.OK());
                        });
                    } else {
                        LOG.debug("Command router - HealthCheck Server context match. Assume protocol adapter is alive.");
                        procedure.tryComplete(Status.OK());
                    }
                });
    }
}
