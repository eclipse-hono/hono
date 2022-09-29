/*******************************************************************************
 * Copyright (c) 2019, 2022 Contributors to the Eclipse Foundation
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

import static com.google.common.truth.Truth.assertThat;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.eclipse.californium.core.CoapClient;
import org.eclipse.californium.core.CoapResponse;
import org.eclipse.californium.core.coap.CoAP;
import org.eclipse.californium.core.coap.CoAP.Type;
import org.eclipse.californium.core.coap.Request;
import org.eclipse.hono.application.client.DownstreamMessage;
import org.eclipse.hono.application.client.MessageConsumer;
import org.eclipse.hono.application.client.MessageContext;
import org.eclipse.hono.service.management.tenant.Tenant;
import org.eclipse.hono.tests.DownstreamMessageAssertions;
import org.eclipse.hono.tests.EnabledIfProtocolAdaptersAreRunning;
import org.eclipse.hono.tests.IntegrationTestSupport;
import org.eclipse.hono.util.EventConstants;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;


/**
 * Integration tests for uploading telemetry data to the CoAP adapter.
 *
 */
@ExtendWith(VertxExtension.class)
@EnabledIfProtocolAdaptersAreRunning(coapAdapter = true)
public class EventCoapIT extends CoapTestBase {

    private static final String POST_URI = "/" + EventConstants.EVENT_ENDPOINT;
    private static final String POST_URI_SHORT = "/" + EventConstants.EVENT_ENDPOINT_SHORT;
    private static final String PUT_URI_TEMPLATE = "%s/%s/%s";

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
    protected void assertAdditionalMessageProperties(final DownstreamMessage<? extends MessageContext> msg) {
        DownstreamMessageAssertions.assertMessageIsDurable(msg);
    }

    @Override
    protected String getPutResource(final int msgNo, final String tenant, final String deviceId) {
        return String.format(PUT_URI_TEMPLATE, getPostResource(msgNo), tenant, deviceId);
    }

    @Override
    protected String getPostResource(final int msgNo) {
        return msgNo % 2 == 0 ? POST_URI : POST_URI_SHORT;
    }

    @Override
    protected Type getMessageType() {
        return Type.CON;
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
        final String messagePayload = UUID.randomUUID().toString();

        helper.registry.addDeviceForTenant(tenantId, new Tenant(), deviceId, SECRET).onComplete(setup.succeedingThenComplete());

        assertThat(setup.awaitCompletion(IntegrationTestSupport.getTestSetupTimeout(), TimeUnit.SECONDS)).isTrue();
        if (setup.failed()) {
            ctx.failNow(setup.causeOfFailure());
            return;
        }

        // WHEN a device that belongs to the tenant publishes an event
        final Promise<CoapResponse> sendEvent = Promise.promise();
        final CoapClient client = getCoapClient();
        final Request eventRequest = createCoapRequest(CoAP.Code.PUT, Type.CON, getPutResource(tenantId, deviceId),
                messagePayload.getBytes());
        client.advanced(getHandler(sendEvent), eventRequest);

        sendEvent.future()
                .onSuccess(eventSent -> {
                    logger.debug("event message has been sent");
                    // THEN create a consumer once the event message has been successfully sent
                    logger.debug("opening event consumer for tenant [{}]", tenantId);
                    createConsumer(tenantId, msg -> {
                        // THEN verify that the event message has been received by the consumer
                        logger.debug("event message has been received by the consumer");
                        ctx.verify(() -> assertThat(msg.getPayload().toString()).isEqualTo(messagePayload));
                        ctx.completeNow();
                    });
                })
                .onFailure(ctx::failNow);
    }
}
