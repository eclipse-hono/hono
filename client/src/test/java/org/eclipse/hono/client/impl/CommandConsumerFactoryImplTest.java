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

import static org.eclipse.hono.client.impl.VertxMockSupport.anyHandler;
import static org.eclipse.hono.client.impl.VertxMockSupport.argumentCaptorHandler;
import static org.eclipse.hono.client.impl.VertxMockSupport.mockHandler;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.HttpURLConnection;

import org.apache.qpid.proton.amqp.transport.Source;
import org.eclipse.hono.client.CommandContext;
import org.eclipse.hono.client.DisconnectListener;
import org.eclipse.hono.client.GatewayMapper;
import org.eclipse.hono.client.HonoConnection;
import org.eclipse.hono.client.MessageConsumer;
import org.eclipse.hono.client.ServerErrorException;
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.config.ClientConfigProperties;
import org.eclipse.hono.util.CommandConstants;
import org.eclipse.hono.util.ResourceIdentifier;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.proton.ProtonMessageHandler;
import io.vertx.proton.ProtonQoS;
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
    public Timeout timeout = Timeout.seconds(300);

    private Vertx vertx;
    private Context context;
    private ClientConfigProperties props;
    private HonoConnection connection;
    private CommandConsumerFactoryImpl commandConsumerFactory;
    private GatewayMapper gatewayMapper;
    private ProtonReceiver deviceSpecificCommandReceiver;
    private ProtonReceiver tenantScopedCommandReceiver;
    private String deviceSpecificCommandAddress;
    private String tenantCommandAddress;
    private String tenantId;
    private String deviceId;

    /**
     * Sets up fixture.
     */
    @Before
    public void setUp() {

        vertx = mock(Vertx.class);
        context = HonoClientUnitTestHelper.mockContext(vertx);
        when(vertx.getOrCreateContext()).thenReturn(context);
        doAnswer(invocation -> {
            final Handler<Void> handler = invocation.getArgument(1);
            handler.handle(null);
            return null;
        }).when(vertx).setTimer(anyLong(), anyHandler());

        deviceId = "theDevice";
        tenantId = "theTenant";

        props = new ClientConfigProperties();

        connection = HonoClientUnitTestHelper.mockHonoConnection(vertx, props);
        deviceSpecificCommandReceiver = mock(ProtonReceiver.class);
        deviceSpecificCommandAddress = ResourceIdentifier.from(CommandConstants.NORTHBOUND_COMMAND_LEGACY_ENDPOINT, tenantId, deviceId).toString();
        when(connection.createReceiver(
                eq(deviceSpecificCommandAddress),
                any(ProtonQoS.class),
                any(ProtonMessageHandler.class),
                anyInt(),
                anyBoolean(),
                anyHandler())).thenReturn(Future.succeededFuture(deviceSpecificCommandReceiver));
        tenantScopedCommandReceiver = mock(ProtonReceiver.class);
        tenantCommandAddress = ResourceIdentifier.from(CommandConstants.NORTHBOUND_COMMAND_REQUEST_ENDPOINT, tenantId, null).toString();
        when(connection.createReceiver(
                eq(tenantCommandAddress),
                any(ProtonQoS.class),
                any(ProtonMessageHandler.class),
                anyInt(),
                anyBoolean(),
                anyHandler())).thenReturn(Future.succeededFuture(tenantScopedCommandReceiver));
        gatewayMapper = mock(GatewayMapper.class);
        commandConsumerFactory = new CommandConsumerFactoryImpl(connection, gatewayMapper);
    }

    /**
     * Verifies that an attempt to open a command consumer fails if the peer
     * rejects to open a receiver link.
     *
     * @param ctx The test context.
     */
    @Test
    public void testCreateCommandConsumerFailsIfPeerRejectsLink(final TestContext ctx) {

        final Handler<CommandContext> commandHandler = mockHandler();
        final Handler<Void> closeHandler = mockHandler();
        final ServerErrorException ex = new ServerErrorException(HttpURLConnection.HTTP_UNAVAILABLE);
        when(connection.createReceiver(
                anyString(),
                any(ProtonQoS.class),
                any(ProtonMessageHandler.class),
                anyInt(),
                anyBoolean(),
                anyHandler()))
        .thenReturn(Future.failedFuture(ex));

        commandConsumerFactory.createCommandConsumer(tenantId, deviceId, commandHandler, closeHandler)
        .setHandler(ctx.asyncAssertFailure(t -> {
                ctx.assertEquals(HttpURLConnection.HTTP_UNAVAILABLE, ((ServiceInvocationException) t).getErrorCode());
            }));
    }

    /**
     * Verifies that the connection successfully opens a command consumer for a
     * tenant and device Id and opens a receiver link that is scoped to the device.
     *
     * @param ctx The test context.
     */
    @Test
    public void testCreateCommandConsumerSucceeds(final TestContext ctx) {

        final Handler<CommandContext> commandHandler = mockHandler();
        final Handler<Void> closeHandler = mockHandler();

        commandConsumerFactory.createCommandConsumer(tenantId, deviceId, commandHandler, closeHandler)
        .setHandler(ctx.asyncAssertSuccess());
    }

    /**
     * Verifies that the close handler passed as an argument when creating
     * a command consumer is invoked when the peer closes the link.
     *
     * @param ctx The test context.
     */
    @Test
    public void testCreateCommandConsumerSetsRemoteCloseHandler(final TestContext ctx) {

        final Handler<CommandContext> commandHandler = mockHandler();
        final Handler<Void> closeHandler = mockHandler();

        commandConsumerFactory.createCommandConsumer(tenantId, deviceId, commandHandler, closeHandler);
        final ArgumentCaptor<Handler<String>> captor = argumentCaptorHandler();
        verify(connection).createReceiver(
                eq(deviceSpecificCommandAddress),
                eq(ProtonQoS.AT_LEAST_ONCE),
                any(ProtonMessageHandler.class),
                eq(0),
                eq(false),
                captor.capture());
        captor.getValue().handle(deviceSpecificCommandAddress);
        verify(closeHandler).handle(null);
    }

    /**
     * Verifies that when a command consumer's <em>close</em> method is invoked,
     * then
     * <ul>
     * <li>the underlying link is closed,</li>
     * <li>the consumer is removed from the cache and</li>
     * <li>the corresponding liveness check is canceled.</li>
     * </ul>
     *
     * @param ctx The test context.
     */
    @Test
    public void testLocalCloseRemovesCommandConsumerFromCache(final TestContext ctx) {

        final Handler<CommandContext> commandHandler = mockHandler();
        final Source source = mock(Source.class);
        when(source.getAddress()).thenReturn(deviceSpecificCommandAddress);
        when(deviceSpecificCommandReceiver.getSource()).thenReturn(source);
        when(deviceSpecificCommandReceiver.getRemoteSource()).thenReturn(source);
        when(deviceSpecificCommandReceiver.isOpen()).thenReturn(Boolean.TRUE);
        when(vertx.setPeriodic(anyLong(), anyHandler())).thenReturn(10L);

        // GIVEN a command consumer
        commandConsumerFactory.createCommandConsumer(tenantId, deviceId, commandHandler, null, 5000L)
        .map(consumer -> {
                    verify(vertx).setPeriodic(eq(5000L), anyHandler());
            // WHEN closing the link locally
            final Future<Void> localCloseHandler = Future.future();
            consumer.close(localCloseHandler);
                    final ArgumentCaptor<Handler<Void>> closeHandler = argumentCaptorHandler();
            verify(connection).closeAndFree(eq(deviceSpecificCommandReceiver), closeHandler.capture());
            // and the peer sends its detach frame
            closeHandler.getValue().handle(null);
            return null;
        }).map(ok -> {
            // THEN the liveness check is canceled
            verify(vertx).cancelTimer(10L);
            // and the next attempt to create a command consumer for the same address
            final Future<MessageConsumer> newConsumer = commandConsumerFactory.createCommandConsumer(tenantId, deviceId, commandHandler, null);
            // results in a new link to be opened
            verify(connection, times(2)).createReceiver(
                    eq(deviceSpecificCommandAddress),
                    eq(ProtonQoS.AT_LEAST_ONCE),
                    any(ProtonMessageHandler.class),
                    eq(0),
                    eq(false),
                    anyHandler());
            return newConsumer;
        }).setHandler(ctx.asyncAssertSuccess());
    }

    /**
     * Verifies that a command consumer link that has been created with a check
     * interval is re-created if the underlying connection to the peer is lost.
     *
     * @param ctx The test context.
     */
    @Test
    public void testConsumerIsRecreatedOnConnectionFailure(final TestContext ctx) {

        final Handler<CommandContext> commandHandler = mockHandler();
        final Handler<Void> closeHandler = mockHandler();
        final Source source = mock(Source.class);
        when(source.getAddress()).thenReturn(deviceSpecificCommandAddress);
        when(deviceSpecificCommandReceiver.getSource()).thenReturn(source);
        when(deviceSpecificCommandReceiver.getRemoteSource()).thenReturn(source);
        when(vertx.setPeriodic(anyLong(), anyHandler())).thenReturn(10L);
        doAnswer(invocation -> {
            final Handler<Void> handler = invocation.getArgument(1);
            handler.handle(null);
            return null;
        }).when(connection).closeAndFree(any(), anyHandler());

        // GIVEN a command connection with an established command consumer
        // which is checked periodically for liveness

        // intentionally using a check interval that is smaller than the minimum
        final long livenessCheckInterval = CommandConsumerFactoryImpl.MIN_LIVENESS_CHECK_INTERVAL_MILLIS - 1;
        final Future<MessageConsumer> commandConsumer = commandConsumerFactory.createCommandConsumer(
                tenantId, deviceId, commandHandler, closeHandler, livenessCheckInterval);
        assertTrue(commandConsumer.isComplete());
        final ArgumentCaptor<Handler<Long>> livenessCheck = argumentCaptorHandler();
        // the liveness check is registered with the minimum interval length
        verify(vertx).setPeriodic(eq(CommandConsumerFactoryImpl.MIN_LIVENESS_CHECK_INTERVAL_MILLIS), livenessCheck.capture());

        // WHEN the command connection fails
        @SuppressWarnings("unchecked")
        final ArgumentCaptor<DisconnectListener<HonoConnection>> disconnectListener = ArgumentCaptor.forClass(DisconnectListener.class);
        verify(connection).addDisconnectListener(disconnectListener.capture());
        disconnectListener.getValue().onDisconnect(connection);

        // THEN the connection is re-established
        when(connection.isConnected()).thenReturn(Future.succeededFuture());
        // and the liveness check re-creates the command consumer
        livenessCheck.getValue().handle(10L);
        verify(connection, times(2)).createReceiver(
                eq(deviceSpecificCommandAddress),
                eq(ProtonQoS.AT_LEAST_ONCE),
                any(ProtonMessageHandler.class),
                eq(0),
                eq(false),
                anyHandler());

        // and when the consumer is finally closed locally
        commandConsumer.result().close(null);
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
    @Test
    public void testLivenessCheckLocksRecreationAttempt(final TestContext ctx) {

        // GIVEN a liveness check for a command consumer
        final Handler<CommandContext> commandHandler = mockHandler();
        final Handler<Void> remoteCloseHandler = mockHandler();
        final Handler<Long> livenessCheck = commandConsumerFactory.newLivenessCheck(tenantId, deviceId, "key", commandHandler, remoteCloseHandler);
        final Future<ProtonReceiver> createdReceiver = Future.future();
        when(connection.isConnected()).thenReturn(Future.succeededFuture());
        when(connection.createReceiver(
                eq(deviceSpecificCommandAddress),
                eq(ProtonQoS.AT_LEAST_ONCE),
                any(ProtonMessageHandler.class),
                anyInt(),
                anyBoolean(),
                anyHandler())).thenReturn(createdReceiver);

        // WHEN the liveness check fires
        livenessCheck.handle(10L);
        // and the peer does not open the link before the check fires again
        livenessCheck.handle(10L);

        // THEN only one attempt has been made to recreate the consumer link
        verify(connection, times(1)).createReceiver(
                eq(deviceSpecificCommandAddress),
                eq(ProtonQoS.AT_LEAST_ONCE),
                any(ProtonMessageHandler.class),
                eq(0),
                eq(false),
                anyHandler());

        // and when the first attempt has finally timed out
        createdReceiver.fail(new ServerErrorException(HttpURLConnection.HTTP_UNAVAILABLE));

        // then the next run of the liveness check
        livenessCheck.handle(10L);
        // will start a new attempt to re-create the consumer link 
        verify(connection, times(2)).createReceiver(
                eq(deviceSpecificCommandAddress),
                eq(ProtonQoS.AT_LEAST_ONCE),
                any(ProtonMessageHandler.class),
                eq(0),
                eq(false),
                anyHandler());
    }
}
