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

package org.eclipse.hono.service.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.eclipse.hono.util.Constants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.web.RoutingContext;

/**
 * Test verifying functionality of the {@link HttpContext} class.
 */
public class HttpContextTest {

    @Mock
    private RoutingContext routingContext;
    @Mock
    private HttpServerRequest httpServerRequest;

    /**
     * Sets up the fixture.
     */
    @BeforeEach
    public void setUp() {

        routingContext = mock(RoutingContext.class);
        httpServerRequest = mock(HttpServerRequest.class);
        when(routingContext.request()).thenReturn(httpServerRequest);

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

        when(httpServerRequest.uri()).thenReturn("/event");
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

        when(httpServerRequest.uri()).thenReturn("/event");
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
