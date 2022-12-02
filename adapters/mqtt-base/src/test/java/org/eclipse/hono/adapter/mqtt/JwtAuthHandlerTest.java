/**
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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

import org.eclipse.hono.adapter.auth.device.jwt.JwtAuthProvider;
import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.test.TracingMockSupport;
import org.eclipse.hono.util.CredentialsConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.opentracing.Span;
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

    /**
     * Sets up the fixture.
     */
    @BeforeEach
    public void setUp() {
        auth = mock(MqttAuth.class);
        endpoint = mock(MqttEndpoint.class);
        span = TracingMockSupport.mockSpan();
        authHandler = new JwtAuthHandler(mock(JwtAuthProvider.class));
    }

    /**
     * Verifies that the handler includes a valid MQTT client identifier in the authentication information retrieved
     * from a device's CONNECT packet.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testParseCredentialsIncludesMqttClientId(final VertxTestContext ctx) {

        final String authId = "authId";
        final String tenantId = "tenantId";
        final String jwt = "header.claims.signature";

        when(auth.getUsername()).thenReturn("");
        when(auth.getPassword()).thenReturn(jwt);

        when(endpoint.auth()).thenReturn(auth);
        when(endpoint.clientIdentifier()).thenReturn(String.format("Tenant/%s/Device/%s", tenantId, authId));

        final MqttConnectContext context = MqttConnectContext.fromConnectPacket(endpoint, span);
        authHandler.parseCredentials(context)
                .onComplete(ctx.succeeding(info -> {
                    ctx.verify(() -> {
                        assertThat(info.getString(CredentialsConstants.FIELD_AUTH_ID)).isEqualTo(authId);
                        assertThat(info.getString(CredentialsConstants.FIELD_PAYLOAD_TENANT_ID)).isEqualTo(tenantId);
                        assertThat(info.getString(CredentialsConstants.FIELD_PASSWORD)).isEqualTo(jwt);
                    });
                    ctx.completeNow();
                }));
    }

    /**
     * Verifies that the handler returns the correct Exception in case the MQTT client identifier in the authentication
     * information retrieved from a device's CONNECT packet is malformed.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testParseCredentialsMalformedMqttClientId(final VertxTestContext ctx) {

        final String authId = "authId";
        final String tenantId = "tenantId";
        final String jwt = "header.claims.signature";

        when(auth.getUsername()).thenReturn("");
        when(auth.getPassword()).thenReturn(jwt);

        when(endpoint.auth()).thenReturn(auth);
        when(endpoint.clientIdentifier()).thenReturn(String.format("Tenant.%s.Device.%s", tenantId, authId));

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

        when(endpoint.auth()).thenReturn(auth);
        when(endpoint.clientIdentifier()).thenReturn(String.format("Tenant/%s/Device/%s", tenantId, authId));

        final MqttConnectContext context = MqttConnectContext.fromConnectPacket(endpoint, span);
        authHandler.parseCredentials(context)
                .onComplete(ctx.failing(t -> {
                    ctx.verify(() -> assertThat(t).isInstanceOf(ClientErrorException.class));
                    ctx.completeNow();
                }));
    }
}
