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

package org.eclipse.hono.service.command;

import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Objects;

import org.apache.qpid.proton.engine.impl.RecordImpl;
import org.eclipse.hono.client.MessageConsumer;
import org.eclipse.hono.config.ClientConfigProperties;
import org.eclipse.hono.connection.ConnectionFactory;
import org.eclipse.hono.service.auth.device.Device;
import org.eclipse.hono.util.Constants;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.ext.unit.Async;
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

    private static Vertx vertx;
    private ClientConfigProperties props;
    private ProtonConnection con;

    private CommandConnection commandConnection;
    private ConnectionFactory connectionFactory;
    private ProtonReceiver receiver;

    /**
     * Sets up vertx.
     */
    @BeforeClass
    public static void setUpVertx() {
        vertx = Vertx.vertx();
    }

    /**
     * Sets up fixture.
     */
    @Before
    public void setUp() {
        props = new ClientConfigProperties();

        receiver = mock(ProtonReceiver.class);
        when(receiver.attachments()).thenReturn(new RecordImpl());
        doAnswer(invocationOnMock -> {
            final Handler<AsyncResult<ProtonReceiver>> receiverHandler = invocationOnMock.getArgument(0);
            receiverHandler.handle(Future.succeededFuture(receiver));
            return receiver;
        }).when(receiver).openHandler(any(Handler.class));

        con = mock(ProtonConnection.class);
        when(con.isDisconnected()).thenReturn(Boolean.FALSE);
        when(con.createReceiver(anyString())).thenReturn(receiver);

        connectionFactory = new ConnectionResultHandlerProvidingConnectionFactory(con);
        commandConnection = new CommandConnectionImpl(vertx, connectionFactory, props);
    }

    /**
     * Verifies that a command consumer can be created for a tenant and deviceId and opens a receiver link
     * that is scoped to the device.
     *
     * @param ctx The test context.
     */
    @Test
    public void testCreateCommandConsumerSucceedsAndOpensReceiverLink(final TestContext ctx) {

        final Handler<CommandContext> commandHandler = mock(Handler.class);
        final Handler<Void> closeHandler = mock(Handler.class);

        final Async connected = ctx.async();

        commandConnection.connect(new ProtonClientOptions()).setHandler(ctx.asyncAssertSuccess(ok -> connected.complete()));
        connected.await();

        final Future<MessageConsumer> messageConsumerFuture =
                commandConnection.getOrCreateCommandConsumer("theTenant", "theDevice", commandHandler, closeHandler);
        assertNotNull(messageConsumerFuture);

        final Async consumerCreated = ctx.async();
        messageConsumerFuture.setHandler(ctx.asyncAssertSuccess(ok ->
                consumerCreated.complete()));
        consumerCreated.await();

        verify(receiver).open();
        verify(con).createReceiver(contains(Device.asAddress("theTenant", "theDevice")));
    }


    /**
     * A connection factory that provides access to the connection result handler registered with
     * a connection passed to the factory.
     */
    private static class ConnectionResultHandlerProvidingConnectionFactory implements ConnectionFactory {

        private final ProtonConnection connectionToCreate;

        ConnectionResultHandlerProvidingConnectionFactory(final ProtonConnection conToCreate) {
            this.connectionToCreate = Objects.requireNonNull(conToCreate);
        }

        @Override
        public void connect(
                final ProtonClientOptions options,
                final Handler<AsyncResult<ProtonConnection>> closeHandler,
                final Handler<ProtonConnection> disconnectHandler,
                final Handler<AsyncResult<ProtonConnection>> connectionResultHandler) {
            connect(options, null, null, closeHandler, disconnectHandler, connectionResultHandler);
        }

        @Override
        public void connect(
                final ProtonClientOptions options,
                final String username,
                final String password,
                final Handler<AsyncResult<ProtonConnection>> closeHandler,
                final Handler<ProtonConnection> disconnectHandler,
                final Handler<AsyncResult<ProtonConnection>> connectionResultHandler) {

            connectionResultHandler.handle(Future.succeededFuture(connectionToCreate));
        }

        @Override
        public String getName() {
            return "client";
        }

        @Override
        public String getHost() {
            return "server";
        }

        @Override
        public int getPort() {
            return Constants.PORT_AMQP;
        }

        @Override
        public String getPathSeparator() {
            return Constants.DEFAULT_PATH_SEPARATOR;
        }
    }

}
