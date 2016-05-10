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

import java.util.concurrent.atomic.AtomicReference;

import io.vertx.core.Handler;
import io.vertx.core.Vertx;
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
}
