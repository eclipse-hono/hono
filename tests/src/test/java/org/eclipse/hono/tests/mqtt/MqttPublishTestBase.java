/*******************************************************************************
 * Copyright (c) 2016, 2023 Contributors to the Eclipse Foundation
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

import static com.google.common.truth.Truth.assertThat;

import java.net.HttpURLConnection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
import org.eclipse.hono.config.ServiceConfigProperties;
import org.eclipse.hono.service.management.device.Device;
import org.eclipse.hono.service.management.tenant.Tenant;
import org.eclipse.hono.tests.DownstreamMessageAssertions;
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
import io.vertx.junit5.Timeout;
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
     * @param includeTenantIdInTopic {@code true} if the tenant identifier should be included in the
     *                               message's topic name.
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
            boolean includeTenantIdInTopic,
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
     * using the standard topic names can be successfully consumed via the messaging infrastructure.
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

        helper.registry.addDeviceForTenant(tenantId, tenant, deviceId, password)
            .onComplete(setup.succeedingThenComplete());

        assertThat(setup.awaitCompletion(IntegrationTestSupport.getTestSetupTimeout(), TimeUnit.SECONDS)).isTrue();
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
     * Verifies that a number of messages published to Hono's MQTT adapter via an authenticated gateway
     * can be successfully consumed via the messaging infrastructure.
     *
     * @param ctx The test context.
     * @throws InterruptedException if the test fails.
     */
    @Test
    public void testUploadMessagesViaGateway(final VertxTestContext ctx) throws InterruptedException {

        final String tenantId = helper.getRandomTenantId();
        final Tenant tenant = new Tenant();
        final String gatewayId = helper.getRandomDeviceId(tenantId);
        final String deviceId = helper.getRandomDeviceId(tenantId);
        final Device deviceConfig = new Device().setVia(List.of(gatewayId));

        final VertxTestContext setup = new VertxTestContext();

        helper.registry.addDeviceForTenant(tenantId, tenant, gatewayId, password)
            .compose(response -> helper.registry.addDeviceToTenant(tenantId, deviceId, deviceConfig, password))
            .onComplete(setup.succeedingThenComplete());

        assertThat(setup.awaitCompletion(IntegrationTestSupport.getTestSetupTimeout(), TimeUnit.SECONDS)).isTrue();
        if (setup.failed()) {
            ctx.failNow(setup.causeOfFailure());
            return;
        }

        final AtomicInteger count = new AtomicInteger();
        doTestUploadMessages(
                ctx,
                tenantId,
                connectToAdapter(IntegrationTestSupport.getUsername(gatewayId, tenantId), password),
                (payload) -> send(tenantId, deviceId, payload, false, (count.getAndIncrement() % 2 == 0), null)
                    .map(String::valueOf),
                (messageHandler) -> createConsumer(tenantId, messageHandler));
    }

    /**
     * Verifies that an edge device is auto-provisioned if it connects via a gateway equipped with the corresponding
     * authority.
     *
     * @param ctx The test context.
     * @throws InterruptedException if the test fails.
     */
    @Test
    @Timeout(timeUnit = TimeUnit.SECONDS, value = 15)
    public void testAutoProvisioningViaGateway(final VertxTestContext ctx) throws InterruptedException {

        final String tenantId = helper.getRandomTenantId();
        final Tenant tenant = new Tenant();

        final String gatewayId = helper.getRandomDeviceId(tenantId);
        final Device gateway = new Device()
                .setAuthorities(Set.of(RegistryManagementConstants.AUTHORITY_AUTO_PROVISIONING_ENABLED));
        final String edgeDeviceId = helper.getRandomDeviceId(tenantId);
        final Promise<Void> provisioningNotificationReceived = Promise.promise();

        helper.createAutoProvisioningMessageConsumers(ctx, provisioningNotificationReceived, tenantId, edgeDeviceId)
            .compose(ok -> helper.registry.addDeviceForTenant(tenantId, tenant, gatewayId, gateway, password))
            .compose(ok -> connectToAdapter(IntegrationTestSupport.getUsername(gatewayId, tenantId), password))
            .compose(ok -> {
                customizeConnectedClient();
                return send(tenantId, edgeDeviceId, Buffer.buffer("hello".getBytes()), false, true, null);
            })
            .compose(ok -> provisioningNotificationReceived.future())
            .compose(ok -> helper.registry.getRegistrationInfo(tenantId, edgeDeviceId))
            .onComplete(ctx.succeeding(registrationResult -> {
                ctx.verify(() -> {
                    final var info = registrationResult.bodyAsJsonObject();
                    IntegrationTestSupport.assertDeviceStatusProperties(
                            info.getJsonObject(RegistryManagementConstants.FIELD_STATUS),
                            true);
                });
                ctx.completeNow();
            }));
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

        helper.registry.addDeviceForTenant(tenantId, tenant, deviceId, password)
            .onComplete(setup.succeedingThenComplete());

        assertThat(setup.awaitCompletion(IntegrationTestSupport.getTestSetupTimeout(), TimeUnit.SECONDS)).isTrue();
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
            })
            .onComplete(setup.succeedingThenComplete());

        assertThat(setup.awaitCompletion(IntegrationTestSupport.getTestSetupTimeout(), TimeUnit.SECONDS)).isTrue();
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
     * Verifies that sending a message to Hono's MQTT adapter with an invalid content type causes
     * a corresponding error message to be published to the client if it is subscribed to the error topic.
     *
     * @param ctx The test context.
     * @throws InterruptedException if the test fails.
     */
    @Test
    @Timeout(value = 5, timeUnit = TimeUnit.SECONDS)
    public void testUploadMessageWithWrongContentTypeSendsError(final VertxTestContext ctx) throws InterruptedException {

        final String tenantId = helper.getRandomTenantId();
        final String deviceId = helper.getRandomDeviceId(tenantId);
        final Tenant tenant = new Tenant();

        final VertxTestContext setup = new VertxTestContext();

        helper.registry.addDeviceForTenant(tenantId, tenant, deviceId, password)
            .onComplete(setup.succeedingThenComplete());

        assertThat(setup.awaitCompletion(IntegrationTestSupport.getTestSetupTimeout(), TimeUnit.SECONDS)).isTrue();
        if (setup.failed()) {
            ctx.failNow(setup.causeOfFailure());
            return;
        }

        testUploadMessageWithInvalidContentType(ctx, tenantId, deviceId, deviceId);
    }

    /**
     * Verifies that the adapter forwards an empty message with a custom content type other
     * than {@value EventConstants#CONTENT_TYPE_EMPTY_NOTIFICATION} to downstream consumers.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    @Timeout(value = 10, timeUnit = TimeUnit.SECONDS)
    public void testUploadEmptyMessageWithCustomContentTypeSucceeds(final VertxTestContext ctx) {

        final String tenantId = helper.getRandomTenantId();
        final String deviceId = helper.getRandomDeviceId(tenantId);
        final String customContentType = "application/custom";

        helper.registry.addDeviceForTenant(tenantId, new Tenant(), deviceId, "secret")
            .compose(response -> createConsumer(tenantId, msg -> {
                LOGGER.trace("received {}", msg);
                ctx.verify(() -> {
                    DownstreamMessageAssertions.assertTelemetryApiProperties(msg);
                    DownstreamMessageAssertions.assertMessageContainsAdapterAndAddress(msg);
                    assertThat(msg.getContentType()).isEqualTo(customContentType);
                    assertThat(msg.getPayload().length()).isEqualTo(0);
                    assertAdditionalMessageProperties(msg);
                });
                ctx.completeNow();
            }))
            .compose(consumer -> connectToAdapter(IntegrationTestSupport.getUsername(deviceId, tenantId), "secret"))
            .compose(connAck -> send(
                        tenantId,
                        deviceId,
                        Buffer.buffer(),
                        false,
                        true,
                        Map.of("content-type", customContentType)))
            .onFailure(ctx::failNow);
    }

    /**
     * Verifies that the adapter rejects empty messages that have no content type set.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    @Timeout(value = 10, timeUnit = TimeUnit.SECONDS)
    public void testUploadEmptyMessageWithoutContentTypeFails(final VertxTestContext ctx) {

        final String tenantId = helper.getRandomTenantId();
        final String deviceId = helper.getRandomDeviceId(tenantId);

        helper.registry.addDeviceForTenant(tenantId, new Tenant(), deviceId, "secret")
            .compose(response -> createConsumer(tenantId, msg -> {
                LOGGER.trace("received {}", msg);
                ctx.failNow("downstream consumer should not have received message");
            }))
            .compose(consumer -> connectToAdapter(IntegrationTestSupport.getUsername(deviceId, tenantId), "secret"))
            .compose(connAck -> {
                mqttClient.publishHandler(msg -> {
                    LOGGER.trace("received error message [topic: {}]", msg.topicName());
                    ctx.verify(() -> {
                        final var error = msg.payload().toJsonObject();
                        assertThat(error.getInteger("code")).isEqualTo(HttpURLConnection.HTTP_BAD_REQUEST);
                    });
                    ctx.completeNow();
                });
                return subscribeToErrorTopic(null);
            })
            .compose(ok -> send(
                        tenantId,
                        deviceId,
                        Buffer.buffer(),
                        false,
                        true,
                        null))
            .onFailure(ctx::failNow);

    }

    /**
     * Verifies that the adapter forwards a non-empty message without any content type set in the request
     * to downstream consumers using the {@value MessageHelper#CONTENT_TYPE_OCTET_STREAM} content type.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    @Timeout(value = 10, timeUnit = TimeUnit.SECONDS)
    public void testUploadNonEmptyMessageWithoutContentTypeSucceeds(final VertxTestContext ctx) {

        final String tenantId = helper.getRandomTenantId();
        final String deviceId = helper.getRandomDeviceId(tenantId);

        helper.registry.addDeviceForTenant(tenantId, new Tenant(), deviceId, "secret")
            .compose(response -> createConsumer(tenantId, msg -> {
                LOGGER.trace("received {}", msg);
                ctx.verify(() -> {
                    DownstreamMessageAssertions.assertTelemetryApiProperties(msg);
                    DownstreamMessageAssertions.assertMessageContainsAdapterAndAddress(msg);
                    assertThat(msg.getContentType()).isEqualTo(MessageHelper.CONTENT_TYPE_OCTET_STREAM);
                    assertThat(msg.getPayload().length()).isGreaterThan(0);
                    assertAdditionalMessageProperties(msg);
                });
                ctx.completeNow();
            }))
            .compose(consumer -> connectToAdapter(IntegrationTestSupport.getUsername(deviceId, tenantId), "secret"))
            .compose(connAck -> send(
                        tenantId,
                        deviceId,
                        Buffer.buffer("some payload"),
                        false,
                        true,
                        null))
            .onFailure(ctx::failNow);
    }

    /**
     * Verifies that the adapter rejects non-empty messages that are marked as empty notifications.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    @Timeout(value = 10, timeUnit = TimeUnit.SECONDS)
    public void testUploadNonEmptyMessageMarkedAsEmptyNotificationFails(final VertxTestContext ctx) {

        final String tenantId = helper.getRandomTenantId();
        final String deviceId = helper.getRandomDeviceId(tenantId);

        helper.registry.addDeviceForTenant(tenantId, new Tenant(), deviceId, "secret")
            .compose(response -> createConsumer(tenantId, msg -> {
                LOGGER.trace("received {}", msg);
                ctx.failNow("downstream consumer should not have received message");
            }))
            .compose(consumer -> connectToAdapter(IntegrationTestSupport.getUsername(deviceId, tenantId), "secret"))
            .compose(connAck -> {
                mqttClient.publishHandler(msg -> {
                    LOGGER.trace("received error message [topic: {}]", msg.topicName());
                    ctx.verify(() -> {
                        final var error = msg.payload().toJsonObject();
                        assertThat(error.getInteger("code")).isEqualTo(HttpURLConnection.HTTP_BAD_REQUEST);
                    });
                    ctx.completeNow();
                });
                return subscribeToErrorTopic(null);
            })
            .compose(ok -> send(
                        tenantId,
                        deviceId,
                        Buffer.buffer("some payload"),
                        false,
                        true,
                        Map.of("content-type", EventConstants.CONTENT_TYPE_EMPTY_NOTIFICATION)))
            .onFailure(ctx::failNow);

    }

    /**
     * Verifies that a message from a device with a payload exceeding the maximum size cannot be consumed and causes
     * the connection to be closed.
     *
     * @param ctx The vert.x test context.
     * @throws InterruptedException if test execution gets interrupted.
     */
    @Test
    public void testMessageWithPayloadExceedingMaxSize(final VertxTestContext ctx) throws InterruptedException {

        final String tenantId = helper.getRandomTenantId();
        final String deviceId = helper.getRandomDeviceId(tenantId);
        final Tenant tenant = new Tenant();
        final VertxTestContext setup = new VertxTestContext();

        helper.registry.addDeviceForTenant(tenantId, tenant, deviceId, "secret")
                .onComplete(setup.succeedingThenComplete());

        assertThat(setup.awaitCompletion(IntegrationTestSupport.getTestSetupTimeout(), TimeUnit.SECONDS)).isTrue();
        if (setup.failed()) {
            ctx.failNow(setup.causeOfFailure());
            return;
        }

        final byte[] payloadBytes = IntegrationTestSupport.getPayload(ServiceConfigProperties.DEFAULT_MAX_PAYLOAD_SIZE + 256);
        // GIVEN a connected device
        connectToAdapter(IntegrationTestSupport.getUsername(deviceId, tenantId), "secret")
                .compose(connAck -> {
                    final Promise<Void> clientClosedPromise = Promise.promise();
                    mqttClient.closeHandler(v -> clientClosedPromise.complete());
                    // WHEN a device publishes a message with a payload exceeding the max size
                    final Future<Integer> sendFuture = send(tenantId, deviceId, Buffer.buffer(payloadBytes), false,
                            true, null);
                    return Future.join(clientClosedPromise.future(), sendFuture)
                            .onComplete(ar -> {
                                // THEN publishing the message fails (if QoS 1)
                                if (getQos() == MqttQoS.AT_LEAST_ONCE) {
                                    assertThat(sendFuture.succeeded()).isFalse();
                                    assertThat(sendFuture.cause()).isInstanceOf(ServerErrorException.class);
                                    assertThat(((ServerErrorException) sendFuture.cause()).getErrorCode())
                                            .isEqualTo(HttpURLConnection.HTTP_UNAVAILABLE);
                                } else {
                                    assertThat(sendFuture.succeeded()).isTrue();
                                }
                                // and the connection gets closed
                                assertThat(clientClosedPromise.future().succeeded()).isTrue();
                                ctx.completeNow();
                            });
                });
    }

    /**
     * Verifies that sending a message to Hono's MQTT adapter with an invalid content type on
     * behalf of a gateway managed device causes a corresponding error message to be published to the client
     * if it is subscribed to the error topic.
     *
     * @param ctx The test context.
     * @throws InterruptedException if the test fails.
     */
    @Test
    @Timeout(value = 5, timeUnit = TimeUnit.SECONDS)
    public void testUploadMessageWithWrongContentTypeForGatewayManagedDeviceSendsError(final VertxTestContext ctx) throws InterruptedException {

        final String tenantId = helper.getRandomTenantId();
        final String deviceId = helper.getRandomDeviceId(tenantId);
        final Tenant tenant = new Tenant();

        final VertxTestContext setup = new VertxTestContext();

        helper.registry.addDeviceForTenant(tenantId, tenant, deviceId, password)
            .onComplete(setup.succeedingThenComplete());

        assertThat(setup.awaitCompletion(IntegrationTestSupport.getTestSetupTimeout(), TimeUnit.SECONDS)).isTrue();
        if (setup.failed()) {
            ctx.failNow(setup.causeOfFailure());
            return;
        }

        final String gatewayManagedDevice = helper.setupGatewayDeviceBlocking(tenantId, deviceId, 5);
        testUploadMessageWithInvalidContentType(ctx, tenantId, deviceId, gatewayManagedDevice);
    }

    private void testUploadMessageWithInvalidContentType(
            final VertxTestContext ctx,
            final String tenantId,
            final String deviceId,
            final String deviceToSendTo) throws InterruptedException {

        final boolean isGatewayOnBehalfOfDevice = !deviceId.equals(deviceToSendTo);
        final var errorMsgReceived = ctx.checkpoint();

        connectToAdapter(IntegrationTestSupport.getUsername(deviceId, tenantId), password)
            .onComplete(conAck -> createConsumer(tenantId, msg -> {
                ctx.failNow("shouldn't have received published message");
            }))
            .onSuccess(conAck -> mqttClient.publishHandler(errorMsg -> {
                final JsonObject payload = new JsonObject(errorMsg.payload());
                final String correlationId = payload.getString(MessageHelper.SYS_PROPERTY_CORRELATION_ID);
                ctx.verify(() -> {
                    assertThat(payload.getInteger("code")).isEqualTo(HttpURLConnection.HTTP_BAD_REQUEST);
                    // error message should be about the non-matching content type
                    assertThat(payload.getString("message"))
                            .contains(EventConstants.CONTENT_TYPE_EMPTY_NOTIFICATION);
                    // validate topic segments; example: error//myDeviceId/telemetry/4/503
                    final String[] topicSegments = errorMsg.topicName().split("/");
                    assertThat(topicSegments.length).isEqualTo(6);
                    assertThat(topicSegments[0]).isEqualTo("e");
                    assertThat(topicSegments[1]).isEmpty(); // tenant
                    assertThat(topicSegments[2]).isEqualTo(isGatewayOnBehalfOfDevice ? deviceToSendTo : "");
                    assertThat(topicSegments[3]).isNotEmpty(); // endpoint used when sending the message that caused the error (e.g. "telemetry")
                    assertThat(topicSegments[4]).isEqualTo(correlationId);
                    assertThat(topicSegments[5]).isEqualTo(Integer.toString(HttpURLConnection.HTTP_BAD_REQUEST));
                });
                errorMsgReceived.flag();
            }))
            .compose(conAck -> subscribeToErrorTopic("e//%s/#".formatted(isGatewayOnBehalfOfDevice ? "+" : "")))
            .onComplete(ctx.succeeding(ok -> sendWithCorrelationIdIfNeeded(
                    tenantId,
                    deviceToSendTo,
                    Buffer.buffer("payload"),
                    Map.of(MessageHelper.SYS_PROPERTY_CONTENT_TYPE, EventConstants.CONTENT_TYPE_EMPTY_NOTIFICATION))));
    }

    /**
     * Verifies that sending a message to Hono's MQTT adapter as a gateway on behalf of an
     * unregistered device causes a corresponding error message to be published to the client if it is
     * subscribed to the error topic.
     *
     * @param ctx The test context.
     * @throws InterruptedException if the test fails.
     */
    @Test
    @Timeout(value = 5, timeUnit = TimeUnit.SECONDS)
    public void testUploadMessageWithGatewayManagedDeviceNotFoundError(final VertxTestContext ctx) throws InterruptedException {

        final String tenantId = helper.getRandomTenantId();
        final String deviceId = helper.getRandomDeviceId(tenantId);
        final Tenant tenant = new Tenant();

        final String deviceToSendTo = "nonExistingDevice";
        final var errorMsgReceived = ctx.checkpoint();

        helper.registry.addDeviceForTenant(tenantId, tenant, deviceId, password)
            .compose(res -> connectToAdapter(IntegrationTestSupport.getUsername(deviceId, tenantId), password))
            .onComplete(conAck -> createConsumer(tenantId, msg -> {
                ctx.failNow("shouldn't have received published message");
            }))
            .onSuccess(conAck -> mqttClient.publishHandler(errorMsg -> {
                final JsonObject payload = new JsonObject(errorMsg.payload());
                final String correlationId = payload.getString(MessageHelper.SYS_PROPERTY_CORRELATION_ID);
                ctx.verify(() -> {
                    assertThat(payload.getInteger("code")).isEqualTo(HttpURLConnection.HTTP_NOT_FOUND);
                    // error message should be about the device being unknown or disabled
                    assertThat(payload.getString("message")).isEqualTo(
                            ServiceInvocationException.getLocalizedMessage(
                                    "CLIENT_ERROR_DEVICE_DISABLED_OR_NOT_REGISTERED"));
                    // validate topic segments; example: error//myDeviceId/telemetry/4/503
                    final String[] topicSegments = errorMsg.topicName().split("/");
                    assertThat(topicSegments.length).isEqualTo(6);
                    assertThat(topicSegments[0]).isEqualTo("error");
                    assertThat(topicSegments[1]).isEqualTo(tenantId);
                    assertThat(topicSegments[2]).isEqualTo(deviceToSendTo);
                    assertThat(topicSegments[3]).isNotEmpty(); // endpoint used when sending the message that caused the error (e.g. "telemetry")
                    assertThat(topicSegments[4]).isEqualTo(correlationId);
                    assertThat(topicSegments[5]).isEqualTo(Integer.toString(HttpURLConnection.HTTP_NOT_FOUND));

                });
                errorMsgReceived.flag();
            }))
            .compose(conAck -> subscribeToErrorTopic("error/%s/+/#".formatted(tenantId)))
            .onComplete(ctx.succeeding(ok -> sendWithCorrelationIdIfNeeded(
                    tenantId,
                    deviceToSendTo,
                    Buffer.buffer("payload"),
                    null)));
    }

    /**
     * Sends a message.
     * <p>
     * In case of a QoS 0 message, a UUID will be added to the topic's property bag as
     * the correlation ID.
     *
     * @param tenantId The tenant that the device belongs to.
     * @param deviceId The identifier of the device.
     * @param payload The message payload.
     * @param propertyBag Optional properties.
     * @return The outcome of sending the message.
     */
    protected final Future<String> sendWithCorrelationIdIfNeeded(
            final String tenantId,
            final String deviceId,
            final Buffer payload,
            final Map<String, String> propertyBag) {
        // QoS 0 messages won't have a packet-id, therefore add a correlation-id in the property bag in that case
        final String correlationId = UUID.randomUUID().toString();
        final Map<String, String> propertyBagToUse = Optional.ofNullable(propertyBag)
                .map(HashMap::new)
                .orElseGet(HashMap::new);
        if (getQos().value() == 0) {
            propertyBagToUse.put(MessageHelper.SYS_PROPERTY_CORRELATION_ID, correlationId);
        }
        return send(tenantId, deviceId, payload, false, true, propertyBagToUse)
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
                (payload) -> send(tenantId, deviceId, payload, useShortTopicName, true, null).map(String::valueOf),
                (messageHandler) -> createConsumer(tenantId, messageHandler));
    }

    /**
     * Uploads a number of messages and verifies that they are either received via the north bound consumer
     * or that corresponding error messages are published to the client on the error topic.
     *
     * @param ctx The test context.
     * @param tenantId The tenant identifier.
     * @param connection The MQTT connection future.
     * @param sender The message sender. The Future result is the correlation/message id of the sent message.
     * @param consumerSupplier The message consumer. The result may be succeeded with a {@code null} value in case
     *                         error message handling for a non-existing consumer shall get tested.
     * @throws InterruptedException if the test fails.
     */
    protected void doTestUploadMessages(
            final VertxTestContext ctx,
            final String tenantId,
            final Future<MqttConnAckMessage> connection,
            final Function<Buffer, Future<String>> sender,
            final Function<Handler<DownstreamMessage<? extends MessageContext>>, Future<MessageConsumer>> consumerSupplier)
            throws InterruptedException {

        final CountDownLatch received = new CountDownLatch(MESSAGES_TO_SEND);
        final AtomicInteger messageCount = new AtomicInteger(0);
        final AtomicLong lastReceivedTimestamp = new AtomicLong(0);
        // <correlation id of the sent telemetry/event message, errorMessageReceived promise>
        final AtomicBoolean consumerIsSet = new AtomicBoolean();

        final VertxTestContext setup = new VertxTestContext();
        connection.compose(ok -> consumerSupplier.apply(msg -> {
            LOGGER.trace("received {}", msg);
            ctx.verify(() -> {
                DownstreamMessageAssertions.assertTelemetryApiProperties(msg);
                DownstreamMessageAssertions.assertMessageContainsAdapterAndAddress(msg);
                assertThat(msg.getQos().ordinal()).isEqualTo(getQos().ordinal());
                assertAdditionalMessageProperties(msg);
            });
            received.countDown();
            lastReceivedTimestamp.set(System.currentTimeMillis());
            if (received.getCount() % 50 == 0) {
                LOGGER.info("messages received: {}", MESSAGES_TO_SEND - received.getCount());
            }
        }))
        .onSuccess(msgConsumer -> consumerIsSet.set(msgConsumer != null))
        .onComplete(setup.succeedingThenComplete());

        assertThat(setup.awaitCompletion(5, TimeUnit.SECONDS)).isTrue();
        if (setup.failed()) {
            ctx.failNow(setup.causeOfFailure());
            return;
        }

        customizeConnectedClient();

        final long start = System.currentTimeMillis();
        final long timeout = start + getTimeToWait();
        while (messageCount.get() < MESSAGES_TO_SEND) {
            final CountDownLatch messageHandlingCompleted = new CountDownLatch(1);
            context.runOnContext(go -> {
                final Buffer msg = Buffer.buffer("hello " + messageCount.getAndIncrement());
                sender.apply(msg).onComplete(sendAttempt -> {
                    if (sendAttempt.failed()) {
                        LOGGER.trace("error sending message {}", messageCount.get(), sendAttempt.cause());
                    }
                    if (messageCount.get() % 50 == 0) {
                        LOGGER.info("messages sent: " + messageCount.get());
                    }
                    messageHandlingCompleted.countDown();
                });
            });

            // cap time to wait for sending a message at the test's overall timeout
            final long sendTimeout = timeout - System.currentTimeMillis();
            if (sendTimeout <= 0 || !messageHandlingCompleted.await(sendTimeout, TimeUnit.MILLISECONDS)) {
                LOGGER.error("failed to send all messages within {}ms", getTimeToWait());
                break;
            }
        }

        // in case no consumer is set, waiting time needs to be longer (adapter will wait for
        // credit when creating the first downstream sender)
        final long timeToWait = timeout - System.currentTimeMillis() + (!consumerIsSet.get() ? 2000 : 0);
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

    /**
     * Subscribes to the MQTT adapter's error topic.
     *
     * @param errorTopic The topic to subscribe for. If {@code null}, errors for the
     *                   authenticated device are being subscribed for.
     * @return A future indicating the outcome of the subscribe request.
     */
    protected Future<Void> subscribeToErrorTopic(final String errorTopic) {
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
