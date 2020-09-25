/*******************************************************************************
 * Copyright (c) 2019, 2020 Contributors to the Eclipse Foundation
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.hono.client.ServerErrorException;
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.service.auth.DeviceUser;
import org.eclipse.hono.service.auth.device.AbstractDeviceCredentials;
import org.eclipse.hono.service.auth.device.DeviceCredentialsAuthProvider;
import org.eclipse.hono.service.auth.device.PreCredentialsValidationHandler;
import org.junit.jupiter.api.Test;

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
import io.vertx.ext.web.handler.AuthHandler;
import io.vertx.ext.web.handler.impl.AuthHandlerImpl;
import io.vertx.ext.web.handler.impl.HttpStatusException;


/**
 * Tests verifying behavior of {@link HonoChainAuthHandler}.
 *
 */
public class HonoChainAuthHandlerTest {

    /**
     * Verifies that the handler returns the status code conveyed in a
     * failed {@code AuthProvider} invocation in the response.
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Test
    public void testHandleFailsWithStatusCodeFromAuthProvider() {

        // GIVEN a chained auth handler configured with an auth provider that
        // fails with a 503 error code during authentication
        final AuthProvider authProvider = mock(AuthProvider.class);
        final AuthHandler chainedAuthHandler = new AuthHandlerImpl(authProvider) {
            @Override
            public void parseCredentials(final RoutingContext context, final Handler<AsyncResult<JsonObject>> handler) {
                handler.handle(Future.succeededFuture(new JsonObject()));
            }
        };
        final HonoChainAuthHandler authHandler = new HonoChainAuthHandler();
        authHandler.append(chainedAuthHandler);
        final ServiceInvocationException error = new ServerErrorException(HttpURLConnection.HTTP_UNAVAILABLE);
        doAnswer(invocation -> {
            final Handler handler = invocation.getArgument(1);
            handler.handle(Future.failedFuture(error));
            return null;
        }).when(authProvider).authenticate(any(JsonObject.class), any(Handler.class));

        // WHEN trying to authenticate a request
        final HttpServerRequest req = mock(HttpServerRequest.class);
        final HttpServerResponse resp = mock(HttpServerResponse.class);
        final Map<String, Object> ctxMap = new HashMap<>();
        final RoutingContext ctx = mock(RoutingContext.class);
        when(ctx.put(anyString(), any())).thenAnswer(invocation -> {
            ctxMap.put(invocation.getArgument(0), invocation.getArgument(1));
            return ctx;
        });
        when(ctx.get(anyString())).thenAnswer(invocation -> ctxMap.get(invocation.getArgument(0)));
        when(ctx.request()).thenReturn(req);
        when(ctx.response()).thenReturn(resp);
        authHandler.handle(ctx);

        // THEN the request context is failed with the 503 error code
        verify(ctx).fail(error);
    }

    /**
     * Verifies that the PreCredentialsValidationHandler given for the HonoChainAuthHandler is invoked
     * when authenticating.
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Test
    public void testPreCredentialsValidationHandlerGetsInvoked() {

        final JsonObject parsedCredentials = new JsonObject().put("someKey", "someValue");
        final AbstractDeviceCredentials deviceCredentials = mock(AbstractDeviceCredentials.class);
        final DeviceUser deviceUser = new DeviceUser("tenant", "device");

        // prepare authProvider
        final DeviceCredentialsAuthProvider<?> authProvider2 = mock(DeviceCredentialsAuthProvider.class);
        doReturn(deviceCredentials).when(authProvider2).getCredentials(any(JsonObject.class));
        doAnswer(invocation -> {
            final Handler handler = invocation.getArgument(2);
            handler.handle(Future.succeededFuture(deviceUser));
            return null;
        }).when(authProvider2).authenticate(any(), any(), any());

        // prepare nestedAuthHandler1
        final AuthProvider authProvider1 = mock(AuthProvider.class);
        final AuthHandler nestedAuthHandler1 = new AuthHandlerImpl(authProvider1) {
            @Override
            public void parseCredentials(final RoutingContext context, final Handler<AsyncResult<JsonObject>> handler) {
                handler.handle(Future.failedFuture(new HttpStatusException(HttpURLConnection.HTTP_UNAUTHORIZED)));
            }
        };
        // prepare nestedAuthHandler2
        final AuthHandler nestedAuthHandler2 = new AuthHandlerImpl(authProvider2) {
            @Override
            public void parseCredentials(final RoutingContext context, final Handler<AsyncResult<JsonObject>> handler) {
                handler.handle(Future.succeededFuture(parsedCredentials));
            }
        };

        // prepare PreCredentialsValidationHandler
        final PreCredentialsValidationHandler<HttpContext> preCredValidationHandler = mock(
                PreCredentialsValidationHandler.class);
        when(preCredValidationHandler.handle(eq(deviceCredentials), any())).thenReturn(Future.succeededFuture());

        // GIVEN an auth handler with a PreCredentialsValidationHandler
        final HonoChainAuthHandler authHandler = new HonoChainAuthHandler(preCredValidationHandler);
        authHandler.append(nestedAuthHandler1);
        authHandler.append(nestedAuthHandler2);

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
        when(routingContext.remove(any())).thenAnswer(invocation -> routingContextMap.remove(invocation.getArgument(0)));
        when(routingContext.request()).thenReturn(req);
        when(routingContext.response()).thenReturn(resp);
        when(routingContext.currentRoute()).thenReturn(mock(Route.class));
        authHandler.handle(routingContext);

        // THEN authentication succeeds and the PreCredentialsValidationHandler has been invoked
        verify(routingContext).setUser(eq(deviceUser));
        verify(preCredValidationHandler).handle(eq(deviceCredentials), any());
    }
}
