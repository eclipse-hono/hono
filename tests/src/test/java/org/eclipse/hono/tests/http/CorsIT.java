/*******************************************************************************
 * Copyright (c) 2016, 2022 Contributors to the Eclipse Foundation
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

import static com.google.common.truth.Truth.assertThat;

import java.net.HttpURLConnection;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.eclipse.hono.tests.CrudHttpClient;
import org.eclipse.hono.tests.EnabledIfProtocolAdaptersAreRunning;
import org.eclipse.hono.tests.IntegrationTestSupport;
import org.eclipse.hono.util.CommandConstants;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.EventConstants;
import org.eclipse.hono.util.TelemetryConstants;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.client.predicate.ResponsePredicate;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;


/**
 * Tests verifying CORS compliance of the HTTP adapter.
 *
 */
@ExtendWith(VertxExtension.class)
@Timeout(timeUnit = TimeUnit.SECONDS, value = 5)
@EnabledIfProtocolAdaptersAreRunning(httpAdapter = true)
public class CorsIT {

    /**
     * A client for sending requests to the HTTP adapter.
     */
    protected static CrudHttpClient httpClient;

    private static final String authenticationHeaderValue = IntegrationTestSupport.getRegistryManagementApiAuthHeader();

    /**
     * Sets up clients.
     *
     * @param vertx The vert.x instance to run on.
     */
    @BeforeAll
    public static void init(final Vertx vertx) {

        httpClient = new CrudHttpClient(
                vertx,
                IntegrationTestSupport.HTTP_HOST,
                IntegrationTestSupport.HTTP_PORT);
    }

    private MultiMap getRequestHeaders() {
        final var requestHeaders = MultiMap.caseInsensitiveMultiMap();
        Optional.ofNullable(authenticationHeaderValue)
            .ifPresent(v -> requestHeaders.add(HttpHeaders.AUTHORIZATION, v));
        return requestHeaders;
    }

    /**
     * Verifies that the HTTP adapter returns matching CORS headers in response to a
     * CORS preflight request for posting telemetry data.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testCorsPreflightRequestForPostingTelemetry(final VertxTestContext ctx) {

        httpClient.options(
                "/" + TelemetryConstants.TELEMETRY_ENDPOINT,
                getRequestHeaders()
                    .add(HttpHeaders.ORIGIN, CrudHttpClient.ORIGIN_URI)
                    .add(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.POST.name()),
                ResponsePredicate.status(HttpURLConnection.HTTP_NO_CONTENT))
        .onComplete(ctx.succeeding(response -> {
            ctx.verify(() -> {
                assertAccessControlHeaders(
                        response.headers(),
                        HttpMethod.POST,
                        Constants.HEADER_QOS_LEVEL,
                        Constants.HEADER_TIME_TILL_DISCONNECT);
            });
            ctx.completeNow();
        }));
    }

    /**
     * Verifies that the HTTP adapter returns matching CORS headers in response to a
     * CORS preflight request for putting telemetry data.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testCorsPreflightRequestForPuttingTelemetry(final VertxTestContext ctx) {

        httpClient.options(
                String.format("/%s/%s/%s", TelemetryConstants.TELEMETRY_ENDPOINT, "my-tenant", "my-device"),
                getRequestHeaders()
                    .add(HttpHeaders.ORIGIN, CrudHttpClient.ORIGIN_URI)
                    .add(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.PUT.name()),
                    ResponsePredicate.status(HttpURLConnection.HTTP_NO_CONTENT))
        .onComplete(ctx.succeeding(response -> {
            ctx.verify(() -> {
                assertAccessControlHeaders(
                        response.headers(),
                        HttpMethod.PUT,
                        Constants.HEADER_QOS_LEVEL,
                        Constants.HEADER_TIME_TILL_DISCONNECT);
            });
            ctx.completeNow();
        }));
    }

    /**
     * Verifies that the HTTP adapter returns matching CORS headers in response to a
     * CORS preflight request for posting an event.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testCorsPreflightRequestForPostingEvents(final VertxTestContext ctx) {

        httpClient.options(
                "/" + EventConstants.EVENT_ENDPOINT,
                getRequestHeaders()
                    .add(HttpHeaders.ORIGIN, CrudHttpClient.ORIGIN_URI)
                    .add(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.POST.name()),
                    ResponsePredicate.status(HttpURLConnection.HTTP_NO_CONTENT))
        .onComplete(ctx.succeeding(response -> {
            ctx.verify(() -> {
                assertAccessControlHeaders(response.headers(), HttpMethod.POST, Constants.HEADER_TIME_TILL_DISCONNECT);
            });
            ctx.completeNow();
        }));
    }

    /**
     * Verifies that the HTTP adapter returns matching CORS headers in response to a
     * CORS preflight request for putting an event.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testCorsPreflightRequestForPuttingEvents(final VertxTestContext ctx) {

        httpClient.options(
                String.format("/%s/%s/%s", EventConstants.EVENT_ENDPOINT, "my-tenant", "my-device"),
                getRequestHeaders()
                    .add(HttpHeaders.ORIGIN, CrudHttpClient.ORIGIN_URI)
                    .add(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.PUT.name()),
                    ResponsePredicate.status(HttpURLConnection.HTTP_NO_CONTENT))
        .onComplete(ctx.succeeding(response -> {
            ctx.verify(() -> {
                assertAccessControlHeaders(response.headers(), HttpMethod.PUT, Constants.HEADER_TIME_TILL_DISCONNECT);
            });
            ctx.completeNow();
        }));
    }

    /**
     * Verifies that the HTTP adapter returns matching CORS headers in response to a
     * CORS preflight request for posting a command response.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testCorsPreflightRequestForPostingCommandResponse(final VertxTestContext ctx) {

        httpClient.options(
                String.format("/%s/res/%s", CommandConstants.COMMAND_ENDPOINT, "cmd-request-id"),
                getRequestHeaders()
                    .add(HttpHeaders.ORIGIN, CrudHttpClient.ORIGIN_URI)
                    .add(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.POST.name()),
                    ResponsePredicate.status(HttpURLConnection.HTTP_NO_CONTENT))
        .onComplete(ctx.succeeding(response -> {
            ctx.verify(() -> {
                assertAccessControlHeaders(response.headers(), HttpMethod.POST, Constants.HEADER_COMMAND_RESPONSE_STATUS);
            });
            ctx.completeNow();
        }));
    }

    /**
     * Verifies that the HTTP adapter returns matching CORS headers in response to a
     * CORS preflight request for putting a command response.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testCorsPreflightRequestForPuttingCommandResponse(final VertxTestContext ctx) {

        httpClient.options(
                String.format("/%s/res/%s/%s/%s", CommandConstants.COMMAND_ENDPOINT, "my-tenant", "my-device", "cmd-request-id"),
                getRequestHeaders()
                    .add(HttpHeaders.ORIGIN, CrudHttpClient.ORIGIN_URI)
                    .add(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.PUT.name()),
                ResponsePredicate.status(HttpURLConnection.HTTP_NO_CONTENT))
        .onComplete(ctx.succeeding(response -> {
            ctx.verify(() -> {
                assertAccessControlHeaders(response.headers(), HttpMethod.PUT, Constants.HEADER_COMMAND_RESPONSE_STATUS);
            });
            ctx.completeNow();
        }));
    }

    private static void assertAccessControlHeaders(
            final MultiMap headers,
            final HttpMethod expectedAllowedMethod,
            final String ... expectedAllowedHeaders) {

        assertThat(headers.get(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS)).contains(expectedAllowedMethod.name());
        assertThat(headers.get(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)).isEqualTo("*");
        assertThat(headers.get(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS)).contains(HttpHeaders.AUTHORIZATION);
        assertThat(headers.get(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS)).contains(HttpHeaders.CONTENT_TYPE);
        Optional.ofNullable(expectedAllowedHeaders).ifPresent(headerNames -> {
            for (final String name : headerNames) {
                assertThat(headers.get(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS)).contains(name);
            }
        });
    }
}
