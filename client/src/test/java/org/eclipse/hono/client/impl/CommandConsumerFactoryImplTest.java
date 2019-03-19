/*******************************************************************************
 * Copyright (c) 2016, 2019 Contributors to the Eclipse Foundation
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

import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.HttpURLConnection;

import org.apache.qpid.proton.amqp.transport.AmqpError;
import org.apache.qpid.proton.amqp.transport.ErrorCondition;
import org.apache.qpid.proton.amqp.transport.Source;
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
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.proton.ProtonConnection;
import io.vertx.proton.ProtonReceiver;


/**
 * Verifies behavior of {@link CommandConsumerFactoryImpl}.
 *
 */
@RunWith(VertxUnitRunner.class)
public class CommandConsumerFactoryImplTest {

    /**
     * Global timeout for each test case.
     */
    @Rule
    public Timeout timeout = Timeout.seconds(3);

    private Vertx vertx;
    private Context context;
    private ClientConfigProperties props;
    private ProtonConnection con;

    private CommandConsumerFactoryImpl factory;
    private DisconnectHandlerProvidingConnectionFactory connectionFactory;
    private ProtonReceiver receiver;

    /**
     * Sets up fixture.
     */
    @SuppressWarnings("unchecked")
    @Before
    public void setUp() {

        vertx = mock(Vertx.class);
        context = HonoClientUnitTestHelper.mockContext(vertx);
        when(vertx.getOrCreateContext()).thenReturn(context);
        doAnswer(invocation -> {
            final Handler<Void> handler = invocation.getArgument(1);
            handler.handle(null);
            return null;
        }).when(vertx).setTimer(anyLong(), any(Handler.class));

        props = new ClientConfigProperties();

        receiver = mock(ProtonReceiver.class);
        con = mock(ProtonConnection.class);
        when(con.createReceiver(anyString())).thenReturn(receiver);

        connectionFactory = new DisconnectHandlerProvidingConnectionFactory(con);
        factory = new CommandConsumerFactoryImpl(vertx, connectionFactory, props);
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

        factory.connect()
            .compose(c -> {
                final ArgumentCaptor<Handler<AsyncResult<ProtonReceiver>>> linkOpenHandler = ArgumentCaptor.forClass(Handler.class);
                final Future<MessageConsumer> consumer = factory.createCommandConsumer("theTenant", "theDevice", commandHandler, closeHandler);
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
     * Verifies that the connection successfully opens a command consumer for a
     * tenant and device Id and opens a receiver link that is scoped to the device.
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
        when(receiver.getSource()).thenReturn(source);
        when(receiver.getRemoteSource()).thenReturn(source);

        factory.connect()
            .compose(c -> {
                final ArgumentCaptor<Handler<AsyncResult<ProtonReceiver>>> linkOpenHandler = ArgumentCaptor.forClass(Handler.class);
                final Future<MessageConsumer> consumer = factory.createCommandConsumer("theTenant", "theDevice", commandHandler, closeHandler);
                verify(con).createReceiver(address);
                verify(receiver).openHandler(linkOpenHandler.capture());
                verify(receiver).open();
                linkOpenHandler.getValue().handle(Future.succeededFuture(receiver));
                return consumer;
            }).setHandler(ctx.asyncAssertSuccess());
    }

    /**
     * Verifies that the close handler passed as an argument when creating
     * a command consumer is invoked when the peer closes the link.
     *
     * @param ctx The test context.
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testCreateCommandConsumerSetsRemoteCloseHandler(final TestContext ctx) {

        final String address = "control/theTenant/theDevice";
        final Handler<CommandContext> commandHandler = mock(Handler.class);
        final Handler<Void> closeHandler = mock(Handler.class);
        final Source source = mock(Source.class);
        when(source.getAddress()).thenReturn(address);
        when(receiver.getSource()).thenReturn(source);
        when(receiver.getRemoteSource()).thenReturn(source);

        // GIVEN a command consumer for which a close handler
        // has been registered
        factory.connect()
            .compose(c -> {
                final Future<MessageConsumer> consumer = factory.createCommandConsumer("theTenant", "theDevice", commandHandler, closeHandler);
                verify(con).createReceiver(address);
                final ArgumentCaptor<Handler<AsyncResult<ProtonReceiver>>> linkOpenHandler = ArgumentCaptor.forClass(Handler.class);
                verify(receiver).openHandler(linkOpenHandler.capture());
                verify(receiver).open();
                linkOpenHandler.getValue().handle(Future.succeededFuture(receiver));
                return consumer;
            }).map(c -> {
                final ArgumentCaptor<Handler<AsyncResult<ProtonReceiver>>> remoteCloseHandler = ArgumentCaptor.forClass(Handler.class);
                verify(receiver).closeHandler(remoteCloseHandler.capture());
                // WHEN the peer closes the link
                remoteCloseHandler.getValue().handle(Future.succeededFuture(receiver));
                // THEN the close handler is invoked
                verify(closeHandler).handle(null);
                return c;
            }).setHandler(ctx.asyncAssertSuccess());
    }

    /**
     * Verifies that a command consumer's <em>close</em> method is invoked,
     * then
     * <ul>
     * <li>the underlying link is closed,</li>
     * <li>the consumer is removed from the cache and</li>
     * <li>the corresponding liveness check is canceled.</li>
     * </ul>
     *
     * @param ctx The test context.
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testLocalCloseRemovesCommandConsumerFromCache(final TestContext ctx) {

        final String address = "control/theTenant/theDevice";
        final Handler<CommandContext> commandHandler = mock(Handler.class);
        final Source source = mock(Source.class);
        when(source.getAddress()).thenReturn(address);
        when(receiver.getSource()).thenReturn(source);
        when(receiver.getRemoteSource()).thenReturn(source);
        when(vertx.setPeriodic(anyLong(), any(Handler.class))).thenReturn(10L);

        // GIVEN a command consumer
        factory.connect()
            .compose(client -> {
                final Future<MessageConsumer> consumer = factory.createCommandConsumer("theTenant", "theDevice", commandHandler, null, 5000L);
                verify(con).createReceiver(address);
                final ArgumentCaptor<Handler<AsyncResult<ProtonReceiver>>> linkOpenHandler = ArgumentCaptor.forClass(Handler.class);
                verify(receiver).closeHandler(any(Handler.class));
                verify(receiver).openHandler(linkOpenHandler.capture());
                verify(receiver).open();
                linkOpenHandler.getValue().handle(Future.succeededFuture(receiver));
                when(receiver.isOpen()).thenReturn(Boolean.TRUE);
                verify(vertx).setPeriodic(eq(5000L), any(Handler.class));
                return consumer;
            }).map(consumer -> {
                // WHEN closing the link locally
                final Future<Void> localCloseHandler = Future.future();
                consumer.close(localCloseHandler.completer());
                final ArgumentCaptor<Handler<AsyncResult<ProtonReceiver>>> closeHandler = ArgumentCaptor.forClass(Handler.class);
                verify(receiver, times(2)).closeHandler(closeHandler.capture());
                verify(receiver).close();
                // and the peer sends its detach frame
                closeHandler.getValue().handle(Future.succeededFuture(receiver));
                return null;
            }).map(ok -> {
                // THEN the liveness check is canceled
                verify(vertx).cancelTimer(10L);
                // and the next attempt to create a command consumer for the same address
                final Future<MessageConsumer> newConsumer = factory.createCommandConsumer("theTenant", "theDevice", commandHandler, null);
                // results in a new link to be opened
                verify(con, times(2)).createReceiver(address);
                final ArgumentCaptor<Handler<AsyncResult<ProtonReceiver>>> linkOpenHandler = ArgumentCaptor.forClass(Handler.class);
                verify(receiver, times(2)).openHandler(linkOpenHandler.capture());
                linkOpenHandler.getValue().handle(Future.succeededFuture(receiver));
                return newConsumer;
            }).setHandler(ctx.asyncAssertSuccess());
    }

    /**
     * Verifies that a command consumer link that has been created with a check
     * interval is re-created if the underlying connection to the peer is lost.
     *
     * @param ctx The test context.
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testConsumerIsRecreatedOnConnectionFailure(final TestContext ctx) {

        final String address = "control/theTenant/theDevice";
        final Handler<CommandContext> commandHandler = mock(Handler.class);
        final Handler<Void> closeHandler = mock(Handler.class);
        final Source source = mock(Source.class);
        when(source.getAddress()).thenReturn(address);
        when(receiver.getSource()).thenReturn(source);
        when(receiver.getRemoteSource()).thenReturn(source);
        when(vertx.setPeriodic(anyLong(), any(Handler.class))).thenReturn(10L);

        // GIVEN a command connection with an established command consumer
        // which is checked periodically for liveness
        factory.connect().setHandler(ctx.asyncAssertSuccess());
        assertTrue(connectionFactory.await());
        connectionFactory.setExpectedSucceedingConnectionAttempts(1);

        // intentionally using a check interval that is smaller than the minimum
        final long livenessCheckInterval = CommandConsumerFactoryImpl.MIN_LIVENESS_CHECK_INTERVAL_MILLIS - 1;
        final Async consumerCreation = ctx.async();
        final Future<MessageConsumer> commandConsumer = factory.createCommandConsumer(
                "theTenant", "theDevice", commandHandler, closeHandler, eq(livenessCheckInterval));
        commandConsumer.setHandler(ctx.asyncAssertSuccess(ok -> consumerCreation.complete()));
        verify(con).createReceiver(address);
        final ArgumentCaptor<Handler<AsyncResult<ProtonReceiver>>> linkOpenHandler = ArgumentCaptor.forClass(Handler.class);
        verify(receiver).openHandler(linkOpenHandler.capture());
        verify(receiver).open();
        linkOpenHandler.getValue().handle(Future.succeededFuture(receiver));
        final ArgumentCaptor<Handler<Long>> livenessCheck = ArgumentCaptor.forClass(Handler.class);
        // the liveness check is registered with the minimum interval length
        verify(vertx).setPeriodic(eq(CommandConsumerFactoryImpl.MIN_LIVENESS_CHECK_INTERVAL_MILLIS), livenessCheck.capture());
        consumerCreation.await();

        // WHEN the command connection fails
        connectionFactory.getDisconnectHandler().handle(con);

        // THEN the connection is re-established
        assertTrue(connectionFactory.await());
        // and the liveness check re-creates the command consumer
        final Async consumerRecreation = ctx.async();
        when(receiver.open()).thenAnswer(invocation -> {
            consumerRecreation.complete();
            return receiver;
        });
        livenessCheck.getValue().handle(10L);
        verify(con, times(2)).createReceiver(address);
        verify(receiver, times(2)).openHandler(any(Handler.class));
        verify(receiver, times(2)).open();
        consumerRecreation.await();

        // and when the consumer is finally closed
        final Future<Void> localCloseHandler = mock(Future.class);
        commandConsumer.result().close(localCloseHandler);
        // then the liveness check has been canceled
        verify(vertx).cancelTimer(10L);
    }

    /**
     * Verifies that consecutive invocations of the liveness check created
     * for a command consumer do not start a new re-creation attempt if another
     * attempt is still ongoing.
     *
     * @param ctx The test context.
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testLivenessCheckLocksRecreationAttempt(final TestContext ctx) {

        // GIVEN a liveness check for a command consumer
        final String address = "control/theTenant/theDevice";
        final Handler<CommandContext> commandHandler = mock(Handler.class);
        final Handler<Void> remoteCloseHandler = mock(Handler.class);

        // GIVEN an established command connection
        factory.connect().setHandler(ctx.asyncAssertSuccess());
        assertTrue(connectionFactory.await());
        connectionFactory.setExpectedSucceedingConnectionAttempts(1);

        final Handler<Long> livenessCheck = factory.newLivenessCheck("theTenant", "theDevice", "key", commandHandler, remoteCloseHandler);

        // WHEN the liveness check fires
        livenessCheck.handle(10L);
        // and the peer does not open the link before the check fires again
        livenessCheck.handle(10L);

        // THEN only one attempt has been made to recreate the consumer link
        verify(con, times(1)).createReceiver(address);

        // and when the first attempt has finally timed out
        when(receiver.getRemoteCondition()).thenReturn(new ErrorCondition(AmqpError.INTERNAL_ERROR, "internal error"));
        final ArgumentCaptor<Handler<AsyncResult<ProtonReceiver>>> linkOpenHandler = ArgumentCaptor.forClass(Handler.class);
        verify(receiver).openHandler(linkOpenHandler.capture());
        linkOpenHandler.getValue().handle(Future.failedFuture("internal error"));

        // then the next run of the liveness check
        livenessCheck.handle(10L);
        // will start a new attempt to re-create the consumer link 
        verify(con, times(2)).createReceiver(address);
    }
}
