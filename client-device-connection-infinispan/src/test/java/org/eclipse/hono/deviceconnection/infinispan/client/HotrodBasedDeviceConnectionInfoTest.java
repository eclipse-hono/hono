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


package org.eclipse.hono.deviceconnection.infinispan.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.DeviceConnectionConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.vertx.core.Future;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;


/**
 * Tests verifying behavior of {@link HotrodBasedDeviceConnectionInfo}.
 *
 */
@ExtendWith(VertxExtension.class)
@Timeout(value = 5, timeUnit = TimeUnit.SECONDS)
class HotrodBasedDeviceConnectionInfoTest {

    private DeviceConnectionInfo info;
    private RemoteCache<String, String> cache;
    private Tracer tracer;

    /**
     * Sets up the fixture.
     */
    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        cache = mock(RemoteCache.class);
        tracer = mock(Tracer.class);
        info = new HotrodBasedDeviceConnectionInfo(cache, tracer);
    }

    /**
     * Verifies that a last known gateway can be successfully set.
     * 
     * @param ctx The vert.x test context.
     */
    @Test
    void testSetLastKnownGatewaySucceeds(final VertxTestContext ctx) {

        when(cache.put(anyString(), anyString())).thenReturn(Future.succeededFuture("oldValue"));
        info.setLastKnownGatewayForDevice(Constants.DEFAULT_TENANT, "device-id", "gw-id", null)
            .setHandler(ctx.completing());
    }

    /**
     * Verifies that a request to set a gateway fails with a {@link org.eclipse.hono.client.ServiceInvocationException}.
     * 
     * @param ctx The vert.x test context.
     */
    @Test
    void testSetLastKnownGatewayFails(final VertxTestContext ctx) {

        when(cache.put(anyString(), anyString())).thenReturn(Future.failedFuture(new IOException("not available")));
        info.setLastKnownGatewayForDevice(Constants.DEFAULT_TENANT, "device-id", "gw-id", mock(SpanContext.class))
            .setHandler(ctx.failing(t -> {
                ctx.verify(() -> assertThat(t).isInstanceOf(ServiceInvocationException.class));
                ctx.completeNow();
            }));
    }

    /**
     * Verifies that a last known gateway can be successfully retrieved.
     * 
     * @param ctx The vert.x test context.
     */
    @Test
    void testGetLastKnownGatewaySucceeds(final VertxTestContext ctx) {

        when(cache.get(anyString())).thenReturn(Future.succeededFuture("gw-id"));
        info.getLastKnownGatewayForDevice(Constants.DEFAULT_TENANT, "device-id", null)
            .setHandler(ctx.succeeding(value -> {
                ctx.verify(() -> {
                    assertThat(value.getString(DeviceConnectionConstants.FIELD_GATEWAY_ID)).isEqualTo("gw-id");
                });
                ctx.completeNow();
            }));
    }

    /**
     * Verifies that a request to set a gateway fails with a {@link org.eclipse.hono.client.ServiceInvocationException}.
     * 
     * @param ctx The vert.x test context.
     */
    @Test
    void testGetLastKnownGatewayFails(final VertxTestContext ctx) {

        when(cache.get(anyString())).thenReturn(Future.failedFuture(new IOException("not available")));
        info.getLastKnownGatewayForDevice(Constants.DEFAULT_TENANT, "device-id", mock(SpanContext.class))
            .setHandler(ctx.failing(t -> {
                ctx.verify(() -> assertThat(t).isInstanceOf(ServiceInvocationException.class));
                ctx.completeNow();
            }));
    }
}
