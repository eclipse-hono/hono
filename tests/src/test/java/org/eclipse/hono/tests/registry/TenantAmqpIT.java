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

package org.eclipse.hono.tests.registry;

import java.net.HttpURLConnection;
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
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.TenantConstants;
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

    private static TenantClientFactory tenantClientFactory;
    private static TenantClient tenantClient;
    private static IntegrationTestSupport helper;



    /**
     * Global timeout for all test cases.
     */
    @Rule
    public Timeout globalTimeout = new Timeout(5, TimeUnit.SECONDS);

    /**
     * Starts the device registry, connects a tenantClientFactory and provides a tenant API tenantClientFactory.
     *
     * @param ctx The vert.x test context.
     */
    @BeforeClass
    public static void prepareDeviceRegistry(final TestContext ctx) {

        helper = new IntegrationTestSupport(vertx);
        helper.initRegistryClient(ctx);

        tenantClientFactory = DeviceRegistryAmqpTestSupport.prepareTenantClientFactory(vertx,
                IntegrationTestSupport.HONO_USER, IntegrationTestSupport.HONO_PWD);

        tenantClientFactory.connect()
            .compose(c -> tenantClientFactory.getOrCreateTenantClient())
            .setHandler(ctx.asyncAssertSuccess(r -> {
                tenantClient = r;
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
        helper.registry.removeTenant(Constants.DEFAULT_TENANT);
    }

    /**
     * Shuts down the device registry and closes the tenantClientFactory.
     *
     * @param ctx The vert.x test context.
     */
    @AfterClass
    public static void shutdown(final TestContext ctx) {

        DeviceRegistryAmqpTestSupport.disconnect(ctx, tenantClientFactory);

    }

    /**
     * Verifies that a tenantClientFactory can use the get operation to retrieve information for an existing
     * tenant.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testGetTenant(final TestContext ctx) {

        final String tenantId = Constants.DEFAULT_TENANT;
        final TenantObject payload = new TenantObject()
                .setTenantId(tenantId);

        helper.registry.addTenant( JsonObject.mapFrom(payload))
                .compose(r -> tenantClient.get(tenantId))
                .setHandler(ctx.asyncAssertSuccess(tenantObject -> {
                   ctx.assertEquals(tenantId, tenantObject.getTenantId());
           }));
    }

    /**
     * Verifies that a tenantClientFactory cannot retrieve information for a tenant that he is not authorized to
     * get information for.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testGetNotConfiguredTenantReturnsUnauthorized(final TestContext ctx) {

        final String tenantId = helper.getRandomTenantId();
        final TenantObject payload = new TenantObject()
                .setTenantId(tenantId)
                .addAdapterConfiguration( new JsonObject()
                                .put(TenantConstants.FIELD_ADAPTERS_TYPE, Constants.PROTOCOL_ADAPTER_TYPE_HTTP)
                                .put(TenantConstants.FIELD_ENABLED, true)
                                .put(TenantConstants.FIELD_ADAPTERS_DEVICE_AUTHENTICATION_REQUIRED, true));

        helper.registry.addTenant(JsonObject.mapFrom(payload))
                .compose(r -> tenantClient.get(tenantId))
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
     * Verifies that a tenantClientFactory can use the getByCa operation to retrieve information for
     * a tenant by the subject DN of the trusted certificate authority.
     *
     * @param ctx The vert.x test context.
     * @throws NoSuchAlgorithmException if the public key cannot be generated
     */
    @Test
    public void testGetByCaSucceeds(final TestContext ctx) throws NoSuchAlgorithmException {

        final String tenantId = Constants.DEFAULT_TENANT;
        final X500Principal subjectDn = new X500Principal("CN=ca, OU=Hono, O=Eclipse");
        final TenantObject payload = new TenantObject()
                .setTenantId(tenantId)
                .setTrustAnchor(getRandomPublicKey(), subjectDn);

        helper.registry.addTenant(JsonObject.mapFrom(payload))
                .compose(r -> tenantClient.get(subjectDn))
                .setHandler(ctx.asyncAssertSuccess(tenantObject -> {

                    ctx.assertEquals(tenantId, tenantObject.getTenantId());
                    final JsonObject trustedCa = tenantObject.getProperty(TenantConstants.FIELD_PAYLOAD_TRUSTED_CA);
                    ctx.assertNotNull(trustedCa);
                    final X500Principal trustedSubjectDn = new X500Principal(trustedCa.getString(TenantConstants.FIELD_PAYLOAD_SUBJECT_DN));
                    ctx.assertEquals(subjectDn, trustedSubjectDn);
                }));
    }

    /**
     * Verifies that a request to retrieve information for a tenant by the
     * subject DN of the trusted certificate authority fails with a
     * <em>403 Forbidden</em> if the tenantClientFactory is not authorized to retrieve
     * information for the tenant.
     *
     * @param ctx The vert.x test context.
     * @throws NoSuchAlgorithmException if the public key cannot be generated
     */
    @Test
    public void testGetByCaFailsIfNotAuthorized(final TestContext ctx) throws NoSuchAlgorithmException {
        final String tenantId = helper.getRandomTenantId();
        final X500Principal subjectDn = new X500Principal("CN=ca-http,OU=Hono,O=Eclipse");
        final TenantObject payload = new TenantObject()
                .setTenantId(tenantId)
                .setTrustAnchor(getRandomPublicKey(), subjectDn)
                .addAdapterConfiguration(new JsonObject()
                        .put(TenantConstants.FIELD_ADAPTERS_TYPE, Constants.PROTOCOL_ADAPTER_TYPE_HTTP)
                        .put(TenantConstants.FIELD_ENABLED, true)
                        .put(TenantConstants.FIELD_ADAPTERS_DEVICE_AUTHENTICATION_REQUIRED, true));

        helper.registry.addTenant(JsonObject.mapFrom(payload))
                .compose(r ->  tenantClient.get(subjectDn))
                .setHandler(ctx.asyncAssertFailure(t -> {
                        ctx.assertEquals(
                                HttpURLConnection.HTTP_FORBIDDEN,
                                ((ServiceInvocationException) t).getErrorCode());
                    }));
    }

    /**
     * Verify that a request to add a tenant returns a 400 status code when the request payload defines a trusted CA
     * configuration containing an invalid Base64 encoding for its <em>public-key</em> property.
     *
     * @param ctx The Vert.x test context.
     */
    @Test
    public void testAddTenantFailsIfTrustedCaPublicKeyHasInvalidEncoding(final TestContext ctx) {

        final JsonObject malformedTrustedCa = new JsonObject()
                .put(TenantConstants.FIELD_PAYLOAD_SUBJECT_DN, "CN=test")
                .put(TenantConstants.FIELD_PAYLOAD_PUBLIC_KEY, "NotBase64Encoded");

        final String tenantId = Constants.DEFAULT_TENANT;
        final TenantObject payload = TenantObject.from(tenantId, true)
                .setProperty(TenantConstants.FIELD_PAYLOAD_TRUSTED_CA, malformedTrustedCa);

        helper.registry.addTenant(JsonObject.mapFrom(payload))
        .setHandler(ctx.asyncAssertFailure(t -> {
                    ctx.assertEquals(HttpURLConnection.HTTP_BAD_REQUEST,
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
