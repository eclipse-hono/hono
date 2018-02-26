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
import java.util.UUID;

import org.eclipse.hono.tests.CrudHttpClient;
import org.eclipse.hono.tests.IntegrationTestSupport;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.TenantConstants;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;

/**
 * Tests verifying the Device Registry component by making HTTP requests to its
 * Tenant HTTP endpoint and validating the corresponding responses.
 */
@RunWith(VertxUnitRunner.class)
public class TenantHttpIT {

    private static final String TENANT = "testTenant";
    private static final String URI_ADD_TENANTS= "/" + TenantConstants.TENANT_ENDPOINT;
    private static final String TEMPLATE_URI_TENANTS_INSTANCE = String.format("/%s/%%s", TenantConstants.TENANT_ENDPOINT);
    private static final Vertx vertx = Vertx.vertx();

    private static CrudHttpClient httpClient;

    private String tenantId;

    /**
     * Set the timeout for all test methods by using a JUnit Rule (instead of providing the timeout at every @Test annotation).
     * See {@link Test#timeout} for details about improved thread safety regarding the @After annotation for each test.
     */
    @Rule
    public final TestRule timeoutForAllMethods = Timeout.seconds(5);

    /**
     * Creates the HTTP client for accessing the registry.
     * 
     * @param ctx The vert.x test context.
     */
    @BeforeClass
    public static void setUpClient(final TestContext ctx) {

        httpClient = new CrudHttpClient(
                vertx,
                IntegrationTestSupport.HONO_DEVICEREGISTRY_HOST,
                IntegrationTestSupport.HONO_DEVICEREGISTRY_HTTP_PORT);
    }

    /**
     * Sets up the fixture.
     */
    @Before
    public void setUp() {
        tenantId = TENANT + "." + UUID.randomUUID();
    }

    /**
     * Removes the credentials that have been added by the test.
     * 
     * @param ctx The vert.x test context.
     */
    @After
    public void removeTenant(final TestContext ctx) {
        final Async deletion = ctx.async();
        removeTenant(tenantId, HttpURLConnection.HTTP_NO_CONTENT).setHandler(attempt -> deletion.complete());
        deletion.await();
    }

    /**
     * Shuts down the server.
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
        addTenant(requestBodyAddTenant).setHandler(context.asyncAssertSuccess());
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

        addTenant(payload).compose(ar -> {
            // now try to add the tenant again
            return addTenant(payload, HttpURLConnection.HTTP_CONFLICT);
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

        addTenant(
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

        addTenant(null, "application/json", HttpURLConnection.HTTP_BAD_REQUEST).setHandler(context.asyncAssertSuccess());
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

        addTenant(orig)
            .compose(ar -> updateTenant(tenantId, altered, HttpURLConnection.HTTP_NO_CONTENT))
            .compose(ur -> getTenant(tenantId))
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

        updateTenant(tenantId, altered, HttpURLConnection.HTTP_NOT_FOUND)
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
        addTenant(tenantPayload)
            .compose(ar -> removeTenant(tenantId, HttpURLConnection.HTTP_NO_CONTENT))
            .setHandler(context.asyncAssertSuccess());
    }


    /**
     * Verifies that a request to delete a tenant that does not exist fails.
     *
     * @param context The vert.x test context.
     */
    @Test
    public void testRemoveTenantFailsForNonExistingTenant(final TestContext context) {

        removeTenant("non-existing-tenant", HttpURLConnection.HTTP_NOT_FOUND).setHandler(context.asyncAssertSuccess());
    }

    /**
     * Verifies that a correctly added tenant record can be successfully looked up again.
     *
     * @param context The vert.x test context.
     */
    @Test
    public void testGetAddedTenant(final TestContext context)  {

        final JsonObject requestBody = buildTenantPayload(tenantId);

        addTenant(requestBody)
            .compose(ar -> getTenant(tenantId))
            .compose(b -> {
                context.assertTrue(IntegrationTestSupport.testJsonObjectToBeContained(b.toJsonObject(), requestBody));
                return Future.succeededFuture();
            }).setHandler(context.asyncAssertSuccess());
    }

    private static Future<Void> addTenant(final JsonObject requestPayload) {
        return addTenant(requestPayload, HttpURLConnection.HTTP_CREATED);
    }

    private static Future<Void> addTenant(final JsonObject requestPayload, final int expectedStatusCode) {
        return addTenant(requestPayload, "application/json", expectedStatusCode);
    }

    private static Future<Void> addTenant(final JsonObject requestPayload, final String contentType, final int expectedStatusCode) {

        return httpClient.create(URI_ADD_TENANTS, requestPayload, contentType, response -> response.statusCode() == expectedStatusCode);
    }

    private static final Future<Buffer> getTenant(final String tenantId) {
        return getTenant(tenantId, HttpURLConnection.HTTP_OK);
    }

    private static final Future<Buffer> getTenant(final String tenantId, final int expectedStatusCode) {

        final String uri = String.format(TEMPLATE_URI_TENANTS_INSTANCE, tenantId);
        return httpClient.get(uri, status -> status == expectedStatusCode);
    }

    private static final Future<Void> updateTenant(final String tenantId, final JsonObject requestPayload, final int expectedResult) {

        final String uri = String.format(TEMPLATE_URI_TENANTS_INSTANCE, tenantId);
        return httpClient.update(uri, requestPayload, status -> status == expectedResult);
    }

    private static final Future<Void> removeTenant(final String tenantId, final int expectedResponseStatus) {

        final String uri = String.format(TEMPLATE_URI_TENANTS_INSTANCE, tenantId);
        return httpClient.delete(uri, status -> status == expectedResponseStatus);
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
        final JsonObject adapterDetailsHttp = new JsonObject()
                .put(TenantConstants.FIELD_ADAPTERS_TYPE, Constants.TYPE_HTTP)
                .put(TenantConstants.FIELD_ADAPTERS_DEVICE_AUTHENTICATION_REQUIRED, Boolean.TRUE)
                .put(TenantConstants.FIELD_ENABLED, Boolean.TRUE);
        final JsonObject adapterDetailsMqtt = new JsonObject()
                .put(TenantConstants.FIELD_ADAPTERS_TYPE, Constants.TYPE_MQTT)
                .put(TenantConstants.FIELD_ADAPTERS_DEVICE_AUTHENTICATION_REQUIRED, Boolean.TRUE)
                .put(TenantConstants.FIELD_ENABLED, Boolean.TRUE);
        final JsonObject tenantPayload = new JsonObject()
                .put(TenantConstants.FIELD_TENANT_ID, tenantId)
                .put(TenantConstants.FIELD_ENABLED, Boolean.TRUE)
                .put(TenantConstants.FIELD_ADAPTERS, new JsonArray().add(adapterDetailsHttp).add(adapterDetailsMqtt));
        return tenantPayload;
    }

}
