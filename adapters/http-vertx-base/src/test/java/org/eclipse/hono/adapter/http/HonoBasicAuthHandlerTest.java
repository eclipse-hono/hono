/*******************************************************************************
 * Copyright (c) 2016, 2021 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.adapter.http;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static com.google.common.truth.Truth.assertThat;

import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.hono.adapter.auth.device.AbstractDeviceCredentials;
import org.eclipse.hono.adapter.auth.device.DeviceCredentialsAuthProvider;
import org.eclipse.hono.adapter.auth.device.PreCredentialsValidationHandler;
import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.ServerErrorException;
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.service.auth.DeviceUser;
import org.eclipse.hono.service.http.HttpContext;
import org.eclipse.hono.test.VertxMockSupport;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

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
import io.vertx.ext.web.Route;
import io.vertx.ext.web.RoutingContext;

/**
 * Tests verifying behavior of {@link HonoBasicAuthHandler}.
 *
 */
public class HonoBasicAuthHandlerTest {

    /**
     * Verifies that the handler returns the status code conveyed in a
     * failed {@code AuthProvider} invocation in the response.
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Test
    public void testHandleFailsWithStatusCodeFromAuthProvider() {

        // GIVEN an auth handler configured with an auth provider that
        // fails with a 503 error code during authentication
        final AuthProvider authProvider = mock(AuthProvider.class);
        final ServiceInvocationException error = new ServerErrorException(HttpURLConnection.HTTP_UNAVAILABLE);
        doAnswer(invocation -> {
            final Handler handler = invocation.getArgument(1);
            handler.handle(Future.failedFuture(error));
            return null;
        }).when(authProvider).authenticate(any(JsonObject.class), any(Handler.class));

        final HonoBasicAuthHandler authHandler = new HonoBasicAuthHandler(authProvider, "test", NoopTracerFactory.create()) {
            @Override
            public void parseCredentials(final RoutingContext context, final Handler<AsyncResult<JsonObject>> handler) {
                handler.handle(Future.succeededFuture(new JsonObject()));
            }
        };

        // WHEN trying to authenticate a request using the HTTP BASIC scheme
        final String authorization = "BASIC "
                + Base64.getEncoder().encodeToString("user:password".getBytes(StandardCharsets.UTF_8));
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
    @Test
    public void testHandleFailsForMalformedAuthorizationHeader() {

        final AuthProvider authProvider = mock(AuthProvider.class);
        final HonoBasicAuthHandler authHandler = new HonoBasicAuthHandler(authProvider, "test", NoopTracerFactory.create());
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
        authHandler.parseCredentials(ctx, VertxMockSupport.mockHandler());

        // THEN the request context is failed with the 400 error code
        verify(ctx).fail(exceptionCaptor.capture());
        assertThat(exceptionCaptor.getValue()).isInstanceOf(ClientErrorException.class);
        assertThat(((ClientErrorException) exceptionCaptor.getValue()).getErrorCode())
                .isEqualTo(HttpURLConnection.HTTP_BAD_REQUEST);

    }

    /**
     * Verifies that the PreCredentialsValidationHandler given for the AuthHandler is invoked
     * when authenticating.
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Test
    public void testPreCredentialsValidationHandlerGetsInvoked() {

        final AbstractDeviceCredentials deviceCredentials = mock(AbstractDeviceCredentials.class);
        final DeviceUser deviceUser = new DeviceUser("tenant", "device");

        // prepare authProvider
        final DeviceCredentialsAuthProvider<?> authProvider = mock(DeviceCredentialsAuthProvider.class);
        doReturn(deviceCredentials).when(authProvider).getCredentials(any(JsonObject.class));
        doAnswer(invocation -> {
            final Handler handler = invocation.getArgument(2);
            handler.handle(Future.succeededFuture(deviceUser));
            return null;
        }).when(authProvider).authenticate(any(), any(), any());

        // prepare PreCredentialsValidationHandler
        final PreCredentialsValidationHandler<HttpContext> preCredValidationHandler = mock(
                PreCredentialsValidationHandler.class);
        when(preCredValidationHandler.handle(eq(deviceCredentials), any())).thenReturn(Future.succeededFuture());

        // GIVEN an auth handler with a PreCredentialsValidationHandler
        final HonoBasicAuthHandler authHandler = new HonoBasicAuthHandler(authProvider, "test", preCredValidationHandler);

        // WHEN the auth handler handles a request
        final String authorization = "BASIC "
                + Base64.getEncoder().encodeToString("user:password".getBytes(StandardCharsets.UTF_8));
        final MultiMap headers = mock(MultiMap.class);
        when(headers.get(eq(HttpHeaders.AUTHORIZATION))).thenReturn(authorization);
        final HttpServerRequest req = mock(HttpServerRequest.class);
        when(req.headers()).thenReturn(headers);
        final HttpServerResponse resp = mock(HttpServerResponse.class);
        final RoutingContext routingContext = mock(RoutingContext.class);
        final Map<String, Object> routingContextMap = new HashMap<>();
        when(routingContext.put(any(), any())).thenAnswer(invocation -> {
            routingContextMap.put(invocation.getArgument(0), invocation.getArgument(1));
            return routingContext;
        });
        when(routingContext.get(any())).thenAnswer(invocation -> routingContextMap.get(invocation.getArgument(0)));
        when(routingContext.request()).thenReturn(req);
        when(routingContext.response()).thenReturn(resp);
        when(routingContext.currentRoute()).thenReturn(mock(Route.class));
        authHandler.handle(routingContext);

        // THEN authentication succeeds and the PreCredentialsValidationHandler has been invoked
        verify(routingContext).setUser(eq(deviceUser));
        verify(preCredValidationHandler).handle(eq(deviceCredentials), any());
    }

}
