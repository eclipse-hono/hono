/*******************************************************************************
 * Copyright (c) 2019, 2021 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.tests.registry;

import static com.google.common.truth.Truth.assertThat;

import java.net.HttpURLConnection;
import java.util.concurrent.TimeUnit;

import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.client.amqp.config.ClientConfigProperties;
import org.eclipse.hono.client.registry.TenantClient;
import org.eclipse.hono.tests.IntegrationTestSupport;
import org.eclipse.hono.tests.jms.JmsBasedHonoConnection;
import org.eclipse.hono.tests.jms.JmsBasedTenantClient;
import org.eclipse.hono.util.TenantConstants;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.Checkpoint;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

/**
 * Tests verifying the behavior of the Device Registry component's Tenant AMQP endpoint.
 */
@ExtendWith(VertxExtension.class)
public class TenantJmsIT extends TenantApiTests {

    private static JmsBasedTenantClient allTenantClient;
    private static JmsBasedTenantClient defaultTenantClient;

    /**
     * Creates an HTTP client for managing the fixture of test cases
     * and creates clients for invoking operations of the
     * Tenant API.
     *
     * @param ctx The vert.x test context.
     */
    @BeforeAll
    public static void prepareDeviceRegistry(final VertxTestContext ctx) {

        final Checkpoint connections = ctx.checkpoint(2);

        final ClientConfigProperties allTenantConfig = IntegrationTestSupport.getDeviceRegistryProperties(
                IntegrationTestSupport.TENANT_ADMIN_USER, IntegrationTestSupport.TENANT_ADMIN_PWD);

        allTenantClient = new JmsBasedTenantClient(JmsBasedHonoConnection.newConnection(allTenantConfig));

        allTenantClient.start()
            .onComplete(ctx.succeeding(client -> {
                connections.flag();
            }));

        final ClientConfigProperties defaultTenantConfig = IntegrationTestSupport.getDeviceRegistryProperties(
                IntegrationTestSupport.HONO_USER, IntegrationTestSupport.HONO_PWD);

        defaultTenantClient = new JmsBasedTenantClient(JmsBasedHonoConnection.newConnection(defaultTenantConfig));

        defaultTenantClient.start()
            .onComplete(ctx.succeeding(client -> {
                connections.flag();
            }));
    }

    /**
     * Closes the connection to the Tenant service.
     *
     * @param ctx The vert.x test context.
     */
    @AfterAll
    public static void shutdown(final VertxTestContext ctx) {

        final Checkpoint connectionClosed = ctx.checkpoint(2);
        stop(ctx, connectionClosed, defaultTenantClient);
        stop(ctx, connectionClosed, allTenantClient);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected TenantClient getAdminClient() {
        return allTenantClient;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected TenantClient getRestrictedClient() {
        return defaultTenantClient;
    }

    /**
     * Verifies that a request to retrieve information for unsupported search criteria
     * fails with a 400 status.
     *
     * @param ctx The vert.x test context.
     */
    @Timeout(value = 5, timeUnit = TimeUnit.SECONDS)
    @Test
    public void testGetTenantFailsForUnsupportedSearchCriteria(final VertxTestContext ctx) {

        final JsonObject unsupportedSearchCriteria = new JsonObject().put("color", "blue");
        allTenantClient
        .get(unsupportedSearchCriteria.toBuffer())
        .onComplete(ctx.failing(t -> {
            ctx.verify(() -> assertThat(((ServiceInvocationException) t).getErrorCode()).isEqualTo(HttpURLConnection.HTTP_BAD_REQUEST));
            ctx.completeNow();
        }));
    }

    /**
     * Verifies that a request to retrieve information for malformed search criteria
     * fails with a 400 status.
     *
     * @param ctx The vert.x test context.
     */
    @Timeout(value = 5, timeUnit = TimeUnit.SECONDS)
    @Test
    public void testGetTenantFailsForMalformedSearchCriteria(final VertxTestContext ctx) {

        allTenantClient
        .get(Buffer.buffer(new byte[] { 0x01, 0x02, 0x03, 0x04 })) // not JSON
        .onComplete(ctx.failing(t -> {
            ctx.verify(() -> assertThat(((ServiceInvocationException) t).getErrorCode()).isEqualTo(HttpURLConnection.HTTP_BAD_REQUEST));
            ctx.completeNow();
        }));
    }

    /**
     * Verifies that the service responds with a 400 status to a request that
     * has no subject.
     *
     * @param ctx The vert.x test context.
     */
    @Timeout(value = 5, timeUnit = TimeUnit.SECONDS)
    @Test
    public void testGetTenantFailsForMissingSubject(final VertxTestContext ctx) {

        allTenantClient
        .sendRequest(null, new JsonObject().put(TenantConstants.FIELD_PAYLOAD_TENANT_ID, "tenant").toBuffer())
        .onComplete(ctx.failing(t -> {
            ctx.verify(() -> {
                assertThat(((ServiceInvocationException) t).getErrorCode()).isEqualTo(HttpURLConnection.HTTP_BAD_REQUEST);
            });
            ctx.completeNow();
        }));
    }

    /**
     * Verifies that the service responds with a 400 status to a request that
     * indicates an unsupported operation in its subject.
     *
     * @param ctx The vert.x test context.
     */
    @Timeout(value = 5, timeUnit = TimeUnit.SECONDS)
    @Test
    public void testGetTenantFailsForUnknownOperation(final VertxTestContext ctx) {

        allTenantClient
        .sendRequest(
                "unsupported-operation",
                new JsonObject().put(TenantConstants.FIELD_PAYLOAD_TENANT_ID, "tenant").toBuffer())
        .onComplete(ctx.failing(t -> {
            ctx.verify(() -> {
                assertThat(((ServiceInvocationException) t).getErrorCode()).isEqualTo(HttpURLConnection.HTTP_BAD_REQUEST);
            });
            ctx.completeNow();
        }));
    }
}
