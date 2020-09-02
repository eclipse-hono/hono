/*******************************************************************************
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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

import static org.assertj.core.api.Assertions.assertThat;

import java.net.HttpURLConnection;
import java.util.concurrent.TimeUnit;

import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.client.TenantClient;
import org.eclipse.hono.config.ClientConfigProperties;
import org.eclipse.hono.tests.IntegrationTestSupport;
import org.eclipse.hono.tests.jms.JmsBasedHonoConnection;
import org.eclipse.hono.tests.jms.JmsBasedTenantClient;
import org.eclipse.hono.util.TenantConstants;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Vertx;
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

    private static final Logger LOG = LoggerFactory.getLogger(TenantJmsIT.class);
    private static final Vertx vertx = Vertx.vertx();

    private static JmsBasedHonoConnection allTenantConnection;
    private static JmsBasedHonoConnection defaultTenantConnection;
    private static JmsBasedTenantClient allTenantClient;
    private static JmsBasedTenantClient defaultTenantClient;
    private static IntegrationTestSupport helper;

    /**
     * Creates an HTTP client for managing the fixture of test cases
     * and creates a {@link TenantClient} for invoking operations of the
     * Tenant API.
     *
     * @param ctx The vert.x test context.
     */
    @BeforeAll
    public static void prepareDeviceRegistry(final VertxTestContext ctx) {

        helper = new IntegrationTestSupport(vertx);
        helper.initRegistryClient();

        final Checkpoint connections = ctx.checkpoint(2);

        final ClientConfigProperties allTenantConfig = IntegrationTestSupport.getDeviceRegistryProperties(
                IntegrationTestSupport.TENANT_ADMIN_USER, IntegrationTestSupport.TENANT_ADMIN_PWD);

        JmsBasedHonoConnection.newConnection(allTenantConfig).connect()
        .map(con -> {
            allTenantConnection = con;
            return con;
        })
        .compose(con -> JmsBasedTenantClient.create(con, allTenantConfig))
        .onComplete(ctx.succeeding(client -> {
            allTenantClient = client;
            connections.flag();
        }));

        final ClientConfigProperties defaultTenantConfig = IntegrationTestSupport.getDeviceRegistryProperties(
                IntegrationTestSupport.HONO_USER, IntegrationTestSupport.HONO_PWD);

        JmsBasedHonoConnection.newConnection(defaultTenantConfig).connect()
        .map(con -> {
            defaultTenantConnection = con;
            return con;
        })
        .compose(con -> JmsBasedTenantClient.create(con, defaultTenantConfig))
        .onComplete(ctx.succeeding(client -> {
            defaultTenantClient = client;
            connections.flag();
        }));
    }

    /**
     * Prints the test name.
     *
     * @param testInfo The test info.
     */
    @BeforeEach
    public void init(final TestInfo testInfo) {
        LOG.info("running test: {}", testInfo.getDisplayName());
    }

    /**
     * Removes all temporary objects from the registry.
     *
     * @param ctx The vert.x test context.
     */
    @AfterEach
    public void cleanUp(final VertxTestContext ctx) {
        helper.deleteObjects(ctx);
    }

    /**
     * Closes the connection to the Tenant service.
     *
     * @param ctx The vert.x test context.
     */
    @AfterAll
    public static void shutdown(final VertxTestContext ctx) {

        final Checkpoint connectionClosed = ctx.checkpoint(2);
        disconnect(ctx, connectionClosed, defaultTenantConnection);
        disconnect(ctx, connectionClosed, allTenantConnection);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected IntegrationTestSupport getHelper() {
        return helper;
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
