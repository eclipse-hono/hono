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


package org.eclipse.hono.deviceregistry.file;

import org.eclipse.hono.adapter.spring.AbstractMessagingClientConfig;
import org.eclipse.hono.auth.HonoPasswordEncoder;
import org.eclipse.hono.auth.SpringBasedHonoPasswordEncoder;
import org.eclipse.hono.client.SendMessageSampler;
import org.eclipse.hono.config.ProtocolAdapterProperties;
import org.eclipse.hono.config.ServiceConfigProperties;
import org.eclipse.hono.deviceregistry.server.DeviceRegistryHttpServer;
import org.eclipse.hono.deviceregistry.service.device.AutoProvisioner;
import org.eclipse.hono.deviceregistry.service.device.AutoProvisionerConfigProperties;
import org.eclipse.hono.deviceregistry.service.deviceconnection.MapBasedDeviceConnectionsConfigProperties;
import org.eclipse.hono.deviceregistry.service.tenant.AutowiredTenantInformationService;
import org.eclipse.hono.service.HealthCheckServer;
import org.eclipse.hono.service.http.HttpEndpoint;
import org.eclipse.hono.service.management.credentials.CredentialsManagementService;
import org.eclipse.hono.service.management.credentials.DelegatingCredentialsManagementHttpEndpoint;
import org.eclipse.hono.service.management.device.DelegatingDeviceManagementHttpEndpoint;
import org.eclipse.hono.service.management.device.DeviceManagementService;
import org.eclipse.hono.service.management.tenant.DelegatingTenantManagementHttpEndpoint;
import org.eclipse.hono.service.management.tenant.TenantManagementService;
import org.eclipse.hono.util.Constants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;

import io.opentracing.Tracer;
import io.vertx.core.Vertx;

/**
 * Spring Boot configuration for the implementation of Hono's HTTP based
 * Device Registry Management API using the file based example implementation.
 *
 */
@Configuration
@ConditionalOnProperty(name = "hono.app.type", havingValue = "file", matchIfMissing = true)
public class FileBasedServiceConfig extends AbstractMessagingClientConfig {

    @Autowired
    private Vertx vertx;

    @Autowired
    private Tracer tracer;

    @Autowired
    private HealthCheckServer healthCheckServer;

    //
    //
    // Service implementations
    //
    //

    /**
     * Gets properties for configuring the file based service for managing tenant information.
     *
     * @return The properties.
     */
    @Bean
    @ConfigurationProperties(prefix = "hono.tenant.svc")
    public FileBasedTenantsConfigProperties tenantsProperties() {
        return new FileBasedTenantsConfigProperties();
    }

    /**
     * Creates an instance of the file based service for managing tenant information.
     *
     * @return The service.
     */
    @Bean
    public FileBasedTenantService tenantService() {
        return new FileBasedTenantService(vertx);
    }

    /**
     * Gets properties for configuring the file based service for managing device registration information.
     *
     * @return The properties.
     */
    @Bean
    @ConfigurationProperties(prefix = "hono.registry.svc")
    public FileBasedRegistrationConfigProperties registrationProperties() {
        return new FileBasedRegistrationConfigProperties();
    }

    /**
     * Gets properties for configuring the in-memory <em>Device Connection</em> service.
     * <p>
     * The maximum number of devices per tenant is copied from the configuration for the
     * Device Registration service in order to use the same capacity limits.
     *
     * @return The properties.
     */
    @Bean
    public MapBasedDeviceConnectionsConfigProperties deviceConnectionProperties() {
        final MapBasedDeviceConnectionsConfigProperties props = new MapBasedDeviceConnectionsConfigProperties();
        props.setMaxDevicesPerTenant(registrationProperties().getMaxDevicesPerTenant());
        return props;
    }

    /**
     * Gets properties for configuring the file based service for managing credentials.
     *
     * @return The properties.
     */
    @Bean
    @ConfigurationProperties(prefix = "hono.credentials.svc")
    public FileBasedCredentialsConfigProperties credentialsProperties() {
        return new FileBasedCredentialsConfigProperties();
    }

    /**
     * Exposes a password encoder to use for encoding clear text passwords
     * and for matching password hashes.
     *
     * @return The encoder.
     */
    @Bean
    public HonoPasswordEncoder passwordEncoder() {
        return new SpringBasedHonoPasswordEncoder(credentialsProperties().getMaxBcryptCostFactor());
    }

    /**
     * Gets properties for configuring gateway based auto-provisioning.
     *
     * @return The properties.
     */
    @Bean
    @ConfigurationProperties(prefix = "hono.registry.autoprovisioning")
    public AutoProvisionerConfigProperties autoProvisionerConfigProperties() {
        return new AutoProvisionerConfigProperties();
    }

    @Override
    protected String getAdapterName() {
        return "device-registry";
    }

    /**
     * Creates an instance of the file based service for managing device registration information
     * and credentials.
     *
     * @return The service.
     */
    @Bean
    public FileBasedDeviceBackend deviceBackend() {

        final FileBasedRegistrationService registrationService = new FileBasedRegistrationService(vertx);
        registrationService.setConfig(registrationProperties());

        final var tenantInformationService = new AutowiredTenantInformationService();
        tenantInformationService.setService(tenantService());

        final AutoProvisioner autoProvisioner = new AutoProvisioner();
        autoProvisioner.setDeviceManagementService(registrationService);
        autoProvisioner.setVertx(vertx);
        autoProvisioner.setTracer(tracer);
        autoProvisioner.setTenantInformationService(tenantInformationService);
        autoProvisioner.setMessagingClients(messagingClients(SendMessageSampler.Factory.noop(), tracer, vertx, new ProtocolAdapterProperties()));
        autoProvisioner.setConfig(autoProvisionerConfigProperties());

        registrationService.setAutoProvisioner(autoProvisioner);

        final FileBasedCredentialsService credentialsService = new FileBasedCredentialsService(
                vertx,
                credentialsProperties(),
                passwordEncoder());

        return new FileBasedDeviceBackend(registrationService, credentialsService);
    }

    //
    //
    // HTTP endpoints
    //
    // The endpoints for Device Registry Management API are only meaningful if we don't use the dummy
    // implementations.
    //

    /**
     * A Spring Boot condition that checks if the deprecated property name prefix
     * <em>hono.registry.rest</em> is being used for configuring the registry's
     * HTTP endpoint.
     */
    public static class DeprecatedEndpointConfigCondition implements Condition {

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean matches(final ConditionContext context, final AnnotatedTypeMetadata metadata) {
            return propertiesUsed(context.getEnvironment(), "rest");
        }

        private boolean propertiesUsed(final Environment env, final String type) {
            return env.containsProperty(String.format("hono.registry.%s.keyPath", type)) ||
            env.containsProperty(String.format("hono.registry.%s.keyStorePath", type)) ||
            env.containsProperty(String.format("hono.registry.%s.insecurePortEnabled", type)) ||
            env.containsProperty(String.format("hono.registry.%s.insecurePort", type)) ||
            env.containsProperty(String.format("hono.registry.%s.insecurePortBindAddress", type));
        }
    }

    /**
     * Gets properties for configuring the HTTP based Device Registry Management endpoint.
     * <p>
     * Uses the deprecated prefix <em>hono.registry.rest</em> for backward compatibility.
     *
     * @return The properties.
     */
    @Bean(name = "deprecated-http-config")
    @Qualifier(Constants.QUALIFIER_HTTP)
    @ConfigurationProperties(prefix = "hono.registry.rest")
    @Conditional(DeprecatedEndpointConfigCondition.class)
    public ServiceConfigProperties httpServerPropertiesDeprecated() {
        return new ServiceConfigProperties();
    }

    /**
     * Gets properties for configuring the HTTP based Device Registry Management endpoint.
     *
     * @return The properties.
     */
    @Bean
    @Qualifier(Constants.QUALIFIER_HTTP)
    @ConfigurationProperties(prefix = "hono.registry.http")
    @ConditionalOnMissingBean(name = "deprecated-http-config")
    public ServiceConfigProperties httpServerProperties() {
        return new ServiceConfigProperties();
    }

    /**
     * Creates a new server for exposing the HTTP based Device Registry Management API
     * endpoints.
     *
     * @return The server.
     */
    @Bean
    public DeviceRegistryHttpServer httpServer() {
        return new DeviceRegistryHttpServer();
    }

    /**
     * Creates a new instance of an HTTP protocol handler for the <em>tenants</em> resources
     * of Hono's Device Registry Management API's.
     *
     * @param service The service instance to delegate to.
     * @return The handler.
     */
    @Bean
    public HttpEndpoint tenantHttpEndpoint(final TenantManagementService service) {
        return new DelegatingTenantManagementHttpEndpoint<TenantManagementService>(vertx, service);
    }

    /**
     * Creates a new instance of an HTTP protocol handler for the <em>devices</em> resources
     * of Hono's Device Registry Management API's.
     *
     * @param service The service instance to delegate to.
     * @return The handler.
     */
    @Bean
    public HttpEndpoint deviceHttpEndpoint(final DeviceManagementService service) {
        return new DelegatingDeviceManagementHttpEndpoint<DeviceManagementService>(vertx, service);
    }

    /**
     * Creates a new instance of an HTTP protocol handler for the <em>credentials</em> resources
     * of Hono's Device Registry Management API's.
     *
     * @param service The service instance to delegate to.
     * @return The handler.
     */
    @Bean
    public HttpEndpoint credentialsHttpEndpoint(final CredentialsManagementService service) {
        return new DelegatingCredentialsManagementHttpEndpoint<CredentialsManagementService>(vertx, service);
    }
}
