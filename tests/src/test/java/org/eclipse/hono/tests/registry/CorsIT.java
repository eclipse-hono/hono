/*******************************************************************************
 * Copyright (c) 2016, 2021 Contributors to the Eclipse Foundation
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
import java.util.Optional;

import org.eclipse.hono.tests.CrudHttpClient;
import org.eclipse.hono.tests.DeviceRegistryHttpClient;
import org.eclipse.hono.tests.IntegrationTestSupport;
import org.eclipse.hono.util.Constants;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.client.predicate.ResponsePredicate;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

/**
 * Tests verifying CORS compliance of the device registry management API.
 *
 */
@ExtendWith(VertxExtension.class)
public class CorsIT {

    /**
     * A client for sending requests to the device registry management API.
     */
    protected static CrudHttpClient httpClient;

    private static final String authenticationHeaderValue = IntegrationTestSupport.getRegistryManagementApiAuthHeader();

    /**
     * Sets up clients.
     *
     * @param vertx The vert.x instance.
     */
    @BeforeAll
    public static void init(final Vertx vertx) {

        httpClient = new CrudHttpClient(
                vertx,
                IntegrationTestSupport.HONO_DEVICEREGISTRY_HOST,
                IntegrationTestSupport.HONO_DEVICEREGISTRY_HTTP_PORT);
    }

    /**
     * Verifies that the device registry returns matching CORS headers in response to a
     * CORS preflight request for adding tenant data.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testCorsPreflightRequestForAddingTenant(final VertxTestContext ctx) {

        final String uri = String.format(DeviceRegistryHttpClient.TEMPLATE_URI_TENANT_INSTANCE, Constants.DEFAULT_TENANT);
        doPreflightCheck(uri, HttpMethod.POST, ctx);
    }

    /**
     * Verifies that the device registry returns matching CORS headers in response to a
     * CORS preflight request for updating tenant data.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testCorsPreflightRequestForUpdatingTenant(final VertxTestContext ctx) {

        final String uri = String.format(DeviceRegistryHttpClient.TEMPLATE_URI_TENANT_INSTANCE, Constants.DEFAULT_TENANT);
        doPreflightCheck(uri, HttpMethod.PUT, ctx);
    }

    /**
     * Verifies that the device registry returns matching CORS headers in response to a
     * CORS preflight request for getting tenant data.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testCorsPreflightRequestForGettingTenant(final VertxTestContext ctx) {

        final String uri = String.format(DeviceRegistryHttpClient.TEMPLATE_URI_TENANT_INSTANCE, Constants.DEFAULT_TENANT);
        doPreflightCheck(uri, HttpMethod.GET, ctx);
    }

    /**
     * Verifies that the device registry returns matching CORS headers in response to a
     * CORS preflight request for deleting tenant data.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testCorsPreflightRequestForDeletingTenant(final VertxTestContext ctx) {

        final String uri = String.format(DeviceRegistryHttpClient.TEMPLATE_URI_TENANT_INSTANCE, Constants.DEFAULT_TENANT);
        doPreflightCheck(uri, HttpMethod.DELETE, ctx);
    }

    /**
     * Verifies that the device registry returns matching CORS headers in response to a
     * CORS preflight request for creating device data.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testCorsPreflightRequestForCreatingDevice(final VertxTestContext ctx) {

        final String uri = String.format(DeviceRegistryHttpClient.TEMPLATE_URI_REGISTRATION_INSTANCE, Constants.DEFAULT_TENANT, "my-device");
        doPreflightCheck(uri, HttpMethod.POST, ctx);
    }

    /**
     * Verifies that the device registry returns matching CORS headers in response to a
     * CORS preflight request for updating device data.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testCorsPreflightRequestForUpdatingDevice(final VertxTestContext ctx) {

        final String uri = String.format(DeviceRegistryHttpClient.TEMPLATE_URI_REGISTRATION_INSTANCE, Constants.DEFAULT_TENANT, "my-device");
        doPreflightCheck(uri, HttpMethod.PUT, ctx);
    }

    /**
     * Verifies that the device registry returns matching CORS headers in response to a
     * CORS preflight request for getting device data.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testCorsPreflightRequestForGettingDevice(final VertxTestContext ctx) {

        final String uri = String.format(DeviceRegistryHttpClient.TEMPLATE_URI_REGISTRATION_INSTANCE, Constants.DEFAULT_TENANT, "my-device");
        doPreflightCheck(uri, HttpMethod.GET, ctx);
    }

    /**
     * Verifies that the device registry returns matching CORS headers in response to a
     * CORS preflight request for deleting device data.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testCorsPreflightRequestForDeletingDevice(final VertxTestContext ctx) {

        final String uri = String.format(DeviceRegistryHttpClient.TEMPLATE_URI_REGISTRATION_INSTANCE, Constants.DEFAULT_TENANT, "my-device");
        doPreflightCheck(uri, HttpMethod.DELETE, ctx);
    }

    /**
     * Verifies that the HTTP adapter returns matching CORS headers in response to a
     * CORS preflight request for updating credentials data.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testCorsPreflightRequestForUpdatingCredentials(final VertxTestContext ctx) {

        final String uri = String.format(DeviceRegistryHttpClient.TEMPLATE_URI_CREDENTIALS_BY_DEVICE, Constants.DEFAULT_TENANT, "my-device");
        doPreflightCheck(uri, HttpMethod.PUT, ctx);
    }

    /**
     * Verifies that the device registry returns matching CORS headers in response to a
     * CORS preflight request for getting credentials data.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testCorsPreflightRequestForGettingCredentials(final VertxTestContext ctx) {

        final String uri = String.format(DeviceRegistryHttpClient.TEMPLATE_URI_CREDENTIALS_BY_DEVICE, Constants.DEFAULT_TENANT, "my-device");
        doPreflightCheck(uri, HttpMethod.GET, ctx);
    }

    private void doPreflightCheck(final String uri, final HttpMethod method, final VertxTestContext ctx) {

        httpClient.options(
                uri,
                getRequestHeaders()
                    .add(HttpHeaders.ORIGIN, CrudHttpClient.ORIGIN_URI)
                    .add(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, method.name()),
                ResponsePredicate.status(HttpURLConnection.HTTP_NO_CONTENT))
            .onComplete(ctx.succeeding(response -> {
                ctx.verify(() -> {
                    assertAccessControlHeaders(response.headers(), method);
                 });
                ctx.completeNow();
            }));
    }

    private MultiMap getRequestHeaders() {
        final var requestHeaders = MultiMap.caseInsensitiveMultiMap();
        Optional.ofNullable(authenticationHeaderValue)
            .ifPresent(v -> requestHeaders.add(HttpHeaders.AUTHORIZATION, v));
        return requestHeaders;
    }

    private static void assertAccessControlHeaders(
            final MultiMap headers,
            final HttpMethod expectedAllowedMethod) {

        Assertions.assertTrue(headers.get(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS).contains(expectedAllowedMethod.name()));
        Assertions.assertEquals("*", headers.get(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
        Assertions.assertTrue(headers.get(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS).contains(HttpHeaders.AUTHORIZATION));
        Assertions.assertTrue(headers.get(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS).contains(HttpHeaders.CONTENT_TYPE));
        Assertions.assertTrue(headers.get(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS).contains(HttpHeaders.IF_MATCH));
    }
}
