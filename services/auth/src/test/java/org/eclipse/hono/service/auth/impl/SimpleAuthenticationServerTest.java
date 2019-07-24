/*******************************************************************************
 * Copyright (c) 2016, 2018 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.service.auth.impl;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

import org.apache.qpid.proton.amqp.Symbol;
import org.apache.qpid.proton.engine.impl.RecordImpl;
import org.eclipse.hono.auth.Activity;
import org.eclipse.hono.auth.AuthoritiesImpl;
import org.eclipse.hono.auth.HonoUser;
import org.eclipse.hono.util.Constants;
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

        final Map<Symbol, Object> properties = Collections.singletonMap(SimpleAuthenticationServer.PROPERTY_CLIENT_VERSION, version);
        final RecordImpl attachments = new RecordImpl();
        final ProtonConnection con = mock(ProtonConnection.class);
        when(con.getRemoteDesiredCapabilities()).thenReturn(new Symbol[] { SimpleAuthenticationServer.CAPABILITY_ADDRESS_AUTHZ });
        when(con.attachments()).thenReturn(attachments);
        when(con.getRemoteContainer()).thenReturn("client container");
        when(con.getRemoteProperties()).thenReturn(properties);
        Constants.setClientPrincipal(con, client);
        final Vertx vertx = mock(Vertx.class);
        final SimpleAuthenticationServer server = new SimpleAuthenticationServer();
        server.init(vertx, mock(Context.class));

        server.processRemoteOpen(con);
        final ArgumentCaptor<Symbol[]> offeredCapabilitiesCaptor = ArgumentCaptor.forClass(Symbol[].class);
        verify(con).setOfferedCapabilities(offeredCapabilitiesCaptor.capture());
        assertTrue(Arrays.stream(offeredCapabilitiesCaptor.getValue()).anyMatch(symbol -> symbol.equals(SimpleAuthenticationServer.CAPABILITY_ADDRESS_AUTHZ)));
        final ArgumentCaptor<Map<Symbol, Object>> propsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(con).setProperties(propsCaptor.capture());
        final Map<String, String[]> authz = (Map<String, String[]>) propsCaptor.getValue().get(SimpleAuthenticationServer.PROPERTY_ADDRESS_AUTHZ);
        assertNotNull(authz);
        assertThat(authz.get("telemetry/DEFAULT_TENANT"), is(new String[] { "recv" }));
        connectionPropertiesAssertion.handle(propsCaptor.getValue());
    }

    @SuppressWarnings("unchecked")
    private void assertClientIdentity(final Map<Symbol, Object> connectionProperties) {
        final Map<String, Object> authenticatedIdentity = (Map<String, Object>) connectionProperties.get(SimpleAuthenticationServer.PROPERTY_AUTH_IDENTITY);
        assertThat(authenticatedIdentity.get("sub"), is("application X"));
    }

    private void assertLegacyClientIdentity(final Map<Symbol, Object> connectionProperties) {
        assertThat(connectionProperties.get(SimpleAuthenticationServer.PROPERTY_AUTH_IDENTITY), is("application X"));
    }

}
