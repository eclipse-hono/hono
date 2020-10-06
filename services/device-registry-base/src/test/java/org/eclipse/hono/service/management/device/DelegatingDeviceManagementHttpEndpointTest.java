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
import java.util.Optional;

import org.eclipse.hono.config.ServiceConfigProperties;
import org.eclipse.hono.service.http.DefaultFailureHandler;
import org.eclipse.hono.service.management.Id;
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
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
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
    private MultiMap requestParams;
    private MultiMap requestHeaders;
    private Buffer requestBody;

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
        // allow upload of data in a request
        router.route().handler(ctx -> {
            if (requestBody != null) {
                ctx.setBody(requestBody);
            }
            ctx.next();
        });
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
        requestParams = MultiMap.caseInsensitiveMultiMap();
        requestHeaders = MultiMap.caseInsensitiveMultiMap();
    }

    /**
     * Verifies that the endpoint uses the device ID provided in a request's URI
     * for creating a device.
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testCreateDeviceUsesIdFromUriParam() {

        final JsonObject json = new JsonObject()
                .put(RegistryManagementConstants.FIELD_MAPPER, "my-mapper")
                .put(RegistryManagementConstants.FIELD_EXT, new JsonObject().put("custom", "value"));
        requestBody = json.toBuffer();

        when(service.createDevice(anyString(), any(Optional.class), any(Device.class), any(Span.class)))
            .thenAnswer(invocation -> {
                final Optional<String> deviceId = invocation.getArgument(1);
                return Future.succeededFuture(OperationResult.ok(
                        HttpURLConnection.HTTP_CREATED,
                        Id.of(deviceId.get()),
                        Optional.empty(),
                        Optional.empty()));
            });

        final HttpServerResponse response = newResponse();

        final HttpServerRequest request = newRequest(
                HttpMethod.POST,
                "/v1/devices/mytenant/mydeviceid",
                requestHeaders,
                requestParams,
                response);
        when(request.getHeader("Content-Type")).thenReturn("application/json");

        router.handle(request);

        verify(response).setStatusCode(HttpURLConnection.HTTP_CREATED);
        verify(response).write(argThat((Buffer buffer) -> "mydeviceid"
                .equals(buffer.toJsonObject().getString(RegistryManagementConstants.FIELD_ID))));
        verify(service).createDevice(
                eq("mytenant"),
                argThat(deviceId -> "mydeviceid".equals(deviceId.get())),
                argThat(device -> {
                    return "my-mapper".equals(device.getMapper()) &&
                            "value".equals(device.getExtensions().get("custom"));
                }),
                any(Span.class));
    }

    /**
     * Verifies that the endpoint returns a 400 status code if the request's URI contains
     * a device ID that does not match the configured device ID pattern.
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testCreateDeviceRejectsInvalidDeviceId() {

        final HttpServerResponse response = newResponse();

        final HttpServerRequest request = newRequest(
                HttpMethod.POST,
                "/v1/devices/mytenant/%265woo_%24",
                requestHeaders,
                requestParams,
                response);

        router.handle(request);

        verify(response).setStatusCode(HttpURLConnection.HTTP_BAD_REQUEST);
        verify(service, never()).createDevice(anyString(), any(Optional.class), any(Device.class), any(Span.class));
    }

    /**
     * Verifies that the endpoint returns a 400 status code if the request body contains
     * a JSON object that does not comply with the Device object specification.
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testCreateDeviceRejectsInvalidPayload() {

        final JsonObject json = new JsonObject().put("manufacturer", "ACME");
        requestBody = json.toBuffer();
        final HttpServerResponse response = newResponse();

        final HttpServerRequest request = newRequest(
                HttpMethod.POST,
                "/v1/devices/mytenant/newdevice",
                requestHeaders,
                requestParams,
                response);
        when(request.getHeader("Content-Type")).thenReturn("application/json");

        router.handle(request);

        verify(response).setStatusCode(HttpURLConnection.HTTP_BAD_REQUEST);
        verify(service, never()).createDevice(anyString(), any(Optional.class), any(Device.class), any(Span.class));
    }

    /**
     * Verifies that the endpoint uses default values if the request does
     * not contain any search criteria.
     */
    @Test
    public void testSearchDevicesUsesDefaultSearchCriteria() {

        final HttpServerResponse response = newResponse();

        final HttpServerRequest request = newRequest(
                HttpMethod.GET,
                "/v1/devices/mytenant",
                requestHeaders,
                requestParams,
                response);

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

        requestParams.add(RegistryManagementConstants.PARAM_PAGE_SIZE, "10");
        requestParams.add(RegistryManagementConstants.PARAM_PAGE_OFFSET, "50");
        requestParams.add(RegistryManagementConstants.PARAM_FILTER_JSON,
                "{\"field\":\"/manufacturer\",\"value\":\"ACME*\"}");
        requestParams.add(RegistryManagementConstants.PARAM_SORT_JSON,
                "{\"field\":\"/manufacturer\",\"direction\":\"desc\"}");

        final HttpServerRequest request = newRequest(
                HttpMethod.GET,
                "/v1/devices/mytenant",
                requestHeaders,
                requestParams,
                response);

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

        requestParams.add(RegistryManagementConstants.PARAM_PAGE_SIZE, "not-a-number");
        testSearchDevicesFailsForMalformedSearchCriteria(requestParams);
    }

    /**
     * Verifies that the endpoint returns a 400 status code if the request contains
     * a page offset query parameter that cannot be parsed into an integer.
     */
    @Test
    public void testSearchDevicesFailsForMalformedPageOffsetParam() {

        requestParams.add(RegistryManagementConstants.PARAM_PAGE_OFFSET, "not-a-number");
        testSearchDevicesFailsForMalformedSearchCriteria(requestParams);
    }

    /**
     * Verifies that the endpoint returns a 400 status code if the request contains
     * a filter JSON query parameter that cannot be parsed into a JSON object.
     */
    @Test
    public void testSearchDevicesFailsForMalformedFilterJsonParam() {

        requestParams.add(RegistryManagementConstants.PARAM_FILTER_JSON, "not-JSON");
        testSearchDevicesFailsForMalformedSearchCriteria(requestParams);
    }

    /**
     * Verifies that the endpoint returns a 400 status code if the request contains
     * a sort JSON query parameter that cannot be parsed into a JSON object.
     */
    @Test
    public void testSearchDevicesFailsForMalformedSortJsonParam() {

        requestParams.add(RegistryManagementConstants.PARAM_SORT_JSON, "not-JSON");
        testSearchDevicesFailsForMalformedSearchCriteria(requestParams);
    }

    @SuppressWarnings("unchecked")
    private void testSearchDevicesFailsForMalformedSearchCriteria(final MultiMap params) {

        final HttpServerResponse response = newResponse();

        final HttpServerRequest request = newRequest(
                HttpMethod.GET,
                "/v1/devices/mytenant",
                requestHeaders,
                params,
                response);

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
            final MultiMap requestHeaders,
            final MultiMap requestParams,
            final HttpServerResponse response) {

        final HttpServerRequest request = mock(HttpServerRequest.class);
        when(request.method()).thenReturn(method);
        when(request.path()).thenReturn(path);
        when(request.headers()).thenReturn(requestHeaders);
        when(request.params()).thenReturn(requestParams);
        when(request.response()).thenReturn(response);
        return request;
    }

    private static HttpServerResponse newResponse() {
        final HttpServerResponse response = mock(HttpServerResponse.class);
        when(response.ended()).thenReturn(false);
        when(response.putHeader(any(CharSequence.class), any(CharSequence.class))).thenReturn(response);
        final MultiMap headers = MultiMap.caseInsensitiveMultiMap();
        when(response.headers()).thenReturn(headers);
        return response;
    }
}
