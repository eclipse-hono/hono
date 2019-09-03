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

package org.eclipse.hono.client.impl;

import static org.eclipse.hono.client.impl.VertxMockSupport.anyHandler;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.eclipse.hono.client.HonoConnection;
import org.eclipse.hono.util.Constants;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.proton.ProtonReceiver;
import io.vertx.proton.ProtonSender;

/**
 * Abstract base class for tests that verify the tenant timeout feature in the client factories.
 * 
 * @param <T> The type of the client that the factory builds.
 */
@RunWith(VertxUnitRunner.class)
public abstract class AbstractTenantTimeoutRelatedClientFactoryTest<T> {

    private static long TIMEOUT = 1000L;

    /**
     * Gets a future with a client of type `T` from its factory.
     * 
     * @param connection The connection to be used.
     * @param tenantId The tenant of the client.
     * @return The future with the client.
     */
    protected abstract Future<T> getClientFuture(HonoConnection connection, String tenantId);

    /**
     * Verifies that the links are closed when a tenant timeout message is received for the tenant of this client.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testLinksCloseOnTenantTimeout(final TestContext ctx) {

        final String tenantId = "tenant";

        // GIVEN a client factory that manages a client with a tenant-scoped link
        final HonoConnection connection = createConnection();
        final Future<T> clientFuture = getClientFuture(connection, tenantId);

        final Async async = ctx.async();
        clientFuture.setHandler(ar -> {
            ctx.assertTrue(ar.succeeded());

            // WHEN a tenant timeout event occurs for this tenant
            connection.getVertx().eventBus().publish(Constants.EVENT_BUS_ADDRESS_TENANT_TIMED_OUT, tenantId);

            // THEN the link is closed
            verify(connection, timeout(TIMEOUT)).closeAndFree(any(ProtonSender.class), anyHandler());
            async.complete();
        });
        async.await();
    }

    /**
     * Verifies that the links of other clients are not closed when a tenant timeout message is received.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testDontCloseLinksForOtherTenants(final TestContext ctx) {

        final String tenantId = "tenant";
        final String otherTenant = "otherTenant";

        // GIVEN a client factory that manages a client with a tenant-scoped link
        final HonoConnection connection = createConnection();
        final Future<T> clientFuture = getClientFuture(connection, tenantId);

        final Async async = ctx.async();
        clientFuture.setHandler(ar -> {
            ctx.assertTrue(ar.succeeded());

            // WHEN a tenant timeout event occurs for another tenant
            connection.getVertx().eventBus().publish(Constants.EVENT_BUS_ADDRESS_TENANT_TIMED_OUT, otherTenant);

            // THEN the link is not closed
            verify(connection, timeout(TIMEOUT).times(0)).closeAndFree(any(ProtonSender.class), anyHandler());
            async.complete();
        });
        async.await();
    }

    private HonoConnection createConnection() {
        final Vertx vertx = Vertx.vertx();
        final HonoConnection connection = HonoClientUnitTestHelper.mockHonoConnection(vertx);
        when(connection.getVertx()).thenReturn(vertx);

        final ProtonReceiver receiver = HonoClientUnitTestHelper.mockProtonReceiver();
        when(connection.createReceiver(anyString(), any(), any(), any())).thenReturn(Future.succeededFuture(receiver));
        final ProtonSender sender = HonoClientUnitTestHelper.mockProtonSender();
        when(connection.createSender(anyString(), any(), any())).thenReturn(Future.succeededFuture(sender));
        return connection;
    }
}
