/**
 * Copyright (c) 2021, 2023 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */


package org.eclipse.hono.adapter.coap;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static com.google.common.truth.Truth.assertThat;

import java.net.HttpURLConnection;
import java.util.concurrent.TimeUnit;

import org.eclipse.californium.core.coap.CoAP.ResponseCode;
import org.eclipse.californium.core.coap.CoAP.Type;
import org.eclipse.californium.core.coap.MediaTypeRegistry;
import org.eclipse.californium.core.coap.Response;
import org.eclipse.californium.core.server.resources.CoapExchange;
import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.service.auth.DeviceUser;
import org.eclipse.hono.service.metric.MetricsTags;
import org.eclipse.hono.service.metric.MetricsTags.TtdStatus;
import org.eclipse.hono.util.TenantObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.opentracing.noop.NoopTracerFactory;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;


/**
 * Tests verifying behavior of {@link EventResource}.
 */
@ExtendWith(VertxExtension.class)
@Timeout(value = 5, timeUnit = TimeUnit.SECONDS)
public class EventResourceTest extends ResourceTestBase {

    private EventResource givenAResource(final CoapProtocolAdapter adapter) {

        return new EventResource(adapter, NoopTracerFactory.create(), vertx);
    }

    /**
     * Verifies that the adapter waits for an event being send with wait for outcome before responding with a 2.04
     * status to the device.
     */
    @Test
    public void testUploadEventWaitsForAcceptedOutcome() {

        // GIVEN an adapter with a downstream event consumer attached
        givenAnAdapter(properties);
        final Promise<Void> outcome = Promise.promise();
        givenAnEventSenderForAnyTenant(outcome);
        final var resource = givenAResource(adapter);

        // WHEN a device publishes an event
        final Buffer payload = Buffer.buffer("some payload");
        final CoapExchange coapExchange = newCoapExchange(payload, Type.CON, MediaTypeRegistry.TEXT_PLAIN);
        final var authenticatedDevice = new DeviceUser("tenant", "device");
        final CoapContext context = CoapContext.fromRequest(coapExchange, authenticatedDevice, authenticatedDevice, "device", span);

        resource.handlePostRequest(context);

        // THEN the message is being forwarded downstream
        assertEventHasBeenSentDownstream("tenant", "device", "text/plain");
        // but the device does not get a response
        verify(coapExchange, never()).respond(any(Response.class));

        // until the event has been accepted
        outcome.complete();

        verify(coapExchange).respond(argThat((Response res) -> ResponseCode.CHANGED.equals(res.getCode())));
        verify(metrics).reportTelemetry(
                eq(MetricsTags.EndpointType.EVENT),
                eq("tenant"),
                any(),
                eq(MetricsTags.ProcessingOutcome.FORWARDED),
                eq(MetricsTags.QoS.AT_LEAST_ONCE),
                eq(payload.length()),
                eq(TtdStatus.NONE),
                any());
    }

    /**
     * Verifies that the adapter fails the upload of an event with a 4.00 result if it is rejected by the downstream
     * peer.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testUploadEventFailsForRejectedOutcome(final VertxTestContext ctx) {

        // GIVEN an adapter with a downstream event consumer attached
        givenAnAdapter(properties);
        final Promise<Void> outcome = Promise.promise();
        givenAnEventSenderForAnyTenant(outcome);
        final var resource = givenAResource(adapter);

        // WHEN a device publishes an event that is not accepted by the peer
        final Buffer payload = Buffer.buffer("some payload");
        final CoapExchange coapExchange = newCoapExchange(payload, Type.CON, MediaTypeRegistry.TEXT_PLAIN);
        final var authenticatedDevice = new DeviceUser("tenant", "device");
        final CoapContext context = CoapContext.fromRequest(coapExchange, authenticatedDevice, authenticatedDevice, "device", span);

        final Future<Void> result = resource.handlePostRequest(context);

        assertEventHasBeenSentDownstream("tenant", "device", "text/plain");
        outcome.fail(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST, "malformed message"));

        // THEN the device gets a 4.00
        result.onComplete(ctx.failing(t -> {
            ctx.verify(() -> {
                assertThat(result.cause()).isInstanceOf(ClientErrorException.class);
                assertThat(((ClientErrorException) result.cause()).getErrorCode())
                        .isEqualTo(HttpURLConnection.HTTP_BAD_REQUEST);
                verify(metrics).reportTelemetry(
                        eq(MetricsTags.EndpointType.EVENT),
                        eq("tenant"),
                        any(),
                        eq(MetricsTags.ProcessingOutcome.UNPROCESSABLE),
                        eq(MetricsTags.QoS.AT_LEAST_ONCE),
                        eq(payload.length()),
                        eq(TtdStatus.NONE),
                        any());
            });
            ctx.completeNow();
        }));

    }

    /**
     * Verifies that an event message is rejected due to the limit exceeded.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testMessageLimitExceededForAnEventMessage(final VertxTestContext ctx) {

        // GIVEN an adapter with a downstream event consumer attached
        givenAnAdapter(properties);
        givenAnEventSenderForAnyTenant();
        final var resource = givenAResource(adapter);

        // WHEN the message limit exceeds
        when(adapter.checkMessageLimit(any(TenantObject.class), anyLong(), any())).thenReturn(Future.failedFuture(new ClientErrorException(429)));

        // WHEN a device publishes an event message
        final Buffer payload = Buffer.buffer("some payload");
        final CoapExchange coapExchange = newCoapExchange(payload, Type.CON, MediaTypeRegistry.TEXT_PLAIN);
        final var authenticatedDevice = new DeviceUser("tenant", "device");
        final CoapContext context = CoapContext.fromRequest(coapExchange, authenticatedDevice, authenticatedDevice, "device", span);

        resource.handlePostRequest(context)
            .onComplete(ctx.failing(t -> {
                ctx.verify(() -> {
                    // THEN the message is not being forwarded downstream
                    assertNoEventHasBeenSentDownstream();
                    // and the device gets a 4.29
                    assertThat(t).isInstanceOf(ClientErrorException.class);
                    assertThat(((ClientErrorException) t).getErrorCode()).isEqualTo(429);
                    verify(metrics).reportTelemetry(
                            eq(MetricsTags.EndpointType.EVENT),
                            eq("tenant"),
                            any(),
                            eq(MetricsTags.ProcessingOutcome.UNPROCESSABLE),
                            eq(MetricsTags.QoS.AT_LEAST_ONCE),
                            eq(payload.length()),
                            eq(TtdStatus.NONE),
                            any());
                });
                ctx.completeNow();
            }));
    }

}
