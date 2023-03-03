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

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import static com.google.common.truth.Truth.assertThat;

import org.eclipse.hono.adapter.auth.device.jwt.JwtAuthProvider;
import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.service.auth.ExternalJwtAuthTokenValidator;
import org.eclipse.hono.test.TracingMockSupport;
import org.eclipse.hono.util.CredentialsConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.MalformedJwtException;
import io.opentracing.Span;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.mqtt.MqttAuth;
import io.vertx.mqtt.MqttEndpoint;

/**
 * Tests verifying behavior of {@link JwtAuthHandler}.
 *
 */
@ExtendWith(VertxExtension.class)
public class JwtAuthHandlerTest {

    private JwtAuthHandler authHandler;
    private Span span;
    private MqttAuth auth;
    private MqttEndpoint endpoint;
    private ExternalJwtAuthTokenValidator authTokenValidator;
    private JsonObject claims;

    /**
     * Sets up the fixture.
     */
    @BeforeEach
    public void setUp() {
        auth = mock(MqttAuth.class);
        endpoint = mock(MqttEndpoint.class);
        authTokenValidator = mock(ExternalJwtAuthTokenValidator.class);
        claims = mock(JsonObject.class);
        span = TracingMockSupport.mockSpan();
        authHandler = new JwtAuthHandler(mock(JwtAuthProvider.class), null, authTokenValidator);

        when(endpoint.auth()).thenReturn(auth);
    }

    /**
     * Verifies that the handler can correctly extract the {@value CredentialsConstants#FIELD_PAYLOAD_TENANT_ID} and
     * {@value CredentialsConstants#FIELD_AUTH_ID} from the MQTT Client Identifier, if they are provided correctly.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testParseCredentialsMqttClientIdIncludesIds(final VertxTestContext ctx) {

        final String authId = "authId";
        final String tenantId = "tenantId";
        final String jwt = "header.body.signature";

        when(auth.getUsername()).thenReturn("");
        when(auth.getPassword()).thenReturn(jwt);
        when(endpoint.clientIdentifier()).thenReturn(String.format("tenants/%s/devices/%s", tenantId, authId));
        when(authTokenValidator.getJwtClaims(jwt)).thenReturn(claims);

        final MqttConnectContext context = MqttConnectContext.fromConnectPacket(endpoint, span);
        authHandler.parseCredentials(context)
                .onComplete(ctx.succeeding(info -> {
                    ctx.verify(() -> {
                        assertThat(info.containsKey(Claims.ISSUER)).isFalse();
                        assertThat(info.getString(CredentialsConstants.FIELD_AUTH_ID)).isEqualTo(authId);
                        assertThat(info.getString(CredentialsConstants.FIELD_PAYLOAD_TENANT_ID)).isEqualTo(tenantId);
                        assertThat(info.getString(CredentialsConstants.FIELD_PASSWORD)).isEqualTo(jwt);
                    });
                    ctx.completeNow();
                }));
    }

    /**
     * Verifies that the handler can correctly extract the {@value CredentialsConstants#FIELD_PAYLOAD_TENANT_ID} and
     * {@value CredentialsConstants#FIELD_AUTH_ID} from the JWT claims, if they are provided correctly.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testParseCredentialsJwtClaimsIncludeIds(final VertxTestContext ctx) {

        final String authId = "authId";
        final String tenantId = "tenantId";
        final String jwt = "header.claims.signature";

        when(auth.getUsername()).thenReturn("");
        when(auth.getPassword()).thenReturn(jwt);
        when(endpoint.clientIdentifier()).thenReturn("client identifier");
        when(authTokenValidator.getJwtClaims(jwt)).thenReturn(claims);
        when(claims.getString(JwtAuthHandler.CLAIM_TENANT_ID)).thenReturn(tenantId);
        when(claims.getString(Claims.SUBJECT)).thenReturn(authId);
        when(claims.getString(Claims.AUDIENCE)).thenReturn(JwtAuthHandler.AUDIENCE_HONO_ADAPTER);
        when(claims.getString(Claims.ISSUER)).thenReturn(authId);

        final MqttConnectContext context = MqttConnectContext.fromConnectPacket(endpoint, span);
        authHandler.parseCredentials(context)
                .onComplete(ctx.succeeding(info -> {
                    ctx.verify(() -> {
                        assertThat(info.getString(Claims.ISSUER)).isEqualTo(authId);
                        assertThat(info.getString(CredentialsConstants.FIELD_AUTH_ID)).isEqualTo(authId);
                        assertThat(info.getString(CredentialsConstants.FIELD_PAYLOAD_TENANT_ID)).isEqualTo(tenantId);
                        assertThat(info.getString(CredentialsConstants.FIELD_PASSWORD)).isEqualTo(jwt);
                    });
                    ctx.completeNow();
                }));
    }

    /**
     * Verifies that the handler returns the correct Exception in case the
     * {@value CredentialsConstants#FIELD_PAYLOAD_TENANT_ID} and {@value CredentialsConstants#FIELD_AUTH_ID} got
     * extracted from the JWT claims, but they are missing the correct {@value Claims#AUDIENCE} claim.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testParseCredentialsJwtClaimsIncludeIdsButMissCorrectAud(final VertxTestContext ctx) {

        final String authId = "authId";
        final String tenantId = "tenantId";
        final String jwt = "header.claims.signature";

        when(auth.getUsername()).thenReturn("");
        when(auth.getPassword()).thenReturn(jwt);
        when(endpoint.clientIdentifier()).thenReturn("client identifier");
        when(authTokenValidator.getJwtClaims(jwt)).thenReturn(claims);
        when(claims.getString(JwtAuthHandler.CLAIM_TENANT_ID)).thenReturn(tenantId);
        when(claims.getString(Claims.SUBJECT)).thenReturn(authId);
        when(claims.getString(Claims.AUDIENCE)).thenReturn("invalid aud claim");

        final MqttConnectContext context = MqttConnectContext.fromConnectPacket(endpoint, span);
        authHandler.parseCredentials(context)
                .onComplete(ctx.failing(t -> {
                    ctx.verify(() -> assertThat(t).isInstanceOf(ClientErrorException.class));
                    ctx.completeNow();
                }));
    }

    /**
     * Verifies that the handler returns the correct Exception in case the
     * {@value CredentialsConstants#FIELD_PAYLOAD_TENANT_ID} and {@value CredentialsConstants#FIELD_AUTH_ID} could
     * neither be extracted from the MQTT client identifier nor the JWT claims.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testParseCredentialsMalformedMqttClientIdAndJwtClaims(final VertxTestContext ctx) {

        final String authId = "authId";
        final String tenantId = "tenantId";
        final String jwt = "header.claims.signature";

        when(auth.getUsername()).thenReturn("");
        when(auth.getPassword()).thenReturn(jwt);
        when(endpoint.clientIdentifier()).thenReturn(String.format("tenants.%s.devices.%s", tenantId, authId));
        // no aud claim
        when(authTokenValidator.getJwtClaims(jwt)).thenReturn(claims);

        final MqttConnectContext context = MqttConnectContext.fromConnectPacket(endpoint, span);
        authHandler.parseCredentials(context)
                .onComplete(ctx.failing(t -> {
                    ctx.verify(() -> assertThat(t).isInstanceOf(ClientErrorException.class));
                    ctx.completeNow();
                }));
    }

    /**
     * Verifies that the handler returns the correct Exception in case the password retrieved from a device's CONNECT
     * packet is not in a valid JWT format (header.claims.signature).
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testParseCredentialsMalformedJWT(final VertxTestContext ctx) {

        final String authId = "authId";
        final String tenantId = "tenantId";
        final String jwt = "jwt";

        when(auth.getUsername()).thenReturn("");
        when(auth.getPassword()).thenReturn(jwt);
        when(endpoint.clientIdentifier()).thenReturn(String.format("tenants/%s/devices/%s", tenantId, authId));
        when(authTokenValidator.getJwtClaims(anyString())).thenThrow(new MalformedJwtException("invalid JWS"));

        final MqttConnectContext context = MqttConnectContext.fromConnectPacket(endpoint, span);
        authHandler.parseCredentials(context)
                .onComplete(ctx.failing(t -> {
                    ctx.verify(() -> assertThat(t).isInstanceOf(ClientErrorException.class));
                    ctx.completeNow();
                }));
    }

    /**
     * Verifies that the handler returns the correct Exception in case the device endpoint provides a MqttAuth that is
     * null.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testParseCredentialsMqttAuthNull(final VertxTestContext ctx) {

        final String authId = "authId";
        final String tenantId = "tenantId";
        final String jwt = "jwt";

        when(auth.getUsername()).thenReturn("");
        when(auth.getPassword()).thenReturn(jwt);
        when(endpoint.clientIdentifier()).thenReturn(String.format("tenants/%s/devices/%s", tenantId, authId));
        when(endpoint.auth()).thenReturn(null);
        when(authTokenValidator.getJwtClaims(jwt)).thenReturn(claims);

        final MqttConnectContext context = MqttConnectContext.fromConnectPacket(endpoint, span);
        authHandler.parseCredentials(context)
                .onComplete(ctx.failing(t -> {
                    ctx.verify(() -> assertThat(t).isInstanceOf(ClientErrorException.class));
                    ctx.completeNow();
                }));
    }
}
