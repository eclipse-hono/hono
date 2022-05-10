/*******************************************************************************
 * Copyright (c) 2016, 2022 Contributors to the Eclipse Foundation
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
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.eclipse.hono.application.client.DownstreamMessage;
import org.eclipse.hono.application.client.MessageConsumer;
import org.eclipse.hono.application.client.MessageContext;
import org.eclipse.hono.client.NoConsumerException;
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.service.management.tenant.Tenant;
import org.eclipse.hono.tests.AssumeMessagingSystem;
import org.eclipse.hono.tests.IntegrationTestSupport;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.MessagingType;
import org.eclipse.hono.util.TelemetryConstants;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.netty.handler.codec.mqtt.MqttQoS;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

/**
 * Integration tests for uploading telemetry data to the MQTT adapter
 * using QoS 1.
 *
 */
@ExtendWith(VertxExtension.class)
public class TelemetryMqttQoS1IT extends MqttPublishTestBase {

    private static final String TOPIC_TEMPLATE = "%s/%s/%s";

    @Override
    protected MqttQoS getQos() {
        return MqttQoS.AT_LEAST_ONCE;
    }

    @Override
    protected Future<Integer> send(
            final String tenantId,
            final String deviceId,
            final Buffer payload,
            final boolean useShortTopicName,
            final boolean includeTenantIdInTopic,
            final Map<String, String> topicPropertyBag) {

        return send(
                tenantId,
                deviceId,
                payload,
                useShortTopicName,
                includeTenantIdInTopic,
                topicPropertyBag,
                DEFAULT_PUBLISH_COMPLETION_TIMEOUT);
    }

    private Future<Integer> send(
            final String tenantId,
            final String deviceId,
            final Buffer payload,
            final boolean useShortTopicName,
            final boolean includeTenantIdInTopic,
            final Map<String, String> topicPropertyBag,
            final long publishCompletionTimeout) {

        final String topic = String.format(
                TOPIC_TEMPLATE,
                useShortTopicName ? TelemetryConstants.TELEMETRY_ENDPOINT_SHORT : TelemetryConstants.TELEMETRY_ENDPOINT,
                includeTenantIdInTopic ? tenantId : "",
                deviceId);
        final Promise<Integer> result = Promise.promise();
        mqttClient.publish(
                getTopicWithPropertyBag(topic, topicPropertyBag),
                payload,
                getQos(),
                false, // is duplicate
                false, // is retained
                sendAttempt -> handlePublishAttempt(sendAttempt, result, publishCompletionTimeout));
        return result.future();
    }

    @Override
    protected Future<MessageConsumer> createConsumer(
            final String tenantId,
            final Handler<DownstreamMessage<? extends MessageContext>> messageConsumer) {

        return helper.applicationClient
                .createTelemetryConsumer(tenantId, (Handler) messageConsumer, remoteClose -> {});
    }

    /**
     * Verifies that sending a number of telemetry messages to Hono's MQTT adapter while there is no consumer causes
     * corresponding error messages to be published to the client if it is subscribed on the error topic.
     *
     * @param ctx The test context.
     * @throws InterruptedException if the test fails.
     */
    @Test
    @AssumeMessagingSystem(type = MessagingType.amqp)
    public void testUploadMessagesWithNoConsumerSendsErrors(final VertxTestContext ctx) throws InterruptedException {

        final String tenantId = helper.getRandomTenantId();
        final String deviceId = helper.getRandomDeviceId(tenantId);
        final Tenant tenant = new Tenant();

        final VertxTestContext setup = new VertxTestContext();

        helper.registry.addDeviceForTenant(tenantId, tenant, deviceId, password).onComplete(setup.succeedingThenComplete());

        assertThat(setup.awaitCompletion(5, TimeUnit.SECONDS)).isTrue();
        if (setup.failed()) {
            ctx.failNow(setup.causeOfFailure());
            return;
        }

        // timeout value should be higher than the "hono.messaging.flowLatency" MQTT adapter configuration value
        // so that no timeout doesn't while the MQTT adapter waits for credit after having created the first downstream
        // sender link
        final long publishCompletionTimeout = 1500;
        doTestUploadMessages(
                ctx,
                tenantId,
                connectToAdapter(IntegrationTestSupport.getUsername(deviceId, tenantId), password),
                (payload) -> send(tenantId, deviceId, payload, true, true, null, publishCompletionTimeout)
                        .map(String::valueOf),
                (messageHandler) -> Future.succeededFuture(), // no consumer created here (future succeeded with null value)
                (msg) -> {
                    final JsonObject payload = new JsonObject(msg.payload());
                    final String correlationId = payload.getString(MessageHelper.SYS_PROPERTY_CORRELATION_ID);
                    ctx.verify(() -> {
                        assertThat(payload.getInteger("code")).isEqualTo(HttpURLConnection.HTTP_UNAVAILABLE);
                        // error message should be the localized NoConsumerException message
                        assertThat(payload.getString("message")).isEqualTo(ServiceInvocationException
                                .getLocalizedMessage(NoConsumerException.CLIENT_FACING_MESSAGE_KEY));
                        // validate topic segments; example: error//myDeviceId/telemetry/4/503
                        final String[] topicSegments = msg.topicName().split("/");
                        assertThat(topicSegments.length).isEqualTo(6);
                        assertThat(topicSegments[0]).isEqualTo("e");
                        assertThat(topicSegments[1]).isEqualTo(tenantId);
                        assertThat(topicSegments[2]).isEmpty(); // device
                        assertThat(topicSegments[3]).isEqualTo(TelemetryConstants.TELEMETRY_ENDPOINT_SHORT);
                        assertThat(topicSegments[4]).isEqualTo(correlationId);
                        assertThat(topicSegments[5]).isEqualTo(Integer.toString(HttpURLConnection.HTTP_UNAVAILABLE));
                    });
                    return Future.succeededFuture(correlationId);
                },
                String.format("e/%s//#", tenantId));
    }
}
