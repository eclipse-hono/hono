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

package org.eclipse.hono.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.eclipse.hono.util.Constants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.web.RoutingContext;

/**
 * Test verifying functionality of the {@link HttpContext} class.
 */
public class HttpContextTest {

    private RoutingContext routingContext;
    private HttpServerRequest httpServerRequest;

    /**
     * Sets up the fixture.
     */
    @BeforeEach
    public void setUp() {
        httpServerRequest = mock(HttpServerRequest.class);
        when(httpServerRequest.path()).thenReturn("/telemetry");
        routingContext = mock(RoutingContext.class);
        when(routingContext.request()).thenReturn(httpServerRequest);

    }

    /**
     * Verifies that encoded characters in the request path get correctly decoded
     * when initializing the HttpContext from the request.
     */
    @Test
    public void testRequestPathElementsGetDecoded() {
        httpServerRequest = mock(HttpServerRequest.class);
        // GIVEN a request URI with encoded characters
        final String tenantId = "test:myTenant";
        final String deviceId = "test:myDevice";
        when(httpServerRequest.path()).thenReturn("/telemetry/%s/%s".formatted(
                URLEncoder.encode(tenantId, StandardCharsets.UTF_8),
                URLEncoder.encode(deviceId, StandardCharsets.UTF_8)));
        routingContext = mock(RoutingContext.class);
        when(routingContext.request()).thenReturn(httpServerRequest);

        // WHEN creating the HTTP context
        final HttpContext httpContext = HttpContext.from(routingContext);
        // THEN the HTTP context request resource contains elements with the decoded characters
        assertEquals(tenantId, httpContext.getRequestedResource().getTenantId());
        assertEquals(deviceId, httpContext.getRequestedResource().getResourceId());
    }

    /**
     * Verifies that the {@link Constants#HEADER_TIME_TILL_DISCONNECT} header is used by
     * the {@link HttpContext#getTimeTillDisconnect()} method.
     */
    @Test
    public void testGetTimeTillDisconnectUsesHeader() {
        final HttpContext httpContext = HttpContext.from(routingContext);

        // GIVEN a time till disconnect
        final Integer timeTillDisconnect = 60;
        // WHEN evaluating a routingContext that has this value set as header
        when(httpServerRequest.getHeader(Constants.HEADER_TIME_TILL_DISCONNECT))
                .thenReturn(timeTillDisconnect.toString());

        // THEN the get method returns this value
        assertEquals(timeTillDisconnect, httpContext.getTimeTillDisconnect());
    }

    /**
     * Verifies that the {@link Constants#HEADER_TIME_TILL_DISCONNECT} query parameter is used by
     * the {@link HttpContext#getTimeTillDisconnect()} method.
     */
    @Test
    public void testGetTimeTillDisconnectUsesQueryParam() {
        final HttpContext httpContext = HttpContext.from(routingContext);

        // GIVEN a time till disconnect
        final Integer timeTillDisconnect = 60;
        // WHEN evaluating a routingContext that has this value set as query param
        when(httpServerRequest.getParam(Constants.HEADER_TIME_TILL_DISCONNECT))
                .thenReturn(timeTillDisconnect.toString());

        // THEN the get method returns this value
        assertEquals(timeTillDisconnect, httpContext.getTimeTillDisconnect());
    }

    /**
     * Verifies that
     * the {@link HttpContext#getTimeTillDisconnect()} method returns {@code null} if
     * {@link Constants#HEADER_TIME_TILL_DISCONNECT} is neither provided as query parameter nor as requests header.
     */
    @Test
    public void testGetTimeTillDisconnectReturnsNullIfNotSpecified() {
        final HttpContext httpContext = HttpContext.from(routingContext);
        assertNull(httpContext.getTimeTillDisconnect());
    }

    /**
     * Verifies that the {@link Constants#HEADER_TIME_TO_LIVE} header is used by
     * the {@link HttpContext#getTimeToLive()} method.
     */
    @Test
    public void testGetTimeToLiveUsesHeader() {

        when(httpServerRequest.path()).thenReturn("/event");
        final HttpContext httpContext = HttpContext.from(routingContext);

        // GIVEN a time to live in seconds
        final long timeToLive = 60L;
        // WHEN evaluating a routingContext that has this value set as a header
        when(httpServerRequest.getHeader(Constants.HEADER_TIME_TO_LIVE))
                .thenReturn(String.valueOf(timeToLive));

        // THEN the get method returns this value
        assertEquals(timeToLive, httpContext.getTimeToLive().get().toSeconds());
    }

    /**
     * Verifies that the {@link Constants#HEADER_TIME_TO_LIVE} query parameter is used by
     * the {@link HttpContext#getTimeToLive()} method.
     */
    @Test
    public void testGetTimeToLiveUsesQueryParam() {

        when(httpServerRequest.path()).thenReturn("/event");
        final HttpContext httpContext = HttpContext.from(routingContext);

        // GIVEN a time to live in seconds
        final long timeToLive = 60L;
        // WHEN evaluating a routingContext that has this value set as a query param
        when(httpServerRequest.getParam(Constants.HEADER_TIME_TO_LIVE))
                .thenReturn(String.valueOf(timeToLive));

        // THEN the get method returns this value
        assertEquals(timeToLive, httpContext.getTimeToLive().get().toSeconds());
    }

    /**
     * Verifies that the {@link HttpContext#getTimeToLive()} method
     * returns an empty Optional if {@link Constants#HEADER_TIME_TO_LIVE} is neither
     * provided as query parameter nor as header.
     */
    @Test
    public void testGetTimeToLiveReturnsEmptyOptionalIfNotSpecified() {
        final HttpContext httpContext = HttpContext.from(routingContext);
        assertTrue(httpContext.getTimeToLive().isEmpty());
    }
}
