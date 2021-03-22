/**
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.hono.commandrouter.quarkus;

import java.util.concurrent.CompletableFuture;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.inject.Inject;

import org.eclipse.hono.adapter.client.registry.DeviceRegistrationClient;
import org.eclipse.hono.adapter.client.registry.amqp.ProtonBasedDeviceRegistrationClient;
import org.eclipse.hono.client.HonoConnection;
import org.eclipse.hono.client.SendMessageSampler;
import org.eclipse.hono.client.kafka.KafkaProducerConfigProperties;
import org.eclipse.hono.client.kafka.KafkaProducerFactory;
import org.eclipse.hono.client.kafka.consumer.KafkaConsumerConfigProperties;
import org.eclipse.hono.client.quarkus.ClientConfigProperties;
import org.eclipse.hono.client.quarkus.RequestResponseClientConfigProperties;
import org.eclipse.hono.commandrouter.CommandConsumerFactory;
import org.eclipse.hono.commandrouter.CommandRouterAmqpServer;
import org.eclipse.hono.commandrouter.CommandTargetMapper;
import org.eclipse.hono.commandrouter.impl.CommandRouterServiceImpl;
import org.eclipse.hono.commandrouter.impl.amqp.ProtonBasedCommandConsumerFactoryImpl;
import org.eclipse.hono.commandrouter.impl.kafka.KafkaBasedCommandConsumerFactoryImpl;
import org.eclipse.hono.config.quarkus.ApplicationConfigProperties;
import org.eclipse.hono.config.quarkus.ServiceConfigProperties;
import org.eclipse.hono.deviceconnection.infinispan.client.DeviceConnectionInfo;
import org.eclipse.hono.service.HealthCheckProvider;
import org.eclipse.hono.service.HealthCheckServer;
import org.eclipse.hono.service.amqp.AmqpEndpoint;
import org.eclipse.hono.service.auth.AuthenticationService;
import org.eclipse.hono.service.cache.Caches;
import org.eclipse.hono.service.commandrouter.CommandRouterService;
import org.eclipse.hono.service.commandrouter.DelegatingCommandRouterAmqpEndpoint;
import org.eclipse.hono.service.metric.MetricsTags;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.RegistrationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.benmanes.caffeine.cache.Cache;

import io.micrometer.core.instrument.MeterRegistry;
import io.opentracing.Tracer;
import io.quarkus.arc.config.ConfigPrefix;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import io.vertx.core.CompositeFuture;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Promise;
import io.vertx.core.Verticle;
import io.vertx.core.Vertx;
import io.vertx.core.impl.cpu.CpuCoreSensor;
import io.vertx.ext.healthchecks.HealthCheckHandler;
import io.vertx.proton.sasl.ProtonSaslAuthenticatorFactory;

/**
 * The Quarkus based Command Router main application class.
 */
@ApplicationScoped
public class Application {

    private static final String COMPONENT_NAME = "Hono Command Router";
    private static final Logger LOG = LoggerFactory.getLogger(Application.class);

    @Inject
    Vertx vertx;

    @Inject
    Tracer tracer;

    @Inject
    ApplicationConfigProperties appConfig;

    @ConfigPrefix("hono.commandRouter.amqp")
    ServiceConfigProperties amqpServerProperties;

    @Inject
    CommandRouterServiceConfigProperties serviceConfig;

    @ConfigPrefix("hono.command")
    ClientConfigProperties commandConsumerFactoryConfig;

    @ConfigPrefix("hono.registration")
    RequestResponseClientConfigProperties deviceRegistrationClientConfig;

    @Inject
    KafkaProducerConfigProperties kafkaProducerConfig;

    @Inject
    KafkaConsumerConfigProperties kafkaConsumerConfig;

    @Inject
    DeviceConnectionInfo deviceConnectionInfo;

    @Inject
    HealthCheckServer healthCheckServer;

    @Inject
    MeterRegistry meterRegistry;

    @Inject
    ProtonSaslAuthenticatorFactory saslAuthenticatorFactory;

    @Inject
    AuthenticationService authenticationService;

    private Cache<Object, RegistrationResult> registrationResponseCache;

    String getComponentName() {
        return COMPONENT_NAME;
    }

    void onStart(final @Observes StartupEvent ev) {

        if (!(authenticationService instanceof Verticle)) {
            throw new IllegalStateException("Authentication service must be a vert.x Verticle");
        }

        logJvmDetails();

        LOG.info("adding common tags to meter registry");
        meterRegistry.config().commonTags(MetricsTags.forService(Constants.SERVICE_NAME_COMMAND_ROUTER));

        LOG.info("deploying {} {} instances ...", appConfig.getMaxInstances(), getComponentName());

        final CompletableFuture<Void> startup = new CompletableFuture<>();

        // deploy authentication service (once only)
        final Promise<String> authServiceDeploymentTracker = Promise.promise();
        vertx.deployVerticle((Verticle) authenticationService, authServiceDeploymentTracker);

        // deploy AMQP 1.0 server
        final Promise<String> amqpServerDeploymentTracker = Promise.promise();
        vertx.deployVerticle(
                () -> amqpServer(),
                new DeploymentOptions().setInstances(appConfig.getMaxInstances()),
                amqpServerDeploymentTracker);

        CompositeFuture.all(authServiceDeploymentTracker.future(), amqpServerDeploymentTracker.future())
            .compose(s -> healthCheckServer.start())
            .onSuccess(ok -> startup.complete(null))
            .onFailure(t -> startup.completeExceptionally(t));
        startup.join();
    }

    void onStop(final @Observes ShutdownEvent ev) {
        LOG.info("shutting down {}", getComponentName());
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

    /**
     * Logs information about the JVM.
     */
    private void logJvmDetails() {
        if (LOG.isInfoEnabled()) {
            LOG.info("running on Java VM [version: {}, name: {}, vendor: {}, max memory: {}MB, processors: {}]",
                    System.getProperty("java.version"),
                    System.getProperty("java.vm.name"),
                    System.getProperty("java.vm.vendor"),
                    Runtime.getRuntime().maxMemory() >> 20,
                    CpuCoreSensor.availableProcessors());
        }

    }

    private CommandRouterAmqpServer amqpServer() {
        final var server = new CommandRouterAmqpServer();
        server.setConfig(amqpServerProperties);
        server.setHealthCheckServer(healthCheckServer);
        server.setSaslAuthenticatorFactory(saslAuthenticatorFactory);
        server.setTracer(tracer);
        server.addEndpoint(commandRouterAmqpEndpoint());

        return server;
    }

    private AmqpEndpoint commandRouterAmqpEndpoint() {

        final var service = commandRouterService();
        final var endpoint = new DelegatingCommandRouterAmqpEndpoint<>(vertx, service) {

            @Override
            public void registerLivenessChecks(final HealthCheckHandler handler) {
                if (service instanceof HealthCheckProvider) {
                    ((HealthCheckProvider) service).registerLivenessChecks(handler);
                }
            }

            @Override
            public void registerReadinessChecks(final HealthCheckHandler handler) {
                if (service instanceof HealthCheckProvider) {
                    ((HealthCheckProvider) service).registerReadinessChecks(handler);
                }
            }
        };
        endpoint.setConfiguration(serviceConfig);
        endpoint.setTracer(tracer);
        return endpoint;
    }

    private CommandRouterService commandRouterService() {
        final var service = new CommandRouterServiceImpl();
        service.setCommandConsumerFactory(commandConsumerFactory());
        service.setCommandTargetMapper(CommandTargetMapper.create(tracer));
        service.setConfig(serviceConfig);
        service.setDeviceConnectionInfo(deviceConnectionInfo);
        service.setRegistrationClient(registrationClient());
        service.setTracer(tracer);
        return service;
    }

    private ClientConfigProperties commandConsumerFactoryConfig() {
        commandConsumerFactoryConfig.setServerRoleIfUnknown("Command & Control");
        commandConsumerFactoryConfig.setNameIfNotSet(getComponentName());
        return commandConsumerFactoryConfig;
    }

    private CommandConsumerFactory commandConsumerFactory() {

        if (kafkaProducerConfig.isConfigured() && kafkaConsumerConfig.isConfigured()) {

            return new KafkaBasedCommandConsumerFactoryImpl(
                    vertx,
                    KafkaProducerFactory.sharedProducerFactory(vertx),
                    kafkaProducerConfig,
                    kafkaConsumerConfig,
                    tracer);

        } else {

            return new ProtonBasedCommandConsumerFactoryImpl(
                    HonoConnection.newConnection(vertx, commandConsumerFactoryConfig(), tracer),
                    SendMessageSampler.Factory.noop());
        }
    }

    private RequestResponseClientConfigProperties registrationServiceClientConfig() {
        deviceRegistrationClientConfig.setServerRoleIfUnknown("Device Registration");
        deviceRegistrationClientConfig.setNameIfNotSet(getComponentName());
        return deviceRegistrationClientConfig;
    }

    private Cache<Object, RegistrationResult> registrationResponseCache() {
        if (registrationResponseCache == null) {
            registrationResponseCache = Caches.newCaffeineCache(deviceRegistrationClientConfig);
        }
        return registrationResponseCache;
    }

    /**
     * Creates a new client for Hono's Device Registration service.
     *
     * @return The client.
     */
    protected DeviceRegistrationClient registrationClient() {
        return new ProtonBasedDeviceRegistrationClient(
                HonoConnection.newConnection(vertx, registrationServiceClientConfig(), tracer),
                SendMessageSampler.Factory.noop(),
                registrationResponseCache());
    }
}
