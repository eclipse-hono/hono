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

package org.eclipse.hono.deviceregistry;

import static org.eclipse.hono.util.TenantConstants.FIELD_ENABLED;

import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.util.UUID;

import org.eclipse.hono.config.ServiceConfigProperties;
import org.eclipse.hono.service.http.HttpUtils;
import org.eclipse.hono.service.tenant.TenantHttpEndpoint;
import org.eclipse.hono.util.TenantConstants;
import org.junit.*;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;

/**
 * Tests the Tenants REST Interface of the {@link DeviceRegistryRestServer}.
 */
@RunWith(VertxUnitRunner.class)
public class TenantsRestServerTest {

    private static final String HOST = InetAddress.getLoopbackAddress().getHostAddress();
    private static final String TENANT = "testTenant";
    private static final String URI_ADD_TENANTS= "/" + TenantConstants.TENANT_ENDPOINT;
    private static final String TEMPLATE_URI_TENANTS_INSTANCE = String.format("/%s/%%s", TenantConstants.TENANT_ENDPOINT);
    private static final long TEST_TIMEOUT_MILLIS = 2000;
    private static final Vertx vertx = Vertx.vertx();

    private static FileBasedTenantService tenantsService;
    private static DeviceRegistryRestServer deviceRegistryRestServer;

    /**
     * Set the timeout for all test methods by using a JUnit Rule (instead of providing the timeout at every @Test annotation).
     * See {@link Test#timeout} for details about improved thread safety regarding the @After annotation for each test.
     */
    @Rule
    public final TestRule timeoutForAllMethods = Timeout.millis(TEST_TIMEOUT_MILLIS);

    /**
     * Deploys the server to vert.x.
     * 
     * @param context The vert.x test context.
     */
    @BeforeClass
    public static void setUp(final TestContext context) {

        final Future<String> restServerDeploymentTracker = Future.future();
        final Future<String> tenantsServiceDeploymentTracker = Future.future();

        tenantsService = new FileBasedTenantService();
        tenantsService.setConfig(new FileBasedTenantsConfigProperties());
        vertx.deployVerticle(tenantsService, tenantsServiceDeploymentTracker.completer());

        final ServiceConfigProperties restServerProps = new ServiceConfigProperties();
        restServerProps.setInsecurePortEnabled(true);
        restServerProps.setInsecurePort(0);
        restServerProps.setInsecurePortBindAddress(HOST);

        deviceRegistryRestServer = new DeviceRegistryRestServer();
        deviceRegistryRestServer.addEndpoint(new TenantHttpEndpoint(vertx));
        deviceRegistryRestServer.setConfig(restServerProps);
        vertx.deployVerticle(deviceRegistryRestServer, restServerDeploymentTracker.completer());

        CompositeFuture.all(restServerDeploymentTracker, tenantsServiceDeploymentTracker)
                .setHandler(context.asyncAssertSuccess());

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
     * Removes all entries from the Tenant service.
     */
    @After
    public void clearRegistry() {
        tenantsService.clear();
    }

    private static int getPort() {
        return deviceRegistryRestServer.getInsecurePort();
    }

    /**
     * Verifies that the service accepts an add tenant request containing a valid tenant structure
     * and that the response contains a <em>Location</em> header for the created resource.
     * 
     * @param context The vert.x test context.
     */
    @Test
    public void testAddTenantSucceeds(final TestContext context)  {

        final String tenantId = getRandomTenantId("TEST_TENANT");
        final JsonObject requestBodyAddTenant = DeviceRegistryTestUtils.buildTenantPayload(tenantId);
        addTenant(requestBodyAddTenant).setHandler(context.asyncAssertSuccess(r -> {
            context.assertNotNull(r.getHeader(HttpHeaders.LOCATION));
        }));
    }

    /**
     * Verify that a correctly filled json payload to add a tenant for an already existing record is
     * responded with {@link HttpURLConnection#HTTP_CONFLICT} and a non empty error response message.
     * 
     * @param context The vert.x test context.
     */
    @Test
    public void testAddTenantRejectsDuplicateRegistration(final TestContext context)  {

        final String tenantId = getRandomTenantId("TEST_TENANT");
        final JsonObject requestBodyAddTenant = DeviceRegistryTestUtils.buildTenantPayload(tenantId);

        addTenant(requestBodyAddTenant).compose(ar -> {
            // now try to add the tenant again
            return addTenant(requestBodyAddTenant, HttpURLConnection.HTTP_CONFLICT);
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

        final String tenantId = getRandomTenantId("TEST_TENANT");
        addTenant(
                DeviceRegistryTestUtils.buildTenantPayload(tenantId),
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

        addTenant(null, HttpUtils.CONTENT_TYPE_JSON, HttpURLConnection.HTTP_BAD_REQUEST).setHandler(context.asyncAssertSuccess());
    }

    /**
     * Verifies that the service accepts an update tenant request for an existing tenant.
     *
     * @param context The vert.x test context.
     */
    @Test
    public void testUpdateTenantSucceeds(final TestContext context) {

        final String tenantId = getRandomTenantId(TENANT);
        final JsonObject orig = DeviceRegistryTestUtils.buildTenantPayload(tenantId);
        final JsonObject altered = orig.copy();
        altered.put(FIELD_ENABLED, "false");

        addTenant(orig).compose(ar -> {
            return updateTenant(tenantId, altered);
        }).compose(ur -> {
            context.assertEquals(HttpURLConnection.HTTP_NO_CONTENT, ur.statusCode());
            return getTenant(tenantId, b -> {
                // compare the changed field only
                context.assertEquals("false", b.toJsonObject().getString(FIELD_ENABLED));
            });
        }).setHandler(context.asyncAssertSuccess(gr -> {
            context.assertEquals(HttpURLConnection.HTTP_OK, gr.statusCode());
        }));
    }

    /**
     * Verifies that the service rejects an update request for a non-existing tenant.
     *
     * @param context The vert.x test context.
     */
    @Test
    public void testUpdateTenantFailsForNonExistingTenant(final TestContext context) {

        final String tenantId = getRandomTenantId(TENANT);
        final JsonObject altered = DeviceRegistryTestUtils.buildTenantPayload(tenantId);

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

        final String tenantId = getRandomTenantId(TENANT);
        final JsonObject tenantPayload = DeviceRegistryTestUtils.buildTenantPayload(tenantId);
        addTenant(tenantPayload).compose(ar -> {
            return removeTenant(tenantId, HttpURLConnection.HTTP_NO_CONTENT);
        }).setHandler(context.asyncAssertSuccess());
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
     * Verify that a correctly added tenant record can be successfully looked up again.
     *
     * @param context The vert.x test context.
     */
    @Test
    public void testGetAddedTenant(final TestContext context)  {

        final String tenantId = getRandomTenantId(TENANT);
        final JsonObject requestBody = DeviceRegistryTestUtils.buildTenantPayload(tenantId);

        addTenant(requestBody).compose(ar -> {
            // now try to get tenant again
            return getTenant(tenantId, b -> {
                context.assertTrue(DeviceRegistryTestUtils.testJsonObjectToBeContained(b.toJsonObject(), requestBody));
            });
        }).setHandler(context.asyncAssertSuccess());
    }

    private static String getRandomTenantId(final String tenantIdPrefix) {
        return tenantIdPrefix + "." + UUID.randomUUID();
    }

    private static Future<HttpClientResponse> addTenant(final JsonObject requestPayload) {
        return addTenant(requestPayload, HttpURLConnection.HTTP_CREATED);
    }

    private static Future<HttpClientResponse> addTenant(final JsonObject requestPayload, final int expectedStatusCode) {
        return addTenant(requestPayload, HttpUtils.CONTENT_TYPE_JSON, expectedStatusCode);
    }

    private static Future<HttpClientResponse> addTenant(final JsonObject requestPayload, final String contentType, final int expectedStatusCode) {

        final Future<HttpClientResponse> result = Future.future();
        final HttpClientRequest req = vertx.createHttpClient().post(getPort(), HOST, URI_ADD_TENANTS)
                .putHeader(HttpHeaders.CONTENT_TYPE, contentType)
                .handler(response -> {
                    if (response.statusCode() == expectedStatusCode) {
                        result.complete(response);
                    } else {
                        result.fail("add tenant failed, expected status code " + expectedStatusCode + " but got " + response.statusCode());
                    }
                }).exceptionHandler(result::fail);

        if (requestPayload == null) {
            req.end();
        } else {
            req.end(requestPayload.encodePrettily());
        }
        return result;
    }

    private static final Future<HttpClientResponse> getTenant(final String tenantId, final Handler<Buffer> responseBodyHandler) {
        return getTenant(tenantId, HttpURLConnection.HTTP_OK, responseBodyHandler);
    }

    private static final Future<HttpClientResponse> getTenant(final String tenantId, final int expectedStatusCode,
            final Handler<Buffer> responseBodyHandler) {

        final Future<HttpClientResponse> result = Future.future();
        final String uri = String.format(TEMPLATE_URI_TENANTS_INSTANCE, tenantId);

        vertx.createHttpClient().get(getPort(), HOST, uri).handler(response -> {
            if (response.statusCode() == expectedStatusCode) {
                if (responseBodyHandler != null) {
                    response.bodyHandler(b -> {
                            responseBodyHandler.handle(b);
                            result.complete(response);
                    });
                } else {
                    result.complete(response);
                }
            } else {
                result.fail("expected response [" + expectedStatusCode + "] but got [" + response.statusCode() + "]");
            }
        }).exceptionHandler(result::fail).end();

        return result;
    }



    private static final Future<HttpClientResponse> updateTenant(final String tenantId, final JsonObject requestPayload) {
        return updateTenant(tenantId, requestPayload, HttpURLConnection.HTTP_NO_CONTENT);
    }

    private static final Future<HttpClientResponse> updateTenant(final String tenantId, final JsonObject requestPayload, final int expectedResult) {

        final Future<HttpClientResponse> result = Future.future();
        final String uri = String.format(TEMPLATE_URI_TENANTS_INSTANCE, tenantId);

        final HttpClientRequest req = vertx.createHttpClient().put(getPort(), HOST, uri)
                .putHeader(HttpHeaders.CONTENT_TYPE, HttpUtils.CONTENT_TYPE_JSON)
                .handler(response -> {
                    if (response.statusCode() == expectedResult) {
                        result.complete(response);
                    } else {
                        result.fail("update tenant failed, expected status code " + expectedResult + " but got " + response.statusCode());
                    }
                })
                .exceptionHandler(result::fail);

        if (requestPayload == null) {
            req.end();
        } else {
            req.end(requestPayload.encodePrettily());
        }
        return result;
    }

    private static final Future<HttpClientResponse> removeTenant(final String tenantId) {

        final Future<HttpClientResponse> result = Future.future();
        final String uri = String.format(TEMPLATE_URI_TENANTS_INSTANCE, tenantId);

        vertx.createHttpClient().delete(getPort(), HOST, uri).handler(result::complete).exceptionHandler(result::fail).end();
        return result;
    }

    private static final Future<HttpClientResponse> removeTenant(final String tenantId, final int expectedResponseStatus) {

        final Future<HttpClientResponse> result = Future.future();
        final String uri = String.format(TEMPLATE_URI_TENANTS_INSTANCE, tenantId);

        vertx.createHttpClient().delete(getPort(), HOST, uri).handler(response -> {
            if (response.statusCode() == expectedResponseStatus) {
                result.complete(response);
            } else {
                result.fail("remove tenant failed, expected response [" + expectedResponseStatus + "] but got [" + response.statusCode() + "]");
            }
        }).exceptionHandler(result::fail).end();
        return result;
    }

}
