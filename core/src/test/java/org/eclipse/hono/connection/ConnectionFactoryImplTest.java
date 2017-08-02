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
package org.eclipse.hono.connection;

import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.hono.config.ClientConfigProperties;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.http.impl.VertxHttp2ClientUpgradeCodec;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.proton.ProtonClientOptions;
import io.vertx.proton.ProtonConnection;

/**
 * Verifies behavior of {@code ConnectionFactoryImpl}.
 *
 */
@RunWith(VertxUnitRunner.class)
public class ConnectionFactoryImplTest {

    private Vertx vertx;

    /**
     * Sets up fixture.
     */
    @Before
    public void setup() {
        vertx = Vertx.vertx();
    }

    /**
     * Verifies that the given result handler is invoked if a connection attempt fails.
     * 
     * @param ctx The vert.x test context.
     */
    @Test
    public void testConnectInvokesHandlerOnfailureToConnect(final TestContext ctx) {

        // GIVEN a factory configured to connect to a non-existing server
        ClientConfigProperties props = new ClientConfigProperties();
        props.setHost("127.0.0.1");
        props.setPort(25673); // no server running on port
        props.setAmqpHostname("hono");
        props.setName("client");
        ConnectionFactoryImpl factory = new ConnectionFactoryImpl(vertx, props);

        // WHEN trying to connect to the server
        final Async handlerInvocation = ctx.async();

        ProtonClientOptions options = new ProtonClientOptions().setConnectTimeout(100);
        factory.connect(options, null, null, ctx.asyncAssertFailure(t -> {
            ctx.assertNotNull(t);
            handlerInvocation.complete();
        }));

        // THEN the connection attempt fails and the given handler is invoked
        handlerInvocation.await(500);
    }

}
