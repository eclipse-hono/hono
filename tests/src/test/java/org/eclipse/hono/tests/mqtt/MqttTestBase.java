/**
 * Copyright (c) 2016, 2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 *
 */

package org.eclipse.hono.tests.mqtt;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.MessageConsumer;
import org.eclipse.hono.tests.IntegrationTestSupport;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.TenantObject;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.mqtt.MqttClient;
import io.vertx.mqtt.MqttClientOptions;
import io.vertx.mqtt.messages.MqttConnAckMessage;

/**
 * Base class for MQTT adapter integration tests.
 *
 */
public abstract class MqttTestBase {

    /**
     * The vert.xt instance to run on.
     */
    protected static final Vertx VERTX = Vertx.vertx();

    private static final int   TEST_TIMEOUT = 9000; // milliseconds

    /**
     * A client for publishing messages to the MQTT protocol adapter.
     */
    protected static MqttClient mqttClient;
    /**
     * A helper accessing the AMQP 1.0 Messaging Network and
     * for managing tenants/devices/credentials.
     */
    protected static IntegrationTestSupport helper;

    /**
     * Time out each test after 9 seconds.
     */
    @Rule
    public final Timeout timeout = Timeout.millis(TEST_TIMEOUT);

    /**
     * A logger to be used by subclasses.
     */
    protected final Logger LOGGER = LoggerFactory.getLogger(getClass());

    private final Set<Integer> pendingMessages = new HashSet<>();

    /**
     * Sets up the helper.
     * 
     * @param ctx The vert.x test context.
     */
    @BeforeClass
    public static void init(final TestContext ctx) {

        helper = new IntegrationTestSupport(VERTX);
        helper.init(ctx);

    }

    /**
     * Deletes all temporary objects from the Device Registry which
     * have been created during the last test execution.
     * 
     * @param ctx The vert.x context.
     */
    @After
    public void postTest(final TestContext ctx) {

        if (mqttClient != null) {
            mqttClient.disconnect();
        }
        pendingMessages.clear();
        helper.deleteObjects(ctx);
    }

    /**
     * Closes the AMQP 1.0 Messaging Network client.
     * 
     * @param ctx The vert.x test context.
     */
    @AfterClass
    public static void disconnect(final TestContext ctx) {

        helper.disconnect(ctx);
    }

    /**
     * Sends a message on behalf of a device to the HTTP adapter.
     * 
     * @param tenantId The tenant that the device belongs to.
     * @param deviceId The identifier of the device.
     * @param payload The message to send.
     * @param useShortTopicName Whether to use short or standard topic names
     * @param publishSentHandler The handler to invoke with the packet ID of the
     *                           PUBLISH packet that has been sent to the MQTT
     *                           adapter.
     */
    protected abstract void send(
            String tenantId,
            String deviceId,
            Buffer payload,
            boolean useShortTopicName,
            Handler<AsyncResult<Integer>> publishSentHandler);

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

        final int messagesToSend = 200;
        final CountDownLatch received = new CountDownLatch(messagesToSend);
        final Async setup = ctx.async();
        final String tenantId = helper.getRandomTenantId();
        final String deviceId = helper.getRandomDeviceId(tenantId);
        final String password = "secret";
        final TenantObject tenant = TenantObject.from(tenantId, true);

        helper.registry.addDeviceForTenant(tenant, deviceId, password)
            .compose(ok -> createConsumer(tenantId, msg -> {
                LOGGER.trace("received {}", msg);
                assertMessageProperties(ctx, msg);
                assertAdditionalMessageProperties(ctx, msg);
                received.countDown();
                if (received.getCount() % 40 == 0) {
                    LOGGER.info("messages received: {}", messagesToSend - received.getCount());
                }
            })).compose(ok -> {
                final Future<MqttConnAckMessage> result = Future.future();
                final MqttClientOptions options = new MqttClientOptions()
                        .setMaxInflightQueue(200)
                        .setUsername(IntegrationTestSupport.getUsername(deviceId, tenantId))
                        .setPassword(password);
                mqttClient = MqttClient.create(VERTX, options);
                mqttClient.connect(IntegrationTestSupport.MQTT_PORT, IntegrationTestSupport.MQTT_HOST, result.completer());
                return result;
            }).setHandler(ctx.asyncAssertSuccess(ok -> setup.complete()));

        setup.await();

        final long start = System.currentTimeMillis();
        final AtomicInteger messageCount = new AtomicInteger(0);
        final AtomicReference<Async> sendResult = new AtomicReference<>();
        mqttClient.publishCompletionHandler(packetId -> {
            synchronized (pendingMessages) {
                if (pendingMessages.remove(packetId)) {
                    sendResult.get().complete();
                } else {
                    LOGGER.info("received PUBACK for unexpected message [id: {}]", packetId);
                }
            }
        });

        while (messageCount.get() < messagesToSend) {

            sendResult.set(ctx.async());
            synchronized (pendingMessages) {
                send(tenantId, deviceId, Buffer.buffer("hello " + messageCount.getAndIncrement()), useShortTopicName, sendAttempt -> {
                    if (sendAttempt.failed()) {
                        LOGGER.debug("error sending message {}", messageCount.get(), sendAttempt.cause());
                    } else {
                        pendingMessages.add(sendAttempt.result());
                    }
                });
            }

            if (messageCount.get() % 40 == 0) {
                LOGGER.info("messages sent: " + messageCount.get());
            }
            sendResult.get().await();
        }

        long timeToWait = Math.max(TEST_TIMEOUT - 1000, Math.round(messagesToSend * 1.2));
        if (!received.await(timeToWait, TimeUnit.MILLISECONDS)) {
            LOGGER.info("sent {} and received {} messages after {} milliseconds",
                    messageCount, messagesToSend - received.getCount(), System.currentTimeMillis() - start);
            ctx.fail("did not receive all messages sent");
        } else {
            LOGGER.info("sent {} and received {} messages after {} milliseconds",
                    messageCount, messagesToSend - received.getCount(), System.currentTimeMillis() - start);
        }
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
