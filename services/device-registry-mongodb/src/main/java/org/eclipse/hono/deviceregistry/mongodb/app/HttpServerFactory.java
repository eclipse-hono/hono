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

package org.eclipse.hono.deviceregistry.mongodb.app;

import org.eclipse.hono.deviceregistry.app.AbstractHttpServerFactory;
import org.eclipse.hono.deviceregistry.mongodb.config.MongoDbBasedHttpServiceConfigOptions;
import org.eclipse.hono.deviceregistry.mongodb.utils.MongoAuthenticationImpl;
import org.eclipse.hono.deviceregistry.server.DeviceRegistryHttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.quarkus.mongodb.reactive.ReactiveMongoClient;
import io.vertx.core.Vertx;
import io.vertx.ext.web.handler.BasicAuthHandler;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * A factory for creating Device Registry Management API endpoints.
 */
@ApplicationScoped
public class HttpServerFactory extends AbstractHttpServerFactory {

    private static final Logger LOG = LoggerFactory.getLogger(HttpServerFactory.class);

    @Inject
    Vertx vertx;

    @Inject
    ReactiveMongoClient mongoClient;

    @Inject
    MongoDbBasedHttpServiceConfigOptions httpServerOptions;

    @Override
    protected void customizeServer(final DeviceRegistryHttpServer server) {
        if (httpServerOptions.commonOptions().authenticationRequired()) {
            final var authOptions = httpServerOptions.auth();
            LOG.debug(
        "creating AuthenticationHandler guarding access to registry's HTTP endpoint using configuration:{}{}",
                System.lineSeparator(), authOptions);
            server.setAuthHandler(BasicAuthHandler.create(
                        new MongoAuthenticationImpl(vertx, mongoClient, authOptions),
                        httpServerOptions.commonOptions().realm()));
        }
    }
}
