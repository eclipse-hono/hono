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
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.time.Instant;
import java.security.cert.TrustAnchor;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import javax.security.auth.x500.X500Principal;

import org.eclipse.hono.client.TenantClient;
import org.eclipse.hono.service.management.tenant.Adapter;
import org.eclipse.hono.service.management.tenant.Tenant;
import org.eclipse.hono.tests.Tenants;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.DataVolume;
import org.eclipse.hono.util.DataVolumePeriod;
import org.eclipse.hono.util.ResourceLimits;
import org.eclipse.hono.util.TenantConstants;
import org.eclipse.hono.util.TenantObject;
import org.junit.jupiter.api.Test;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxTestContext;

/**
 * Common test cases for the Tenant API.
 */
abstract class TenantApiTests extends DeviceRegistryTestBase {

    /**
     * Gets a client for the Tenant service that has access to all tenants.
     * 
     * @return The client.
     */
    protected abstract TenantClient getAdminClient();

    /**
     * Gets a client for the Tenant service that has access to the
     * {@link Constants#DEFAULT_TENANT} only.
     * 
     * @return The client.
     */
    protected abstract TenantClient getRestrictedClient();

    /**
     * Verifies that an existing tenant can be retrieved.
     * 
     * @param ctx The vert.x test context.
     */
    @Timeout(value = 5, timeUnit = TimeUnit.SECONDS)
    @Test
    public void testGetTenant(final VertxTestContext ctx) {

        final JsonObject defaults = new JsonObject().put("ttl", 30);

        final JsonObject httpAdapterExtensions = new JsonObject()
                .put("deployment", new JsonObject()
                        .put("maxInstances", 4));

        final ResourceLimits resourceLimits = new ResourceLimits()
                .setMaxConnections(100000)
                .setMaxTtl(30L)
                .setDataVolume(new DataVolume()
                        .setMaxBytes(2147483648L)
                        .setEffectiveSince(Instant.parse("2019-07-27T14:30:00Z"))
                        .setPeriod(new DataVolumePeriod()
                                .setMode("days")
                                .setNoOfDays(30)));

        final String tenantId = getHelper().getRandomTenantId();
        final Tenant tenant = new Tenant();
        tenant.setEnabled(true);
        tenant.setResourceLimits(resourceLimits);
        tenant.setDefaults(defaults.getMap());
        tenant.putExtension("customer", "ACME Inc.");

        tenant
           .addAdapterConfig(new Adapter(Constants.PROTOCOL_ADAPTER_TYPE_MQTT)
                .setEnabled(true)
                .setDeviceAuthenticationRequired(false))
           .addAdapterConfig(new Adapter(Constants.PROTOCOL_ADAPTER_TYPE_HTTP)
                .setEnabled(true)
                .setDeviceAuthenticationRequired(true)
                .setExtensions(httpAdapterExtensions.getMap()));

        // expected tenant object
        final TenantObject expectedTenantObject = TenantObject.from(tenantId, true)
                .setDefaults(defaults)
                .setResourceLimits(new JsonObject()
                        .put("max-connections", 100000)
                        .put("max-ttl", 300L)
                        .put("data-volume", new JsonObject()
                                .put("max-bytes", 2147483648L)
                                .put("effective-since", "2019-07-27T14:30:00Z")
                                .put("period", new JsonObject()
                                        .put("mode", "days")
                                        .put("no-of-days", 30))))
                .setAdapterConfigurations(new JsonArray()
                        .add(new JsonObject()
                                .put(TenantConstants.FIELD_ADAPTERS_TYPE, Constants.PROTOCOL_ADAPTER_TYPE_MQTT)
                                .put(TenantConstants.FIELD_ENABLED, true)
                                .put(TenantConstants.FIELD_ADAPTERS_DEVICE_AUTHENTICATION_REQUIRED, false))
                        .add(new JsonObject()
                                .put(TenantConstants.FIELD_ADAPTERS_TYPE, Constants.PROTOCOL_ADAPTER_TYPE_HTTP)
                                .put(TenantConstants.FIELD_ENABLED, true)
                                .put(TenantConstants.FIELD_ADAPTERS_DEVICE_AUTHENTICATION_REQUIRED, true)
                                .put("ext", httpAdapterExtensions))
                        );

        getHelper().registry
        .addTenant(tenantId, tenant)
        .compose(ok -> getAdminClient().get(tenantId))
        .setHandler(ctx.succeeding(tenantObject -> {
            ctx.verify(() -> {
                assertThat(tenantObject.getDefaults()).isEqualTo(expectedTenantObject.getDefaults());
                assertThat(tenantObject.getAdapterConfigurations()).isEqualTo(expectedTenantObject.getAdapterConfigurations());
                assertThat(tenantObject.getResourceLimits().getMaxConnections())
                        .isEqualTo(expectedTenantObject.getResourceLimits().getMaxConnections());
                assertThat(tenantObject.getResourceLimits().getDataVolume().getMaxBytes())
                        .isEqualTo(expectedTenantObject.getResourceLimits().getDataVolume().getMaxBytes());
                assertThat(tenantObject.getResourceLimits().getDataVolume().getEffectiveSince()).isEqualTo(
                        expectedTenantObject.getResourceLimits().getDataVolume().getEffectiveSince());
                assertThat(tenantObject.getResourceLimits().getDataVolume().getPeriod().getMode()).isEqualTo(
                        expectedTenantObject.getResourceLimits().getDataVolume().getPeriod().getMode());
                assertThat(tenantObject.getResourceLimits().getDataVolume().getPeriod().getNoOfDays())
                        .isEqualTo(expectedTenantObject.getResourceLimits().getDataVolume().getPeriod()
                                .getNoOfDays());
                assertThat(tenantObject.getProperty("ext", JsonObject.class).getString("customer")).isEqualTo("ACME Inc.");
            });
            ctx.completeNow();
        }));
    }

    /**
     * Verifies that a request to retrieve information for a tenant that the client
     * is not authorized for fails with a 403 status.
     *
     * @param ctx The vert.x test context.
     */
    @Timeout(value = 5, timeUnit = TimeUnit.SECONDS)
    @Test
    public void testGetTenantFailsIfNotAuthorized(final VertxTestContext ctx) {

        final String tenantId = getHelper().getRandomTenantId();
        final var tenant = new Tenant();
        tenant.setEnabled(true);

        getHelper().registry
        .addTenant(tenantId, tenant)
        .compose(r -> getRestrictedClient().get(tenantId))
        .setHandler(ctx.failing(t -> {
            assertErrorCode(t, HttpURLConnection.HTTP_FORBIDDEN);
            ctx.completeNow();
        }));
    }

    /**
     * Verifies that a request to retrieve information for a non existing tenant
     * fails with a 404 status.
     *
     * @param ctx The vert.x test context.
     */
    @Timeout(value = 5, timeUnit = TimeUnit.SECONDS)
    @Test
    public void testGetTenantFailsForNonExistingTenant(final VertxTestContext ctx) {

        getAdminClient()
        .get("non-existing-tenant")
        .setHandler(ctx.failing(t -> {
            assertErrorCode(t, HttpURLConnection.HTTP_NOT_FOUND);
            ctx.completeNow();
        }));
    }

    /**
     * Verifies that an existing tenant can be retrieved by a trusted CA's subject DN.
     *
     * @param ctx The vert.x test context.
     */
    @Timeout(value = 5, timeUnit = TimeUnit.SECONDS)
    @Test
    public void testGetTenantByCa(final VertxTestContext ctx) {

        final String tenantId = getHelper().getRandomTenantId();
        final X500Principal subjectDn = new X500Principal("CN=ca, OU=Hono, O=Eclipse");
        final PublicKey publicKey = getRandomPublicKey();

        final Tenant tenant = Tenants.createTenantForTrustAnchor(subjectDn, publicKey);

        getHelper().registry
        .addTenant(tenantId, tenant)
        .compose(r -> getAdminClient().get(subjectDn))
        .setHandler(ctx.succeeding(tenantObject -> {
            ctx.verify(() -> {
                final Set<X500Principal> subjectNames = tenantObject.getTrustedCaSubjectDns();
                assertThat(subjectNames).isNotNull();
                assertThat(subjectNames.size()).isEqualTo(1);
                assertThat(tenantObject.getTenantId()).isEqualTo(tenantId);
                assertThat(subjectNames.contains(subjectDn)).isTrue();
                final List<TrustAnchor> anchors = tenantObject.getTrustAnchors();
                assertThat(anchors).isNotNull();
                assertThat(anchors.size()).isEqualTo(1);
                assertThat(anchors.get(0).getCAPublicKey()).isEqualTo(publicKey);
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
     */
    @Timeout(value = 5, timeUnit = TimeUnit.SECONDS)
    @Test
    public void testGetTenantByCaFailsIfNotAuthorized(final VertxTestContext ctx) {

        final String tenantId = getHelper().getRandomTenantId();
        final X500Principal subjectDn = new X500Principal("CN=ca-http,OU=Hono,O=Eclipse");

        final PublicKey publicKey = getRandomPublicKey();

        final Tenant tenant = Tenants.createTenantForTrustAnchor(subjectDn, publicKey);

        getHelper().registry
        .addTenant(tenantId, tenant)
        .compose(r -> getRestrictedClient().get(subjectDn))
        .setHandler(ctx.failing(t -> {
            assertErrorCode(t, HttpURLConnection.HTTP_FORBIDDEN);
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
        } catch (final NoSuchAlgorithmException e) {
            // cannot happen because RSA mandatory on every JRE
            throw new IllegalStateException("JRE does not support RSA algorithm");
        }
    }

}
