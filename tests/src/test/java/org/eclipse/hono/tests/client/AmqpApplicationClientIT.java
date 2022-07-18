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
package org.eclipse.hono.tests.client;

import static com.google.common.truth.Truth.assertThat;

import java.net.HttpURLConnection;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.eclipse.hono.application.client.amqp.AmqpApplicationClient;
import org.eclipse.hono.application.client.amqp.ProtonBasedApplicationClient;
import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.amqp.config.ClientConfigProperties;
import org.eclipse.hono.client.amqp.connection.HonoConnection;
import org.eclipse.hono.tests.EnabledIfMessagingSystemConfigured;
import org.eclipse.hono.tests.IntegrationTestSupport;
import org.eclipse.hono.util.MessagingType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.Vertx;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

/**
 * Test cases verifying the behavior of {@link ProtonBasedApplicationClient}.
 */
@ExtendWith(VertxExtension.class)
@EnabledIfMessagingSystemConfigured(type = MessagingType.amqp)
public class AmqpApplicationClientIT {

    private static Vertx vertx;

    private AmqpApplicationClient client;

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

        client = new ProtonBasedApplicationClient(HonoConnection.newConnection(vertx, downstreamProps));
        // WHEN the client tries to connect
        client.connect().onComplete(ctx.failing(t -> {
            // THEN the connection attempt fails due to lack of authorization
            ctx.verify(() -> {
                assertThat(t).isInstanceOf(ClientErrorException.class);
                assertThat(((ClientErrorException) t).getErrorCode()).isEqualTo(HttpURLConnection.HTTP_UNAUTHORIZED);
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

        // GIVEN a client that is configured to try to connect using an unsupported TLS version
        final ClientConfigProperties downstreamProps = new ClientConfigProperties();
        downstreamProps.setHost(IntegrationTestSupport.DOWNSTREAM_HOST);
        downstreamProps.setPort(IntegrationTestSupport.DOWNSTREAM_PORT);
        downstreamProps.setTlsEnabled(true);
        downstreamProps.setSecureProtocols(List.of("TLSv1.1"));
        downstreamProps.setReconnectAttempts(2);

        client = new ProtonBasedApplicationClient(HonoConnection.newConnection(vertx, downstreamProps));
        // WHEN the client tries to connect
        client.connect().onComplete(ctx.failing(t -> {
            // THEN the connection attempt fails due to lack of authorization
            ctx.verify(() -> {
                assertThat(t).isInstanceOf(ClientErrorException.class);
                assertThat(((ClientErrorException) t).getErrorCode()).isEqualTo(HttpURLConnection.HTTP_BAD_REQUEST);
            });
            ctx.completeNow();
        }));
    }
}

