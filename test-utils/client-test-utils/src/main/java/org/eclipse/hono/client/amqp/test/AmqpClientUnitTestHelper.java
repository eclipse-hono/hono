/*******************************************************************************
 * Copyright (c) 2020, 2021 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.client.amqp.test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Objects;

import org.apache.qpid.proton.amqp.transport.Target;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.HonoConnection;
import org.eclipse.hono.config.ClientConfigProperties;
import org.eclipse.hono.test.VertxMockSupport;
import org.mockito.ArgumentCaptor;

import io.opentracing.Tracer;
import io.opentracing.noop.NoopTracerFactory;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.proton.ProtonConnection;
import io.vertx.proton.ProtonLink;
import io.vertx.proton.ProtonMessageHandler;
import io.vertx.proton.ProtonQoS;
import io.vertx.proton.ProtonReceiver;
import io.vertx.proton.ProtonSender;
import io.vertx.proton.ProtonSession;

/**
 * Helper class that provides mocks for objects that are needed by unit tests for
 * AMQP based clients.
 *
 */
public final class AmqpClientUnitTestHelper {

    private AmqpClientUnitTestHelper() {
        // prevent instantiation
    }

    /**
     * Asserts that a message has been sent via a given sender link.
     *
     * @param sender The sender link.
     * @return The message that has been sent.
     * @throws NullPointerException if sender is {@code null}.
     * @throws AssertionError if no message has been sent.
     */
    public static Message assertMessageHasBeenSent(final ProtonSender sender) {
        Objects.requireNonNull(sender);
        final ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(sender).send(messageCaptor.capture(), VertxMockSupport.anyHandler());
        return messageCaptor.getValue();
    }

    /**
     * Asserts that a receiver link has been created for a connection.
     *
     * @param connection The connection.
     * @return The message handler that has been set on the receiver link.
     * @throws NullPointerException if connection is {@code null}.
     * @throws AssertionError if no link has been created.
     */
    public static ProtonMessageHandler assertReceiverLinkCreated(final HonoConnection connection) {
        final ArgumentCaptor<ProtonMessageHandler> messageHandler = ArgumentCaptor.forClass(ProtonMessageHandler.class);
        verify(connection).createReceiver(anyString(), any(ProtonQoS.class), messageHandler.capture(), VertxMockSupport.anyHandler());
        return messageHandler.getValue();
    }

    /**
     * Creates a mocked Proton sender which always returns {@code true} when its isOpen method is called
     * and whose connection is not disconnected.
     *
     * @return The mocked sender.
     */
    public static ProtonSender mockProtonSender() {

        final ProtonSender sender = mock(ProtonSender.class);
        when(sender.isOpen()).thenReturn(Boolean.TRUE);
        when(sender.getQoS()).thenReturn(ProtonQoS.AT_LEAST_ONCE);
        when(sender.getTarget()).thenReturn(mock(Target.class));
        mockLinkConnection(sender);

        return sender;
    }

    /**
     * Creates a mocked Proton receiver which always returns {@code true} when its isOpen method is called
     * and whose connection is not disconnected.
     *
     * @return The mocked receiver.
     */
    public static ProtonReceiver mockProtonReceiver() {

        final ProtonReceiver receiver = mock(ProtonReceiver.class);
        when(receiver.isOpen()).thenReturn(Boolean.TRUE);
        mockLinkConnection(receiver);

        return receiver;
    }

    private static void mockLinkConnection(final ProtonLink<?> link) {
        final ProtonConnection connection = mock(ProtonConnection.class);
        when(connection.isDisconnected()).thenReturn(false);
        final ProtonSession session = mock(ProtonSession.class);
        when(session.getConnection()).thenReturn(connection);
        when(link.getSession()).thenReturn(session);
    }

    /**
     * Creates a mocked Hono connection that returns a
     * Noop Tracer.
     * <p>
     * Invokes {@link #mockHonoConnection(Vertx, ClientConfigProperties)}
     * with default {@link ClientConfigProperties}.
     *
     * @param vertx The vert.x instance to use.
     * @return The connection.
     */
    public static HonoConnection mockHonoConnection(final Vertx vertx) {
        return mockHonoConnection(vertx, new ClientConfigProperties());
    }

    /**
     * Creates a mocked Hono connection that returns a
     * Noop Tracer.
     *
     * @param vertx The vert.x instance to use.
     * @param props The client properties to use.
     * @return The connection.
     */
    public static HonoConnection mockHonoConnection(final Vertx vertx, final ClientConfigProperties props) {
        return mockHonoConnection(vertx, props, NoopTracerFactory.create());
    }

    /**
     * Creates a mocked Hono connection.
     *
     * @param vertx The vert.x instance to use.
     * @param props The client properties to use.
     * @param tracer The tracer to use.
     * @return The connection.
     */
    public static HonoConnection mockHonoConnection(
            final Vertx vertx,
            final ClientConfigProperties props,
            final Tracer tracer) {

        final HonoConnection connection = mock(HonoConnection.class);
        when(connection.getVertx()).thenReturn(vertx);
        when(connection.getConfig()).thenReturn(props);
        when(connection.getTracer()).thenReturn(tracer);
        when(connection.executeOnContext(VertxMockSupport.anyHandler())).then(invocation -> {
            final Promise<?> result = Promise.promise();
            final Handler<Future<?>> handler = invocation.getArgument(0);
            handler.handle(result.future());
            return result.future();
        });
        when(connection.isConnected()).thenReturn(Future.succeededFuture());
        when(connection.isConnected(anyLong())).thenReturn(Future.succeededFuture());
        return connection;
    }
}
