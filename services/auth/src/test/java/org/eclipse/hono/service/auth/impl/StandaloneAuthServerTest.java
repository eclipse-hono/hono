/**
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 */

package org.eclipse.hono.service.auth.impl;

import org.eclipse.hono.config.ServiceConfigProperties;
import org.eclipse.hono.connection.ConnectionFactory;
import org.eclipse.hono.connection.ConnectionFactoryImpl;
import org.eclipse.hono.service.auth.AuthTokenHelper;
import org.eclipse.hono.service.auth.AuthTokenHelperImpl;
import org.eclipse.hono.service.auth.HonoSaslAuthenticatorFactory;
import org.eclipse.hono.service.auth.delegating.AuthenticationServerClient;
import org.eclipse.hono.service.auth.delegating.AuthenticationServerClientConfigProperties;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.core.io.ClassPathResource;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;


/**
 * Tests verifying behavior of a running authentication server.
 *
 */
@RunWith(VertxUnitRunner.class)
public class StandaloneAuthServerTest {

    private static final String SIGNING_SECRET = "sdfhuhsdauifhashgifhgfhsdaguihuifhsdifhsdifhiahfuihfuihsdaf";
    private static Vertx vertx = Vertx.vertx();

    private static SimpleAuthenticationServer server;
    private static AuthenticationServerClient client;

    /**
     * Sets up the server.
     * 
     * @param ctx The vertx test context.
     */
    @BeforeClass
    public static void prepareServer(final TestContext ctx) {

        AuthTokenHelper tokenHelper = AuthTokenHelperImpl.forSharedSecret(SIGNING_SECRET, 5);

        ServiceConfigProperties props = new ServiceConfigProperties();
        props.setInsecurePortEnabled(true);
        props.setInsecurePort(0);

        server = new SimpleAuthenticationServer();
        server.setConfig(props);
        server.setSaslAuthenticatorFactory(new HonoSaslAuthenticatorFactory(vertx, tokenHelper));
        server.addEndpoint(new AuthenticationEndpoint(vertx));

        AuthenticationServerConfigProperties serviceProps = new AuthenticationServerConfigProperties();
        serviceProps.getSigning().setTokenExpiration(5);
        serviceProps.getSigning().setSharedSecret(SIGNING_SECRET);
        serviceProps.setPermissionsPath(new ClassPathResource("authentication-service-test-permissions.json"));
        FileBasedAuthenticationService authServiceImpl = new FileBasedAuthenticationService();
        authServiceImpl.setConfig(serviceProps);
        authServiceImpl.setTokenFactory(tokenHelper);

        Async startup = ctx.async();
        Future<String> serverTracker = Future.future();
        serverTracker.setHandler(ctx.asyncAssertSuccess(s -> startup.complete()));
        Future<String> serviceTracker = Future.future();
        vertx.deployVerticle(authServiceImpl, serviceTracker.completer());
        serviceTracker.compose(s -> {
            vertx.deployVerticle(server, ctx.asyncAssertSuccess(d -> serverTracker.complete(d)));
        }, serverTracker);

        startup.await(2000);

        AuthenticationServerClientConfigProperties clientProps = new AuthenticationServerClientConfigProperties();
        clientProps.setHost("127.0.0.1");
        clientProps.setName("test-client");
        clientProps.setPort(server.getInsecurePort());
        clientProps.getValidation().setSharedSecret(SIGNING_SECRET);

        ConnectionFactory clientFactory = new ConnectionFactoryImpl(vertx, clientProps);
        client = new AuthenticationServerClient(vertx, clientFactory);
    }

    /**
     * Verifies that a client having authority <em>READ</em> on resource <em>cbs</em> can
     * successfully retrieve a token.
     * 
     * @param ctx The test context.
     */
    @Test
    public void testTokenRetrievalSucceedsForAuthenticatedUser(final TestContext ctx) {
        client.verifyPlain(null, "http-adapter@HONO", "secret", ctx.asyncAssertSuccess(user -> {
            ctx.assertNotNull(user.getToken());
        }));
    }

    /**
     * Verifies that an unauthenticated client can not retrieve a token.
     * 
     * @param ctx The test context.
     */
    @Test
    public void testTokenRetrievalFailsForUnauthenticatedUser(final TestContext ctx) {
        client.verifyPlain(null, "no-such-user", "secret", ctx.asyncAssertFailure());
    }

}
