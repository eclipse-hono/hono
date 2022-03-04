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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static com.google.common.truth.Truth.assertThat;

import java.net.HttpURLConnection;

import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.util.RequestResponseApiConstants;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;


/**
 * Verifies behavior of {@link DefaultFailureHandler}.
 *
 */
public class DefaultFailureHandlerTest {

    private void assertErrorResponse(final Buffer responseBody) {
        assertErrorResponse(responseBody, null);
    }

    private void assertErrorResponse(final Buffer responseBody, final String expectedDetailMsg) {
        final JsonObject json = responseBody.toJsonObject();
        final String detailMsg = json.getString(RequestResponseApiConstants.FIELD_ERROR);
        if (expectedDetailMsg == null) {
            assertThat(detailMsg).isNotNull();
        } else {
            assertThat(detailMsg).isEqualTo(expectedDetailMsg);
        }
    }

    /**
     * Verifies that the handler does not try to process a failed
     * context if the response is already ended.
     */
    @Test
    public void testHandlerDetectsEndedResponse() {
        final HttpServerResponse response = mock(HttpServerResponse.class);
        when(response.ended()).thenReturn(true);
        final RoutingContext ctx = mock(RoutingContext.class);
        when(ctx.response()).thenReturn(response);
        when(ctx.failed()).thenReturn(true);

        final DefaultFailureHandler handler = new DefaultFailureHandler();
        handler.handle(ctx);

        verify(response, never()).setStatusCode(anyInt());
        verify(response, never()).write(any(Buffer.class));
        verify(response, never()).end();
    }

    /**
     * Verifies that the handler sets a <em>N/A</em> error in the response body for
     * a context that has failed with an exception that does not contain
     * a detail message.
     */
    @Test
    public void testHandlerSucceedsForExceptionsWithoutMessage() {

        final HttpServerRequest request = mock(HttpServerRequest.class, Mockito.RETURNS_MOCKS);
        final HttpServerResponse response = mock(HttpServerResponse.class);
        when(response.ended()).thenReturn(false);
        final RoutingContext ctx = mock(RoutingContext.class);
        when(ctx.request()).thenReturn(request);
        when(ctx.response()).thenReturn(response);
        when(ctx.failed()).thenReturn(true);
        when(ctx.failure()).thenReturn(new IllegalStateException()); // no detail message

        final DefaultFailureHandler handler = new DefaultFailureHandler();
        handler.handle(ctx);

        verify(response).setStatusCode(HttpURLConnection.HTTP_INTERNAL_ERROR);
        final ArgumentCaptor<Buffer> responseBody = ArgumentCaptor.forClass(Buffer.class);
        verify(response).write(responseBody.capture());
        assertErrorResponse(responseBody.getValue());
        verify(response).end();
    }

    /**
     * Verifies that the handler writes the detail message of the failure
     * to the response.
     */
    @Test
    public void testHandlerWritesDetailMessageToBody() {

        final String detailMessage = "detail message";

        final HttpServerRequest request = mock(HttpServerRequest.class, Mockito.RETURNS_MOCKS);
        final HttpServerResponse response = mock(HttpServerResponse.class);
        when(response.ended()).thenReturn(false);
        final RoutingContext ctx = mock(RoutingContext.class);
        when(ctx.request()).thenReturn(request);
        when(ctx.response()).thenReturn(response);
        when(ctx.failed()).thenReturn(true);
        when(ctx.failure()).thenReturn(new IllegalStateException(detailMessage));

        final DefaultFailureHandler handler = new DefaultFailureHandler();
        handler.handle(ctx);

        final ArgumentCaptor<Buffer> bufferCaptor = ArgumentCaptor.forClass(Buffer.class);
        verify(response).setStatusCode(HttpURLConnection.HTTP_INTERNAL_ERROR);
        verify(response).write(bufferCaptor.capture());
        assertErrorResponse(bufferCaptor.getValue(), detailMessage);
        verify(response).end();
    }

    /**
     * Verifies that the handler writes the detail message of the failure
     * to the response and keeps the RoutingContext error status code.
     */
    @Test
    public void testHandlerWritesDetailMessageToBodyAndKeepsErrorStatus() {

        final String detailMessage = "detail message";

        final HttpServerRequest request = mock(HttpServerRequest.class, Mockito.RETURNS_MOCKS);
        final HttpServerResponse response = mock(HttpServerResponse.class);
        when(response.ended()).thenReturn(false);
        final RoutingContext ctx = mock(RoutingContext.class);
        when(ctx.request()).thenReturn(request);
        when(ctx.response()).thenReturn(response);
        when(ctx.failed()).thenReturn(true);
        when(ctx.statusCode()).thenReturn(403);
        when(ctx.failure()).thenReturn(new IllegalStateException(detailMessage));

        final DefaultFailureHandler handler = new DefaultFailureHandler();
        handler.handle(ctx);

        final ArgumentCaptor<Buffer> bufferCaptor = ArgumentCaptor.forClass(Buffer.class);
        verify(response).setStatusCode(HttpURLConnection.HTTP_FORBIDDEN);
        verify(response).write(bufferCaptor.capture());
        assertErrorResponse(bufferCaptor.getValue(), detailMessage);
        verify(response).end();
    }

    /**
     * Verifies that the handler writes the detail message and error code of the failure
     * to the response.
     */
    @Test
    public void testHandlerWritesDetailMessageAndErrorCodeToBody() {

        final String detailMessage = "bad request";

        final HttpServerRequest request = mock(HttpServerRequest.class, Mockito.RETURNS_MOCKS);
        final HttpServerResponse response = mock(HttpServerResponse.class);
        when(response.ended()).thenReturn(false);
        final RoutingContext ctx = mock(RoutingContext.class);
        when(ctx.request()).thenReturn(request);
        when(ctx.response()).thenReturn(response);
        when(ctx.failed()).thenReturn(true);
        when(ctx.failure()).thenReturn(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST, detailMessage));

        final DefaultFailureHandler handler = new DefaultFailureHandler();
        handler.handle(ctx);

        final ArgumentCaptor<Buffer> bufferCaptor = ArgumentCaptor.forClass(Buffer.class);
        verify(response).setStatusCode(HttpURLConnection.HTTP_BAD_REQUEST);
        verify(response).write(bufferCaptor.capture());
        assertErrorResponse(bufferCaptor.getValue(), detailMessage);
        verify(response).end();
    }

    /**
     * Verifies that the handler writes the status code and the status message to the response in case of failure and
     * ctx.failure() returns null.
     */
    @Test
    public void testHandlerWithFailedContextAndEmptyFailure() {
        final RoutingContext ctx = mock(RoutingContext.class);
        final HttpServerRequest request = mock(HttpServerRequest.class, Mockito.RETURNS_MOCKS);
        final HttpServerResponse response = mock(HttpServerResponse.class, Mockito.RETURNS_MOCKS);

        when(response.ended()).thenReturn(false);
        when(ctx.request()).thenReturn(request);
        when(ctx.response()).thenReturn(response);
        when(ctx.failed()).thenReturn(true);
        when(ctx.failure()).thenReturn(null);
        when(ctx.statusCode()).thenReturn(HttpURLConnection.HTTP_UNAUTHORIZED);

        final DefaultFailureHandler handler = new DefaultFailureHandler();
        handler.handle(ctx);

        verify(response).setStatusCode(HttpURLConnection.HTTP_UNAUTHORIZED);
        final ArgumentCaptor<Buffer> responseBody = ArgumentCaptor.forClass(Buffer.class);
        verify(response).write(responseBody.capture());
        assertErrorResponse(responseBody.getValue());
        verify(response).end();
    }
}
