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


package org.eclipse.hono.service.auth.delegating.quarkus;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Produces;

import org.eclipse.hono.client.amqp.connection.ConnectionFactory;
import org.eclipse.hono.service.auth.AuthTokenHelperImpl;
import org.eclipse.hono.service.auth.AuthenticationService;
import org.eclipse.hono.service.auth.HonoSaslAuthenticatorFactory;
import org.eclipse.hono.service.auth.delegating.AuthenticationServerClientConfigProperties;
import org.eclipse.hono.service.auth.delegating.DelegatingAuthenticationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.quarkus.arc.properties.IfBuildProperty;
import io.vertx.core.Vertx;
import io.vertx.proton.sasl.ProtonSaslAuthenticatorFactory;

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
    ProtonSaslAuthenticatorFactory honoSaslAuthenticatorFactory(
            final Vertx vertx,
            final AuthenticationServerClientConfigProperties authServerClientConfig,
            final AuthenticationService authenticationService) {

        final var authTokenValidator = AuthTokenHelperImpl.forValidating(vertx, authServerClientConfig.getValidation());
        return new HonoSaslAuthenticatorFactory(vertx, authTokenValidator, authenticationService);
    }
}
