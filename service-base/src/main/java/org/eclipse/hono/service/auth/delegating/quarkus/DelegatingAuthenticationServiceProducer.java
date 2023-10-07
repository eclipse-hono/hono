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


package org.eclipse.hono.service.auth.delegating.quarkus;

import org.eclipse.hono.client.amqp.connection.ConnectionFactory;
import org.eclipse.hono.service.auth.AuthenticationService;
import org.eclipse.hono.service.auth.HonoSaslAuthenticatorFactory;
import org.eclipse.hono.service.auth.JjwtBasedAuthTokenValidator;
import org.eclipse.hono.service.auth.delegating.AuthenticationServerClientConfigProperties;
import org.eclipse.hono.service.auth.delegating.AuthenticationServerClientOptions;
import org.eclipse.hono.service.auth.delegating.DelegatingAuthenticationService;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.quarkus.arc.properties.IfBuildProperty;
import io.smallrye.config.ConfigMapping;
import io.smallrye.health.api.HealthRegistry;
import io.vertx.core.Vertx;
import io.vertx.proton.sasl.ProtonSaslAuthenticatorFactory;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

/**
 * A producer of an application scoped {@link AuthenticationService} which delegates
 * validation of credentials to a Hono Authentication Server.
 *
 */
@ApplicationScoped
@IfBuildProperty(name = "hono.auth", stringValue = "delegating")
public class DelegatingAuthenticationServiceProducer {

    private static final Logger LOG = LoggerFactory.getLogger(DelegatingAuthenticationServiceProducer.class);

    @Produces
    @Singleton
    AuthenticationServerClientConfigProperties authenticationServerClientProperties(
            @ConfigMapping(prefix = "hono.auth")
            final AuthenticationServerClientOptions options) {
        final var props = new AuthenticationServerClientConfigProperties(options);
        props.setServerRoleIfUnknown("Authentication Server");
        return props;
    }

    @Produces
    @Singleton
    AuthenticationService delegatingAuthenticationService(
            final Vertx vertx,
            final AuthenticationServerClientConfigProperties authServerClientConfig) {

        LOG.info("creating {} instance", DelegatingAuthenticationService.class.getName());
        final var service = new DelegatingAuthenticationService();
        service.setConfig(authServerClientConfig);
        service.setConnectionFactory(ConnectionFactory.newConnectionFactory(vertx, authServerClientConfig));
        return service;
    }

    @Produces
    @Singleton
    ProtonSaslAuthenticatorFactory honoSaslAuthenticatorFactory(
            final Vertx vertx,
            final AuthenticationServerClientConfigProperties authServerClientConfig,
            final AuthenticationService authenticationService,
            @Readiness
            final HealthRegistry readinessChecks) {

        final var authTokenValidator = new JjwtBasedAuthTokenValidator(vertx, authServerClientConfig);
        readinessChecks.register(() -> {
            return HealthCheckResponse.builder()
                    .name("AuthTokenValidator")
                    .status(authTokenValidator.hasValidatingKey())
                    .build();
        });
        return new HonoSaslAuthenticatorFactory(vertx, authTokenValidator, authenticationService);
    }
}
