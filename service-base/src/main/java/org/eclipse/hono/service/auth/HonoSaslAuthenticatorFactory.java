/*******************************************************************************
 * Copyright (c) 2016, 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.hono.service.auth;

import java.util.Objects;

import org.eclipse.hono.util.AuthenticationConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import io.vertx.core.Vertx;
import io.vertx.proton.sasl.ProtonSaslAuthenticator;
import io.vertx.proton.sasl.ProtonSaslAuthenticatorFactory;

/**
 * A factory for objects performing SASL authentication on an AMQP connection.
 */
@Component
public final class HonoSaslAuthenticatorFactory implements ProtonSaslAuthenticatorFactory {

    private final AuthenticationService authenticationService;

    /**
     * Creates a new factory for a Vertx environment.
     * <p>
     * Verifies credentials by means of sending authentication requests to address
     * {@link AuthenticationConstants#EVENT_BUS_ADDRESS_AUTHENTICATION_IN} on the Vert.x
     * Event Bus.
     * 
     * @param vertx the Vertx environment to run the factory in.
     * @param validator The object to use for validating auth tokens.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    @Autowired
    public HonoSaslAuthenticatorFactory(final Vertx vertx, @Qualifier(AuthenticationConstants.QUALIFIER_AUTHENTICATION) final AuthTokenHelper validator) {
        this(new EventBusAuthenticationService(vertx, validator));
    }

    /**
     * Creates a new factory using a specific authentication service instance.
     * 
     * @param authService The object to return on invocations of {@link #create()}.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    public HonoSaslAuthenticatorFactory(final AuthenticationService authService) {
        this.authenticationService = Objects.requireNonNull(authService);
    }

    @Override
    public ProtonSaslAuthenticator create() {
        return new HonoSaslAuthenticator(authenticationService);
    }

}
