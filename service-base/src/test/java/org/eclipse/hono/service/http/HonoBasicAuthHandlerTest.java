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

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import io.vertx.ext.web.Route;
import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.ServerErrorException;
import org.eclipse.hono.client.ServiceInvocationException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.opentracing.noop.NoopTracerFactory;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.AuthProvider;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.ext.web.RoutingContext;
import org.mockito.ArgumentCaptor;

/**
 * Tests verifying behavior of {@link HonoBasicAuthHandler}.
 *
 */
@RunWith(VertxUnitRunner.class)
public class HonoBasicAuthHandlerTest {

    private HonoBasicAuthHandler authHandler;
    private AuthProvider authProvider;

    /**
     * Sets up the fixture.
     */
    @Before
    public void setUp() {
        authProvider = mock(AuthProvider.class);
        authHandler = new HonoBasicAuthHandler(authProvider, "test", NoopTracerFactory.create()) {

            @Override
            public void parseCredentials(final RoutingContext context, final Handler<AsyncResult<JsonObject>> handler) {
                handler.handle(Future.succeededFuture(new JsonObject()));
            }
        };
    }

    /**
     * Verifies that the handler returns the status code conveyed in a
     * failed {@code AuthProvider} invocation in the response.
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Test
    public void testHandleFailsWithStatusCodeFromAuthProvider() {

        // GIVEN an auth handler configured with an auth provider that
        // fails with a 503 error code during authentication
        final ServiceInvocationException error = new ServerErrorException(HttpURLConnection.HTTP_UNAVAILABLE);
        doAnswer(invocation -> {
            final Handler handler = invocation.getArgument(1);
            handler.handle(Future.failedFuture(error));
            return null;
        }).when(authProvider).authenticate(any(JsonObject.class), any(Handler.class));

        // WHEN trying to authenticate a request using the HTTP BASIC scheme
        final String authorization = new StringBuffer()
                .append("BASIC ")
                .append(Base64.getEncoder().encodeToString("user:password".getBytes(StandardCharsets.UTF_8)))
                .toString();
        final MultiMap headers = mock(MultiMap.class);
        when(headers.get(eq(HttpHeaders.AUTHORIZATION))).thenReturn(authorization);
        final HttpServerRequest req = mock(HttpServerRequest.class);
        when(req.headers()).thenReturn(headers);
        final HttpServerResponse resp = mock(HttpServerResponse.class);
        final RoutingContext ctx = mock(RoutingContext.class);
        when(ctx.request()).thenReturn(req);
        when(ctx.response()).thenReturn(resp);
        authHandler.handle(ctx);

        // THEN the request context is failed with the 503 error code
        verify(ctx).fail(error);
    }

    /**
     * Verifies that the handler returns the status code {@link HttpURLConnection#HTTP_BAD_REQUEST} in case of malformed
     * authorization header.
     */
    @SuppressWarnings({ "unchecked" })
    @Test
    public void testHandleFailsForMalformedAuthorizationHeader() {

        authHandler = new HonoBasicAuthHandler(authProvider, "test", NoopTracerFactory.create());
        final ArgumentCaptor<Throwable> exceptionCaptor = ArgumentCaptor.forClass(Throwable.class);
        // WHEN trying to authenticate a request using the HTTP BASIC scheme
        final String authorization = "BASIC test test";
        final MultiMap headers = mock(MultiMap.class);
        when(headers.get(eq(HttpHeaders.AUTHORIZATION))).thenReturn(authorization);
        final HttpServerRequest req = mock(HttpServerRequest.class);
        when(req.headers()).thenReturn(headers);
        final HttpServerResponse resp = mock(HttpServerResponse.class);
        final RoutingContext ctx = mock(RoutingContext.class);
        when(ctx.request()).thenReturn(req);
        when(ctx.response()).thenReturn(resp);
        when(ctx.currentRoute()).thenReturn(mock(Route.class));
        authHandler.parseCredentials(ctx, mock(Handler.class));

        // THEN the request context is failed with the 400 error code
        verify(ctx).fail(exceptionCaptor.capture());
        assertThat(exceptionCaptor.getValue(), instanceOf(ClientErrorException.class));
        assertThat(((ClientErrorException) exceptionCaptor.getValue()).getErrorCode(),
                is(HttpURLConnection.HTTP_BAD_REQUEST));

    }
}
