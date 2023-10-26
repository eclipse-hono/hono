/*******************************************************************************
 * Copyright (c) 2016, 2023 Contributors to the Eclipse Foundation
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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import static com.google.common.truth.Truth.assertThat;

import java.net.HttpURLConnection;
import java.security.PublicKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.eclipse.hono.service.management.SearchResult;
import org.eclipse.hono.service.management.device.Device;
import org.eclipse.hono.service.management.tenant.RegistrationLimits;
import org.eclipse.hono.service.management.tenant.Tenant;
import org.eclipse.hono.service.management.tenant.TenantWithId;
import org.eclipse.hono.service.management.tenant.TrustedCertificateAuthority;
import org.eclipse.hono.tests.EnabledIfRegistrySupportsFeatures;
import org.eclipse.hono.tests.IntegrationTestSupport;
import org.eclipse.hono.tests.Tenants;
import org.eclipse.hono.util.Adapter;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.CredentialsConstants;
import org.eclipse.hono.util.RegistryManagementConstants;
import org.eclipse.hono.util.ResourceLimits;
import org.eclipse.hono.util.TenantConstants;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.type.TypeReference;

import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.json.jackson.JacksonCodec;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

/**
 * Tests verifying the Device Registry component by making HTTP requests to its
 * Tenant HTTP endpoint and validating the corresponding responses.
 */
@ExtendWith(VertxExtension.class)
@Timeout(value = 5, timeUnit = TimeUnit.SECONDS)
public class TenantManagementIT extends DeviceRegistryTestBase {

    private static final Logger LOG = LoggerFactory.getLogger(TenantManagementIT.class);

    /**
     * Verifies that the service accepts an add tenant request containing a valid tenant structure
     * and that the response contains a <em>Location</em> header for the created resource.
     *
     * @param context The vert.x test context.
     */
    @Test
    public void testAddTenantSucceeds(final VertxTestContext context) {

        final String tenantId = getHelper().getRandomTenantId();
        final Tenant tenant = buildTenantPayload();
        getHelper().registry.addTenant(tenantId, tenant)
            .onComplete(context.succeeding(response -> {
                context.verify(() -> {
                    final String idFromLocation = assertLocationHeader(response.headers());
                    assertThat(idFromLocation).isEqualTo(tenantId);
                    assertThat(response.getHeader(HttpHeaders.ETAG.toString())).isNotNull();
                });
                context.completeNow();
            }));
    }

    /**
     * Verifies that the service successfully create a tenant from a request with an empty body.
     *
     * @param context The vert.x test context.
     */
    @Test
    public void testAddTenantSucceedsForEmptyBody(final VertxTestContext context) {

        final String tenantId = getHelper().getRandomTenantId();
        getHelper().registry.addTenant(tenantId)
            .onFailure(context::failNow)
            .compose(ok -> getHelper().registry.getTenant(tenantId, HttpURLConnection.HTTP_OK))
            .onComplete(context.succeedingThenComplete());
    }

    /**
     * Verifies that the service successfully create a tenant with a generated tenant ID.
     *
     * @param context The vert.x test context.
     */
    @Test
    public void testAddTenantSucceedsWithAGeneratedId(final VertxTestContext context) {

        getHelper().registry.addTenant()
            .onComplete(context.succeeding(httpResponse -> {
                context.verify(() -> {
                    final String generatedId = assertLocationHeader(httpResponse.headers());
                    // update the global tenantId value for cleanup
                    getHelper().addTenantIdForRemoval(generatedId);
                    assertThat(httpResponse.getHeader(HttpHeaders.ETAG.toString())).isNotNull();
                });
                context.completeNow();
            }));
    }

    /**
     * Verifies that the service successfully creates a tenant from a request having a tenant anchor
     * with an ID and an another without an ID.
     *
     * @param context The Vert.x test context.
     */
    @Test
    public void testAddTenantSucceedsForConfigurationWithMissingTrustAnchorIds(final VertxTestContext context) {

        final PublicKey publicKey = TenantApiTests.getRandomPublicKey();
        final TrustedCertificateAuthority trustAnchor1 = Tenants
                .createTrustAnchor("test-ca", "CN=test-dn", publicKey.getEncoded(), publicKey.getAlgorithm(),
                        Instant.now(), Instant.now().plus(365, ChronoUnit.DAYS));
        final TrustedCertificateAuthority trustAnchor2 = Tenants
                .createTrustAnchor(null, "CN=test-dn-1", publicKey.getEncoded(), publicKey.getAlgorithm(),
                        Instant.now(), Instant.now().plus(365, ChronoUnit.DAYS));
        final Tenant tenant = new Tenant()
                .setTrustedCertificateAuthorities(List.of(trustAnchor1, trustAnchor2));
        final String tenantId = getHelper().getRandomTenantId();

        getHelper().registry.addTenant(tenantId, tenant)
            .compose(ok -> getHelper().registry.getTenant(tenantId))
            .onComplete(context.succeeding(httpResponse -> {
                context.verify(() -> {
                    final JsonObject response = httpResponse.bodyAsJsonObject();
                    assertThat(response.getJsonArray(TenantConstants.FIELD_PAYLOAD_TRUSTED_CA)).hasSize(2);
                    final JsonArray trustAnchors = response
                            .getJsonArray(RegistryManagementConstants.FIELD_PAYLOAD_TRUSTED_CA);
                    assertThat(trustAnchors.getJsonObject(0).getString(RegistryManagementConstants.FIELD_ID))
                            .isEqualTo("test-ca");
                    assertNotNull(trustAnchors.getJsonObject(1).getString(RegistryManagementConstants.FIELD_ID));
                });
                context.completeNow();
            }));
    }

    /**
     * Verifies that the service successfully creates a tenant containing a CA that is also used by
     * an existing tenant that belongs to the same trust anchor group.
     *
     * @param context The Vert.x test context.
     */
    @Test
    @EnabledIfRegistrySupportsFeatures(trustAnchorGroups = true)
    public void testAddTenantSucceedsForCaSharedWithinTrustAnchorGroup(final VertxTestContext context) {

        final PublicKey publicKey = TenantApiTests.getRandomPublicKey();
        final TrustedCertificateAuthority trustAnchor = Tenants.createTrustAnchor(
                "test-ca",
                "CN=test-dn",
                publicKey.getEncoded(),
                publicKey.getAlgorithm(),
                Instant.now(),
                Instant.now().plus(365, ChronoUnit.DAYS));
        final Tenant tenant = new Tenant()
                .setTrustAnchorGroup("test-group")
                .setTrustedCertificateAuthorities(List.of(trustAnchor));
        final String tenantId = getHelper().getRandomTenantId();

        getHelper().registry.addTenant(tenantId, tenant)
            .onFailure(context::failNow)
            .compose(ok -> getHelper().registry.addTenant(getHelper().getRandomTenantId(), tenant))
            .onComplete(context.succeedingThenComplete());
    }

    /**
     * Verifies that a request to create a tenant with a body that exceeds the registry's max payload limit
     * fails with a {@link HttpURLConnection#HTTP_ENTITY_TOO_LARGE} status code.
     *
     * @param context The vert.x test context.
     */
    @Test
    public void testAddTenantFailsForRequestPayloadExceedingLimit(final VertxTestContext context)  {

        final var data = new char[3000];
        Arrays.fill(data, 'x');
        final Tenant payload = new Tenant();
        payload.setExtensions(Map.of("data", new String(data)));
        final String tenantId = getHelper().getRandomTenantId();

        getHelper().registry.addTenant(tenantId, payload, HttpURLConnection.HTTP_ENTITY_TOO_LARGE)
            .onComplete(context.succeeding(response -> {
                context.verify(() -> IntegrationTestSupport.assertErrorPayload(response));
                context.completeNow();
            }));
    }

    /**
     * Verifies that a correctly filled JSON payload to add a tenant for an already existing record is
     * responded with {@link HttpURLConnection#HTTP_CONFLICT} and a non empty error response message.
     *
     * @param context The vert.x test context.
     */
    @Test
    public void testAddTenantFailsForDuplicateTenantId(final VertxTestContext context)  {

        final Tenant payload = buildTenantPayload();
        final String tenantId = getHelper().getRandomTenantId();

        getHelper().registry.addTenant(tenantId, payload)
            .onFailure(context::failNow)
            // now try to add the tenant again
            .compose(ar -> getHelper().registry.addTenant(tenantId, payload, HttpURLConnection.HTTP_CONFLICT))
            .onComplete(context.succeeding(response -> {
                context.verify(() -> IntegrationTestSupport.assertErrorPayload(response));
                context.completeNow();
            }));
    }

    /**
     * Verifies that the registry rejects an attempt to create a tenant with an alias that is already used
     * by another tenant.
     *
     * @param context The vert.x test context.
     */
    @Test
    @EnabledIfRegistrySupportsFeatures(tenantAlias = true)
    public void testAddTenantFailsForDuplicateTenantAlias(final VertxTestContext context)  {

        final Tenant payload = new Tenant().setAlias("the-alias");
        final String tenantId = getHelper().getRandomTenantId();

        getHelper().registry.addTenant(tenantId, payload)
            .onFailure(context::failNow)
            // now try to add the tenant again
            .compose(ar -> getHelper().registry.addTenant(
                    getHelper().getRandomTenantId(),
                    payload,
                    HttpURLConnection.HTTP_CONFLICT))
            .onComplete(context.succeeding(response -> {
                context.verify(() -> IntegrationTestSupport.assertErrorPayload(response));
                context.completeNow();
            }));
    }

    /**
     * Verifies that the service returns a 400 status code for an add tenant request with a Content-Type
     * other than application/json.
     *
     * @param context The vert.x test context.
     */
    @Test
    public void testAddTenantFailsForWrongContentType(final VertxTestContext context)  {

        final String tenantId = getHelper().getRandomTenantId();
        getHelper().registry.addTenant(
                tenantId,
                buildTenantPayload(),
                "application/x-www-form-urlencoded",
                HttpURLConnection.HTTP_BAD_REQUEST)
            .onComplete(context.succeeding(response -> {
                context.verify(() -> IntegrationTestSupport.assertErrorPayload(response));
                context.completeNow();
            }));
    }

    /**
     * Verifies that the a tenant cannot be created if the tenant ID is invalid.
     *
     * @param context The vert.x test context.
     */
    @Test
    public void testAddTenantFailsForInvalidTenantId(final VertxTestContext context) {

        getHelper().registry.addTenant("invalid tenantid$", null, HttpURLConnection.HTTP_BAD_REQUEST)
            .onComplete(context.succeeding(response -> {
                context.verify(() -> IntegrationTestSupport.assertErrorPayload(response));
                context.completeNow();
            }));
    }

    /**
     * Verifies that the service returns a 400 status code for an add tenant request containing
     * a malformed trust configuration (i.e an invalid Base64 encoding value of the trust CA's certificate).
     *
     * @param context The Vert.x test context.
     */
    @Test
    public void testAddTenantFailsForMalformedTrustConfiguration(final VertxTestContext context) {

        final Tenant requestBody = Tenants.createTenantForTrustAnchor("CN=test-dn", "NotBased64Encoded".getBytes(), CredentialsConstants.RSA_ALG);
        final String tenantId = getHelper().getRandomTenantId();

        getHelper().registry.addTenant(
                tenantId,
                requestBody,
                HttpURLConnection.HTTP_BAD_REQUEST)
            .onComplete(context.succeeding(response -> {
                context.verify(() -> IntegrationTestSupport.assertErrorPayload(response));
                context.completeNow();
            }));
    }

    /**
     * Verifies that the service returns a 409 status code for an add tenant request containing a
     * CA that is already in use by another tenant that does not belong to the same trust anchor group.
     *
     * @param context The Vert.x test context.
     */
    @Test
    public void testAddTenantFailsForConfigurationWithDuplicateTrustAnchor(final VertxTestContext context) {

        final PublicKey publicKey = TenantApiTests.getRandomPublicKey();
        final TrustedCertificateAuthority trustAnchor = Tenants.createTrustAnchor(
                "test-ca",
                "CN=test-dn",
                publicKey.getEncoded(),
                publicKey.getAlgorithm(),
                Instant.now(),
                Instant.now().plus(365, ChronoUnit.DAYS));
        final Tenant tenant = new Tenant().setTrustedCertificateAuthorities(List.of(trustAnchor));
        final String tenantId = getHelper().getRandomTenantId();

        getHelper().registry.addTenant(tenantId, tenant)
            .onFailure(context::failNow)
            .compose(ok -> getHelper().registry.addTenant(
                getHelper().getRandomTenantId(),
                tenant,
                HttpURLConnection.HTTP_CONFLICT))
            .onComplete(context.succeeding(response -> {
                context.verify(() -> IntegrationTestSupport.assertErrorPayload(response));
                context.completeNow();
            }));
    }

    /**
     * Verifies that the service returns a 400 status code for an add tenant request containing a malformed trust
     * configuration (i.e with non-unique trust anchor IDs).
     *
     * @param context The Vert.x test context.
     */
    @Test
    public void testAddTenantFailsForConfigurationWithNonUniqueTrustAnchorIds(final VertxTestContext context) {
        final PublicKey publicKey = TenantApiTests.getRandomPublicKey();
        final TrustedCertificateAuthority trustAnchor1 = Tenants
                .createTrustAnchor("test-ca", "CN=test-dn", publicKey.getEncoded(), publicKey.getAlgorithm(),
                        Instant.now(), Instant.now().plus(365, ChronoUnit.DAYS));
        final TrustedCertificateAuthority trustAnchor2 = Tenants
                .createTrustAnchor("test-ca", "CN=test-dn", publicKey.getEncoded(), publicKey.getAlgorithm(),
                        Instant.now().plus(366, ChronoUnit.DAYS), Instant.now().plus(730, ChronoUnit.DAYS));
        final Tenant tenant = new Tenant()
                .setTrustedCertificateAuthorities(List.of(trustAnchor1, trustAnchor2));
        final String tenantId = getHelper().getRandomTenantId();

        getHelper().registry.addTenant(
                tenantId,
                tenant,
                HttpURLConnection.HTTP_BAD_REQUEST)
            .onComplete(context.succeeding(response -> {
                context.verify(() -> IntegrationTestSupport.assertErrorPayload(response));
                context.completeNow();
            }));
    }

    /**
     * Verifies that the service returns a 400 status code for a request to add a tenant containing
     * multiple adapter configurations for the same adapter type.
     *
     * @param context The Vert.x test context.
     */
    @Test
    public void testAddTenantFailsForMalformedAdapterConfiguration(final VertxTestContext context) {

        final Adapter httpAdapter = new Adapter(Constants.PROTOCOL_ADAPTER_TYPE_HTTP);
        final JsonObject requestBody = JsonObject.mapFrom(buildTenantPayload());
        requestBody.getJsonArray(RegistryManagementConstants.FIELD_ADAPTERS)
            .add(JsonObject.mapFrom(httpAdapter));
        final String tenantId = getHelper().getRandomTenantId();

        getHelper().registry.addTenant(
                tenantId,
                requestBody,
                "application/json",
                HttpURLConnection.HTTP_BAD_REQUEST)
            .onComplete(context.succeeding(response -> {
                context.verify(() -> IntegrationTestSupport.assertErrorPayload(response));
                context.completeNow();
            }));
    }

    /**
     * Verifies that a request to register a tenant that contains unsupported properties
     * fails with a 400 status.
     *
     * @param context The Vert.x test context.
     */
    @Test
    public void testAddTenantFailsForUnknownProperties(final VertxTestContext context) {

        final JsonObject requestBody = JsonObject.mapFrom(new Tenant());
        requestBody.put("unexpected", "property");
        final String tenantId = getHelper().getRandomTenantId();

        getHelper().registry.addTenant(
                tenantId,
                requestBody,
                "application/json",
                HttpURLConnection.HTTP_BAD_REQUEST)
            .onComplete(context.succeeding(response -> {
                context.verify(() -> IntegrationTestSupport.assertErrorPayload(response));
                context.completeNow();
            }));
    }

    /**
     * Verifies that the service accepts an update tenant request for an existing tenant.
     *
     * @param context The vert.x test context.
     */
    @Test
    public void testUpdateTenantSucceeds(final VertxTestContext context) {

        final Tenant orig = buildTenantPayload();
        final Tenant altered = new Tenant();
        altered.setExtensions(orig.getExtensions());
        altered.setAdapters(orig.getAdapters());
        altered.setEnabled(Boolean.FALSE);
        final AtomicReference<String> latestVersion = new AtomicReference<>();
        final String tenantId = getHelper().getRandomTenantId();

        getHelper().registry.addTenant(tenantId, orig)
            .compose(httpResponse -> {
                latestVersion.set(httpResponse.getHeader(HttpHeaders.ETAG.toString()));
                assertThat(latestVersion.get()).isNotNull();
                return getHelper().registry.updateTenant(tenantId, altered, HttpURLConnection.HTTP_NO_CONTENT);
            })
            .compose(httpResponse -> {
                final String updatedVersion = httpResponse.getHeader(HttpHeaders.ETAG.toString());
                assertThat(updatedVersion).isNotNull();
                assertThat(updatedVersion).isNotEqualTo(latestVersion.get());
                return getHelper().registry.getTenant(tenantId);
            })
            .onComplete(context.succeeding(httpResponse -> {
                // compare the changed field only
                context.verify(() -> {
                    assertFalse(httpResponse.bodyAsJsonObject().getBoolean(TenantConstants.FIELD_ENABLED, Boolean.TRUE));
                });
                context.completeNow();
            }));
    }

    /**
     * Verifies that the service successfully updates a tenant from a request having a tenant anchor
     * with an ID and an another without an ID.
     *
     * @param context The Vert.x test context.
     */
    @Test
    public void testUpdateTenantSucceedsForConfigurationWithMissingTrustAnchorIds(final VertxTestContext context) {
        final PublicKey publicKey = TenantApiTests.getRandomPublicKey();
        final TrustedCertificateAuthority trustAnchor1 = Tenants
                .createTrustAnchor("test-ca", "CN=test-dn", publicKey.getEncoded(), publicKey.getAlgorithm(),
                        Instant.now(), Instant.now().plus(365, ChronoUnit.DAYS));
        final TrustedCertificateAuthority trustAnchor2 = Tenants
                .createTrustAnchor(null, "CN=test-dn-1", publicKey.getEncoded(), publicKey.getAlgorithm(),
                        Instant.now(), Instant.now().plus(365, ChronoUnit.DAYS));
        final Tenant tenantForUpdate = new Tenant()
                .setTrustedCertificateAuthorities(List.of(trustAnchor1, trustAnchor2));
        final String tenantId = getHelper().getRandomTenantId();

        getHelper().registry.addTenant(tenantId, new Tenant())
                .compose(ok -> getHelper().registry.updateTenant(tenantId, tenantForUpdate, HttpURLConnection.HTTP_NO_CONTENT))
                .compose(ok -> getHelper().registry.getTenant(tenantId))
                .onComplete(context.succeeding(httpResponse -> {
                    context.verify(() -> {
                        final JsonObject response = httpResponse.bodyAsJsonObject();
                        assertThat(response.getJsonArray(TenantConstants.FIELD_PAYLOAD_TRUSTED_CA)).hasSize(2);
                        final JsonArray trustAnchors = response
                                .getJsonArray(RegistryManagementConstants.FIELD_PAYLOAD_TRUSTED_CA);
                        assertThat(trustAnchors.getJsonObject(0).getString(RegistryManagementConstants.FIELD_ID))
                                .isEqualTo("test-ca");
                        assertNotNull(trustAnchors.getJsonObject(1).getString(RegistryManagementConstants.FIELD_ID));
                    });
                    context.completeNow();
                }));
    }

    /**
     * Verifies that the service successfully updates a tenant with a CA that is also used by
     * an existing tenant that belongs to the same trust anchor group.
     *
     * @param context The Vert.x test context.
     */
    @Test
    @EnabledIfRegistrySupportsFeatures(trustAnchorGroups = true)
    public void testUpdateTenantSucceedsForCaSharedWithinTrustAnchorGroup(final VertxTestContext context) {

        final PublicKey publicKey = TenantApiTests.getRandomPublicKey();
        final TrustedCertificateAuthority trustAnchor = Tenants.createTrustAnchor(
                "test-ca",
                "CN=test-dn",
                publicKey.getEncoded(),
                publicKey.getAlgorithm(),
                Instant.now(),
                Instant.now().plus(365, ChronoUnit.DAYS));
        final Tenant tenant = new Tenant()
                .setTrustAnchorGroup("test-group")
                .setTrustedCertificateAuthorities(List.of(trustAnchor));
        final String tenantId = getHelper().getRandomTenantId();

        getHelper().registry.addTenant(getHelper().getRandomTenantId(), tenant)
            .onFailure(context::failNow)
            .compose(ok -> getHelper().registry.addTenant(tenantId))
            .onFailure(context::failNow)
            .compose(ok -> getHelper().registry.updateTenant(tenantId, tenant, HttpURLConnection.HTTP_NO_CONTENT))
            .onComplete(context.succeedingThenComplete());
    }

    /**
     * Verifies that setting an empty list of trusted CAs on multiple tenants does not result in a unique key
     * violation.
     *
     * @param context The Vert.x test context.
     */
    @Test
    public void testUpdateTenantPreventsEmptyCaArray(final VertxTestContext context) {

        final String tenantId = getHelper().getRandomTenantId();
        final var tenantTwoId = getHelper().getRandomTenantId();
        final PublicKey publicKey = TenantApiTests.getRandomPublicKey();
        final TrustedCertificateAuthority trustAnchor1 = Tenants
                .createTrustAnchor("test-ca", "CN=test-dn-1", publicKey.getEncoded(), publicKey.getAlgorithm(),
                        Instant.now(), Instant.now().plus(365, ChronoUnit.DAYS));
        final TrustedCertificateAuthority trustAnchor2 = Tenants
                .createTrustAnchor(null, "CN=test-dn-2", publicKey.getEncoded(), publicKey.getAlgorithm(),
                        Instant.now(), Instant.now().plus(365, ChronoUnit.DAYS));

        getHelper().registry.addTenant(tenantId, new Tenant().setTrustedCertificateAuthorities(List.of(trustAnchor1)))
            .compose(ok -> getHelper().registry.addTenant(
                    tenantTwoId,
                    new Tenant().setTrustedCertificateAuthorities(List.of(trustAnchor2))))
            .compose(ok -> getHelper().registry.updateTenant(
                    tenantId,
                    new Tenant().setTrustedCertificateAuthorities(List.of()),
                    HttpURLConnection.HTTP_NO_CONTENT))
            .compose(ok -> getHelper().registry.updateTenant(
                    tenantTwoId,
                    new Tenant().setTrustedCertificateAuthorities(List.of()),
                    HttpURLConnection.HTTP_NO_CONTENT))
            .onComplete(context.succeedingThenComplete());
    }

    /**
     * Verifies that the service rejects an update request for a non-existing tenant.
     *
     * @param context The vert.x test context.
     */
    @Test
    public void testUpdateTenantFailsForNonExistingTenant(final VertxTestContext context) {

        final Tenant altered = buildTenantPayload();

        getHelper().registry.updateTenant("non-existing-tenant", altered, HttpURLConnection.HTTP_NOT_FOUND)
            .onComplete(context.succeeding(response -> {
                context.verify(() -> IntegrationTestSupport.assertErrorPayload(response));
                context.completeNow();
            }));
    }

    /**
     * Verifies that the service returns a 400 status code for an update tenant request containing a malformed trust
     * configuration (i.e with non-unique trust anchor IDs).
     *
     * @param context The Vert.x test context.
     */
    @Test
    public void testUpdateTenantFailsForConfigurationWithNonUniqueTrustAnchorIds(final VertxTestContext context) {
        final PublicKey publicKey = TenantApiTests.getRandomPublicKey();
        final TrustedCertificateAuthority trustAnchor1 = Tenants
                .createTrustAnchor("test-ca", "CN=test-dn", publicKey.getEncoded(), publicKey.getAlgorithm(),
                        Instant.now(), Instant.now().plus(365, ChronoUnit.DAYS));
        final TrustedCertificateAuthority trustAnchor2 = Tenants
                .createTrustAnchor("test-ca", "CN=test-dn", publicKey.getEncoded(), publicKey.getAlgorithm(),
                        Instant.now().plus(366, ChronoUnit.DAYS), Instant.now().plus(730, ChronoUnit.DAYS));
        final Tenant tenantForUpdate = new Tenant()
                .setTrustedCertificateAuthorities(List.of(trustAnchor1, trustAnchor2));
        final String tenantId = getHelper().getRandomTenantId();

        getHelper().registry.addTenant(tenantId, new Tenant())
                .compose(ok -> getHelper().registry.updateTenant(tenantId, tenantForUpdate,
                        HttpURLConnection.HTTP_BAD_REQUEST))
                .onComplete(context.succeeding(response -> {
                    context.verify(() -> IntegrationTestSupport.assertErrorPayload(response));
                    context.completeNow();
                }));
    }

    /**
     * Verifies that the service returns a 409 status code for an update tenant request containing a
     * CA that is already in use by another tenant that does not belong to the same trust anchor group.
     *
     * @param context The Vert.x test context.
     */
    @Test
    public void testUpdateTenantFailsForConfigurationWithDuplicateTrustAnchor(final VertxTestContext context) {

        final PublicKey publicKey = TenantApiTests.getRandomPublicKey();
        final TrustedCertificateAuthority trustAnchor = Tenants.createTrustAnchor(
                "test-ca",
                "CN=test-dn",
                publicKey.getEncoded(),
                publicKey.getAlgorithm(),
                Instant.now(),
                Instant.now().plus(365, ChronoUnit.DAYS));
        final Tenant tenant = new Tenant().setTrustedCertificateAuthorities(List.of(trustAnchor));
        final String tenantId = getHelper().getRandomTenantId();

        getHelper().registry.addTenant(getHelper().getRandomTenantId(), tenant)
            .onFailure(context::failNow)
            .compose(ok -> getHelper().registry.addTenant(tenantId, new Tenant()))
            .onFailure(context::failNow)
            .compose(ok -> getHelper().registry.updateTenant(
                    tenantId,
                    tenant,
                    HttpURLConnection.HTTP_CONFLICT))
            .onComplete(context.succeeding(response -> {
                context.verify(() -> IntegrationTestSupport.assertErrorPayload(response));
                context.completeNow();
            }));
    }

    /**
     * Verifies that the registry rejects an attempt to update a tenant with an alias that is already used
     * by another tenant.
     *
     * @param context The vert.x test context.
     */
    @Test
    @EnabledIfRegistrySupportsFeatures(tenantAlias = true)
    public void testUpdateTenantFailsForDuplicateTenantAlias(final VertxTestContext context)  {

        final Tenant payload = new Tenant().setAlias("the-alias");
        final String tenantId = getHelper().getRandomTenantId();

        getHelper().registry.addTenant(getHelper().getRandomTenantId(), payload)
            .onFailure(context::failNow)
            .compose(ok -> getHelper().registry.addTenant(tenantId, new Tenant()))
            .onFailure(context::failNow)
            .compose(ar -> getHelper().registry.updateTenant(
                    tenantId,
                    payload,
                    HttpURLConnection.HTTP_CONFLICT))
            .onComplete(context.succeeding(response -> {
                context.verify(() -> IntegrationTestSupport.assertErrorPayload(response));
                context.completeNow();
            }));
    }

    /**
     * Verifies that a request to update a tenant with a body that exceeds the registry's max payload limit
     * fails with a {@link HttpURLConnection#HTTP_ENTITY_TOO_LARGE} status code.
     *
     * @param context The vert.x test context.
     */
    @Test
    public void testUpdateTenantFailsForRequestPayloadExceedingLimit(final VertxTestContext context)  {

        final String tenantId = getHelper().getRandomTenantId();
        getHelper().registry.addTenant(tenantId, new Tenant())
            .compose(ok -> {
                final var data = new char[3000];
                Arrays.fill(data, 'x');
                final Tenant payload = new Tenant();
                payload.setExtensions(Map.of("data", new String(data)));

                return getHelper().registry.updateTenant(tenantId, payload, HttpURLConnection.HTTP_ENTITY_TOO_LARGE);
            })
            .onComplete(context.succeeding(response -> {
                context.verify(() -> IntegrationTestSupport.assertErrorPayload(response));
                context.completeNow();
            }));
    }

    /**
     * Verifies that an existing tenant  can be successfully deleted and that objects belonging to the
     * tenant can no longer be retrieved after the tenant has been deleted.
     *
     * @param context The vert.x test context.
     */
    @Test
    public void testRemoveTenantSucceeds(final VertxTestContext context) {

        final Tenant tenantPayload = buildTenantPayload();
        final String tenantId = getHelper().getRandomTenantId();
        final String deviceId = getHelper().getRandomDeviceId(tenantId);

        getHelper().registry.addDeviceForTenant(tenantId, tenantPayload, deviceId, new Device(), "secret")
            .compose(ok -> getHelper().registry.getRegistrationInfo(tenantId, deviceId))
            .compose(ok -> getHelper().registry.getCredentials(tenantId, deviceId))
            .compose(ar -> getHelper().registry.removeTenant(tenantId))
            .compose(ok -> getHelper().registry.getCredentials(tenantId, deviceId, HttpURLConnection.HTTP_NOT_FOUND))
            .compose(ok -> getHelper().registry.getRegistrationInfo(tenantId, deviceId, HttpURLConnection.HTTP_NOT_FOUND))
            .compose(ok -> getHelper().registry.getTenant(tenantId, HttpURLConnection.HTTP_NOT_FOUND))
            .onComplete(context.succeedingThenComplete());
    }

    /**
     * Verifies that a request to delete a tenant that does not exist fails.
     *
     * @param context The vert.x test context.
     */
    @Test
    public void testRemoveTenantFailsForNonExistingTenant(final VertxTestContext context) {

        getHelper().registry.removeTenant("non-existing-tenant", HttpURLConnection.HTTP_NOT_FOUND)
            .onComplete(context.succeeding(response -> {
                context.verify(() -> IntegrationTestSupport.assertErrorPayload(response));
                context.completeNow();
            }));
    }

    /**
     * Verifies that a correctly added tenant record can be successfully looked up again.
     *
     * @param context The vert.x test context.
     */
    @Test
    public void testGetTenantSucceeds(final VertxTestContext context)  {

        final var resourceLimits = new ResourceLimits();
        resourceLimits.setMaxConnections(1000);
        final var registrationLimits = new RegistrationLimits();
        registrationLimits.setMaxNumberOfDevices(100);
        registrationLimits.setMaxCredentialsPerDevice(5);
        final Tenant requestBody = buildTenantPayload();
        requestBody.setMinimumMessageSize(2048);
        requestBody.setResourceLimits(resourceLimits);
        requestBody.setRegistrationLimits(registrationLimits);
        final String tenantId = getHelper().getRandomTenantId();

        LOG.debug("registering tenant using Management API: {}", JsonObject.mapFrom(requestBody).encodePrettily());
        getHelper().registry.addTenant(tenantId, requestBody)
            .compose(ar -> getHelper().registry.getTenant(tenantId))
            .onComplete(context.succeeding(httpResponse -> {
                final JsonObject json = httpResponse.bodyAsJsonObject();
                LOG.debug("retrieved tenant using Tenant API: {}", json.encodePrettily());
                context.verify(() -> {
                    assertThat(json.containsKey(RegistryManagementConstants.FIELD_STATUS)).isFalse();
                    assertTrue(IntegrationTestSupport.testJsonObjectToBeContained(json, JsonObject.mapFrom(requestBody)));
                });
                context.completeNow();
            }));
    }

    /**
     * Verifies that a request to get a non-existing tenant fails with a 404.
     *
     * @param context The vert.x test context.
     */
    @Test
    public void testGetTenantFailsForNonExistingTenant(final VertxTestContext context) {

        getHelper().registry.getTenant("non-existing-tenant", HttpURLConnection.HTTP_NOT_FOUND)
            .onComplete(context.succeeding(response -> {
                context.verify(() -> IntegrationTestSupport.assertErrorPayload(response));
                context.completeNow();
            }));
    }

    /**
     * Tests verifying the search tenants operation.
     *
     * @see <a href="https://www.eclipse.org/hono/docs/api/management/#/tenants/searchTenants"> 
     *      Device Registry Management API - Search Tenants</a>
     */
    @Nested
    @EnabledIfRegistrySupportsFeatures(searchTenants = true)
    class SearchTenantsIT {

        /**
         * Verifies that a request to search tenants fails with a {@value HttpURLConnection#HTTP_NOT_FOUND} when no
         * matching tenants are found.
         *
         * @param ctx The vert.x test context.
         */
        @Test
        public void testSearchTenantsFailsWhenNoTenantsAreFound(final VertxTestContext ctx) {
            final Tenant tenant = new Tenant().setEnabled(false);
            final String tenantId = getHelper().getRandomTenantId();
            final String filterJson = getFilterJson("/enabled", true, "eq");

            getHelper().registry.addTenant(tenantId, tenant)
                    .compose(ok -> getHelper().registry.searchTenants(Optional.empty(), Optional.empty(),
                            List.of(filterJson), List.of(), HttpURLConnection.HTTP_NOT_FOUND))
                    .onComplete(ctx.succeedingThenComplete());
        }

        /**
         * Verifies that a request to search tenants fails when the page size is invalid.
         *
         * @param ctx The vert.x test context.
         */
        @Test
        public void testSearchTenantsWithInvalidPageSizeFails(final VertxTestContext ctx) {

            final int invalidPageSize = -100;

            getHelper().registry.addTenant(getHelper().getRandomTenantId(), new Tenant())
                    .compose(ok -> getHelper().registry.searchTenants(Optional.of(invalidPageSize), Optional.empty(),
                            List.of(), List.of(), HttpURLConnection.HTTP_BAD_REQUEST))
                    .onComplete(ctx.succeedingThenComplete());
        }

        /**
         * Verifies that a request to search tenants with a valid pageSize succeeds and the result is in accordance
         * with the specified page size.
         *
         * @param ctx The vert.x test context.
         */
        @Test
        public void testSearchTenantsWithValidPageSizeSucceeds(final VertxTestContext ctx) {

            final int pageSize = 1;

            Future.all(
                    getHelper().registry.addTenant(getHelper().getRandomTenantId(), new Tenant()),
                    getHelper().registry.addTenant(getHelper().getRandomTenantId(), new Tenant()))
                    .compose(response -> getHelper().registry.searchTenants(
                            Optional.of(pageSize),
                            Optional.empty(),
                            List.of(),
                            List.of(),
                            HttpURLConnection.HTTP_OK))
                    .onComplete(ctx.succeeding(httpResponse -> {
                        ctx.verify(() -> {
                            final SearchResult<TenantWithId> searchResult = JacksonCodec
                                    .decodeValue(httpResponse.body(), new TypeReference<>() {
                                    });
                            assertThat(searchResult.getTotal()).isEqualTo(2);
                            assertThat(searchResult.getResult()).hasSize(1);
                        });
                        ctx.completeNow();
                    }));
        }

        /**
         * Verifies that a request to search tenants fails when page offset is invalid.
         *
         * @param ctx The vert.x test context.
         */
        @Test
        public void testSearchTenantsWithInvalidPageOffsetFails(final VertxTestContext ctx) {
            final int invalidPageOffset = -100;

            getHelper().registry.addTenant(getHelper().getRandomTenantId(), new Tenant())
                    .compose(ok -> getHelper().registry.searchTenants(Optional.empty(), Optional.of(invalidPageOffset),
                            List.of(), List.of(), HttpURLConnection.HTTP_BAD_REQUEST))
                    .onComplete(ctx.succeedingThenComplete());
        }

        /**
         * Verifies that a request to search tenants with a valid page offset succeeds and the result is in accordance
         * with the specified page offset.
         *
         * @param ctx The vert.x test context.
         */
        @Test
        public void testSearchTenantsWithValidPageOffsetSucceeds(final VertxTestContext ctx) {

            final var tenants = Map.of(
                    getHelper().getRandomTenantId(), new Tenant(),
                    getHelper().getRandomTenantId(), new Tenant(),
                    getHelper().getRandomTenantId(), new Tenant(),
                    getHelper().getRandomTenantId(), new Tenant(),
                    getHelper().getRandomTenantId(), new Tenant(),
                    getHelper().getRandomTenantId(), new Tenant());
            final var tenantIds = new ArrayList<String>(tenants.keySet());
            Collections.sort(tenantIds);
            final int pageSize = 4;

            createTenants(tenants)
                .compose(ok -> getHelper().registry.searchTenants(
                        Optional.of(pageSize),
                        Optional.empty(),
                        List.of(),
                        List.of(),
                        HttpURLConnection.HTTP_OK))
                .compose(response -> {
                    ctx.verify(() -> {
                        final SearchResult<TenantWithId> searchResult = JacksonCodec
                                .decodeValue(response.body(), new TypeReference<>() { });

                        assertThat(searchResult.getTotal()).isEqualTo(6);
                        assertThat(searchResult.getResult()).hasSize(4);
                        final var foundTenantIds = searchResult.getResult().stream().map(TenantWithId::getId).collect(Collectors.toList());
                        assertThat(foundTenantIds).containsExactlyElementsIn(tenantIds.subList(0, 4));
                    });
                    return getHelper().registry.searchTenants(
                            Optional.of(pageSize),
                            Optional.of(1),
                            List.of(),
                            List.of(),
                            HttpURLConnection.HTTP_OK);
                })
                .onComplete(ctx.succeeding(response -> {
                    ctx.verify(() -> {
                        final SearchResult<TenantWithId> searchResult = JacksonCodec
                                .decodeValue(response.body(), new TypeReference<>() { });

                        assertThat(searchResult.getTotal()).isEqualTo(6);
                        assertThat(searchResult.getResult()).hasSize(2);
                        final var foundTenantIds = searchResult.getResult().stream().map(TenantWithId::getId).collect(Collectors.toList());
                        assertThat(foundTenantIds).containsExactlyElementsIn(tenantIds.subList(4, 6));
                    });
                    ctx.completeNow();
                }));
        }

        /**
         * Verifies that a request to search tenant fails when the filterJson is invalid.
         *
         * @param ctx The vert.x test context.
         */
        @Test
        public void testSearchTenantsWithInvalidFilterJsonFails(final VertxTestContext ctx) {

            getHelper().registry.addTenant(getHelper().getRandomTenantId(), new Tenant())
                    .compose(ok -> getHelper().registry.searchTenants(Optional.empty(), Optional.empty(),
                            List.of("Invalid filterJson"), List.of(), HttpURLConnection.HTTP_BAD_REQUEST))
                    .onComplete(ctx.succeedingThenComplete());
        }

        /**
         * Verifies that a request to search tenants with multiple filters succeeds and matching tenants are found.
         *
         * @param ctx The vert.x test context.
         */
        @Test
        public void testSearchTenantsWithValidMultipleFiltersSucceeds(final VertxTestContext ctx) {
            final String tenantId1 = getHelper().getRandomTenantId();
            final String tenantId2 = getHelper().getRandomTenantId();
            final Tenant tenant1 = new Tenant().setEnabled(false).setExtensions(Map.of("id", "1"));
            final Tenant tenant2 = new Tenant().setEnabled(true).setExtensions(Map.of("id", "2"));
            final String filterJson1 = getFilterJson("/ext/id", "1", "eq");
            final String filterJson2 = getFilterJson("/enabled", true, "eq");
            final String filterJson3 = getFilterJson("/enabled", false, "eq");

            Future.all(
                    getHelper().registry.addTenant(tenantId1, tenant1),
                    getHelper().registry.addTenant(tenantId2, tenant2))
                    .compose(ok -> getHelper().registry.searchTenants(Optional.empty(), Optional.empty(),
                            List.of(filterJson1, filterJson2), List.of(), HttpURLConnection.HTTP_NOT_FOUND))
                    .compose(ok -> getHelper().registry.searchTenants(Optional.empty(), Optional.empty(),
                            List.of(filterJson1, filterJson3), List.of(), HttpURLConnection.HTTP_OK))
                    .onComplete(ctx.succeeding(httpResponse -> {
                        ctx.verify(() -> {
                            final SearchResult<TenantWithId> searchResult = JacksonCodec
                                    .decodeValue(httpResponse.body(), new TypeReference<>() {
                                    });
                            assertThat(searchResult.getTotal()).isEqualTo(1);
                            assertThat(searchResult.getResult()).hasSize(1);
                            assertThat(searchResult.getResult().get(0).getId()).isEqualTo(tenantId1);
                        });
                        ctx.completeNow();
                    }));
        }

        /**
         * Verifies that a request to search tenants with filters containing the wildcard character '*' 
         * succeeds and matching tenants are found.
         *
         * @param ctx The vert.x test context.
         */
        @Test
        public void testSearchTenantsWithWildCardToMatchMultipleCharactersSucceeds(final VertxTestContext ctx) {
            final String tenantId1 = getHelper().getRandomTenantId();
            final String tenantId2 = getHelper().getRandomTenantId();
            final Tenant tenant1 = new Tenant().setEnabled(false).setExtensions(Map.of("id", "$id:1"));
            final Tenant tenant2 = new Tenant().setEnabled(true).setExtensions(Map.of("id", "$id:2"));
            final String filterJson1 = getFilterJson("/enabled", true, "eq");
            final String filterJson2 = getFilterJson("/ext/id", "$id*", "eq");

            Future.all(
                    getHelper().registry.addTenant(tenantId1, tenant1),
                    getHelper().registry.addTenant(tenantId2, tenant2))
                    .compose(ok -> getHelper().registry.searchTenants(Optional.empty(), Optional.empty(),
                            List.of(filterJson1, filterJson2), List.of(), HttpURLConnection.HTTP_OK))
                    .onComplete(ctx.succeeding(httpResponse -> {
                        ctx.verify(() -> {
                            final SearchResult<TenantWithId> searchResult = JacksonCodec
                                    .decodeValue(httpResponse.body(), new TypeReference<>() {
                                    });
                            assertThat(searchResult.getTotal()).isEqualTo(1);
                            assertThat(searchResult.getResult()).hasSize(1);
                            assertThat(searchResult.getResult().get(0).getId()).isEqualTo(tenantId2);
                        });
                        ctx.completeNow();
                    }));
        }

        /**
         * Verifies that a request to search tenants with a filter containing the wildcard character '*' fails with a
         * {@value HttpURLConnection#HTTP_NOT_FOUND} as no matching tenants are found.
         *
         * @param ctx The vert.x test context.
         */
        @Test
        public void testSearchTenantsWithWildCardToMatchMultipleCharactersFails(final VertxTestContext ctx) {
            final Tenant tenant = new Tenant().setExtensions(Map.of("id", "$id:1"));
            final String filterJson = getFilterJson("/ext/id", "*id*2", "eq");

            getHelper().registry.addTenant(getHelper().getRandomTenantId(), tenant)
                    .compose(ok -> getHelper().registry.searchTenants(Optional.empty(), Optional.empty(),
                            List.of(filterJson), List.of(), HttpURLConnection.HTTP_NOT_FOUND))
                    .onComplete(ctx.succeedingThenComplete());
        }

        /**
         * Verifies that a request to search tenants with filters containing the wildcard character '?' 
         * succeeds and matching tenants are found.
         *
         * @param ctx The vert.x test context.
         */
        @Test
        public void testSearchTenantsWithWildCardToMatchExactlyOneCharacterSucceeds(final VertxTestContext ctx) {
            final String tenantId1 = getHelper().getRandomTenantId();
            final String tenantId2 = getHelper().getRandomTenantId();
            final Tenant tenant1 = new Tenant().setEnabled(false).setExtensions(Map.of("id", "$id:1"));
            final Tenant tenant2 = new Tenant().setEnabled(true).setExtensions(Map.of("id", "$id:2"));
            final String filterJson1 = getFilterJson("/enabled", true, "eq");
            final String filterJson2 = getFilterJson("/ext/id", "$id?2", "eq");

            Future.all(
                    getHelper().registry.addTenant(tenantId1, tenant1),
                    getHelper().registry.addTenant(tenantId2, tenant2))
                    .compose(ok -> getHelper().registry.searchTenants(Optional.empty(), Optional.empty(),
                            List.of(filterJson1, filterJson2), List.of(), HttpURLConnection.HTTP_OK))
                    .onComplete(ctx.succeeding(httpResponse -> {
                        ctx.verify(() -> {
                            final SearchResult<TenantWithId> searchResult = JacksonCodec
                                    .decodeValue(httpResponse.body(), new TypeReference<>() {
                                    });
                            assertThat(searchResult.getTotal()).isEqualTo(1);
                            assertThat(searchResult.getResult()).hasSize(1);
                            assertThat(searchResult.getResult().get(0).getId()).isEqualTo(tenantId2);
                        });
                        ctx.completeNow();
                    }));
        }

        /**
         * Verifies that a request to search tenants with a filter containing the wildcard character '?' fails with a
         * {@value HttpURLConnection#HTTP_NOT_FOUND} as no matching tenants are found.
         *
         * @param ctx The vert.x test context.
         */
        @Test
        public void testSearchTenantsWithWildCardToMatchExactlyOneCharacterFails(final VertxTestContext ctx) {
            final Tenant tenant = new Tenant().setExtensions(Map.of("id", "$id:2"));
            final String filterJson = getFilterJson("/ext/id", "$id:?2", "eq");

            getHelper().registry.addTenant(getHelper().getRandomTenantId(), tenant)
                    .compose(ok -> getHelper().registry.searchTenants(Optional.empty(), Optional.empty(),
                            List.of(filterJson), List.of(), HttpURLConnection.HTTP_NOT_FOUND))
                    .onComplete(ctx.succeedingThenComplete());
        }

        /**
         * Verifies that a request to search tenants fails when sortJson is invalid.
         *
         * @param ctx The vert.x test context.
         */
        @Test
        public void testSearchTenantsWithInvalidSortJsonFails(final VertxTestContext ctx) {

            getHelper().registry.addTenant(getHelper().getRandomTenantId(), new Tenant())
                    .compose(ok -> getHelper().registry.searchTenants(Optional.empty(), Optional.empty(), List.of(),
                            List.of("Invalid sortJson"), HttpURLConnection.HTTP_BAD_REQUEST))
                    .onComplete(ctx.succeedingThenComplete());
        }

        /**
         * Verifies that a request to search tenants with a valid sort option succeeds and the result is sorted
         * accordingly.
         *
         * @param ctx The vert.x test context.
         */
        @Test
        public void testSearchTenantsWithValidSortOptionSucceeds(final VertxTestContext ctx) {
            final String tenantId1 = getHelper().getRandomTenantId();
            final String tenantId2 = getHelper().getRandomTenantId();
            final Tenant tenant1 = new Tenant().setExtensions(Map.of("id", "aaa"));
            final Tenant tenant2 = new Tenant().setExtensions(Map.of("id", "bbb"));
            final String sortJson = getSortJson("/ext/id", "desc");

            Future.all(
                    getHelper().registry.addTenant(tenantId1, tenant1),
                    getHelper().registry.addTenant(tenantId2, tenant2))
                    .compose(ok -> getHelper().registry.searchTenants(Optional.empty(), Optional.empty(), List.of(),
                            List.of(sortJson), HttpURLConnection.HTTP_OK))
                    .onComplete(ctx.succeeding(httpResponse -> {
                        ctx.verify(() -> {
                            final SearchResult<TenantWithId> searchResult = JacksonCodec
                                    .decodeValue(httpResponse.body(), new TypeReference<>() {
                                    });
                            assertThat(searchResult.getTotal()).isEqualTo(2);
                            assertThat(searchResult.getResult()).hasSize(2);
                            assertThat(searchResult.getResult().get(0).getId()).isEqualTo(tenantId2);
                            assertThat(searchResult.getResult().get(1).getId()).isEqualTo(tenantId1);
                        });
                        ctx.completeNow();
                    }));
        }

        /**
         * Creates a set of tenants.
         *
         * @param tenantsToCreate The tenants to be created. The keys are the tenant identifiers.
         * @return A succeeded future if all the tenants have been created successfully.
         */
        private Future<Void> createTenants(final Map<String, Tenant> tenantsToCreate) {

            final List<Future<Object>> creationResult = tenantsToCreate.entrySet().stream()
                    .map(entry -> getHelper().registry.addTenant(entry.getKey(), entry.getValue())
                        .map(response -> {
                            assertThat(response.statusCode()).isEqualTo(HttpURLConnection.HTTP_CREATED);
                            return null;
                        }))
                    .collect(Collectors.toList());
            return Future.all(creationResult).mapEmpty();
        }

        private <T> String getFilterJson(final String field, final T value, final String operator) {
            final JsonObject filterJson = new JsonObject()
                    .put(RegistryManagementConstants.FIELD_FILTER_FIELD, field)
                    .put(RegistryManagementConstants.FIELD_FILTER_VALUE, value);

            Optional.ofNullable(operator)
                    .ifPresent(op -> filterJson.put(RegistryManagementConstants.FIELD_FILTER_OPERATOR, op));

            return filterJson.toString();
        }

        private String getSortJson(final String field, final String direction) {
            final JsonObject sortJson = new JsonObject().put(RegistryManagementConstants.FIELD_FILTER_FIELD, field);

            Optional.ofNullable(direction)
                    .ifPresent(dir -> sortJson.put(RegistryManagementConstants.FIELD_SORT_DIRECTION, dir));

            return sortJson.toString();
        }
    }

    private static String assertLocationHeader(final MultiMap responseHeaders) {
        final String location = responseHeaders.get(HttpHeaders.LOCATION);
        assertThat(location).isNotNull();
        final Pattern pattern = Pattern.compile("/(.*)/(.*)/(.*)");
        final Matcher matcher = pattern.matcher(location);
        assertThat(matcher.matches()).isTrue();
        final String generatedId = matcher.group(3);
        assertThat(generatedId).isNotNull();
        return generatedId;
    }

    /**
     * Creates a tenant payload.
     * <p>
     * The tenant payload contains configurations for the http, mqtt and a custom adapter.
     *
     * @return The tenant object.
     */
    private static Tenant buildTenantPayload() {
        final Tenant tenant = new Tenant();
        tenant.putExtension("plan", "unlimited");
        // explicitly (add the messaging-type extension (implicitly added by DeviceRegistryHttpClient)
        // here so that it is also part of the expected output and hence verified
        tenant.putExtension(TenantConstants.FIELD_EXT_MESSAGING_TYPE,
                IntegrationTestSupport.getConfiguredMessagingType().name());
        tenant.addAdapterConfig(new Adapter(Constants.PROTOCOL_ADAPTER_TYPE_HTTP)
                .setEnabled(true)
                .setDeviceAuthenticationRequired(true));
        tenant.addAdapterConfig(new Adapter(Constants.PROTOCOL_ADAPTER_TYPE_MQTT)
                .setEnabled(true)
                .setDeviceAuthenticationRequired(true));
        tenant.addAdapterConfig(new Adapter("custom")
                .setEnabled(false)
                .setDeviceAuthenticationRequired(false)
                .putExtension("maxInstances", 4));

        return tenant;
    }
}
