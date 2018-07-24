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

package org.eclipse.hono.tests.auth;

import org.eclipse.hono.client.impl.AuthenticationServerClient;
import org.eclipse.hono.config.ClientConfigProperties;
import org.eclipse.hono.connection.ConnectionFactory;
import org.eclipse.hono.connection.impl.ConnectionFactoryImpl;
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
