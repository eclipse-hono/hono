/*******************************************************************************
 * Copyright (c) 2019, 2020 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.deviceconnection.infinispan;

import java.util.Optional;

import org.eclipse.hono.config.ApplicationConfigProperties;
import org.eclipse.hono.config.ServerConfig;
import org.eclipse.hono.config.ServiceConfigProperties;
import org.eclipse.hono.config.VertxProperties;
import org.eclipse.hono.deviceconnection.infinispan.client.HotrodBasedDeviceConnectionInfo;
import org.eclipse.hono.deviceconnection.infinispan.client.HotrodCache;
import org.eclipse.hono.deviceconnection.infinispan.client.InfinispanRemoteConfigurationProperties;
import org.eclipse.hono.service.HealthCheckServer;
import org.eclipse.hono.service.VertxBasedHealthCheckServer;
import org.eclipse.hono.service.deviceconnection.DeviceConnectionAmqpEndpoint;
import org.eclipse.hono.service.metric.MetricsTags;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.DeviceConnectionConstants;
import org.infinispan.client.hotrod.RemoteCacheManager;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.ObjectFactoryCreatingFactoryBean;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

import io.micrometer.core.instrument.MeterRegistry;
import io.opentracing.Tracer;
import io.opentracing.contrib.tracerresolver.TracerResolver;
import io.opentracing.noop.NoopTracerFactory;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;

/**
 * Spring Boot configuration for the Device Connection service.
 *
 */
@Configuration
public class ApplicationConfig {

    private static final String BEAN_NAME_AMQP_SERVER = "amqpServer";

    /**
     * Exposes a Vert.x instance as a Spring bean.
     * <p>
     * This method creates new Vert.x default options and invokes
     * {@link VertxProperties#configureVertx(VertxOptions)} on the object returned
     * by {@link #vertxProperties()}.
     * 
     * @return The Vert.x instance. 
     */
    @Bean
    public Vertx vertx() {
        return Vertx.vertx(vertxProperties().configureVertx(new VertxOptions()));
    }

    /**
     * Exposes configuration properties for Vert.x.
     * 
     * @return The properties.
     */
    @ConfigurationProperties("hono.vertx")
    @Bean
    public VertxProperties vertxProperties() {
        return new VertxProperties();
    }

    /**
     * Exposes an OpenTracing {@code Tracer} as a Spring Bean.
     * <p>
     * The Tracer will be resolved by means of a Java service lookup.
     * If no tracer can be resolved this way, the {@code NoopTracer} is
     * returned.
     * 
     * @return The tracer.
     */
    @Bean
    public Tracer getTracer() {

        return Optional.ofNullable(TracerResolver.resolveTracer())
                .orElse(NoopTracerFactory.create());
    }

    /**
     * Gets general properties for configuring the Device Registry Spring Boot application.
     *
     * @return The properties.
     */
    @Bean
    @ConfigurationProperties(prefix = "hono.app")
    public ApplicationConfigProperties applicationConfigProperties(){
        return new ApplicationConfigProperties();
    }

    /**
     * Gets properties for configuring the Device Connection service's AMQP 1.0 endpoint.
     * 
     * @return The properties.
     */
    @Qualifier(Constants.QUALIFIER_AMQP)
    @Bean
    @ConfigurationProperties(prefix = "hono.device-connection.amqp")
    public ServiceConfigProperties amqpProperties() {
        return new ServiceConfigProperties();
    }

    /**
     * Creates a new instance of an AMQP 1.0 protocol handler for Hono's <em>Device Connection</em> API.
     *
     * @return The handler.
     */
    @Bean
    @Scope("prototype")
    public DeviceConnectionAmqpEndpoint deviceConnectionAmqpEndpoint() {
        return new DeviceConnectionAmqpEndpoint(vertx());
    }

    /**
     * Exposes the AMQP based Device Connection service as a Spring Bean.
     * 
     * @return The service.
     */
    @Bean(name = BEAN_NAME_AMQP_SERVER)
    @Scope("prototype")
    public DeviceConnectionAmqpServer server() {
        return new DeviceConnectionAmqpServer();
    }

    /**
     * Exposes a factory for creating Device Connection service instances.
     *
     * @return The factory bean.
     */
    @Bean
    public ObjectFactoryCreatingFactoryBean serviceFactory() {
        final ObjectFactoryCreatingFactoryBean factory = new ObjectFactoryCreatingFactoryBean();
        factory.setTargetBeanName(BEAN_NAME_AMQP_SERVER);
        return factory;
    }

    /**
     * Gets properties for configuring the connection to the remote cache.
     * 
     * @return The properties.
     */
    @Bean
    @ConfigurationProperties(prefix = "hono.device-connection.remote")
    public InfinispanRemoteConfigurationProperties remoteCacheProperties() {
        return new InfinispanRemoteConfigurationProperties();
    }

    /**
     * Exposes a remote cache manager as a Spring bean.
     * 
     * @return The newly created cache manager. The manager will not be started.
     */
    @Bean
    public RemoteCacheManager remoteCacheManager() {
        final InfinispanRemoteConfigurationProperties properties = remoteCacheProperties();
        return new RemoteCacheManager(properties.getConfigurationBuilder().build(), false);
    }

    /**
     * Exposes a remote cache for accessing the Infinispan data grid that contains device
     * connection information.
     *
     * @param vertx The vert.x instance to run on.
     * @return The cache.
     */
    @Bean
    public HotrodCache<String, String> remoteCache(final Vertx vertx) {
        return new HotrodCache<>(
                vertx,
                remoteCacheManager(),
                DeviceConnectionConstants.CACHE_NAME,
                "KEY_CONNECTION_CHECK",
                "VALUE_CONNECTION_CHECK");
    }

    /**
     * Exposes a Device Connection service as a Spring bean.
     * 
     * @param cache The remote cache.
     * @param tracer The tracer instance.
     * @return The service implementation.
     */
    @Bean
    public RemoteCacheBasedDeviceConnectionService deviceConnectionService(final HotrodCache<String, String> cache,
            final Tracer tracer) {
        return new RemoteCacheBasedDeviceConnectionService(new HotrodBasedDeviceConnectionInfo(cache, tracer));
    }

    /**
     * Customizer for meter registry.
     * 
     * @return The new meter registry customizer.
     */
    @Bean
    public MeterRegistryCustomizer<MeterRegistry> commonTags() {

        return r -> r.config().commonTags(MetricsTags.forService(Constants.SERVICE_NAME_DEVICE_CONNECTION));

    }

    /**
     * Gets properties for configuring the service's health check endpoint.
     * 
     * @return The properties.
     */
    @Bean
    @ConfigurationProperties(prefix = "hono.health-check")
    public ServerConfig healthCheckProperties() {
        return new ServerConfig();
    }


    /**
     * Exposes the health check server as a Spring bean.
     *
     * @return The health check server.
     */
    @Bean
    public HealthCheckServer healthCheckServer() {
        return new VertxBasedHealthCheckServer(vertx(), healthCheckProperties());
    }
}
