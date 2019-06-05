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

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import java.net.HttpURLConnection;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;

import javax.security.auth.x500.X500Principal;

import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.client.TenantClient;
import org.eclipse.hono.tests.IntegrationTestSupport;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.TenantConstants;
import org.eclipse.hono.util.TenantObject;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxTestContext;

/**
 * Tests verifying the behavior of the Device Registry component's Tenant AMQP endpoint.
 */
public abstract class TenantApiTests {

    private TenantApiTests() {
        // prevent instantiation
    }

    /**
     * Verifies that an existing tenant can be retrieved.
     * 
     * @param ctx The vert.x test context.
     * @param helper The helper to use for setting up the fixture.
     * @param tenantClient The client to use for interacting with Tenant service.
     */
    public static void testGetTenant(
            final VertxTestContext ctx,
            final IntegrationTestSupport helper,
            final TenantClient tenantClient) {

        final JsonObject defaults = new JsonObject().put("ttl", 30);
        final JsonObject resourceLimits = new JsonObject()
                .put("max-connections", 100000)
                .put("data-volume", new JsonObject()
                        .put("max-bytes", 2147483648L)
                        .put("period-in-days", 30)
                        .put("effective-since", "2019-04-27T12:00:00+00:00"));
        final JsonArray adapterConfig = new JsonArray()
                .add(new JsonObject()
                        .put(TenantConstants.FIELD_ADAPTERS_TYPE, Constants.PROTOCOL_ADAPTER_TYPE_MQTT)
                        .put(TenantConstants.FIELD_ENABLED, true)
                        .put(TenantConstants.FIELD_ADAPTERS_DEVICE_AUTHENTICATION_REQUIRED, true))
                .add(new JsonObject()
                        .put(TenantConstants.FIELD_ADAPTERS_TYPE, Constants.PROTOCOL_ADAPTER_TYPE_HTTP)
                        .put(TenantConstants.FIELD_ENABLED, true)
                        .put(TenantConstants.FIELD_ADAPTERS_DEVICE_AUTHENTICATION_REQUIRED, true)
                        .put("deployment", new JsonObject()
                                .put("maxInstances", 4)));
        final String tenantId = helper.getRandomTenantId();
        final TenantObject tenant = TenantObject.from(tenantId, true)
                .setResourceLimits(resourceLimits)
                .setDefaults(defaults)
                .setAdapterConfigurations(adapterConfig)
                .setProperty("customer", "ACME Inc.");

        helper.registry
        .addTenant(JsonObject.mapFrom(tenant))
        .compose(ok -> tenantClient.get(tenantId))
        .setHandler(ctx.succeeding(tenantObject -> {
            ctx.verify(() -> {
                assertTrue(tenantObject.isEnabled());
                assertThat(tenantObject.getTenantId(), is(tenantId));
                assertThat(tenantObject.getResourceLimits(), is(resourceLimits));
                assertThat(tenantObject.getDefaults(), is(defaults));
                assertThat(tenantObject.getAdapterConfigurations(), is(adapterConfig));
                assertThat(tenantObject.getProperty("customer"), is("ACME Inc."));
            });
            ctx.completeNow();
        }));
    }

    /**
     * Verifies that a request to retrieve information for a tenant that the client
     * is not authorized for fails with a 403 status.
     *
     * @param ctx The vert.x test context.
     * @param helper The helper to use for setting up the fixture.
     * @param tenantClient The client to use for interacting with Tenant service.
     *                     The client must be restricted to access the
     *                     <em>DEFAULT_TENANT</em> only.
     */
    public static void testGetTenantFailsIfNotAuthorized(
            final VertxTestContext ctx,
            final IntegrationTestSupport helper,
            final TenantClient tenantClient) {

        final String tenantId = helper.getRandomTenantId();
        final TenantObject payload = TenantObject.from(tenantId, true);

        helper.registry
        .addTenant(JsonObject.mapFrom(payload))
        .compose(r -> tenantClient.get(tenantId))
        .setHandler(ctx.failing(t -> {
            ctx.verify(() -> assertThat(((ServiceInvocationException) t).getErrorCode(), is(HttpURLConnection.HTTP_FORBIDDEN)));
            ctx.completeNow();
        }));
    }

    /**
     * Verifies that a request to retrieve information for a non existing tenant
     * fails with a 404 status.
     *
     * @param ctx The vert.x test context.
     * @param helper The helper to use for setting up the fixture.
     * @param tenantClient The client to use for interacting with Tenant service.
     */
    public static void testGetTenantFailsForNonExistingTenant(
            final VertxTestContext ctx,
            final IntegrationTestSupport helper,
            final TenantClient tenantClient) {

        tenantClient
        .get("non-existing-tenant")
        .setHandler(ctx.failing(t -> {
            ctx.verify(() -> assertThat(((ServiceInvocationException) t).getErrorCode(), is(HttpURLConnection.HTTP_NOT_FOUND)));
            ctx.completeNow();
        }));
    }

    /**
     * Verifies that an existing tenant can be retrieved by a trusted CA's subject DN.
     *
     * @param ctx The vert.x test context.
     * @param helper The helper to use for setting up the fixture.
     * @param tenantClient The client to use for interacting with Tenant service.
     */
    public static void testGetTenantByCa(
            final VertxTestContext ctx,
            final IntegrationTestSupport helper,
            final TenantClient tenantClient) {

        final String tenantId = helper.getRandomTenantId();
        final X500Principal subjectDn = new X500Principal("CN=ca, OU=Hono, O=Eclipse");
        final PublicKey publicKey = getRandomPublicKey();
        final TenantObject payload = TenantObject.from(tenantId, true)
                .setTrustAnchor(publicKey, subjectDn);

        helper.registry
        .addTenant(JsonObject.mapFrom(payload))
        .compose(r -> tenantClient.get(subjectDn))
        .setHandler(ctx.succeeding(tenantObject -> {
            ctx.verify(() -> {
                assertThat(tenantObject.getTenantId(), is(tenantId));
                assertThat(tenantObject.getTrustedCaSubjectDn(), is(subjectDn));
                assertThat(tenantObject.getTrustAnchor().getCAPublicKey(), is(publicKey));
            });
            ctx.completeNow();
        }));
    }

    /**
     * Verifies that a request to retrieve information for a tenant by the
     * subject DN of the trusted certificate authority fails with a
     * <em>403 Forbidden</em> status if the client is not authorized to retrieve
     * information for the tenant.
     *
     * @param ctx The vert.x test context.
     * @param helper The helper to use for setting up the fixture.
     * @param tenantClient The client to use for interacting with Tenant service.
     *                     The client must be restricted to access the
     *                     <em>DEFAULT_TENANT</em> only.
     */
    public static void testGetTenantByCaFailsIfNotAuthorized(
            final VertxTestContext ctx,
            final IntegrationTestSupport helper,
            final TenantClient tenantClient) {

        final String tenantId = helper.getRandomTenantId();
        final X500Principal subjectDn = new X500Principal("CN=ca-http,OU=Hono,O=Eclipse");
        final TenantObject payload = TenantObject.from(tenantId, true)
                .setTrustAnchor(getRandomPublicKey(), subjectDn);

        helper.registry
        .addTenant(JsonObject.mapFrom(payload))
        .compose(r -> tenantClient.get(subjectDn))
        .setHandler(ctx.failing(t -> {
            ctx.verify(() -> assertThat(((ServiceInvocationException) t).getErrorCode(), is(HttpURLConnection.HTTP_FORBIDDEN)));
            ctx.completeNow();
        }));
    }

    /**
     * Creates a random RSA public key.
     * 
     * @return The key.
     */
    public static PublicKey getRandomPublicKey() {

        try {
            final KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
            keyGen.initialize(1024);
            final KeyPair keypair = keyGen.genKeyPair();
            return keypair.getPublic();
        } catch (NoSuchAlgorithmException e) {
            // cannot happen because RSA mandatory on every JRE
            throw new IllegalStateException("JRE does not support RSA algorithm");
        }
    }
}
