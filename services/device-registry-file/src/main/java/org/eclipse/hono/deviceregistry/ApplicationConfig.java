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

package org.eclipse.hono.deviceregistry;

import java.util.Optional;

import org.eclipse.hono.auth.HonoPasswordEncoder;
import org.eclipse.hono.auth.SpringBasedHonoPasswordEncoder;
import org.eclipse.hono.config.ApplicationConfigProperties;
import org.eclipse.hono.config.ServerConfig;
import org.eclipse.hono.config.ServiceConfigProperties;
import org.eclipse.hono.config.VertxProperties;
import org.eclipse.hono.deviceregistry.file.FileBasedCredentialsConfigProperties;
import org.eclipse.hono.deviceregistry.file.FileBasedRegistrationConfigProperties;
import org.eclipse.hono.deviceregistry.file.FileBasedTenantsConfigProperties;
import org.eclipse.hono.deviceregistry.service.deviceconnection.MapBasedDeviceConnectionsConfigProperties;
import org.eclipse.hono.service.HealthCheckServer;
import org.eclipse.hono.service.VertxBasedHealthCheckServer;
import org.eclipse.hono.service.amqp.AmqpEndpoint;
import org.eclipse.hono.service.credentials.CredentialsService;
import org.eclipse.hono.service.credentials.DelegatingCredentialsAmqpEndpoint;
import org.eclipse.hono.service.deviceconnection.DelegatingDeviceConnectionAmqpEndpoint;
import org.eclipse.hono.service.deviceconnection.DeviceConnectionService;
import org.eclipse.hono.service.http.HttpEndpoint;
import org.eclipse.hono.service.management.credentials.CredentialsManagementService;
import org.eclipse.hono.service.management.credentials.DelegatingCredentialsManagementHttpEndpoint;
import org.eclipse.hono.service.management.device.DelegatingDeviceManagementHttpEndpoint;
import org.eclipse.hono.service.management.device.DeviceManagementService;
import org.eclipse.hono.service.management.tenant.DelegatingTenantManagementHttpEndpoint;
import org.eclipse.hono.service.management.tenant.TenantManagementService;
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
 * Spring Boot configuration for the Device Registry application.
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
     * Gets properties for configuring the Device Registry's AMQP 1.0 endpoint.
     *
     * @return The properties.
     */
    @Qualifier(Constants.QUALIFIER_AMQP)
    @Bean
    @ConfigurationProperties(prefix = "hono.registry.amqp")
    public ServiceConfigProperties amqpServerProperties() {
        final ServiceConfigProperties props = new ServiceConfigProperties();
        return props;
    }

    /**
     * Creates a new instance of an AMQP 1.0 protocol handler for Hono's <em>Tenant</em> service.
     *
     * @param service The service instance to delegate to.
     * @return The handler.
     */
    @Bean
    public AmqpEndpoint tenantAmqpEndpoint(@Qualifier("backend") final TenantService service) {
        final DelegatingTenantAmqpEndpoint<TenantService> ep = new DelegatingTenantAmqpEndpoint<>(vertx());
        ep.setConfiguration(amqpServerProperties());
        ep.setService(service);
        return ep;
    }

    /**
     * Creates a new instance of an AMQP 1.0 protocol handler for Hono's <em>Device Registration</em> service.
     *
     * @param service The service instance to delegate to.
     * @return The handler.
     */
    @Bean
    public AmqpEndpoint registrationAmqpEndpoint(@Qualifier("backend") final RegistrationService service) {
        final DelegatingRegistrationAmqpEndpoint<RegistrationService> ep = new DelegatingRegistrationAmqpEndpoint<>(vertx());
        ep.setConfiguration(amqpServerProperties());
        ep.setService(service);
        return ep;
    }

    /**
     * Creates a new instance of an AMQP 1.0 protocol handler for Hono's <em>Credentials</em> service.
     *
     * @param service The service instance to delegate to.
     * @return The handler.
     */
    @Bean
    public AmqpEndpoint credentialsAmqpEndpoint(@Qualifier("backend") final CredentialsService service) {
        final DelegatingCredentialsAmqpEndpoint<CredentialsService> ep = new DelegatingCredentialsAmqpEndpoint<>(vertx());
        ep.setConfiguration(amqpServerProperties());
        ep.setService(service);
        return ep;
    }

    /**
     * Creates a new instance of an AMQP 1.0 protocol handler for Hono's <em>Device Connection</em> service.
     *
     * @param service The service instance to delegate to.
     * @return The handler.
     */
    @Bean
    public AmqpEndpoint deviceConnectionAmqpEndpoint(@Qualifier("backend") final DeviceConnectionService service) {
        final DelegatingDeviceConnectionAmqpEndpoint<DeviceConnectionService> ep = new DelegatingDeviceConnectionAmqpEndpoint<>(vertx());
        ep.setConfiguration(amqpServerProperties());
        ep.setService(service);
        return ep;
    }

    /**
     * Gets properties for configuring the HTTP based Device Registry Management endpoint.
     *
     * @return The properties.
     */
    @Qualifier(Constants.QUALIFIER_REST)
    @Bean
    @ConfigurationProperties(prefix = "hono.registry.rest")
    public ServiceConfigProperties httpServerProperties() {
        return new ServiceConfigProperties();
    }

    /**
     * Creates a new instance of an HTTP protocol handler for the <em>devices</em> resources
     * of Hono's Device Registry Management API's.
     *
     * @return The handler.
     */
    @Bean
    public HttpEndpoint deviceHttpEndpoint() {
        return new DelegatingDeviceManagementHttpEndpoint<DeviceManagementService>(vertx());
    }

    /**
     * Creates a new instance of an HTTP protocol handler for the <em>credentials</em> resources
     * of Hono's Device Registry Management API's.
     *
     * @return The handler.
     */
    @Bean
    public HttpEndpoint credentialsHttpEndpoint() {
        return new DelegatingCredentialsManagementHttpEndpoint<CredentialsManagementService>(vertx());
    }

    /**
     * Creates a new instance of an HTTP protocol handler for the <em>tenants</em> resources
     * of Hono's Device Registry Management API's.
     *
     * @return The handler.
     */
    @Bean
    public HttpEndpoint tenantHttpEndpoint() {
        return new DelegatingTenantManagementHttpEndpoint<TenantManagementService>(vertx());
    }

    /**
     * Gets properties for configuring {@code FileBasedRegistrationService} which implements
     * the <em>Device Registration</em> API.
     *
     * @return The properties.
     */
    @Bean
    @ConfigurationProperties(prefix = "hono.registry.svc")
    public FileBasedRegistrationConfigProperties serviceProperties() {
        return new FileBasedRegistrationConfigProperties();
    }

    /**
     * Gets properties for configuring {@code FileBasedCredentialsService} which implements
     * the <em>Credentials</em> API.
     *
     * @return The properties.
     */
    @Bean
    @ConfigurationProperties(prefix = "hono.credentials.svc")
    public FileBasedCredentialsConfigProperties credentialsProperties() {
        return new FileBasedCredentialsConfigProperties();
    }

    /**
     * Gets properties for configuring {@code FileBasedTenantsService} which implements
     * the <em>Tenants</em> API.
     *
     * @return The properties.
     */
    @Bean
    @ConfigurationProperties(prefix = "hono.tenant.svc")
    public FileBasedTenantsConfigProperties tenantsProperties() {
        return new FileBasedTenantsConfigProperties();
    }

    /**
     * Gets properties for configuring {@code InMemoryDeviceConnectionService} which implements
     * the <em>Device Connection</em> API.
     *
     * @return The properties.
     */
    @Bean
    @ConfigurationProperties(prefix = "hono.device-connection.svc")
    public MapBasedDeviceConnectionsConfigProperties deviceConnectionsProperties() {
        return new MapBasedDeviceConnectionsConfigProperties();
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
     * Exposes a password encoder to use for encoding clear text passwords
     * and for matching password hashes.
     *
     * @return The encoder.
     */
    @Bean
    public HonoPasswordEncoder passwordEncoder() {
        return new SpringBasedHonoPasswordEncoder(credentialsProperties().getMaxBcryptIterations());
    }
}
