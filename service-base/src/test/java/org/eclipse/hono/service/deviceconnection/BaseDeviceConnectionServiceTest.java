/*******************************************************************************
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.service.deviceconnection;

import java.net.HttpURLConnection;

import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.util.EventBusMessage;
import org.eclipse.hono.util.DeviceConnectionConstants;
import org.eclipse.hono.util.DeviceConnectionResult;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;

import io.opentracing.Span;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;


/**
 * Tests verifying behavior of {@link BaseDeviceConnectionService}.
 *
 */
@RunWith(VertxUnitRunner.class)
public class BaseDeviceConnectionServiceTest {

    private static final String TEST_TENANT = "dummy";

    private static BaseDeviceConnectionService<Object> deviceConnectionService;

    /**
     * Time out each test after five seconds.
     */
    public final Timeout timeout = Timeout.seconds(5);

    /**
     * Sets up the fixture.
     */
    @BeforeClass
    public static void setUp() {
        deviceConnectionService = createBaseDeviceConnectionService();
    }

    /**
     * Verifies that the base service rejects a request for setting the last known gateway if no device id is given.
     * 
     * @param ctx The vert.x test context.
     */
    @Test
    public void testSetLastKnownGatewayFailsForMissingDevice(final TestContext ctx) {

        final EventBusMessage request = createRequest(DeviceConnectionConstants.DeviceConnectionAction.SET_LAST_GATEWAY,
                null, "testGW");
        deviceConnectionService.processRequest(request).setHandler(ctx.asyncAssertFailure(throwable -> {
            ctx.assertTrue(throwable instanceof ClientErrorException);
        }));
    }

    /**
     * Verifies that the base service rejects a request for setting the last known gateway if no gateway id is given.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testSetLastKnownGatewayFailsForMissingGateway(final TestContext ctx) {

        final EventBusMessage request = createRequest(DeviceConnectionConstants.DeviceConnectionAction.SET_LAST_GATEWAY,
                "testDevice", null);
        deviceConnectionService.processRequest(request).setHandler(ctx.asyncAssertFailure(throwable -> {
            ctx.assertTrue(throwable instanceof ClientErrorException);
        }));
    }

    /**
     * Verifies that the base service routes a request for setting the last known gateway to the corresponding
     * <em>setLastKnownGatewayForDevice</em> method.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testSetLastKnownGatewaySucceeds(final TestContext ctx) {

        final EventBusMessage request = createRequest(DeviceConnectionConstants.DeviceConnectionAction.SET_LAST_GATEWAY,
                "testDevice", "testGateway");
        deviceConnectionService.processRequest(request).setHandler(ctx.asyncAssertSuccess(response -> {
            ctx.assertEquals(HttpURLConnection.HTTP_OK, response.getStatus());
        }));
    }

    /**
     * Verifies that the base service rejects a request for retrieving the last known gateway if no device id is given.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testGetLastKnownGatewayFailsForMissingDevice(final TestContext ctx) {

        final EventBusMessage request = createRequest(DeviceConnectionConstants.DeviceConnectionAction.GET_LAST_GATEWAY, null,
                null);
        deviceConnectionService.processRequest(request).setHandler(ctx.asyncAssertFailure(throwable -> {
            ctx.assertTrue(throwable instanceof ClientErrorException);
        }));
    }

    /**
     * Verifies that the base service routes a request for retrieving the last known gateway to the corresponding
     * <em>getLastKnownGatewayForDevice</em> method.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testGetLastKnownGatewaySucceeds(final TestContext ctx) {

        final String deviceId = "testDevice";
        final EventBusMessage request = createRequest(DeviceConnectionConstants.DeviceConnectionAction.GET_LAST_GATEWAY, deviceId,
                "testGateway");
        deviceConnectionService.processRequest(request).setHandler(ctx.asyncAssertSuccess(response -> {
            ctx.assertEquals(HttpURLConnection.HTTP_OK, response.getStatus());
            ctx.assertEquals(deviceId, response.getJsonPayload().getString(DeviceConnectionConstants.FIELD_GATEWAY_ID));
        }));
    }

    private static EventBusMessage createRequest(final DeviceConnectionConstants.DeviceConnectionAction action,
            final String deviceId, final String gatewayId) {

        return EventBusMessage.forOperation(action.getSubject())
                .setTenant(TEST_TENANT)
                .setDeviceId(deviceId)
                .setGatewayId(gatewayId);
    }

    private static BaseDeviceConnectionService<Object> createBaseDeviceConnectionService() {

        return new BaseDeviceConnectionService<>() {

            @Override
            public void setLastKnownGatewayForDevice(final String tenantId, final String deviceId,
                    final String gatewayId, final Span span, final Handler<AsyncResult<DeviceConnectionResult>> resultHandler) {
                resultHandler.handle(Future.succeededFuture(DeviceConnectionResult.from(HttpURLConnection.HTTP_OK)));
            }

            @Override
            public void getLastKnownGatewayForDevice(final String tenantId, final String deviceId, final Span span,
                    final Handler<AsyncResult<DeviceConnectionResult>> resultHandler) {
                final JsonObject payload = new JsonObject().put(DeviceConnectionConstants.FIELD_GATEWAY_ID, deviceId);
                resultHandler.handle(Future.succeededFuture(DeviceConnectionResult.from(HttpURLConnection.HTTP_OK, payload)));
            }

            @Override
            public void setConfig(final Object configuration) {
            }
        };
    }
}
