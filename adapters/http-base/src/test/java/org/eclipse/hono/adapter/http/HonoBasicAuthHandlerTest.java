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

import org.eclipse.hono.adapter.HttpContext;
import org.eclipse.hono.adapter.auth.device.AbstractDeviceCredentials;
import org.eclipse.hono.adapter.auth.device.DeviceCredentialsAuthProvider;
import org.eclipse.hono.adapter.auth.device.PreCredentialsValidationHandler;
import org.eclipse.hono.adapter.auth.device.usernamepassword.UsernamePasswordCredentials;
import org.eclipse.hono.client.ServerErrorException;
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.service.auth.DeviceUser;
import org.eclipse.hono.test.VertxMockSupport;
import org.eclipse.hono.util.TelemetryConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.HttpException;

/**
 * Tests verifying behavior of {@link HonoBasicAuthHandler}.
 *
 */
public class HonoBasicAuthHandlerTest {

    private RoutingContext ctx;
    private HttpServerRequest req;
    private HttpServerResponse resp;

    @BeforeEach
    void setUp() {
        req = mock(HttpServerRequest.class);
        when(req.path()).thenReturn("/" + TelemetryConstants.TELEMETRY_ENDPOINT);
        resp = mock(HttpServerResponse.class);
        ctx = mock(RoutingContext.class);
        when(ctx.request()).thenReturn(req);
        when(ctx.response()).thenReturn(resp);
    }

    /**
     * Verifies that the handler returns the status code conveyed in a
     * failed {@code AuthenticationProvider} invocation in the response.
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Test
    public void testHandleFailsWithStatusCodeFromAuthProvider() {

        // GIVEN an auth handler configured with an auth provider that
        // fails with a 503 error code during authentication
        final DeviceCredentialsAuthProvider<UsernamePasswordCredentials> authProvider = mock(DeviceCredentialsAuthProvider.class);
        final ServiceInvocationException error = new ServerErrorException(HttpURLConnection.HTTP_UNAVAILABLE);
        doAnswer(invocation -> {
            final Handler handler = invocation.getArgument(2);
            handler.handle(Future.failedFuture(error));
            return null;
        }).when(authProvider).authenticate(any(UsernamePasswordCredentials.class), any(), VertxMockSupport.anyHandler());
        when(authProvider.getCredentials(any(JsonObject.class)))
            .thenReturn(UsernamePasswordCredentials.create("device1@DEFAULT_TENANT", "secret"));
        final HonoBasicAuthHandler authHandler = new HonoBasicAuthHandler(authProvider, "test");

        // WHEN trying to authenticate a request using the HTTP BASIC scheme
        final String authorization = "BASIC "
                + Base64.getEncoder().encodeToString("user:password".getBytes(StandardCharsets.UTF_8));
        final MultiMap headers = MultiMap.caseInsensitiveMultiMap();
        headers.add(HttpHeaders.AUTHORIZATION, authorization);
        when(req.headers()).thenReturn(headers);
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

        final DeviceCredentialsAuthProvider<?> authProvider = mock(DeviceCredentialsAuthProvider.class);
        final HonoBasicAuthHandler authHandler = new HonoBasicAuthHandler(authProvider, "test");
        // WHEN trying to authenticate a request using the HTTP BASIC scheme
        final String authorization = "BASIC test test";
        final MultiMap headers = mock(MultiMap.class);
        when(headers.get(eq(HttpHeaders.AUTHORIZATION))).thenReturn(authorization);
        when(req.headers()).thenReturn(headers);
        when(ctx.currentRoute()).thenReturn(mock(Route.class));

        authHandler.handle(ctx);

        // THEN the routing context is failed with a 400 error
        final ArgumentCaptor<Throwable> error = ArgumentCaptor.forClass(Throwable.class);
        verify(ctx).fail(error.capture());
        assertThat(error.getValue()).isInstanceOf(HttpException.class);
        assertThat(((HttpException) error.getValue()).getStatusCode()).isEqualTo(HttpURLConnection.HTTP_BAD_REQUEST);
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
        final DeviceCredentialsAuthProvider<AbstractDeviceCredentials> authProvider = mock(DeviceCredentialsAuthProvider.class);
        doReturn(deviceCredentials).when(authProvider).getCredentials(any(JsonObject.class));
        doAnswer(invocation -> {
            final Handler handler = invocation.getArgument(2);
            handler.handle(Future.succeededFuture(deviceUser));
            return null;
        }).when(authProvider).authenticate(any(AbstractDeviceCredentials.class), any(), VertxMockSupport.anyHandler());

        // prepare PreCredentialsValidationHandler
        final PreCredentialsValidationHandler<HttpContext> preCredValidationHandler = mock(
                PreCredentialsValidationHandler.class);
        when(preCredValidationHandler.handle(eq(deviceCredentials), any(HttpContext.class))).thenReturn(Future.succeededFuture());

        // GIVEN an auth handler with a PreCredentialsValidationHandler
        final HonoBasicAuthHandler authHandler = new HonoBasicAuthHandler(authProvider, "test", preCredValidationHandler);

        // WHEN the auth handler handles a request
        final String authorization = "BASIC "
                + Base64.getEncoder().encodeToString("user:password".getBytes(StandardCharsets.UTF_8));
        final MultiMap headers = mock(MultiMap.class);
        when(headers.get(eq(HttpHeaders.AUTHORIZATION))).thenReturn(authorization);
        when(req.headers()).thenReturn(headers);
        final Map<String, Object> routingContextMap = new HashMap<>();
        when(ctx.put(any(), any())).thenAnswer(invocation -> {
            routingContextMap.put(invocation.getArgument(0), invocation.getArgument(1));
            return ctx;
        });
        when(ctx.get(any())).thenAnswer(invocation -> routingContextMap.get(invocation.getArgument(0)));
        when(ctx.currentRoute()).thenReturn(mock(Route.class));
        authHandler.handle(ctx);

        // THEN authentication succeeds and the PreCredentialsValidationHandler has been invoked
        verify(ctx).setUser(eq(deviceUser));
        verify(preCredValidationHandler).handle(eq(deviceCredentials), any(HttpContext.class));
    }

}
