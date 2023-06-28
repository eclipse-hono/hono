/**
 * Copyright (c) 2018, 2023 Contributors to the Eclipse Foundation
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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static com.google.common.truth.Truth.assertThat;

import java.net.HttpURLConnection;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import org.eclipse.californium.core.coap.CoAP.ResponseCode;
import org.eclipse.californium.core.coap.CoAP.Type;
import org.eclipse.californium.core.coap.MediaTypeRegistry;
import org.eclipse.californium.core.coap.OptionSet;
import org.eclipse.californium.core.coap.Response;
import org.eclipse.californium.core.server.resources.CoapExchange;
import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.ServerErrorException;
import org.eclipse.hono.client.command.CommandContext;
import org.eclipse.hono.service.auth.DeviceUser;
import org.eclipse.hono.service.metric.MetricsTags;
import org.eclipse.hono.service.metric.MetricsTags.Direction;
import org.eclipse.hono.service.metric.MetricsTags.ProcessingOutcome;
import org.eclipse.hono.service.metric.MetricsTags.TtdStatus;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.EventConstants;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.MessagingType;
import org.eclipse.hono.util.QoS;
import org.eclipse.hono.util.TenantObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;

import io.micrometer.core.instrument.Timer.Sample;
import io.opentracing.noop.NoopTracerFactory;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

/**
 * Tests verifying behavior of {@link TelemetryResource}.
 */
@ExtendWith(VertxExtension.class)
@Timeout(value = 5, timeUnit = TimeUnit.SECONDS)
public class TelemetryResourceTest extends ResourceTestBase {

    private TelemetryResource givenAResource(final CoapProtocolAdapter adapter) {

        return new TelemetryResource(adapter, NoopTracerFactory.create(), vertx);
    }

    /**
     * Verifies that the adapter fails the upload of an event with a 4.03 result if the device belongs to a tenant for
     * which the adapter is disabled.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testUploadTelemetryFailsForDisabledTenant(final VertxTestContext ctx) {

        // GIVEN an adapter
        givenAnAdapter(properties);
        givenATelemetrySenderForAnyTenant();
        final var resource = givenAResource(adapter);
        // which is disabled for tenant "my-tenant"
        when(adapter.isAdapterEnabled(any(TenantObject.class)))
            .thenReturn(Future.failedFuture(new ClientErrorException(HttpURLConnection.HTTP_FORBIDDEN)));

        // WHEN a device that belongs to "my-tenant" publishes a telemetry message
        final Buffer payload = Buffer.buffer("some payload");
        final CoapExchange coapExchange = newCoapExchange(payload, Type.NON, MediaTypeRegistry.TEXT_PLAIN);
        final DeviceUser authenticatedDevice = new DeviceUser("my-tenant", "the-device");
        final CoapContext context = CoapContext.fromRequest(coapExchange, authenticatedDevice, authenticatedDevice, "the-device", span);

        resource.handlePostRequest(context)
            .onComplete(ctx.failing(t -> {
                ctx.verify(() -> {
                    // THEN the device gets a response with code 4.03
                    assertThat(t).isInstanceOf(ClientErrorException.class);
                    assertThat(((ClientErrorException) t).getErrorCode())
                            .isEqualTo(HttpURLConnection.HTTP_FORBIDDEN);

                    // and the message has not been forwarded downstream
                    assertNoTelemetryMessageHasBeenSentDownstream();
                    verify(metrics).reportTelemetry(
                            eq(MetricsTags.EndpointType.TELEMETRY),
                            eq("my-tenant"),
                            any(),
                            eq(MetricsTags.ProcessingOutcome.UNPROCESSABLE),
                            eq(MetricsTags.QoS.AT_MOST_ONCE),
                            eq(payload.length()),
                            eq(TtdStatus.NONE),
                            any());
                });
                ctx.completeNow();
            }));

    }

    /**
     * Verifies that the adapter successfully forwards a non-empty message that has no content-format set.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testUploadTelemetrySucceedsForMissingContentFormat(final VertxTestContext ctx) {

        // GIVEN an adapter
        givenAnAdapter(properties);
        givenATelemetrySenderForAnyTenant();
        final var resource = givenAResource(adapter);

        // WHEN a device publishes a non-empty message that lacks a content-format option
        final Buffer payload = Buffer.buffer("some payload");
        final CoapExchange coapExchange = newCoapExchange(payload, Type.NON, (Integer) null);
        final DeviceUser authenticatedDevice = new DeviceUser("my-tenant", "the-device");
        final CoapContext context = CoapContext.fromRequest(coapExchange, authenticatedDevice, authenticatedDevice, "the-device", span);

        resource.handlePostRequest(context)
            .onComplete(ctx.succeeding(ok -> {
                ctx.verify(() -> {

                    // THEN the message has been forwarded downstream
                    assertTelemetryMessageHasBeenSentDownstream(
                            QoS.AT_MOST_ONCE,
                            "my-tenant",
                            "the-device",
                            null);
                    verify(metrics).reportTelemetry(
                            eq(MetricsTags.EndpointType.TELEMETRY),
                            eq("my-tenant"),
                            any(),
                            eq(MetricsTags.ProcessingOutcome.FORWARDED),
                            eq(MetricsTags.QoS.AT_MOST_ONCE),
                            eq(payload.length()),
                            eq(TtdStatus.NONE),
                            any());
                });
                ctx.completeNow();
            }));

    }

    /**
     * Verifies that the adapter successfully forwards an empty message that is not marked as an empty notification.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testUploadTelemetrySucceedsForEmptyBody(final VertxTestContext ctx) {

        // GIVEN an adapter
        givenAnAdapter(properties);
        givenATelemetrySenderForAnyTenant();
        final var resource = givenAResource(adapter);

        // WHEN a device publishes an empty message that is not marked as an empty notification
        final CoapExchange coapExchange = newCoapExchange(null, Type.NON, MediaTypeRegistry.APPLICATION_OCTET_STREAM);
        final DeviceUser authenticatedDevice = new DeviceUser("my-tenant", "the-device");
        final CoapContext context = CoapContext.fromRequest(coapExchange, authenticatedDevice, authenticatedDevice, "the-device", span);

        resource.handlePostRequest(context)
            .onComplete(ctx.succeeding(ok -> {
                ctx.verify(() -> {
                    // THEN the message has been forwarded downstream
                    assertTelemetryMessageHasBeenSentDownstream(
                            QoS.AT_MOST_ONCE,
                            "my-tenant",
                            "the-device",
                            MessageHelper.CONTENT_TYPE_OCTET_STREAM);
                    verify(metrics).reportTelemetry(
                            eq(MetricsTags.EndpointType.TELEMETRY),
                            eq("my-tenant"),
                            any(),
                            eq(MetricsTags.ProcessingOutcome.FORWARDED),
                            eq(MetricsTags.QoS.AT_MOST_ONCE),
                            eq(0),
                            eq(TtdStatus.NONE),
                            any());
                });
                ctx.completeNow();
            }));

    }

    /**
     * Verifies that the adapter forwards an empty notification downstream.
     */
    @Test
    public void testUploadEmptyNotificationSucceeds() {

        // GIVEN an adapter
        givenAnAdapter(properties);
        givenATelemetrySenderForAnyTenant();
        final var resource = givenAResource(adapter);

        // WHEN a device publishes an empty message that is marked as an empty notification
        final OptionSet options = new OptionSet();
        options.addUriQuery(CoapContext.PARAM_EMPTY_CONTENT);
        final CoapExchange coapExchange = newCoapExchange(null, Type.NON, options);
        final DeviceUser authenticatedDevice = new DeviceUser("my-tenant", "the-device");
        final CoapContext context = CoapContext.fromRequest(coapExchange, authenticatedDevice, authenticatedDevice, "the-device", span);

        resource.handlePostRequest(context);

        // THEN the device gets a response indicating success
        verify(coapExchange).respond(argThat((Response res) -> ResponseCode.CHANGED.equals(res.getCode())));
        // and the message has been forwarded downstream
        assertTelemetryMessageHasBeenSentDownstream(
                QoS.AT_MOST_ONCE,
                "my-tenant",
                "the-device",
                EventConstants.CONTENT_TYPE_EMPTY_NOTIFICATION);
        verify(metrics).reportTelemetry(
                eq(MetricsTags.EndpointType.TELEMETRY),
                eq("my-tenant"),
                any(),
                eq(MetricsTags.ProcessingOutcome.FORWARDED),
                eq(MetricsTags.QoS.AT_MOST_ONCE),
                eq(0),
                eq(TtdStatus.NONE),
                any());
    }

    /**
     * Verifies that the adapter immediately responds with a 2.04 status if a
     * device publishes telemetry data using a NON message.
     */
    @Test
    public void testUploadTelemetryWithQoS0() {

        // GIVEN an adapter with a downstream telemetry consumer attached
        givenAnAdapter(properties);
        givenATelemetrySenderForAnyTenant();
        final var resource = givenAResource(adapter);

        // WHEN a device publishes an telemetry message
        final Buffer payload = Buffer.buffer("some payload");
        final CoapExchange coapExchange = newCoapExchange(payload, Type.NON, MediaTypeRegistry.TEXT_PLAIN);
        final DeviceUser authenticatedDevice = new DeviceUser("tenant", "device");
        final CoapContext context = CoapContext.fromRequest(coapExchange, authenticatedDevice, authenticatedDevice, "device", span);

        resource.handlePostRequest(context);

        // THEN the device gets a response indicating success
        verify(coapExchange).respond(argThat((Response res) -> ResponseCode.CHANGED.equals(res.getCode())));
        // and the message has been forwarded downstream
        assertTelemetryMessageHasBeenSentDownstream(
                QoS.AT_MOST_ONCE,
                "tenant",
                "device",
                "text/plain");
        verify(metrics).reportTelemetry(
                eq(MetricsTags.EndpointType.TELEMETRY),
                eq("tenant"),
                any(),
                eq(MetricsTags.ProcessingOutcome.FORWARDED),
                eq(MetricsTags.QoS.AT_MOST_ONCE),
                eq(payload.length()),
                eq(TtdStatus.NONE),
                any());
    }

    /**
     * Verifies that the adapter waits for a the AMQP Messaging Network to accept a
     * forwarded telemetry message that has been published using a CON message,
     * before responding with a 2.04 status to the device.
     */
    @Test
    public void testUploadTelemetryWithQoS1() {

        // GIVEN an adapter with a downstream telemetry consumer attached
        givenAnAdapter(properties);
        final Promise<Void> outcome = Promise.promise();
        givenATelemetrySenderForAnyTenant(outcome);
        final var resource = givenAResource(adapter);

        // WHEN a device publishes an telemetry message with QoS 1
        final Buffer payload = Buffer.buffer("some payload");
        final CoapExchange coapExchange = newCoapExchange(payload, Type.CON, MediaTypeRegistry.TEXT_PLAIN);
        final DeviceUser authenticatedDevice = new DeviceUser("tenant", "device");
        final CoapContext context = CoapContext.fromRequest(coapExchange, authenticatedDevice, authenticatedDevice, "device", span);

        resource.handlePostRequest(context);

        // THEN the message is being forwarded downstream
        assertTelemetryMessageHasBeenSentDownstream(
                QoS.AT_LEAST_ONCE,
                "tenant",
                "device",
                "text/plain");
        // and the device does not get a response
        verify(coapExchange, never()).respond(any(Response.class));
        // until the telemetry message has been accepted
        outcome.complete();

        verify(coapExchange).respond(argThat((Response res) -> ResponseCode.CHANGED.equals(res.getCode())));
        verify(metrics).reportTelemetry(
                eq(MetricsTags.EndpointType.TELEMETRY),
                eq("tenant"),
                any(),
                eq(MetricsTags.ProcessingOutcome.FORWARDED),
                eq(MetricsTags.QoS.AT_LEAST_ONCE),
                eq(payload.length()),
                eq(TtdStatus.NONE),
                any());
    }

    /**
     * Verifies that a telemetry message is rejected due to the limit exceeded.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testMessageLimitExceededForATelemetryMessage(final VertxTestContext ctx) {

        // GIVEN an adapter with a downstream telemetry consumer attached
        givenAnAdapter(properties);
        givenATelemetrySenderForAnyTenant();
        final var resource = givenAResource(adapter);

        // WHEN a device that belongs to a tenant for which the message limit is exceeded
        // publishes a telemetry message
        when(adapter.checkMessageLimit(any(TenantObject.class), anyLong(), any())).thenReturn(Future.failedFuture(new ClientErrorException(429)));
        final Buffer payload = Buffer.buffer("some payload");
        final CoapExchange coapExchange = newCoapExchange(payload, Type.NON, MediaTypeRegistry.TEXT_PLAIN);
        final DeviceUser authenticatedDevice = new DeviceUser("tenant", "device");
        final CoapContext context = CoapContext.fromRequest(coapExchange, authenticatedDevice, authenticatedDevice, "device", span);

        resource.handlePostRequest(context)
            .onComplete(ctx.failing(t -> {
                ctx.verify(() -> {
                    // THEN the message is not being forwarded downstream
                    assertNoTelemetryMessageHasBeenSentDownstream();
                    // and the device gets a 4.29
                    assertThat(t).isInstanceOf(ClientErrorException.class);
                    assertThat(((ClientErrorException) t).getErrorCode()).isEqualTo(429);
                    verify(metrics).reportTelemetry(
                            eq(MetricsTags.EndpointType.TELEMETRY),
                            eq("tenant"),
                            any(),
                            eq(MetricsTags.ProcessingOutcome.UNPROCESSABLE),
                            eq(MetricsTags.QoS.AT_MOST_ONCE),
                            eq(payload.length()),
                            eq(TtdStatus.NONE),
                            any());
                });
                ctx.completeNow();
            }));
    }

    /**
     * Verifies that the adapter includes a command in the response to a telemetry request.
     *
     * @param isAuthenticated {@code true} if the device has been authenticated.
     * @param isOneWayCommand {@code true} if the response should contain a one-way command,
     *                        {@code false} if the response should contain a request-response command.
     * @param requestEndpoint The endpoint name contained in the CoAP request.
     * @param expectedLocationPath The expected value of the location-path option of the response.
     *                             If isOneWayCommand is {@code false}, then the command's request ID is being
     *                             appended to this parameter's value when performing the check.
     * @param ctx The vert.x test context.
     */
    @ParameterizedTest
    @CsvSource(value = {
            "true,true,t,c",
            "true,false,t,cr/",
            "false,true,t,c",
            "false,false,t,cr/tenant/device/",
            "true,true,telemetry,command",
            "true,false,telemetry,command_response/",
            "false,true,telemetry,command",
            "false,false,telemetry,command_response/tenant/device/",
            })
    public void testUploadTelemetryWithOneWayCommand(
            final boolean isAuthenticated,
            final boolean isOneWayCommand,
            final String requestEndpoint,
            final String expectedLocationPath,
            final VertxTestContext ctx) {

        // GIVEN an adapter with a downstream telemetry consumer attached
        givenAnAdapter(properties);
        givenATelemetrySenderForAnyTenant();
        final var resource = givenAResource(adapter);
        final var tenantId = "tenant";
        final var deviceId = "device";
        final var commandName = "doThis";

        // and a commandConsumerFactory that upon creating a consumer will invoke it with a command
        final Sample commandTimer = mock(Sample.class);
        final CommandContext commandContext;
        if (isOneWayCommand) {
            commandContext = givenAOneWayCommandContext(tenantId, deviceId, commandName, null, null);
        } else {
            commandContext = givenARequestResponseCommandContext(
                    tenantId,
                    deviceId,
                    commandName,
                    "reply-to-id",
                    null,
                    null,
                    MessagingType.kafka);
        }

        when(commandContext.get(anyString())).thenReturn(commandTimer);
        when(commandConsumerFactory.createCommandConsumer(eq(tenantId), eq(deviceId), eq(false), any(), any(), any()))
            .thenAnswer(invocation -> {
                final Function<CommandContext, Future<Void>> consumer = invocation.getArgument(3);
                consumer.apply(commandContext);
                return Future.succeededFuture(commandConsumer);
            });

        // WHEN a device publishes a telemetry message with a hono-ttd parameter
        final Buffer payload = Buffer.buffer("some payload");
        final OptionSet options = new OptionSet();
        options.setUriPath(requestEndpoint);
        options.addUriQuery(String.format("%s=%d", Constants.HEADER_TIME_TILL_DISCONNECT, 20));
        options.setContentFormat(MediaTypeRegistry.TEXT_PLAIN);
        final CoapExchange coapExchange = newCoapExchange(payload, Type.CON, options);
        final var originDevice = new DeviceUser(tenantId, deviceId);
        final DeviceUser authenticatedDevice = isAuthenticated ? originDevice : null;
        final CoapContext context = CoapContext.fromRequest(coapExchange, originDevice, authenticatedDevice, deviceId, span);

        resource.handlePostRequest(context)
            .onComplete(ctx.succeeding(ok -> {
                ctx.verify(() -> {
                    // THEN the message is being forwarded downstream
                    assertTelemetryMessageHasBeenSentDownstream(
                            QoS.AT_LEAST_ONCE,
                            tenantId,
                            deviceId,
                            "text/plain");
                    // correctly reported
                    verify(metrics).reportTelemetry(
                            eq(MetricsTags.EndpointType.TELEMETRY),
                            eq(tenantId),
                            any(),
                            eq(ProcessingOutcome.FORWARDED),
                            eq(MetricsTags.QoS.AT_LEAST_ONCE),
                            eq(payload.length()),
                            eq(TtdStatus.COMMAND),
                            any());
                    // and the device gets a response which contains a command
                    final ArgumentCaptor<Response> res = ArgumentCaptor.forClass(Response.class);
                    verify(coapExchange).respond(res.capture());
                    assertAll("response contains command",
                            () -> assertThat(res.getValue().getCode()).isEqualTo(ResponseCode.CHANGED),
                            () -> assertThat(res.getValue().getOptions().getLocationQuery())
                                    .contains("hono-command=%s".formatted(commandName)),
                            () -> assertThat(res.getValue().getPayloadSize()).isEqualTo(0));

                    final String expectedPath;
                    if (isOneWayCommand) {
                        expectedPath = expectedLocationPath;
                    } else {
                        expectedPath = expectedLocationPath + commandContext.getCommand().getRequestId();
                    }
                    assertThat(res.getValue().getOptions().getLocationPathString())
                        .isEqualTo(expectedPath);

                    verify(metrics).reportCommand(
                            eq(isOneWayCommand ? Direction.ONE_WAY : Direction.REQUEST),
                            eq(tenantId),
                            any(),
                            eq(ProcessingOutcome.FORWARDED),
                            eq(0),
                            eq(commandTimer));
                });
                ctx.completeNow();
            }));
    }

    /**
     * Verifies that the adapter releases an incoming command if the forwarding of the preceding telemetry
     * message did not succeed.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testUploadTelemetryReleasesCommandForFailedDownstreamSender(final VertxTestContext ctx) {

        // GIVEN an adapter with a downstream telemetry consumer attached
        givenAnAdapter(properties);
        final Promise<Void> sendTelemetryOutcome = Promise.promise();
        givenATelemetrySenderForAnyTenant(sendTelemetryOutcome);
        final var resource = givenAResource(adapter);

        // and a commandConsumerFactory that upon creating a consumer will invoke it with a command
        final CommandContext commandContext = givenAOneWayCommandContext("tenant", "device", "doThis", null, null);
        when(commandConsumerFactory.createCommandConsumer(eq("tenant"), eq("device"), eq(false), any(), any(), any()))
                .thenAnswer(invocation -> {
                    final Function<CommandContext, Future<Void>> consumer = invocation.getArgument(3);
                    consumer.apply(commandContext);
                    return Future.succeededFuture(commandConsumer);
                });

        // WHEN a device publishes a telemetry message with a hono-ttd parameter
        final Buffer payload = Buffer.buffer("some payload");
        final OptionSet options = new OptionSet();
        options.addUriQuery(String.format("%s=%d", Constants.HEADER_TIME_TILL_DISCONNECT, 20));
        options.setContentFormat(MediaTypeRegistry.TEXT_PLAIN);
        final CoapExchange coapExchange = newCoapExchange(payload, Type.CON, options);
        final DeviceUser authenticatedDevice = new DeviceUser("tenant", "device");
        final CoapContext context = CoapContext.fromRequest(coapExchange, authenticatedDevice, authenticatedDevice, "device", span);

        final Future<Void> result = resource.handlePostRequest(context);

        // THEN the message is being forwarded downstream
        assertTelemetryMessageHasBeenSentDownstream(
                QoS.AT_LEAST_ONCE,
                "tenant",
                "device",
                "text/plain");
        // with no response being sent to the device yet
        verify(coapExchange, never()).respond(any(Response.class));

        // WHEN the telemetry message delivery gets failed with an exception representing a "released" delivery outcome
        sendTelemetryOutcome.fail(new ServerErrorException(HttpURLConnection.HTTP_UNAVAILABLE));

        result.onComplete(ctx.failing(t -> {
            ctx.verify(() -> {
                // THEN the device gets a response with code SERVICE_UNAVAILABLE
                assertThat(t).isInstanceOf(ServerErrorException.class);
                assertThat(((ServerErrorException) t).getErrorCode())
                        .isEqualTo(HttpURLConnection.HTTP_UNAVAILABLE);
                verify(metrics).reportTelemetry(
                        eq(MetricsTags.EndpointType.TELEMETRY),
                        eq("tenant"),
                        any(),
                        eq(ProcessingOutcome.UNDELIVERABLE),
                        eq(MetricsTags.QoS.AT_LEAST_ONCE),
                        eq(payload.length()),
                        eq(TtdStatus.COMMAND),
                        any());
                // and the command delivery is released
                verify(commandContext).release(any(Throwable.class));
            });
            ctx.completeNow();
        }));
    }
}
