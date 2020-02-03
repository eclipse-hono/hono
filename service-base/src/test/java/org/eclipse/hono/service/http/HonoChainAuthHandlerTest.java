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
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.HttpURLConnection;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.hono.client.ServerErrorException;
import org.eclipse.hono.client.ServiceInvocationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.AuthProvider;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.AuthHandler;
import io.vertx.ext.web.handler.impl.AuthHandlerImpl;


/**
 * Tests verifying behavior of {@link HonoChainAuthHandler}.
 *
 */
public class HonoChainAuthHandlerTest {

    private HonoChainAuthHandler authHandler;
    private AuthProvider authProvider;

    /**
     * Sets up the fixture.
     */
    @BeforeEach
    public void setUp() {

        authProvider = mock(AuthProvider.class);
        final AuthHandler chainedAuthHandler = new AuthHandlerImpl(authProvider) {

            @Override
            public void parseCredentials(final RoutingContext context, final Handler<AsyncResult<JsonObject>> handler) {
                handler.handle(Future.succeededFuture(new JsonObject()));
            }
        };
        authHandler = new HonoChainAuthHandler();
        authHandler.append(chainedAuthHandler);
    }

    /**
     * Verifies that the handler returns the status code conveyed in a
     * failed {@code AuthProvider} invocation in the response.
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Test
    public void testHandleFailsWithStatusCodeFromAuthProvider() {

        // GIVEN a chained auth handler configured with an auth provider that
        // fails with a 503 error code during authentication
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
        when(ctx.get(anyString())).thenAnswer(invocation -> {
            return ctxMap.get(invocation.getArgument(0));
        });
        when(ctx.request()).thenReturn(req);
        when(ctx.response()).thenReturn(resp);
        authHandler.handle(ctx);

        // THEN the request context is failed with the 503 error code
        verify(ctx).fail(error);
    }

}
