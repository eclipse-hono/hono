/*******************************************************************************
 * Copyright (c) 2023 Contributors to the Eclipse Foundation
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
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static com.google.common.truth.Truth.assertThat;

import java.net.HttpURLConnection;
import java.security.Key;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import org.eclipse.hono.adapter.HttpContext;
import org.eclipse.hono.adapter.auth.device.DeviceCredentialsAuthProvider;
import org.eclipse.hono.adapter.auth.device.PreCredentialsValidationHandler;
import org.eclipse.hono.adapter.auth.device.jwt.JwtCredentials;
import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.ServerErrorException;
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.service.auth.DeviceUser;
import org.eclipse.hono.test.VertxMockSupport;
import org.eclipse.hono.util.CredentialsConstants;
import org.eclipse.hono.util.TelemetryConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;

import io.jsonwebtoken.Jwts;
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
 * Tests verifying behavior of {@link JwtAuthHandler}.
 *
 */
class JwtAuthHandlerTest {

    private RoutingContext ctx;
    private HttpServerRequest req;
    private HttpServerResponse resp;

    private static String getJws(final String audience, final String tenant, final String subject) {
        final Key key;
        try {
            final KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("EC");
            keyPairGenerator.initialize(256);
            key = keyPairGenerator.generateKeyPair().getPrivate();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
        final var builder = Jwts.builder().signWith(key);
        Optional.ofNullable(audience).ifPresent(builder::setAudience);
        Optional.ofNullable(tenant).ifPresent(t -> builder.claim(CredentialsConstants.CLAIM_TENANT_ID, t));
        Optional.ofNullable(subject).ifPresent(s -> builder.setSubject(s).setIssuer(s));
        builder.setExpiration(Date.from(Instant.now().plus(Duration.ofMinutes(10))));
        return builder.compact();
    }

    private static Stream<String> testHandleFailsForMalformedUriOrClaims() {
        return Stream.of(
                getJws(null, null, null),
                getJws("hono-adapter", null, "device"),
                getJws("hono-adapter", "tenant", null)
        );
    }

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
     * Verifies that the handler succeeds with the DeviceUser.
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Test
    public void testHandleSucceedsWithDeviceUser() {

        final String jws = getJws("not-hono-adapter", null, null);
        final DeviceUser deviceUser = new DeviceUser("tenant", "device");
        // GIVEN an auth handler configured with an auth provider that
        // succeeds with a DeviceUser during authentication
        final DeviceCredentialsAuthProvider<JwtCredentials> authProvider = mock(DeviceCredentialsAuthProvider.class);
        doAnswer(invocation -> {
            final Handler handler = invocation.getArgument(2);
            handler.handle(Future.succeededFuture(deviceUser));
            return null;
        }).when(authProvider).authenticate(any(JwtCredentials.class), any(), VertxMockSupport.anyHandler());
        when(authProvider.getCredentials(any(JsonObject.class)))
                .thenReturn(JwtCredentials.create("tenant", "device", jws));
        final JwtAuthHandler authHandler = new JwtAuthHandler(authProvider, "test");

        // WHEN trying to authenticate a request using the Bearer scheme
        final String authorization = "Bearer " + jws;
        final MultiMap headers = MultiMap.caseInsensitiveMultiMap();
        headers.add(HttpHeaders.AUTHORIZATION, authorization);
        when(req.headers()).thenReturn(headers);
        when(req.uri()).thenReturn("/telemetry/tenant/device/");
        authHandler.handle(ctx);

        // THEN the request context is succeeded with the DeviceUser
        verify(ctx).setUser(deviceUser);
    }

    /**
     * Verifies that the PreCredentialsValidationHandler given for the AuthHandler is invoked when authenticating.
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Test
    public void testPreCredentialsValidationHandlerGetsInvoked() {

        final JwtCredentials deviceCredentials = mock(JwtCredentials.class);
        final DeviceUser deviceUser = new DeviceUser("tenant", "device");

        // prepare authProvider
        final DeviceCredentialsAuthProvider<JwtCredentials> authProvider = mock(
                DeviceCredentialsAuthProvider.class);
        doReturn(deviceCredentials).when(authProvider).getCredentials(any(JsonObject.class));
        doAnswer(invocation -> {
            final Handler handler = invocation.getArgument(2);
            handler.handle(Future.succeededFuture(deviceUser));
            return null;
        }).when(authProvider).authenticate(any(JwtCredentials.class), any(), VertxMockSupport.anyHandler());

        // prepare PreCredentialsValidationHandler
        final PreCredentialsValidationHandler<HttpContext> preCredValidationHandler = mock(
                PreCredentialsValidationHandler.class);
        when(preCredValidationHandler.handle(eq(deviceCredentials), any(HttpContext.class)))
                .thenReturn(Future.succeededFuture());

        // GIVEN an auth handler with a PreCredentialsValidationHandler
        final JwtAuthHandler authHandler = new JwtAuthHandler(authProvider, "test", preCredValidationHandler);

        // WHEN the auth handler handles a request
        final String authorization = "Bearer " + getJws("hono-adapter", "tenant", "device");
        final MultiMap headers = mock(MultiMap.class);
        when(headers.get(HttpHeaders.AUTHORIZATION)).thenReturn(authorization);
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
        verify(ctx).setUser(deviceUser);
        verify(preCredValidationHandler).handle(eq(deviceCredentials), any(HttpContext.class));
    }

    /**
     * Verifies that the handler returns the status code conveyed in a failed {@code AuthenticationProvider} invocation
     * in the response.
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Test
    public void testHandleFailsWithStatusCodeFromAuthProvider() {

        final String jws = getJws("hono-adapter", "DEFAULT_TENANT", "device1");
        // GIVEN an auth handler configured with an auth provider that
        // fails with a 503 error code during authentication
        final DeviceCredentialsAuthProvider<JwtCredentials> authProvider = mock(DeviceCredentialsAuthProvider.class);
        final ServiceInvocationException error = new ServerErrorException(HttpURLConnection.HTTP_UNAVAILABLE);
        doAnswer(invocation -> {
            final Handler handler = invocation.getArgument(2);
            handler.handle(Future.failedFuture(error));
            return null;
        }).when(authProvider).authenticate(any(JwtCredentials.class), any(), VertxMockSupport.anyHandler());
        when(authProvider.getCredentials(any(JsonObject.class)))
                .thenReturn(JwtCredentials.create("DEFAULT_TENANT", "device1", jws));
        final JwtAuthHandler authHandler = new JwtAuthHandler(authProvider, "test");

        // WHEN trying to authenticate a request using the Bearer scheme
        final String authorization = "Bearer " + jws;
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
    @SuppressWarnings({ "unchecked" })
    @Test
    public void testHandleFailsForMalformedAuthorizationHeader() {

        final DeviceCredentialsAuthProvider<JwtCredentials> authProvider = mock(DeviceCredentialsAuthProvider.class);
        final JwtAuthHandler authHandler = new JwtAuthHandler(authProvider, "test");
        // WHEN trying to authenticate a request using a wrong scheme
        final String authorization = "Basic test";
        final MultiMap headers = mock(MultiMap.class);
        when(headers.get(HttpHeaders.AUTHORIZATION)).thenReturn(authorization);
        when(req.headers()).thenReturn(headers);
        when(ctx.currentRoute()).thenReturn(mock(Route.class));

        authHandler.handle(ctx);

        // THEN the routing context is failed with a 401 error
        final ArgumentCaptor<Throwable> error = ArgumentCaptor.forClass(Throwable.class);
        verify(ctx).fail(error.capture());
        assertThat(error.getValue()).isInstanceOf(HttpException.class);
        assertThat(((HttpException) error.getValue()).getStatusCode()).isEqualTo(HttpURLConnection.HTTP_UNAUTHORIZED);
    }

    /**
     * Verifies that the handler returns the status code {@link HttpURLConnection#HTTP_BAD_REQUEST} in case of a
     * malformed token.
     */
    @SuppressWarnings({ "unchecked" })
    @ParameterizedTest
    @CsvSource(value = { "header.claims.signature.extra", "$header.claims.signature", "header.claims.signature" })
    public void testHandleFailsForMalformedToken(final String jws) {

        final DeviceCredentialsAuthProvider<JwtCredentials> authProvider = mock(DeviceCredentialsAuthProvider.class);
        final JwtAuthHandler authHandler = new JwtAuthHandler(authProvider, "test");
        // WHEN trying to authenticate a request using the HTTP Bearer scheme with a malformed token
        final String authorization = "Bearer " + jws;
        final MultiMap headers = mock(MultiMap.class);
        when(headers.get(HttpHeaders.AUTHORIZATION)).thenReturn(authorization);
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
     * Verifies that the handler returns a ClientErrorException in case of a malformed Uri or missing tenant and device
     * claims.
     */
    @SuppressWarnings({ "unchecked" })
    @ParameterizedTest
    @MethodSource
    public void testHandleFailsForMalformedUriOrClaims(final String jws) {

        final DeviceCredentialsAuthProvider<JwtCredentials> authProvider = mock(DeviceCredentialsAuthProvider.class);
        final JwtAuthHandler authHandler = new JwtAuthHandler(authProvider, "test");
        // WHEN trying to authenticate a request using the HTTP Bearer scheme with a token without tenant and device
        // claims
        final String authorization = "Bearer " + jws;
        final MultiMap headers = mock(MultiMap.class);
        when(headers.get(HttpHeaders.AUTHORIZATION)).thenReturn(authorization);
        when(req.headers()).thenReturn(headers);
        // and a URI without tenant and device
        when(req.uri()).thenReturn("/telemetry/");
        when(ctx.currentRoute()).thenReturn(mock(Route.class));

        authHandler.handle(ctx);

        // THEN the routing context is failed with a ClientErrorException
        final ArgumentCaptor<Throwable> error = ArgumentCaptor.forClass(Throwable.class);
        verify(ctx).fail(error.capture());
        assertThat(error.getValue()).isInstanceOf(ClientErrorException.class);
    }
}
