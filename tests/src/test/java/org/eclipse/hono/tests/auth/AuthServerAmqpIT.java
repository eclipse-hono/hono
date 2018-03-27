/**
 * Copyright (c) 2017, 2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 */

package org.eclipse.hono.tests.auth;

import org.eclipse.hono.client.impl.AuthenticationServerClient;
import org.eclipse.hono.config.ClientConfigProperties;
import org.eclipse.hono.connection.ConnectionFactory;
import org.eclipse.hono.connection.ConnectionFactoryImpl;
import org.eclipse.hono.tests.IntegrationTestSupport;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.vertx.core.Vertx;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;


/**
 * Tests verifying behavior of a running Auth server.
 *
 */
@RunWith(VertxUnitRunner.class)
public class AuthServerAmqpIT {

    private static Vertx vertx = Vertx.vertx();

    private static AuthenticationServerClient client;

    /**
     * Sets up the server.
     * 
     * @param ctx The vertx test context.
     */
    @BeforeClass
    public static void prepareServer(final TestContext ctx) {

        final ClientConfigProperties clientProps = new ClientConfigProperties();
        clientProps.setHost(IntegrationTestSupport.AUTH_HOST);
        clientProps.setPort(IntegrationTestSupport.AUTH_PORT);
        clientProps.setName("test-client");

        final ConnectionFactory clientFactory = new ConnectionFactoryImpl(vertx, clientProps);
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
        client.verifyPlain(null, "hono-client", "secret", ctx.asyncAssertSuccess(user -> {
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
