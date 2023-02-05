/*******************************************************************************
 * Copyright (c) 2019, 2022 Contributors to the Eclipse Foundation
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
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.cert.TrustAnchor;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.security.auth.x500.X500Principal;

import org.assertj.core.api.Assertions;
import org.eclipse.hono.client.registry.TenantClient;
import org.eclipse.hono.service.management.tenant.Tenant;
import org.eclipse.hono.tests.EnabledIfRegistrySupportsFeatures;
import org.eclipse.hono.tests.IntegrationTestSupport;
import org.eclipse.hono.tests.Tenants;
import org.eclipse.hono.util.Adapter;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.CredentialsConstants;
import org.eclipse.hono.util.DataVolume;
import org.eclipse.hono.util.ResourceLimits;
import org.eclipse.hono.util.ResourceLimitsPeriod;
import org.eclipse.hono.util.ResourceLimitsPeriod.PeriodMode;
import org.eclipse.hono.util.TenantConstants;
import org.eclipse.hono.util.TenantObject;
import org.junit.jupiter.api.Test;

import io.opentracing.noop.NoopSpan;
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

        final Map<String, Object> httpAdapterExtensions = Map.of("deployment", Map.of("maxInstances", 4));

        final ResourceLimits resourceLimits = new ResourceLimits()
                .setMaxConnections(100000)
                .setMaxTtl(30L)
                .setDataVolume(new DataVolume(
                        Instant.parse("2019-07-27T14:30:00Z"),
                        new ResourceLimitsPeriod(PeriodMode.days).setNoOfDays(30),
                        2147483648L));

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
                .setExtensions(httpAdapterExtensions));

        // expected tenant object
        final TenantObject expectedTenantObject = TenantObject.from(tenantId, true)
                .setDefaults(defaults)
                .setResourceLimits(resourceLimits)
                .addAdapter(new org.eclipse.hono.util.Adapter(Constants.PROTOCOL_ADAPTER_TYPE_MQTT)
                        .setEnabled(Boolean.TRUE)
                        .setDeviceAuthenticationRequired(Boolean.FALSE))
                .addAdapter(new org.eclipse.hono.util.Adapter(Constants.PROTOCOL_ADAPTER_TYPE_HTTP)
                        .setEnabled(Boolean.TRUE)
                        .setDeviceAuthenticationRequired(Boolean.TRUE)
                        .setExtensions(httpAdapterExtensions));

        getHelper().registry
            .addTenant(tenantId, tenant)
            .compose(ok -> getAdminClient().get(tenantId, NoopSpan.INSTANCE.context()))
            .onComplete(ctx.succeeding(tenantObject -> {
                ctx.verify(() -> {
                    assertThat(tenantObject.getDefaults()).isEqualTo(expectedTenantObject.getDefaults());
                    Assertions.assertThat(tenantObject.getAdapters())
                        .usingRecursiveFieldByFieldElementComparator()
                        .containsAll(expectedTenantObject.getAdapters());
                    assertThat(tenantObject.getResourceLimits().getMaxConnections())
                            .isEqualTo(expectedTenantObject.getResourceLimits().getMaxConnections());
                    assertThat(tenantObject.getResourceLimits().getMaxTtl())
                    .isEqualTo(expectedTenantObject.getResourceLimits().getMaxTtl());
                    assertThat(tenantObject.getResourceLimits().getDataVolume().getMaxBytes())
                            .isEqualTo(expectedTenantObject.getResourceLimits().getDataVolume().getMaxBytes());
                    assertThat(tenantObject.getResourceLimits().getDataVolume().getEffectiveSince()).isEqualTo(
                            expectedTenantObject.getResourceLimits().getDataVolume().getEffectiveSince());
                    assertThat(tenantObject.getResourceLimits().getDataVolume().getPeriod().getMode()).isEqualTo(
                            expectedTenantObject.getResourceLimits().getDataVolume().getPeriod().getMode());
                    assertThat(tenantObject.getResourceLimits().getDataVolume().getPeriod().getNoOfDays())
                            .isEqualTo(expectedTenantObject.getResourceLimits().getDataVolume().getPeriod().getNoOfDays());
                    final JsonObject extensions = tenantObject.getProperty("ext", JsonObject.class);
                    assertThat(extensions.getString("customer")).isEqualTo("ACME Inc.");
                    // implicitly added by DeviceRegistryHttpClient
                    assertThat(extensions.getString(TenantConstants.FIELD_EXT_MESSAGING_TYPE)).isEqualTo(
                            IntegrationTestSupport.getConfiguredMessagingType().name());
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
            .compose(r -> getRestrictedClient().get(tenantId, NoopSpan.INSTANCE.context()))
            .onComplete(ctx.failing(t -> {
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
            .get("non-existing-tenant", NoopSpan.INSTANCE.context())
            .onComplete(ctx.failing(t -> {
                assertErrorCode(t, HttpURLConnection.HTTP_NOT_FOUND);
                ctx.completeNow();
            }));
    }

    /**
     * Verifies that an existing tenant can be retrieved by alias.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    @EnabledIfRegistrySupportsFeatures(tenantAlias = true)
    public void testGetTenantByAlias(final VertxTestContext ctx) {

        final String tenantId = getHelper().getRandomTenantId();

        getHelper().registry.addTenant(tenantId, new Tenant().setAlias("test-alias"))
            .onFailure(ctx::failNow)
            .compose(response -> getAdminClient().get("test-alias", NoopSpan.INSTANCE.context()))
            .onComplete(ctx.succeeding(tenantObject -> {
                ctx.verify(() -> assertThat(tenantObject.getTenantId()).isEqualTo(tenantId));
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
            .compose(r -> getAdminClient().get(subjectDn, NoopSpan.INSTANCE.context()))
            .onComplete(ctx.succeeding(tenantObject -> {
                ctx.verify(() -> {
                    assertThat(tenantObject.getTenantId()).isEqualTo(tenantId);
                    assertThat(tenantObject.getTrustAnchors()).hasSize(1);
                    final TrustAnchor trustAnchor = tenantObject.getTrustAnchors().iterator().next();
                    assertThat(trustAnchor.getCA()).isEqualTo(subjectDn);
                    assertThat(trustAnchor.getCAPublicKey()).isEqualTo(publicKey);
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
            .compose(r -> getRestrictedClient().get(subjectDn, NoopSpan.INSTANCE.context()))
            .onComplete(ctx.failing(t -> {
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
            final KeyPairGenerator keyGen = KeyPairGenerator.getInstance(CredentialsConstants.RSA_ALG);
            keyGen.initialize(1024);
            final KeyPair keypair = keyGen.genKeyPair();
            return keypair.getPublic();
        } catch (final NoSuchAlgorithmException e) {
            // cannot happen because RSA mandatory on every JRE
            throw new IllegalStateException("JRE does not support RSA algorithm");
        }
    }

}
