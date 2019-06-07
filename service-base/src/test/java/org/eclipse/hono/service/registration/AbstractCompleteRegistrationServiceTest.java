/*******************************************************************************
 * Copyright (c) 2016, 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.hono.service.registration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.Checkpoint;
import io.vertx.junit5.VertxTestContext;

import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.RegistrationResult;
import org.junit.jupiter.api.Test;

import java.net.HttpURLConnection;

/**
 * Abstract class used as a base for verifying behavior of {@link CompleteRegistrationService} in device registry implementations.
 *
 */
@Deprecated
public abstract class AbstractCompleteRegistrationServiceTest {

    /**
     * The tenant used in tests.
     */
    protected static final String TENANT = Constants.DEFAULT_TENANT;
    /**
     * The device identifier used in tests.
     */
    protected static final String DEVICE = "4711";
    /**
     * The gateway identifier used in the tests.
     */
    protected static final String GW = "gw-1";

    /**
     * Gets registration service being tested.
     * @return The registration service
     */
    public abstract CompleteRegistrationService getCompleteRegistrationService();

    /**
     * Verifies that the registry returns 404 when getting an unknown device.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testGetUnknownDeviceReturnsNotFound(final VertxTestContext ctx) {

        getCompleteRegistrationService()
                .getDevice(TENANT, DEVICE, ctx.succeeding(response -> ctx.verify(() -> {
                    assertEquals(HttpURLConnection.HTTP_NOT_FOUND, response.getStatus());
                    ctx.completeNow();
                })));
    }

    /**
     * Verifies that the registry returns 404 when unregistering an unknown device.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testDeregisterUnknownDeviceReturnsNotFound(final VertxTestContext ctx) {

        getCompleteRegistrationService()
                .removeDevice(TENANT, DEVICE, ctx.succeeding(response -> ctx.verify(() -> {
                            assertEquals(HttpURLConnection.HTTP_NOT_FOUND, response.getStatus());
                            ctx.completeNow();
                        })));
    }

    /**
     * Verifies that the registry returns 409 when trying to register a device twice.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testDuplicateRegistrationFails(final VertxTestContext ctx) {

        final Future<RegistrationResult> result = Future.future();
        final Checkpoint register = ctx.checkpoint(2);

        getCompleteRegistrationService().addDevice(TENANT, DEVICE, new JsonObject(), result);
        result.map(response -> {
            assertEquals(HttpURLConnection.HTTP_CREATED, response.getStatus());
            register.flag();
            return response;
        }).compose(ok -> {
            final Future<RegistrationResult> addResult = Future.future();
            getCompleteRegistrationService().addDevice(TENANT, DEVICE, new JsonObject(), addResult);
            return addResult;
        }).setHandler(
                ctx.succeeding(response -> ctx.verify(() -> {
                    assertEquals(HttpURLConnection.HTTP_CONFLICT, response.getStatus());
                    register.flag();
                })));
    }

    /**
     * Verifies that the registry returns 200 when getting an existing device.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testGetSucceedsForRegisteredDevice(final VertxTestContext ctx) {

        final Future<RegistrationResult> addResult = Future.future();

        getCompleteRegistrationService().addDevice(TENANT, DEVICE, new JsonObject(), addResult);
        addResult.map(r -> ctx.verify(() -> {
            assertEquals(HttpURLConnection.HTTP_CREATED, r.getStatus());
        }))
        .compose(ok -> {
            final Future<RegistrationResult> getResult = Future.future();
            getCompleteRegistrationService().getDevice(TENANT, DEVICE, getResult);
            return getResult;
        })
        .setHandler(ctx.succeeding(s -> ctx.verify(() -> {
            assertEquals(HttpURLConnection.HTTP_OK, s.getStatus());
            assertNotNull(s.getPayload());
            ctx.completeNow();
        })));
    }

    /**
     * Verifies that the registry returns a copy of the registered device information
     * on each invocation of the get operation..
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testGetReturnsCopyOfOriginalData(final VertxTestContext ctx) {

        final Future<RegistrationResult> addResult = Future.future();
        final Future<RegistrationResult> getResult = Future.future();

        getCompleteRegistrationService().addDevice(TENANT, DEVICE, new JsonObject(), addResult);
        addResult
        .compose(r -> {
            ctx.verify(() -> assertEquals(HttpURLConnection.HTTP_CREATED, r.getStatus()));
            getCompleteRegistrationService().getDevice(TENANT, DEVICE, getResult);
            return getResult;
        })
        .compose(r -> {
            ctx.verify(() -> assertEquals(HttpURLConnection.HTTP_OK, r.getStatus()));
            r.getPayload().put("new-prop", true);
            final Future<RegistrationResult> secondGetResult = Future.future();
            getCompleteRegistrationService().getDevice(TENANT, DEVICE, secondGetResult);
            return secondGetResult;
        })
        .setHandler(ctx.succeeding(secondGetResult -> {
            ctx.verify(() -> {
                assertEquals(HttpURLConnection.HTTP_OK, secondGetResult.getStatus());
                assertNotNull(getResult.result().getPayload().getBoolean("new-prop"));
                assertNotEquals(getResult.result().getPayload(), secondGetResult.getPayload());
                assertNotNull(secondGetResult.getPayload());
                assertNull(secondGetResult.getPayload().getBoolean("new-prop"));
                ctx.completeNow();
            });
        }));
    }

    /**
     * Verifies that the registry returns 404 when getting an unregistered device.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testGetFailsForDeregisteredDevice(final VertxTestContext ctx) {

        final Future<RegistrationResult> result = Future.future();
        final Checkpoint get = ctx.checkpoint(3);

        getCompleteRegistrationService().addDevice(TENANT, DEVICE, new JsonObject(), result);
        result.compose(response -> {
            assertEquals(HttpURLConnection.HTTP_CREATED, response.getStatus());
            get.flag();
            final Future<RegistrationResult> deregisterResult = Future.future();
            getCompleteRegistrationService().removeDevice(TENANT, DEVICE, deregisterResult);
            return deregisterResult;
        }).compose(response -> {
            assertEquals(HttpURLConnection.HTTP_NO_CONTENT, response.getStatus());
            get.flag();
            final Future<RegistrationResult> getResult = Future.future();
            getCompleteRegistrationService().getDevice(TENANT, DEVICE, getResult);
            return getResult;
        }).setHandler(ctx.succeeding(response -> ctx.verify(() -> {
            assertEquals(HttpURLConnection.HTTP_NOT_FOUND, response.getStatus());
            get.flag();
        })));
    }

    /**
     * Asserts that a device is registered.
     * 
     * @param tenantId The tenant that the device belongs to.
     * @param deviceId The identifier of the device.
     * @return A succeeded future if the device is registered.
     */
    protected final Future<RegistrationResult> assertRegistered(final String tenantId, final String deviceId) {
        final Future<RegistrationResult> result = Future.future();
        getCompleteRegistrationService().getDevice(tenantId, deviceId, result);
        return result.map(r -> {
            if (r.getStatus() == HttpURLConnection.HTTP_OK) {
                return r;
            } else {
                throw new ClientErrorException(HttpURLConnection.HTTP_PRECON_FAILED);
            }
        });
    }
}
