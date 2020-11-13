/**
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
 */
package org.eclipse.hono.adapter.http.quarkus;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.inject.Inject;

import org.eclipse.hono.adapter.client.command.DeviceConnectionClient;
import org.eclipse.hono.adapter.client.registry.DeviceRegistrationClient;
import org.eclipse.hono.adapter.http.HttpAdapterMetrics;
import org.eclipse.hono.adapter.http.HttpProtocolAdapterProperties;
import org.eclipse.hono.adapter.http.impl.VertxBasedHttpProtocolAdapter;
import org.eclipse.hono.client.CommandTargetMapper;
import org.eclipse.hono.client.CommandTargetMapper.CommandTargetMapperContext;
import org.eclipse.hono.client.ProtocolAdapterCommandConsumerFactory;
import org.eclipse.hono.client.ProtocolAdapterCommandConsumerFactory.CommandHandlingAdapterInfoAccess;
import org.eclipse.hono.service.quarkus.AbstractProtocolAdapterApplication;
import org.eclipse.hono.util.RegistrationAssertion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opentracing.SpanContext;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;

/**
 * The Hono HTTP adapter main application class.
 */
@ApplicationScoped
public class Application extends AbstractProtocolAdapterApplication {

    private static final Logger LOG = LoggerFactory.getLogger(Application.class);

    @Inject
    HttpAdapterMetrics metrics;

    @Inject
    HttpProtocolAdapterProperties adapterProperties;

    void onStart(final @Observes StartupEvent ev) {
        LOG.info("deploying {} HTTP adapter instances ...", config.app.getMaxInstances());

        final CompletableFuture<Void> startup = new CompletableFuture<>();
        final Promise<String> deploymentTracker = Promise.promise();
        vertx.deployVerticle(
                () -> adapter(),
                new DeploymentOptions().setInstances(config.app.getMaxInstances()),
                deploymentTracker);
        deploymentTracker.future()
            .compose(s -> healthCheckServer.start())
            .onSuccess(ok -> startup.complete(null))
            .onFailure(t -> startup.completeExceptionally(t));
        startup.join();

    }

    void onStop(final @Observes ShutdownEvent ev) {
        LOG.info("shutting down HTTP adapter");
        final CompletableFuture<Void> shutdown = new CompletableFuture<>();
        healthCheckServer.stop()
            .onComplete(ok -> {
                vertx.close(attempt -> {
                    if (attempt.succeeded()) {
                        shutdown.complete(null);
                    } else {
                        shutdown.completeExceptionally(attempt.cause());
                    }
                });
            });
        shutdown.join();
    }

    private VertxBasedHttpProtocolAdapter adapter() {

        final DeviceRegistrationClient registrationClient = registrationClient();
        final DeviceConnectionClient deviceConnectionClient = deviceConnectionClient();
        final CommandTargetMapper commandTargetMapper = commandTargetMapper();

        commandTargetMapper.initialize(new CommandTargetMapperContext() {

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

                return deviceConnectionClient.getCommandHandlingAdapterInstances(
                        tenant, deviceId, viaGateways, context);
            }
        });

        final ProtocolAdapterCommandConsumerFactory commandConsumerFactory = commandConsumerFactory();
        commandConsumerFactory.initialize(commandTargetMapper, new CommandHandlingAdapterInfoAccess() {

            @Override
            public Future<Void> setCommandHandlingAdapterInstance(
                    final String tenant,
                    final String deviceId,
                    final String adapterInstanceId,
                    final Duration lifespan,
                    final SpanContext context) {
                return deviceConnectionClient.setCommandHandlingAdapterInstance(tenant, deviceId, adapterInstanceId, lifespan, context);
            }

            @Override
            public Future<Void> removeCommandHandlingAdapterInstance(
                    final String tenant,
                    final String deviceId,
                    final String adapterInstanceId,
                    final SpanContext context) {
                return deviceConnectionClient.removeCommandHandlingAdapterInstance(tenant, deviceId, adapterInstanceId, context);
            }
        });

        final VertxBasedHttpProtocolAdapter adapter = new VertxBasedHttpProtocolAdapter();
        adapter.setCommandConsumerFactory(commandConsumerFactory);
        adapter.setConfig(adapterProperties);
        adapter.setCredentialsClient(credentialsClient());
        adapter.setDeviceConnectionClient(deviceConnectionClient);
        adapter.setEventSender(downstreamSender());
        adapter.setHealthCheckServer(healthCheckServer);
        adapter.setMetrics(metrics);
        adapter.setRegistrationClient(registrationClient);
        adapter.setTelemetrySender(downstreamSender());
        adapter.setTenantClient(tenantClient());
        adapter.setTracer(tracer);
        adapter.setResourceLimitChecks(resourceLimitChecks);
        return adapter;
    }
}
