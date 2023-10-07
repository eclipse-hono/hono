/**
 * Copyright (c) 2022, 2023 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.deviceregistry.app;

import org.eclipse.hono.deviceregistry.server.DeviceRegistryHttpServer;
import org.eclipse.hono.service.HealthCheckServer;
import org.eclipse.hono.service.http.HttpEndpoint;
import org.eclipse.hono.service.http.HttpServiceConfigProperties;
import org.eclipse.hono.service.management.credentials.CredentialsManagementService;
import org.eclipse.hono.service.management.credentials.DelegatingCredentialsManagementHttpEndpoint;
import org.eclipse.hono.service.management.device.DelegatingDeviceManagementHttpEndpoint;
import org.eclipse.hono.service.management.device.DeviceManagementService;
import org.eclipse.hono.service.management.tenant.DelegatingTenantManagementHttpEndpoint;
import org.eclipse.hono.service.management.tenant.TenantManagementService;

import io.opentracing.Tracer;
import io.vertx.core.Vertx;
import jakarta.inject.Inject;

/**
 * A factory base class for creating Device Registry Management API endpoints.
 *
 */
public abstract class AbstractHttpServerFactory {

    @Inject
    Vertx vertx;

    @Inject
    Tracer tracer;

    @Inject
    TenantManagementService tenantManagementService;

    @Inject
    DeviceManagementService deviceManagementService;

    @Inject
    CredentialsManagementService credentialsManagementService;

    @Inject
    HealthCheckServer healthCheckServer;

    /**
     * Gets the HTTP endpoint configuration properties.
     *
     * @return The properties.
     */
    protected abstract HttpServiceConfigProperties getHttpServerProperties();

    /**
     * Creates a server with an HTTP endpoint exposing Hono's Device Registry Management API.
     *
     * @return The server.
     */
    public final DeviceRegistryHttpServer newServer() {
        final var server = new DeviceRegistryHttpServer();
        server.setConfig(getHttpServerProperties());
        server.setHealthCheckServer(healthCheckServer);
        server.setTracer(tracer);
        server.addEndpoint(tenantHttpEndpoint());
        server.addEndpoint(deviceHttpEndpoint());
        server.addEndpoint(credentialsHttpEndpoint());

        customizeServer(server);

        return server;
    }

    /**
     * Customizes the given server instance.
     * <p>
     * This default implementation does nothing. Subclasses may override this method.
     *
     * @param server The service instance.
     */
    protected void customizeServer(final DeviceRegistryHttpServer server) {
        // nothing done by default
    }

    /**
     * Creates a new instance of an HTTP protocol handler for the <em>tenants</em> resources
     * of Hono's Device Registry Management API's.
     *
     * @return The handler.
     */
    private HttpEndpoint tenantHttpEndpoint() {
        final var endpoint = new DelegatingTenantManagementHttpEndpoint<>(vertx, tenantManagementService);
        endpoint.setConfiguration(getHttpServerProperties());
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
        final var endpoint = new DelegatingDeviceManagementHttpEndpoint<>(vertx, deviceManagementService);
        endpoint.setConfiguration(getHttpServerProperties());
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
        final var endpoint = new DelegatingCredentialsManagementHttpEndpoint<>(vertx, credentialsManagementService);
        endpoint.setConfiguration(getHttpServerProperties());
        endpoint.setTracer(tracer);
        return endpoint;
    }
}
