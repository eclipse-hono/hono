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
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.enterprise.event.Observes;
import javax.inject.Inject;

import org.eclipse.hono.adapter.client.command.DeviceConnectionClient;
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
import org.eclipse.hono.client.CommandTargetMapper;
import org.eclipse.hono.client.HonoConnection;
import org.eclipse.hono.client.ProtocolAdapterCommandConsumerFactory;
import org.eclipse.hono.client.RequestResponseClientConfigProperties;
import org.eclipse.hono.client.SendMessageSampler;
import org.eclipse.hono.config.ProtocolAdapterProperties;
import org.eclipse.hono.service.HealthCheckServer;
import org.eclipse.hono.service.VertxBasedHealthCheckServer;
import org.eclipse.hono.service.cache.CaffeineBasedExpiringValueCache;
import org.eclipse.hono.service.resourcelimits.ResourceLimitChecks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.benmanes.caffeine.cache.Caffeine;

import io.opentracing.Tracer;
import io.quarkus.runtime.StartupEvent;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.ext.web.Router;

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
    protected List<Handler<Router>> healthCheckResourceProviders;

    @Inject
    protected ResourceLimitChecks resourceLimitChecks;

    @Inject
    protected ProtocolAdapterConfig config;

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
     * Creates a new Health Check server.
     *
     * @return The server.
     */
    protected HealthCheckServer healthCheckServer() {
        final VertxBasedHealthCheckServer server = new VertxBasedHealthCheckServer(vertx, config.healthCheck);
        server.setAdditionalResources(healthCheckResourceProviders);
        return server;
    }

    /**
     * Creates a new client for Hono's Tenant service.
     *
     * @return The client.
     */
    protected TenantClient tenantClient() {
        return new ProtonBasedTenantClient(
                HonoConnection.newConnection(vertx, config.tenant),
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
                HonoConnection.newConnection(vertx, config.registration),
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
                HonoConnection.newConnection(vertx, config.credentials),
                messageSamplerFactory,
                protocolAdapterProperties,
                newCaffeineCache(config.credentials));
    }

    /**
     * Creates a new client for Hono's Device Connection service.
     *
     * @return The client.
     */
    protected DeviceConnectionClient deviceConnectionClient() {
        return new ProtonBasedDeviceConnectionClient(
                HonoConnection.newConnection(vertx, config.deviceConnection),
                messageSamplerFactory,
                protocolAdapterProperties);
    }

    /**
     * Creates a new downstream sender for telemetry and event messages.
     *
     * @return The sender.
     */
    protected ProtonBasedDownstreamSender downstreamSender() {
        return new ProtonBasedDownstreamSender(
                HonoConnection.newConnection(vertx, config.messaging),
                messageSamplerFactory,
                protocolAdapterProperties);
    }

    /**
     * Creates a new connection to the AMQP Messaging Network's Command &amp; Control endpoint.
     *
     * @return The connection.
     */
    protected HonoConnection commandConsumerConnection() {
        return HonoConnection.newConnection(vertx, config.command);
    }

    /**
     * Creates a new factory for command consumers.
     *
     * @return The factory.
     */
    protected ProtocolAdapterCommandConsumerFactory commandConsumerFactory() {
        return ProtocolAdapterCommandConsumerFactory.create(commandConsumerConnection());
    }

    /**
     * Creates a new command target mapper.
     *
     * @return The mapper.
     */
    protected CommandTargetMapper commandTargetMapper() {
        return CommandTargetMapper.create(tracer);
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
