/**
 * Copyright (c) 2016, 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 */
package org.eclipse.hono.service.auth;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

import org.eclipse.hono.auth.Authorities;
import org.eclipse.hono.auth.AuthoritiesImpl;
import org.eclipse.hono.auth.HonoUser;
import org.eclipse.hono.util.AuthenticationConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.json.JsonObject;
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

    /**
     * An authentication service that verifies credentials by means of sending authentication
     * requests to address {@link AuthenticationConstants#EVENT_BUS_ADDRESS_AUTHENTICATION_IN}
     * on the Vert.x Event Bus.
     *
     */
    public static final class EventBusAuthenticationService implements AuthenticationService {

        private static final int AUTH_REQUEST_TIMEOUT_MILLIS = 3000;

        private final Logger log = LoggerFactory.getLogger(EventBusAuthenticationService.class);
        private final Vertx vertx;
        private final AuthTokenHelper tokenValidator;

        /**
         * Creates a new auth service for a Vertx environment.
         * <p>
         * 
         * 
         * @param vertx the Vertx environment to run the factory in.
         * @param validator The object to use for validating auth tokens.
         * @throws NullPointerException if any of the parameters is {@code null}.
         */
        public EventBusAuthenticationService(final Vertx vertx, final AuthTokenHelper validator) {
            this.vertx = Objects.requireNonNull(vertx);
            this.tokenValidator = Objects.requireNonNull(validator);
        }

        @Override
        public void authenticate(final JsonObject authRequest, final Handler<AsyncResult<HonoUser>> authenticationResultHandler) {

            final DeliveryOptions options = new DeliveryOptions().setSendTimeout(AUTH_REQUEST_TIMEOUT_MILLIS);
            vertx.eventBus().send(AuthenticationConstants.EVENT_BUS_ADDRESS_AUTHENTICATION_IN, authRequest, options, reply -> {
                if (reply.succeeded()) {
                    JsonObject result = (JsonObject) reply.result().body();
                    String token = result.getString(AuthenticationConstants.FIELD_TOKEN);
                    log.debug("received token [length: {}] in response to authentication request", token.length());
                    try {
                        Jws<Claims> expandedToken = tokenValidator.expand(result.getString(AuthenticationConstants.FIELD_TOKEN));
                        authenticationResultHandler.handle(Future.succeededFuture(new HonoUserImpl(expandedToken, token)));
                    } catch (JwtException e) {
                        authenticationResultHandler.handle(Future.failedFuture(e));
                    }
                } else {
                    authenticationResultHandler.handle(Future.failedFuture(reply.cause()));
                }
            });

        }
    }

    /**
     * A Hono user wrapping a JSON Web Token.
     *
     */
    public static final class HonoUserImpl implements HonoUser {

        private static Duration expirationLeeway = Duration.ofMinutes(2);
        private String token;
        private Jws<Claims> expandedToken;
        private Authorities authorities;

        private HonoUserImpl(final Jws<Claims> expandedToken, final String token) {
            Objects.requireNonNull(expandedToken);
            Objects.requireNonNull(token);
            if (expandedToken.getBody() == null) {
                throw new IllegalArgumentException("token has no claims");
            }
            this.token = token;
            this.expandedToken = expandedToken;
            this.authorities = AuthoritiesImpl.from(expandedToken.getBody());
        }

        @Override
        public String getName() {
            return expandedToken.getBody().getSubject();
        }

        @Override
        public Authorities getAuthorities() {
            return authorities;
        }

        @Override
        public String getToken() {
            return token;
        }

        @Override
        public boolean isExpired() {
            // we add some leeway to the token's expiration time to account for system clocks not being
            // perfectly in sync
            return !Instant.now().isBefore(getExpirationTime().plus(expirationLeeway));
        }

        @Override
        public Instant getExpirationTime() {
            return expandedToken.getBody().getExpiration().toInstant();
        }
    }
}
