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
package org.eclipse.hono.service.quarkus;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import javax.enterprise.event.Observes;
import javax.inject.Inject;

import org.eclipse.hono.adapter.client.command.CommandConsumerFactory;
import org.eclipse.hono.adapter.client.command.CommandResponseSender;
import org.eclipse.hono.adapter.client.command.CommandRouterClient;
import org.eclipse.hono.adapter.client.command.DeviceConnectionClient;
import org.eclipse.hono.adapter.client.command.DeviceConnectionClientAdapter;
import org.eclipse.hono.adapter.client.command.amqp.ProtonBasedCommandConsumerFactory;
import org.eclipse.hono.adapter.client.command.amqp.ProtonBasedCommandResponseSender;
import org.eclipse.hono.adapter.client.command.amqp.ProtonBasedCommandRouterClient;
import org.eclipse.hono.adapter.client.command.amqp.ProtonBasedDeviceConnectionClient;
import org.eclipse.hono.adapter.client.registry.CredentialsClient;
import org.eclipse.hono.adapter.client.registry.DeviceRegistrationClient;
import org.eclipse.hono.adapter.client.registry.TenantClient;
import org.eclipse.hono.adapter.client.registry.amqp.ProtonBasedCredentialsClient;
import org.eclipse.hono.adapter.client.registry.amqp.ProtonBasedDeviceRegistrationClient;
import org.eclipse.hono.adapter.client.registry.amqp.ProtonBasedTenantClient;
import org.eclipse.hono.adapter.client.telemetry.amqp.ProtonBasedDownstreamSender;
import org.eclipse.hono.cache.CacheProvider;
import org.eclipse.hono.cache.ExpiringValueCache;
import org.eclipse.hono.client.HonoConnection;
import org.eclipse.hono.client.RequestResponseClientConfigProperties;
import org.eclipse.hono.client.SendMessageSampler;
import org.eclipse.hono.config.ProtocolAdapterProperties;
import org.eclipse.hono.deviceconnection.infinispan.client.CacheBasedDeviceConnectionClient;
import org.eclipse.hono.deviceconnection.infinispan.client.CacheBasedDeviceConnectionInfo;
import org.eclipse.hono.deviceconnection.infinispan.client.CommonCacheConfig;
import org.eclipse.hono.deviceconnection.infinispan.client.HotrodCache;
import org.eclipse.hono.deviceconnection.infinispan.client.quarkus.DeviceConnectionCacheConfig;
import org.eclipse.hono.service.AbstractProtocolAdapterBase;
import org.eclipse.hono.service.HealthCheckServer;
import org.eclipse.hono.service.cache.CaffeineBasedExpiringValueCache;
import org.eclipse.hono.service.monitoring.ConnectionEventProducer;
import org.eclipse.hono.service.monitoring.HonoEventConnectionEventProducer;
import org.eclipse.hono.service.monitoring.LoggingConnectionEventProducer;
import org.eclipse.hono.service.resourcelimits.ResourceLimitChecks;
import org.infinispan.client.hotrod.RemoteCacheManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.benmanes.caffeine.cache.Caffeine;

import io.opentracing.Tracer;
import io.quarkus.runtime.StartupEvent;
import io.vertx.core.Vertx;

/**
 * A Quarkus main application base class for Hono protocol adapters.
 * <p>
 * This class provides helper methods for creating clients for Hono's service APIs
 * which to be used with protocol adapter instances.
 */
public abstract class AbstractProtocolAdapterApplication {

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
    protected ProtocolAdapterProperties protocolAdapterProperties;

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

    /**
     * Creates a new client for Hono's Tenant service.
     *
     * @return The client.
     */
    protected TenantClient tenantClient() {
        return new ProtonBasedTenantClient(
                HonoConnection.newConnection(vertx, config.tenant, tracer),
                messageSamplerFactory,
                protocolAdapterProperties,
                newCaffeineCache(config.tenant));
    }

    /**
     * Creates a new client for Hono's Device Registration service.
     *
     * @return The client.
     */
    protected DeviceRegistrationClient registrationClient() {
        return new ProtonBasedDeviceRegistrationClient(
                HonoConnection.newConnection(vertx, config.registration, tracer),
                messageSamplerFactory,
                protocolAdapterProperties,
                newCaffeineCache(config.registration));
    }

    /**
     * Creates a new client for Hono's Credentials service.
     *
     * @return The client.
     */
    protected CredentialsClient credentialsClient() {
        return new ProtonBasedCredentialsClient(
                HonoConnection.newConnection(vertx, config.credentials, tracer),
                messageSamplerFactory,
                protocolAdapterProperties,
                newCaffeineCache(config.credentials));
    }

    /**
     * Creates a new client for Hono's Command Router service.
     *
     * @return The client.
     */
    protected CommandRouterClient commandRouterClient() {
        return new ProtonBasedCommandRouterClient(
                HonoConnection.newConnection(vertx, config.commandRouter, tracer),
                messageSamplerFactory,
                protocolAdapterProperties);
    }

    /**
     * Creates a new client for Hono's Device Connection service.
     *
     * @return The client.
     */
    protected DeviceConnectionClient deviceConnectionClient() {

        if (config.deviceConnection.isHostConfigured()) {
            return new ProtonBasedDeviceConnectionClient(
                    HonoConnection.newConnection(vertx, config.deviceConnection, tracer),
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

    /**
     * Creates a new downstream sender for telemetry and event messages.
     *
     * @return The sender.
     */
    protected ProtonBasedDownstreamSender downstreamSender() {
        return new ProtonBasedDownstreamSender(
                HonoConnection.newConnection(vertx, config.messaging, tracer),
                messageSamplerFactory,
                protocolAdapterProperties);
    }

    /**
     * Creates a new connection to the AMQP Messaging Network's Command &amp; Control endpoint.
     *
     * @return The connection.
     */
    protected HonoConnection commandConsumerConnection() {
        return HonoConnection.newConnection(vertx, config.command, tracer);
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
                ProtonBasedCommandConsumerFactory.class.getName());
        return new ProtonBasedCommandConsumerFactory(
                commandConsumerConnection(),
                messageSamplerFactory,
                protocolAdapterProperties,
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
     * @throws UnsupportedOperationException if the factory type is not supported yet
     */
    protected CommandConsumerFactory commandConsumerFactory(
            final CommandRouterClient commandRouterClient) {
        LOG.debug("using Command Router service client, configuring CommandConsumerFactory [unknown]");
        throw new UnsupportedOperationException("not supported yet");
    }

    /**
     * Creates a new client for sending command response messages downstream.
     *
     * @return The client.
     */
    protected CommandResponseSender commandResponseSender() {
        return new ProtonBasedCommandResponseSender(
                HonoConnection.newConnection(vertx, config.command, tracer),
                messageSamplerFactory,
                protocolAdapterProperties);
    }

    /**
     * Create a new cache provider based on Caffeine.
     *
     * @param config The client configuration to determine the cache size from.
     * @return A new cache provider or {@code null} if no cache should be used.
     * @throws NullPointerException if config is {@code null}.
     */
    protected static CacheProvider newCaffeineCache(final RequestResponseClientConfigProperties config) {

        Objects.requireNonNull(config);

        if (config.getResponseCacheMaxSize() <= 0) {
            return null;
        }

        final Caffeine<Object, Object> caffeine = Caffeine.newBuilder()
                .initialCapacity(config.getResponseCacheMinSize())
                .maximumSize(Math.max(config.getResponseCacheMinSize(), config.getResponseCacheMaxSize()));

        return new CacheProvider() {
            private final Map<String, ExpiringValueCache<Object, Object>> caches = new HashMap<>();

            @Override
            public ExpiringValueCache<Object, Object> getCache(final String cacheName) {

                return caches.computeIfAbsent(cacheName, name -> new CaffeineBasedExpiringValueCache<>(caffeine.build()));
            }
        };
    }

}
