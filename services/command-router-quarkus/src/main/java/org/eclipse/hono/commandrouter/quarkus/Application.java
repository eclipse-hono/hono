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
import javax.inject.Inject;

import org.eclipse.hono.client.HonoConnection;
import org.eclipse.hono.client.SendMessageSampler;
import org.eclipse.hono.client.kafka.KafkaProducerConfigProperties;
import org.eclipse.hono.client.kafka.KafkaProducerFactory;
import org.eclipse.hono.client.kafka.consumer.KafkaConsumerConfigProperties;
import org.eclipse.hono.client.quarkus.ClientConfigProperties;
import org.eclipse.hono.client.quarkus.RequestResponseClientConfigProperties;
import org.eclipse.hono.client.registry.DeviceRegistrationClient;
import org.eclipse.hono.client.registry.TenantClient;
import org.eclipse.hono.client.registry.amqp.ProtonBasedDeviceRegistrationClient;
import org.eclipse.hono.client.registry.amqp.ProtonBasedTenantClient;
import org.eclipse.hono.client.util.MessagingClientProvider;
import org.eclipse.hono.commandrouter.AdapterInstanceStatusService;
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
import org.eclipse.hono.service.amqp.AmqpEndpoint;
import org.eclipse.hono.service.auth.AuthenticationService;
import org.eclipse.hono.service.cache.Caches;
import org.eclipse.hono.service.commandrouter.CommandRouterService;
import org.eclipse.hono.service.commandrouter.DelegatingCommandRouterAmqpEndpoint;
import org.eclipse.hono.service.metric.MetricsTags;
import org.eclipse.hono.service.quarkus.AbstractServiceApplication;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.RegistrationResult;
import org.eclipse.hono.util.TenantObject;
import org.eclipse.hono.util.TenantResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.benmanes.caffeine.cache.Cache;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.WatcherException;
import io.opentracing.Tracer;
import io.quarkus.arc.config.ConfigPrefix;
import io.vertx.core.CompositeFuture;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Promise;
import io.vertx.core.Verticle;
import io.vertx.ext.healthchecks.HealthCheckHandler;
import io.vertx.proton.sasl.ProtonSaslAuthenticatorFactory;

/**
 * The Quarkus based Command Router main application class.
 */
@ApplicationScoped
public class Application extends AbstractServiceApplication {

    // workaround so that the Quarkus KubernetesClientProcessor finds a Pod watcher and registers corresponding model classes
    static {
        new Watcher<Pod>() {
            @Override
            public void eventReceived(final Action action, final Pod resource) {
            }
            @Override
            public void onClose(final WatcherException cause) {
            }
        };
    }

    private static final String COMPONENT_NAME = "Hono Command Router";
    private static final Logger LOG = LoggerFactory.getLogger(Application.class);

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

    @ConfigPrefix("hono.tenant")
    RequestResponseClientConfigProperties tenantClientConfig;

    @Inject
    KafkaProducerConfigProperties kafkaProducerConfig;

    @Inject
    KafkaConsumerConfigProperties kafkaConsumerConfig;

    @Inject
    DeviceConnectionInfo deviceConnectionInfo;

    @Inject
    ProtonSaslAuthenticatorFactory saslAuthenticatorFactory;

    @Inject
    AuthenticationService authenticationService;

    @Inject
    AdapterInstanceStatusService adapterInstanceStatusService;

    private Cache<Object, RegistrationResult> registrationResponseCache;

    private Cache<Object, TenantResult<TenantObject>> tenantResponseCache;

    @Override
    public String getComponentName() {
        return COMPONENT_NAME;
    }

    @Override
    protected void doStart() {

        if (!(authenticationService instanceof Verticle)) {
            throw new IllegalStateException("Authentication service must be a vert.x Verticle");
        }

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
        final DeviceRegistrationClient registrationClient = registrationClient();
        final TenantClient tenantClient = tenantClient();

        final CommandTargetMapper commandTargetMapper = CommandTargetMapper.create(registrationClient, deviceConnectionInfo, tracer);
        return new CommandRouterServiceImpl(
                serviceConfig,
                registrationClient,
                tenantClient,
                deviceConnectionInfo,
                commandConsumerFactoryProvider(tenantClient, commandTargetMapper),
                adapterInstanceStatusService,
                tracer);
    }

    private ClientConfigProperties commandConsumerFactoryConfig() {
        commandConsumerFactoryConfig.setServerRoleIfUnknown("Command & Control");
        commandConsumerFactoryConfig.setNameIfNotSet(getComponentName());
        return commandConsumerFactoryConfig;
    }

    private MessagingClientProvider<CommandConsumerFactory> commandConsumerFactoryProvider(final TenantClient tenantClient,
            final CommandTargetMapper commandTargetMapper) {

        final MessagingClientProvider<CommandConsumerFactory> commandConsumerFactoryProvider = new MessagingClientProvider<>();
        if (kafkaProducerConfig.isConfigured() && kafkaConsumerConfig.isConfigured()) {
            commandConsumerFactoryProvider.setClient(new KafkaBasedCommandConsumerFactoryImpl(
                    vertx,
                    tenantClient,
                    commandTargetMapper,
                    KafkaProducerFactory.sharedProducerFactory(vertx),
                    kafkaProducerConfig,
                    kafkaConsumerConfig,
                    tracer));
        }
        final ClientConfigProperties commandConsumerFactoryConfig = commandConsumerFactoryConfig();
        if (commandConsumerFactoryConfig.isHostConfigured()) {
            commandConsumerFactoryProvider.setClient(new ProtonBasedCommandConsumerFactoryImpl(
                    HonoConnection.newConnection(vertx, commandConsumerFactoryConfig, tracer),
                    tenantClient,
                    commandTargetMapper,
                    SendMessageSampler.Factory.noop()));
        }
        return commandConsumerFactoryProvider;
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

    private RequestResponseClientConfigProperties tenantServiceClientConfig() {
        tenantClientConfig.setServerRoleIfUnknown("Tenant");
        tenantClientConfig.setNameIfNotSet(getComponentName());
        return tenantClientConfig;
    }

    private Cache<Object, TenantResult<TenantObject>> tenantResponseCache() {
        if (tenantResponseCache == null) {
            tenantResponseCache = Caches.newCaffeineCache(tenantClientConfig);
        }
        return tenantResponseCache;
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

    /**
     * Creates a new client for Hono's Tenant service.
     *
     * @return The client.
     */
    protected TenantClient tenantClient() {
        return new ProtonBasedTenantClient(
                HonoConnection.newConnection(vertx, tenantServiceClientConfig(), tracer),
                SendMessageSampler.Factory.noop(),
                tenantResponseCache());
    }
}
