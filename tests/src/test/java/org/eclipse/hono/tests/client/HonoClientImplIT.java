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
package org.eclipse.hono.tests.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.HttpURLConnection;
import java.util.concurrent.TimeUnit;

import org.eclipse.hono.client.HonoConnection;
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.config.ClientConfigProperties;
import org.eclipse.hono.tests.IntegrationTestApplicationClientFactory;
import org.eclipse.hono.tests.IntegrationTestSupport;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.Vertx;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

/**
 * Test cases verifying the behavior of {@code HonoClient}.
 */
@ExtendWith(VertxExtension.class)
public class HonoClientImplIT {

    private static Vertx vertx;

    private IntegrationTestApplicationClientFactory clientFactory;

    /**
     * Sets up vert.x.
     */
    @BeforeAll
    public static void init() {
        vertx = Vertx.vertx();
    }

    /**
     * Verifies that a connection attempt where no credentials are given fails after two retries with a
     * ClientErrorException with status code 401.
     * 
     * @param ctx The vert.x test context.
     */
    @Test
    public void testConnectFailsWithClientErrorForNoSASLMechanismException(final VertxTestContext ctx) {

        // GIVEN a client that is configured with no username and password
        final ClientConfigProperties downstreamProps = new ClientConfigProperties();
        downstreamProps.setHost(IntegrationTestSupport.DOWNSTREAM_HOST);
        downstreamProps.setPort(IntegrationTestSupport.DOWNSTREAM_PORT);
        downstreamProps.setReconnectAttempts(2);

        clientFactory = IntegrationTestApplicationClientFactory.create(HonoConnection.newConnection(vertx, downstreamProps));
        // WHEN the client tries to connect
        clientFactory.connect().onComplete(ctx.failing(t -> {
            // THEN the connection attempt fails due to lack of authorization
            ctx.verify(() -> {
                assertThat(ServiceInvocationException.extractStatusCode(t)).isEqualTo(HttpURLConnection.HTTP_UNAUTHORIZED);
            });
            ctx.completeNow();
        }));
    }

    /**
     * Verifies that a connection attempt where the TLS handshake cannot be finished successfully fails after two
     * retries with a ClientErrorException with status code 400.
     * 
     * @param ctx The vert.x test context.
     */
    @Test
    @Timeout(value = 10, timeUnit = TimeUnit.SECONDS)
    public void testConnectFailsWithClientErrorIfTlsHandshakeFails(final VertxTestContext ctx) {

        // GIVEN a client that is configured to try to connect using TLS to a port that does not support TLS
        final ClientConfigProperties downstreamProps = new ClientConfigProperties();
        downstreamProps.setHost(IntegrationTestSupport.DOWNSTREAM_HOST);
        downstreamProps.setPort(IntegrationTestSupport.DOWNSTREAM_PORT);
        downstreamProps.setTlsEnabled(true);
        downstreamProps.setReconnectAttempts(2);

        clientFactory = IntegrationTestApplicationClientFactory.create(HonoConnection.newConnection(vertx, downstreamProps));
        // WHEN the client tries to connect
        clientFactory.connect().onComplete(ctx.failing(t -> {
            // THEN the connection attempt fails due to lack of authorization
            ctx.verify(() -> {
                assertThat(ServiceInvocationException.extractStatusCode(t)).isEqualTo(HttpURLConnection.HTTP_BAD_REQUEST);
            });
            ctx.completeNow();
        }));
    }
}

