/*******************************************************************************
 * Copyright (c) 2016, 2018 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.tests.registry.legacy;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import java.net.HttpURLConnection;
import java.util.UUID;
import org.eclipse.hono.tests.LegacyDeviceRegistryHttpClient;
import org.eclipse.hono.tests.IntegrationTestSupport;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.TenantConstants;
import org.eclipse.hono.util.TenantObject;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;

/**
 * Tests verifying the Device Registry component by making HTTP requests to its
 * Tenant HTTP endpoint and validating the corresponding responses.
 */
@Deprecated
@RunWith(VertxUnitRunner.class)
public class LegacyTenantHttpIT {

    private static final String TENANT_PREFIX = "testTenant";
    private static final Vertx vertx = Vertx.vertx();

    private static LegacyDeviceRegistryHttpClient registry;

    /**
     * Set the timeout for all test methods by using a JUnit Rule (instead of providing the timeout at every @Test annotation).
     * See {@link Test#timeout} for details about improved thread safety regarding the @After annotation for each test.
     */
    @Rule
    public final TestRule timeoutForAllMethods = Timeout.seconds(5);

    private String tenantId;

    /**
     * Creates the HTTP client for accessing the registry.
     *
     * @param ctx The vert.x test context.
     */
    @BeforeClass
    public static void setUpClient(final TestContext ctx) {

        registry = new LegacyDeviceRegistryHttpClient(
                vertx,
                IntegrationTestSupport.HONO_DEVICEREGISTRY_HOST,
                IntegrationTestSupport.HONO_DEVICEREGISTRY_HTTP_PORT);
    }

    /**
     * Sets up the fixture.
     */
    @Before
    public void setUp() {
        tenantId = TENANT_PREFIX + "." + UUID.randomUUID();
    }

    /**
     * Removes the credentials that have been added by the test.
     *
     * @param ctx The vert.x test context.
     */
    @After
    public void removeTenant(final TestContext ctx) {
        final Async deletion = ctx.async();
        registry.removeTenant(tenantId, HttpURLConnection.HTTP_NO_CONTENT).setHandler(attempt -> deletion.complete());
        deletion.await();
    }

    /**
     * Shuts down the client.
     *
     * @param context The vert.x test context.
     */
    @AfterClass
    public static void tearDown(final TestContext context) {
        vertx.close(context.asyncAssertSuccess());
    }

    /**
     * Verifies that the service accepts an add tenant request containing a valid tenant structure
     * and that the response contains a <em>Location</em> header for the created resource.
     *
     * @param context The vert.x test context.
     */
    @Test
    public void testAddTenantSucceeds(final TestContext context)  {

        final JsonObject requestBodyAddTenant = buildTenantPayload(tenantId);
        registry.addTenant(requestBodyAddTenant).setHandler(context.asyncAssertSuccess());
    }

    /**
     * Verifies that a correctly filled JSON payload to add a tenant for an already existing record is
     * responded with {@link HttpURLConnection#HTTP_CONFLICT} and a non empty error response message.
     *
     * @param context The vert.x test context.
     */
    @Test
    public void testAddTenantRejectsDuplicateRegistration(final TestContext context)  {

        final JsonObject payload = buildTenantPayload(tenantId);

        registry.addTenant(payload).compose(ar -> {
            // now try to add the tenant again
            return registry.addTenant(payload, HttpURLConnection.HTTP_CONFLICT);
        }).setHandler(context.asyncAssertSuccess());
    }

    /**
     * Verifies that the service returns a 400 status code for an add tenant request with a Content-Type
     * other than application/json.
     *
     * @param context The vert.x test context.
     */
    @Test
    public void testAddTenantFailsForWrongContentType(final TestContext context)  {

        registry.addTenant(
                buildTenantPayload(tenantId),
                "application/x-www-form-urlencoded",
                HttpURLConnection.HTTP_BAD_REQUEST).setHandler(context.asyncAssertSuccess());
    }

    /**
     * Verifies that the service returns a 400 status code for an add tenant request with an empty body.
     *
     * @param context The vert.x test context.
     */
    @Test
    public void testAddTenantFailsForEmptyBody(final TestContext context) {

        registry.addTenant(null, "application/json", HttpURLConnection.HTTP_BAD_REQUEST).setHandler(context.asyncAssertSuccess());
    }

    /**
     * Verifies that the service accepts an update tenant request for an existing tenant.
     *
     * @param context The vert.x test context.
     */
    @Test
    public void testUpdateTenantSucceeds(final TestContext context) {

        final JsonObject orig = buildTenantPayload(tenantId);
        final JsonObject altered = orig.copy();
        altered.put(TenantConstants.FIELD_ENABLED, Boolean.FALSE);

        registry.addTenant(orig)
                .compose(ar -> registry.updateTenant(tenantId, altered, HttpURLConnection.HTTP_NO_CONTENT))
                .compose(ur -> registry.getTenant(tenantId))
                .compose(b -> {
                    // compare the changed field only
                    context.assertFalse(b.toJsonObject().getBoolean(TenantConstants.FIELD_ENABLED, Boolean.TRUE));
                    return Future.succeededFuture();
                }).setHandler(context.asyncAssertSuccess());
    }

    /**
     * Verifies that the service rejects an update request for a non-existing tenant.
     *
     * @param context The vert.x test context.
     */
    @Test
    public void testUpdateTenantFailsForNonExistingTenant(final TestContext context) {

        final JsonObject altered = buildTenantPayload(tenantId);

        registry.updateTenant(tenantId, altered, HttpURLConnection.HTTP_NOT_FOUND)
                .setHandler(context.asyncAssertSuccess());
    }


    /**
     * Verify that a correctly added tenant record can be successfully deleted again.
     *
     * @param context The vert.x test context.
     */
    @Test
    public void testRemoveTenantSucceeds(final TestContext context) {

        final JsonObject tenantPayload = buildTenantPayload(tenantId);
        registry.addTenant(tenantPayload)
                .compose(ar -> registry.removeTenant(tenantId, HttpURLConnection.HTTP_NO_CONTENT))
                .setHandler(context.asyncAssertSuccess());
    }


    /**
     * Verifies that a request to delete a tenant that does not exist fails.
     *
     * @param context The vert.x test context.
     */
    @Test
    public void testRemoveTenantFailsForNonExistingTenant(final TestContext context) {

        registry.removeTenant("non-existing-tenant", HttpURLConnection.HTTP_NOT_FOUND).setHandler(context.asyncAssertSuccess());
    }

    /**
     * Verifies that a correctly added tenant record can be successfully looked up again.
     *
     * @param context The vert.x test context.
     */
    @Test
    public void testGetAddedTenant(final TestContext context)  {

        final JsonObject requestBody = buildTenantPayload(tenantId);

        registry.addTenant(requestBody)
                .compose(ar -> registry.getTenant(tenantId))
                .compose(b -> {
                    context.assertTrue(IntegrationTestSupport.testJsonObjectToBeContained(b.toJsonObject(), requestBody));
                    return Future.succeededFuture();
                }).setHandler(context.asyncAssertSuccess());
    }

    /**
     * Verifies that the service returns a 400 status code for an add tenant request containing
     * a malformed trust configuration (i.e an invalid Base64 encoding value of the trust CA's certificate).
     *
     * @param context The Vert.x test context.
     */
    @Test
    public void testAddTenantFailsForMalformedTrustConfiguration(final TestContext context) {
        final JsonObject trustConfig = new JsonObject()
                .put(TenantConstants.FIELD_PAYLOAD_SUBJECT_DN, "test-dn")
                .put(TenantConstants.FIELD_PAYLOAD_CERT, "NotBased64Encoded");
        final JsonObject requestBody = buildTenantPayload(tenantId)
                .put(TenantConstants.FIELD_PAYLOAD_TRUSTED_CA, trustConfig);
        registry.addTenant(requestBody, "application/json", HttpURLConnection.HTTP_BAD_REQUEST).setHandler(context.asyncAssertSuccess());
    }

    /**
     * Creates a tenant object for a tenantId.
     * <p>
     * The tenant object created contains configurations for the http and the mqtt adapter.
     *
     * @param tenantId The tenant identifier.
     * @return The tenant object.
     */
    private static JsonObject buildTenantPayload(final String tenantId) {
        final JsonArray adapterConfigs = new JsonArray();
        adapterConfigs.add(TenantObject.newAdapterConfig(Constants.PROTOCOL_ADAPTER_TYPE_HTTP, true)
                .put(TenantConstants.FIELD_ADAPTERS_DEVICE_AUTHENTICATION_REQUIRED, true));
        adapterConfigs.add(TenantObject.newAdapterConfig(Constants.PROTOCOL_ADAPTER_TYPE_MQTT, true)
                .put(TenantConstants.FIELD_ADAPTERS_DEVICE_AUTHENTICATION_REQUIRED, true));
        adapterConfigs.add(TenantObject.newAdapterConfig("custom", false)
                .put("options", new JsonObject().put("maxInstances", 4)));
        final TenantObject tenantPayload = TenantObject.from(tenantId, true)
                .setProperty("plan", "unlimited")
                .setAdapterConfigurations(adapterConfigs);
        return JsonObject.mapFrom(tenantPayload);
    }

}
