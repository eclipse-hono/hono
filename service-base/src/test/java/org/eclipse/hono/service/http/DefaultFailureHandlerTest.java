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

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.net.HttpURLConnection;

import org.eclipse.hono.client.ClientErrorException;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;


/**
 * Verifies behavior of {@link DefaultFailureHandler}.
 *
 */
public class DefaultFailureHandlerTest {

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
     * Verifies that the handler sets an empty response body for
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
        verify(response, never()).write(any(Buffer.class));
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
        assertThat(bufferCaptor.getValue().toString(), is(detailMessage));
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
        assertThat(bufferCaptor.getValue().toString(), is(detailMessage));
        verify(response).end();
    }

}
