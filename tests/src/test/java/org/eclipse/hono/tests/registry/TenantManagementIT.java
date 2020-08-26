/*******************************************************************************
 * Copyright (c) 2016, 2020 Contributors to the Eclipse Foundation
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.HttpURLConnection;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.hono.service.management.tenant.Tenant;
import org.eclipse.hono.tests.IntegrationTestSupport;
import org.eclipse.hono.tests.Tenants;
import org.eclipse.hono.util.Adapter;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.RegistryManagementConstants;
import org.eclipse.hono.util.ResourceLimits;
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

import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

/**
 * Tests verifying the Device Registry component by making HTTP requests to its
 * Tenant HTTP endpoint and validating the corresponding responses.
 */
@ExtendWith(VertxExtension.class)
@Timeout(value = 5, timeUnit = TimeUnit.SECONDS)
public class TenantManagementIT {

    private static final Logger LOG = LoggerFactory.getLogger(TenantManagementIT.class);
    private static final Vertx vertx = Vertx.vertx();

    private static IntegrationTestSupport helper;
    private static String tenantId;

    /**
     * Creates the HTTP client for accessing the registry.
     */
    @BeforeAll
    public static void setUpClient() {

        helper = new IntegrationTestSupport(vertx);
        helper.initRegistryClient();
    }

    /**
     * Sets up the fixture.
     *
     * @param testInfo The test meta data.
     */
    @BeforeEach
    public void setUp(final TestInfo testInfo) {
        LOG.info("running test: {}", testInfo.getDisplayName());
        tenantId = helper.getRandomTenantId();
    }

    /**
     * Removes the credentials that have been added by the test.
     *
     * @param ctx The vert.x test context.
     */
    @AfterEach
    public void removeTenant(final VertxTestContext ctx) {

        helper.deleteObjects(ctx);
    }

    /**
     * Shuts down the client.
     *
     * @param context The vert.x test context.
     */
    @AfterAll
    public static void tearDown(final VertxTestContext context) {
        vertx.close(context.completing());
    }

    /**
     * Verifies that the service accepts an add tenant request containing a valid tenant structure
     * and that the response contains a <em>Location</em> header for the created resource.
     *
     * @param context The vert.x test context.
     */
    @Test
    public void testAddTenantSucceeds(final VertxTestContext context) {

        final Tenant tenant = buildTenantPayload();
        helper.registry.addTenant(tenantId, tenant).onComplete(context.completing());
    }

    /**
     * Verifies that the service successfully create a tenant with a generated tenant ID.
     *
     * @param context The vert.x test context.
     */
    @Test
    public void testAddTenantSucceedsWithAGeneratedId(final VertxTestContext context) {

        helper.registry.addTenant()
            .onComplete(context.succeeding(responseHeaders -> {
                context.verify(() -> {
                    assertThat(responseHeaders.get(HttpHeaders.ETAG)).isNotNull();
                    final String generatedId = assertLocationHeader(responseHeaders);
                    // update the global tenantId value for cleanup
                    helper.addTenantIdForRemoval(generatedId);
                });
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

        helper.registry.addTenant(tenantId, payload).compose(ar -> {
            // now try to add the tenant again
            return helper.registry.addTenant(tenantId, payload, HttpURLConnection.HTTP_CONFLICT);
        }).onComplete(context.completing());
    }

    /**
     * Verifies that the service returns a 400 status code for an add tenant request with a Content-Type
     * other than application/json.
     *
     * @param context The vert.x test context.
     */
    @Test
    public void testAddTenantFailsForWrongContentType(final VertxTestContext context)  {

        helper.registry.addTenant(tenantId,
                buildTenantPayload(),
                "application/x-www-form-urlencoded",
                HttpURLConnection.HTTP_BAD_REQUEST).onComplete(context.completing());
    }

    /**
     * Verifies that the service successfully create a tenant from a request with an empty body.
     *
     * @param context The vert.x test context.
     */
    @Test
    public void testAddTenantSucceedsForEmptyBody(final VertxTestContext context) {

        helper.registry.addTenant(tenantId)
            .onComplete(context.completing());
    }

    /**
     * Verifies that the a tenant cannot be created if the tenant ID is invalid.
     *
     * @param context The vert.x test context.
     */
    @Test
    public void testAddTenantFailsForInvalidTenantId(final VertxTestContext context) {

        helper.registry.addTenant("invalid tenantid$", null, HttpURLConnection.HTTP_BAD_REQUEST)
            .onComplete(context.completing());
    }

    /**
     * Verifies that the service returns a 400 status code for an add tenant request containing
     * a malformed trust configuration (i.e an invalid Base64 encoding value of the trust CA's certificate).
     *
     * @param context The Vert.x test context.
     */
    @Test
    public void testAddTenantFailsForMalformedTrustConfiguration(final VertxTestContext context) {

        final Tenant requestBody = Tenants.createTenantForTrustAnchor("CN=test-dn", "NotBased64Encoded".getBytes(), "RSA");

        helper.registry.addTenant(
                tenantId,
                requestBody,
                "application/json",
                HttpURLConnection.HTTP_BAD_REQUEST)
        .onComplete(context.completing());
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

        helper.registry.addTenant(
                tenantId,
                requestBody,
                "application/json",
                HttpURLConnection.HTTP_BAD_REQUEST)
            .onComplete(context.completing());
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

        helper.registry.addTenant(
                tenantId,
                requestBody,
                "application/json",
                HttpURLConnection.HTTP_BAD_REQUEST)
            .onComplete(context.completing());
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

        helper.registry.addTenant(tenantId, orig)
            .compose(responseHeaders -> {
                latestVersion.set(responseHeaders.get(HttpHeaders.ETAG));
                assertThat(latestVersion.get()).isNotNull();
                return helper.registry.updateTenant(tenantId, altered, HttpURLConnection.HTTP_NO_CONTENT);
            })
            .compose(responseHeaders -> {
                final String updatedVersion = responseHeaders.get(HttpHeaders.ETAG);
                assertThat(updatedVersion).isNotNull();
                assertThat(updatedVersion).isNotEqualTo(latestVersion.get());
                return helper.registry.getTenant(tenantId);
            })
            .onComplete(context.succeeding(b -> {
                // compare the changed field only
                context.verify(() -> {
                    assertFalse(b.toJsonObject().getBoolean(TenantConstants.FIELD_ENABLED, Boolean.TRUE));
                });
                context.completeNow();
            }));
    }

    /**
     * Verifies that the service rejects an update request for a non-existing tenant.
     *
     * @param context The vert.x test context.
     */
    @Test
    public void testUpdateTenantFailsForNonExistingTenant(final VertxTestContext context) {

        final Tenant altered = buildTenantPayload();

        helper.registry.updateTenant("non-existing-tenant", altered, HttpURLConnection.HTTP_NOT_FOUND)
            .onComplete(context.completing());
    }


    /**
     * Verify that a correctly added tenant record can be successfully deleted again.
     *
     * @param context The vert.x test context.
     */
    @Test
    public void testRemoveTenantSucceeds(final VertxTestContext context) {

        final Tenant tenantPayload = buildTenantPayload();
        helper.registry.addTenant(tenantId, tenantPayload)
            .compose(ar -> helper.registry.removeTenant(tenantId, HttpURLConnection.HTTP_NO_CONTENT))
            .onComplete(context.completing());
    }

    /**
     * Verifies that a request to delete a tenant that does not exist fails.
     *
     * @param context The vert.x test context.
     */
    @Test
    public void testRemoveTenantFailsForNonExistingTenant(final VertxTestContext context) {

        helper.registry.removeTenant("non-existing-tenant", HttpURLConnection.HTTP_NOT_FOUND)
            .onComplete(context.completing());
    }

    /**
     * Verifies that a correctly added tenant record can be successfully looked up again.
     *
     * @param context The vert.x test context.
     */
    @Test
    public void testGetTenantSucceeds(final VertxTestContext context)  {

        final ResourceLimits resourceLimits = new ResourceLimits();
        resourceLimits.setMaxConnections(1000);
        final Tenant requestBody = buildTenantPayload();
        requestBody.setMinimumMessageSize(2048);
        requestBody.setResourceLimits(resourceLimits);

        LOG.debug("registering tenant using Management API: {}", JsonObject.mapFrom(requestBody).encodePrettily());
        helper.registry.addTenant(tenantId, requestBody)
            .compose(ar -> helper.registry.getTenant(tenantId))
            .onComplete(context.succeeding(b -> {
                final JsonObject json = b.toJsonObject();
                LOG.debug("retrieved tenant using Tenant API: {}", json.encodePrettily());
                context.verify(() -> {
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

        helper.registry.getTenant("non-existing-tenant", HttpURLConnection.HTTP_NOT_FOUND)
            .onComplete(context.completing());
    }

    private static String assertLocationHeader(final MultiMap responseHeaders) {
        final String location = responseHeaders.get(HttpHeaders.LOCATION);
        assertThat(location).isNotNull();
        final Pattern pattern = Pattern.compile("/(.*)/(.*)/(.*)");
        final Matcher matcher = pattern.matcher(location);
        assertThat(matcher.matches());
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
