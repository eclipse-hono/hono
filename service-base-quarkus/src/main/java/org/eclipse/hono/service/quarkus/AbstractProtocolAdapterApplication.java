/**
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
 */
package org.eclipse.hono.service.quarkus;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import javax.enterprise.event.Observes;
import javax.inject.Inject;

import org.eclipse.hono.adapter.client.command.CommandConsumerFactory;
import org.eclipse.hono.adapter.client.command.CommandResponseSender;
import org.eclipse.hono.adapter.client.command.CommandRouterClient;
import org.eclipse.hono.adapter.client.command.DeviceConnectionClient;
import org.eclipse.hono.adapter.client.command.DeviceConnectionClientAdapter;
import org.eclipse.hono.adapter.client.command.amqp.ProtonBasedCommandResponseSender;
import org.eclipse.hono.adapter.client.command.amqp.ProtonBasedCommandRouterClient;
import org.eclipse.hono.adapter.client.command.amqp.ProtonBasedCommandRouterCommandConsumerFactoryImpl;
import org.eclipse.hono.adapter.client.command.amqp.ProtonBasedDelegatingCommandConsumerFactory;
import org.eclipse.hono.adapter.client.command.amqp.ProtonBasedDeviceConnectionClient;
import org.eclipse.hono.adapter.client.registry.CredentialsClient;
import org.eclipse.hono.adapter.client.registry.DeviceRegistrationClient;
import org.eclipse.hono.adapter.client.registry.TenantClient;
import org.eclipse.hono.adapter.client.registry.amqp.ProtonBasedCredentialsClient;
import org.eclipse.hono.adapter.client.registry.amqp.ProtonBasedDeviceRegistrationClient;
import org.eclipse.hono.adapter.client.registry.amqp.ProtonBasedTenantClient;
import org.eclipse.hono.adapter.client.telemetry.amqp.ProtonBasedDownstreamSender;
import org.eclipse.hono.client.HonoConnection;
import org.eclipse.hono.client.RequestResponseClientConfigProperties;
import org.eclipse.hono.client.SendMessageSampler;
import org.eclipse.hono.config.ClientConfigProperties;
import org.eclipse.hono.config.ProtocolAdapterProperties;
import org.eclipse.hono.deviceconnection.infinispan.client.CacheBasedDeviceConnectionClient;
import org.eclipse.hono.deviceconnection.infinispan.client.CacheBasedDeviceConnectionInfo;
import org.eclipse.hono.deviceconnection.infinispan.client.CommonCacheConfig;
import org.eclipse.hono.deviceconnection.infinispan.client.HotrodCache;
import org.eclipse.hono.deviceconnection.infinispan.client.quarkus.DeviceConnectionCacheConfig;
import org.eclipse.hono.service.AbstractProtocolAdapterBase;
import org.eclipse.hono.service.AdapterConfigurationSupport;
import org.eclipse.hono.service.HealthCheckServer;
import org.eclipse.hono.service.cache.Caches;
import org.eclipse.hono.service.monitoring.ConnectionEventProducer;
import org.eclipse.hono.service.monitoring.HonoEventConnectionEventProducer;
import org.eclipse.hono.service.monitoring.LoggingConnectionEventProducer;
import org.eclipse.hono.service.resourcelimits.ResourceLimitChecks;
import org.eclipse.hono.util.CredentialsObject;
import org.eclipse.hono.util.CredentialsResult;
import org.eclipse.hono.util.RegistrationResult;
import org.eclipse.hono.util.TenantObject;
import org.eclipse.hono.util.TenantResult;
import org.infinispan.client.hotrod.RemoteCacheManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.benmanes.caffeine.cache.Cache;

import io.opentracing.Tracer;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;

/**
 * A Quarkus main application base class for Hono protocol adapters.
 * <p>
 * This class provides helper methods for creating clients for Hono's service APIs
 * which to be used with protocol adapter instances.
 *
 * @param <C> The type of configuration properties the adapter uses.
 */
public abstract class AbstractProtocolAdapterApplication<C extends ProtocolAdapterProperties> extends AdapterConfigurationSupport {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractProtocolAdapterApplication.class);

    @Inject
    protected Vertx vertx;

    @Inject
    protected Tracer tracer;

    @Inject
    protected SendMessageSampler.Factory messageSamplerFactory;

    @Inject
    protected HealthCheckServer healthCheckServer;

    @Inject
    protected ResourceLimitChecks resourceLimitChecks;

    @Inject
    protected ProtocolAdapterConfig config;

    @Inject
    protected DeviceConnectionCacheConfig deviceConnectionCacheConfig;

    @Inject
    protected C protocolAdapterProperties;

    private Cache<Object, TenantResult<TenantObject>> tenantResponseCache;
    private Cache<Object, RegistrationResult> registrationResponseCache;
    private Cache<Object, CredentialsResult<CredentialsObject>> credentialsResponseCache;

    /**
     * Creates an instance of the protocol adapter.
     *
     * @return The adapter instance.
     */
    protected abstract AbstractProtocolAdapterBase<C> adapter();

    void onStart(final @Observes StartupEvent ev) {
        LOG.info("deploying {} {} instances ...", config.app.getMaxInstances(), getAdapterName());

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
        LOG.info("shutting down {}", getAdapterName());
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
     *
     * @param start The event indicating that the application has been started.
     */
    protected void logJvmDetails(@Observes final StartupEvent start) {
        if (LOG.isInfoEnabled()) {
            LOG.info("running on Java VM [version: {}, name: {}, vendor: {}, max memory: {}MB, processors: {}]",
                    System.getProperty("java.version"),
                    System.getProperty("java.vm.name"),
                    System.getProperty("java.vm.vendor"),
                    Runtime.getRuntime().maxMemory() >> 20,
                    Runtime.getRuntime().availableProcessors());
        }

    }

    /**
     * Sets collaborators required by all protocol adapters.
     *
     * @param adapter The adapter to set the collaborators on.
     * @throws NullPointerException if adapter is {@code null}
     */
    protected void setCollaborators(final AbstractProtocolAdapterBase<?> adapter) {

        Objects.requireNonNull(adapter);

        final DeviceRegistrationClient registrationClient = registrationClient();

        if (config.commandRouter.isHostConfigured()) {
            final CommandRouterClient commandRouterClient = commandRouterClient();
            adapter.setCommandRouterClient(commandRouterClient);
            adapter.setCommandConsumerFactory(commandConsumerFactory(commandRouterClient));
        } else {
            final DeviceConnectionClient deviceConnectionClient = deviceConnectionClient();
            adapter.setCommandRouterClient(new DeviceConnectionClientAdapter(deviceConnectionClient));
            adapter.setCommandConsumerFactory(commandConsumerFactory(deviceConnectionClient, registrationClient));
        }

        adapter.setCommandResponseSender(commandResponseSender());
        Optional.ofNullable(connectionEventProducer())
            .ifPresent(adapter::setConnectionEventProducer);
        adapter.setCredentialsClient(credentialsClient());
        adapter.setEventSender(downstreamSender());
        adapter.setHealthCheckServer(healthCheckServer);
        adapter.setRegistrationClient(registrationClient);
        adapter.setResourceLimitChecks(resourceLimitChecks);
        adapter.setTelemetrySender(downstreamSender());
        adapter.setTenantClient(tenantClient());
        adapter.setTracer(tracer);
    }

    /**
     * Creates a component that the adapter should use for reporting
     * devices connecting/disconnecting to/from the adapter.
     *
     * @return The component or {@code null} if the configured producer type is <em>none</em> or unsupported.
     */
    protected ConnectionEventProducer connectionEventProducer() {
        switch (config.connectionEvents.getType()) {
        case logging:
            return new LoggingConnectionEventProducer(config.connectionEvents);
        case events:
            return new HonoEventConnectionEventProducer();
        default:
            return null;
        }
    }

    private RequestResponseClientConfigProperties tenantServiceClientConfig() {
        setConfigServerRoleIfUnknown(config.tenant, "Tenant");
        setDefaultConfigNameIfNotSet(config.tenant);
        return config.tenant;
    }

    private Cache<Object, TenantResult<TenantObject>> tenantResponseCache() {
        if (tenantResponseCache == null) {
            tenantResponseCache = Caches.newCaffeineCache(config.tenant);
        }
        return tenantResponseCache;
    }

    /**
     * Creates a new client for Hono's Tenant service.
     *
     * @return The client.
     */
    protected TenantClient tenantClient() {
        return new ProtonBasedTenantClient(
                HonoConnection.newConnection(vertx, tenantServiceClientConfig(), tracer),
                messageSamplerFactory,
                protocolAdapterProperties,
                tenantResponseCache());
    }

    private RequestResponseClientConfigProperties registrationServiceClientConfig() {
        setConfigServerRoleIfUnknown(config.registration, "Device Registration");
        setDefaultConfigNameIfNotSet(config.registration);
        return config.registration;
    }

    private Cache<Object, RegistrationResult> registrationResponseCache() {
        if (registrationResponseCache == null) {
            registrationResponseCache = Caches.newCaffeineCache(config.registration);
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
                messageSamplerFactory,
                protocolAdapterProperties,
                registrationResponseCache());
    }

    private RequestResponseClientConfigProperties credentialsServiceClientConfig() {
        setConfigServerRoleIfUnknown(config.credentials, "Credentials");
        setDefaultConfigNameIfNotSet(config.credentials);
        return config.credentials;
    }

    private Cache<Object, CredentialsResult<CredentialsObject>> credentialsResponseCache() {
        if (credentialsResponseCache == null) {
            credentialsResponseCache = Caches.newCaffeineCache(config.credentials);
        }
        return credentialsResponseCache;
    }

    /**
     * Creates a new client for Hono's Credentials service.
     *
     * @return The client.
     */
    protected CredentialsClient credentialsClient() {
        return new ProtonBasedCredentialsClient(
                HonoConnection.newConnection(vertx, credentialsServiceClientConfig(), tracer),
                messageSamplerFactory,
                protocolAdapterProperties,
                credentialsResponseCache());
    }

    private RequestResponseClientConfigProperties commandRouterServiceClientConfig() {
        setConfigServerRoleIfUnknown(config.commandRouter, "Command Router");
        setDefaultConfigNameIfNotSet(config.commandRouter);
        return config.commandRouter;
    }

    /**
     * Creates a new client for Hono's Command Router service.
     *
     * @return The client.
     */
    protected CommandRouterClient commandRouterClient() {
        return new ProtonBasedCommandRouterClient(
                HonoConnection.newConnection(vertx, commandRouterServiceClientConfig(), tracer),
                messageSamplerFactory,
                protocolAdapterProperties);
    }

    private RequestResponseClientConfigProperties deviceConnectionServiceClientConfig() {
        setConfigServerRoleIfUnknown(config.deviceConnection, "Device Connection");
        setDefaultConfigNameIfNotSet(config.deviceConnection);
        return config.deviceConnection;
    }

    /**
     * Creates a new client for Hono's Device Connection service.
     *
     * @return The client.
     */
    protected DeviceConnectionClient deviceConnectionClient() {

        if (config.deviceConnection.isHostConfigured()) {
            return new ProtonBasedDeviceConnectionClient(
                    HonoConnection.newConnection(vertx, deviceConnectionServiceClientConfig(), tracer),
                    messageSamplerFactory,
                    protocolAdapterProperties);
        } else {
            final RemoteCacheManager cacheManager = new RemoteCacheManager(
                    deviceConnectionCacheConfig.getConfigurationBuilder().build(),
                    false);
            final CommonCacheConfig cacheConfig = new CommonCacheConfig();
            final HotrodCache<String, String> cache = new HotrodCache<>(
                    vertx,
                    cacheManager,
                    cacheConfig.getCacheName(),
                    cacheConfig.getCheckKey(),
                    cacheConfig.getCheckValue());
            return new CacheBasedDeviceConnectionClient(
                    new CacheBasedDeviceConnectionInfo(cache, tracer),
                    tracer);
        }
    }

    private ClientConfigProperties downstreamSenderConfig() {
        setConfigServerRoleIfUnknown(config.messaging, "Downstream");
        setDefaultConfigNameIfNotSet(config.messaging);
        return config.messaging;
    }

    /**
     * Creates a new downstream sender for telemetry and event messages.
     *
     * @return The sender.
     */
    protected ProtonBasedDownstreamSender downstreamSender() {
        return new ProtonBasedDownstreamSender(
                HonoConnection.newConnection(vertx, downstreamSenderConfig(), tracer),
                messageSamplerFactory,
                protocolAdapterProperties.isDefaultsEnabled(),
                protocolAdapterProperties.isJmsVendorPropsEnabled());
    }

    private ClientConfigProperties commandConsumerFactoryConfig() {
        final ClientConfigProperties props = new ClientConfigProperties(config.command);
        setConfigServerRoleIfUnknown(props, "Command & Control");
        setDefaultConfigNameIfNotSet(props);
        return props;
    }

    /**
     * Creates a new connection to the AMQP Messaging Network's Command &amp; Control endpoint.
     *
     * @return The connection.
     */
    protected HonoConnection commandConsumerConnection() {
        return HonoConnection.newConnection(vertx, commandConsumerFactoryConfig(), tracer);
    }

    /**
     * Creates a new factory for creating command consumers.
     * <p>
     * The returned factory will also take care of routing commands to target adapter instances.
     *
     * @param deviceConnectionClient The client to use for accessing the Device Connection service.
     * @param deviceRegistrationClient The client to use for accessing the Device Registration service.
     * @return The factory.
     */
    protected CommandConsumerFactory commandConsumerFactory(
            final DeviceConnectionClient deviceConnectionClient,
            final DeviceRegistrationClient deviceRegistrationClient) {

        LOG.debug("using Device Connection service client, configuring CommandConsumerFactory [{}]",
                ProtonBasedDelegatingCommandConsumerFactory.class.getName());
        return new ProtonBasedDelegatingCommandConsumerFactory(
                commandConsumerConnection(),
                messageSamplerFactory,
                deviceConnectionClient,
                deviceRegistrationClient,
                tracer);
    }

    /**
     * Creates a new factory for creating command consumers.
     * <p>
     * The returned factory does not support routing commands to target adapter instances.
     *
     * @param commandRouterClient The client for accessing the Command Router service.
     * @return The factory.
     */
    protected CommandConsumerFactory commandConsumerFactory(final CommandRouterClient commandRouterClient) {
        LOG.debug("using Command Router service client, configuring CommandConsumerFactory [{}}]",
                ProtonBasedCommandRouterCommandConsumerFactoryImpl.class.getName());
        return new ProtonBasedCommandRouterCommandConsumerFactoryImpl(
                commandConsumerConnection(),
                messageSamplerFactory,
                commandRouterClient);
    }

    private ClientConfigProperties commandResponseSenderConfig() {
        final ClientConfigProperties props = new ClientConfigProperties(config.messaging);
        setConfigServerRoleIfUnknown(props, "Command Response");
        setDefaultConfigNameIfNotSet(props);
        return props;
    }

    /**
     * Creates a new client for sending command response messages downstream.
     *
     * @return The client.
     */
    protected CommandResponseSender commandResponseSender() {
        return new ProtonBasedCommandResponseSender(
                HonoConnection.newConnection(vertx, commandResponseSenderConfig(), tracer),
                messageSamplerFactory,
                protocolAdapterProperties);
    }
}
