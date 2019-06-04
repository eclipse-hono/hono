/*******************************************************************************
 * Copyright (c) 2018, 2019 Contributors to the Eclipse Foundation
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
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.util.concurrent.TimeUnit;

import javax.security.auth.x500.X500Principal;

import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.client.TenantClient;
import org.eclipse.hono.client.TenantClientFactory;
import org.eclipse.hono.tests.IntegrationTestSupport;
import org.eclipse.hono.util.TenantObject;
import org.junit.After;
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

/**
 * Tests verifying the behavior of the Device Registry component's Tenant AMQP endpoint.
 */
@RunWith(VertxUnitRunner.class)
public class TenantAmqpIT {

    private static final Vertx vertx = Vertx.vertx();

    private static TenantClientFactory allTenantClientFactory;
    private static TenantClientFactory defaultTenantClientFactory;
    private static TenantClient allTenantClient;
    private static TenantClient defaultTenantClient;
    private static IntegrationTestSupport helper;

    /**
     * Global timeout for all test cases.
     */
    @Rule
    public Timeout globalTimeout = new Timeout(5, TimeUnit.SECONDS);

    /**
     * Creates an HTTP client for managing the fixture of test cases
     * and creates a {@link TenantClient} for invoking operations of the
     * Tenant API.
     *
     * @param ctx The vert.x test context.
     */
    @BeforeClass
    public static void prepareDeviceRegistry(final TestContext ctx) {

        helper = new IntegrationTestSupport(vertx);
        helper.initRegistryClient();

        allTenantClientFactory = DeviceRegistryAmqpTestSupport.prepareTenantClientFactory(vertx,
                IntegrationTestSupport.TENANT_ADMIN_USER, IntegrationTestSupport.TENANT_ADMIN_PWD);

        allTenantClientFactory
        .connect()
        .compose(c -> allTenantClientFactory.getOrCreateTenantClient())
        .setHandler(ctx.asyncAssertSuccess(r -> {
            allTenantClient = r;
        }));

        defaultTenantClientFactory = DeviceRegistryAmqpTestSupport.prepareTenantClientFactory(vertx,
                IntegrationTestSupport.HONO_USER, IntegrationTestSupport.HONO_PWD);

        defaultTenantClientFactory
        .connect()
        .compose(c -> defaultTenantClientFactory.getOrCreateTenantClient())
        .setHandler(ctx.asyncAssertSuccess(r -> {
            defaultTenantClient = r;
        }));
    }

    /**
     * Removes all temporary objects from the registry.
     *
     * @param ctx The vert.x test context.
     */
    @After
    public void cleanUp(final TestContext ctx) {
        helper.deleteObjects(ctx);
    }

    /**
     * Shuts down the device registry and closes the tenantClientFactory.
     *
     * @param ctx The vert.x test context.
     */
    @AfterClass
    public static void shutdown(final TestContext ctx) {

        DeviceRegistryAmqpTestSupport.disconnect(ctx, allTenantClientFactory);
        DeviceRegistryAmqpTestSupport.disconnect(ctx, defaultTenantClientFactory);
    }

    /**
     * Verifies that an existing tenant can be retrieved.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testGetTenant(final TestContext ctx) {

        final String tenantId = helper.getRandomTenantId();
        final TenantObject tenant = TenantObject.from(tenantId, true);

        helper.registry
        .addTenant(JsonObject.mapFrom(tenant))
        .compose(r -> allTenantClient.get(tenantId))
        .setHandler(ctx.asyncAssertSuccess(tenantObject -> {
            ctx.assertTrue(tenantObject.isEnabled());
            ctx.assertEquals(tenantId, tenantObject.getTenantId());
        }));
    }

    /**
     * Verifies that a request to retrieve information for a tenant that the client
     * is not authorized for fails with a 403 status.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testGetTenantFailsIfNotAuthorized(final TestContext ctx) {

        final String tenantId = helper.getRandomTenantId();
        final TenantObject payload = TenantObject.from(tenantId, true);

        helper.registry
        .addTenant(JsonObject.mapFrom(payload))
        .compose(r -> defaultTenantClient.get(tenantId))
        .setHandler(ctx.asyncAssertFailure(t -> {
                ctx.assertEquals(
                        HttpURLConnection.HTTP_FORBIDDEN,
                        ((ServiceInvocationException) t).getErrorCode());
        }));
    }

    /**
     * Verifies that a request to retrieve information for a non existing tenant
     * fails with a 404 status.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testGetTenantFailsForNonExistingTenant(final TestContext ctx) {

        allTenantClient
        .get("non-existing-tenant")
        .setHandler(ctx.asyncAssertFailure(t -> {
            ctx.assertEquals(
                    HttpURLConnection.HTTP_NOT_FOUND,
                    ((ServiceInvocationException) t).getErrorCode());
        }));
    }

    /**
     * Verifies that an existing tenant can be retrieved by a trusted CA's subject DN.
     *
     * @param ctx The vert.x test context.
     * @throws NoSuchAlgorithmException if the public key cannot be generated
     */
    @Test
    public void testGetTenantByCa(final TestContext ctx) throws NoSuchAlgorithmException {

        final String tenantId = helper.getRandomTenantId();
        final X500Principal subjectDn = new X500Principal("CN=ca, OU=Hono, O=Eclipse");
        final PublicKey publicKey = getRandomPublicKey();
        final TenantObject payload = TenantObject.from(tenantId, true)
                .setTrustAnchor(publicKey, subjectDn);

        helper.registry
        .addTenant(JsonObject.mapFrom(payload))
        .compose(r -> allTenantClient.get(subjectDn))
        .setHandler(ctx.asyncAssertSuccess(tenantObject -> {
            try {
                ctx.assertEquals(tenantId, tenantObject.getTenantId());
                ctx.assertEquals(subjectDn, tenantObject.getTrustedCaSubjectDn());
                ctx.assertEquals(publicKey, tenantObject.getTrustAnchor().getCAPublicKey());
            } catch (GeneralSecurityException e) {
                ctx.fail(e);
            }
        }));
    }

    /**
     * Verifies that a request to retrieve information for a tenant by the
     * subject DN of the trusted certificate authority fails with a
     * <em>403 Forbidden</em> status if the client is not authorized to retrieve
     * information for the tenant.
     *
     * @param ctx The vert.x test context.
     * @throws NoSuchAlgorithmException if the public key cannot be generated
     */
    @Test
    public void testGetTenantByCaFailsIfNotAuthorized(final TestContext ctx) throws NoSuchAlgorithmException {

        final String tenantId = helper.getRandomTenantId();
        final X500Principal subjectDn = new X500Principal("CN=ca-http,OU=Hono,O=Eclipse");
        final TenantObject payload = TenantObject.from(tenantId, true)
                .setTrustAnchor(getRandomPublicKey(), subjectDn);

        helper.registry
        .addTenant(JsonObject.mapFrom(payload))
        .compose(r -> defaultTenantClient.get(subjectDn))
        .setHandler(ctx.asyncAssertFailure(t -> {
                ctx.assertEquals(
                        HttpURLConnection.HTTP_FORBIDDEN,
                        ((ServiceInvocationException) t).getErrorCode());
        }));
    }

    private PublicKey getRandomPublicKey() throws NoSuchAlgorithmException {

        final KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(1024);
        final KeyPair keypair = keyGen.genKeyPair();
        return keypair.getPublic();
    }
}
