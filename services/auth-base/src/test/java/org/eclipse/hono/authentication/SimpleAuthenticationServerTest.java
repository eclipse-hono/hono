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

package org.eclipse.hono.authentication;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static com.google.common.truth.Truth.assertThat;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

import org.apache.qpid.proton.amqp.Symbol;
import org.apache.qpid.proton.engine.impl.RecordImpl;
import org.eclipse.hono.auth.Activity;
import org.eclipse.hono.auth.AuthoritiesImpl;
import org.eclipse.hono.auth.HonoUser;
import org.eclipse.hono.client.amqp.connection.AmqpUtils;
import org.eclipse.hono.service.auth.AddressAuthzHelper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.proton.ProtonConnection;


/**
 * Tests verifying behavior of {@link SimpleAuthenticationServer}.
 *
 */
public class SimpleAuthenticationServerTest {

    /**
     * Verifies that the server processes a client's request to get
     * its granted authorities.
     */
    @Test
    public void testProcessRemoteOpenAddsClientAuthorities() {

        testProcessRemoteOpenAddsClientAuthorities("1.4", this::assertClientIdentity);
    }

    /**
     * Verifies that the server processes a legacy client's request to get
     * its granted authorities.
     */
    @Test
    public void testProcessRemoteOpenAddsLegacyClientAuthorities() {

        testProcessRemoteOpenAddsClientAuthorities("1.3.9", this::assertLegacyClientIdentity);
    }

    @SuppressWarnings("unchecked")
    private void testProcessRemoteOpenAddsClientAuthorities(
            final String version,
            final Handler<Map<Symbol, Object>> connectionPropertiesAssertion) {

        final AuthoritiesImpl authorities = new AuthoritiesImpl();
        authorities.addResource("telemetry/DEFAULT_TENANT", Activity.READ);
        final HonoUser client = mock(HonoUser.class);
        when(client.getAuthorities()).thenReturn(authorities);
        when(client.getExpirationTime()).thenReturn(Instant.now().plusSeconds(60));
        when(client.getName()).thenReturn("application X");

        final Map<Symbol, Object> properties = Collections.singletonMap(AddressAuthzHelper.PROPERTY_CLIENT_VERSION, version);
        final RecordImpl attachments = new RecordImpl();
        final ProtonConnection con = mock(ProtonConnection.class);
        when(con.getRemoteDesiredCapabilities()).thenReturn(new Symbol[] { AddressAuthzHelper.CAPABILITY_ADDRESS_AUTHZ });
        when(con.attachments()).thenReturn(attachments);
        when(con.getRemoteContainer()).thenReturn("client container");
        when(con.getRemoteProperties()).thenReturn(properties);
        AmqpUtils.setClientPrincipal(con, client);
        final Vertx vertx = mock(Vertx.class);
        final SimpleAuthenticationServer server = new SimpleAuthenticationServer();
        server.init(vertx, mock(Context.class));

        server.processRemoteOpen(con);
        final ArgumentCaptor<Symbol[]> offeredCapabilitiesCaptor = ArgumentCaptor.forClass(Symbol[].class);
        verify(con).setOfferedCapabilities(offeredCapabilitiesCaptor.capture());
        assertThat(Arrays.stream(offeredCapabilitiesCaptor.getValue()).anyMatch(symbol -> symbol.equals(AddressAuthzHelper.CAPABILITY_ADDRESS_AUTHZ))).isTrue();
        final ArgumentCaptor<Map<Symbol, Object>> propsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(con).setProperties(propsCaptor.capture());
        final Map<String, String[]> authz = (Map<String, String[]>) propsCaptor.getValue().get(AddressAuthzHelper.PROPERTY_ADDRESS_AUTHZ);
        assertThat(authz).isNotNull();
        assertThat(authz.get("telemetry/DEFAULT_TENANT")).isEqualTo(new String[] { "recv" });
        connectionPropertiesAssertion.handle(propsCaptor.getValue());
    }

    @SuppressWarnings("unchecked")
    private void assertClientIdentity(final Map<Symbol, Object> connectionProperties) {
        final Map<String, Object> authenticatedIdentity = (Map<String, Object>) connectionProperties.get(AddressAuthzHelper.PROPERTY_AUTH_IDENTITY);
        assertThat(authenticatedIdentity.get("sub")).isEqualTo("application X");
    }

    private void assertLegacyClientIdentity(final Map<Symbol, Object> connectionProperties) {
        assertThat(connectionProperties.get(AddressAuthzHelper.PROPERTY_AUTH_IDENTITY)).isEqualTo("application X");
    }

}
