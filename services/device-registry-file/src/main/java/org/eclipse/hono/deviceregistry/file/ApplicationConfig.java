/*******************************************************************************
 * Copyright (c) 2016, 2020 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.deviceregistry.file;

import java.util.Optional;

import org.eclipse.hono.config.ApplicationConfigProperties;
import org.eclipse.hono.config.ServerConfig;
import org.eclipse.hono.config.ServiceConfigProperties;
import org.eclipse.hono.config.VertxProperties;
import org.eclipse.hono.deviceregistry.server.DeviceRegistryAmqpServer;
import org.eclipse.hono.deviceregistry.service.deviceconnection.MapBasedDeviceConnectionService;
import org.eclipse.hono.deviceregistry.service.deviceconnection.MapBasedDeviceConnectionsConfigProperties;
import org.eclipse.hono.service.HealthCheckServer;
import org.eclipse.hono.service.VertxBasedHealthCheckServer;
import org.eclipse.hono.service.amqp.AmqpEndpoint;
import org.eclipse.hono.service.credentials.CredentialsService;
import org.eclipse.hono.service.credentials.DelegatingCredentialsAmqpEndpoint;
import org.eclipse.hono.service.deviceconnection.DelegatingDeviceConnectionAmqpEndpoint;
import org.eclipse.hono.service.deviceconnection.DeviceConnectionService;
import org.eclipse.hono.service.metric.MetricsTags;
import org.eclipse.hono.service.registration.DelegatingRegistrationAmqpEndpoint;
import org.eclipse.hono.service.registration.RegistrationService;
import org.eclipse.hono.service.tenant.DelegatingTenantAmqpEndpoint;
import org.eclipse.hono.service.tenant.TenantService;
import org.eclipse.hono.util.Constants;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.micrometer.core.instrument.MeterRegistry;
import io.opentracing.Tracer;
import io.opentracing.contrib.tracerresolver.TracerResolver;
import io.opentracing.noop.NoopTracerFactory;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;

/**
 * Spring Boot configuration for the example Device Registry implementation.
 *
 */
@Configuration
public class ApplicationConfig {

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
    public ApplicationConfigProperties applicationConfigProperties() {
        return new ApplicationConfigProperties();
    }

    /**
     * Exposes properties for configuring the health check as a Spring bean.
     *
     * @return The health check configuration properties.
     */
    @Bean
    @ConfigurationProperties(prefix = "hono.health-check")
    public ServerConfig healthCheckConfigProperties() {
        return new ServerConfig();
    }

    /**
     * Exposes the health check server as a Spring bean.
     *
     * @return The health check server.
     */
    @Bean
    public HealthCheckServer healthCheckServer() {
        return new VertxBasedHealthCheckServer(vertx(), healthCheckConfigProperties());
    }

    /**
     * Customizer for meter registry.
     *
     * @return The new meter registry customizer.
     */
    @Bean
    public MeterRegistryCustomizer<MeterRegistry> commonTags() {

        return r -> r.config().commonTags(MetricsTags.forService(Constants.SERVICE_NAME_DEVICE_REGISTRY));

    }

    //
    //
    // Service implementations
    //
    // The other service implementations are defined in DummyServiceConfig and/or FileBasedServiceConfig.
    //

    /**
     * Gets properties for configuring the in-memory <em>Device Connection</em> service.
     *
     * @return The properties.
     */
    @Bean
    @ConfigurationProperties(prefix = "hono.device-connection.svc")
    public MapBasedDeviceConnectionsConfigProperties deviceConnectionProperties() {
        return new MapBasedDeviceConnectionsConfigProperties();
    }

    /**
     * Creates an instance of the file based service for managing device connection information.
     *
     * @return The service.
     */
    @Bean
    public DeviceConnectionService deviceConnectionService() {
        final MapBasedDeviceConnectionService service = new MapBasedDeviceConnectionService();
        service.setConfig(deviceConnectionProperties());
        return service;
    }

    //
    //
    // AMQP 1.0 endpoints
    //
    // The protocol adapters always need a Tenant, Device Registration, Credentials and Device Connection service.
    //

    /**
     * Gets properties for configuring the Device Registry's AMQP 1.0 endpoint.
     *
     * @return The properties.
     */
    @Bean
    @Qualifier(Constants.QUALIFIER_AMQP)
    @ConfigurationProperties(prefix = "hono.registry.amqp")
    public ServiceConfigProperties amqpServerProperties() {
        final ServiceConfigProperties props = new ServiceConfigProperties();
        return props;
    }

    /**
     * Creates a new server for exposing the device registry's AMQP 1.0 based
     * endpoints.
     *
     * @return The server.
     */
    @Bean
    public DeviceRegistryAmqpServer amqpServer() {
        return new DeviceRegistryAmqpServer();
    }

    /**
     * Creates a new instance of an AMQP 1.0 protocol handler for Hono's <em>Tenant</em> API.
     *
     * @param service The service instance to delegate to.
     * @return The handler.
     */
    @Bean
    public AmqpEndpoint tenantAmqpEndpoint(final TenantService service) {
        return new DelegatingTenantAmqpEndpoint<TenantService>(vertx(), service);
    }

    /**
     * Creates a new instance of an AMQP 1.0 protocol handler for Hono's <em>Device Registration</em> API.
     *
     * @param service The service instance to delegate to.
     * @return The handler.
     */
    @Bean
    public AmqpEndpoint registrationAmqpEndpoint(final RegistrationService service) {
        return new DelegatingRegistrationAmqpEndpoint<RegistrationService>(vertx(), service);
    }

    /**
     * Creates a new instance of an AMQP 1.0 protocol handler for Hono's <em>Credentials</em> API.
     *
     * @param service The service instance to delegate to.
     * @return The handler.
     */
    @Bean
    public AmqpEndpoint credentialsAmqpEndpoint(final CredentialsService service) {
        return new DelegatingCredentialsAmqpEndpoint<CredentialsService>(vertx(), service);
    }

    /**
     * Creates a new instance of an AMQP 1.0 protocol handler for Hono's <em>Device Connection</em> API.
     *
     * @param service The service instance to delegate to.
     * @return The handler.
     */
    @Bean
    public AmqpEndpoint deviceConnectionAmqpEndpoint(final DeviceConnectionService service) {
        return new DelegatingDeviceConnectionAmqpEndpoint<DeviceConnectionService>(vertx(), service);
    }
}
