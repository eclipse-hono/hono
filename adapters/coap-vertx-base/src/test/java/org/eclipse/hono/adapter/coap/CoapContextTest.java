/**
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.eclipse.californium.core.server.resources.CoapExchange;
import org.eclipse.hono.auth.Device;
import org.eclipse.hono.util.Adapter;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.TenantObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;


/**
 * Tests verifying behavior of {@link CoapContext}.
 *
 */
public class CoapContextTest {

    private Vertx vertx;
    private Span span;

    /**
     * Sets up the fixture.
     */
    @BeforeEach
    void setUp() {
        vertx = mock(Vertx.class);

        span = mock(Span.class);
        final SpanContext spanContext = mock(SpanContext.class);
        when(span.context()).thenReturn(spanContext);
    }

    /**
     * Verifies that no ACK timer is started for a timeout value &lt;= 0.
     */
    @SuppressWarnings("unchecked")
    @ParameterizedTest
    @ValueSource(longs = {-1L, 0L})
    void testStartAckTimerDoesNotStartTimer(final long timeout) {
        final CoapExchange exchange = mock(CoapExchange.class);
        final Adapter coapConfig = new Adapter(Constants.PROTOCOL_ADAPTER_TYPE_COAP);
        coapConfig.putExtension(CoapConstants.TIMEOUT_TO_ACK, -1L); // never send separate ACK
        final TenantObject tenant = TenantObject.from("tenant", true).addAdapter(coapConfig);
        final Device authenticatedDevice = new Device(tenant.getTenantId(), "device-id");
        final CoapContext ctx = CoapContext.fromRequest(exchange, authenticatedDevice, authenticatedDevice, "4711", span);
        ctx.startAcceptTimer(vertx, tenant, timeout);
        verify(vertx, never()).setTimer(anyLong(), any(Handler.class));
    }

    /**
     * Verifies that the tenant specific value set for the ACK timeout gets
     * precedence over the global adapter configuration.
     */
    @SuppressWarnings("unchecked")
    @Test
    void testStartAckTimerUsesTenantSpecificTimeout() {
        final CoapExchange exchange = mock(CoapExchange.class);
        final Adapter coapConfig = new Adapter(Constants.PROTOCOL_ADAPTER_TYPE_COAP);
        coapConfig.putExtension(CoapConstants.TIMEOUT_TO_ACK, 200);
        final TenantObject tenant = TenantObject.from("tenant", true).addAdapter(coapConfig);
        final Device authenticatedDevice = new Device(tenant.getTenantId(), "device-id");
        final CoapContext ctx = CoapContext.fromRequest(exchange, authenticatedDevice, authenticatedDevice, "4711", span);
        ctx.startAcceptTimer(vertx, tenant, 500);
        verify(vertx).setTimer(eq(200L), any(Handler.class));
    }

    /**
     * Verifies that the global ACK timeout is used if no tenant specific value is configured.
     */
    @SuppressWarnings("unchecked")
    @Test
    void testStartAckTimerFallsBackToGlobalTimeout() {
        final CoapExchange exchange = mock(CoapExchange.class);
        final Adapter coapConfig = new Adapter(Constants.PROTOCOL_ADAPTER_TYPE_COAP);
        final TenantObject tenant = TenantObject.from("tenant", true).addAdapter(coapConfig);
        final Device authenticatedDevice = new Device(tenant.getTenantId(), "device-id");
        final CoapContext ctx = CoapContext.fromRequest(exchange, authenticatedDevice, authenticatedDevice, "4711", span);
        ctx.startAcceptTimer(vertx, tenant, 500);
        verify(vertx).setTimer(eq(500L), any(Handler.class));
    }

    /**
     * Verifies that the global ACK timeout is used if a tenant specific value is configured that is not a number.
     */
    @SuppressWarnings("unchecked")
    @Test
    void testStartAckTimerHandlesNonNumberPropertyValue() {
        final CoapExchange exchange = mock(CoapExchange.class);
        final Adapter coapConfig = new Adapter(Constants.PROTOCOL_ADAPTER_TYPE_COAP);
        coapConfig.putExtension(CoapConstants.TIMEOUT_TO_ACK, "not-a-number");
        final TenantObject tenant = TenantObject.from("tenant", true).addAdapter(coapConfig);
        final Device authenticatedDevice = new Device(tenant.getTenantId(), "device-id");
        final CoapContext ctx = CoapContext.fromRequest(exchange, authenticatedDevice, authenticatedDevice, "4711", span);
        ctx.startAcceptTimer(vertx, tenant, 500);
        verify(vertx).setTimer(eq(500L), any(Handler.class));
    }
}
