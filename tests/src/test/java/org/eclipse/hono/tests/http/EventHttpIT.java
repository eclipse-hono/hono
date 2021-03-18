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

package org.eclipse.hono.tests.http;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.HttpURLConnection;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.eclipse.hono.application.client.DownstreamMessage;
import org.eclipse.hono.application.client.MessageConsumer;
import org.eclipse.hono.application.client.MessageContext;
import org.eclipse.hono.service.management.tenant.Tenant;
import org.eclipse.hono.tests.AmqpMessageContextConditionalVerifier;
import org.eclipse.hono.tests.IntegrationTestSupport;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.EventConstants;
import org.eclipse.hono.util.QoS;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpHeaders;
import io.vertx.ext.web.client.predicate.ResponsePredicate;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;


/**
 * Integration tests for uploading events to the HTTP adapter.
 *
 */
@ExtendWith(VertxExtension.class)
public class EventHttpIT extends HttpTestBase {

    private static final String URI = String.format("/%s", EventConstants.EVENT_ENDPOINT);

    @Override
    protected String getEndpointUri() {
        return URI;
    }

    @Override
    protected Future<MessageConsumer> createConsumer(
            final String tenantId,
            final Handler<DownstreamMessage<? extends MessageContext>> messageConsumer) {

        return helper.applicationClient.createEventConsumer(tenantId, (Handler) messageConsumer, remoteClose -> {});
    }

    @Override
    protected void assertAdditionalMessageProperties(final DownstreamMessage<? extends MessageContext> msg) {
        AmqpMessageContextConditionalVerifier.assertMessageIsDurable(msg);
    }

    /**
     * Checks an event with an unsupported device QoS level is rejected.
     *
     * @param ctx The test context.
     *
     * @throws InterruptedException if the test fails.
     */
    @Test
    public void testEventIsRejectedForUnsupportedQosLevel(final VertxTestContext ctx) throws InterruptedException {
        final VertxTestContext setup = new VertxTestContext();
        final Tenant tenant = new Tenant();
        final MultiMap requestHeaders = MultiMap.caseInsensitiveMultiMap()
                .add(HttpHeaders.CONTENT_TYPE, "text/plain")
                .add(HttpHeaders.AUTHORIZATION, authorization)
                .add(HttpHeaders.ORIGIN, ORIGIN_URI)
                .add(Constants.HEADER_QOS_LEVEL, String.valueOf(QoS.AT_MOST_ONCE.ordinal()));

        helper.registry.addDeviceForTenant(tenantId, tenant, deviceId, PWD).onComplete(setup.completing());

        assertThat(setup.awaitCompletion(5, TimeUnit.SECONDS)).isTrue();
        if (setup.failed()) {
            ctx.failNow(setup.causeOfFailure());
            return;
        }

        httpClient.create(
                getEndpointUri(),
                Buffer.buffer("hello"),
                requestHeaders,
                ResponsePredicate.status(HttpURLConnection.HTTP_BAD_REQUEST))
                .onComplete(ctx.completing());
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
        final MultiMap requestHeaders = MultiMap.caseInsensitiveMultiMap()
                .add(HttpHeaders.CONTENT_TYPE, "text/plain")
                .add(HttpHeaders.AUTHORIZATION, authorization)
                .add(HttpHeaders.ORIGIN, ORIGIN_URI);

        helper.registry.addDeviceForTenant(tenantId, new Tenant(), deviceId, PWD).onComplete(setup.completing());

        assertThat(setup.awaitCompletion(IntegrationTestSupport.getTestSetupTimeout(), TimeUnit.SECONDS)).isTrue();
        if (setup.failed()) {
            ctx.failNow(setup.causeOfFailure());
            return;
        }

        // WHEN a device that belongs to the tenant publishes an event
        httpClient.create(
                getEndpointUri(),
                Buffer.buffer(messagePayload),
                requestHeaders,
                ResponsePredicate.status(HttpURLConnection.HTTP_ACCEPTED))
                .onSuccess(eventSent -> {
                    // THEN create a consumer once the event message has been successfully sent
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
