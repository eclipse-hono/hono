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

import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.MessageConsumer;
import org.eclipse.hono.service.management.tenant.Tenant;
import org.eclipse.hono.tests.IntegrationTestSupport;
import org.eclipse.hono.util.EventConstants;
import org.eclipse.hono.util.MessageHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.netty.handler.codec.mqtt.MqttQoS;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;


/**
 * Integration tests for uploading events to the MQTT adapter.
 *
 */
@ExtendWith(VertxExtension.class)
public class EventMqttIT extends MqttPublishTestBase {

    private static final String TOPIC_TEMPLATE = "%s/%s/%s";

    @Override
    protected Future<Void> send(
            final String tenantId,
            final String deviceId,
            final Buffer payload,
            final boolean useShortTopicName) {
        return send(tenantId, deviceId, payload, useShortTopicName, this::handlePublishAttempt);
    }

    private Future<Void> send(
            final String tenantId,
            final String deviceId,
            final Buffer payload,
            final boolean useShortTopicName,
            final BiConsumer<AsyncResult<Integer>, Promise<Void>> sendAttemptHandler) {

        final String topic = String.format(
                TOPIC_TEMPLATE,
                useShortTopicName ? EventConstants.EVENT_ENDPOINT_SHORT : EventConstants.EVENT_ENDPOINT,
                tenantId,
                deviceId);
        final Promise<Void> result = Promise.promise();
        mqttClient.publish(
                topic,
                payload,
                MqttQoS.AT_LEAST_ONCE,
                false, // is duplicate
                false, // is retained
                sendAttempt -> sendAttemptHandler.accept(sendAttempt, result));
        return result.future();
    }

    @Override
    protected Future<MessageConsumer> createConsumer(final String tenantId, final Consumer<Message> messageConsumer) {

        return helper.applicationClientFactory.createEventConsumer(tenantId, messageConsumer, remoteClose -> {});
    }

    @Override
    protected void assertAdditionalMessageProperties(final VertxTestContext ctx, final Message msg) {
        // assert that events are marked as "durable"
        ctx.verify(() -> assertThat(msg.isDurable()).isTrue());
    }

    /**
     * Verifies that an event from a device for which a default TTL has been
     * specified cannot be consumed after the TTL has expired.
     * 
     * @param ctx The vert.x test context.
     * @throws InterruptedException if test execution gets interrupted.
     */
    @Test
    public void testMessagesExpire(final VertxTestContext ctx) throws InterruptedException {

        // GIVEN a tenant for which all messages have a TTL of 500ms
        final String tenantId = helper.getRandomTenantId();
        final String deviceId = helper.getRandomDeviceId(tenantId);
        final Tenant tenant = new Tenant();
        tenant.setDefaults(Map.of(MessageHelper.SYS_HEADER_PROPERTY_TTL, 3)); // seconds
        final VertxTestContext setup = new VertxTestContext();

        helper.registry.addDeviceForTenant(tenantId, tenant, deviceId, "secret").onComplete(setup.completing());

        assertThat(setup.awaitCompletion(5, TimeUnit.SECONDS)).isTrue();
        if (setup.failed()) {
            ctx.failNow(setup.causeOfFailure());
        }

        // WHEN a device that belongs to the tenant publishes an event
        final AtomicInteger receivedMessageCount = new AtomicInteger(0);
        connectToAdapter(IntegrationTestSupport.getUsername(deviceId, tenantId), "secret")
        .compose(connack -> send(tenantId, deviceId, Buffer.buffer("hello"), false, (sendAttempt, result) -> {
            if (sendAttempt.succeeded()) {
                LOGGER.info("successfully sent event [tenant-id: {}, device-id: {}", tenantId, deviceId);
                result.complete();
            } else {
                result.fail(sendAttempt.cause());
            }
        }))
        .compose(ok -> {
            final Promise<MessageConsumer> consumerCreated = Promise.promise();
            VERTX.setTimer(4000, tid -> {
                LOGGER.info("opening event consumer for tenant [{}]", tenantId);
                // THEN no messages can be consumed after the TTL has expired
                createConsumer(tenantId, msg -> receivedMessageCount.incrementAndGet())
                .onComplete(consumerCreated);
            });
            return consumerCreated.future();
        })
        .compose(c -> {
            final Promise<Void> done = Promise.promise();
            VERTX.setTimer(1000, tid -> {
                if (receivedMessageCount.get() > 0) {
                    done.fail(new IllegalStateException("should not have received any events after TTL has expired"));
                } else {
                    done.complete();
                }
            });
            return done.future();
        }).onComplete(ctx.completing());
    }
}
