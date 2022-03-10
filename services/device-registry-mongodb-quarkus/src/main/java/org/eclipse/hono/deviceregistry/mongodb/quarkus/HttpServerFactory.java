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


package org.eclipse.hono.deviceregistry.mongodb.quarkus;

import java.util.Objects;
import java.util.Optional;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.eclipse.hono.deviceregistry.mongodb.config.MongoDbBasedHttpServiceConfigOptions;
import org.eclipse.hono.deviceregistry.mongodb.config.MongoDbBasedHttpServiceConfigProperties;
import org.eclipse.hono.deviceregistry.server.DeviceRegistryHttpServer;
import org.eclipse.hono.service.HealthCheckServer;
import org.eclipse.hono.service.http.HttpEndpoint;
import org.eclipse.hono.service.management.credentials.CredentialsManagementService;
import org.eclipse.hono.service.management.credentials.DelegatingCredentialsManagementHttpEndpoint;
import org.eclipse.hono.service.management.device.DelegatingDeviceManagementHttpEndpoint;
import org.eclipse.hono.service.management.device.DeviceManagementService;
import org.eclipse.hono.service.management.tenant.DelegatingTenantManagementHttpEndpoint;
import org.eclipse.hono.service.management.tenant.TenantManagementService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opentracing.Tracer;
import io.vertx.core.Vertx;
import io.vertx.ext.auth.User;
import io.vertx.ext.auth.mongo.HashSaltStyle;
import io.vertx.ext.auth.mongo.MongoAuthenticationOptions;
import io.vertx.ext.auth.mongo.impl.DefaultHashStrategy;
import io.vertx.ext.auth.mongo.impl.MongoAuthenticationImpl;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.web.handler.BasicAuthHandler;

/**
 * A factory for creating Device Registry Management API endpoints.
 *
 */
@ApplicationScoped
public class HttpServerFactory {

    private static final Logger LOG = LoggerFactory.getLogger(HttpServerFactory.class);

    @Inject
    Vertx vertx;

    @Inject
    Tracer tracer;

    @Inject
    MongoClient mongoClient;

    @Inject
    TenantManagementService tenantManagementService;

    @Inject
    DeviceManagementService deviceManagementService;

    @Inject
    CredentialsManagementService credentialsManagementService;

    @Inject
    HealthCheckServer healthCheckServer;

    private final MongoDbBasedHttpServiceConfigProperties httpServerProperties;

    /**
     * Creates a new factory.
     *
     * @param endpointOptions The HTTP endpoint configuration.
     */
    public HttpServerFactory(final MongoDbBasedHttpServiceConfigOptions endpointOptions) {
        Objects.requireNonNull(endpointOptions);
        this.httpServerProperties = new MongoDbBasedHttpServiceConfigProperties(endpointOptions);
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

        if (httpServerProperties.isAuthenticationRequired()) {
            final var authConfig = httpServerProperties.getAuth();
            LOG.debug("creating AuthenticationHandler guarding access to registry's HTTP endpoint using configuration:{}{}",
                    System.lineSeparator(), authConfig);
            final var mongoAuthOptions = new MongoAuthenticationOptions();
            mongoAuthOptions.setCollectionName(authConfig.getCollectionName());
            mongoAuthOptions.setUsernameField(authConfig.getUsernameField());
            mongoAuthOptions.setPasswordField(authConfig.getPasswordField());
            final var hashStrategy = new DefaultHashStrategy() {
                @Override
                public String computeHash(final String password, final User user) {
                    final String hash = super.computeHash(password, user);
                    // apply workaround for https://github.com/vert-x3/vertx-auth/issues/534 TODO remove after update to vert.x 4.2.5
                    if (getSaltStyle() != HashSaltStyle.NO_SALT) {
                        return hash.toUpperCase();
                    }
                    return hash;
                }
            };
            Optional.ofNullable(authConfig.getHashAlgorithm())
                .ifPresent(hashStrategy::setAlgorithm);
            Optional.ofNullable(authConfig.getSaltStyle())
                .ifPresent(hashStrategy::setSaltStyle);
            final var mongoAuth = new MongoAuthenticationImpl(
                    mongoClient,
                    hashStrategy,
                    authConfig.getSaltField(),
                    mongoAuthOptions);
            server.setAuthHandler(BasicAuthHandler.create(
                    mongoAuth,
                    httpServerProperties.getRealm()));
        }

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
