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

import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.ext.unit.TestContext;
import io.vertx.proton.ProtonClient;
import io.vertx.proton.ProtonConnection;

/**
 *
 */
public class TestSupport {

    public static ProtonClient connect(final TestContext ctx, final Vertx vertx, final String host, final int port,
            Handler<ProtonConnection> handler) {
        ProtonClient client = ProtonClient.create(vertx);
        client.connect(host, port, res -> {
            ctx.assertTrue(res.succeeded());
            handler.handle(res.result());
        });
        return client;
    }
}
