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

import org.eclipse.hono.client.HonoConnection;
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.config.ClientConfigProperties;
import org.eclipse.hono.tests.IntegrationTestApplicationClientFactory;
import org.eclipse.hono.tests.IntegrationTestSupport;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.vertx.core.Vertx;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;

/**
 * Test cases verifying the behavior of {@code HonoClient}.
 */
@RunWith(VertxUnitRunner.class)
public class HonoClientImplIT {

    private static Vertx vertx;

    private IntegrationTestApplicationClientFactory clientFactory;

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
        clientFactory = IntegrationTestApplicationClientFactory.create(HonoConnection.newConnection(vertx, downstreamProps));
        // WHEN the client tries to connect
        clientFactory.connect().setHandler(res -> {
            // THEN the connection attempt fails due to lack of authorization
            ctx.assertTrue(res.failed());
            ctx.assertEquals(HttpURLConnection.HTTP_UNAUTHORIZED, ServiceInvocationException.extractStatusCode(res.cause()));
            ctx.assertEquals("no suitable SASL mechanism found for authentication with server", res.cause().getMessage());
            async.complete();
        });
        async.await();
    }

}

