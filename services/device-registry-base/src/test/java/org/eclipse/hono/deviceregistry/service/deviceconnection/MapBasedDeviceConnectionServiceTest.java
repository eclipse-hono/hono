/*******************************************************************************
 * Copyright (c) 2019, 2020 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.deviceregistry.service.deviceconnection;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.HttpURLConnection;

import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.DeviceConnectionConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.opentracing.Span;
import io.vertx.core.Context;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;


/**
 * Tests verifying behavior of {@link MapBasedDeviceConnectionService}.
 *
 */
@ExtendWith(VertxExtension.class)
public class MapBasedDeviceConnectionServiceTest {

    private MapBasedDeviceConnectionService svc;
    private Span span;
    private MapBasedDeviceConnectionsConfigProperties props;

    /**
     * Sets up fixture.
     */
    @BeforeEach
    public void setUp() {
        span = mock(Span.class);

        final Context ctx = mock(Context.class);
        final EventBus eventBus = mock(EventBus.class);
        final Vertx vertx = mock(Vertx.class);
        when(vertx.eventBus()).thenReturn(eventBus);

        svc = new MapBasedDeviceConnectionService();
        props = new MapBasedDeviceConnectionsConfigProperties();
        svc.setConfig(props);
        svc.init(vertx, ctx);
    }

    /**
     * Verifies that the last known gateway id can be set via the <em>setLastKnownGatewayForDevice</em> operation
     * and retrieved via <em>getLastKnownGatewayForDevice</em>.
     *
     * @param ctx The vert.x context.
     */
    @Test
    public void testSetAndGetLastKnownGatewayForDevice(final VertxTestContext ctx) {
        final String deviceId = "testDevice";
        final String gatewayId = "testGateway";
        svc.setLastKnownGatewayForDevice(Constants.DEFAULT_TENANT, deviceId, gatewayId, span)
                .compose(deviceConnectionResult -> {
                    ctx.verify(() -> {
                        assertEquals(HttpURLConnection.HTTP_NO_CONTENT, deviceConnectionResult.getStatus());
                    });
                    return svc.getLastKnownGatewayForDevice(Constants.DEFAULT_TENANT, deviceId, span);
                }).setHandler(ctx.succeeding(result -> ctx.verify(() -> {
                    assertEquals(HttpURLConnection.HTTP_OK, result.getStatus());
                    assertNotNull(result.getPayload());
                    assertEquals(gatewayId, result.getPayload().getString(DeviceConnectionConstants.FIELD_GATEWAY_ID));
                    assertNotNull(result.getPayload().getString(DeviceConnectionConstants.FIELD_LAST_UPDATED));
                    ctx.completeNow();
                })));
    }

    /**
     * Verifies that the <em>getLastKnownGatewayForDevice</em> operation fails if no such entry is associated
     * with the given device.
     *
     * @param ctx The vert.x context.
     */
    @Test
    public void testGetLastKnownGatewayForDeviceNotFound(final VertxTestContext ctx) {
        final String deviceId = "testDevice";
        svc.getLastKnownGatewayForDevice(Constants.DEFAULT_TENANT, deviceId, span)
                .setHandler(ctx.succeeding(deviceConnectionResult -> ctx.verify(() -> {
                    assertEquals(HttpURLConnection.HTTP_NOT_FOUND, deviceConnectionResult.getStatus());
                    assertNull(deviceConnectionResult.getPayload());
                    ctx.completeNow();
                })));
    }

    /**
     * Verifies that the <em>setLastKnownGatewayForDevice</em> operation fails if the maximum number of entries
     * for the given tenant is reached.
     *
     * @param ctx The vert.x context.
     */
    @Test
    public void testSetLastKnownGatewayForDeviceFailsIfLimitReached(final VertxTestContext ctx) {
        props.setMaxDevicesPerTenant(1);
        final String deviceId = "testDevice";
        final String gatewayId = "testGateway";
        svc.setLastKnownGatewayForDevice(Constants.DEFAULT_TENANT, deviceId, gatewayId, span)
                .compose(deviceConnectionResult -> {
                    ctx.verify(() -> {
                        assertEquals(HttpURLConnection.HTTP_NO_CONTENT, deviceConnectionResult.getStatus());
                    });
                    // set another entry
                    return svc.setLastKnownGatewayForDevice(Constants.DEFAULT_TENANT, "testDevice2", gatewayId, span);
                }).setHandler(ctx.succeeding(deviceConnectionResult -> ctx.verify(() -> {
                    assertEquals(HttpURLConnection.HTTP_FORBIDDEN, deviceConnectionResult.getStatus());
                    assertNull(deviceConnectionResult.getPayload());
                    ctx.completeNow();
                })));
    }
}
