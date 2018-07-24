/*******************************************************************************
 * Copyright (c) 2016, 2018 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/

package org.eclipse.hono.service.auth.delegating;

import org.eclipse.hono.connection.ConnectionFactory;
import org.eclipse.hono.connection.impl.ConnectionFactoryImpl;
import org.eclipse.hono.service.auth.AuthTokenHelper;
import org.eclipse.hono.service.auth.AuthTokenHelperImpl;
import org.eclipse.hono.util.AuthenticationConstants;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import io.vertx.core.Vertx;

/**
 * Spring Boot configuration for the {@code DelegatingAuthenticationService}.
 * <p>
 * In particular, this class exposes a {@link AuthenticationServerClientConfigProperties} instance
 * configured by means of system properties from the <em>hono.auth</em> namespace.
 *
 */
@Configuration
@Profile("!authentication-impl")
public class DelegatingAuthenticationServiceConfig {

    /**
     * Exposes configuration properties for using a remote {@code AuthenticationService}.
     * 
     * @return The properties.
     */
    @Bean
    @ConfigurationProperties(prefix = "hono.auth")
    @Qualifier(AuthenticationConstants.QUALIFIER_AUTHENTICATION)
    public AuthenticationServerClientConfigProperties authenticationServiceClientProperties() {
        return new AuthenticationServerClientConfigProperties();
    }

    /**
     * Exposes a factory for connections to the authentication service
     * as a Spring bean.
     * 
     * @param vertx The Vertx instance to use.
     * @return The connection factory.
     */
    @Bean
    @Qualifier(AuthenticationConstants.QUALIFIER_AUTHENTICATION)
    public ConnectionFactory authenticationServiceConnectionFactory(final Vertx vertx) {
        return new ConnectionFactoryImpl(vertx, authenticationServiceClientProperties());
    }

    /**
     * Creates a helper for validating JWTs asserting a client's identity and authorities.
     * 
     * @param vertx The Vertx instance to use.
     * @return The bean.
     */
    @Bean
    @Qualifier(AuthenticationConstants.QUALIFIER_AUTHENTICATION)
    public AuthTokenHelper tokenValidator(final Vertx vertx) {
        final AuthenticationServerClientConfigProperties authClientProps = authenticationServiceClientProperties();
        if (!authClientProps.getValidation().isAppropriateForValidating() && authClientProps.getCertPath() != null) {
            // fall back to TLS configuration
            authClientProps.getValidation().setCertPath(authClientProps.getCertPath());
        }
        return AuthTokenHelperImpl.forValidating(vertx, authClientProps.getValidation());
    }
}
