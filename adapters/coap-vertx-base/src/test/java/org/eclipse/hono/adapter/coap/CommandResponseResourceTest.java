/**
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.HttpURLConnection;
import java.util.HashMap;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import org.eclipse.californium.core.coap.CoAP.ResponseCode;
import org.eclipse.californium.core.coap.CoAP.Type;
import org.eclipse.californium.core.coap.MediaTypeRegistry;
import org.eclipse.californium.core.coap.OptionSet;
import org.eclipse.californium.core.coap.Request;
import org.eclipse.californium.core.coap.Response;
import org.eclipse.californium.core.network.Endpoint;
import org.eclipse.californium.core.network.Exchange;
import org.eclipse.californium.core.network.Exchange.Origin;
import org.eclipse.californium.core.server.resources.CoapExchange;
import org.eclipse.hono.adapter.client.command.CommandConsumer;
import org.eclipse.hono.adapter.client.command.CommandResponse;
import org.eclipse.hono.adapter.client.command.CommandResponseSender;
import org.eclipse.hono.adapter.client.command.Commands;
import org.eclipse.hono.adapter.test.ProtocolAdapterMockSupport;
import org.eclipse.hono.auth.Device;
import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.service.metric.MetricsTags.Direction;
import org.eclipse.hono.service.metric.MetricsTags.ProcessingOutcome;
import org.eclipse.hono.test.TracingMockSupport;
import org.eclipse.hono.test.VertxMockSupport;
import org.eclipse.hono.util.CommandConstants;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.RegistrationAssertion;
import org.eclipse.hono.util.TelemetryExecutionContext;
import org.eclipse.hono.util.TenantObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.noop.NoopTracerFactory;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
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
public class CommandResponseResourceTest extends ProtocolAdapterMockSupport {

    private CoapProtocolAdapter adapter;
    private CoapAdapterProperties properties;
    private Vertx vertx;
    private CoapAdapterMetrics metrics;
    private CommandConsumer commandConsumer;
    private Span span;

    /**
     * Sets up common fixture.
     */
    @BeforeEach
    public void setup() {

        vertx = mock(Vertx.class);
        metrics = mock(CoapAdapterMetrics.class);
        span = TracingMockSupport.mockSpan();

        createClients();
        prepareClients();

        commandConsumer = mock(CommandConsumer.class);
        when(commandConsumer.close(any())).thenReturn(Future.succeededFuture());
        when(commandConsumerFactory.createCommandConsumer(anyString(), anyString(), VertxMockSupport.anyHandler(), any(), any()))
            .thenReturn(Future.succeededFuture(commandConsumer));
        when(commandConsumerFactory.createCommandConsumer(anyString(), anyString(), anyString(), VertxMockSupport.anyHandler(), any(), any()))
            .thenReturn(Future.succeededFuture(commandConsumer));

        properties = new CoapAdapterProperties();
        properties.setInsecurePortEnabled(true);
        properties.setAuthenticationRequired(false);
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
        final String reqId = Commands.getRequestId("correlation", "replyToId", "device");
        final Buffer payload = Buffer.buffer("some payload");
        final OptionSet options = new OptionSet();
        options.addUriPath(CommandConstants.COMMAND_RESPONSE_ENDPOINT).addUriPath(reqId);
        options.addUriQuery(String.format("%s=%d", Constants.HEADER_COMMAND_RESPONSE_STATUS, 200));
        options.setContentFormat(MediaTypeRegistry.TEXT_PLAIN);
        final CoapExchange coapExchange = newCoapExchange(payload, Type.CON, options);
        final Device authenticatedDevice = new Device("tenant", "device");
        final CoapContext context = CoapContext.fromRequest(coapExchange, authenticatedDevice, authenticatedDevice, "device", span);

        resource.uploadCommandResponseMessage(context)
            .onComplete(ctx.failing(t -> {
                ctx.verify(() -> {
                    // THEN the command response has not been forwarded downstream
                    verify(sender, never()).sendCommandResponse(any(CommandResponse.class), any(SpanContext.class));
                    // and the device gets a 4.03 response
                    assertThat(t).isInstanceOfSatisfying(ClientErrorException.class,
                            e -> assertThat(e.getErrorCode()).isEqualTo(HttpURLConnection.HTTP_FORBIDDEN));
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
        final String reqId = Commands.getRequestId("correlation", "replyToId", "device");
        final Buffer payload = Buffer.buffer("some payload");
        final OptionSet options = new OptionSet();
        options.addUriPath(CommandConstants.COMMAND_RESPONSE_ENDPOINT).addUriPath(reqId);
        options.addUriQuery(String.format("%s=%d", Constants.HEADER_COMMAND_RESPONSE_STATUS, 200));
        options.setContentFormat(MediaTypeRegistry.TEXT_PLAIN);
        final CoapExchange coapExchange = newCoapExchange(payload, Type.CON, options);
        final Device authenticatedDevice = new Device("tenant", "device");
        final CoapContext context = CoapContext.fromRequest(coapExchange, authenticatedDevice, authenticatedDevice, "device", span);

        final Future<?> result = resource.uploadCommandResponseMessage(context);
        outcome.fail(new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST, "malformed message"));

        result.onComplete(ctx.failing(t -> {
            ctx.verify(() -> {
                // THEN the command response is being forwarded downstream
                verify(sender).sendCommandResponse(any(CommandResponse.class), any(SpanContext.class));
                // and the device gets a 4.00 response
                assertThat(t).isInstanceOfSatisfying(ClientErrorException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(HttpURLConnection.HTTP_BAD_REQUEST));
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
        final String reqId = Commands.getRequestId("correlation", "replyToId", "device");
        final Buffer payload = Buffer.buffer("some payload");
        final OptionSet options = new OptionSet();
        options.addUriPath(CommandConstants.COMMAND_RESPONSE_ENDPOINT).addUriPath(reqId);
        options.addUriQuery(String.format("%s=%d", Constants.HEADER_COMMAND_RESPONSE_STATUS, 200));
        options.setContentFormat(MediaTypeRegistry.TEXT_PLAIN);
        final CoapExchange coapExchange = newCoapExchange(payload, Type.CON, options);
        final Device authenticatedDevice = new Device("tenant", "device");
        final CoapContext context = CoapContext.fromRequest(coapExchange, authenticatedDevice, authenticatedDevice, "device", span);

        final Future<?> result = resource.uploadCommandResponseMessage(context);

        // THEN the command response is being forwarded downstream
        verify(sender).sendCommandResponse(any(CommandResponse.class), any(SpanContext.class));
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

    private static CoapExchange newCoapExchange(final Buffer payload, final Type requestType, final OptionSet options) {

        final Request request = mock(Request.class);
        when(request.getType()).thenReturn(requestType);
        when(request.isConfirmable()).thenReturn(requestType == Type.CON);
        when(request.getOptions()).thenReturn(options);
        final Exchange exchange = new Exchange(request, Origin.REMOTE, mock(Executor.class));
        final CoapExchange coapExchange = mock(CoapExchange.class);
        when(coapExchange.advanced()).thenReturn(exchange);
        Optional.ofNullable(payload).ifPresent(b -> when(coapExchange.getRequestPayload()).thenReturn(b.getBytes()));
        when(coapExchange.getRequestOptions()).thenReturn(options);
        when(coapExchange.getQueryParameter(anyString())).thenAnswer(invocation -> {
            final String key = invocation.getArgument(0);
            return options.getUriQuery().stream()
                    .map(param -> param.split("=", 2))
                    .filter(keyValue -> key.equals(keyValue[0]))
                    .findFirst()
                    .map(keyValue -> keyValue.length < 2 ? Boolean.TRUE.toString() : keyValue[1])
                    .orElse(null);
        });
        return coapExchange;
    }

    private CommandResponseResource givenAResource(final CoapProtocolAdapter adapter) {

        return new CommandResponseResource(adapter, NoopTracerFactory.create(), vertx);
    }

    private CoapProtocolAdapter givenAnAdapter(final CoapAdapterProperties configuration) {

        adapter = mock(CoapProtocolAdapter.class);
        when(adapter.checkMessageLimit(any(TenantObject.class), anyLong(), any())).thenReturn(Future.succeededFuture());
        when(adapter.getCommandConsumerFactory()).thenReturn(commandConsumerFactory);
        when(adapter.getCommandResponseSender(any(TenantObject.class))).thenReturn(commandResponseSender);
        when(adapter.getConfig()).thenReturn(configuration);
        when(adapter.getDownstreamMessageProperties(any(TelemetryExecutionContext.class))).thenReturn(new HashMap<>());
        when(adapter.getEventSender(any(TenantObject.class))).thenReturn(eventSender);
        when(adapter.getInsecureEndpoint()).thenReturn(mock(Endpoint.class));
        when(adapter.getMetrics()).thenReturn(metrics);
        when(adapter.getRegistrationAssertion(anyString(), anyString(), any(), (SpanContext) any()))
            .thenAnswer(invocation -> {
                final String deviceId = invocation.getArgument(1);
                final RegistrationAssertion regAssertion = new RegistrationAssertion(deviceId);
                return Future.succeededFuture(regAssertion);
            });
        when(adapter.getSecureEndpoint()).thenReturn(mock(Endpoint.class));
        when(adapter.getTelemetrySender(any(TenantObject.class))).thenReturn(telemetrySender);
        when(adapter.getTenantClient()).thenReturn(tenantClient);
        when(adapter.getTimeUntilDisconnect(any(TenantObject.class), any())).thenCallRealMethod();
        when(adapter.getTypeName()).thenReturn(Constants.PROTOCOL_ADAPTER_TYPE_COAP);
        when(adapter.isAdapterEnabled(any(TenantObject.class)))
            .thenAnswer(invocation -> Future.succeededFuture(invocation.getArgument(0)));
        doAnswer(invocation -> {
            final Handler<Void> codeToRun = invocation.getArgument(0);
            codeToRun.handle(null);
            return null;
        }).when(adapter).runOnContext(VertxMockSupport.anyHandler());
        when(adapter.sendTtdEvent(anyString(), anyString(), any(), anyInt(), any())).thenReturn(Future.succeededFuture());
        return adapter;
    }

}
