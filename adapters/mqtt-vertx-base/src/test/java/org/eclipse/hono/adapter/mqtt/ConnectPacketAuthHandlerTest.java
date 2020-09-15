/**
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
 */


package org.eclipse.hono.adapter.mqtt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import javax.net.ssl.SSLPeerUnverifiedException;

import org.eclipse.hono.service.auth.device.DeviceCredentialsAuthProvider;
import org.eclipse.hono.service.auth.device.UsernamePasswordCredentials;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.mqtt.MqttAuth;
import io.vertx.mqtt.MqttEndpoint;


/**
 * Tests verifying behavior of {@link ConnectPacketAuthHandler}.
 *
 */
@ExtendWith(VertxExtension.class)
public class ConnectPacketAuthHandlerTest {

    private ConnectPacketAuthHandler authHandler;
    private DeviceCredentialsAuthProvider<UsernamePasswordCredentials> authProvider;
    private Span span;

    /**
     * Sets up the fixture.
     */
    @SuppressWarnings("unchecked")
    @BeforeEach
    public void setUp() {
        authProvider = mock(DeviceCredentialsAuthProvider.class);
        authHandler = new ConnectPacketAuthHandler(authProvider);

        span = mock(Span.class);
        final SpanContext spanContext = mock(SpanContext.class);
        when(span.context()).thenReturn(spanContext);
    }

    /**
     * Verifies that the handler includes the MQTT client identifier in the authentication
     * information retrieved from a device's CONNECT packet.
     *
     * @param ctx The vert.x test context.
     * @throws SSLPeerUnverifiedException if the client certificate cannot be determined.
     */
    @Test
    public void testParseCredentialsIncludesMqttClientId(final VertxTestContext ctx) throws SSLPeerUnverifiedException {

        // GIVEN an auth handler configured with an auth provider

        // WHEN trying to authenticate a device using a username and password
        final MqttAuth auth = mock(MqttAuth.class);
        when(auth.getUsername()).thenReturn("sensor1@DEFAULT_TENANT");
        when(auth.getPassword()).thenReturn("secret");

        final MqttEndpoint endpoint = mock(MqttEndpoint.class);
        when(endpoint.auth()).thenReturn(auth);
        when(endpoint.clientIdentifier()).thenReturn("mqtt-device");

        final MqttContext context = MqttContext.fromConnectPacket(endpoint, span);
        authHandler.parseCredentials(context)
            // THEN the auth info is correctly retrieved from the client certificate
            .onComplete(ctx.succeeding(info -> {
                ctx.verify(() -> {
                    assertThat(info.getString("username")).isEqualTo("sensor1@DEFAULT_TENANT");
                    assertThat(info.getString("password")).isEqualTo("secret");
                    assertThat(info.getString(X509AuthHandler.PROPERTY_CLIENT_IDENTIFIER)).isEqualTo("mqtt-device");
                });
                ctx.completeNow();
            }));
    }
}
