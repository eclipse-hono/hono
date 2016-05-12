/**
 * Copyright (c) 2016 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 */
package org.eclipse.hono.util;

import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;

import java.util.concurrent.atomic.AtomicReference;

import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.proton.ProtonClient;
import io.vertx.proton.ProtonConnection;

/**
 *
 */
public class TestSupport {

    public static void connect(final TestContext ctx, final Vertx vertx, final String host, final int port,
            Handler<ProtonConnection> handler) {
        ProtonClient client = ProtonClient.create(vertx);
        client.connect(host, port, res -> {
            ctx.assertTrue(res.succeeded());
            handler.handle(res.result());
        });
    }

    public static ProtonConnection connect(final TestContext ctx, final Vertx vertx, final String host, final int port) {
        Async connectionAttempt = ctx.async();
        AtomicReference<ProtonConnection> result = new AtomicReference<>();
        connect(ctx, vertx, host, port, res -> {
            result.set(res);
            connectionAttempt.complete();
        });
        connectionAttempt.await(500);
        return result.get();
    }

    /**
     * Establishes an opened connection to a Hono server.
     * 
     * @param ctx
     * @param vertx
     * @param host
     * @param port
     * @return the opened connection.
     */
    public static ProtonConnection openConnection(final TestContext ctx, final Vertx vertx, final String host, final int port) {
        Async connectionAttempt = ctx.async();
        ProtonConnection con = connect(ctx, vertx, host, port);
        con.openHandler(openAttempt -> {
            ctx.assertTrue(openAttempt.succeeded());
            connectionAttempt.complete();
        }).open();
        connectionAttempt.await(500);
        return con;
    }

    /**
     * Prepares a Mockito mock {@code EventBus} to send a given reply for a message sent to a specific address.
     * 
     * @param bus the mock event bus.
     * @param address the address to expect the message to be sent to.
     * @param msg the message to expect.
     * @param reply the reply to send.
     */
    @SuppressWarnings("unchecked")
    public static <T> void expectReplyForMessage(final EventBus bus, final String address, final Object msg, final T reply) {
        when(bus.send(eq(address), eq(msg), any(Handler.class)))
            .then(new Answer<EventBus>() {
                @Override
                public EventBus answer(InvocationOnMock invocation) throws Throwable {
                    io.vertx.core.eventbus.Message<T> response = mock(io.vertx.core.eventbus.Message.class);
                    when(response.body()).thenReturn(reply);
                    Future<io.vertx.core.eventbus.Message<T>> future = Future.succeededFuture(response);
                    Handler<AsyncResult<io.vertx.core.eventbus.Message<T>>> handler = 
                            (Handler<AsyncResult<io.vertx.core.eventbus.Message<T>>>) invocation.getArguments()[2];
                    handler.handle(future);
                    return bus;
                }
            });
    }
}
