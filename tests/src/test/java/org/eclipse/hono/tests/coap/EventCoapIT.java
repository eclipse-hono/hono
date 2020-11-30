/*******************************************************************************
 * Copyright (c) 2019, 2020 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.tests.coap;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import org.apache.qpid.proton.message.Message;
import org.eclipse.californium.core.CoapClient;
import org.eclipse.californium.core.coap.CoAP;
import org.eclipse.californium.core.coap.CoAP.Type;
import org.eclipse.californium.core.coap.OptionSet;
import org.eclipse.californium.core.coap.Request;
import org.eclipse.hono.client.MessageConsumer;
import org.eclipse.hono.service.management.tenant.Tenant;
import org.eclipse.hono.util.EventConstants;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;


/**
 * Integration tests for uploading telemetry data to the CoAP adapter.
 *
 */
@ExtendWith(VertxExtension.class)
public class EventCoapIT extends CoapTestBase {

    private static final String POST_URI = "/" + EventConstants.EVENT_ENDPOINT;
    private static final String PUT_URI_TEMPLATE = POST_URI + "/%s/%s";

    @Override
    protected Future<MessageConsumer> createConsumer(final String tenantId, final Consumer<Message> messageConsumer) {

        return helper.applicationClientFactory.createEventConsumer(tenantId, messageConsumer, remoteClose -> {});
    }

    @Override
    protected String getPutResource(final String tenant, final String deviceId) {
        return String.format(PUT_URI_TEMPLATE, tenant, deviceId);
    }

    @Override
    protected String getPostResource() {
        return POST_URI;
    }

    @Override
    protected Type getMessageType() {
        return Type.CON;
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

        helper.registry.addDeviceForTenant(tenantId, new Tenant(), deviceId, SECRET).onComplete(setup.completing());

        assertThat(setup.awaitCompletion(5, TimeUnit.SECONDS)).isTrue();
        if (setup.failed()) {
            ctx.failNow(setup.causeOfFailure());
            return;
        }

        // WHEN a device that belongs to the tenant publishes an event
        final Promise<OptionSet> sendEvent = Promise.promise();
        final CoapClient client = getCoapClient();
        final Request eventRequest =  createCoapRequest(CoAP.Code.PUT, getPutResource(tenantId, deviceId), 1);
        client.advanced(getHandler(sendEvent), eventRequest);

        final AtomicInteger receivedMessageCount = new AtomicInteger(0);
        sendEvent.future()
                .compose(eventSent -> {
                    //THEN create a consumer once the event message has been successfully sent
                    final Promise<MessageConsumer> consumerCreated = Promise.promise();
                    vertx.setTimer(4000, tid -> {
                        logger.info("opening event consumer for tenant [{}]", tenantId);
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
