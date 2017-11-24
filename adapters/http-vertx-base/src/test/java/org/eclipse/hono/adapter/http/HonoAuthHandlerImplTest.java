/**
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 1.0 which is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * SPDX-License-Identifier: EPL-1.0
 */

package org.eclipse.hono.adapter.http;

import static org.junit.Assert.*;
import static org.hamcrest.CoreMatchers.is;
import static org.mockito.Mockito.*;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.eclipse.hono.client.ServerErrorException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;

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


/**
 * Tests verifying behavior of {@link HonoAuthHandlerImpl}.
 *
 */
@RunWith(VertxUnitRunner.class)
public class HonoAuthHandlerImplTest {

    private HonoAuthHandlerImpl authHandler;
    private AuthProvider authProvider;

    /**
     * Sets up the fixture.
     */
    @Before
    public void setUp() {
        authProvider = mock(AuthProvider.class);
        authHandler = new HonoAuthHandlerImpl(authProvider, "hono");
    }

    /**
     * Verifies that the handler returns the status code conveyed in a
     * failed @{@code AuthProvider} invocation in the response.
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Test
    public void testHandleFailsWithStatusCodeFromAuthProvider() {

        // GIVEN an auth handler configured with an auth provider that
        // fails with a 503 error code during authentication
        final int EXPECTED_ERROR_CODE = 503;
        doAnswer(invocation -> {
            Handler handler = invocation.getArgumentAt(1, Handler.class);
            handler.handle(Future.failedFuture(new ServerErrorException(EXPECTED_ERROR_CODE)));
            return null;
        }).when(authProvider).authenticate(any(JsonObject.class), any(Handler.class));

        // WHEN trying to authenticate a request using the HTTP BASIC scheme
        final String authorization = new StringBuffer()
                .append("BASIC ")
                .append(Base64.getEncoder().encodeToString("user:password".getBytes(StandardCharsets.UTF_8)))
                .toString();
        MultiMap headers = mock(MultiMap.class);
        when(headers.get(eq(HttpHeaders.AUTHORIZATION))).thenReturn(authorization);
        HttpServerRequest req = mock(HttpServerRequest.class);
        when(req.headers()).thenReturn(headers);
        HttpServerResponse resp = mock(HttpServerResponse.class);
        RoutingContext ctx = mock(RoutingContext.class);
        when(ctx.request()).thenReturn(req);
        when(ctx.response()).thenReturn(resp);
        authHandler.handle(ctx);

        // THEN the request context is failed with the 503 error code
        ArgumentCaptor<Throwable> failureCaptor = ArgumentCaptor.forClass(Throwable.class);
        verify(ctx).fail(failureCaptor.capture());
        ServerErrorException ex = (ServerErrorException) failureCaptor.getValue();
        assertThat(ex.getErrorCode(), is(EXPECTED_ERROR_CODE));
    }

}
