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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.Produces;
import javax.inject.Inject;
import javax.inject.Singleton;

import org.eclipse.hono.adapter.http.MicrometerBasedHttpAdapterMetrics;
import org.eclipse.hono.adapter.http.impl.VertxBasedHttpProtocolAdapter;
import org.eclipse.hono.cache.CacheProvider;
import org.eclipse.hono.cache.ExpiringValueCache;
import org.eclipse.hono.client.CommandTargetMapper;
import org.eclipse.hono.client.CredentialsClientFactory;
import org.eclipse.hono.client.DeviceConnectionClientFactory;
import org.eclipse.hono.client.DownstreamSenderFactory;
import org.eclipse.hono.client.HonoConnection;
import org.eclipse.hono.client.ProtocolAdapterCommandConsumerFactory;
import org.eclipse.hono.client.RegistrationClientFactory;
import org.eclipse.hono.client.TenantClientFactory;
import org.eclipse.hono.service.HealthCheckServer;
import org.eclipse.hono.service.VertxBasedHealthCheckServer;
import org.eclipse.hono.service.metric.PrometheusScrapingResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.benmanes.caffeine.cache.Caffeine;

import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import io.opentracing.Tracer;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.Startup;
import io.quarkus.runtime.StartupEvent;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;

/**
 * The Hono HTTP adapter main application class.
 */
@ApplicationScoped
@Startup
public class Application {

    private static final Logger LOG = LoggerFactory.getLogger(Application.class);

    @Inject
    Vertx vertx;

    @Inject
    HonoConfig config;

    @Inject Tracer tracer;

    void onStart(final @Observes StartupEvent ev) {
        LOG.info("deploying {} HTTP adapter instances ...", config.app.getMaxInstances());

        final CompletableFuture<Void> startup = new CompletableFuture<>();
        final Promise<String> deploymentTracker = Promise.promise();
        vertx.deployVerticle(
                () -> adapter(),
                new DeploymentOptions().setInstances(config.app.getMaxInstances()),
                deploymentTracker);
        deploymentTracker.future()
            .compose(s -> healthCheckServer().start())
            .onSuccess(ok -> startup.complete(null))
            .onFailure(t -> startup.completeExceptionally(t));
        startup.join();

    }

    void onStop(final @Observes ShutdownEvent ev) {
        LOG.info("shutting down HTTP adapter");
        final CompletableFuture<Void> shutdown = new CompletableFuture<>();
        healthCheckServer().stop()
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

    @Produces
    VertxBasedHttpProtocolAdapter adapter() {

        final VertxBasedHttpProtocolAdapter adapter = new VertxBasedHttpProtocolAdapter();
        adapter.setCommandConsumerFactory(commandConsumerFactory());
        adapter.setCommandTargetMapper(commandTargetMapper());
        adapter.setConfig(config.http);
        adapter.setCredentialsClientFactory(credentialsClientFactory());
        adapter.setDeviceConnectionClientFactory(deviceConnectionClientFactory());
        adapter.setDownstreamSenderFactory(downstreamSenderFactory());
        adapter.setHealthCheckServer(healthCheckServer());
        adapter.setMetrics(metrics());
        adapter.setRegistrationClientFactory(registrationClientFactory());
        adapter.setTenantClientFactory(tenantClientFactory());
        adapter.setTracer(tracer);
        //TODO consider adding resource limit check
        return adapter;
    }

    @Singleton
    PrometheusMeterRegistry metricsRegistry() {
        return new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
    }

    @Singleton
    MicrometerBasedHttpAdapterMetrics metrics() {
        return new MicrometerBasedHttpAdapterMetrics(metricsRegistry(), vertx);
    }

    @Singleton
    PrometheusScrapingResource scrapingResource() {
        return new PrometheusScrapingResource(metricsRegistry());
    }

    @Produces
    HonoConnection commandConsumerConnection() {
        return HonoConnection.newConnection(vertx, config.command);
    }

    @Produces
    ProtocolAdapterCommandConsumerFactory commandConsumerFactory() {
        return ProtocolAdapterCommandConsumerFactory.create(commandConsumerConnection());
    }

    @Produces
    CommandTargetMapper commandTargetMapper() {
        return CommandTargetMapper.create(tracer);
    }

    @Produces
    CredentialsClientFactory credentialsClientFactory() {
        return CredentialsClientFactory.create(
                HonoConnection.newConnection(vertx, config.credentials),
                newCaffeineCache(config.credentials.getResponseCacheMinSize(), config.credentials.getResponseCacheMaxSize()));
    }

    @Produces
    DeviceConnectionClientFactory deviceConnectionClientFactory() {
        return DeviceConnectionClientFactory.create(HonoConnection.newConnection(vertx, config.deviceConnection));
    }

    @Produces
    DownstreamSenderFactory downstreamSenderFactory() {
        return DownstreamSenderFactory.create(HonoConnection.newConnection(vertx, config.messaging));
    }

    @Singleton
    HealthCheckServer healthCheckServer() {
        final VertxBasedHealthCheckServer server = new VertxBasedHealthCheckServer(vertx, config.healthCheck);
        server.setAdditionalResources(Collections.singletonList(scrapingResource()));
        return server;
    }

    @Produces
    RegistrationClientFactory registrationClientFactory() {
        return RegistrationClientFactory.create(
                HonoConnection.newConnection(vertx, config.registration),
                newCaffeineCache(config.registration.getResponseCacheMinSize(), config.registration.getResponseCacheMaxSize()));
    }

    @Produces
    TenantClientFactory tenantClientFactory() {
        return TenantClientFactory.create(
                HonoConnection.newConnection(vertx, config.tenant),
                newCaffeineCache(config.tenant.getResponseCacheMinSize(), config.tenant.getResponseCacheMaxSize()));
    }

    /**
     * Create a new cache provider based on Caffeine.
     *
     * @param minCacheSize The minimum size of the cache.
     * @param maxCacheSize the maximum size of the cache.
     * @return A new cache provider or {@code null} if no cache should be used.
     */
    private static CacheProvider newCaffeineCache(final int minCacheSize, final long maxCacheSize) {

        if (maxCacheSize <= 0) {
            return null;
        }

        final Caffeine<Object, Object> caffeine = Caffeine.newBuilder()
                .initialCapacity(minCacheSize)
                .maximumSize(Math.max(minCacheSize, maxCacheSize));

        return new CacheProvider() {
            private Map<String, ExpiringValueCache<Object, Object>> caches = new HashMap<>();

            @Override
            public ExpiringValueCache<Object, Object> getCache(final String cacheName) {

                return caches.computeIfAbsent(cacheName, name -> new CaffeineBasedExpiringValueCache<>(caffeine.build()));
            }
        };
    }

}
