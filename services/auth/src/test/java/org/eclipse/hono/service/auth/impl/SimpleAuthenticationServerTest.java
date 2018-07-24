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
import java.util.Map;

import org.apache.qpid.proton.amqp.Symbol;
import org.apache.qpid.proton.engine.impl.RecordImpl;
import org.eclipse.hono.auth.Activity;
import org.eclipse.hono.auth.AuthoritiesImpl;
import org.eclipse.hono.auth.HonoUser;
import org.eclipse.hono.util.Constants;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import io.vertx.core.Context;
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
    @SuppressWarnings("unchecked")
    @Test
    public void testProcessRemoteOpenAddsClientAuthorities() {

        final Symbol addressAutzSymbol = Symbol.getSymbol("ADDRESS-AUTHZ");
        final AuthoritiesImpl authorities = new AuthoritiesImpl();
        authorities.addResource("telemetry/DEFAULT_TENANT", Activity.READ);
        final HonoUser client = mock(HonoUser.class);
        when(client.getAuthorities()).thenReturn(authorities);
        when(client.getExpirationTime()).thenReturn(Instant.now().plusSeconds(60));
        final RecordImpl attachments = new RecordImpl();
        final ProtonConnection con = mock(ProtonConnection.class);
        when(con.getRemoteDesiredCapabilities()).thenReturn(new Symbol[] { addressAutzSymbol });
        when(con.attachments()).thenReturn(attachments);
        when(con.getRemoteContainer()).thenReturn("client container");
        when(client.getName()).thenReturn("application X");
        Constants.setClientPrincipal(con, client);
        final Vertx vertx = mock(Vertx.class);
        final SimpleAuthenticationServer server = new SimpleAuthenticationServer();
        server.init(vertx, mock(Context.class));

        server.processRemoteOpen(con);
        final ArgumentCaptor<Symbol[]> offeredCapabilitiesCaptor = ArgumentCaptor.forClass(Symbol[].class);
        verify(con).setOfferedCapabilities(offeredCapabilitiesCaptor.capture());
        assertTrue(Arrays.stream(offeredCapabilitiesCaptor.getValue()).anyMatch(symbol -> symbol.equals(addressAutzSymbol)));
        final ArgumentCaptor<Map<Symbol, Object>> propsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(con).setProperties(propsCaptor.capture());
        final Map<String, String[]> authz = (Map<String, String[]>) propsCaptor.getValue().get(Symbol.getSymbol("address-authz"));
        assertNotNull(authz);
        assertThat(authz.get("telemetry/DEFAULT_TENANT"), is(new String[] { "recv" }));
    }

}
