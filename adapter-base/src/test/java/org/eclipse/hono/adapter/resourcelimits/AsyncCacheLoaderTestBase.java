/**
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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


package org.eclipse.hono.adapter.resourcelimits;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.Executor;
import java.util.concurrent.TimeoutException;

import org.eclipse.hono.test.TracingMockSupport;
import org.eclipse.hono.test.VertxMockSupport;
import org.eclipse.hono.util.Constants;
import org.junit.jupiter.api.BeforeEach;

import io.opentracing.Span;
import io.opentracing.Tracer;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.predicate.ResponsePredicate;
import io.vertx.ext.web.codec.BodyCodec;

/**
 * A base class for implementing unit tests for cache loaders.
 * <p>
 * Provides a set of pre-configured mock objects that are useful for implementing tests.
 */
abstract class AsyncCacheLoaderTestBase {

    protected static final String TENANT_ID = Constants.DEFAULT_TENANT;
    protected static final int QUERY_TIMEOUT = 500;
    protected static final int REQUEST_TIMEOUT = QUERY_TIMEOUT + 100;

    protected WebClient webClient;
    protected HttpRequest<JsonObject> jsonRequest;
    protected HttpRequest<Buffer> bufferReq;
    protected Span span;
    protected Tracer tracer;
    protected PrometheusBasedResourceLimitChecksConfig config;
    protected Executor executor;

    /**
     * Sets up the mocks.
     */
    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUpMocks() {
        jsonRequest = mock(HttpRequest.class);

        bufferReq = mock(HttpRequest.class);
        when(bufferReq.addQueryParam(anyString(), anyString())).thenReturn(bufferReq);
        when(bufferReq.expect(any(ResponsePredicate.class))).thenReturn(bufferReq);
        when(bufferReq.basicAuthentication(anyString(), anyString())).thenReturn(bufferReq);
        when(bufferReq.timeout(anyLong())).thenReturn(bufferReq);
        when(bufferReq.as(any(BodyCodec.class))).thenReturn(jsonRequest);

        webClient = mock(WebClient.class);
        when(webClient.post(anyString())).thenReturn(bufferReq);

        span = TracingMockSupport.mockSpan();
        tracer = TracingMockSupport.mockTracer(span);

        config = new PrometheusBasedResourceLimitChecksConfig();
        config.setQueryTimeout(QUERY_TIMEOUT);

        executor = mock(Executor.class);
    }

    protected void givenCurrentConnections(final Integer currentConnections) {
        givenResponseWithValue(currentConnections);
    }

    protected void givenDataVolumeUsageInBytes(final Integer consumedBytes) {
        givenResponseWithValue(consumedBytes);
    }

    protected void givenDeviceConnectionDurationInMinutes(final Integer consumedMinutes) {
        givenResponseWithValue(consumedMinutes);
    }

    @SuppressWarnings("unchecked")
    protected void givenResponseWithValue(final Integer value) {
        doAnswer(invocation -> {
            final Handler<AsyncResult<HttpResponse<JsonObject>>> responseHandler = invocation.getArgument(0);
            final HttpResponse<JsonObject> response = mock(HttpResponse.class);
            when(response.body()).thenReturn(createPrometheusResponse(value));
            responseHandler.handle(Future.succeededFuture(response));
            return null;
        }).when(jsonRequest).send(VertxMockSupport.anyHandler());
    }

    protected void givenFailResponseWithTimeoutException() {
        doAnswer(invocation -> {
            final Handler<AsyncResult<HttpResponse<JsonObject>>> responseHandler = invocation.getArgument(0);
            responseHandler.handle(Future.failedFuture(new TimeoutException()));
            return null;
        }).when(jsonRequest).send(VertxMockSupport.anyHandler());
    }

    protected static JsonObject createPrometheusResponse(final Integer value) {
        final JsonArray valueArray = new JsonArray();
        if (value != null) {
            valueArray.add("timestamp").add(String.valueOf(value));
        }
        return new JsonObject()
                .put("status", "success")
                .put("data", new JsonObject()
                        .put("result", new JsonArray().add(new JsonObject()
                                .put("value", valueArray))));
    }

    protected static void assertRequestParamsSet(
            final HttpRequest<?> request,
            final String expectedQuery,
            final int expectedQueryTimeoutMillis,
            final long expectedRequestTimeoutMillis) {
        verify(request).addQueryParam(eq("query"), eq(expectedQuery));
        verify(request).addQueryParam(eq("timeout"), eq(String.valueOf(expectedQueryTimeoutMillis) + "ms"));
        verify(request).timeout(expectedRequestTimeoutMillis);
    }

}
