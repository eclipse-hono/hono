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

import static com.google.common.truth.Truth.assertThat;

import java.net.HttpURLConnection;

import org.eclipse.hono.auth.AuthoritiesImpl;
import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.ServerErrorException;
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.util.AuthenticationConstants;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.Vertx;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

/**
 * Tests verifying behavior of {@link EventBusAuthenticationService}.
 */
@ExtendWith(VertxExtension.class)
@TestInstance(Lifecycle.PER_CLASS)
public class EventBusAuthenticationServiceTest {

    private static Vertx vertx;

    private static String secret = "dafhkjsdahfuksahuioahgfqgpsgjkhfdjkg";
    private static AuthTokenFactory authTokenFactory;
    private static AuthTokenValidator authTokenValidator;

    private MessageConsumer<JsonObject> authRequestConsumer;

    /**
     * Sets up vert.x.
     */
    @BeforeAll
    public static void init() {
        vertx = Vertx.vertx();
        final SignatureSupportingConfigProperties props = new SignatureSupportingConfigProperties();
        props.setSharedSecret(secret);
        props.setTokenExpiration(100);
        authTokenFactory = new JjwtBasedAuthTokenFactory(vertx, props);
        authTokenValidator = new JjwtBasedAuthTokenValidator(vertx, props);
    }

    /**
     * Unregisters the consumer for the authentication requests.
     */
    @AfterEach
    public void unregisterAuthRequestConsumer() {
        if (authRequestConsumer != null) {
            authRequestConsumer.unregister();
        }
    }

    /**
     * Verifies that an authentication request is sent via the vert.x event bus and the reply
     * is passed on to the handler of the <code>authenticate</code> method.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testAuthenticateSuccess(final VertxTestContext ctx) {
        final String token = createTestToken();

        authRequestConsumer = vertx.eventBus().consumer(AuthenticationConstants.EVENT_BUS_ADDRESS_AUTHENTICATION_IN, message -> {
            message.reply(AuthenticationConstants.getAuthenticationReply(token));
        });

        final EventBusAuthenticationService eventBusAuthService = new EventBusAuthenticationService(vertx, authTokenValidator);
        eventBusAuthService.authenticate(new JsonObject())
            .onComplete(ctx.succeeding(t -> {
                ctx.verify(() -> assertThat(t.getToken()).isEqualTo(token));
                ctx.completeNow();
            }));
    }

    /**
     * Verifies that the correct exception is given to the handler of the <code>authenticate</code> method in
     * case no event bus handler is registered for handling authentication requests.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testAuthenticateFailureNoHandler(final VertxTestContext ctx) {

        final EventBusAuthenticationService eventBusAuthService = new EventBusAuthenticationService(vertx, authTokenValidator);
        eventBusAuthService.authenticate(new JsonObject())
            .onComplete(ctx.failing(t -> {
                ctx.verify(() -> {
                    assertThat(t).isInstanceOf(ServerErrorException.class);
                    assertThat(((ServiceInvocationException) t).getErrorCode()).isEqualTo(HttpURLConnection.HTTP_INTERNAL_ERROR);
                });
                ctx.completeNow();
            }));
    }

    /**
     * Verifies that an authentication request is sent via the vert.x event bus and the failure reply
     * is passed on to the handler of the <code>authenticate</code> method.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testAuthenticateFailureValidStatusCode(final VertxTestContext ctx) {

        final String failureMessage = "failureMessage";

        authRequestConsumer = vertx.eventBus().consumer(AuthenticationConstants.EVENT_BUS_ADDRESS_AUTHENTICATION_IN, message -> {
            message.fail(HttpURLConnection.HTTP_UNAUTHORIZED, failureMessage);
        });

        final EventBusAuthenticationService eventBusAuthService = new EventBusAuthenticationService(vertx, authTokenValidator);
        eventBusAuthService.authenticate(new JsonObject())
            .onComplete(ctx.failing(t -> {
                ctx.verify(() -> {
                    assertThat(t).isInstanceOf(ClientErrorException.class);
                    assertThat(((ServiceInvocationException) t).getErrorCode()).isEqualTo(HttpURLConnection.HTTP_UNAUTHORIZED);
                    assertThat(t.getMessage()).isEqualTo(failureMessage);
                });
                ctx.completeNow();
            }));
    }

    /**
     * Verifies that an authentication request is sent via the vert.x event bus and the failure reply,
     * containing an invalid error code, is passed on to the handler of the <code>authenticate</code> method
     * as an internal error.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testAuthenticateFailureInvalidStatusCode(final VertxTestContext ctx) {

        final String failureMessage = "failureMessage";

        authRequestConsumer = vertx.eventBus().consumer(AuthenticationConstants.EVENT_BUS_ADDRESS_AUTHENTICATION_IN, message -> {
            message.fail(200, failureMessage);
        });

        final EventBusAuthenticationService eventBusAuthService = new EventBusAuthenticationService(vertx, authTokenValidator);
        eventBusAuthService.authenticate(new JsonObject())
            .onComplete(ctx.failing(t -> {
                ctx.verify(() -> {
                    assertThat(t).isInstanceOf(ServerErrorException.class);
                    assertThat(((ServiceInvocationException) t).getErrorCode()).isEqualTo(HttpURLConnection.HTTP_INTERNAL_ERROR);
                    assertThat(t.getMessage()).isEqualTo(failureMessage);
                });
                ctx.completeNow();
            }));
    }

    private String createTestToken() {
        return authTokenFactory.createToken("authId", new AuthoritiesImpl());
    }
}
