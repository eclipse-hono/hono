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

package org.eclipse.hono.adapter.mqtt;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import static com.google.common.truth.Truth.assertThat;

import java.net.HttpURLConnection;
import java.security.Key;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.eclipse.hono.adapter.auth.device.jwt.JwtAuthProvider;
import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.test.TracingMockSupport;
import org.eclipse.hono.util.CredentialsConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.opentracing.Span;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.mqtt.MqttAuth;
import io.vertx.mqtt.MqttEndpoint;

/**
 * Tests verifying behavior of {@link JwtAuthHandler}.
 *
 */
@ExtendWith(VertxExtension.class)
@Timeout(value = 5, timeUnit = TimeUnit.SECONDS)
public class JwtAuthHandlerTest {

    private static final String AUTH_ID = "authId";
    private static final String TENANT_ID = "tenantId";

    private JwtAuthHandler authHandler;
    private Span span;
    private MqttAuth auth;
    private MqttEndpoint endpoint;

    /**
     * Sets up the fixture.
     */
    @BeforeEach
    public void setUp() {
        auth = mock(MqttAuth.class);
        endpoint = mock(MqttEndpoint.class);
        span = TracingMockSupport.mockSpan();
        authHandler = new JwtAuthHandler(mock(JwtAuthProvider.class), null);

        when(endpoint.auth()).thenReturn(auth);
    }

    private String getJws(final String audience, final String tenant, final String subject) {
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

    /**
     * Verifies that the handler extracts the tenant and auth ID from the MQTT Client Identifier,
     * if the JWT's audience claim does not have value {@value CredentialsConstants#AUDIENCE_HONO_ADAPTER}.
     *
     * @param audience The value of the aud claim.
     * @param tenant The value of the tid claim.
     * @param authId The value of the sub claim.
     * @param ctx The vert.x test context.
     */
    @ParameterizedTest
    @CsvSource(value = {",,", "not-hono-adapter,,", "not-hono-adapter,some-tenant,some-device"})
    public void testParseCredentialsMqttClientIdIncludesIds(
            final String audience,
            final String tenant,
            final String authId,
            final VertxTestContext ctx) {

        final var jws = getJws(audience, tenant, authId);

        when(auth.getUsername()).thenReturn("");
        when(auth.getPassword()).thenReturn(jws);
        when(endpoint.clientIdentifier()).thenReturn(String.format("tenants/%s/devices/%s", TENANT_ID, AUTH_ID));

        final MqttConnectContext context = MqttConnectContext.fromConnectPacket(endpoint, span);
        authHandler.parseCredentials(context)
                .onComplete(ctx.succeeding(info -> {
                    ctx.verify(() -> {
                        assertThat(info.getString(Claims.ISSUER)).isEqualTo(AUTH_ID);
                        assertThat(info.getString(CredentialsConstants.FIELD_AUTH_ID)).isEqualTo(AUTH_ID);
                        assertThat(info.getString(CredentialsConstants.FIELD_PAYLOAD_TENANT_ID)).isEqualTo(TENANT_ID);
                        assertThat(info.getString(CredentialsConstants.FIELD_PASSWORD)).isEqualTo(jws);
                    });
                    ctx.completeNow();
                }));
    }

    /**
     * Verifies that the handler extracts the tenant and auth ID from the JWT,
     * if the JWT's audience claim does have value {@value CredentialsConstants#AUDIENCE_HONO_ADAPTER}.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testParseCredentialsJwtClaimsIncludeIds(final VertxTestContext ctx) {

        final String jws = getJws(CredentialsConstants.AUDIENCE_HONO_ADAPTER, TENANT_ID, AUTH_ID);

        when(auth.getUsername()).thenReturn("");
        when(auth.getPassword()).thenReturn(jws);
        when(endpoint.clientIdentifier()).thenReturn("client identifier");

        final MqttConnectContext context = MqttConnectContext.fromConnectPacket(endpoint, span);
        authHandler.parseCredentials(context)
                .onComplete(ctx.succeeding(info -> {
                    ctx.verify(() -> {
                        assertThat(info.getString(Claims.ISSUER)).isEqualTo(AUTH_ID);
                        assertThat(info.getString(CredentialsConstants.FIELD_AUTH_ID)).isEqualTo(AUTH_ID);
                        assertThat(info.getString(CredentialsConstants.FIELD_PAYLOAD_TENANT_ID)).isEqualTo(TENANT_ID);
                        assertThat(info.getString(CredentialsConstants.FIELD_PASSWORD)).isEqualTo(jws);
                    });
                    ctx.completeNow();
                }));
    }

    /**
     * Verifies that the handler fails with a 401 error code if the MQTT client identifier
     * does not have the expected format.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testParseCredentialsMalformedMqttClientIdAndJwtClaims(final VertxTestContext ctx) {

        final String jws = getJws(null, null, null);

        when(auth.getUsername()).thenReturn("");
        when(auth.getPassword()).thenReturn(jws);
        when(endpoint.clientIdentifier()).thenReturn(String.format("tenants.%s.devices.%s", TENANT_ID, AUTH_ID));

        final MqttConnectContext context = MqttConnectContext.fromConnectPacket(endpoint, span);
        authHandler.parseCredentials(context)
                .onComplete(ctx.failing(t -> {
                    ctx.verify(() -> {
                        assertThat(t).isInstanceOf(ClientErrorException.class);
                        assertThat(ServiceInvocationException.extractStatusCode(t))
                            .isEqualTo(HttpURLConnection.HTTP_UNAUTHORIZED);
                    });
                    ctx.completeNow();
                }));
    }

    /**
     * Verifies that the handler fails with a 401 error code if the password from the device's CONNECT
     * packet is not in a valid JWS structure.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testParseCredentialsMalformedJws(final VertxTestContext ctx) {

        final String jws = "not a jws";

        when(auth.getUsername()).thenReturn("");
        when(auth.getPassword()).thenReturn(jws);
        when(endpoint.clientIdentifier()).thenReturn(String.format("tenants/%s/devices/%s", TENANT_ID, AUTH_ID));

        final MqttConnectContext context = MqttConnectContext.fromConnectPacket(endpoint, span);
        authHandler.parseCredentials(context)
                .onComplete(ctx.failing(t -> {
                    ctx.verify(() -> {
                        assertThat(t).isInstanceOf(ClientErrorException.class);
                        assertThat(ServiceInvocationException.extractStatusCode(t))
                            .isEqualTo(HttpURLConnection.HTTP_UNAUTHORIZED);
                    });
                    ctx.completeNow();
                }));
    }

    /**
     * Verifies that the handler fails with a 401 error code if the device's MQTT CONNECT packet does not
     * contain authentication information.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testParseCredentialsMqttAuthNull(final VertxTestContext ctx) {

        when(endpoint.clientIdentifier()).thenReturn(String.format("tenants/%s/devices/%s", TENANT_ID, AUTH_ID));
        when(endpoint.auth()).thenReturn(null);

        final MqttConnectContext context = MqttConnectContext.fromConnectPacket(endpoint, span);
        authHandler.parseCredentials(context)
                .onComplete(ctx.failing(t -> {
                    ctx.verify(() -> {
                        assertThat(t).isInstanceOf(ClientErrorException.class);
                        assertThat(ServiceInvocationException.extractStatusCode(t))
                            .isEqualTo(HttpURLConnection.HTTP_UNAUTHORIZED);
                    });
                    ctx.completeNow();
                }));
    }
}
