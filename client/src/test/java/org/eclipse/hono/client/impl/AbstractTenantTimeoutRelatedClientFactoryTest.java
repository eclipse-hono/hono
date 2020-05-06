/*******************************************************************************
 * Copyright (c) 2016, 2020 Contributors to the Eclipse Foundation
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.TimeUnit;

import org.eclipse.hono.client.HonoConnection;
import org.eclipse.hono.util.Constants;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.proton.ProtonReceiver;
import io.vertx.proton.ProtonSender;

/**
 * Abstract base class for tests that verify the tenant timeout feature in the client factories.
 * 
 * @param <T> The type of the client that the factory builds.
 */
@ExtendWith(VertxExtension.class)
@Timeout(value = 5, timeUnit = TimeUnit.SECONDS)
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
    public void testLinksCloseOnTenantTimeout(final VertxTestContext ctx) {

        final String tenantId = "tenant";

        // GIVEN a client factory that manages a client with a tenant-scoped link
        final HonoConnection connection = createConnection();
        getClientFuture(connection, tenantId).onComplete(ctx.succeeding(r -> {

            // WHEN a tenant timeout event occurs for this tenant
            connection.getVertx().eventBus().publish(Constants.EVENT_BUS_ADDRESS_TENANT_TIMED_OUT, tenantId);

            // THEN the link is closed
            ctx.verify(() -> {
                verify(connection, timeout(TIMEOUT)).closeAndFree(any(ProtonSender.class), VertxMockSupport.anyHandler());
            });
            ctx.completeNow();
        }));
    }

    /**
     * Verifies that the links of other clients are not closed when a tenant timeout message is received.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testDontCloseLinksForOtherTenants(final VertxTestContext ctx) {

        final String tenantId = "tenant";
        final String otherTenant = "otherTenant";

        // GIVEN a client factory that manages a client with a tenant-scoped link
        final HonoConnection connection = createConnection();
        getClientFuture(connection, tenantId).onComplete(ctx.succeeding(r -> {

            // WHEN a tenant timeout event occurs for another tenant
            connection.getVertx().eventBus().publish(Constants.EVENT_BUS_ADDRESS_TENANT_TIMED_OUT, otherTenant);

            ctx.verify(() -> {
                // THEN the link is not closed
                verify(connection, timeout(TIMEOUT).times(0)).closeAndFree(any(ProtonSender.class), VertxMockSupport.anyHandler());
            });
            ctx.completeNow();
        }));
    }

    private HonoConnection createConnection() {
        final Vertx vertx = Vertx.vertx();
        final HonoConnection connection = HonoClientUnitTestHelper.mockHonoConnection(vertx);
        when(connection.getVertx()).thenReturn(vertx);

        when(connection.isConnected()).thenReturn(Future.succeededFuture());
        when(connection.isConnected(anyLong())).thenReturn(Future.succeededFuture());

        final ProtonReceiver receiver = HonoClientUnitTestHelper.mockProtonReceiver();
        when(connection.createReceiver(anyString(), any(), any(), any())).thenReturn(Future.succeededFuture(receiver));
        final ProtonSender sender = HonoClientUnitTestHelper.mockProtonSender();
        when(connection.createSender(anyString(), any(), any())).thenReturn(Future.succeededFuture(sender));
        return connection;
    }
}
