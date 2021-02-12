/*******************************************************************************
 * Copyright (c) 2016, 2020 Contributors to the Eclipse Foundation
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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.apache.qpid.proton.amqp.transport.Target;
import org.eclipse.hono.client.HonoConnection;
import org.eclipse.hono.config.ClientConfigProperties;
import org.eclipse.hono.test.VertxMockSupport;

import io.opentracing.Tracer;
import io.opentracing.noop.NoopTracerFactory;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.proton.ProtonConnection;
import io.vertx.proton.ProtonLink;
import io.vertx.proton.ProtonQoS;
import io.vertx.proton.ProtonReceiver;
import io.vertx.proton.ProtonSender;
import io.vertx.proton.ProtonSession;

/**
 * Helper class that provides mocks for objects that are needed by unit tests using a Hono client.
 *
 */
public final class HonoClientUnitTestHelper {

    private HonoClientUnitTestHelper() {
        // prevent instantiation
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
        return connection;
    }
}
