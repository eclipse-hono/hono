/**
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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


package org.eclipse.hono.deviceregistry.jdbc.quarkus;

import java.util.Objects;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.eclipse.hono.deviceregistry.jdbc.config.DeviceServiceOptions;
import org.eclipse.hono.deviceregistry.server.DeviceRegistryHttpServer;
import org.eclipse.hono.service.HealthCheckServer;
import org.eclipse.hono.service.http.HttpEndpoint;
import org.eclipse.hono.service.http.HttpServiceConfigOptions;
import org.eclipse.hono.service.http.HttpServiceConfigProperties;
import org.eclipse.hono.service.management.credentials.CredentialsManagementService;
import org.eclipse.hono.service.management.credentials.DelegatingCredentialsManagementHttpEndpoint;
import org.eclipse.hono.service.management.device.DelegatingDeviceManagementHttpEndpoint;
import org.eclipse.hono.service.management.device.DeviceManagementService;
import org.eclipse.hono.service.management.tenant.DelegatingTenantManagementHttpEndpoint;
import org.eclipse.hono.service.management.tenant.TenantManagementService;

import io.opentracing.Tracer;
import io.smallrye.config.ConfigMapping;
import io.vertx.core.Vertx;

/**
 * A factory for creating Device Registry Management API endpoints.
 *
 */
@ApplicationScoped
public class HttpServerFactory {

    @Inject
    Vertx vertx;

    @Inject
    Tracer tracer;

    @Inject
    DeviceServiceOptions deviceServiceOptions;

    @Inject
    TenantManagementService tenantManagementService;

    @Inject
    DeviceManagementService deviceManagementService;

    @Inject
    CredentialsManagementService credentialsManagementService;

    @Inject
    HealthCheckServer healthCheckServer;

    private final HttpServiceConfigProperties httpServerProperties;

    /**
     * Creates a new factory.
     *
     * @param endpointOptions The HTTP endpoint configuration.
     */
    public HttpServerFactory(
            @ConfigMapping(prefix = "hono.registry.http")
            final HttpServiceConfigOptions endpointOptions) {
        Objects.requireNonNull(endpointOptions);
        this.httpServerProperties = new HttpServiceConfigProperties(endpointOptions);
    }

    /**
     * Creates a server with an HTTP endpoint exposing Hono's Device Registry Management API.
     *
     * @return The server.
     */
    public DeviceRegistryHttpServer newServer() {
        final var server = new DeviceRegistryHttpServer();
        server.setConfig(httpServerProperties);
        server.setHealthCheckServer(healthCheckServer);
        server.setTracer(tracer);
        server.addEndpoint(tenantHttpEndpoint());
        server.addEndpoint(deviceHttpEndpoint());
        server.addEndpoint(credentialsHttpEndpoint());

        return server;
    }

    /**
     * Creates a new instance of an HTTP protocol handler for the <em>tenants</em> resources
     * of Hono's Device Registry Management API's.
     *
     * @return The handler.
     */
    private HttpEndpoint tenantHttpEndpoint() {
        final var endpoint = new DelegatingTenantManagementHttpEndpoint<TenantManagementService>(
                vertx,
                tenantManagementService);
        endpoint.setConfiguration(httpServerProperties);
        endpoint.setTracer(tracer);
        return endpoint;
    }

    /**
     * Creates a new instance of an HTTP protocol handler for the <em>devices</em> resources
     * of Hono's Device Registry Management API's.
     *
     * @return The handler.
     */
    private HttpEndpoint deviceHttpEndpoint() {
        final var endpoint = new DelegatingDeviceManagementHttpEndpoint<DeviceManagementService>(
                vertx,
                deviceManagementService);
        endpoint.setConfiguration(httpServerProperties);
        endpoint.setTracer(tracer);
        return endpoint;
    }

    /**
     * Creates a new instance of an HTTP protocol handler for the <em>credentials</em> resources
     * of Hono's Device Registry Management API's.
     *
     * @return The handler.
     */
    private HttpEndpoint credentialsHttpEndpoint() {
        final var endpoint = new DelegatingCredentialsManagementHttpEndpoint<CredentialsManagementService>(
                vertx,
                credentialsManagementService);
        endpoint.setConfiguration(httpServerProperties);
        endpoint.setTracer(tracer);
        return endpoint;
    }
}
