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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.HttpURLConnection;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.eclipse.hono.tests.DeviceRegistryHttpClient;
import org.eclipse.hono.tests.IntegrationTestSupport;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.RegistryManagementConstants;
import org.eclipse.hono.util.TenantConstants;
import org.eclipse.hono.util.TenantObject;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.Checkpoint;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

/**
 * Tests verifying the Device Registry component by making HTTP requests to its
 * Tenant HTTP endpoint and validating the corresponding responses.
 */
@ExtendWith(VertxExtension.class)
@Timeout(value = 5, timeUnit = TimeUnit.SECONDS)
public class TenantHttpIT {

    private static final Logger LOG = LoggerFactory.getLogger(TenantHttpIT.class);
    private static final String TENANT_PREFIX = "testTenant";
    private static final Vertx vertx = Vertx.vertx();

    private static DeviceRegistryHttpClient registry;

    private String tenantId;

    /**
     * Creates the HTTP client for accessing the registry.
     */
    @BeforeAll
    public static void setUpClient() {

        registry = new DeviceRegistryHttpClient(
                vertx,
                IntegrationTestSupport.HONO_DEVICEREGISTRY_HOST,
                IntegrationTestSupport.HONO_DEVICEREGISTRY_HTTP_PORT);
    }

    /**
     * Sets up the fixture.
     * 
     * @param testInfo The test meta data.
     */
    @BeforeEach
    public void setUp(final TestInfo testInfo) {
        LOG.debug("running test: {}", testInfo.getDisplayName());
        tenantId = TENANT_PREFIX + "." + UUID.randomUUID();
    }

    /**
     * Removes the credentials that have been added by the test.
     * 
     * @param ctx The vert.x test context.
     */
    @AfterEach
    public void removeTenant(final VertxTestContext ctx) {

        final Checkpoint deletion = ctx.checkpoint();
        registry.removeTenant(tenantId, HttpURLConnection.HTTP_NO_CONTENT)
        .setHandler(attempt -> deletion.flag());
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

        final JsonObject requestBodyAddTenant = buildTenantPayload();
        registry.addTenant(tenantId, requestBodyAddTenant).setHandler(context.completing());
    }

    /**
     * Verifies that a correctly filled JSON payload to add a tenant for an already existing record is
     * responded with {@link HttpURLConnection#HTTP_CONFLICT} and a non empty error response message.
     * 
     * @param context The vert.x test context.
     */
    @Test
    public void testAddTenantRejectsDuplicateRegistration(final VertxTestContext context)  {

        final JsonObject payload = buildTenantPayload();

        registry.addTenant(tenantId, payload).compose(ar -> {
            // now try to add the tenant again
            return registry.addTenant(tenantId, payload, HttpURLConnection.HTTP_CONFLICT);
        }).setHandler(context.completing());
    }

    /**
     * Verifies that the service returns a 400 status code for an add tenant request with a Content-Type
     * other than application/json.
     * 
     * @param context The vert.x test context.
     */
    @Test
    public void testAddTenantFailsForWrongContentType(final VertxTestContext context)  {

        registry.addTenant(tenantId,
                buildTenantPayload(),
                "application/x-www-form-urlencoded",
                HttpURLConnection.HTTP_BAD_REQUEST).setHandler(context.completing());
    }

    /**
     * Verifies that the service successfully create a tenant from a request with an empty body.
     * 
     * @param context The vert.x test context.
     */
    @Test
    public void testAddTenantSucceedsForEmptyBody(final VertxTestContext context) {

        registry.addTenant(tenantId, null, HttpURLConnection.HTTP_CREATED)
        .setHandler(context.completing());
    }

    /**
     * Verifies that the service accepts an update tenant request for an existing tenant.
     *
     * @param context The vert.x test context.
     */
    @Test
    public void testUpdateTenantSucceeds(final VertxTestContext context) {

        final JsonObject orig = buildTenantPayload();
        final JsonObject altered = orig.copy();
        altered.put(TenantConstants.FIELD_ENABLED, Boolean.FALSE);

        registry.addTenant(tenantId, orig)
            .compose(ar -> registry.updateTenant(tenantId, altered, HttpURLConnection.HTTP_NO_CONTENT))
            .compose(ur -> registry.getTenant(tenantId))
            .compose(b -> {
                // compare the changed field only
                context.verify(() -> {
                    assertFalse(b.toJsonObject().getBoolean(TenantConstants.FIELD_ENABLED, Boolean.TRUE));
                });
                return Future.succeededFuture();
            }).setHandler(context.completing());
    }

    /**
     * Verifies that the service rejects an update request for a non-existing tenant.
     *
     * @param context The vert.x test context.
     */
    @Test
    public void testUpdateTenantFailsForNonExistingTenant(final VertxTestContext context) {

        final JsonObject altered = buildTenantPayload();

        registry.updateTenant(tenantId, altered, HttpURLConnection.HTTP_NOT_FOUND)
            .setHandler(context.completing());
    }


    /**
     * Verify that a correctly added tenant record can be successfully deleted again.
     *
     * @param context The vert.x test context.
     */
    @Test
    public void testRemoveTenantSucceeds(final VertxTestContext context) {

        final JsonObject tenantPayload = buildTenantPayload();
        registry.addTenant(tenantId, tenantPayload)
            .compose(ar -> registry.removeTenant(tenantId, HttpURLConnection.HTTP_NO_CONTENT))
            .setHandler(context.completing());
    }


    /**
     * Verifies that a request to delete a tenant that does not exist fails.
     *
     * @param context The vert.x test context.
     */
    @Test
    public void testRemoveTenantFailsForNonExistingTenant(final VertxTestContext context) {

        registry.removeTenant("non-existing-tenant", HttpURLConnection.HTTP_NOT_FOUND)
        .setHandler(context.completing());
    }

    /**
     * Verifies that a correctly added tenant record can be successfully looked up again.
     *
     * @param context The vert.x test context.
     */
    @Test
    public void testGetAddedTenant(final VertxTestContext context)  {

        final JsonObject requestBody = buildTenantPayload()
                .put(RegistryManagementConstants.FIELD_MINIMUM_MESSAGE_SIZE, 2048)
                .put(RegistryManagementConstants.FIELD_RESOURCE_LIMITS, new JsonObject()
                        .put(RegistryManagementConstants.FIELD_RESOURCE_LIMITS_MAX_CONNECTIONS, 1000));

        LOG.debug("registering tenant using Management API: {}", requestBody.encodePrettily());
        registry.addTenant(tenantId, requestBody)
            .compose(ar -> registry.getTenant(tenantId))
            .setHandler(context.succeeding(b -> {
                final JsonObject json = b.toJsonObject();
                LOG.debug("retrieved tenant using Tenant API: {}", json.encodePrettily());
                context.verify(() -> {
                    assertTrue(IntegrationTestSupport.testJsonObjectToBeContained(json, requestBody));
                });
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

        final JsonObject trustConfig = new JsonObject()
                .put(TenantConstants.FIELD_PAYLOAD_SUBJECT_DN, "test-dn")
                .put(TenantConstants.FIELD_PAYLOAD_CERT, "NotBased64Encoded");
        final JsonObject requestBody = buildTenantPayload()
                .put(TenantConstants.FIELD_PAYLOAD_TRUSTED_CA, trustConfig);
        registry.addTenant(tenantId, requestBody, "application/json", HttpURLConnection.HTTP_BAD_REQUEST)
        .setHandler(context.completing());
    }

    /**
     * Creates a tenant object for a tenantId.
     * <p>
     * The tenant object created contains configurations for the http and the mqtt adapter.
     *
     * @return The tenant object.
     */
    private static JsonObject buildTenantPayload() {

        final JsonArray adapterConfigs = new JsonArray();
        adapterConfigs.add(TenantObject.newAdapterConfig(Constants.PROTOCOL_ADAPTER_TYPE_HTTP, true)
                .put(TenantConstants.FIELD_ADAPTERS_DEVICE_AUTHENTICATION_REQUIRED, true));
        adapterConfigs.add(TenantObject.newAdapterConfig(Constants.PROTOCOL_ADAPTER_TYPE_MQTT, true)
                .put(TenantConstants.FIELD_ADAPTERS_DEVICE_AUTHENTICATION_REQUIRED, true));
        adapterConfigs.add(TenantObject.newAdapterConfig("custom", false)
                .put(RegistryManagementConstants.FIELD_EXT, new JsonObject()
                        .put("options", new JsonObject().put("maxInstances", 4))));
        final TenantObject tenantPayload = TenantObject.from(true)
                .setProperty(RegistryManagementConstants.FIELD_EXT, new JsonObject().put("plan", "unlimited"))
                .setAdapterConfigurations(adapterConfigs);
        return JsonObject.mapFrom(tenantPayload);
    }
}
