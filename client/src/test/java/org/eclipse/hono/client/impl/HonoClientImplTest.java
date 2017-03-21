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

package org.eclipse.hono.client.impl;

import static org.mockito.Mockito.*;

import org.eclipse.hono.client.impl.HonoClientImpl;
import org.eclipse.hono.connection.ConnectionFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.proton.ProtonConnection;
import io.vertx.proton.ProtonSender;

/**
 * Test cases verifying the behavior of {@code HonoClient}.
 *
 */
@RunWith(VertxUnitRunner.class)
public class HonoClientImplTest {

    Vertx vertx;

    /**
     * Sets up common test bed.
     */
    @Before
    public void setUp() {
        vertx = Vertx.vertx();
    }

    /**
     * Cleans up after test execution.
     */
    @After
    public void shutdown() {
        if (vertx != null) {
            vertx.close();
        }
    }

    /**
     * Verifies that a concurrent request to create a sender fails the given future for tracking the attempt.
     * 
     * @param ctx The helper to use for running async tests.
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testGetOrCreateTelemetrySenderFailsIfInvokedConcurrently(final TestContext ctx) {

        // GIVEN a client that already tries to create a telemetry sender for "tenant"
        ConnectionFactory connectionFactory = mock(ConnectionFactory.class);
        ProtonConnection con = mock(ProtonConnection.class);
        ProtonSender sender = mock(ProtonSender.class);
        when(con.createSender("telemetry/tenant")).thenReturn(sender);
        when(sender.openHandler(any(Handler.class))).thenReturn(sender);
        when(sender.closeHandler(any(Handler.class))).thenReturn(sender);
        HonoClientImpl client = new HonoClientImpl(vertx, connectionFactory);
        client.setConnection(con);
        client.setContext(vertx.getOrCreateContext());
        client.getOrCreateTelemetrySender("tenant", creationAttempt -> {
            ctx.fail("should not have created sender");
        });

        // WHEN an additional, concurrent attempt is made to create a telemetry sender for "tenant"
        final Async creationFailure = ctx.async();
        client.getOrCreateTelemetrySender("tenant", creationAttempt -> {
            ctx.assertFalse(creationAttempt.succeeded());
            creationFailure.complete();
        });

        // THEN the concurrent attempt fails immediately without any attempt being made to create another sender
        creationFailure.await(2000);
        verify(con).createSender("telemetry/tenant");
    }
}
