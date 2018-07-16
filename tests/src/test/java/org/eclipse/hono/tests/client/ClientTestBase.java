/**
 * Copyright (c) 2018 Bosch Software Innovations GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 */
package org.eclipse.hono.tests.client;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import org.eclipse.hono.tests.IntegrationTestSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.ext.unit.TestContext;

/**
 * Base class for integration tests for Hono's AMQP 1.0 services.
 */
public abstract class ClientTestBase {

    /**
     * A logger to be used by subclasses.
     */
    protected final Logger log = LoggerFactory.getLogger(getClass());

    static final long   DEFAULT_TEST_TIMEOUT = 15000; // ms

    /**
     * Upload a number of messages to Hono's Telemetry/Event APIs.
     * 
     * @param context The Vert.x test context.
     * @param receiver The receiver for consuming messages from the messaging network.
     * @param sender The sender for sending messaging to the Hono server.
     * @throws InterruptedException if test execution is interrupted.
     */
    protected void doUploadMessages(final TestContext context, final Consumer<CountDownLatch> receiver, final Consumer<String> sender) throws InterruptedException {
        final CountDownLatch received = new CountDownLatch(IntegrationTestSupport.MSG_COUNT);

        receiver.accept(received);

        if (received.getCount() % 200 == 0) {
            log.info("messages received: {}", IntegrationTestSupport.MSG_COUNT - received.getCount());
        }

        final AtomicInteger messageCount = new AtomicInteger(0);

        while (messageCount.get() < IntegrationTestSupport.MSG_COUNT) {
            final String payload = "temp: " + messageCount.getAndIncrement();
            sender.accept(payload);
            if (messageCount.get() % 200 == 0) {
                log.info("messages sent: {}", messageCount.get());
            }
        }

        final long timeToWait = Math.max(DEFAULT_TEST_TIMEOUT, Math.round(IntegrationTestSupport.MSG_COUNT * 1.2));
        received.await(timeToWait, TimeUnit.MILLISECONDS);
        final long messagesReceived = IntegrationTestSupport.MSG_COUNT - received.getCount();
        log.info("sent {} and received {} messages", messageCount, messagesReceived);
        if (messagesReceived < messageCount.get()) {
            context.fail("did not receive all messages");
        }

    }
}
