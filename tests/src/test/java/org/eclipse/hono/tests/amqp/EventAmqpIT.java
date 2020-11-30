/*******************************************************************************
 * Copyright (c) 2016, 2020 Contributors to the Eclipse Foundation
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

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.apache.qpid.proton.amqp.messaging.Accepted;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.MessageConsumer;
import org.eclipse.hono.util.AmqpErrorException;
import org.eclipse.hono.util.MessageHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.Future;
import io.vertx.core.Promise;
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
    protected Future<MessageConsumer> createConsumer(final String tenantId, final Consumer<Message> messageConsumer) {
        return helper.applicationClientFactory.createEventConsumer(tenantId, messageConsumer, close -> {});
    }

    @Override
    protected String getEndpointName() {
        return EVENT_ENDPOINT;
    }

    @Override
    protected void assertAdditionalMessageProperties(final VertxTestContext ctx, final Message msg) {
        // assert that events are marked as "durable"
        ctx.verify(() -> {
            assertThat(msg.isDurable()).isTrue();
        });
    }

    /**
     * Verifies that an event message from a device has been successfully sent and a north bound application, 
     * which connects after the event has been sent, can successfully receive those event message.
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

        setupProtocolAdapter(tenantId, deviceId, ProtonQoS.AT_LEAST_ONCE, false)
                .map(s -> sender = s)
                .onComplete(setup.completing());

        assertThat(setup.awaitCompletion(5, TimeUnit.SECONDS)).isTrue();
        if (setup.failed()) {
            ctx.failNow(setup.causeOfFailure());
            return;
        }

        // WHEN a device that belongs to the tenant publishes an event
        final AtomicInteger receivedMessageCount = new AtomicInteger(0);
        final Message event = ProtonHelper.message();
        MessageHelper.setPayload(event, "opaque/binary", Buffer.buffer("hello"));
        event.setAddress(getEndpointName());
        final Promise<?> sendingComplete = Promise.promise();
        sender.send(event, delivery -> {
            if (Accepted.class.isInstance(delivery.getRemoteState())) {
                sendingComplete.complete();
            } else {
                sendingComplete.fail(AmqpErrorException.from(delivery.getRemoteState()));
            }
        });

        sendingComplete
                .future()
                .compose(eventSent -> {
                    //THEN create a consumer once the event message has been successfully sent
                    final Promise<MessageConsumer> consumerCreated = Promise.promise();
                    vertx.setTimer(4000, tid -> {
                        log.info("opening event consumer for tenant [{}]", tenantId);
                        createConsumer(tenantId, msg -> receivedMessageCount.incrementAndGet())
                                .onComplete(consumerCreated);
                    });
                    return consumerCreated.future();
                })
                .compose(consumer -> {
                    final Promise<Void> done = Promise.promise();
                    vertx.setTimer(1000, tid -> {
                        //THEN verify if the message is received by the consumer
                        assertThat(receivedMessageCount.get()).isEqualTo(1);
                        done.complete();
                    });
                    return done.future();
                }).onComplete(ctx.completing());
    }
}
