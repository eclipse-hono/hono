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

import java.util.function.Consumer;

import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.MessageConsumer;
import org.eclipse.hono.tests.IntegrationTestSupport;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.TelemetryConstants;
import org.eclipse.hono.util.TenantConstants;
import org.eclipse.hono.util.TenantObject;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.netty.handler.codec.mqtt.MqttQoS;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.mqtt.MqttClient;
import io.vertx.mqtt.MqttClientOptions;
import io.vertx.mqtt.messages.MqttConnAckMessage;


/**
 * Integration tests for uploading telemetry data to the MQTT adapter
 * using QoS 0.
 *
 */
@RunWith(VertxUnitRunner.class)
public class TelemetryMqttQoS0IT extends MqttTestBase {

    private static final String TOPIC_TEMPLATE = "%s/%s/%s";

    private boolean isTestEnvironment() {
        return Boolean.parseBoolean(System.getProperty("test.env", "false"));
    }

    @Override
    protected Future<Void> send(
            final String tenantId,
            final String deviceId,
            final Buffer payload,
            final boolean useShortTopicName) {

        final String topic = String.format(
                TOPIC_TEMPLATE,
                useShortTopicName ? TelemetryConstants.TELEMETRY_ENDPOINT_SHORT : TelemetryConstants.TELEMETRY_ENDPOINT,
                tenantId,
                deviceId);
        final Future<Void> result = Future.future();
        mqttClient.publish(topic, payload, MqttQoS.AT_MOST_ONCE, false, false, sendAttempt -> {
            if (sendAttempt.succeeded()) {
                result.complete();
            } else {
                result.fail(sendAttempt.cause());
            }
        });
        return result;
    }

    @Override
    protected void assertMessageReceivedRatio(final long received, final long sent, final TestContext ctx) {

        int expectedPercentage = 100;
        if (isTestEnvironment()) {
            LOGGER.info("running on CI test environment, allowing for 100 percent of messages to be lost ...");
            expectedPercentage = 0;
        } else {
            LOGGER.info("running on default environment, requiring 90 percent of messages to be received ...");
            expectedPercentage = 90;
        }
        final int expected = Math.round(sent * expectedPercentage / 100);
        if (received < expected) {
            // fail if less than 90% of sent messages have been received
            ctx.fail(String.format("did not receive expected number of messages [expected: %d, received: %d]",
                    expected, received));
        }
    }

    @Override
    protected long getTimeToWait() {
        if (isTestEnvironment()) {
            return MESSAGES_TO_SEND * 100;
        } else {
            return MESSAGES_TO_SEND * 20;
        }
    }

    @Override
    protected Future<MessageConsumer> createConsumer(final String tenantId, final Consumer<Message> messageConsumer) {

        return helper.honoClient.createTelemetryConsumer(tenantId, messageConsumer, remoteClose -> {});
    }

    /**
     * Verifies that the adapter rejects connection attempts from devices
     * that belong to a tenant for which the adapter has been disabled.
     *
     * @param ctx The test context
     */
    @Test
    public void testConnectFailsForDisabledTenant(final TestContext ctx) {

        // GIVEN a tenant for which the HTTP adapter is disabled
        final Async rejected = ctx.async();
        final String tenantId = helper.getRandomTenantId();
        final String deviceId = helper.getRandomDeviceId(tenantId);
        final String password = "secret";
        final JsonObject adapterDetailsHttp = new JsonObject()
                .put(TenantConstants.FIELD_ADAPTERS_TYPE, Constants.PROTOCOL_ADAPTER_TYPE_MQTT)
                .put(TenantConstants.FIELD_ENABLED, Boolean.FALSE);
        final TenantObject tenant = TenantObject.from(tenantId, true);
        tenant.addAdapterConfiguration(adapterDetailsHttp);

        helper.registry.addDeviceForTenant(tenant, deviceId, password)
            .compose(ok -> {
                final Future<MqttConnAckMessage> result = Future.future();
                final MqttClientOptions options = new MqttClientOptions()
                        .setUsername(IntegrationTestSupport.getUsername(deviceId, tenantId))
                        .setPassword(password);
                mqttClient = MqttClient.create(VERTX, options);
                // WHEN a device that belongs to the tenant tries to connect to the adapter
                mqttClient.connect(IntegrationTestSupport.MQTT_PORT, IntegrationTestSupport.MQTT_HOST, result.completer());
                return result;
            }).setHandler(ctx.asyncAssertFailure(t -> {
                // THEN the connection attempt gets rejected
                rejected.complete();
            }));

        rejected.await(TEST_TIMEOUT);
    }
}
