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

import java.net.HttpURLConnection;

import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.config.ClientConfigProperties;
import org.eclipse.hono.tests.IntegrationTestHonoClient;
import org.eclipse.hono.tests.IntegrationTestSupport;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.vertx.core.Vertx;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.proton.ProtonClientOptions;

/**
 * Test cases verifying the behavior of {@code HonoClient}.
 */
@RunWith(VertxUnitRunner.class)
public class HonoClientImplIT {

    private static Vertx vertx;

    public IntegrationTestHonoClient honoClient;

    /**
     * Sets up vert.x.
     */
    @BeforeClass
    public static void init() {
        vertx = Vertx.vertx();
    }

    /**
     * Verifies that a connection attempt where no credentials are given fails
     * immediately with a ClientErrorException with status code 401.
     * 
     * @param ctx The vert.x test context.
     */
    @Test
    public void testConnectFailsWithClientErrorForNoSASLMechanismException(final TestContext ctx) {

        // GIVEN a client that is configured with no username and password
        final ClientConfigProperties downstreamProps = new ClientConfigProperties();
        downstreamProps.setHost(IntegrationTestSupport.DOWNSTREAM_HOST);
        downstreamProps.setPort(IntegrationTestSupport.DOWNSTREAM_PORT);
        downstreamProps.setFlowLatency(200);
        downstreamProps.setReconnectAttempts(-1);

        final Async async = ctx.async();
        honoClient = new IntegrationTestHonoClient(vertx, downstreamProps);
        final ProtonClientOptions protonClientOptions = new ProtonClientOptions();
        protonClientOptions.setReconnectInterval(20L);
        // WHEN the client tries to connect
        honoClient.connect(protonClientOptions).setHandler(res -> {
            // THEN the connection attempt fails due do lack of authorization
            ctx.assertTrue(res.failed());
            ctx.assertEquals(HttpURLConnection.HTTP_UNAUTHORIZED, ServiceInvocationException.extractStatusCode(res.cause()));
            ctx.assertEquals("no suitable SASL mechanism found for authentication with server", res.cause().getMessage());
            async.complete();
        });
        async.await();
    }

}

