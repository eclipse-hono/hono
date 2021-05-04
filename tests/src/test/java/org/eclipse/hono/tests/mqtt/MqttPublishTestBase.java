/*******************************************************************************
 * Copyright (c) 2016, 2021 Contributors to the Eclipse Foundation
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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import org.eclipse.hono.application.client.DownstreamMessage;
import org.eclipse.hono.application.client.MessageConsumer;
import org.eclipse.hono.application.client.MessageContext;
import org.eclipse.hono.client.ServerErrorException;
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.service.management.device.Device;
import org.eclipse.hono.service.management.tenant.Tenant;
import org.eclipse.hono.tests.IntegrationTestSupport;
import org.eclipse.hono.tests.Tenants;
import org.eclipse.hono.util.EventConstants;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.RegistryManagementConstants;
import org.junit.jupiter.api.Test;

import io.netty.handler.codec.http.QueryStringEncoder;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.SelfSignedCertificate;
import io.vertx.junit5.VertxTestContext;
import io.vertx.mqtt.messages.MqttConnAckMessage;
import io.vertx.mqtt.messages.MqttPublishMessage;

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
    /**
     * The maximum number of milliseconds to wait for the completion of a PUBLISH operation.
     */
    protected static final int DEFAULT_PUBLISH_COMPLETION_TIMEOUT = 1000; // milliseconds
    /**
     * The default password of devices.
     */
    protected final String password = "secret";

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
     * @param topicPropertyBag Property bag properties to add to the topic.
     * @return A future indicating the outcome of the attempt to publish the
     *         message. The future will be succeeded if and when the message
     *         has been published successfully. The future result will be the
     *         message packet id.
     */
    protected abstract Future<Integer> send(
            String tenantId,
            String deviceId,
            Buffer payload,
            boolean useShortTopicName,
            Map<String, String> topicPropertyBag);

    /**
     * Handles the outcome of an attempt to publish a message.
     * <p>
     * The given publish result will only get completed once a corresponding completion handler
     * gets invoked (see {@link #customizeConnectedClient()}).
     *
     * @param attempt The outcome of the attempt to send a PUBLISH message.
     * @param publishResult The overall outcome of publishing the message.
     * @param publishCompletionTimeout The timeout to wait for the completion of the publish attempt.
     */
    protected final void handlePublishAttempt(final AsyncResult<Integer> attempt, final Promise<Integer> publishResult,
            final long publishCompletionTimeout) {

        if (attempt.failed()) {
            publishResult.fail(attempt.cause());
        } else {
            final Integer messageId = attempt.result();
            final long timerId = vertx.setTimer(publishCompletionTimeout, tid -> {
                pendingMessages.remove(messageId);
                publishResult.fail(new ServerErrorException(HttpURLConnection.HTTP_UNAVAILABLE));
            });
            pendingMessages.put(messageId, mid -> {
                if (vertx.cancelTimer(timerId)) {
                    publishResult.complete(messageId);
                }
            });
        }
    }

    /**
     * Handles the outcome of an attempt to publish a message. Uses the default PUBLISH completion timeout.
     *
     * @param attempt The outcome of the attempt to send a PUBLISH message.
     * @param publishResult The overall outcome of publishing the message.
     * @see #handlePublishAttempt(AsyncResult, Promise, long)
     */
    protected final void handlePublishAttempt(final AsyncResult<Integer> attempt, final Promise<Integer> publishResult) {
        handlePublishAttempt(attempt, publishResult, DEFAULT_PUBLISH_COMPLETION_TIMEOUT);
    }

    /**
     * Creates a topic String with contained property bag properties.
     *
     * @param topicWithoutPropertyBag The topic prefix.
     * @param propertyBag The properties.
     * @return The topic string.
     */
    protected static final String getTopicWithPropertyBag(final String topicWithoutPropertyBag, final Map<String, String> propertyBag) {
        if (propertyBag == null || propertyBag.isEmpty()) {
            return topicWithoutPropertyBag;
        }
        final QueryStringEncoder encoder = new QueryStringEncoder(topicWithoutPropertyBag + "/");
        propertyBag.forEach(encoder::addParam);
        return encoder.toString();
    }

    /**
     * Asserts that the ratio between messages that have been received and messages
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
     * @param messageHandler The handler to invoke for every message received.
     * @return A future succeeding with the created consumer.
     */
    protected abstract Future<MessageConsumer> createConsumer(
            String tenantId,
            Handler<DownstreamMessage<? extends MessageContext>> messageHandler);

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
     * Verifies that an edge device is auto-provisioned if it connects via a gateway equipped with the corresponding
     * authority.
     *
     * @param ctx The test context.
     * @throws InterruptedException if the test fails.
     */
    @Test
    public void testAutoProvisioningViaGateway(final VertxTestContext ctx) throws InterruptedException {

        final String tenantId = helper.getRandomTenantId();
        final Tenant tenant = new Tenant();

        final String gatewayId = helper.getRandomDeviceId(tenantId);
        final Device gateway = new Device()
                .setAuthorities(Collections.singleton(RegistryManagementConstants.AUTHORITY_AUTO_PROVISIONING_ENABLED));

        final String edgeDeviceId = helper.getRandomDeviceId(tenantId);
        helper.createAutoProvisioningMessageConsumers(ctx, tenantId, edgeDeviceId)
            .compose(ok -> helper.registry.addDeviceForTenant(tenantId, tenant, gatewayId, gateway, password))
            .compose(ok -> connectToAdapter(IntegrationTestSupport.getUsername(gatewayId, tenantId), password))
            .compose(ok -> {
                customizeConnectedClient();
                return send(tenantId, edgeDeviceId, Buffer.buffer("hello".getBytes()), false, null);
            })
            .onComplete(ctx.succeeding());
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

    /**
     * Verifies that sending a number of messages to Hono's MQTT adapter with an invalid content type causes
     * corresponding error messages to be published to the client if it is subscribed on the error topic.
     *
     * @param ctx The test context.
     * @throws InterruptedException if the test fails.
     */
    @Test
    public void testUploadMessagesWithWrongContentTypeSendsErrors(final VertxTestContext ctx) throws InterruptedException {

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

        testUploadMessageWithInvalidContentType(ctx, tenantId, deviceId, deviceId);
    }

    /**
     * Verifies that sending a number of messages to Hono's MQTT adapter with an invalid content type on
     * behalf of a gateway managed device causes corresponding error messages to be published to the client
     * if it is subscribed on the error topic.
     *
     * @param ctx The test context.
     * @throws InterruptedException if the test fails.
     */
    @Test
    public void testUploadMessagesWithWrongContentTypeForGatewayManagedDeviceSendsErrors(final VertxTestContext ctx) throws InterruptedException {

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

        final String gatewayManagedDevice = helper.setupGatewayDeviceBlocking(tenantId, deviceId, 5);
        testUploadMessageWithInvalidContentType(ctx, tenantId, deviceId, gatewayManagedDevice);
    }

    private void testUploadMessageWithInvalidContentType(final VertxTestContext ctx, final String tenantId,
            final String deviceId, final String deviceToSendTo) throws InterruptedException {
        doTestUploadMessages(
                ctx,
                tenantId,
                connectToAdapter(IntegrationTestSupport.getUsername(deviceId, tenantId), password),
                (payload) -> {
                    // send message with empty-notification content type, will cause error since doTestUploadMessages() uses non-empty payload
                    return sendWithCorrelationIdIfNeeded(tenantId, deviceToSendTo, payload, Map.of(
                            MessageHelper.SYS_PROPERTY_CONTENT_TYPE, EventConstants.CONTENT_TYPE_EMPTY_NOTIFICATION));
                },
                (messageHandler) -> createConsumer(tenantId, messageHandler),
                (msg) -> {
                    final JsonObject payload = new JsonObject(msg.payload());
                    final String correlationId = payload.getString(MessageHelper.SYS_PROPERTY_CORRELATION_ID);
                    ctx.verify(() -> {
                        assertThat(payload.getInteger("code")).isEqualTo(HttpURLConnection.HTTP_BAD_REQUEST);
                        // error message should be about the non-matching content type
                        assertThat(payload.getString("message"))
                                .contains(EventConstants.CONTENT_TYPE_EMPTY_NOTIFICATION);
                        // validate topic segments; example: error//myDeviceId/telemetry/4/503
                        final String[] topicSegments = msg.topicName().split("/");
                        assertThat(topicSegments.length).isEqualTo(6);
                        assertThat(topicSegments[0]).isEqualTo("error");
                        assertThat(topicSegments[1]).isEmpty(); // tenant
                        assertThat(topicSegments[2]).isEqualTo(deviceId.equals(deviceToSendTo) ? "" : deviceToSendTo);
                        assertThat(topicSegments[3]).isNotEmpty(); // endpoint used when sending the message that caused the error (e.g. "telemetry")
                        assertThat(topicSegments[4]).isEqualTo(correlationId);
                        assertThat(topicSegments[5]).isEqualTo(Integer.toString(HttpURLConnection.HTTP_BAD_REQUEST));

                    });
                    return Future.succeededFuture(correlationId);
                },
                "error///#");
    }

    /**
     * Verifies that sending a number of messages to Hono's MQTT adapter as a gateway on behalf of an
     * unregistered device causes corresponding error messages to be published to the client if it is
     * subscribed on the error topic.
     *
     * @param ctx The test context.
     * @throws InterruptedException if the test fails.
     */
    @Test
    public void testUploadMessagesWithGatewayManagedDeviceNotFoundError(final VertxTestContext ctx) throws InterruptedException {

        final String tenantId = helper.getRandomTenantId();
        final String deviceId = helper.getRandomDeviceId(tenantId);
        final Tenant tenant = new Tenant();

        final VertxTestContext setup = new VertxTestContext();

        helper.registry.addDeviceForTenant(tenantId, tenant, deviceId, password).onComplete(setup.completing());
        final String deviceToSendTo = "nonExistingDevice";

        assertThat(setup.awaitCompletion(5, TimeUnit.SECONDS)).isTrue();
        if (setup.failed()) {
            ctx.failNow(setup.causeOfFailure());
            return;
        }

        doTestUploadMessages(
                ctx,
                tenantId,
                connectToAdapter(IntegrationTestSupport.getUsername(deviceId, tenantId), password),
                (payload) -> sendWithCorrelationIdIfNeeded(tenantId, deviceToSendTo, payload, null),
                (messageHandler) -> createConsumer(tenantId, messageHandler),
                (msg) -> {
                    final JsonObject payload = new JsonObject(msg.payload());
                    final String correlationId = payload.getString(MessageHelper.SYS_PROPERTY_CORRELATION_ID);
                    ctx.verify(() -> {
                        assertThat(payload.getInteger("code")).isEqualTo(HttpURLConnection.HTTP_NOT_FOUND);
                        // error message should be about the device being unknown or disabled
                        assertThat(payload.getString("message")).isEqualTo(
                                ServiceInvocationException.getLocalizedMessage(
                                        "CLIENT_ERROR_DEVICE_DISABLED_OR_NOT_REGISTERED"));
                        // validate topic segments; example: error//myDeviceId/telemetry/4/503
                        final String[] topicSegments = msg.topicName().split("/");
                        assertThat(topicSegments.length).isEqualTo(6);
                        assertThat(topicSegments[0]).isEqualTo("error");
                        assertThat(topicSegments[1]).isEmpty(); // tenant
                        assertThat(topicSegments[2]).isEqualTo(deviceToSendTo);
                        assertThat(topicSegments[3]).isNotEmpty(); // endpoint used when sending the message that caused the error (e.g. "telemetry")
                        assertThat(topicSegments[4]).isEqualTo(correlationId);
                        assertThat(topicSegments[5]).isEqualTo(Integer.toString(HttpURLConnection.HTTP_NOT_FOUND));

                    });
                    return Future.succeededFuture(correlationId);
                },
                "error///#");
    }

    private Future<String> sendWithCorrelationIdIfNeeded(final String tenantId, final String deviceId,
            final Buffer payload, final Map<String, String> propertyBag) {
        // QoS 0 messages won't have a packet-id, therefore add a correlation-id in the property bag in that case
        final String correlationId = UUID.randomUUID().toString();
        final Map<String, String> propertyBagToUse = propertyBag != null ? new HashMap<>(propertyBag) : new HashMap<>();
        if (getQos().value() == 0) {
            propertyBagToUse.put(MessageHelper.SYS_PROPERTY_CORRELATION_ID, correlationId);
        }
        return send(tenantId, deviceId, payload, false, propertyBagToUse)
                .map(id -> getQos().value() == 0 ? correlationId : Integer.toString(id));
    }

    private void doTestUploadMessages(
            final VertxTestContext ctx,
            final String tenantId,
            final String deviceId,
            final Future<MqttConnAckMessage> connection,
            final boolean useShortTopicName)
            throws InterruptedException {
        doTestUploadMessages(ctx,
                tenantId,
                connection,
                (payload) -> send(tenantId, deviceId, payload, useShortTopicName, null).map(String::valueOf),
                (messageHandler) -> createConsumer(tenantId, messageHandler),
                null,
                null);
    }

    /**
     * Uploads a number of messages and verifies that they are either received via the northbound consumer or that
     * corresponding error messages are published to the client on the error topic.
     *
     * @param ctx The test context.
     * @param tenantId The tenant identifier.
     * @param connection The MQTT connection future.
     * @param sender The message sender. The Future result is the correlation/message id of the sent message.
     * @param consumerSupplier The message consumer. The result may be succeeded with a {@code null} value in case
     *                         error message handling for a non-existing consumer shall get tested.
     * @param errorMsgHandler The handler to invoke with received error messages or {@code null} if no error messages
     *            are expected. The future result is the error message correlation id.
     * @param errorTopic The errorTopic to subscribe to. Will be ignored of errorMsgHandler is {@code null}.
     * @throws InterruptedException if the test fails.
     */
    protected void doTestUploadMessages(
            final VertxTestContext ctx,
            final String tenantId,
            final Future<MqttConnAckMessage> connection,
            final Function<Buffer, Future<String>> sender,
            final Function<Handler<DownstreamMessage<? extends MessageContext>>, Future<MessageConsumer>> consumerSupplier,
            final Function<MqttPublishMessage, Future<String>> errorMsgHandler,
            final String errorTopic)
            throws InterruptedException {

        final boolean errorMessagesExpected = errorMsgHandler != null;
        final CountDownLatch received = new CountDownLatch(MESSAGES_TO_SEND);
        final AtomicInteger messageCount = new AtomicInteger(0);
        final AtomicLong lastReceivedTimestamp = new AtomicLong(0);
        // <correlation id of the sent telemetry/event message, errorMessageReceived promise>
        final Map<String, Promise<Void>> pendingErrorMessages = new HashMap<>();
        final AtomicBoolean consumerIsSet = new AtomicBoolean();

        final VertxTestContext setup = new VertxTestContext();
        connection.compose(ok -> consumerSupplier.apply(msg -> {
            if (errorMessagesExpected) {
                ctx.failNow(new IllegalStateException("consumer received message although sending was supposed to fail"));
                return;
            }
            LOGGER.trace("received {}", msg);
            ctx.verify(() -> {
                IntegrationTestSupport.assertTelemetryMessageProperties(msg, tenantId);
                assertThat(msg.getQos().ordinal()).isEqualTo(getQos().ordinal());
                assertAdditionalMessageProperties(msg);
            });
            received.countDown();
            lastReceivedTimestamp.set(System.currentTimeMillis());
            if (received.getCount() % 50 == 0) {
                LOGGER.info("messages received: {}", MESSAGES_TO_SEND - received.getCount());
            }
        })).compose(msgConsumer -> {
            consumerIsSet.set(msgConsumer != null);
            if (errorMsgHandler == null) {
                return Future.succeededFuture();
            }
            mqttClient.publishHandler(msg -> {
                LOGGER.trace("received error message [topic: {}]", msg.topicName());
                errorMsgHandler.apply(msg).onSuccess(correlationId -> {
                    // correlate the error message with the corresponding publish operation and complete the publish operation promise here
                    pendingErrorMessages.compute(correlationId, (key, oldValue) -> {
                        final Promise<Void> promise = Optional.ofNullable(oldValue).orElseGet(Promise::promise);
                        promise.tryComplete();
                        return oldValue != null ? null : promise; // remove mapping if oldValue is set
                    });
                }).onFailure(ctx::failNow);
            });
            return subscribeToErrorTopic(errorTopic);
        }).onComplete(setup.completing());

        assertThat(setup.awaitCompletion(5, TimeUnit.SECONDS)).isTrue();
        if (setup.failed()) {
            ctx.failNow(setup.causeOfFailure());
            return;
        }

        customizeConnectedClient();

        final long start = System.currentTimeMillis();
        while (messageCount.get() < MESSAGES_TO_SEND) {
            final CountDownLatch messageHandlingCompleted = new CountDownLatch(errorMessagesExpected ? 2 : 1);
            context.runOnContext(go -> {
                final Buffer msg = Buffer.buffer("hello " + messageCount.getAndIncrement());
                sender.apply(msg).onComplete(sendAttempt -> {
                    if (sendAttempt.failed()) {
                        LOGGER.error("error sending message {}", messageCount.get(), sendAttempt.cause());
                    }
                    if (messageCount.get() % 50 == 0) {
                        LOGGER.info("messages sent: " + messageCount.get());
                    }
                    messageHandlingCompleted.countDown();
                    if (errorMessagesExpected) {
                        if (sendAttempt.failed()) {
                            messageHandlingCompleted.countDown();
                        } else {
                            // wait til error message has been received
                            final String correlationId = sendAttempt.result();
                            final long timerId = vertx.setTimer(1000, tid -> {
                                Optional.ofNullable(pendingErrorMessages.remove(correlationId))
                                        .ifPresent(promise -> promise.tryFail(new ServerErrorException(
                                                HttpURLConnection.HTTP_UNAVAILABLE, "timeout waiting for error response")));
                            });
                            final Handler<AsyncResult<Void>> errorMessageReceivedOrTimeoutHandler = ar -> {
                                vertx.cancelTimer(timerId);
                                if (ar.succeeded()) {
                                    received.countDown();
                                    lastReceivedTimestamp.set(System.currentTimeMillis());
                                    if (received.getCount() % 50 == 0) {
                                        LOGGER.info("error messages received: {}", MESSAGES_TO_SEND - received.getCount());
                                    }
                                } else {
                                    LOGGER.warn("failed to handle error message with correlation id [{}]", correlationId, ar.cause());
                                }
                                messageHandlingCompleted.countDown();
                            };
                            pendingErrorMessages.compute(correlationId, (key, oldValue) -> {
                                final Promise<Void> promise = Optional.ofNullable(oldValue).orElseGet(Promise::promise);
                                promise.future().onComplete(errorMessageReceivedOrTimeoutHandler);
                                return oldValue != null ? null : promise; // remove mapping if oldValue is set
                            });
                        }
                    }
                });
            });

            messageHandlingCompleted.await();
        }

        // in case no consumer is set, waiting time needs to be longer (adapter will wait for credit when creating the first downstream sender)
        final long timeToWait = getTimeToWait() + (!consumerIsSet.get() ? 2000 : 0);
        if (!received.await(timeToWait, TimeUnit.MILLISECONDS)) {
            LOGGER.info("Timeout of {} milliseconds reached, stop waiting to receive messages.", timeToWait);
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

    private Future<Void> subscribeToErrorTopic(final String errorTopic) {
        final Promise<Void> result = Promise.promise();
        context.runOnContext(go -> {
            final int qos = 0;
            final AtomicReference<Integer> subMessageIdRef = new AtomicReference<>();
            mqttClient.subscribeCompletionHandler(subAckMsg -> {
                final List<Integer> ackQoSLevels = subAckMsg.grantedQoSLevels();
                if (subMessageIdRef.get() != null && subAckMsg.messageId() == subMessageIdRef.get()
                        && ackQoSLevels.size() == 1 && ackQoSLevels.get(0) == qos) {
                    result.complete();
                } else {
                    LOGGER.warn("could not process SUBACK packet from MQTT adapter [grantedQoSLevels: {}, SUBACK messageId: {}, SUBSCRIBE messageId: {}]",
                            ackQoSLevels, subAckMsg.messageId(), subMessageIdRef.get());
                    result.fail("could not subscribe to error topic");
                }
            });
            mqttClient.subscribe(Optional.ofNullable(errorTopic).orElse("error///#"), qos, ar -> {
                if (ar.succeeded()) {
                    subMessageIdRef.set(ar.result());
                } else {
                    result.fail("could not subscribe to error topic: " + ar.cause().getMessage());
                }
            });
        });
        return result.future().onComplete(v -> mqttClient.subscribeCompletionHandler(null));
    }

    /**
     * Invoked before messages are being published by test cases.
     * Provides a hook to e.g. further customize the MQTT client.
     * <p>
     * This default implementation makes sure that the result promise of {@link #handlePublishAttempt(AsyncResult, Promise)}
     * gets completed when a publish completion handler gets called.
     */
    protected void customizeConnectedClient() {
        mqttClient.publishCompletionHandler(id -> {
            Optional.ofNullable(pendingMessages.remove(id)).ifPresent(handler -> handler.handle(id));
        });
    }

    /**
     * Perform additional checks on a received message.
     * <p>
     * This default implementation does nothing. Subclasses should override this method to implement
     * reasonable checks.
     *
     * @param msg The message to perform checks on.
     * @throws RuntimeException if any of the checks fail.
     */
    protected void assertAdditionalMessageProperties(final DownstreamMessage<? extends MessageContext> msg) {
        // empty
    }
}
