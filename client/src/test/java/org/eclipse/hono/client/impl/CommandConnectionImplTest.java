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

package org.eclipse.hono.client.impl;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.HttpURLConnection;

import org.apache.qpid.proton.amqp.transport.Source;
import org.eclipse.hono.client.CommandConnection;
import org.eclipse.hono.client.CommandContext;
import org.eclipse.hono.client.MessageConsumer;
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.config.ClientConfigProperties;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.proton.ProtonClientOptions;
import io.vertx.proton.ProtonConnection;
import io.vertx.proton.ProtonReceiver;


/**
 * Verifies behavior of {@link CommandConnectionImpl}.
 *
 */
@RunWith(VertxUnitRunner.class)
public class CommandConnectionImplTest {

    /**
     * Global timeout for each test case.
     */
    @Rule
    public Timeout timeout = Timeout.seconds(3);

    private Vertx vertx;
    private Context context;
    private ClientConfigProperties props;
    private ProtonConnection con;

    private CommandConnection commandConnection;
    private DisconnectHandlerProvidingConnectionFactory connectionFactory;
    private ProtonReceiver receiver;

    /**
     * Sets up fixture.
     */
    @SuppressWarnings("unchecked")
    @Before
    public void setUp() {

        vertx = mock(Vertx.class);
        context = mock(Context.class);
        when(vertx.getOrCreateContext()).thenReturn(context);
        when(context.owner()).thenReturn(vertx);
        doAnswer(invocation -> {
            final Handler<Void> handler = invocation.getArgument(0);
            handler.handle(null);
            return null;
        }).when(context).runOnContext(any(Handler.class));

        props = new ClientConfigProperties();

        receiver = mock(ProtonReceiver.class);
        con = mock(ProtonConnection.class);
        when(con.createReceiver(anyString())).thenReturn(receiver);

        connectionFactory = new DisconnectHandlerProvidingConnectionFactory(con);
        commandConnection = new CommandConnectionImpl(vertx, connectionFactory, props);
    }

    /**
     * Verifies that an attempt to open a command consumer fails if the peer
     * rejects to open a receiver link.
     *
     * @param ctx The test context.
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testCreateCommandConsumerFailsIfPeerRejectsLink(final TestContext ctx) {

        final Handler<CommandContext> commandHandler = mock(Handler.class);
        final Handler<Void> closeHandler = mock(Handler.class);
        final Source source = mock(Source.class);
        when(source.getAddress()).thenReturn(null);
        when(receiver.getRemoteSource()).thenReturn(source);

        commandConnection.connect(new ProtonClientOptions())
            .compose(c -> {
                final ArgumentCaptor<Handler<AsyncResult<ProtonReceiver>>> linkOpenHandler = ArgumentCaptor.forClass(Handler.class);
                final Future<MessageConsumer> consumer = commandConnection.getOrCreateCommandConsumer("theTenant", "theDevice", commandHandler, closeHandler);
                verify(con).createReceiver("control/theTenant/theDevice");
                verify(receiver).openHandler(linkOpenHandler.capture());
                verify(receiver).open();
                linkOpenHandler.getValue().handle(Future.succeededFuture());
                return consumer;
            }).setHandler(ctx.asyncAssertFailure(t -> {
                ctx.assertEquals(HttpURLConnection.HTTP_UNAVAILABLE, ((ServiceInvocationException) t).getErrorCode());
            }));
    }

    /**
     * Verifies that an attempt to open a command consumer for a
     * tenant and device Id succeeds if the peer agrees to open a
     * corresponding receiver link that is scoped to the device.
     *
     * @param ctx The test context.
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testCreateCommandConsumerSucceeds(final TestContext ctx) {

        final String address = "control/theTenant/theDevice";
        final Handler<CommandContext> commandHandler = mock(Handler.class);
        final Handler<Void> closeHandler = mock(Handler.class);
        final Source source = mock(Source.class);
        when(source.getAddress()).thenReturn(address);
        when(receiver.getRemoteSource()).thenReturn(source);

        commandConnection.connect(new ProtonClientOptions())
            .compose(c -> {
                final ArgumentCaptor<Handler<AsyncResult<ProtonReceiver>>> linkOpenHandler = ArgumentCaptor.forClass(Handler.class);
                final Future<MessageConsumer> consumer = commandConnection.getOrCreateCommandConsumer("theTenant", "theDevice", commandHandler, closeHandler);
                verify(con).createReceiver(address);
                verify(receiver).openHandler(linkOpenHandler.capture());
                verify(receiver).open();
                linkOpenHandler.getValue().handle(Future.succeededFuture(receiver));
                return consumer;
            }).setHandler(ctx.asyncAssertSuccess());
    }
}
