/*******************************************************************************
 * Copyright (c) 2019, 2020 Contributors to the Eclipse Foundation
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

import java.net.HttpURLConnection;
import java.util.concurrent.TimeUnit;

import org.eclipse.hono.client.CredentialsClient;
import org.eclipse.hono.config.ClientConfigProperties;
import org.eclipse.hono.tests.IntegrationTestSupport;
import org.eclipse.hono.tests.jms.JmsBasedCredentialsClient;
import org.eclipse.hono.tests.jms.JmsBasedHonoConnection;
import org.eclipse.hono.util.CredentialsConstants;
import org.eclipse.hono.util.CredentialsConstants.CredentialsAction;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.Checkpoint;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

/**
 * Tests verifying the behavior of a Credentials service implementation using a JMS based client.
 */
@ExtendWith(VertxExtension.class)
public class CredentialsJmsIT extends CredentialsApiTests {

    private static JmsBasedHonoConnection connection;
    private static ClientConfigProperties props;

    /**
     * Creates the JMS connection to the registry's Credentials service.
     *
     * @param ctx The vert.x test context.
     */
    @BeforeAll
    public static void createJmsConnection(final VertxTestContext ctx) {

        props = IntegrationTestSupport.getDeviceRegistryProperties(
                IntegrationTestSupport.TENANT_ADMIN_USER,
                IntegrationTestSupport.TENANT_ADMIN_PWD);

        connection = JmsBasedHonoConnection.newConnection(props);
        connection.connect().onComplete(ctx.completing());

    }

    /**
     * Shuts down the device registry and closes the client.
     *
     * @param ctx The vert.x test context.
     */
    @AfterAll
    public static void shutdown(final VertxTestContext ctx) {
        final Checkpoint cons = ctx.checkpoint();
        disconnect(ctx, cons, connection);
    }

    private Future<JmsBasedCredentialsClient> getJmsBasedClient(final String tenant) {
        if (connection == null) {
            throw new IllegalStateException("no connection to Credentials service");
        }
        return connection
                .isConnected()
                .compose(ok -> JmsBasedCredentialsClient.create(connection, props, tenant));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Future<CredentialsClient> getClient(final String tenant) {
        return getJmsBasedClient(tenant).map(client -> (CredentialsClient) client);
    }

    /**
     * Verifies that a request to retrieve information for malformed search criteria
     * fails with a 400 status.
     *
     * @param ctx The vert.x test context.
     */
    @Timeout(value = 5, timeUnit = TimeUnit.SECONDS)
    @Test
    public void testGetCredentialsFailsForMalformedSearchCriteria(final VertxTestContext ctx) {

        getJmsBasedClient(tenantId)
            .compose(client -> client.sendRequest(
                    CredentialsAction.get.toString(),
                    null,
                    Buffer.buffer(new byte[] { 0x01, 0x02, 0x03, 0x04 }))) // no JSON
            .onComplete(ctx.failing(t -> {
                assertErrorCode(t, HttpURLConnection.HTTP_BAD_REQUEST);
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
    public void testRequestFailsForMissingSubject(final VertxTestContext ctx) {

        final JsonObject searchCriteria = CredentialsConstants.getSearchCriteria(
                CredentialsConstants.SECRETS_TYPE_PRESHARED_KEY, "device");

        getJmsBasedClient(tenantId)
            .compose(client -> client.sendRequest(
                    null,
                    null,
                    searchCriteria.toBuffer()))
            .onComplete(ctx.failing(t -> {
                assertErrorCode(t, HttpURLConnection.HTTP_BAD_REQUEST);
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
    public void testRequestFailsForUnsupportedOperation(final VertxTestContext ctx) {

        final JsonObject searchCriteria = CredentialsConstants.getSearchCriteria(
                CredentialsConstants.SECRETS_TYPE_PRESHARED_KEY, "device");

        getJmsBasedClient(tenantId)
            .compose(client -> client.sendRequest(
                    "unsupported-operation",
                    null,
                    searchCriteria.toBuffer()))
            .onComplete(ctx.failing(t -> {
                assertErrorCode(t, HttpURLConnection.HTTP_BAD_REQUEST);
                ctx.completeNow();
            }));
    }
}

