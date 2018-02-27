/**
 * Copyright (c) 2018 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 1.0 which is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * SPDX-License-Identifier: EPL-1.0
 */

package org.eclipse.hono.client.impl;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.*;

import java.net.HttpURLConnection;

import org.apache.qpid.proton.amqp.transport.AmqpError;
import org.apache.qpid.proton.amqp.transport.ErrorCondition;
import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.config.ClientConfigProperties;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.proton.ProtonConnection;
import io.vertx.proton.ProtonQoS;
import io.vertx.proton.ProtonReceiver;
import io.vertx.proton.ProtonSender;


/**
 * Tests verifying behavior of {@link AbstractHonoClient}.
 *
 */
@RunWith(VertxUnitRunner.class)
public class AbstractHonoClientTest {

    /**
     * Time out each test after five seconds.
     */
    @Rule
    public final Timeout timeout = Timeout.seconds(5);

    private Context context;
    private ClientConfigProperties props;

    /**
     * Sets up the fixture.
     */
    @SuppressWarnings("unchecked")
    @Before
    public void setUp() {
        props = new ClientConfigProperties();
        context = mock(Context.class);
        doAnswer(invocation -> {
            final Handler<Void> handler = invocation.getArgumentAt(0, Handler.class);
            handler.handle(null);
            return null;
        }).when(context).runOnContext(any(Handler.class));
    }

    /**
     * Verifies that the attempt to create a sender fails with a
     * {@code ServiceInvocationException} if the remote peer refuses
     * to open the link.
     * 
     * @param ctx The vert.x test context.
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Test
    public void testCreateSenderFailsForErrorCondition(final TestContext ctx) {

        final ProtonSender sender = mock(ProtonSender.class);
        when(sender.getRemoteCondition()).thenReturn(new ErrorCondition(AmqpError.RESOURCE_LIMIT_EXCEEDED, "unauthorized"));
        final ProtonConnection con = mock(ProtonConnection.class);
        when(con.createSender(anyString())).thenReturn(sender);

        final Future<ProtonSender> result = AbstractHonoClient.createSender(context, props, con,
                "target", ProtonQoS.AT_LEAST_ONCE, null);

        final ArgumentCaptor<Handler> openHandler = ArgumentCaptor.forClass(Handler.class);
        verify(sender).openHandler(openHandler.capture());
        openHandler.getValue().handle(Future.failedFuture(new IllegalStateException()));
        assertTrue(result.failed());
        assertThat(result.cause(), instanceOf(ServiceInvocationException.class));
    }

    /**
     * Verifies that the attempt to create a sender fails with a
     * {@code ServiceInvocationException} if the remote peer refuses
     * to open the link.
     * 
     * @param ctx The vert.x test context.
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Test
    public void testCreateSenderFailsWithoutErrorCondition(final TestContext ctx) {

        final ProtonSender sender = mock(ProtonSender.class);
        final ProtonConnection con = mock(ProtonConnection.class);
        when(con.createSender(anyString())).thenReturn(sender);

        final Future<ProtonSender> result = AbstractHonoClient.createSender(context, props, con,
                "target", ProtonQoS.AT_LEAST_ONCE, null);

        final ArgumentCaptor<Handler> openHandler = ArgumentCaptor.forClass(Handler.class);
        verify(sender).openHandler(openHandler.capture());
        openHandler.getValue().handle(Future.failedFuture(new IllegalStateException()));
        assertTrue(result.failed());
        assertThat(((ClientErrorException) result.cause()).getErrorCode(), is(HttpURLConnection.HTTP_NOT_FOUND));
    }

    /**
     * Verifies that the attempt to create a receiver fails with a
     * {@code ServiceInvocationException} if the remote peer refuses
     * to open the link.
     * 
     * @param ctx The vert.x test context.
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Test
    public void testCreateReceiverFailsForErrorCondition(final TestContext ctx) {

        final ProtonReceiver sender = mock(ProtonReceiver.class);
        when(sender.getRemoteCondition()).thenReturn(new ErrorCondition(AmqpError.RESOURCE_LIMIT_EXCEEDED, "unauthorized"));
        final ProtonConnection con = mock(ProtonConnection.class);
        when(con.createReceiver(anyString())).thenReturn(sender);

        final Future<ProtonReceiver> result = AbstractHonoClient.createReceiver(context, props, con,
                "source", ProtonQoS.AT_LEAST_ONCE, (delivery, msg) -> {}, null);

        final ArgumentCaptor<Handler> openHandler = ArgumentCaptor.forClass(Handler.class);
        verify(sender).openHandler(openHandler.capture());
        openHandler.getValue().handle(Future.failedFuture(new IllegalStateException()));
        assertTrue(result.failed());
        assertThat(result.cause(), instanceOf(ServiceInvocationException.class));
    }

    /**
     * Verifies that the attempt to create a receiver fails with a
     * {@code ServiceInvocationException} if the remote peer refuses
     * to open the link.
     * 
     * @param ctx The vert.x test context.
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Test
    public void testCreateReceiverFailsWithoutErrorCondition(final TestContext ctx) {

        final ProtonReceiver sender = mock(ProtonReceiver.class);
        final ProtonConnection con = mock(ProtonConnection.class);
        when(con.createReceiver(anyString())).thenReturn(sender);

        final Future<ProtonReceiver> result = AbstractHonoClient.createReceiver(context, props, con,
                "source", ProtonQoS.AT_LEAST_ONCE, (delivery, msg) -> {}, null);

        final ArgumentCaptor<Handler> openHandler = ArgumentCaptor.forClass(Handler.class);
        verify(sender).openHandler(openHandler.capture());
        openHandler.getValue().handle(Future.failedFuture(new IllegalStateException()));
        assertTrue(result.failed());
        assertThat(((ClientErrorException) result.cause()).getErrorCode(), is(HttpURLConnection.HTTP_NOT_FOUND));
    }
}
