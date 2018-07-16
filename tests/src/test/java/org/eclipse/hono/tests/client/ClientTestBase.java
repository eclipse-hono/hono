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

import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.MessageConsumer;
import org.eclipse.hono.client.MessageSender;
import org.eclipse.hono.tests.IntegrationTestSupport;
import org.eclipse.hono.util.MessageHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.ext.unit.TestContext;

/**
 * Base class for integration tests for Hono's AMQP 1.0 services.
 */
public abstract class ClientTestBase {

    /**
     * A logger to be used by subclasses.
     */
    protected final Logger LOGGER = LoggerFactory.getLogger(getClass());

    private static final long   DEFAULT_TEST_TIMEOUT = 15000; // ms

    /**
     * Creates a test specific message consumer.
     *
     * @param tenantId        The tenant to create the consumer for.
     * @param messageConsumer The handler to invoke for every message received.
     * @return A future succeeding with the created consumer.
     */
    protected abstract Future<MessageConsumer> createConsumer(String tenantId, Consumer<Message> messageConsumer);

    /**
     * Creates a test specific message sender.
     *
     * @param tenantId     The tenant to create the sender for.
     * @return A future succeeding with the created sender.
     */
    protected abstract Future<MessageSender> createProducer(String tenantId);

    /**
     * Upload a number of messages to Hono's Telemetry/Event APIs.
     * 
     * @param context The Vert.x test context.
     * @param consumerHandler The tenantId from which messages should be consumed.
     * @param senderHandler The handler to call for sending messages to the messaging network.
     * @throws InterruptedException if test execution is interrupted.
     */
    protected void doUploadMessages(final TestContext context, final Handler<CountDownLatch> consumerHandler, final Handler<String> senderHandler) throws InterruptedException {
        final CountDownLatch received = new CountDownLatch(IntegrationTestSupport.MSG_COUNT);

        consumerHandler.handle(received);

        if (received.getCount() % 200 == 0) {
            LOGGER.info("messages received: {}", IntegrationTestSupport.MSG_COUNT - received.getCount());
        }

        final AtomicInteger messageCount = new AtomicInteger(0);

        while (messageCount.get() < IntegrationTestSupport.MSG_COUNT) {
            final String payload = "temp: " + messageCount.getAndIncrement();
            senderHandler.handle(payload);
            if (messageCount.get() % 200 == 0) {
                LOGGER.info("messages sent: {}", messageCount.get());
            }
        }

        final long timeToWait = Math.max(DEFAULT_TEST_TIMEOUT, Math.round(IntegrationTestSupport.MSG_COUNT * 1.2));
        received.await(timeToWait, TimeUnit.MILLISECONDS);
        final long messagesReceived = IntegrationTestSupport.MSG_COUNT - received.getCount();
        LOGGER.info("sent {} and received {} messages", messageCount, messagesReceived);
        if (messagesReceived < messageCount.get()) {
            context.fail("did not receive all messages");
        }

    }

    protected void assertMessageProperties(final TestContext ctx, final Message msg) {
        ctx.assertNotNull(MessageHelper.getDeviceId(msg));
        ctx.assertNotNull(MessageHelper.getTenantIdAnnotation(msg));
        ctx.assertNotNull(MessageHelper.getDeviceIdAnnotation(msg));
        ctx.assertNull(MessageHelper.getRegistrationAssertion(msg));
    }

    /**
     * Perform additional checks on a received message.
     * <p>
     * This default implementation does nothing. Subclasses should override this method to implement
     * reasonable checks.
     * 
     * @param ctx The test context.
     * @param msg The message to perform checks on.
     */
    protected void assertAdditionalMessageProperties(final TestContext ctx, final Message msg) {
        // empty
    }

}
