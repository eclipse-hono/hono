/**
 * Copyright (c) 2022, 2023 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * https://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package org.eclipse.hono.adapter.auth.device.jwt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import static com.google.common.truth.Truth.assertThat;

import java.net.HttpURLConnection;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;

import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.registry.CredentialsClient;
import org.eclipse.hono.service.auth.DeviceUser;
import org.eclipse.hono.util.CredentialsConstants;
import org.eclipse.hono.util.CredentialsObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.opentracing.noop.NoopTracerFactory;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

/**
 * Tests verifying behavior of {@link JwtAuthProvider}.
 *
 */
@ExtendWith(VertxExtension.class)
class JwtAuthProviderTest {

    private final String tenantId = "tenant-id";
    private final String deviceId = "device-id";
    private final String authId = "auth-id";
    private final String password = "jws";
    private final JwtCredentials deviceCredentials = JwtCredentials.create(tenantId, authId, password);
    private JwtAuthProvider authProvider;
    private CredentialsClient credentialsClient;
    private JwsValidator tokenValidator;

    /**
     * Sets up the fixture.
     */
    @BeforeEach
    void setUp() {
        tokenValidator = mock(JwsValidator.class);
        credentialsClient = mock(CredentialsClient.class);
        authProvider = new JwtAuthProvider(credentialsClient, tokenValidator, NoopTracerFactory.create());
        givenCredentialsOnRecord(CredentialsObject.fromRawPublicKey(
                deviceId,
                authId,
                CredentialsConstants.RSA_ALG,
                new byte[]{0x01},
                null,
                null));

    }

    /**
     * Verifies that getCredentials returns a JwtCredentials object, when valid authInfo is provided.
     */
    @Test
    void testGetCredentialsAuthInfoIsValid() {
        final JsonObject authInfo = new JsonObject()
                .put(CredentialsConstants.FIELD_AUTH_ID, authId)
                .put(CredentialsConstants.FIELD_PAYLOAD_TENANT_ID, tenantId)
                .put(CredentialsConstants.FIELD_PASSWORD, password);

        final JwtCredentials jwtCredentials = authProvider.getCredentials(authInfo);
        assertThat(jwtCredentials).isNotNull();
        assertEquals(authId, jwtCredentials.getAuthId());
        assertEquals(tenantId, jwtCredentials.getTenantId());
        assertEquals(password, jwtCredentials.getJws());
    }

    /**
     * Verifies that getCredentials returns null, when invalid authInfo is provided.
     */
    @Test
    void testGetCredentialsAuthInfoIsInvalid() {
        final JsonObject authInfo = new JsonObject()
                .put(CredentialsConstants.FIELD_AUTH_ID, null)
                .put(CredentialsConstants.FIELD_PAYLOAD_TENANT_ID, tenantId)
                .put(CredentialsConstants.FIELD_PASSWORD, password);

        final JwtCredentials jwtCredentials = authProvider.getCredentials(authInfo);
        assertThat(jwtCredentials).isNull();
    }

    /**
     * Verifies that getCredentials throws a {@link NullPointerException}, when provided authInfo is null.
     */
    @Test
    void testGetCredentialsAuthInfoIsNull() {
        assertThrows(NullPointerException.class, () -> authProvider.getCredentials(null));
    }

    /**
     * Verifies that doValidateCredentials returns a succeeding future with the Device information, when
     * AuthTokenValidator.expand() returns valid JWS Claims.
     */
    @SuppressWarnings("unchecked")
    @Test
    void testDoValidateCredentialsAuthTokenValidatorExpandReturnsValidJwsClaims(final VertxTestContext ctx) {
        final Instant now = Instant.now();
        final Jws<Claims> claimsJws = mock(Jws.class);
        final Claims claims = mock(Claims.class);

        when(claims.getExpiration()).thenReturn(Date.from(now.plusSeconds(3600 * 24)));
        when(claims.getIssuedAt()).thenReturn(Date.from(now));
        when(claimsJws.getBody()).thenReturn(claims);

        when(tokenValidator.expand(anyString(), any(List.class), any(Duration.class)))
            .thenReturn(Future.succeededFuture(claimsJws));

        final Promise<DeviceUser> result = Promise.promise();
        authProvider.authenticate(deviceCredentials, null, result);
        result.future().onComplete(ctx.succeeding(device -> {
            ctx.verify(() -> {
                assertThat(device.getDeviceId()).isEqualTo(deviceId);
                assertThat(device.getTenantId()).isEqualTo(tenantId);
            });
            ctx.completeNow();
        }));
    }

    /**
     * Verifies that doValidateCredentials returns a failing future, when AuthTokenValidator.expand() throws an
     * Exception.
     */
    @SuppressWarnings("unchecked")
    @Test
    void testDoValidateCredentialsAuthTokenValidatorExpandThrowsException(final VertxTestContext ctx) {

        when(tokenValidator.expand(anyString(), any(List.class), any(Duration.class)))
            .thenReturn(Future.failedFuture(new ClientErrorException(
                    HttpURLConnection.HTTP_UNAUTHORIZED,
                    "failed to validate token")));

        final Promise<DeviceUser> result = Promise.promise();
        authProvider.authenticate(deviceCredentials, null, result);
        result.future().onComplete(ctx.failing(t -> {
            ctx.verify(() -> assertThat(t).isInstanceOf(ClientErrorException.class));
            ctx.completeNow();
        }));
    }

    private void givenCredentialsOnRecord(final CredentialsObject credentials) {
        when(credentialsClient.get(
                anyString(),
                eq(CredentialsConstants.SECRETS_TYPE_RAW_PUBLIC_KEY),
                anyString(),
                any(),
                any())).thenReturn(Future.succeededFuture(credentials));
    }
}
