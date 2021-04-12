/**
 * Copyright (c) 2018, 2021 Contributors to the Eclipse Foundation
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executor;

import org.eclipse.californium.core.coap.CoAP.Type;
import org.eclipse.californium.core.coap.OptionSet;
import org.eclipse.californium.core.coap.Request;
import org.eclipse.californium.core.network.Endpoint;
import org.eclipse.californium.core.network.Exchange;
import org.eclipse.californium.core.network.Exchange.Origin;
import org.eclipse.californium.core.server.resources.CoapExchange;
import org.eclipse.californium.elements.EndpointContext;
import org.eclipse.californium.elements.auth.PreSharedKeyIdentity;
import org.eclipse.hono.adapter.client.command.CommandConsumer;
import org.eclipse.hono.adapter.test.ProtocolAdapterMockSupport;
import org.eclipse.hono.test.TracingMockSupport;
import org.eclipse.hono.test.VertxMockSupport;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.RegistrationAssertion;
import org.eclipse.hono.util.TelemetryExecutionContext;
import org.eclipse.hono.util.TenantObject;
import org.junit.jupiter.api.BeforeEach;

import io.micrometer.core.instrument.Timer.Sample;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;

/**
 * Base class for implementing CoAP resource tests.
 */
public abstract class ResourceTestBase extends ProtocolAdapterMockSupport {

    protected CommandConsumer commandConsumer;
    protected CoapAdapterMetrics metrics;
    protected Span span;
    protected CoapProtocolAdapter adapter;
    protected CoapAdapterProperties properties;
    protected Vertx vertx;
    protected Endpoint secureEndpoint;

    /**
     * Sets up common fixture.
     */
    @BeforeEach
    public void setup() {

        vertx = mock(Vertx.class);
        metrics = mock(CoapAdapterMetrics.class);
        when(metrics.startTimer()).thenReturn(mock(Sample.class));

        span = TracingMockSupport.mockSpan();
        secureEndpoint = mock(Endpoint.class);

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
     * Creates a new CoAP exchange.
     *
     * @param payload The payload contained in the CoAP request.
     * @param requestType The request type.
     * @param contentFormat The payload's content format.
     * @return The exchange.
     */
    protected CoapExchange newCoapExchange(final Buffer payload, final Type requestType, final Integer contentFormat) {

        final OptionSet options = new OptionSet();
        Optional.ofNullable(contentFormat).ifPresent(options::setContentFormat);
        return newCoapExchange(payload, requestType, options);
    }

    /**
     * Creates a new CoAP exchange.
     *
     * @param payload The payload contained in the CoAP request.
     * @param requestType The request type.
     * @param options The request options.
     * @return The exchange.
     * @throws NullPointerException if request type is {@code null}-
     */
    protected CoapExchange newCoapExchange(final Buffer payload, final Type requestType, final OptionSet options) {

        Objects.requireNonNull(requestType);

        final EndpointContext epContext = mock(EndpointContext.class);
        when(epContext.getPeerAddress()).thenReturn(InetSocketAddress.createUnresolved("localhost", 15000));
        when(epContext.getPeerIdentity()).thenReturn(new PreSharedKeyIdentity("identity"));

        final Request request = mock(Request.class);
        when(request.getType()).thenReturn(requestType);
        when(request.isConfirmable()).thenReturn(requestType == Type.CON);
        when(request.getOptions()).thenReturn(Optional.ofNullable(options).orElseGet(OptionSet::new));
        when(request.getSourceContext()).thenReturn(epContext);
        Optional.ofNullable(payload).ifPresent(b -> when(request.getPayload()).thenReturn(b.getBytes()));

        final Exchange exchange = new Exchange(request, Origin.REMOTE, mock(Executor.class));
        exchange.setEndpoint(secureEndpoint);
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

    /**
     * Creates an adapter based on the mock service clients.
     * <p>
     * The returned adapter will also be assigned to the <em> adapter</em> field.
     *
     * @param configuration The adapter's configuration properties.
     * @return The adapter.
     */
    protected CoapProtocolAdapter givenAnAdapter(final CoapAdapterProperties configuration) {

        adapter = mock(CoapProtocolAdapter.class);
        when(adapter.checkMessageLimit(any(TenantObject.class), anyLong(), any())).thenReturn(Future.succeededFuture());
        when(adapter.getCommandConsumerFactory()).thenReturn(commandConsumerFactory);
        when(adapter.getCommandResponseSender(any(TenantObject.class))).thenReturn(commandResponseSender);
        when(adapter.getConfig()).thenReturn(configuration);
        when(adapter.getDownstreamMessageProperties(any(TelemetryExecutionContext.class))).thenReturn(new HashMap<>());
        when(adapter.getEventSender(any(TenantObject.class))).thenReturn(eventSender);
        when(adapter.getInsecureEndpoint()).thenReturn(null);
        when(adapter.getMetrics()).thenReturn(metrics);
        when(adapter.getRegistrationAssertion(anyString(), anyString(), any(), (SpanContext) any()))
            .thenAnswer(invocation -> {
                final String deviceId = invocation.getArgument(1);
                final RegistrationAssertion regAssertion = new RegistrationAssertion(deviceId);
                return Future.succeededFuture(regAssertion);
            });
        when(adapter.getSecureEndpoint()).thenReturn(secureEndpoint);
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
