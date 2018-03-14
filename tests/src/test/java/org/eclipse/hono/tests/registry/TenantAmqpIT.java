/**
 * Copyright (c) 2018 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 1.0 which is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * SPDX-License-Identifier: EPL-1.0
 */

package org.eclipse.hono.tests.registry;

import java.net.HttpURLConnection;
import java.util.concurrent.TimeUnit;

import javax.security.auth.x500.X500Principal;

import org.eclipse.hono.client.HonoClient;
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.client.TenantClient;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.TenantConstants;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.proton.ProtonClientOptions;

/**
 * Tests verifying the behavior of the Device Registry component's Tenant AMQP endpoint.
 */
@RunWith(VertxUnitRunner.class)
public class TenantAmqpIT {

    private static final Vertx vertx = Vertx.vertx();

    private static HonoClient client;
    private static TenantClient tenantClient;

    /**
     * Global timeout for all test cases.
     */
    @Rule
    public Timeout globalTimeout = new Timeout(5, TimeUnit.SECONDS);

    /**
     * Starts the device registry, connects a client and provides a tenant API client.
     * 
     * @param ctx The vert.x test context.
     */
    @BeforeClass
    public static void prepareDeviceRegistry(final TestContext ctx) {

        client = DeviceRegistryAmqpTestSupport.prepareDeviceRegistryClient(vertx);

        client.connect(new ProtonClientOptions())
            .compose(c -> c.getOrCreateTenantClient())
            .setHandler(ctx.asyncAssertSuccess(r -> {
                tenantClient = r;
            }));
    }

    /**
     * Shuts down the device registry and closes the client.
     * 
     * @param ctx The vert.x test context.
     */
    @AfterClass
    public static void shutdown(final TestContext ctx) {

        DeviceRegistryAmqpTestSupport.shutdownDeviceRegistryClient(ctx, vertx, client);

    }

    /**
     * Verifies that a client can use the get operation to retrieve information for an existing
     * tenant.
     * 
     * @param ctx The vert.x test context.
     */
    @Test
    public void testGetTenant(final TestContext ctx) {

        tenantClient
            .get(Constants.DEFAULT_TENANT)
            .setHandler(ctx.asyncAssertSuccess(tenantObject -> {
                ctx.assertEquals(Constants.DEFAULT_TENANT, tenantObject.getTenantId());
            }));
    }

    /**
     * Verifies that a client cannot retrieve information for a tenant that he is not authorized to
     * get information for.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testGetNotConfiguredTenantReturnsUnauthorized(final TestContext ctx) {

        tenantClient
                .get("HTTP_ONLY")
                .setHandler(ctx.asyncAssertFailure(t -> {
                    ctx.assertEquals(
                            HttpURLConnection.HTTP_FORBIDDEN,
                            ((ServiceInvocationException) t).getErrorCode());
                }));
    }

    /**
     * Verifies that a request to retrieve information for a non existing tenant
     * fails with a {@link HttpURLConnection#HTTP_NOT_FOUND}.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testGetConfiguredButNotCreatedTenantReturnsNotFound(final TestContext ctx) {

        tenantClient
                .get("NON_EXISTING_TENANT")
                .setHandler(ctx.asyncAssertFailure(t -> {
                    ctx.assertEquals(
                            HttpURLConnection.HTTP_NOT_FOUND,
                            ((ServiceInvocationException) t).getErrorCode());
                }));
    }

    /**
     * Verifies that a client can use the getByCa operation to retrieve information for
     * a tenant by the subject DN of the trusted certificate authority.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testGetByCaSucceeds(final TestContext ctx) {

        final X500Principal subjectDn = new X500Principal("CN=ca, OU=Hono, O=Eclipse");
        tenantClient
                .get(subjectDn)
                .setHandler(ctx.asyncAssertSuccess(tenantObject -> {
                    ctx.assertEquals(Constants.DEFAULT_TENANT, tenantObject.getTenantId());
                    final JsonObject trustedCa = tenantObject.getProperty(TenantConstants.FIELD_PAYLOAD_TRUSTED_CA);
                    ctx.assertNotNull(trustedCa);
                    final X500Principal trustedSubjectDn = new X500Principal(trustedCa.getString(TenantConstants.FIELD_PAYLOAD_SUBJECT_DN));
                    ctx.assertEquals(subjectDn, trustedSubjectDn);
                }));
    }

    /**
     * Verifies that a request to retrieve information for a tenant by the
     * subject DN of the trusted certificate authority fails with a
     * <em>403 Forbidden</em> if the client is not authorized to retrieve
     * information for the tenant.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testGetByCaFailsIfNotAuthorized(final TestContext ctx) {

        final X500Principal subjectDn = new X500Principal("CN=ca-http, OU=Hono, O=Eclipse");
        tenantClient
                .get(subjectDn)
                .setHandler(ctx.asyncAssertFailure(t -> {
                    ctx.assertEquals(
                            HttpURLConnection.HTTP_FORBIDDEN,
                            ((ServiceInvocationException) t).getErrorCode());
                }));
    }
}
