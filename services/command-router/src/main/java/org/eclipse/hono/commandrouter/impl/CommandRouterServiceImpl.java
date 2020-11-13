/*******************************************************************************
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.eclipse.hono.adapter.client.registry.DeviceRegistrationClient;
import org.eclipse.hono.adapter.client.util.ServiceClient;
import org.eclipse.hono.client.CommandTargetMapper;
import org.eclipse.hono.client.ConnectionLifecycle;
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.commandrouter.CommandConsumerFactory;
import org.eclipse.hono.commandrouter.CommandRouterServiceConfigProperties;
import org.eclipse.hono.deviceconnection.infinispan.client.DeviceConnectionInfo;
import org.eclipse.hono.service.HealthCheckProvider;
import org.eclipse.hono.service.commandrouter.CommandRouterResult;
import org.eclipse.hono.service.commandrouter.CommandRouterService;
import org.eclipse.hono.tracing.TracingHelper;
import org.eclipse.hono.util.ConfigurationSupportingVerticle;
import org.eclipse.hono.util.Lifecycle;
import org.eclipse.hono.util.RegistrationAssertion;
import org.eclipse.hono.util.RegistrationConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.noop.NoopTracerFactory;
import io.opentracing.tag.Tags;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.healthchecks.HealthCheckHandler;
import io.vertx.ext.healthchecks.Status;

/**
 * An implementation of Hono's <em>Command Router</em> API.
 */
public class CommandRouterServiceImpl extends ConfigurationSupportingVerticle<CommandRouterServiceConfigProperties>
        implements CommandRouterService, HealthCheckProvider {

    private static final Logger LOG = LoggerFactory.getLogger(CommandRouterServiceImpl.class);

    private DeviceRegistrationClient registrationClient;
    private DeviceConnectionInfo deviceConnectionInfo;
    private CommandConsumerFactory commandConsumerFactory;
    private CommandTargetMapper commandTargetMapper;
    private Tracer tracer = NoopTracerFactory.create();

    @Autowired
    @Override
    public void setConfig(final CommandRouterServiceConfigProperties configuration) {
        setSpecificConfig(configuration);
    }

    /**
     * Sets the OpenTracing {@code Tracer} to use for tracking the processing
     * of messages published by devices across Hono's components.
     * <p>
     * If not set explicitly, the {@code NoopTracer} from OpenTracing will
     * be used.
     *
     * @param opentracingTracer The tracer.
     */
    @Autowired(required = false)
    public final void setTracer(final Tracer opentracingTracer) {
        LOG.info("using OpenTracing Tracer implementation [{}]", opentracingTracer.getClass().getName());
        this.tracer = Objects.requireNonNull(opentracingTracer);
    }

    /**
     * Sets the client for accessing device connection data.
     *
     * @param deviceConnectionInfo The client object.
     * @throws NullPointerException if deviceConnectionInfo is {@code null}.
     */
    @Autowired
    public final void setDeviceConnectionInfo(final DeviceConnectionInfo deviceConnectionInfo) {
        this.deviceConnectionInfo = Objects.requireNonNull(deviceConnectionInfo);
    }

    /**
     * Sets the client to use for accessing the Device Registration service.
     *
     * @param client The client.
     * @throws NullPointerException if the client is {@code null}.
     */
    @Qualifier(RegistrationConstants.REGISTRATION_ENDPOINT)
    @Autowired
    public final void setRegistrationClient(final DeviceRegistrationClient client) {
        this.registrationClient = Objects.requireNonNull(client);
    }

    /**
     * Sets the factory to use for creating clients to receive commands.
     *
     * @param factory The factory.
     * @throws NullPointerException if factory is {@code null}.
     */
    @Autowired
    public final void setCommandConsumerFactory(final CommandConsumerFactory factory) {
        this.commandConsumerFactory = Objects.requireNonNull(factory);
    }

    /**
     * Sets the component for mapping an incoming command to the gateway (if applicable)
     * and protocol adapter instance that can handle it.
     *
     * @param commandTargetMapper The mapper component.
     * @throws NullPointerException if commandTargetMapper is {@code null}.
     */
    @Autowired
    public final void setCommandTargetMapper(final CommandTargetMapper commandTargetMapper) {
        this.commandTargetMapper = Objects.requireNonNull(commandTargetMapper);
    }

    @Override
    public void start(final Promise<Void> startPromise) throws Exception {

        if (registrationClient == null) {
            startPromise.fail(new IllegalStateException("Device Registration client must be set"));
        } else if (deviceConnectionInfo == null) {
            startPromise.fail(new IllegalStateException("Device Connection info client must be set"));
        } else {
            startServiceClient(registrationClient, "Device Registration service");
            if (deviceConnectionInfo instanceof Lifecycle) {
                startServiceClient((Lifecycle) deviceConnectionInfo, "Device Connection info");
            }
            connectToService(commandConsumerFactory, "Command & Control");

            // initialize components dependent on the above clientFactories
            commandTargetMapper.initialize(new CommandTargetMapper.CommandTargetMapperContext() {

                @Override
                public Future<List<String>> getViaGateways(
                        final String tenant,
                        final String deviceId,
                        final SpanContext context) {

                    Objects.requireNonNull(tenant);
                    Objects.requireNonNull(deviceId);

                    return registrationClient.assertRegistration(tenant, deviceId, null, context)
                            .map(RegistrationAssertion::getAuthorizedGateways);
                }

                @Override
                public Future<JsonObject> getCommandHandlingAdapterInstances(
                        final String tenant,
                        final String deviceId,
                        final List<String> viaGateways,
                        final SpanContext context) {

                    Objects.requireNonNull(tenant);
                    Objects.requireNonNull(deviceId);
                    Objects.requireNonNull(viaGateways);

                    final Span span = TracingHelper.buildChildSpan(tracer, context, "getCommandHandlingAdapterInstances",
                                    getClass().getSimpleName())
                            .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_CLIENT)
                            .start();
                    return deviceConnectionInfo
                            .getCommandHandlingAdapterInstances(tenant, deviceId, new HashSet<>(viaGateways), span);
                }
            });
            commandConsumerFactory.initialize(commandTargetMapper);

            startPromise.complete();
        }
    }

    @Override
    public void stop(final Promise<Void> stopPromise) throws Exception {
        LOG.info("stopping command router");

        @SuppressWarnings("rawtypes")
        final List<Future> results = new ArrayList<>();
        results.add(registrationClient.stop());
        if (deviceConnectionInfo instanceof Lifecycle) {
            results.add(((Lifecycle) deviceConnectionInfo).stop());
        }
        results.add(disconnectFromService(commandConsumerFactory));

        CompositeFuture.all(results)
                .recover(t -> {
                    LOG.info("error while stopping command router", t);
                    return Future.failedFuture(t);
                })
                .map(ok -> {
                    LOG.info("successfully stopped command router");
                    return (Void) null;
                })
                .onComplete(stopPromise);
    }

    /**
     * Disconnects from a Hono Service component.
     *
     * @param connection  The connection.
     * @return A future indicating the outcome of the operation.
     * @throws NullPointerException if connection is {@code null}.
     */
    protected final Future<Void> disconnectFromService(final ConnectionLifecycle<?> connection) {
        Objects.requireNonNull(connection);
        final Promise<Void> disconnectTracker = Promise.promise();
        connection.disconnect(disconnectTracker);
        return disconnectTracker.future();
    }

    /**
     * Starts a service client.
     * <p>
     * This method invokes the given client's {@link Lifecycle#start()} method.
     *
     * @param serviceClient The client to start.
     * @param serviceName The name of the service that the client is for (used for logging).
     * @return A future indicating the outcome of starting the client.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    protected final Future<Void> startServiceClient(final Lifecycle serviceClient, final String serviceName) {

        Objects.requireNonNull(serviceClient);
        Objects.requireNonNull(serviceName);

        return serviceClient.start().map(c -> {
            LOG.info("{} client [{}] successfully connected", serviceName, serviceClient);
            return c;
        }).recover(t -> {
            LOG.warn("{} client [{}] failed to connect", serviceName, serviceClient, t);
            return Future.failedFuture(t);
        });
    }

    /**
     * Establishes a connection to a Hono Service component.
     *
     * @param factory The client factory for the service that is to be connected.
     * @param serviceName The name of the service that is to be connected (used for logging).
     * @return A future that will succeed once the connection has been established. The future will fail if the
     *         connection cannot be established.
     * @throws NullPointerException if serviceName is {@code null}.
     * @throws IllegalArgumentException if factory is {@code null}.
     * @param <C> The type of connection that the factory uses.
     */
    protected final <C> Future<C> connectToService(final ConnectionLifecycle<C> factory, final String serviceName) {
        Objects.requireNonNull(factory);
        factory.addDisconnectListener(c -> LOG.info("lost connection to {}", serviceName));
        factory.addReconnectListener(c -> LOG.info("connection to {} re-established", serviceName));
        return factory.connect().map(c -> {
            LOG.info("connected to {}", serviceName);
            return c;
        }).recover(t -> {
            LOG.warn("failed to connect to {}", serviceName, t);
            return Future.failedFuture(t);
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

        return commandConsumerFactory.createCommandConsumer(tenantId, deviceId, adapterInstanceId, lifespan, span.context())
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

        return commandConsumerFactory.removeCommandConsumer(tenantId, deviceId, adapterInstanceId, span.context())
                .compose(v -> deviceConnectionInfo
                        .removeCommandHandlingAdapterInstance(tenantId, deviceId, adapterInstanceId, span)
                        .recover(thr -> {
                            if (ServiceInvocationException.extractStatusCode(thr) != HttpURLConnection.HTTP_PRECON_FAILED) {
                                LOG.info("error removing command handling adapter instance [tenant: {}, device: {}]", tenantId, deviceId, thr);
                            }
                            return Future.failedFuture(thr);
                        }))
                .map(v -> CommandRouterResult.from(HttpURLConnection.HTTP_NO_CONTENT))
                .otherwise(t -> CommandRouterResult.from(ServiceInvocationException.extractStatusCode(t)));
    }

    @Override
    public void registerReadinessChecks(final HealthCheckHandler handler) {
        Optional.ofNullable(commandConsumerFactory)
                .ifPresent(factory -> {
                    handler.register("connected-to-command-endpoint", 2000L, status -> {
                        factory.isConnected()
                                .onSuccess(connected -> status.tryComplete(Status.OK()))
                                .onFailure(t -> status.tryComplete(Status.KO()));
                    });
                });
        if (registrationClient instanceof ServiceClient) {
            ((ServiceClient) registrationClient).registerReadinessChecks(handler);
        }
        if (deviceConnectionInfo instanceof ServiceClient) {
            ((ServiceClient) deviceConnectionInfo).registerReadinessChecks(handler);
        }
    }

    @Override
    public void registerLivenessChecks(final HealthCheckHandler handler) {
        registerEventLoopBlockedCheck(handler);
        if (registrationClient instanceof ServiceClient) {
            ((ServiceClient) registrationClient).registerLivenessChecks(handler);
        }
        if (deviceConnectionInfo instanceof ServiceClient) {
            ((ServiceClient) deviceConnectionInfo).registerLivenessChecks(handler);
        }
    }

    /**
     * Registers a health check which tries to run an action on the protocol adapter context.
     * <p>
     * If the protocol adapter vert.x event loop is blocked, the health check procedure will not complete
     * with OK status within the defined timeout.
     *
     * @param handler The health check handler to register the checks with.
     */
    protected void registerEventLoopBlockedCheck(final HealthCheckHandler handler) {

        handler.register(
                "event-loop-blocked-check",
                getConfig().getEventLoopBlockedCheckTimeout(),
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
