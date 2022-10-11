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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.concurrent.Executor;

import org.eclipse.californium.core.coap.CoAP.Type;
import org.eclipse.californium.core.coap.OptionSet;
import org.eclipse.californium.core.coap.Request;
import org.eclipse.californium.core.network.Exchange;
import org.eclipse.californium.core.network.Exchange.Origin;
import org.eclipse.californium.core.server.resources.CoapExchange;
import org.eclipse.hono.adapter.test.ProtocolAdapterMockSupport;
import org.eclipse.hono.client.command.ProtocolAdapterCommandConsumer;
import org.eclipse.hono.test.TracingMockSupport;
import org.junit.jupiter.api.BeforeEach;

import io.opentracing.Span;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;

/**
 * Base class for implementing CoAP adapter based tests.
 *
 * @param <T> The type of adapter involved in the tests.
 * @param <P> The type of configuration properties the adapter requires.
 */
abstract class CoapProtocolAdapterMockSupport<T extends CoapProtocolAdapter, P extends CoapAdapterProperties> extends ProtocolAdapterMockSupport {

    ProtocolAdapterCommandConsumer commandConsumer;
    CoapAdapterMetrics metrics;
    Span span;
    T adapter;
    P properties;
    Vertx vertx;

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

        commandConsumer = mock(ProtocolAdapterCommandConsumer.class);
        when(commandConsumer.close(eq(false), any())).thenReturn(Future.succeededFuture());
        when(commandConsumerFactory.createCommandConsumer(anyString(), anyString(), eq(false), any(), any(), any()))
                .thenReturn(Future.succeededFuture(commandConsumer));
        when(commandConsumerFactory.createCommandConsumer(anyString(), anyString(), anyString(), eq(false), any(),
                any(), any())).thenReturn(Future.succeededFuture(commandConsumer));

        properties = newDefaultConfigurationProperties();
        properties.setInsecurePortEnabled(true);
        properties.setAuthenticationRequired(false);
    }

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

    /**
     * Creates new default configuration properties.
     *
     * @return The new properties.
     */
    abstract P newDefaultConfigurationProperties();
}
