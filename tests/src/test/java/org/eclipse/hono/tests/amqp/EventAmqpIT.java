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
package org.eclipse.hono.tests.amqp;

import static com.google.common.truth.Truth.assertThat;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.apache.qpid.proton.amqp.messaging.Accepted;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.application.client.DownstreamMessage;
import org.eclipse.hono.application.client.MessageConsumer;
import org.eclipse.hono.application.client.MessageContext;
import org.eclipse.hono.client.amqp.connection.AmqpUtils;
import org.eclipse.hono.service.management.tenant.Tenant;
import org.eclipse.hono.tests.DownstreamMessageAssertions;
import org.eclipse.hono.tests.EnabledIfProtocolAdaptersAreRunning;
import org.eclipse.hono.tests.IntegrationTestSupport;
import org.eclipse.hono.util.ResourceLimits;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.proton.ProtonDelivery;
import io.vertx.proton.ProtonHelper;
import io.vertx.proton.ProtonQoS;

/**
 * An Event based integration test for the AMQP adapter.
 */
@ExtendWith(VertxExtension.class)
@EnabledIfProtocolAdaptersAreRunning(amqpAdapter = true)
public class EventAmqpIT extends AmqpUploadTestBase {

    private static final String EVENT_ENDPOINT = "event";
    private static final Duration TTL = Duration.ofSeconds(60L);

    static Stream<ProtonQoS> senderQoSTypes() {
        // events may only be published using AT LEAST ONCE delivery semantics
        return Stream.of(ProtonQoS.AT_LEAST_ONCE);
    }

    @Override
    protected Future<MessageConsumer> createConsumer(
            final String tenantId,
            final Handler<DownstreamMessage<? extends MessageContext>> messageConsumer) {
        return helper.applicationClient.createEventConsumer(
                tenantId,
                messageConsumer::handle,
                close -> {});
    }

    @Override
    protected void prepareTenantConfig(final Tenant config) {
        config.setResourceLimits(new ResourceLimits().setMaxTtl(TTL.toSeconds()));
    }

    @Override
    protected String getEndpointName() {
        return EVENT_ENDPOINT;
    }

    @Override
    protected void assertAdditionalMessageProperties(final DownstreamMessage<? extends MessageContext> msg) {
        DownstreamMessageAssertions.assertMessageIsDurable(msg);
        DownstreamMessageAssertions.assertMessageContainsTimeToLive(msg, TTL);
    }

    /**
     * Verifies that an event message from a device has been successfully sent and a north bound application, 
     * which connects after the event has been sent, can successfully receive this event message.
     *
     * @param ctx The vert.x test context.
     * @throws InterruptedException if test execution gets interrupted.
     */
    @Test
    @Timeout(value = 20, timeUnit = TimeUnit.SECONDS)
    public void testEventMessageAlreadySentIsDeliveredWhenConsumerConnects(final VertxTestContext ctx)
            throws InterruptedException {
        final VertxTestContext setup = new VertxTestContext();
        final String tenantId = helper.getRandomTenantId();
        final String deviceId = helper.getRandomDeviceId(tenantId);
        final String messagePayload = UUID.randomUUID().toString();

        setupProtocolAdapter(tenantId, new Tenant(), deviceId, ProtonQoS.AT_LEAST_ONCE)
                .onSuccess(s -> sender = s)
                .onComplete(setup.succeedingThenComplete());

        assertThat(setup.awaitCompletion(IntegrationTestSupport.getTestSetupTimeout(), TimeUnit.SECONDS)).isTrue();
        if (setup.failed()) {
            ctx.failNow(setup.causeOfFailure());
            return;
        }

        // WHEN a device that belongs to the tenant publishes an event
        final Message event = ProtonHelper.message();
        event.setAddress(getEndpointName());
        AmqpUtils.setPayload(event, "text/plain", Buffer.buffer(messagePayload));

        boolean eventAccepted = false;

        while (!eventAccepted) {
            final CompletableFuture<ProtonDelivery> deliveryUpdate = new CompletableFuture<>();
            sender.send(event, deliveryUpdate::complete);
            try {
                final var delivery = deliveryUpdate.join();
                eventAccepted = Accepted.class.isInstance(delivery.getRemoteState());
            } catch (final CompletionException e) {
                log.warn("failed to send event: {}", e.getCause().getMessage());
            }
        }

        log.debug("opening event consumer for tenant [{}]", tenantId);
        createConsumer(tenantId, msg -> {
            // THEN the event message has been received by the consumer
            log.debug("event message has been received by the consumer");
            ctx.verify(() -> assertThat(msg.getPayload().toString()).isEqualTo(messagePayload));
            ctx.completeNow();
        });
    }
}
