/**
 * Copyright (c) 2018, 2022 Contributors to the Eclipse Foundation
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

import java.util.HashMap;
import java.util.Optional;
import java.util.concurrent.Executor;

import org.eclipse.californium.core.coap.CoAP.Type;
import org.eclipse.californium.core.coap.OptionSet;
import org.eclipse.californium.core.coap.Request;
import org.eclipse.californium.core.network.Endpoint;
import org.eclipse.californium.core.network.Exchange;
import org.eclipse.californium.core.network.Exchange.Origin;
import org.eclipse.californium.core.server.resources.CoapExchange;
import org.eclipse.hono.adapter.TelemetryExecutionContext;
import org.eclipse.hono.test.VertxMockSupport;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.MessagingType;
import org.eclipse.hono.util.RegistrationAssertion;
import org.eclipse.hono.util.TenantObject;

import io.opentracing.SpanContext;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;

/**
 * Base class for implementing CoAP resource tests.
 */
abstract class ResourceTestBase extends CoapProtocolAdapterMockSupport<CoapProtocolAdapter, CoapAdapterProperties> {

    CoapProtocolAdapter adapter;

    static CoapExchange newCoapExchange(final Buffer payload, final Type requestType, final Integer contentFormat) {

        final OptionSet options = new OptionSet();
        Optional.ofNullable(contentFormat).ifPresent(options::setContentFormat);
        return newCoapExchange(payload, requestType, options);
    }

    static CoapExchange newCoapExchange(final Buffer payload, final Type requestType, final OptionSet options) {
        final Request request = mock(Request.class);
        when(request.getType()).thenReturn(requestType);
        when(request.isConfirmable()).thenReturn(requestType == Type.CON);
        when(request.getOptions()).thenReturn(options);
        return newCoapExchange(payload, request);
    }

    static CoapExchange newCoapExchange(final Buffer payload, final Request request) {

        final OptionSet options = request.getOptions();
        final Object identity = "dummy";
        final Exchange exchange = new Exchange(request, identity, Origin.REMOTE, mock(Executor.class));
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

    CoapProtocolAdapter givenAnAdapter(final CoapAdapterProperties configuration) {

        adapter = mock(CoapProtocolAdapter.class);
        when(adapter.checkMessageLimit(any(TenantObject.class), anyLong(), any())).thenReturn(Future.succeededFuture());
        when(adapter.getCommandConsumerFactory()).thenReturn(commandConsumerFactory);
        when(adapter.getCommandResponseSender(any(MessagingType.class), any(TenantObject.class))).thenReturn(commandResponseSender);
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

    /**
     * {@inheritDoc}
     */
    @Override
    CoapAdapterProperties newDefaultConfigurationProperties() {
        return new CoapAdapterProperties();
    }
}
