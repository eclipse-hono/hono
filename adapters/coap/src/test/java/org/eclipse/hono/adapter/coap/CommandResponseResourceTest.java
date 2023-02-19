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
import static org.mockito.ArgumentMatchers.anyInt;
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
import org.eclipse.californium.core.coap.OptionSet;
import org.eclipse.californium.core.coap.Response;
import org.eclipse.californium.core.server.resources.CoapExchange;
import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.command.CommandResponse;
import org.eclipse.hono.client.command.CommandResponseSender;
import org.eclipse.hono.client.command.Commands;
import org.eclipse.hono.service.auth.DeviceUser;
import org.eclipse.hono.service.metric.MetricsTags.Direction;
import org.eclipse.hono.service.metric.MetricsTags.ProcessingOutcome;
import org.eclipse.hono.util.CommandConstants;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.MessagingType;
import org.eclipse.hono.util.RegistrationAssertion;
import org.eclipse.hono.util.TenantObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.opentracing.SpanContext;
import io.opentracing.noop.NoopTracerFactory;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;


/**
 * Tests verifying behavior of {@link CommandResponseResource}.
 *
 */
@ExtendWith(VertxExtension.class)
@Timeout(value = 5, timeUnit = TimeUnit.SECONDS)
public class CommandResponseResourceTest extends ResourceTestBase {

    private CommandResponseResource givenAResource(final CoapProtocolAdapter adapter) {

        return new CommandResponseResource(adapter, NoopTracerFactory.create(), vertx);
    }

    /**
     * Verifies that the adapter fails the upload of a command response with a 4.03
     * if the adapter is disabled for the device's tenant.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testUploadCommandResponseFailsForDisabledTenant(final VertxTestContext ctx) {

        // GIVEN an adapter
        givenAnAdapter(properties);
        final var resource = givenAResource(adapter);
        final Promise<Void> outcome = Promise.promise();
        final CommandResponseSender sender = givenACommandResponseSenderForAnyTenant(outcome);
        // that is not enabled for a device's tenant
        when(adapter.isAdapterEnabled(any(TenantObject.class)))
            .thenReturn(Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_FORBIDDEN)));

        // WHEN a device publishes an command response
        final String reqId = Commands.encodeRequestIdParameters("correlation", "replyToId", "device",
                MessagingType.amqp);
        final Buffer payload = Buffer.buffer("some payload");
        final OptionSet options = new OptionSet();
        options.addUriPath(CommandConstants.COMMAND_RESPONSE_ENDPOINT).addUriPath(reqId);
        options.addUriQuery(String.format("%s=%d", Constants.HEADER_COMMAND_RESPONSE_STATUS, 200));
        options.setContentFormat(MediaTypeRegistry.TEXT_PLAIN);
        final CoapExchange coapExchange = newCoapExchange(payload, Type.CON, options);
        final var authenticatedDevice = new DeviceUser("tenant", "device");
        final CoapContext context = CoapContext.fromRequest(coapExchange, authenticatedDevice, authenticatedDevice, "device", span);

        resource.uploadCommandResponseMessage(context)
            .onComplete(ctx.failing(t -> {
                ctx.verify(() -> {
                    // THEN the command response has not been forwarded downstream
                    verify(sender, never()).sendCommandResponse(
                            any(TenantObject.class),
                            any(RegistrationAssertion.class),
                            any(CommandResponse.class),
                            any(SpanContext.class));
                    // and the device gets a 4.03 response
                    assertThat(t).isInstanceOf(ClientErrorException.class);
                    assertThat(((ClientErrorException) t).getErrorCode())
                            .isEqualTo(HttpURLConnection.HTTP_FORBIDDEN);
                    // and the response has not been reported as forwarded
                    verify(metrics, never()).reportCommand(
                            eq(Direction.RESPONSE),
                            eq("tenant"),
                            any(),
                            eq(ProcessingOutcome.FORWARDED),
                            anyInt(),
                            any());
                });
                ctx.completeNow();
            }));

    }

    /**
     * Verifies that the adapter fails the upload of a command response with a 4.00
     * response code if it is rejected by the downstream peer.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testUploadCommandResponseFailsForRejectedOutcome(final VertxTestContext ctx) {

        // GIVEN an adapter with a downstream application attached
        givenAnAdapter(properties);
        final var resource = givenAResource(adapter);
        final Promise<Void> outcome = Promise.promise();
        final CommandResponseSender sender = givenACommandResponseSenderForAnyTenant(outcome);

        // WHEN a device publishes an command response
        final String reqId = Commands.encodeRequestIdParameters("correlation", "replyToId", "device",
                MessagingType.amqp);
        final Buffer payload = Buffer.buffer("some payload");
        final OptionSet options = new OptionSet();
        options.addUriPath(CommandConstants.COMMAND_RESPONSE_ENDPOINT).addUriPath(reqId);
        options.addUriQuery(String.format("%s=%d", Constants.HEADER_COMMAND_RESPONSE_STATUS, 200));
        options.setContentFormat(MediaTypeRegistry.TEXT_PLAIN);
        final CoapExchange coapExchange = newCoapExchange(payload, Type.CON, options);
        final var authenticatedDevice = new DeviceUser("tenant", "device");
        final CoapContext context = CoapContext.fromRequest(coapExchange, authenticatedDevice, authenticatedDevice, "device", span);

        final Future<Void> result = resource.uploadCommandResponseMessage(context);
        outcome.fail(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST, "malformed message"));

        result.onComplete(ctx.failing(t -> {
            ctx.verify(() -> {
                // THEN the command response is being forwarded downstream
                verify(sender).sendCommandResponse(
                        any(TenantObject.class),
                        any(RegistrationAssertion.class),
                        any(CommandResponse.class),
                        any(SpanContext.class));
                // and the device gets a 4.00 response
                assertThat(t).isInstanceOf(ClientErrorException.class);
                assertThat(((ClientErrorException) t).getErrorCode())
                        .isEqualTo(HttpURLConnection.HTTP_BAD_REQUEST);
                // and the response has not been reported as forwarded
                verify(metrics, never()).reportCommand(
                        eq(Direction.RESPONSE),
                        eq("tenant"),
                        any(),
                        eq(ProcessingOutcome.FORWARDED),
                        anyInt(),
                        any());
            });
            ctx.completeNow();
        }));
    }

    /**
     * Verifies that the adapter waits for a command response being successfully sent
     * downstream before responding with a 2.04 status to the device.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testUploadCommandResponseWaitsForAcceptedOutcome(final VertxTestContext ctx) {

        // GIVEN an adapter with a downstream application attached
        givenAnAdapter(properties);
        final var resource = givenAResource(adapter);
        final Promise<Void> outcome = Promise.promise();
        final CommandResponseSender sender = givenACommandResponseSenderForAnyTenant(outcome);

        // WHEN a device publishes an command response
        final String reqId = Commands.encodeRequestIdParameters("correlation", "replyToId", "device",
                MessagingType.amqp);
        final Buffer payload = Buffer.buffer("some payload");
        final OptionSet options = new OptionSet();
        options.addUriPath(CommandConstants.COMMAND_RESPONSE_ENDPOINT).addUriPath(reqId);
        options.addUriQuery(String.format("%s=%d", Constants.HEADER_COMMAND_RESPONSE_STATUS, 200));
        options.setContentFormat(MediaTypeRegistry.TEXT_PLAIN);
        final CoapExchange coapExchange = newCoapExchange(payload, Type.CON, options);
        final var authenticatedDevice = new DeviceUser("tenant", "device");
        final CoapContext context = CoapContext.fromRequest(coapExchange, authenticatedDevice, authenticatedDevice, "device", span);

        final Future<Void> result = resource.uploadCommandResponseMessage(context);

        // THEN the command response is being forwarded downstream
        verify(sender).sendCommandResponse(
                any(TenantObject.class),
                any(RegistrationAssertion.class),
                any(CommandResponse.class),
                any(SpanContext.class));
        // but the device does not get a response
        verify(coapExchange, never()).respond(any(Response.class));
        // and the response has not been reported as forwarded
        verify(metrics, never()).reportCommand(
                eq(Direction.RESPONSE),
                eq("tenant"),
                any(),
                eq(ProcessingOutcome.FORWARDED),
                anyInt(),
                any());

        // until the message has been accepted
        outcome.complete();

        result.onComplete(ctx.succeeding(code -> {
            ctx.verify(() -> {
                verify(coapExchange).respond(argThat((Response res) -> res.getCode() == ResponseCode.CHANGED));
                verify(metrics).reportCommand(
                        eq(Direction.RESPONSE),
                        eq("tenant"),
                        any(),
                        eq(ProcessingOutcome.FORWARDED),
                        eq(payload.length()),
                        any());
            });
            ctx.completeNow();
        }));
    }
}
