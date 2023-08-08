/**
 * Copyright (c) 2020, 2023 Contributors to the Eclipse Foundation
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

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static com.google.common.truth.Truth.assertThat;

import org.eclipse.californium.core.coap.OptionSet;
import org.eclipse.californium.core.coap.Response;
import org.eclipse.californium.core.server.resources.CoapExchange;
import org.eclipse.hono.adapter.coap.option.TimeOption;
import org.eclipse.hono.service.auth.DeviceUser;
import org.eclipse.hono.test.TracingMockSupport;
import org.eclipse.hono.test.VertxMockSupport;
import org.eclipse.hono.util.Adapter;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.TenantObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.google.common.collect.Range;

import io.opentracing.Span;
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
        span = TracingMockSupport.mockSpan();
    }

    /**
     * Verifies that no ACK timer is started for a timeout value &lt;= 0.
     */
    @ParameterizedTest
    @ValueSource(longs = {-1L, 0L})
    void testStartAckTimerDoesNotStartTimer(final long timeout) {
        final CoapExchange exchange = mock(CoapExchange.class);
        final Adapter coapConfig = new Adapter(Constants.PROTOCOL_ADAPTER_TYPE_COAP);
        coapConfig.putExtension(CoapConstants.TIMEOUT_TO_ACK, -1L); // never send separate ACK
        final TenantObject tenant = TenantObject.from("tenant", true).addAdapter(coapConfig);
        final var authenticatedDevice = new DeviceUser(tenant.getTenantId(), "device-id");
        final CoapContext ctx = CoapContext.fromRequest(exchange, authenticatedDevice, authenticatedDevice, "4711", span);
        ctx.startAcceptTimer(vertx, tenant, timeout);
        verify(vertx, never()).setTimer(anyLong(), VertxMockSupport.anyHandler());
    }

    /**
     * Verifies that the tenant specific value set for the ACK timeout gets
     * precedence over the global adapter configuration.
     */
    @Test
    void testStartAckTimerUsesTenantSpecificTimeout() {
        final CoapExchange exchange = mock(CoapExchange.class);
        final Adapter coapConfig = new Adapter(Constants.PROTOCOL_ADAPTER_TYPE_COAP);
        coapConfig.putExtension(CoapConstants.TIMEOUT_TO_ACK, 200);
        final TenantObject tenant = TenantObject.from("tenant", true).addAdapter(coapConfig);
        final var authenticatedDevice = new DeviceUser(tenant.getTenantId(), "device-id");
        final CoapContext ctx = CoapContext.fromRequest(exchange, authenticatedDevice, authenticatedDevice, "4711", span);
        ctx.startAcceptTimer(vertx, tenant, 500);
        verify(vertx).setTimer(eq(200L), VertxMockSupport.anyHandler());
    }

    /**
     * Verifies that the tenant specific value of 0 is overwritten by the global
     * adapter configuration (larger 0), if a device uses the URI-query-parameter "piggy".
     */
    @Test
    void testStartAckTimerRespectsDeviceProvidedPiggyQueryParam() {

        // GIVEN a tenant that is configured to always return a CoAP response in a separate
        // message (i.e. never to use piggy-backing)
        final Adapter coapConfig = new Adapter(Constants.PROTOCOL_ADAPTER_TYPE_COAP);
        coapConfig.putExtension(CoapConstants.TIMEOUT_TO_ACK, 0);
        final TenantObject tenant = TenantObject.from("tenant", true).addAdapter(coapConfig);

        // WHEN a device sends a request that contains a "piggy" query parameter
        final var authenticatedDevice = new DeviceUser(tenant.getTenantId(), "device-id");
        final CoapExchange exchange = mock(CoapExchange.class);
        when(exchange.getQueryParameter(eq(CoapContext.PARAM_PIGGYBACKED))).thenReturn("true");
        final CoapContext ctx = CoapContext.fromRequest(exchange, authenticatedDevice, authenticatedDevice, "4711", span);
        ctx.startAcceptTimer(vertx, tenant, 500);

        // THEN the adapter sends the response (piggy-backed) in the ACK if a command is received within the general timeout to ACK
        verify(vertx).setTimer(eq(500L), VertxMockSupport.anyHandler());
    }

    /**
     * Verifies that the tenant specific value larger than 0 is not overwritten by the global
     * adapter configuration, even if a device uses the URI-query-parameter "piggy".
     */
    @Test
    void testStartAckTimerIgnoresDeviceProvidedPiggyQueryParam() {

        // GIVEN a tenant that is configured to use a timeout to ACK larger than 0
        final Adapter coapConfig = new Adapter(Constants.PROTOCOL_ADAPTER_TYPE_COAP);
        coapConfig.putExtension(CoapConstants.TIMEOUT_TO_ACK, 100);
        final TenantObject tenant = TenantObject.from("tenant", true).addAdapter(coapConfig);

        // WHEN a device sends a request that contains a "piggy" query parameter
        final var authenticatedDevice = new DeviceUser(tenant.getTenantId(), "device-id");
        final CoapExchange exchange = mock(CoapExchange.class);
        when(exchange.getQueryParameter(eq(CoapContext.PARAM_PIGGYBACKED))).thenReturn("true");
        final CoapContext ctx = CoapContext.fromRequest(exchange, authenticatedDevice, authenticatedDevice, "4711", span);
        ctx.startAcceptTimer(vertx, tenant, 500);

        // THEN the adapter sends the response (piggy-backed) in the ACK if a command is received within the tenant's timeout
        verify(vertx).setTimer(eq(100L), VertxMockSupport.anyHandler());
    }

    /**
     * Verifies that the global ACK timeout is used if no tenant specific value is configured.
     */
    @Test
    void testStartAckTimerFallsBackToGlobalTimeout() {
        final CoapExchange exchange = mock(CoapExchange.class);
        final Adapter coapConfig = new Adapter(Constants.PROTOCOL_ADAPTER_TYPE_COAP);
        final TenantObject tenant = TenantObject.from("tenant", true).addAdapter(coapConfig);
        final var authenticatedDevice = new DeviceUser(tenant.getTenantId(), "device-id");
        final CoapContext ctx = CoapContext.fromRequest(exchange, authenticatedDevice, authenticatedDevice, "4711", span);
        ctx.startAcceptTimer(vertx, tenant, 500);
        verify(vertx).setTimer(eq(500L), VertxMockSupport.anyHandler());
    }

    /**
     * Verifies that the global ACK timeout is used if a tenant specific value is configured that is not a number.
     */
    @Test
    void testStartAckTimerHandlesNonNumberPropertyValue() {
        final CoapExchange exchange = mock(CoapExchange.class);
        final Adapter coapConfig = new Adapter(Constants.PROTOCOL_ADAPTER_TYPE_COAP);
        coapConfig.putExtension(CoapConstants.TIMEOUT_TO_ACK, "not-a-number");
        final TenantObject tenant = TenantObject.from("tenant", true).addAdapter(coapConfig);
        final var authenticatedDevice = new DeviceUser(tenant.getTenantId(), "device-id");
        final CoapContext ctx = CoapContext.fromRequest(exchange, authenticatedDevice, authenticatedDevice, "4711", span);
        ctx.startAcceptTimer(vertx, tenant, 500);
        verify(vertx).setTimer(eq(500L), VertxMockSupport.anyHandler());
    }

    /**
     * Verifies that the CoAP time option is not in the response if not requested by either a request option or parameter.
     */
    @Test
    void testTimeOptionIsNotIncludedInResponseIfNotRequested() {
        final CoapExchange exchange = mock(CoapExchange.class);
        when(exchange.getRequestOptions()).thenReturn(new OptionSet());
        final Adapter coapConfig = new Adapter(Constants.PROTOCOL_ADAPTER_TYPE_COAP);
        final TenantObject tenant = TenantObject.from("tenant", true).addAdapter(coapConfig);
        final var authenticatedDevice = new DeviceUser(tenant.getTenantId(), "device-id");
        final CoapContext ctx = CoapContext.fromRequest(exchange, authenticatedDevice, authenticatedDevice, "4711", span);
        final Response response = mock(Response.class);
        final OptionSet responseOptions = new OptionSet();
        when(response.getOptions()).thenReturn(responseOptions);
        ctx.respond(response);
        assertThat(responseOptions.hasOption(TimeOption.DEFINITION)).isFalse();
    }

    /**
     * Verifies that the CoAP time option is in the response if requested by a request option.
     */
    @Test
    void testTimeOptionIsIncludedInResponseIfOptionPresentInRequest() {
        final long start = System.currentTimeMillis();
        final CoapExchange exchange = mock(CoapExchange.class);
        final OptionSet requestOptions = new OptionSet();
        requestOptions.addOption(new TimeOption(0));
        when(exchange.getRequestOptions()).thenReturn(requestOptions);
        final Adapter coapConfig = new Adapter(Constants.PROTOCOL_ADAPTER_TYPE_COAP);
        final TenantObject tenant = TenantObject.from("tenant", true).addAdapter(coapConfig);
        final var authenticatedDevice = new DeviceUser(tenant.getTenantId(), "device-id");
        final CoapContext ctx = CoapContext.fromRequest(exchange, authenticatedDevice, authenticatedDevice, "4711", span);
        final Response response = mock(Response.class);
        final OptionSet responseOptions = new OptionSet();
        when(response.getOptions()).thenReturn(responseOptions);
        ctx.respond(response);
        verify(response).getOptions();
        final var serverTimeOption = responseOptions.getOtherOption(TimeOption.DEFINITION);
        assertThat(serverTimeOption).isNotNull();
        final long end = System.currentTimeMillis();
        assertThat(serverTimeOption.getLongValue()).isIn(Range.closed(start, end));
    }

    /**
     * Verifies that the CoAP time option is in the response if requested by a request parameter.
     */
    @Test
    void testTimeOptionIsIncludedInResponseIfParameterPresentInRequest() {
        final long start = System.currentTimeMillis();
        final CoapExchange exchange = mock(CoapExchange.class);
        when(exchange.getQueryParameter(eq(TimeOption.QUERY_PARAMETER_NAME))).thenReturn("true");
        final Adapter coapConfig = new Adapter(Constants.PROTOCOL_ADAPTER_TYPE_COAP);
        final TenantObject tenant = TenantObject.from("tenant", true).addAdapter(coapConfig);
        final var authenticatedDevice = new DeviceUser(tenant.getTenantId(), "device-id");
        final CoapContext ctx = CoapContext.fromRequest(exchange, authenticatedDevice, authenticatedDevice, "4711", span);
        final Response response = mock(Response.class);
        final OptionSet responseOptions = new OptionSet();
        when(response.getOptions()).thenReturn(responseOptions);
        ctx.respond(response);
        verify(response).getOptions();
        final var serverTimeOption = responseOptions.getOtherOption(TimeOption.DEFINITION);
        assertThat(serverTimeOption).isNotNull();
        final long end = System.currentTimeMillis();
        assertThat(serverTimeOption.getLongValue()).isIn(Range.closed(start, end));
    }
}
