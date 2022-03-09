/**
 * Copyright (c) 2021, 2022 Contributors to the Eclipse Foundation
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


package org.eclipse.hono.service.auth;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.function.Consumer;

import org.apache.qpid.proton.engine.Sasl;
import org.apache.qpid.proton.engine.Sasl.SaslOutcome;
import org.apache.qpid.proton.engine.Sasl.SaslState;
import org.apache.qpid.proton.engine.Transport;
import org.apache.qpid.proton.engine.impl.RecordImpl;
import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.service.auth.AuthenticationService.AuthenticationAttemptOutcome;
import org.eclipse.hono.test.VertxMockSupport;
import org.junit.jupiter.api.Test;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.NetSocket;
import io.vertx.proton.ProtonConnection;


/**
 * Tests verifying behavior of {@link HonoSaslAuthenticator}.
 *
 */
public class HonoSaslAuthenticatorTest {

    /**
     * Verifies that the SASL handshake is being finished with outcome SASL_AUTH
     * if the client's credentials cannot be verified.
     */
    @Test
    public void testProcessFinishesHandshakeOnAuthenticationFailure() {
        final var authService = mock(AuthenticationService.class);
        when(authService.authenticate(any(JsonObject.class)))
            .thenReturn(Future.failedFuture(new ClientErrorException(401, "no such user")));
        @SuppressWarnings("unchecked")
        final Consumer<AuthenticationService.AuthenticationAttemptOutcome> meter = mock(Consumer.class);
        final NetSocket socket = mock(NetSocket.class);
        final ProtonConnection con = mock(ProtonConnection.class);
        when(con.attachments()).thenReturn(new RecordImpl());
        final Sasl sasl = mock(Sasl.class);
        when(sasl.getRemoteMechanisms()).thenReturn(new String[] { "PLAIN" });
        when(sasl.getHostname()).thenReturn("localhost");
        when(sasl.getState()).thenReturn(SaslState.PN_SASL_STEP);

        final Transport transport = mock(Transport.class);
        when(transport.sasl()).thenReturn(sasl);
        final var authenticator = new HonoSaslAuthenticator(authService, meter);
        authenticator.init(socket, con, transport);

        final Handler<Boolean> completionHandler = VertxMockSupport.mockHandler();
        authenticator.process(completionHandler);
        verify(authService).authenticate(any(JsonObject.class));
        verify(completionHandler).handle(Boolean.TRUE);
        verify(sasl).done(SaslOutcome.PN_SASL_AUTH);
        verify(meter).accept(AuthenticationAttemptOutcome.UNAUTHORIZED);
    }
}
