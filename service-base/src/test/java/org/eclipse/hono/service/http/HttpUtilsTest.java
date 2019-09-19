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

package org.eclipse.hono.service.http;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.when;

import org.eclipse.hono.util.Constants;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.web.RoutingContext;

/**
 * Test verifying functionality of the {@link HttpUtils} class.
 */
@RunWith(MockitoJUnitRunner.class)
public class HttpUtilsTest {

    @Mock
    private RoutingContext routingContext;
    @Mock
    private HttpServerRequest httpServerRequest;

    /**
     * Sets up the fixture.
     */
    @Before
    public void setUp() {

        when(routingContext.request()).thenReturn(httpServerRequest);

    }

    /**
     * Verifies that the {@link Constants#HEADER_TIME_TILL_DISCONNECT} header is used by
     * the {@link HttpUtils#getTimeTillDisconnect(RoutingContext)} method.
     */
    @Test
    public void testGetTimeTillDisconnectUsesHeader() {

        // GIVEN a time till disconnect
        final Integer timeTillDisconnect = 60;
        // WHEN evaluating a routingContext that has this value set as header
        when(httpServerRequest.getHeader(Constants.HEADER_TIME_TILL_DISCONNECT))
                .thenReturn(timeTillDisconnect.toString());

        // THEN the get method returns this value
        assertEquals(timeTillDisconnect, HttpUtils.getTimeTillDisconnect(routingContext));
    }

    /**
     * Verifies that the {@link Constants#HEADER_TIME_TILL_DISCONNECT} query parameter is used by
     * the {@link HttpUtils#getTimeTillDisconnect(RoutingContext)} method.
     */
    @Test
    public void testGetTimeTillDisconnectUsesQueryParam() {

        // GIVEN a time till disconnect
        final Integer timeTillDisconnect = 60;
        // WHEN evaluating a routingContext that has this value set as query param
        when(httpServerRequest.getParam(Constants.HEADER_TIME_TILL_DISCONNECT))
                .thenReturn(timeTillDisconnect.toString());

        // THEN the get method returns this value
        assertEquals(timeTillDisconnect, HttpUtils.getTimeTillDisconnect(routingContext));
    }

    /**
     * Verifies that
     * the {@link HttpUtils#getTimeTillDisconnect(RoutingContext)} method returns {@code null} if
     * {@link Constants#HEADER_TIME_TILL_DISCONNECT} is neither provided as query parameter nor as requests header.
     */
    @Test
    public void testGetTimeTillDisconnectReturnsNullIfNotSpecified() {

        assertNull(HttpUtils.getTimeTillDisconnect(routingContext));
    }

    /**
     * Verifies that the {@link Constants#HEADER_TIME_TO_LIVE} header is used by
     * the {@link HttpUtils#getTimeToLive(RoutingContext)} method.
     */
    @Test
    public void testGetTimeToLiveUsesHeader() {

        // GIVEN a time to live in seconds
        final long timeToLive = 60L;
        // WHEN evaluating a routingContext that has this value set as a header
        when(httpServerRequest.getHeader(Constants.HEADER_TIME_TO_LIVE))
                .thenReturn(String.valueOf(timeToLive));

        // THEN the get method returns this value
        assertEquals(timeToLive, HttpUtils.getTimeToLive(routingContext).toSeconds());
    }

    /**
     * Verifies that the {@link Constants#HEADER_TIME_TO_LIVE} query parameter is used by
     * the {@link HttpUtils#getTimeToLive(RoutingContext)} method.
     */
    @Test
    public void testGetTimeToLiveUsesQueryParam() {

        // GIVEN a time to live in seconds
        final long timeToLive = 60L;
        // WHEN evaluating a routingContext that has this value set as a query param
        when(httpServerRequest.getParam(Constants.HEADER_TIME_TO_LIVE))
                .thenReturn(String.valueOf(timeToLive));

        // THEN the get method returns this value
        assertEquals(timeToLive, HttpUtils.getTimeToLive(routingContext).toSeconds());
    }

    /**
     * Verifies that the {@link HttpUtils#getTimeToLive(RoutingContext)} method 
     * returns {@code null} if {@link Constants#HEADER_TIME_TO_LIVE} is neither
     * provided as query parameter nor as header.
     */
    @Test
    public void testGetTimeToLiveReturnsNullIfNotSpecified() {

        assertNull(HttpUtils.getTimeToLive(routingContext));
    }
}
