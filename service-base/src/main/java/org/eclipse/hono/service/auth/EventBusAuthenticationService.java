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

import java.net.HttpURLConnection;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

import org.eclipse.hono.auth.Authorities;
import org.eclipse.hono.auth.AuthoritiesImpl;
import org.eclipse.hono.auth.HonoUser;
import org.eclipse.hono.client.ServerErrorException;
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.client.util.StatusCodeMapper;
import org.eclipse.hono.util.AuthenticationConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.ReplyException;
import io.vertx.core.json.JsonObject;
import io.vertx.core.tracing.TracingPolicy;

/**
 * An authentication service that verifies credentials by means of sending authentication
 * requests to address {@link AuthenticationConstants#EVENT_BUS_ADDRESS_AUTHENTICATION_IN}
 * on the Vert.x Event Bus.
 *
 */
public final class EventBusAuthenticationService implements AuthenticationService {

    private static final int AUTH_REQUEST_TIMEOUT_MILLIS = 3000;

    private final Logger log = LoggerFactory.getLogger(EventBusAuthenticationService.class);
    private final Vertx vertx;
    private final AuthTokenValidator tokenValidator;
    private final String[] supportedSASLMechanisms;

    /**
     * Creates a new auth service for a Vertx environment.
     * <p>
     * Uses EXTERNAL and PLAIN as supported SASL mechanisms.
     *
     * @param vertx the Vertx environment to run the factory in.
     * @param validator The object to use for validating auth tokens.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    public EventBusAuthenticationService(final Vertx vertx, final AuthTokenValidator validator) {
        this(vertx, validator, AbstractHonoAuthenticationService.DEFAULT_SASL_MECHANISMS);
    }

    /**
     * Creates a new auth service for a Vertx environment.
     *
     * @param vertx the Vertx environment to run the factory in.
     * @param validator The object to use for validating auth tokens.
     * @param supportedSASLMechanisms The supported SASL mechanisms.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    public EventBusAuthenticationService(final Vertx vertx, final AuthTokenValidator validator,
            final String[] supportedSASLMechanisms) {
        this.vertx = Objects.requireNonNull(vertx);
        this.tokenValidator = Objects.requireNonNull(validator);
        this.supportedSASLMechanisms = Objects.requireNonNull(supportedSASLMechanisms);
    }

    @Override
    public String[] getSupportedSaslMechanisms() {
        return supportedSASLMechanisms;
    }

    @Override
    public Future<HonoUser> authenticate(final JsonObject authRequest) {

        final Promise<HonoUser> result = Promise.promise();
        final DeliveryOptions options = new DeliveryOptions();
        options.setSendTimeout(AUTH_REQUEST_TIMEOUT_MILLIS);
        options.setTracingPolicy(TracingPolicy.IGNORE);
        vertx.eventBus().request(AuthenticationConstants.EVENT_BUS_ADDRESS_AUTHENTICATION_IN, authRequest, options, reply -> {
            if (reply.succeeded()) {
                final JsonObject resultBody = (JsonObject) reply.result().body();
                final String token = resultBody.getString(AuthenticationConstants.FIELD_TOKEN);
                log.debug("received token [length: {}] in response to authentication request", token.length());
                try {
                    final Jws<Claims> expandedToken = tokenValidator.expand(resultBody.getString(AuthenticationConstants.FIELD_TOKEN));
                    result.complete(new HonoUserImpl(expandedToken, token));
                } catch (final JwtException e) {
                    result.fail(new ServerErrorException(HttpURLConnection.HTTP_INTERNAL_ERROR, e));
                }
            } else {
                final ServiceInvocationException resultException;
                if (reply.cause() instanceof ReplyException) {
                    switch (((ReplyException) reply.cause()).failureType()) {
                        case TIMEOUT:
                            log.debug("timeout processing authentication request", reply.cause());
                            resultException = new ServerErrorException(HttpURLConnection.HTTP_UNAVAILABLE, reply.cause());
                            break;
                        case NO_HANDLERS:
                            log.debug("could not process authentication request", reply.cause());
                            resultException = new ServerErrorException(HttpURLConnection.HTTP_INTERNAL_ERROR, reply.cause());
                            break;
                        case RECIPIENT_FAILURE:
                            final int statusCode = ((ReplyException) reply.cause()).failureCode();
                            if (statusCode < 400 || statusCode >= 600) {
                                log.error("got illegal status code in authentication response exception: {}", statusCode);
                                resultException = new ServerErrorException(HttpURLConnection.HTTP_INTERNAL_ERROR, reply.cause().getMessage());
                            } else {
                                resultException = StatusCodeMapper.from(statusCode, reply.cause().getMessage());
                            }
                            break;
                        default:
                            resultException = new ServerErrorException(HttpURLConnection.HTTP_INTERNAL_ERROR, reply.cause());
                    }
                } else {
                    resultException = new ServerErrorException(HttpURLConnection.HTTP_INTERNAL_ERROR, reply.cause());
                }
                result.fail(resultException);
            }
        });

        return result.future();
    }

    /**
     * A Hono user wrapping a JSON Web Token.
     *
     */
    public static final class HonoUserImpl implements HonoUser {

        private static Duration expirationLeeway = Duration.ofMinutes(2);
        private final String token;
        private final Jws<Claims> expandedToken;
        private final Authorities authorities;

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
