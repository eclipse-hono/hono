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

package org.eclipse.hono.tests.http;

import java.net.HttpURLConnection;

import org.eclipse.hono.tests.CrudHttpClient;
import org.eclipse.hono.tests.IntegrationTestSupport;
import org.eclipse.hono.util.CommandConstants;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.EventConstants;
import org.eclipse.hono.util.TelemetryConstants;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;

import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;


/**
 * Tests verifying CORS compliance of the HTTP adapter.
 *
 */
@RunWith(VertxUnitRunner.class)
public class CorsIT {

    private static final Vertx VERTX = Vertx.vertx();
    private static final String CORS_ORIGIN = "http://hono.eclipse.org";

    /**
     * A client for connecting to Hono Messaging.
     */
    protected static CrudHttpClient httpClient;

    /**
     * Time out each test after five seconds.
     */
    @Rule
    public final Timeout timeout = Timeout.seconds(5);

    /**
     * Sets up clients.
     * 
     * @param ctx The vert.x test context.
     */
    @BeforeClass
    public static void init(final TestContext ctx) {

        httpClient = new CrudHttpClient(
                VERTX,
                IntegrationTestSupport.HTTP_HOST,
                IntegrationTestSupport.HTTP_PORT);
    }

    /**
     * Verifies that the HTTP adapter returns matching CORS headers in response to a
     * CORS preflight request for posting telemetry data.
     * 
     * @param ctx The vert.x test context.
     */
    @Test
    public void testCorsPreflightRequestForPostingTelemetry(final TestContext ctx) {

        httpClient.options(
                "/" + TelemetryConstants.TELEMETRY_ENDPOINT,
                MultiMap.caseInsensitiveMultiMap()
                    .add(HttpHeaders.ORIGIN, CORS_ORIGIN)
                    .add(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.POST.name()),
                status -> status == HttpURLConnection.HTTP_OK)
        .setHandler(ctx.asyncAssertSuccess(headers -> {
            assertAccessControlHeaders(ctx, headers, HttpMethod.POST);
            ctx.assertTrue(headers.get(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS).contains(Constants.HEADER_QOS_LEVEL));
            ctx.assertTrue(headers.get(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS).contains(Constants.HEADER_TIME_TIL_DISCONNECT));
        }));
    }

    /**
     * Verifies that the HTTP adapter returns matching CORS headers in response to a
     * CORS preflight request for putting telemetry data.
     * 
     * @param ctx The vert.x test context.
     */
    @Test
    public void testCorsPreflightRequestForPuttingTelemetry(final TestContext ctx) {

        httpClient.options(
                String.format("/%s/%s/%s", TelemetryConstants.TELEMETRY_ENDPOINT, "my-tenant", "my-device"),
                MultiMap.caseInsensitiveMultiMap()
                    .add(HttpHeaders.ORIGIN, CORS_ORIGIN)
                    .add(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.PUT.name()),
                status -> status == HttpURLConnection.HTTP_OK)
        .setHandler(ctx.asyncAssertSuccess(headers -> {
            assertAccessControlHeaders(ctx, headers, HttpMethod.PUT);
            ctx.assertTrue(headers.get(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS).contains(Constants.HEADER_QOS_LEVEL));
            ctx.assertTrue(headers.get(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS).contains(Constants.HEADER_TIME_TIL_DISCONNECT));
        }));
    }

    /**
     * Verifies that the HTTP adapter returns matching CORS headers in response to a
     * CORS preflight request for posting an event.
     * 
     * @param ctx The vert.x test context.
     */
    @Test
    public void testCorsPreflightRequestForPostingEvents(final TestContext ctx) {

        httpClient.options(
                "/" + EventConstants.EVENT_ENDPOINT,
                MultiMap.caseInsensitiveMultiMap()
                    .add(HttpHeaders.ORIGIN, CORS_ORIGIN)
                    .add(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.POST.name()),
                status -> status == HttpURLConnection.HTTP_OK)
        .setHandler(ctx.asyncAssertSuccess(headers -> {
            assertAccessControlHeaders(ctx, headers, HttpMethod.POST);
            ctx.assertTrue(headers.get(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS).contains(Constants.HEADER_TIME_TIL_DISCONNECT));
        }));
    }

    /**
     * Verifies that the HTTP adapter returns matching CORS headers in response to a
     * CORS preflight request for putting an event.
     * 
     * @param ctx The vert.x test context.
     */
    @Test
    public void testCorsPreflightRequestForPuttingEvents(final TestContext ctx) {

        httpClient.options(
                String.format("/%s/%s/%s", EventConstants.EVENT_ENDPOINT, "my-tenant", "my-device"),
                MultiMap.caseInsensitiveMultiMap()
                    .add(HttpHeaders.ORIGIN, CORS_ORIGIN)
                    .add(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.PUT.name()),
                status -> status == HttpURLConnection.HTTP_OK)
        .setHandler(ctx.asyncAssertSuccess(headers -> {
            assertAccessControlHeaders(ctx, headers, HttpMethod.PUT);
            ctx.assertTrue(headers.get(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS).contains(Constants.HEADER_TIME_TIL_DISCONNECT));
        }));
    }

    /**
     * Verifies that the HTTP adapter returns matching CORS headers in response to a
     * CORS preflight request for posting a command response.
     * 
     * @param ctx The vert.x test context.
     */
    @Test
    public void testCorsPreflightRequestForPostingCommandResponse(final TestContext ctx) {

        httpClient.options(
                String.format("/%s/res/%s", CommandConstants.COMMAND_ENDPOINT, "cmd-request-id"),
                MultiMap.caseInsensitiveMultiMap()
                    .add(HttpHeaders.ORIGIN, CORS_ORIGIN)
                    .add(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.POST.name()),
                status -> status == HttpURLConnection.HTTP_OK)
        .setHandler(ctx.asyncAssertSuccess(headers -> {
            assertAccessControlHeaders(ctx, headers, HttpMethod.POST);
            ctx.assertTrue(headers.get(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS).contains(Constants.HEADER_COMMAND_RESPONSE_STATUS));
        }));
    }

    /**
     * Verifies that the HTTP adapter returns matching CORS headers in response to a
     * CORS preflight request for putting a command response.
     * 
     * @param ctx The vert.x test context.
     */
    @Test
    public void testCorsPreflightRequestForPuttingCommandResonse(final TestContext ctx) {

        httpClient.options(
                String.format("/%s/res/%s/%s/%s", CommandConstants.COMMAND_ENDPOINT, "my-tenant", "my-device", "cmd-request-id"),
                MultiMap.caseInsensitiveMultiMap()
                    .add(HttpHeaders.ORIGIN, CORS_ORIGIN)
                    .add(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.PUT.name()),
                status -> status == HttpURLConnection.HTTP_OK)
        .setHandler(ctx.asyncAssertSuccess(headers -> {
            assertAccessControlHeaders(ctx, headers, HttpMethod.PUT);
            ctx.assertTrue(headers.get(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS).contains(Constants.HEADER_COMMAND_RESPONSE_STATUS));
        }));
    }

    private static void assertAccessControlHeaders(final TestContext ctx, final MultiMap headers, final HttpMethod expectedAllowedMethod) {

        ctx.assertTrue(headers.get(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS).contains(expectedAllowedMethod.name()));
        ctx.assertEquals("*", headers.get(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
        ctx.assertTrue(headers.get(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS).contains(HttpHeaders.AUTHORIZATION));
        ctx.assertTrue(headers.get(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS).contains(HttpHeaders.CONTENT_TYPE));
    }
}
