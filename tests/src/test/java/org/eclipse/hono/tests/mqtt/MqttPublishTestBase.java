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

package org.eclipse.hono.tests.mqtt;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.MessageConsumer;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.TenantObject;
import org.junit.Test;

import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;

/**
 * Base class for integration tests verifying that devices can upload messages
 * to the MQTT adapter.
 *
 */
public abstract class MqttPublishTestBase extends MqttTestBase {

    /**
     * The number of messages to send as part of the test cases.
     */
    protected static final int MESSAGES_TO_SEND = 200;

    /**
     * Sends a message on behalf of a device to the HTTP adapter.
     * 
     * @param tenantId The tenant that the device belongs to.
     * @param deviceId The identifier of the device.
     * @param payload The message to send.
     * @param useShortTopicName Whether to use short or standard topic names
     * @return A future indicating the outcome of the attempt to publish the
     *         message. The future will succeed if the message has been
     *         published successfully.
     */
    protected abstract Future<Void> send(
            String tenantId,
            String deviceId,
            Buffer payload,
            boolean useShortTopicName);

    /**
     * Asserts that the ration between messages that have been received and messages
     * being sent is acceptable for the particular QoS used for publishing messages.
     * <p>
     * This default implementation asserts that received = sent.
     * 
     * @param received The number of messages that have been received.
     * @param sent The number of messages that have been sent.
     * @param ctx The test context that will be failed if the ratio is not acceptable.
     */
    protected void assertMessageReceivedRatio(final long received, final long sent, final TestContext ctx) {
        if (received < sent) {
            ctx.fail(String.format("did not receive expected number of messages [expected: %d, received: %d]",
                    sent, received));
        }
    }

    /**
     * Gets the number of milliseconds that the message sending test cases
     * should wait for messages being received by the consumer.
     * 
     * @return The number of milliseconds.
     */
    protected long getTimeToWait() {
        return Math.max(TEST_TIMEOUT, MESSAGES_TO_SEND * 20);
    }

    /**
     * Creates a test specific message consumer.
     *
     * @param tenantId        The tenant to create the consumer for.
     * @param messageConsumer The handler to invoke for every message received.
     * @return A future succeeding with the created consumer.
     */
    protected abstract Future<MessageConsumer> createConsumer(String tenantId, Consumer<Message> messageConsumer);

    /**
     * Verifies that a number of messages published to Hono's MQTT adapter
     * using the standard topic names can be successfully consumed via the AMQP Messaging Network.
     * 
     * @param ctx The test context.
     * @throws InterruptedException if the test fails.
     */
    @Test
    public void testUploadMessages(final TestContext ctx) throws InterruptedException {
        doTestUploadMessages(ctx, false);
    }

    /**
     * Verifies that a number of messages published to Hono's MQTT adapter
     * using the short topic names can be successfully consumed via the AMQP Messaging Network.
     * 
     * @param ctx The test context.
     * @throws InterruptedException if the test fails.
     */
    @Test
    public void testUploadMessagesUsingShortTopicNames(final TestContext ctx) throws InterruptedException {
        doTestUploadMessages(ctx, true);
    }

    private void doTestUploadMessages(final TestContext ctx, final boolean useShortTopicName)
            throws InterruptedException {

        final CountDownLatch received = new CountDownLatch(MESSAGES_TO_SEND);
        final AtomicInteger messageCount = new AtomicInteger(0);
        final AtomicLong lastReceivedTimestamp = new AtomicLong();
        final String tenantId = helper.getRandomTenantId();
        final String deviceId = helper.getRandomDeviceId(tenantId);
        final String password = "secret";
        final TenantObject tenant = TenantObject.from(tenantId, true);

        final Async setup = ctx.async();
        connectToAdapter(tenant, deviceId, password, () -> createConsumer(tenantId, msg -> {
            LOGGER.trace("received {}", msg);
            assertMessageProperties(ctx, msg);
            assertAdditionalMessageProperties(ctx, msg);
            received.countDown();
            lastReceivedTimestamp.set(System.currentTimeMillis());
            if (received.getCount() % 40 == 0) {
                LOGGER.info("messages received: {}", MESSAGES_TO_SEND - received.getCount());
            }
        })).setHandler(ctx.asyncAssertSuccess(ok -> setup.complete()));
        setup.await();

        final long start = System.currentTimeMillis();
        while (messageCount.get() < MESSAGES_TO_SEND) {
            final Async messageSent = ctx.async();
            context.runOnContext(go -> {
                final Buffer msg = Buffer.buffer("hello " + messageCount.getAndIncrement());
                send(tenantId, deviceId, msg, useShortTopicName).setHandler(sendAttempt -> {
                    if (sendAttempt.failed()) {
                        LOGGER.debug("error sending message {}", messageCount.get(), sendAttempt.cause());
                    }
                    if (messageCount.get() % 40 == 0) {
                        LOGGER.info("messages sent: " + messageCount.get());
                    }
                    messageSent.complete();
                });
            });

            messageSent.await();
        }

        if (!received.await(getTimeToWait(), TimeUnit.MILLISECONDS)) {
            LOGGER.info("Timeout of {} milliseconds reached, stop waiting to receive messages.", getTimeToWait());
        }
        final long messagesReceived = MESSAGES_TO_SEND - received.getCount();
        LOGGER.info("sent {} and received {} messages in {} milliseconds",
                messageCount.get(), messagesReceived, lastReceivedTimestamp.get() - start);
        assertMessageReceivedRatio(messagesReceived, messageCount.get(), ctx);
    }

    private void assertMessageProperties(final TestContext ctx, final Message msg) {
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
