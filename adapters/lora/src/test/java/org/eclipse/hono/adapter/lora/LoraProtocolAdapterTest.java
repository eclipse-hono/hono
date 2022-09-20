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

package org.eclipse.hono.adapter.lora;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import static com.google.common.truth.Truth.assertThat;

import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import org.eclipse.hono.adapter.HttpContext;
import org.eclipse.hono.adapter.http.HttpAdapterMetrics;
import org.eclipse.hono.adapter.http.HttpProtocolAdapterProperties;
import org.eclipse.hono.adapter.lora.providers.LoraProvider;
import org.eclipse.hono.adapter.lora.providers.LoraProviderMalformedPayloadException;
import org.eclipse.hono.adapter.test.ProtocolAdapterTestSupport;
import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.client.command.Command;
import org.eclipse.hono.client.command.CommandContext;
import org.eclipse.hono.client.command.ProtocolAdapterCommandConsumer;
import org.eclipse.hono.service.auth.DeviceUser;
import org.eclipse.hono.service.http.HttpServerSpanHelper;
import org.eclipse.hono.test.VertxMockSupport;
import org.eclipse.hono.util.CommandEndpoint;
import org.eclipse.hono.util.QoS;
import org.eclipse.hono.util.RegistrationAssertion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import io.micrometer.core.instrument.Timer.Sample;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.Tracer.SpanBuilder;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RequestBody;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;

/**
 * Verifies behavior of {@link LoraProtocolAdapter}.
 */
public class LoraProtocolAdapterTest extends ProtocolAdapterTestSupport<HttpProtocolAdapterProperties, LoraProtocolAdapter> {

    private static final int TEST_FUNCTION_PORT = 2;
    private static final String TEST_TENANT_ID = "myTenant";
    private static final String TEST_GATEWAY_ID = "myLoraGateway";
    private static final String TEST_DEVICE_ID = "0102030405060708";
    private static final byte[] TEST_PAYLOAD = "bumxlux".getBytes(StandardCharsets.UTF_8);
    private static final String TEST_PROVIDER = "bumlux";

    private Span processMessageSpan;
    private Vertx vertx;
    private WebClient webClient;

    /**
     * Sets up the fixture.
     */
    @BeforeEach
    public void setUp() {

        vertx = mock(Vertx.class);
        final Context context = VertxMockSupport.mockContext(vertx);
        webClient = mock(WebClient.class);

        this.properties = givenDefaultConfigurationProperties();
        createClients();
        prepareClients();

        processMessageSpan = mock(Span.class);
        when(processMessageSpan.context()).thenReturn(mock(SpanContext.class));
        final Span otherSpan = mock(Span.class);
        when(otherSpan.context()).thenReturn(mock(SpanContext.class));

        final SpanBuilder processMessageSpanBuilder = mock(SpanBuilder.class, withSettings().defaultAnswer(RETURNS_SELF));
        when(processMessageSpanBuilder.start()).thenReturn(processMessageSpan);
        final SpanBuilder otherSpanBuilder = mock(SpanBuilder.class, withSettings().defaultAnswer(RETURNS_SELF));
        when(otherSpanBuilder.start()).thenReturn(otherSpan);

        final Tracer tracer = mock(Tracer.class);
        when(tracer.buildSpan(eq(LoraProtocolAdapter.SPAN_NAME_PROCESS_MESSAGE))).thenReturn(processMessageSpanBuilder);
        when(tracer.buildSpan(argThat(opName -> !opName.equals(LoraProtocolAdapter.SPAN_NAME_PROCESS_MESSAGE)))).thenReturn(otherSpanBuilder);

        final HttpAdapterMetrics metrics = mock(HttpAdapterMetrics.class);
        when(metrics.startTimer()).thenReturn(mock(Sample.class));

        final LoraCommandSubscriptions commandSubscriptions = new LoraCommandSubscriptions(vertx, tracer);

        adapter = new LoraProtocolAdapter(webClient);
        adapter.setConfig(properties);
        adapter.setTracer(tracer);
        adapter.init(vertx, context);
        adapter.setMetrics(metrics);
        adapter.setCommandSubscriptions(commandSubscriptions);
        setServiceClients(adapter);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected HttpProtocolAdapterProperties givenDefaultConfigurationProperties() {
        return new HttpProtocolAdapterProperties();
    }

    /**
     * Verifies that an uplink message is routed to a provider correctly.
     */
    @Test
    public void handleProviderRouteSuccessfullyForUplinkMessage() {

        givenATelemetrySenderForAnyTenant();

        final LoraProvider providerMock = getLoraProviderMock();
        final HttpServerRequest request = mock(HttpServerRequest.class);
        final HttpContext httpContext = newHttpContext();
        when(httpContext.request()).thenReturn(request);

        setGatewayDeviceCommandEndpoint(new CommandEndpoint());

        final ProtocolAdapterCommandConsumer commandConsumer = mock(ProtocolAdapterCommandConsumer.class);
        when(commandConsumer.close(eq(false), any())).thenReturn(Future.succeededFuture());
        when(commandConsumerFactory.createCommandConsumer(any(), any(), eq(false), any(), any(), any()))
                .thenReturn(Future.succeededFuture(commandConsumer));

        adapter.handleProviderRoute(httpContext, providerMock);

        verify(httpContext.getRoutingContext()).put(LoraConstants.APP_PROPERTY_ORIG_LORA_PROVIDER, TEST_PROVIDER);

        @SuppressWarnings("unchecked")
        final ArgumentCaptor<Map<String, Object>> props = ArgumentCaptor.forClass(Map.class);
        verify(telemetrySender).sendTelemetry(
                argThat(tenant -> TEST_TENANT_ID.equals(tenant.getTenantId())),
                argThat(assertion -> TEST_DEVICE_ID.equals(assertion.getDeviceId())),
                eq(QoS.AT_MOST_ONCE),
                eq(LoraConstants.CONTENT_TYPE_LORA_BASE + TEST_PROVIDER),
                eq(Buffer.buffer(TEST_PAYLOAD)),
                props.capture(),
                any());
        assertThat(props.getValue()).containsEntry(LoraConstants.APP_PROPERTY_FUNCTION_PORT, TEST_FUNCTION_PORT);
        final String metaData = (String) props.getValue().get(LoraConstants.APP_PROPERTY_META_DATA);
        assertThat(metaData).isNotNull();
        final JsonObject metaDataJson = new JsonObject(metaData);
        assertThat(metaDataJson.getInteger(LoraConstants.APP_PROPERTY_FUNCTION_PORT)).isEqualTo(TEST_FUNCTION_PORT);
        verify(httpContext.getRoutingContext().response()).setStatusCode(HttpResponseStatus.ACCEPTED.code());
        verify(processMessageSpan).finish();
    }

    /**
     * Verifies that an uplink message triggers a command subscription.
     */
    @Test
    public void handleCommandSubscriptionSuccessfullyForUplinkMessage() {

        givenATelemetrySenderForAnyTenant();

        final LoraProvider providerMock = getLoraProviderMock();
        final HttpServerRequest request = mock(HttpServerRequest.class);
        final HttpContext httpContext = newHttpContext();
        when(httpContext.request()).thenReturn(request);

        setGatewayDeviceCommandEndpoint(new CommandEndpoint());

        final ProtocolAdapterCommandConsumer commandConsumer = mock(ProtocolAdapterCommandConsumer.class);
        when(commandConsumer.close(eq(false), any())).thenReturn(Future.succeededFuture());
        when(commandConsumerFactory.createCommandConsumer(any(), any(), eq(false), any(), any(), any()))
                .thenReturn(Future.succeededFuture(commandConsumer));

        adapter.handleProviderRoute(httpContext, providerMock);

        verify(commandConsumerFactory).createCommandConsumer(
                eq("myTenant"),
                eq("myLoraGateway"),
                eq(false),
                any(),
                isNull(),
                any());
    }

    /**
     * Verifies that an uplink message triggers a command subscription.
     *
     */
    @SuppressWarnings("unchecked")
    @Test
    public void handleCommandForLNS() {

        givenATelemetrySenderForAnyTenant();

        final LoraProvider providerMock = getLoraProviderMock();
        final HttpServerRequest request = mock(HttpServerRequest.class);

        final HttpContext httpContext = newHttpContext();
        when(httpContext.request()).thenReturn(request);

        final CommandEndpoint commandEndpoint = new CommandEndpoint();
        commandEndpoint.setHeaders(Map.of("my-header", "my-header-value"));
        commandEndpoint.setUri("https://my-server.com/commands/{{deviceId}}/send");

        setGatewayDeviceCommandEndpoint(commandEndpoint);

        final ProtocolAdapterCommandConsumer commandConsumer = mock(ProtocolAdapterCommandConsumer.class);
        when(commandConsumer.close(eq(false), any())).thenReturn(Future.succeededFuture());
        when(commandConsumerFactory.createCommandConsumer(any(), any(), eq(false), any(), any(), any()))
                .thenReturn(Future.succeededFuture(commandConsumer));

        adapter.handleProviderRoute(httpContext, providerMock);

        final ArgumentCaptor<Function<CommandContext, Future<Void>>> handlerArgumentCaptor = ArgumentCaptor.forClass(Function.class);

        verify(commandConsumerFactory).createCommandConsumer(
                eq(TEST_TENANT_ID),
                eq(TEST_GATEWAY_ID),
                eq(false),
                handlerArgumentCaptor.capture(),
                isNull(),
                any());

        final Function<CommandContext, Future<Void>> commandHandler = handlerArgumentCaptor.getValue();
        final Command command = mock(Command.class);
        when(command.getTenant()).thenReturn(TEST_TENANT_ID);
        when(command.getDeviceId()).thenReturn(TEST_DEVICE_ID);
        when(command.getGatewayId()).thenReturn(TEST_GATEWAY_ID);
        when(command.getPayload()).thenReturn(Buffer.buffer("bumlux"));
        when(command.isValid()).thenReturn(true);

        final CommandContext commandContext = mock(CommandContext.class);
        when(commandContext.getCommand()).thenReturn(command);
        when(commandContext.getTracingSpan()).thenReturn(processMessageSpan);

        final JsonObject json = new JsonObject().put("my-payload", "bumlux");
        final LoraCommand loraCommand = new LoraCommand(json, "https://my-server.com/commands/deviceId/send");
        when(providerMock.getCommand(any(), any(), any(), any())).thenReturn(loraCommand);
        when(providerMock.getDefaultHeaders()).thenReturn(Map.of("my-provider-header", "my-provider-header-value"));

        final HttpRequest<Buffer> httpClientRequest = mock(HttpRequest.class, withSettings().defaultAnswer(RETURNS_SELF));
        final HttpResponse<Buffer> httpResponse = mock(HttpResponse.class);
        when(httpResponse.statusCode()).thenReturn(HttpURLConnection.HTTP_NO_CONTENT);
        when(httpClientRequest.sendJson(any(JsonObject.class))).thenReturn(Future.succeededFuture(httpResponse));

        when(webClient.postAbs(anyString())).thenReturn(httpClientRequest);
        commandHandler.apply(commandContext);

        verify(webClient, times(1)).postAbs("https://my-server.com/commands/deviceId/send");
        verify(httpClientRequest, times(1)).putHeader("my-header", "my-header-value");
        verify(httpClientRequest, times(1)).putHeader("my-provider-header", "my-provider-header-value");
        verify(httpClientRequest, times(1)).sendJson(json);
    }

    /**
     * Verifies that an options request is routed to a provider correctly.
     */
    @Test
    public void handleProviderRouteSuccessfullyForOptionsRequest() {
        final HttpContext httpContext = newHttpContext();

        adapter.handleOptionsRoute(httpContext.getRoutingContext());

        verify(httpContext.getRoutingContext().response()).setStatusCode(HttpResponseStatus.OK.code());
    }

    /**
     * Verifies that the provider route discards join messages.
     */
    @Test
    public void handleProviderRouteDiscardsJoinMessages() {

        givenATelemetrySenderForAnyTenant();

        final LoraMessage message = mock(LoraMessage.class);
        when(message.getType()).thenReturn(LoraMessageType.JOIN);
        final LoraProvider providerMock = getLoraProviderMock(message);
        final HttpContext httpContext = newHttpContext();

        adapter.handleProviderRoute(httpContext, providerMock);

        verify(httpContext.getRoutingContext()).put(LoraConstants.APP_PROPERTY_ORIG_LORA_PROVIDER, TEST_PROVIDER);
        assertNoTelemetryMessageHasBeenSentDownstream();
        verify(httpContext.getRoutingContext().response()).setStatusCode(HttpResponseStatus.ACCEPTED.code());
        verify(processMessageSpan).finish();
    }

    /**
     * Verifies that the provider route discards downlink messages.
     */
    @Test
    public void handleProviderRouteDiscardsDownlinkMessages() {

        givenATelemetrySenderForAnyTenant();

        final LoraMessage message = mock(LoraMessage.class);
        when(message.getType()).thenReturn(LoraMessageType.DOWNLINK);
        final LoraProvider providerMock = getLoraProviderMock(message);
        final HttpContext httpContext = newHttpContext();

        adapter.handleProviderRoute(httpContext, providerMock);

        verify(httpContext.getRoutingContext()).put(LoraConstants.APP_PROPERTY_ORIG_LORA_PROVIDER, TEST_PROVIDER);
        assertNoTelemetryMessageHasBeenSentDownstream();
        verify(httpContext.response()).setStatusCode(HttpResponseStatus.ACCEPTED.code());
        verify(processMessageSpan).finish();
    }

    /**
     * Verifies that the provider route discards other messages.
     */
    @Test
    public void handleProviderRouteDiscardsOtherMessages() {

        givenATelemetrySenderForAnyTenant();

        final LoraMessage message = mock(LoraMessage.class);
        when(message.getType()).thenReturn(LoraMessageType.UNKNOWN);
        final LoraProvider providerMock = getLoraProviderMock(message);
        final HttpContext httpContext = newHttpContext();

        adapter.handleProviderRoute(httpContext, providerMock);

        verify(httpContext.getRoutingContext()).put(LoraConstants.APP_PROPERTY_ORIG_LORA_PROVIDER, TEST_PROVIDER);
        assertNoTelemetryMessageHasBeenSentDownstream();
        verify(httpContext.response()).setStatusCode(HttpResponseStatus.ACCEPTED.code());
        verify(processMessageSpan).finish();
    }

    /**
     * Verifies that the provider route rejects invalid gateway credentials with unauthorized.
     */
    @Test
    public void handleProviderRouteCausesUnauthorizedForInvalidGatewayCredentials() {

        givenATelemetrySenderForAnyTenant();

        final LoraProvider providerMock = getLoraProviderMock();
        final HttpContext httpContext = newHttpContext();
        when(httpContext.getRoutingContext().user()).thenReturn(null);

        adapter.handleProviderRoute(httpContext, providerMock);

        verify(httpContext.getRoutingContext()).put(LoraConstants.APP_PROPERTY_ORIG_LORA_PROVIDER, TEST_PROVIDER);
        assertNoTelemetryMessageHasBeenSentDownstream();
        verifyUnauthorized(httpContext.getRoutingContext());
        verify(processMessageSpan).finish();
    }

    /**
     * Verifies that the provider route rejects a request if the request body cannot
     * be parsed.
     */
    @Test
    public void handleProviderRouteCausesBadRequestForFailureToParseBody() {

        givenATelemetrySenderForAnyTenant();

        final LoraProvider providerMock = getLoraProviderMock();
        when(providerMock.getMessage(any(RoutingContext.class))).thenThrow(new LoraProviderMalformedPayloadException("no device ID"));
        final HttpContext httpContext = newHttpContext();

        adapter.handleProviderRoute(httpContext, providerMock);

        verify(httpContext.getRoutingContext()).put(LoraConstants.APP_PROPERTY_ORIG_LORA_PROVIDER, TEST_PROVIDER);
        assertNoTelemetryMessageHasBeenSentDownstream();
        verifyBadRequest(httpContext.getRoutingContext());
        verify(processMessageSpan).finish();
    }

    /**
     * Verifies that the provider name is added to the message when using customized downstream message.
     */
    @Test
    public void customizeDownstreamMessageAddsProviderNameToMessage() {

        final HttpContext httpContext = newHttpContext();
        final Map<String, Object> props = new HashMap<>();

        adapter.customizeDownstreamMessageProperties(props, httpContext);

        assertThat(props).containsEntry(LoraConstants.APP_PROPERTY_ORIG_LORA_PROVIDER, TEST_PROVIDER);
    }

    private LoraProvider getLoraProviderMock() {
        final UplinkLoraMessage message = new UplinkLoraMessage(TEST_DEVICE_ID);
        message.setPayload(Buffer.buffer(TEST_PAYLOAD));
        return getLoraProviderMock(message);
    }

    private LoraProvider getLoraProviderMock(final LoraMessage message) {
        final LoraProvider provider = mock(LoraProvider.class);
        when(provider.getProviderName()).thenReturn(TEST_PROVIDER);
        when(provider.pathPrefixes()).thenReturn(Set.of("/bumlux"));
        when(provider.getMessage(any(RoutingContext.class))).thenReturn(message);

        return provider;
    }

    private HttpContext newHttpContext() {

        final LoraMetaData metaData = new LoraMetaData();
        metaData.setFunctionPort(TEST_FUNCTION_PORT);

        final HttpServerRequest request = mock(HttpServerRequest.class);
        when(request.path()).thenReturn("/lora");

        final RequestBody body = mock(RequestBody.class);
        when(body.buffer()).thenReturn(Buffer.buffer());
        final RoutingContext context = mock(RoutingContext.class);
        when(context.body()).thenReturn(body);
        when(context.user()).thenReturn(new DeviceUser(TEST_TENANT_ID, TEST_GATEWAY_ID));
        when(context.request()).thenReturn(request);
        when(context.response()).thenReturn(mock(HttpServerResponse.class));
        when(context.get(LoraConstants.APP_PROPERTY_ORIG_LORA_PROVIDER)).thenReturn(TEST_PROVIDER);
        when(context.get(LoraConstants.APP_PROPERTY_META_DATA)).thenReturn(metaData);
        final Span parentSpan = mock(Span.class);
        when(parentSpan.context()).thenReturn(mock(SpanContext.class));
        when(context.get(HttpServerSpanHelper.ROUTING_CONTEXT_SPAN_KEY)).thenReturn(parentSpan);

        return HttpContext.from(context);
    }

    private void setGatewayDeviceCommandEndpoint(final CommandEndpoint commandEndpoint) {
        when(registrationClient.assertRegistration(eq(TEST_TENANT_ID), eq(TEST_GATEWAY_ID), eq(null), (SpanContext) any()))
                .thenAnswer(invocation -> {
                    final String deviceId = invocation.getArgument(1);
                    final RegistrationAssertion regAssertion = new RegistrationAssertion(deviceId);
                    regAssertion.setCommandEndpoint(commandEndpoint);
                    return Future.succeededFuture(regAssertion);
                });
    }

    private void verifyUnauthorized(final RoutingContext routingContextMock) {
        verifyErrorCode(routingContextMock, HttpResponseStatus.UNAUTHORIZED);
    }

    private void verifyBadRequest(final RoutingContext routingContextMock) {
        verifyErrorCode(routingContextMock, HttpResponseStatus.BAD_REQUEST);
    }

    private void verifyErrorCode(final RoutingContext routingContextMock, final HttpResponseStatus expectedStatusCode) {
        final ArgumentCaptor<ClientErrorException> failCaptor = ArgumentCaptor.forClass(ClientErrorException.class);
        verify(routingContextMock).fail(failCaptor.capture());
        assertEquals(expectedStatusCode.code(), failCaptor.getValue().getErrorCode());
    }
}
