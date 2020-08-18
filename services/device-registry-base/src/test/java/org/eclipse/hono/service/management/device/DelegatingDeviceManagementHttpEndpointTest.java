/**
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */


package org.eclipse.hono.service.management.device;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.HttpURLConnection;
import java.util.List;

import org.eclipse.hono.config.ServiceConfigProperties;
import org.eclipse.hono.service.http.DefaultFailureHandler;
import org.eclipse.hono.service.management.OperationResult;
import org.eclipse.hono.service.management.device.Filter.Operator;
import org.eclipse.hono.service.management.device.Sort.Direction;
import org.eclipse.hono.util.RegistryManagementConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.opentracing.Span;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.Router;


/**
 * Tests verifying behavior of {@link DelegatingDeviceManagementHttpEndpoint}.
 *
 */
public class DelegatingDeviceManagementHttpEndpointTest {

    private DeviceManagementService service;
    private DelegatingDeviceManagementHttpEndpoint<DeviceManagementService> endpoint;
    private Vertx vertx;
    private Router router;

    /**
     * Sets up the fixture.
     */
    @SuppressWarnings("unchecked")
    @BeforeEach
    public void setUp() {

        vertx = mock(Vertx.class);
        router = Router.router(vertx);
        // make sure that ServiceInvocationExceptions are properly handled
        // and result in the exception's error code being set on the response
        router.route().failureHandler(new DefaultFailureHandler());
        service = mock(DeviceManagementService.class);
        when(service.searchDevices(
                anyString(),
                anyInt(),
                anyInt(),
                any(List.class),
                any(List.class),
                any(Span.class)))
            .thenReturn(Future.succeededFuture(OperationResult.empty(HttpURLConnection.HTTP_OK)));
        endpoint = new DelegatingDeviceManagementHttpEndpoint<DeviceManagementService>(vertx, service);
        endpoint.setConfiguration(new ServiceConfigProperties());
        endpoint.addRoutes(router);
    }

    /**
     * Verifies that the endpoint uses default values if the request does
     * not contain any search criteria.
     */
    @Test
    public void testSearchDevicesUsesDefaultSearchCriteria() {

        final HttpServerResponse response = newResponse();

        final MultiMap params = MultiMap.caseInsensitiveMultiMap();
        params.add("tenant_id", "mytenant");

        final HttpServerRequest request = newRequest(HttpMethod.GET, "/v1/devices/mytenant", response);
        when(request.params()).thenReturn(params);
        router.handle(request);

        verify(response).setStatusCode(HttpURLConnection.HTTP_OK);
        verify(service).searchDevices(
                eq("mytenant"),
                eq(DelegatingDeviceManagementHttpEndpoint.DEFAULT_PAGE_SIZE),
                eq(DelegatingDeviceManagementHttpEndpoint.DEFAULT_PAGE_OFFSET),
                argThat(List::isEmpty),
                argThat(List::isEmpty),
                any(Span.class));
    }

    /**
     * Verifies that the endpoint uses search criteria provided in a request's query parameters.
     */
    @Test
    public void testSearchDevicesSucceedsWithSearchCriteria() {

        final HttpServerResponse response = newResponse();

        final MultiMap params = MultiMap.caseInsensitiveMultiMap();
        params.add("tenant_id", "mytenant");
        params.add(RegistryManagementConstants.PARAM_PAGE_SIZE, "10");
        params.add(RegistryManagementConstants.PARAM_PAGE_OFFSET, "50");
        params.add(RegistryManagementConstants.PARAM_FILTER_JSON,
                "{\"field\":\"/manufacturer\",\"value\":\"ACME*\"}");
        params.add(RegistryManagementConstants.PARAM_SORT_JSON,
                "{\"field\":\"/manufacturer\",\"direction\":\"desc\"}");

        final HttpServerRequest request = newRequest(HttpMethod.GET, "/v1/devices/mytenant", response);
        when(request.params()).thenReturn(params);
        router.handle(request);

        verify(response).setStatusCode(HttpURLConnection.HTTP_OK);
        verify(service).searchDevices(
                eq("mytenant"),
                eq(10),
                eq(50),
                argThat(filters -> {
                    if (filters.isEmpty()) {
                        return false;
                    } else {
                        final Filter filter = filters.get(0);
                        return "/manufacturer".equals(filter.getField().toString()) &&
                                "ACME*".equals(filter.getValue()) &&
                                Operator.eq == filter.getOperator();
                    }
                }),
                argThat(sortOptions -> {
                    if (sortOptions.isEmpty()) {
                        return false;
                    } else {
                        final Sort sortOption = sortOptions.get(0);
                        return "/manufacturer".equals(sortOption.getField().toString()) &&
                                Direction.desc == sortOption.getDirection();
                    }
                }),
                any(Span.class));
    }

    /**
     * Verifies that the endpoint returns a 400 status code if the request contains
     * a page size query parameter that cannot be parsed into an integer.
     */
    @Test
    public void testSearchDevicesFailsForMalformedPageSizeParam() {

        final MultiMap params = MultiMap.caseInsensitiveMultiMap();
        params.add("tenant_id", "mytenant");
        params.add(RegistryManagementConstants.PARAM_PAGE_SIZE, "not-a-number");
        testSearchDevicesFailsForMalformedSearchCriteria(params);
    }

    /**
     * Verifies that the endpoint returns a 400 status code if the request contains
     * a page offset query parameter that cannot be parsed into an integer.
     */
    @Test
    public void testSearchDevicesFailsForMalformedPageOffsetParam() {

        final MultiMap params = MultiMap.caseInsensitiveMultiMap();
        params.add("tenant_id", "mytenant");
        params.add(RegistryManagementConstants.PARAM_PAGE_OFFSET, "not-a-number");
        testSearchDevicesFailsForMalformedSearchCriteria(params);
    }

    /**
     * Verifies that the endpoint returns a 400 status code if the request contains
     * a filter JSON query parameter that cannot be parsed into a JSON object.
     */
    @Test
    public void testSearchDevicesFailsForMalformedFilterJsonParam() {

        final MultiMap params = MultiMap.caseInsensitiveMultiMap();
        params.add("tenant_id", "mytenant");
        params.add(RegistryManagementConstants.PARAM_FILTER_JSON, "not-JSON");
        testSearchDevicesFailsForMalformedSearchCriteria(params);
    }

    /**
     * Verifies that the endpoint returns a 400 status code if the request contains
     * a sort JSON query parameter that cannot be parsed into a JSON object.
     */
    @Test
    public void testSearchDevicesFailsForMalformedSortJsonParam() {

        final MultiMap params = MultiMap.caseInsensitiveMultiMap();
        params.add("tenant_id", "mytenant");
        params.add(RegistryManagementConstants.PARAM_SORT_JSON, "not-JSON");
        testSearchDevicesFailsForMalformedSearchCriteria(params);
    }

    @SuppressWarnings("unchecked")
    private void testSearchDevicesFailsForMalformedSearchCriteria(final MultiMap params) {

        final HttpServerResponse response = newResponse();

        final HttpServerRequest request = newRequest(HttpMethod.GET, "/v1/devices/mytenant", response);
        when(request.params()).thenReturn(params);

        router.handle(request);

        verify(response).setStatusCode(HttpURLConnection.HTTP_BAD_REQUEST);
        verify(service, never()).searchDevices(
                anyString(),
                anyInt(),
                anyInt(),
                any(List.class),
                any(List.class),
                any(Span.class));
    }

    private static HttpServerRequest newRequest(
            final HttpMethod method,
            final String path,
            final HttpServerResponse response) {
        return newRequest(method, path, MultiMap.caseInsensitiveMultiMap(), response);
    }

    private static HttpServerRequest newRequest(
            final HttpMethod method,
            final String path,
            final MultiMap headers,
            final HttpServerResponse response) {

        final HttpServerRequest request = mock(HttpServerRequest.class);
        when(request.method()).thenReturn(method);
        when(request.path()).thenReturn(path);
        when(request.headers()).thenReturn(headers);
        when(request.response()).thenReturn(response);
        return request;
    }

    private static HttpServerResponse newResponse() {
        final HttpServerResponse response = mock(HttpServerResponse.class);
        when(response.ended()).thenReturn(false);
        when(response.putHeader(any(CharSequence.class), any(CharSequence.class))).thenReturn(response);
        return response;
    }
}
