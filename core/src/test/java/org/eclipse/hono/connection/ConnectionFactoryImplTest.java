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

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.proton.ProtonClientOptions;
import io.vertx.proton.ProtonConnection;

/**
 * Verifies behavior of {@code ConnectionFactoryImpl}.
 *
 */
public class ConnectionFactoryImplTest {

    ConnectionFactoryImpl factory;
    Vertx vertx;

    @Before
    public void setup() {
        vertx = Vertx.vertx();
        factory = new ConnectionFactoryImpl();
        factory.setVertx(vertx);
    }

    /**
     * Verifies that the given result handler is invoked if a connection attempt fails.
     * 
     * @throws InterruptedException if the test gets interrupted during execution.
     */
    @Test
    public void testConnectInvokesHandlerOnfailureToConnect() throws InterruptedException {

        // GIVEN a factory configured to connect to a non-existing server
        ClientConfigProperties props = new ClientConfigProperties();
        props.setHost("127.0.0.1");
        props.setPort(12000); // no server running on port
        props.setAmqpHostname("hono");
        props.setName("client");
        factory.setClientConfig(props);

        // WHEN trying to connect to the server
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<Throwable> cause = new AtomicReference<>();
        Handler<AsyncResult<ProtonConnection>> connectionHandler = (attempt) -> {
            if (attempt.failed()) {
                cause.set(attempt.cause());
                latch.countDown();
            }
        };

        ProtonClientOptions options = new ProtonClientOptions().setConnectTimeout(100);
        factory.connect(options, null, null, connectionHandler);

        // THEN the connection attempt fails and the given handler is invoked
        assertTrue(latch.await(200, TimeUnit.MILLISECONDS));
        assertThat(cause.get(), is(notNullValue()));
    }

}
