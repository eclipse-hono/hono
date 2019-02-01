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

import static org.eclipse.hono.util.AuthenticationConstants.EVENT_BUS_ADDRESS_AUTHENTICATION_IN;

import java.net.HttpURLConnection;

import org.eclipse.hono.auth.AuthoritiesImpl;
import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.ServerErrorException;
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.config.SignatureSupportingConfigProperties;
import org.eclipse.hono.util.AuthenticationConstants;
import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.vertx.core.Vertx;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;

/**
 * Tests verifying behavior of {@link EventBusAuthenticationService}.
 */
@RunWith(VertxUnitRunner.class)
public class EventBusAuthenticationServiceTest {

    private static Vertx vertx;

    private static String secret = "dafhkjsdahfuksahuioahgfqgpsgjkhfdjkg";
    private static AuthTokenHelper authTokenHelperForSigning;
    private static AuthTokenHelper authTokenHelperForValidating;

    private MessageConsumer<JsonObject> authRequestConsumer;

    /**
     * Sets up vert.x.
     */
    @BeforeClass
    public static void init() {
        vertx = Vertx.vertx();
        final SignatureSupportingConfigProperties props = new SignatureSupportingConfigProperties();
        props.setSharedSecret(secret);
        props.setTokenExpiration(100);
        authTokenHelperForSigning = AuthTokenHelperImpl.forSigning(vertx, props);
        authTokenHelperForValidating = AuthTokenHelperImpl.forValidating(vertx, props);
    }

    /**
     * Unregisters the consumer for the authentication requests.
     */
    @After
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
    public void testAuthenticateSuccess(final TestContext ctx) {
        final String token = createTestToken();

        authRequestConsumer = vertx.eventBus().consumer(EVENT_BUS_ADDRESS_AUTHENTICATION_IN, message -> {
            message.reply(AuthenticationConstants.getAuthenticationReply(token));
        });

        final Async async = ctx.async();
        final EventBusAuthenticationService eventBusAuthService = new EventBusAuthenticationService(vertx, authTokenHelperForValidating);
        eventBusAuthService.authenticate(new JsonObject(), ar -> {
            ctx.assertTrue(ar.succeeded());
            ctx.assertEquals(token, ar.result().getToken());
            async.complete();
        });
        async.await();
    }

    /**
     * Verifies that the correct exception is given to the handler of the <code>authenticate</code> method in
     * case no event bus handler is registered for handling authentication requests.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testAuthenticateFailureNoHandler(final TestContext ctx) {
        final Async async = ctx.async();
        final EventBusAuthenticationService eventBusAuthService = new EventBusAuthenticationService(vertx, authTokenHelperForValidating);
        eventBusAuthService.authenticate(new JsonObject(), ar -> {
            ctx.assertTrue(ar.failed());
            ctx.assertTrue(ar.cause() instanceof ServerErrorException);
            ctx.assertEquals(HttpURLConnection.HTTP_INTERNAL_ERROR, ((ServiceInvocationException) ar.cause()).getErrorCode());
            async.complete();
        });
        async.await();
    }

    /**
     * Verifies that an authentication request is sent via the vert.x event bus and the failure reply
     * is passed on to the handler of the <code>authenticate</code> method.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testAuthenticateFailureValidStatusCode(final TestContext ctx) {
        final String failureMessage = "failureMessage";

        authRequestConsumer = vertx.eventBus().consumer(EVENT_BUS_ADDRESS_AUTHENTICATION_IN, message -> {
            message.fail(HttpURLConnection.HTTP_UNAUTHORIZED, failureMessage);
        });

        final Async async = ctx.async();
        final EventBusAuthenticationService eventBusAuthService = new EventBusAuthenticationService(vertx, authTokenHelperForValidating);
        eventBusAuthService.authenticate(new JsonObject(), ar -> {
            ctx.assertTrue(ar.failed());
            ctx.assertTrue(ar.cause() instanceof ClientErrorException);
            ctx.assertEquals(HttpURLConnection.HTTP_UNAUTHORIZED, ((ServiceInvocationException) ar.cause()).getErrorCode());
            ctx.assertEquals(failureMessage, ar.cause().getMessage());
            async.complete();
        });
        async.await();
    }

    /**
     * Verifies that an authentication request is sent via the vert.x event bus and the failure reply,
     * containing an invalid error code, is passed on to the handler of the <code>authenticate</code> method
     * as an internal error.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testAuthenticateFailureInvalidStatusCode(final TestContext ctx) {
        final String failureMessage = "failureMessage";

        authRequestConsumer = vertx.eventBus().consumer(EVENT_BUS_ADDRESS_AUTHENTICATION_IN, message -> {
            message.fail(200, failureMessage);
        });

        final Async async = ctx.async();
        final EventBusAuthenticationService eventBusAuthService = new EventBusAuthenticationService(vertx, authTokenHelperForValidating);
        eventBusAuthService.authenticate(new JsonObject(), ar -> {
            ctx.assertTrue(ar.failed());
            ctx.assertTrue(ar.cause() instanceof ServerErrorException);
            ctx.assertEquals(HttpURLConnection.HTTP_INTERNAL_ERROR, ((ServiceInvocationException) ar.cause()).getErrorCode());
            ctx.assertEquals(failureMessage, ar.cause().getMessage());
            async.complete();
        });
        async.await();
    }

    private String createTestToken() {
        return authTokenHelperForSigning.createToken("authId", new AuthoritiesImpl());
    }
}
