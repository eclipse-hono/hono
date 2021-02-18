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
package org.eclipse.hono.tests.amqp;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.apache.qpid.proton.amqp.messaging.Accepted;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.application.client.DownstreamMessage;
import org.eclipse.hono.application.client.MessageConsumer;
import org.eclipse.hono.application.client.MessageContext;
import org.eclipse.hono.tests.AmqpMessageContextConditionalVerifier;
import org.eclipse.hono.tests.IntegrationTestSupport;
import org.eclipse.hono.util.AmqpErrorException;
import org.eclipse.hono.util.MessageHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.proton.ProtonHelper;
import io.vertx.proton.ProtonQoS;

/**
 * An Event based integration test for the AMQP adapter.
 */
@ExtendWith(VertxExtension.class)
public class EventAmqpIT extends AmqpUploadTestBase {

    private static final String EVENT_ENDPOINT = "event";

    static Stream<ProtonQoS> senderQoSTypes() {
        // events may only be published using AT LEAST ONCE delivery semantics
        return Stream.of(ProtonQoS.AT_LEAST_ONCE);
    }

    @Override
    protected Future<MessageConsumer> createConsumer(
            final String tenantId,
            final Handler<DownstreamMessage<? extends MessageContext>> messageConsumer) {
        return helper.applicationClient.createEventConsumer(tenantId, (Handler) messageConsumer, close -> {});
    }

    @Override
    protected String getEndpointName() {
        return EVENT_ENDPOINT;
    }

    @Override
    protected void assertAdditionalMessageProperties(final DownstreamMessage<? extends MessageContext> msg) {
        AmqpMessageContextConditionalVerifier.assertMessageIsDurable(msg);
    }

    /**
     * Verifies that an event message from a device has been successfully sent and a north bound application, 
     * which connects after the event has been sent, can successfully receive this event message.
     *
     * @param ctx The vert.x test context.
     * @throws InterruptedException if test execution gets interrupted.
     */
    @Test
    public void testEventMessageAlreadySentIsDeliveredWhenConsumerConnects(final VertxTestContext ctx)
            throws InterruptedException {
        final VertxTestContext setup = new VertxTestContext();
        final String tenantId = helper.getRandomTenantId();
        final String deviceId = helper.getRandomDeviceId(tenantId);
        final String messagePayload = UUID.randomUUID().toString();
        final Message event = ProtonHelper.message();

        setupProtocolAdapter(tenantId, deviceId, ProtonQoS.AT_LEAST_ONCE, false)
                .map(s -> sender = s)
                .onComplete(setup.completing());

        assertThat(setup.awaitCompletion(IntegrationTestSupport.getTestSetupTimeout(), TimeUnit.SECONDS)).isTrue();
        if (setup.failed()) {
            ctx.failNow(setup.causeOfFailure());
            return;
        }

        // WHEN a device that belongs to the tenant publishes an event
        MessageHelper.setPayload(event, "text/plain", Buffer.buffer(messagePayload));
        event.setAddress(getEndpointName());
        sender.send(event, delivery -> {
            if (delivery.getRemoteState() instanceof Accepted) {
                log.debug("event message has been sent");
                // THEN create a consumer once the event message has been successfully sent
                log.debug("opening event consumer for tenant [{}]", tenantId);
                createConsumer(tenantId, msg -> {
                    // THEN verify that the event message has been received by the consumer
                    log.debug("event message has been received by the consumer");
                    ctx.verify(() -> assertThat(msg.getPayload().toString()).isEqualTo(messagePayload));
                    ctx.completeNow();
                });
            } else {
                ctx.failNow(AmqpErrorException.from(delivery.getRemoteState()));
            }
        });
    }
}
