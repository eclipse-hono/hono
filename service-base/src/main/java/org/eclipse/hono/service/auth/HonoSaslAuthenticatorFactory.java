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

import java.util.Objects;

import org.eclipse.hono.auth.Authorities;
import org.eclipse.hono.auth.HonoUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

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
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    @Autowired
    public HonoSaslAuthenticatorFactory(final Vertx vertx) {
        this(new EventBusAuthenticationService(vertx));
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

        private static final int AUTH_REQUEST_TIMEOUT_MILLIS = 300;

        private final Logger log = LoggerFactory.getLogger(EventBusAuthenticationService.class);
        private final Vertx vertx;

        /**
         * Creates a new auth service for a Vertx environment.
         * <p>
         * 
         * 
         * @param vertx the Vertx environment to run the factory in.
         * @throws NullPointerException if any of the parameters is {@code null}.
         */
        public EventBusAuthenticationService(final Vertx vertx) {
            this.vertx = Objects.requireNonNull(vertx);
        }

        @Override
        public void authenticate(final JsonObject authRequest, final Handler<AsyncResult<HonoUser>> authenticationResultHandler) {

            final DeliveryOptions options = new DeliveryOptions().setSendTimeout(AUTH_REQUEST_TIMEOUT_MILLIS);
            vertx.eventBus().send(AuthenticationConstants.EVENT_BUS_ADDRESS_AUTHENTICATION_IN, authRequest, options, reply -> {
                if (reply.succeeded()) {
                    JsonObject result = (JsonObject) reply.result().body();
                    log.debug("received result of successful authentication request: {}", result);
                    final String authorizationId = result.getString(AuthenticationConstants.FIELD_AUTHORIZATION_ID);
                    HonoUser user = new HonoUser() {

                        @Override
                        public String getName() {
                            return authorizationId;
                        }

                        @Override
                        public Authorities getAuthorities() {
                            return null;
                        }

                        @Override
                        public String getToken() {
                            return null;
                        }
                    };
                    authenticationResultHandler.handle(Future.succeededFuture(user));
                } else {
                    authenticationResultHandler.handle(Future.failedFuture(reply.cause()));
                }
            });
        }
    }
}
