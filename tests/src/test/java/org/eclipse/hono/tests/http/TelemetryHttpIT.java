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

package org.eclipse.hono.tests.http;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.HttpURLConnection;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.MessageConsumer;
import org.eclipse.hono.client.NoConsumerException;
import org.eclipse.hono.client.SendMessageTimeoutException;
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.service.management.tenant.Tenant;
import org.eclipse.hono.tests.IntegrationTestSupport;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.TelemetryConstants;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpHeaders;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.predicate.ResponsePredicate;
import io.vertx.junit5.Checkpoint;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;


/**
 * Integration tests for uploading telemetry data to the HTTP adapter.
 *
 */
@ExtendWith(VertxExtension.class)
public class TelemetryHttpIT extends HttpTestBase {

    private static final String URI = "/" + TelemetryConstants.TELEMETRY_ENDPOINT;

    @Override
    protected String getEndpointUri() {
        return URI;
    }

    @Override
    protected Future<MessageConsumer> createConsumer(final String tenantId, final Consumer<Message> messageConsumer) {
        return helper.applicationClientFactory.createTelemetryConsumer(tenantId, messageConsumer, remoteClose -> {});
    }

    /**
     * Verifies that a number of telemetry messages uploaded to Hono's HTTP adapter
     * using QoS 1 can be successfully consumed via the AMQP Messaging Network.
     *
     * @param ctx The test context.
     * @throws InterruptedException if the test fails.
     */
    @Test
    public void testUploadUsingQoS1(final VertxTestContext ctx) throws InterruptedException {

        final VertxTestContext setup = new VertxTestContext();
        final Tenant tenant = new Tenant();
        final MultiMap requestHeaders = MultiMap.caseInsensitiveMultiMap()
                .add(HttpHeaders.CONTENT_TYPE, "binary/octet-stream")
                .add(HttpHeaders.AUTHORIZATION, authorization)
                .add(HttpHeaders.ORIGIN, ORIGIN_URI)
                .add(Constants.HEADER_QOS_LEVEL, "1");

        helper.registry.addDeviceForTenant(tenantId, tenant, deviceId, PWD)
                .onComplete(setup.completing());

        assertThat(setup.awaitCompletion(IntegrationTestSupport.getTestSetupTimeout(), TimeUnit.SECONDS)).isTrue();
        if (setup.failed()) {
            ctx.failNow(setup.causeOfFailure());
            return;
        }

        testUploadMessages(ctx, tenantId, count -> {
            return httpClient.create(
                    getEndpointUri(),
                    Buffer.buffer("hello " + count),
                    requestHeaders,
                    ResponsePredicate.status(HttpURLConnection.HTTP_ACCEPTED));
        });
    }

    /**
     * Verifies that the upload of a telemetry message containing a payload that
     * exceeds the HTTP adapter's configured max payload size fails with a 413
     * status code.
     *
     * @param ctx The test context
     */
    @Test
    public void testUploadMessageFailsForLargePayload(final VertxTestContext ctx) {

        // GIVEN a device
        final Tenant tenant = new Tenant();

        helper.registry
            .addDeviceForTenant(tenantId, tenant, deviceId, PWD)
            .compose(ok -> {

                // WHEN the device tries to upload a message that exceeds the max
                // payload size
                final MultiMap requestHeaders = MultiMap.caseInsensitiveMultiMap()
                        .add(HttpHeaders.CONTENT_TYPE, "text/plain")
                        .add(HttpHeaders.AUTHORIZATION, authorization);

                return httpClient.create(
                        getEndpointUri(),
                        Buffer.buffer(IntegrationTestSupport.getPayload(4096)),
                        requestHeaders,
                        ResponsePredicate.status(HttpURLConnection.HTTP_ENTITY_TOO_LARGE));

            })
            // THEN the message gets rejected by the HTTP adapter with a 413
            .onComplete(ctx.completing());
    }

    /**
     * Verifies that the upload of a telemetry message fails with a 503 status code
     * when there is no consumer.
     *
     * @param ctx The test context
     */
    @Test
    public void testUploadMessageFailsForNoConsumer(final VertxTestContext ctx) {

        // GIVEN a device
        final Tenant tenant = new Tenant();

        helper.registry
                .addDeviceForTenant(tenantId, tenant, deviceId, PWD)
                .compose(ok -> {

                    // WHEN the device tries to upload a telemetry message while there is no consumer for it
                    final MultiMap requestHeaders = MultiMap.caseInsensitiveMultiMap()
                            .add(HttpHeaders.CONTENT_TYPE, "text/plain")
                            .add(HttpHeaders.AUTHORIZATION, authorization);

                    return httpClient.create(
                            getEndpointUri(),
                            Buffer.buffer("hello"),
                            requestHeaders,
                            // THEN the message gets rejected by the HTTP adapter with a 503
                            ResponsePredicate.status(HttpURLConnection.HTTP_UNAVAILABLE));

                })
                .onComplete(ctx.succeeding(response -> {
                    ctx.verify(() -> {
                        assertThat(response.bodyAsString()).isEqualTo(ServiceInvocationException
                                .getLocalizedMessage(NoConsumerException.CLIENT_FACING_MESSAGE_KEY));
                    });
                    ctx.completeNow();
                }));
    }

    /**
     * Verifies that the upload of a QoS 1 telemetry message fails with a 503 status code
     * when the consumer doesn't update the message delivery state and the
     * <em>sendMessageTimeout</em> has elapsed.
     *
     * @param ctx The test context
     * @throws InterruptedException if test is interrupted while running.
     */
    @Test
    public void testUploadQos1MessageFailsIfDeliveryStateNotUpdated(final VertxTestContext ctx)
            throws InterruptedException {

        // GIVEN a device and a northbound message consumer that doesn't update the message delivery state
        final Tenant tenant = new Tenant();
        final Checkpoint messageReceived = ctx.checkpoint();
        final Checkpoint httpResponseReceived = ctx.checkpoint();

        final VertxTestContext setup = new VertxTestContext();
        final Checkpoint setupDone = setup.checkpoint();
        helper.registry
                .addDeviceForTenant(tenantId, tenant, deviceId, PWD)
                .compose(ok -> helper.applicationClientFactory.createTelemetryConsumer(
                        tenantId, 
                        (delivery, msg) -> {
                            logger.debug("received {}", msg);
                            messageReceived.flag();
                            // don't update the delivery state here
                        },
                        false, 
                        remoteClose -> {}))
                .onComplete(setup.succeeding(v -> setupDone.flag()));

        assertThat(setup.awaitCompletion(IntegrationTestSupport.getTestSetupTimeout(), TimeUnit.SECONDS)).isTrue();
        if (setup.failed()) {
            ctx.failNow(setup.causeOfFailure());
            return;
        }

        // WHEN the device tries to upload a telemetry message
        final MultiMap requestHeaders = MultiMap.caseInsensitiveMultiMap()
                .add(HttpHeaders.CONTENT_TYPE, "binary/octet-stream")
                .add(HttpHeaders.AUTHORIZATION, authorization)
                .add(HttpHeaders.ORIGIN, ORIGIN_URI)
                .add(Constants.HEADER_QOS_LEVEL, "1");

        final Future<HttpResponse<Buffer>> httpResponseFuture = httpClient.create(
                getEndpointUri(),
                Buffer.buffer("hello"),
                requestHeaders,
                // THEN the message gets rejected by the HTTP adapter with a 503
                ResponsePredicate.status(HttpURLConnection.HTTP_UNAVAILABLE));

        httpResponseFuture
                .onComplete(ctx.succeeding(response -> {
                    ctx.verify(() -> {
                        assertThat(response.bodyAsString()).isEqualTo(ServiceInvocationException
                                .getLocalizedMessage(SendMessageTimeoutException.CLIENT_FACING_MESSAGE_KEY));
                    });
                    httpResponseReceived.flag();
                }));
    }
}
