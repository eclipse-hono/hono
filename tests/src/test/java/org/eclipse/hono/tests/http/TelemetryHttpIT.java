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

package org.eclipse.hono.tests.http;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.HttpURLConnection;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.MessageConsumer;
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

        assertThat(setup.awaitCompletion(5, TimeUnit.SECONDS)).isTrue();
        if (setup.failed()) {
            ctx.failNow(setup.causeOfFailure());
            return;
        }

        testUploadMessages(ctx, tenantId, count -> {
            return httpClient.create(
                    getEndpointUri(),
                    Buffer.buffer("hello " + count),
                    requestHeaders,
                    response -> response.statusCode() == HttpURLConnection.HTTP_ACCEPTED);
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
                        response -> response.statusCode() == HttpURLConnection.HTTP_ACCEPTED)
                        .recover(HttpProtocolException::transformInto);

            })
            .onComplete(ctx.failing(t -> {

                // THEN the message gets rejected by the HTTP adapter with a 413
                ctx.verify(() ->  HttpProtocolException.assertProtocolError(HttpURLConnection.HTTP_ENTITY_TOO_LARGE, t));
                ctx.completeNow();
            }));
    }
}
