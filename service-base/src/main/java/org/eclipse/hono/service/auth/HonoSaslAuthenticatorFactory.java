/*******************************************************************************
 * Copyright (c) 2016, 2022 Contributors to the Eclipse Foundation
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
import java.util.function.Consumer;

import org.eclipse.hono.service.auth.AuthenticationService.AuthenticationAttemptOutcome;

import io.vertx.core.Vertx;
import io.vertx.proton.sasl.ProtonSaslAuthenticator;
import io.vertx.proton.sasl.ProtonSaslAuthenticatorFactory;

/**
 * A factory for objects performing SASL authentication on an AMQP connection.
 */
public final class HonoSaslAuthenticatorFactory implements ProtonSaslAuthenticatorFactory {

    private final AuthenticationService authenticationService;
    private final Consumer<AuthenticationAttemptOutcome> authenticationAttemptMeter;

    /**
     * Creates a new factory for a Vertx environment.
     * <p>
     * Verifies credentials by means of sending authentication requests to address
     * {@link org.eclipse.hono.util.AuthenticationConstants#EVENT_BUS_ADDRESS_AUTHENTICATION_IN} on the Vert.x
     * Event Bus.
     *
     * @param vertx the Vertx environment to run the factory in.
     * @param validator The object to use for validating auth tokens.
     * @param actualAuthenticationService The authentication service that will handle the authentication requests sent
     *            on the Vert.x event bus. Used for determining the supported SASL mechanisms.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    public HonoSaslAuthenticatorFactory(
            final Vertx vertx,
            final AuthTokenValidator validator,
            final AuthenticationService actualAuthenticationService) {
        this(new EventBusAuthenticationService(vertx, validator,
                actualAuthenticationService.getSupportedSaslMechanisms()));
    }

    /**
     * Creates a new factory for a Vertx environment.
     * <p>
     * Verifies credentials by means of sending authentication requests to address
     * {@link org.eclipse.hono.util.AuthenticationConstants#EVENT_BUS_ADDRESS_AUTHENTICATION_IN} on the Vert.x
     * Event Bus.
     *
     * @param vertx the Vertx environment to run the factory in.
     * @param validator The object to use for validating auth tokens.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    public HonoSaslAuthenticatorFactory(final Vertx vertx, final AuthTokenValidator validator) {
        this(new EventBusAuthenticationService(vertx, validator));
    }

    /**
     * Creates a new factory using a specific authentication service instance.
     *
     * @param authService The object to return on invocations of {@link #create()}.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    public HonoSaslAuthenticatorFactory(final AuthenticationService authService) {
        this(authService, null);
    }

    /**
     * Creates a new factory using a specific authentication service instance.
     *
     * @param authService The object to return on invocations of {@link #create()}.
     * @param authenticationAttemptMeter A consumer for reporting the outcome of authentication attempts
     *                               or {@code null}, if authentication attempts should not be metered.
     * @throws NullPointerException if authentication service is {@code null}.
     */
    public HonoSaslAuthenticatorFactory(
            final AuthenticationService authService,
            final Consumer<AuthenticationAttemptOutcome> authenticationAttemptMeter) {
        this.authenticationService = Objects.requireNonNull(authService);
        this.authenticationAttemptMeter = authenticationAttemptMeter;
    }

    @Override
    public ProtonSaslAuthenticator create() {
        return new HonoSaslAuthenticator(authenticationService, authenticationAttemptMeter);
    }
}
