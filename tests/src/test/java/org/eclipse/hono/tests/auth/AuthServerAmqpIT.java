/*******************************************************************************
 * Copyright (c) 2016, 2021 Contributors to the Eclipse Foundation
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

import static org.assertj.core.api.Assertions.assertThat;

import java.net.HttpURLConnection;

import org.eclipse.hono.client.AuthenticationServerClient;
import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.ServerErrorException;
import org.eclipse.hono.config.ClientConfigProperties;
import org.eclipse.hono.connection.ConnectionFactory;
import org.eclipse.hono.connection.impl.ConnectionFactoryImpl;
import org.eclipse.hono.tests.IntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;


/**
 * Tests verifying behavior of a running Authentication server.
 *
 */
@ExtendWith(VertxExtension.class)
public class AuthServerAmqpIT {

    private static Vertx vertx = Vertx.vertx();
    private AuthenticationServerClient client;

    /**
     * Creates the authentication server client.
     */
    @BeforeEach
    public void prepareClient() {

        client = getClient();
    }

    private AuthenticationServerClient getClient() {
        return getClient(IntegrationTestSupport.AUTH_HOST, IntegrationTestSupport.AUTH_PORT);
    }

    private AuthenticationServerClient getClient(final String host, final int port) {

        final ClientConfigProperties clientProps = new ClientConfigProperties();
        clientProps.setHost(host);
        clientProps.setPort(port);
        clientProps.setName("test-client");
        clientProps.setTrustStorePath(IntegrationTestSupport.TRUST_STORE_PATH);

        final ConnectionFactory clientFactory = new ConnectionFactoryImpl(vertx, clientProps);
        return new AuthenticationServerClient(vertx, clientFactory);
    }

    /**
     * Verifies that a client having authority <em>READ</em> on resource <em>cbs</em> can
     * successfully retrieve a token.
     *
     * @param ctx The test context.
     */
    @Test
    public void testTokenRetrievalSucceedsForAuthenticatedUser(final VertxTestContext ctx) {

        client.verifyPlain(null, "hono-client", "secret", ctx.succeeding(user -> {
            ctx.verify(() -> {
                assertThat(user.getToken()).isNotNull();
            });
            ctx.completeNow();
        }));
    }

    /**
     * Verifies that an unauthenticated client can not retrieve a token.
     *
     * @param ctx The test context.
     */
    @Test
    public void testTokenRetrievalFailsForUnauthenticatedUser(final VertxTestContext ctx) {
        client.verifyPlain(null, "no-such-user", "secret", ctx.failing(t -> {
            ctx.verify(() -> {
                assertThat(t).isInstanceOf(ClientErrorException.class);
                assertThat(((ClientErrorException) t).getErrorCode()).isEqualTo(HttpURLConnection.HTTP_UNAUTHORIZED);
            });
            ctx.completeNow();
        }));
    }

    /**
     * Verifies that an unauthenticated client can not retrieve a token.
     *
     * @param ctx The test context.
     */
    @Test
    public void testTokenRetrievalFailsForFailureToConnect(final VertxTestContext ctx) {

        client = getClient("127.0.0.1", 13412);
        client.verifyPlain(null, "hono-client", "secret", ctx.failing(t -> {
            ctx.verify(() -> {
                assertThat(t).isInstanceOf(ServerErrorException.class);
                assertThat(((ServerErrorException) t).getErrorCode()).isEqualTo(HttpURLConnection.HTTP_UNAVAILABLE);
            });
            ctx.completeNow();
        }));
    }

}
