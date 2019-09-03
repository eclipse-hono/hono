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

import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.Tracer.SpanBuilder;
import io.opentracing.noop.NoopTracerFactory;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.proton.ProtonQoS;
import io.vertx.proton.ProtonReceiver;
import io.vertx.proton.ProtonSender;

import static org.eclipse.hono.client.impl.VertxMockSupport.anyHandler;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.apache.qpid.proton.amqp.transport.Target;
import org.eclipse.hono.client.HonoConnection;
import org.eclipse.hono.config.ClientConfigProperties;
import org.mockito.Mockito;

/**
 * Helper class that provides mocks for objects that are needed by unit tests using a Hono client.
 *
 */
public final class HonoClientUnitTestHelper {

    private HonoClientUnitTestHelper() {}

    /**
     * Creates a mocked vert.x Context which immediately invokes any handler that is passed to its runOnContext method.
     *
     * @param vertx The vert.x instance that the mock of the context is created for.
     * @return The mocked context.
     */
    public static Context mockContext(final Vertx vertx) {

        final Context context = mock(Context.class);

        when(context.owner()).thenReturn(vertx);
        doAnswer(invocation -> {
            final Handler<Void> handler = invocation.getArgument(0);
            handler.handle(null);
            return null;
        }).when(context).runOnContext(anyHandler());
        return context;
    }

    /**
     * Creates a mocked Proton sender which always returns {@code true} when its isOpen method is called.
     *
     * @return The mocked sender.
     */
    public static ProtonSender mockProtonSender() {

        final ProtonSender sender = mock(ProtonSender.class);
        when(sender.isOpen()).thenReturn(Boolean.TRUE);
        when(sender.getQoS()).thenReturn(ProtonQoS.AT_LEAST_ONCE);
        when(sender.getTarget()).thenReturn(mock(Target.class));

        return sender;
    }

    /**
     * Creates a mocked Proton receiver which always returns {@code true} when its isOpen method is called.
     *
     * @return The mocked receiver.
     */
    public static ProtonReceiver mockProtonReceiver() {

        final ProtonReceiver receiver = mock(ProtonReceiver.class);
        when(receiver.isOpen()).thenReturn(Boolean.TRUE);

        return receiver;
    }

    /**
     * Creates a mocked OpenTracing SpanBuilder for creating a given Span.
     * <p>
     * All invocations on the mock are stubbed to return the builder by default.
     * 
     * @param spanToCreate The object that the <em>start</em> method of the
     *                     returned builder should produce.
     * @return The builder.
     */
    public static SpanBuilder mockSpanBuilder(final Span spanToCreate) {
        final SpanBuilder spanBuilder = mock(SpanBuilder.class, Mockito.RETURNS_SMART_NULLS);
        when(spanBuilder.addReference(anyString(), any())).thenReturn(spanBuilder);
        when(spanBuilder.withTag(anyString(), anyBoolean())).thenReturn(spanBuilder);
        when(spanBuilder.withTag(anyString(), anyString())).thenReturn(spanBuilder);
        when(spanBuilder.withTag(anyString(), (Number) any())).thenReturn(spanBuilder);
        when(spanBuilder.ignoreActiveSpan()).thenReturn(spanBuilder);
        when(spanBuilder.start()).thenReturn(spanToCreate);
        return spanBuilder;
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

        final Tracer tracer = NoopTracerFactory.create();
        final HonoConnection connection = mock(HonoConnection.class);
        when(connection.getVertx()).thenReturn(vertx);
        when(connection.getConfig()).thenReturn(props);
        when(connection.getTracer()).thenReturn(tracer);
        when(connection.executeOrRunOnContext(anyHandler())).then(invocation -> {
            final Future<?> result = Future.future();
            final Handler<Future<?>> handler = invocation.getArgument(0);
            handler.handle(result);
            return result;
        });
        return connection;
    }
}
