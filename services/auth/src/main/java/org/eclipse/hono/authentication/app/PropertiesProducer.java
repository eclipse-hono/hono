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


package org.eclipse.hono.authentication.app;

import org.eclipse.hono.authentication.file.FileBasedAuthenticationServiceConfigProperties;
import org.eclipse.hono.authentication.file.FileBasedAuthenticationServiceOptions;
import org.eclipse.hono.config.ServiceConfigProperties;
import org.eclipse.hono.config.ServiceOptions;
import org.eclipse.hono.service.auth.AuthTokenFactory;
import org.eclipse.hono.service.auth.JjwtBasedAuthTokenFactory;

import io.smallrye.config.ConfigMapping;
import io.vertx.core.Vertx;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

/**
 * A producer for the Authentication Server's JWK resource.
 *
 */
@ApplicationScoped
public final class PropertiesProducer {

    @Produces
    @Singleton
    FileBasedAuthenticationServiceConfigProperties properties(
            final FileBasedAuthenticationServiceOptions options) {
        return new FileBasedAuthenticationServiceConfigProperties(options);
    }

    @Produces
    @Singleton
    ServiceConfigProperties serviceConfigProperties(
            @ConfigMapping(prefix = "hono.auth.amqp")
            final ServiceOptions options) {
        return new ServiceConfigProperties(options);
    }

    @Produces
    @Singleton
    AuthTokenFactory authTokenFactory(
            final Vertx vertx,
            final ServiceConfigProperties amqpProps,
            final FileBasedAuthenticationServiceConfigProperties serviceConfig) {
        if (!serviceConfig.getSigning().isAppropriateForCreating()
                && amqpProps.getKeyPath() != null
                && amqpProps.getCertPath() != null) {
            // fall back to TLS configuration
            serviceConfig.getSigning().setKeyPath(amqpProps.getKeyPath());
            serviceConfig.getSigning().setCertPath(amqpProps.getCertPath());
        }
        return new JjwtBasedAuthTokenFactory(vertx, serviceConfig.getSigning());
    }
}
