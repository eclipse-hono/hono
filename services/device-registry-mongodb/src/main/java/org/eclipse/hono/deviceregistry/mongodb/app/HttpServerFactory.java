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


package org.eclipse.hono.deviceregistry.mongodb.app;

import java.util.Optional;

import org.eclipse.hono.deviceregistry.app.AbstractHttpServerFactory;
import org.eclipse.hono.deviceregistry.mongodb.config.MongoDbBasedHttpServiceConfigOptions;
import org.eclipse.hono.deviceregistry.mongodb.config.MongoDbBasedHttpServiceConfigProperties;
import org.eclipse.hono.deviceregistry.server.DeviceRegistryHttpServer;
import org.eclipse.hono.service.http.HttpServiceConfigProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.ext.auth.mongo.MongoAuthenticationOptions;
import io.vertx.ext.auth.mongo.impl.DefaultHashStrategy;
import io.vertx.ext.auth.mongo.impl.MongoAuthenticationImpl;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.web.handler.BasicAuthHandler;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * A factory for creating Device Registry Management API endpoints.
 *
 */
@ApplicationScoped
public class HttpServerFactory extends AbstractHttpServerFactory {

    private static final Logger LOG = LoggerFactory.getLogger(HttpServerFactory.class);

    @Inject
    MongoClient mongoClient;

    private MongoDbBasedHttpServiceConfigProperties httpServerProperties;

    @Inject
    void setHttpServerProperties(final MongoDbBasedHttpServiceConfigOptions endpointOptions) {
        this.httpServerProperties = new MongoDbBasedHttpServiceConfigProperties(endpointOptions);
    }

    @Override
    protected final HttpServiceConfigProperties getHttpServerProperties() {
        return httpServerProperties;
    }

    @Override
    protected void customizeServer(final DeviceRegistryHttpServer server) {
        if (httpServerProperties.isAuthenticationRequired()) {
            final var authConfig = httpServerProperties.getAuth();
            LOG.debug("creating AuthenticationHandler guarding access to registry's HTTP endpoint using configuration:{}{}",
                    System.lineSeparator(), authConfig);
            final var mongoAuthOptions = new MongoAuthenticationOptions();
            mongoAuthOptions.setCollectionName(authConfig.getCollectionName());
            mongoAuthOptions.setUsernameField(authConfig.getUsernameField());
            mongoAuthOptions.setPasswordField(authConfig.getPasswordField());
            final var hashStrategy = new DefaultHashStrategy();
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
    }
}
