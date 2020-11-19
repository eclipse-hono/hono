/*******************************************************************************
 * Copyright (c) 2016, 2019 Contributors to the Eclipse Foundation
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

import static org.assertj.core.api.Assertions.assertThat;

import java.net.HttpURLConnection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.MessageConsumer;
import org.eclipse.hono.client.ServerErrorException;
import org.eclipse.hono.service.management.tenant.Tenant;
import org.eclipse.hono.tests.IntegrationTestSupport;
import org.eclipse.hono.tests.Tenants;
import org.eclipse.hono.util.MessageHelper;
import org.junit.jupiter.api.Test;

import io.netty.handler.codec.mqtt.MqttQoS;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.SelfSignedCertificate;
import io.vertx.junit5.VertxTestContext;
import io.vertx.mqtt.messages.MqttConnAckMessage;

/**
 * Base class for integration tests verifying that devices can upload messages
 * to the MQTT adapter.
 *
 */
public abstract class MqttPublishTestBase extends MqttTestBase {

    /**
     * The maximum number of milliseconds a test case may run before it
     * is considered to have failed.
     */
    protected static final int TEST_TIMEOUT = 2000; // milliseconds
    /**
     * The number of messages to send as part of the test cases.
     */
    protected static final int MESSAGES_TO_SEND = IntegrationTestSupport.MSG_COUNT;

    private final String password = "secret";

    // <MQTT message ID, PUBACK handler>
    private final Map<Integer, Handler<Integer>> pendingMessages = new HashMap<>();

    /**
     * Gets the QoS level with which the MQTT message shall be sent.
     *
     * @return The QoS level.
     */
    protected abstract MqttQoS getQos();

    /**
     * Sends a message on behalf of a device to the MQTT adapter.
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
     * Handles the outcome of an attempt to publish a message.
     *
     * @param attempt The outcome of the attempt to send a PUBLISH message.
     * @param publishResult The overall outcome of publishing the message.
     */
    protected void handlePublishAttempt(final AsyncResult<Integer> attempt, final Promise<?> publishResult) {

        if (attempt.failed()) {
            publishResult.fail(attempt.cause());
        } else {
            final Integer messageId = attempt.result();
            final long timerId = VERTX.setTimer(1000, tid -> {
                pendingMessages.remove(messageId);
                publishResult.fail(new ServerErrorException(HttpURLConnection.HTTP_UNAVAILABLE));
            });
            pendingMessages.put(messageId, mid -> {
                if (VERTX.cancelTimer(timerId)) {
                    publishResult.complete();
                }
            });
        }
    }

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
    protected void assertMessageReceivedRatio(final long received, final long sent, final VertxTestContext ctx) {
        if (received < sent) {
            final String msg = String.format("did not receive expected number of messages [expected: %d, received: %d]",
                    sent, received);
            ctx.failNow(new IllegalStateException(msg));
        } else {
            ctx.completeNow();
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
    public void testUploadMessages(final VertxTestContext ctx) throws InterruptedException {

        final String tenantId = helper.getRandomTenantId();
        final String deviceId = helper.getRandomDeviceId(tenantId);
        final Tenant tenant = new Tenant();

        final VertxTestContext setup = new VertxTestContext();

        helper.registry.addDeviceForTenant(tenantId, tenant, deviceId, password).onComplete(setup.completing());

        assertThat(setup.awaitCompletion(5, TimeUnit.SECONDS)).isTrue();
        if (setup.failed()) {
            ctx.failNow(setup.causeOfFailure());
            return;
        }

        doTestUploadMessages(
                ctx,
                tenantId,
                deviceId,
                connectToAdapter(IntegrationTestSupport.getUsername(deviceId, tenantId), password),
                false);
    }

    /**
     * Verifies that a number of messages published to Hono's MQTT adapter
     * using the short topic names can be successfully consumed via the AMQP Messaging Network.
     *
     * @param ctx The test context.
     * @throws InterruptedException if the test fails.
     */
    @Test
    public void testUploadMessagesUsingShortTopicNames(final VertxTestContext ctx) throws InterruptedException {

        final String tenantId = helper.getRandomTenantId();
        final String deviceId = helper.getRandomDeviceId(tenantId);
        final Tenant tenant = new Tenant();
        final VertxTestContext setup = new VertxTestContext();

        helper.registry.addDeviceForTenant(tenantId, tenant, deviceId, password).onComplete(setup.completing());

        assertThat(setup.awaitCompletion(5, TimeUnit.SECONDS)).isTrue();
        if (setup.failed()) {
            ctx.failNow(setup.causeOfFailure());
            return;
        }

        doTestUploadMessages(
                ctx,
                tenantId,
                deviceId,
                connectToAdapter(IntegrationTestSupport.getUsername(deviceId, tenantId), password),
                true);
    }

    /**
     * Verifies that a number of messages published by a device authenticating with a client certificate can be
     * successfully consumed via the AMQP Messaging Network.
     *
     * @param ctx The test context.
     * @throws InterruptedException if the test fails.
     */
    @Test
    public void testUploadMessagesUsingClientCertificate(final VertxTestContext ctx) throws InterruptedException {

        final SelfSignedCertificate deviceCert = SelfSignedCertificate.create(UUID.randomUUID().toString());
        final String tenantId = helper.getRandomTenantId();
        final String deviceId = helper.getRandomDeviceId(tenantId);
        final VertxTestContext setup = new VertxTestContext();

        helper.getCertificate(deviceCert.certificatePath())
        .compose(cert -> {
            final var tenant = Tenants.createTenantForTrustAnchor(cert);
            return helper.registry.addDeviceForTenant(tenantId, tenant, deviceId, cert);
        }).onComplete(setup.completing());

        assertThat(setup.awaitCompletion(5, TimeUnit.SECONDS)).isTrue();
        if (setup.failed()) {
            ctx.failNow(setup.causeOfFailure());
            return;
        }

        doTestUploadMessages(
            ctx,
            tenantId,
            deviceId,
            connectToAdapter(deviceCert),
            false);
    }

    private void doTestUploadMessages(
            final VertxTestContext ctx,
            final String tenantId,
            final String deviceId,
            final Future<MqttConnAckMessage> connection,
            final boolean useShortTopicName)
            throws InterruptedException {

        final CountDownLatch received = new CountDownLatch(MESSAGES_TO_SEND);
        final AtomicInteger messageCount = new AtomicInteger(0);
        final AtomicLong lastReceivedTimestamp = new AtomicLong(0);

        final VertxTestContext setup = new VertxTestContext();
        connection.compose(ok -> createConsumer(tenantId, msg -> {
            LOGGER.trace("received {}", msg);
            assertMessageProperties(ctx, msg);
            received.countDown();
            lastReceivedTimestamp.set(System.currentTimeMillis());
            if (received.getCount() % 50 == 0) {
                LOGGER.info("messages received: {}", MESSAGES_TO_SEND - received.getCount());
            }
        })).onComplete(setup.completing());

        assertThat(setup.awaitCompletion(5, TimeUnit.SECONDS)).isTrue();
        if (setup.failed()) {
            ctx.failNow(setup.causeOfFailure());
            return;
        }

        customizeConnectedClient();

        final long start = System.currentTimeMillis();
        while (messageCount.get() < MESSAGES_TO_SEND) {
            final CountDownLatch messageSent = new CountDownLatch(1);
            context.runOnContext(go -> {
                final Buffer msg = Buffer.buffer("hello " + messageCount.getAndIncrement());
                send(tenantId, deviceId, msg, useShortTopicName).onComplete(sendAttempt -> {
                    if (sendAttempt.failed()) {
                        LOGGER.debug("error sending message {}", messageCount.get(), sendAttempt.cause());
                    }
                    if (messageCount.get() % 50 == 0) {
                        LOGGER.info("messages sent: " + messageCount.get());
                    }
                    messageSent.countDown();
                });
            });

            messageSent.await();
        }

        if (!received.await(getTimeToWait(), TimeUnit.MILLISECONDS)) {
            LOGGER.info("Timeout of {} milliseconds reached, stop waiting to receive messages.", getTimeToWait());
        }
        if (lastReceivedTimestamp.get() == 0L) {
            // no message has been received at all
            lastReceivedTimestamp.set(System.currentTimeMillis());
        }
        final long messagesReceived = MESSAGES_TO_SEND - received.getCount();
        LOGGER.info("sent {} and received {} messages in {} milliseconds",
                messageCount.get(), messagesReceived, lastReceivedTimestamp.get() - start);
        assertMessageReceivedRatio(messagesReceived, messageCount.get(), ctx);
    }

    /**
     * Invoked before messages are being published by test cases.
     * Provides a hook to e.g. further customize the MQTT client.
     */
    protected void customizeConnectedClient() {
        mqttClient.publishCompletionHandler(id -> {
            Optional.ofNullable(pendingMessages.remove(id)).ifPresent(handler -> handler.handle(id));
        });
    }

    private void assertMessageProperties(final VertxTestContext ctx, final Message msg) {
        ctx.verify(() -> {
            assertThat(MessageHelper.getDeviceId(msg)).isNotNull();
            assertThat(MessageHelper.getTenantIdAnnotation(msg)).isNotNull();
            assertThat(MessageHelper.getDeviceIdAnnotation(msg)).isNotNull();
            assertThat(MessageHelper.getRegistrationAssertion(msg)).isNull();
            assertThat(MessageHelper.getQoS(msg)).isEqualTo(getQos().ordinal());
            assertThat(msg.getCreationTime()).isGreaterThan(0);
        });
        assertAdditionalMessageProperties(ctx, msg);
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
    protected void assertAdditionalMessageProperties(final VertxTestContext ctx, final Message msg) {
        // empty
    }

}
