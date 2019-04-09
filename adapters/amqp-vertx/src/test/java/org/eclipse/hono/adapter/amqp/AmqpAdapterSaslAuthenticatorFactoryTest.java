/**
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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


package org.eclipse.hono.adapter.amqp;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.HttpURLConnection;
import java.util.function.Function;
import java.util.function.Supplier;

import org.apache.qpid.proton.engine.Record;
import org.apache.qpid.proton.engine.Sasl;
import org.apache.qpid.proton.engine.Sasl.SaslOutcome;
import org.apache.qpid.proton.engine.Transport;
import org.apache.qpid.proton.engine.impl.RecordImpl;
import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.TenantClient;
import org.eclipse.hono.client.TenantClientFactory;
import org.eclipse.hono.config.ProtocolAdapterProperties;
import org.eclipse.hono.service.auth.DeviceUser;
import org.eclipse.hono.service.auth.device.HonoClientBasedAuthProvider;
import org.eclipse.hono.service.auth.device.UsernamePasswordCredentials;
import org.eclipse.hono.service.limiting.ConnectionLimitManager;
import org.eclipse.hono.util.AuthenticationConstants;
import org.eclipse.hono.util.TenantObject;
import org.junit.Before;
import org.junit.Test;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.NetSocket;
import io.vertx.proton.ProtonConnection;
import io.vertx.proton.sasl.ProtonSaslAuthenticator;

/**
 * Tests verifying behavior of {@link AmqpAdapterSaslAuthenticatorFactory}.
 *
 */
public class AmqpAdapterSaslAuthenticatorFactoryTest {

    private ConnectionLimitManager adapterConnectionLimit;
    private TenantClient tenantClient;
    private TenantClientFactory tenantClientFactory;
    private ProtocolAdapterProperties props;
    private HonoClientBasedAuthProvider<UsernamePasswordCredentials> authProvider;
    private Supplier<Span> spanFactory;
    private ProtonConnection con;

    /**
     * Sets up the fixture.
     */
    @SuppressWarnings("unchecked")
    @Before
    public void setUp() {

        props = new ProtocolAdapterProperties();
        adapterConnectionLimit = mock(ConnectionLimitManager.class);
        when(adapterConnectionLimit.isLimitExceeded()).thenReturn(Boolean.FALSE);

        final TenantObject tenant = TenantObject.from("tenant", true);
        tenantClient = mock(TenantClient.class);
        when(tenantClient.get(anyString(), any(SpanContext.class))).thenReturn(Future.succeededFuture(tenant));
        tenantClientFactory = mock(TenantClientFactory.class);
        when(tenantClientFactory.getOrCreateTenantClient()).thenReturn(Future.succeededFuture(tenantClient));

        authProvider = mock(HonoClientBasedAuthProvider.class);
        doAnswer(invocation -> {
            final Handler<AsyncResult<DeviceUser>> resultHandler = invocation.getArgument(2);
            resultHandler.handle(Future.succeededFuture(new DeviceUser("tenant", "4711")));
            return null;
        }).when(authProvider).authenticate(any(UsernamePasswordCredentials.class), any(SpanContext.class), any(Handler.class));

        final SpanContext spanContext = mock(SpanContext.class);
        final Span span = mock(Span.class);
        when(span.context()).thenReturn(spanContext);
        spanFactory = () -> span;

        final Record attachments = new RecordImpl();
        con = mock(ProtonConnection.class);
        when(con.attachments()).thenReturn(attachments);
    }

    private Buffer getPlainCredentials(final String username, final String password) {

        final Buffer credentials = Buffer.buffer();
        credentials.appendByte((byte) 0x00);
        credentials.appendString(username);
        credentials.appendByte((byte) 0x00);
        credentials.appendString(password);

        return credentials;
    }

    private Sasl getSasl(final Buffer credentials) {
        final Sasl sasl = mock(Sasl.class);
        when(sasl.getRemoteMechanisms()).thenReturn(new String[] { AuthenticationConstants.MECHANISM_PLAIN });
        when(sasl.pending()).thenReturn(credentials.length());
        when(sasl.recv(any(byte[].class), anyInt(), anyInt())).thenAnswer(invocation -> {
            final byte[] buffer = (byte[]) invocation.getArgument(0);
            credentials.getBytes(buffer);
            return credentials.length();
        });
        return sasl;
    }

    /**
     * Verifies that the SASL authenticator created by the factory fails
     * a SASL handshake with outcome AUTH if the tenant's overall connection
     * limit has been reached.
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testHandshakeFailsIfTenantLevelConnectionLimitIsExceeded() {

        // GIVEN a device belonging to a tenant for which the connection limit has been reached
        final Function<TenantObject, Future<Void>> tenantConnectionLimit = tenant -> Future.failedFuture(
                new ClientErrorException(HttpURLConnection.HTTP_FORBIDDEN, "maximum number of connections exceeded"));
        final Buffer credentials = getPlainCredentials("sensor1@tenant", "secret");

        // WHEN the device starts a SASL PLAIN handshake
        final AmqpAdapterSaslAuthenticatorFactory factory = new AmqpAdapterSaslAuthenticatorFactory(
                tenantClientFactory,
                props,
                spanFactory,
                adapterConnectionLimit,
                tenantConnectionLimit,
                authProvider,
                null);

        final ProtonSaslAuthenticator authenticator = factory.create();
        final Handler<Boolean> completionHandler = mock(Handler.class);
        final Sasl sasl = getSasl(credentials);
        final Transport transport = mock(Transport.class);
        when(transport.sasl()).thenReturn(sasl);
        authenticator.init(mock(NetSocket.class), con, transport);
        authenticator.process(completionHandler);

        // THEN the handshake fails with the SASL_AUTH outcome
        verify(completionHandler).handle(Boolean.TRUE);
        verify(sasl).done(SaslOutcome.PN_SASL_AUTH);
    }

    /**
     * Verifies that the SASL authenticator created by the factory fails
     * a SASL handshake with outcome AUTH if the adapter instance's connection
     * limit has been reached.
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testHandshakeFailsIfAdapterLevelConnectionLimitIsExceeded() {

        // GIVEN a device
        final Buffer credentials = getPlainCredentials("sensor1@tenant", "secret");

        // WHEN the device starts a SASL PLAIN handshake with an adapter
        // whose connection limit has been reached
        when(adapterConnectionLimit.isLimitExceeded()).thenReturn(Boolean.TRUE);
        final Function<TenantObject, Future<Void>> tenantConnectionLimit = tenant -> Future.succeededFuture();
        final AmqpAdapterSaslAuthenticatorFactory factory = new AmqpAdapterSaslAuthenticatorFactory(
                tenantClientFactory,
                props,
                spanFactory,
                adapterConnectionLimit,
                tenantConnectionLimit,
                authProvider,
                null);

        final ProtonSaslAuthenticator authenticator = factory.create();
        final Handler<Boolean> completionHandler = mock(Handler.class);
        final Sasl sasl = getSasl(credentials);
        final Transport transport = mock(Transport.class);
        when(transport.sasl()).thenReturn(sasl);
        authenticator.init(mock(NetSocket.class), con, transport);
        authenticator.process(completionHandler);

        // THEN the handshake fails with the SASL_TEMP outcome
        verify(completionHandler).handle(Boolean.TRUE);
        verify(sasl).done(SaslOutcome.PN_SASL_TEMP);
    }
}
