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
package org.eclipse.hono.tests;

import java.util.stream.IntStream;

import org.eclipse.hono.client.TelemetryClient;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.proton.ProtonClientOptions;

/**
 * A simple test that uses the {@code TelemetryClient} to send some messages to
 * Hono server and verifies they are received at the Qpid Dispatch router.
 */
@RunWith(VertxUnitRunner.class)
public class TelemetryClientIT {

    private static final Logger LOGGER = LoggerFactory.getLogger(TelemetryClientIT.class);

    /* connection parameters */
    public static final String HONO_HOST = System.getProperty("hono.host", "localhost");
    public static final int HONO_PORT = Integer.getInteger("hono.amqp.port", 5672);
    public static final String QPID_HOST = System.getProperty("qpid.host", "localhost");
    public static final int QPID_PORT = Integer.getInteger("qpid.amqp.port", 15672);

    /* test constants */
    public static final int MSG_COUNT = 100;
    public static final String TEST_TENANT_ID = "tenant";
    public static final String DEVICE_ID = "device-0";

    private TelemetryClient sender;
    private TelemetryClient receiver;

    @Before
    public void init(final TestContext ctx) throws Exception {
        final ProtonClientOptions options = new ProtonClientOptions();
        options.setReconnectAttempts(10);

        sender = new TelemetryClient(HONO_HOST, HONO_PORT, TEST_TENANT_ID, options);
        sender.createSender().setHandler(ctx.asyncAssertSuccess());

        receiver = new TelemetryClient(QPID_HOST, QPID_PORT, TEST_TENANT_ID, options);
    }

    @After
    public void cleanup(final TestContext ctx) throws InterruptedException {
        sender.shutdown(ctx.asyncAssertSuccess());
        receiver.shutdown(ctx.asyncAssertSuccess());
    }

    @Test
    public void testSendingMessages(final TestContext ctx) throws Exception {

        final Async received = ctx.async(MSG_COUNT);

        sender.register(DEVICE_ID).setHandler(registration -> {
            ctx.assertTrue(registration.succeeded());
            ctx.assertEquals(200, registration.result());
            receiver.createReceiver(message -> {
                LOGGER.debug("Received " + message);
                received.countDown();
            }).setHandler(ok -> {
                IntStream.range(0, MSG_COUNT).forEach(i -> sender.send(DEVICE_ID, "payload" + i));
            });
        });

        received.awaitSuccess(5000);
    }

    @Test
    public void testAttachDenied(final TestContext ctx) throws Exception {

        /* create client for tenant that has no permission */
        final TelemetryClient unauthorized = new TelemetryClient(HONO_HOST, HONO_PORT, "evil");

        try {
            /* and expect that the link is closed immediately */
            final Async senderClosed = ctx.async();
            unauthorized.createSender(closed -> {
                LOGGER.debug("Sender was closed");
                senderClosed.complete();
            });
    
            senderClosed.awaitSuccess(5000);
        } finally {
            unauthorized.shutdown(ctx.asyncAssertSuccess());
        }
    }

    @Test
    public void testExpectExceptionWhenSenderIsNotOpen(final TestContext ctx) throws Exception {

        /* create client for tenant that has no permission */
        final TelemetryClient unauthorized = new TelemetryClient(HONO_HOST, HONO_PORT, "evil");

        try {
            unauthorized.createSender().setHandler(result -> sender.send("test1", "should fail"));
        } catch (final IllegalStateException e) {
            // ok
            LOGGER.debug("Caught exception as expected: ", e.getMessage());
        } finally {
            unauthorized.shutdown(ctx.asyncAssertSuccess());
        }
    }
}
