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


package org.eclipse.hono.deviceregistry.file;

import org.eclipse.hono.auth.HonoPasswordEncoder;
import org.eclipse.hono.auth.SpringBasedHonoPasswordEncoder;
import org.eclipse.hono.config.ServiceConfigProperties;
import org.eclipse.hono.deviceregistry.server.DeviceRegistryHttpServer;
import org.eclipse.hono.service.http.HttpEndpoint;
import org.eclipse.hono.service.management.credentials.CredentialsManagementService;
import org.eclipse.hono.service.management.credentials.DelegatingCredentialsManagementHttpEndpoint;
import org.eclipse.hono.service.management.device.DelegatingDeviceManagementHttpEndpoint;
import org.eclipse.hono.service.management.device.DeviceManagementService;
import org.eclipse.hono.service.management.tenant.DelegatingTenantManagementHttpEndpoint;
import org.eclipse.hono.service.management.tenant.TenantManagementService;
import org.eclipse.hono.util.Constants;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.vertx.core.Vertx;

/**
 * Spring Boot configuration for the implementation of Hono's HTTP based
 * Device Registry Management API using the file based example implementation.
 *
 */
@Configuration
@ConditionalOnProperty(name = "hono.app.type", havingValue = "file", matchIfMissing = true)
public class FileBasedServiceConfig {

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
     * @param vertx The vert.x instance to run on.
     * @return The service.
     */
    @Bean
    public FileBasedTenantService tenantService(final Vertx vertx) {
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
        return new SpringBasedHonoPasswordEncoder(credentialsProperties().getMaxBcryptIterations());
    }

    /**
     * Creates an instance of the file based service for managing device registration information
     * and credentials.
     * 
     * @param vertx The vert.x instance to run on.
     * @return The service.
     */
    @Bean
    public FileBasedDeviceBackend deviceBackend(final Vertx vertx) {

        final FileBasedRegistrationService registrationService = new FileBasedRegistrationService(vertx);
        registrationService.setConfig(registrationProperties());

        final FileBasedCredentialsService credentialsService = new FileBasedCredentialsService(vertx);
        credentialsService.setConfig(credentialsProperties());

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
     * Gets properties for configuring the HTTP based Device Registry Management endpoint.
     *
     * @return The properties.
     */
    @Bean
    @Qualifier(Constants.QUALIFIER_REST)
    @ConfigurationProperties(prefix = "hono.registry.rest")
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
     * @param vertx The vert.x instance to run on.
     * @param service The service instance to delegate to.
     * @return The handler.
     */
    @Bean
    public HttpEndpoint tenantHttpEndpoint(final Vertx vertx, final TenantManagementService service) {
        return new DelegatingTenantManagementHttpEndpoint<TenantManagementService>(vertx, service);
    }

    /**
     * Creates a new instance of an HTTP protocol handler for the <em>devices</em> resources
     * of Hono's Device Registry Management API's.
     *
     * @param vertx The vert.x instance to run on.
     * @param service The service instance to delegate to.
     * @return The handler.
     */
    @Bean
    public HttpEndpoint deviceHttpEndpoint(final Vertx vertx, final DeviceManagementService service) {
        return new DelegatingDeviceManagementHttpEndpoint<DeviceManagementService>(vertx, service);
    }

    /**
     * Creates a new instance of an HTTP protocol handler for the <em>credentials</em> resources
     * of Hono's Device Registry Management API's.
     *
     * @param vertx The vert.x instance to run on.
     * @param service The service instance to delegate to.
     * @return The handler.
     */
    @Bean
    public HttpEndpoint credentialsHttpEndpoint(final Vertx vertx, final CredentialsManagementService service) {
        return new DelegatingCredentialsManagementHttpEndpoint<CredentialsManagementService>(vertx, service);
    }
}
