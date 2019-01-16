/*******************************************************************************
 * Copyright (c) 2016, 2018 Contributors to the Eclipse Foundation
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

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.TestContext;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.RegistrationResult;
import org.junit.Test;

import java.net.HttpURLConnection;

/**
 * Abstract class used as a base for verifying behavior of {@link CompleteRegistrationService} in device registry implementations.
 *
 */
public abstract class AbstractCompleteRegistrationServiceTest {

    protected static final String TENANT = Constants.DEFAULT_TENANT;
    protected static final String DEVICE = "4711";
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
    public void testGetUnknownDeviceReturnsNotFound(final TestContext ctx) {

        getCompleteRegistrationService()
                .getDevice(TENANT, DEVICE, ctx.asyncAssertSuccess(response -> {
                    ctx.assertEquals(HttpURLConnection.HTTP_NOT_FOUND, response.getStatus());
                }));
    }

    /**
     * Verifies that the registry returns 404 when unregistering an unknown device.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testDeregisterUnknownDeviceReturnsNotFound(final TestContext ctx) {

        getCompleteRegistrationService()
                .removeDevice(TENANT, DEVICE,
                        ctx.asyncAssertSuccess(response -> {
                            ctx.assertEquals(HttpURLConnection.HTTP_NOT_FOUND, response.getStatus());
                        }));
    }

    /**
     * Verifies that the registry returns 409 when trying to register a device twice.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testDuplicateRegistrationFails(final TestContext ctx) {

        final Future<RegistrationResult> result = Future.future();

        getCompleteRegistrationService().addDevice(TENANT, DEVICE, new JsonObject(), result.completer());
        result.map(response -> {
            ctx.assertEquals(HttpURLConnection.HTTP_CREATED, response.getStatus());
            return response;
        }).compose(ok -> {
            final Future<RegistrationResult> addResult = Future.future();
            getCompleteRegistrationService().addDevice(TENANT, DEVICE, new JsonObject(), addResult.completer());
            return addResult;
        }).setHandler(
                ctx.asyncAssertSuccess(response -> {
                    ctx.assertEquals(HttpURLConnection.HTTP_CONFLICT, response.getStatus());
                }));
    }

    /**
     * Verifies that the registry returns 200 when getting an existing device.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testGetSucceedsForRegisteredDevice(final TestContext ctx) {

        final Future<RegistrationResult> result = Future.future();

        getCompleteRegistrationService().addDevice(TENANT, DEVICE, new JsonObject(), result.completer());
        result.compose(response -> {
            ctx.assertEquals(HttpURLConnection.HTTP_CREATED, response.getStatus());
            final Future<RegistrationResult> addResult = Future.future();
            getCompleteRegistrationService().getDevice(TENANT, DEVICE, addResult.completer());
            return addResult;
        }).setHandler(
                ctx.asyncAssertSuccess(s -> {
                            ctx.assertEquals(HttpURLConnection.HTTP_OK, s.getStatus());
                            ctx.assertNotNull(s.getPayload());
                        }
                ));
    }

    /**
     * Verifies that the registry returns 404 when getting an unregistered device.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testGetFailsForDeregisteredDevice(final TestContext ctx) {

        final Future<RegistrationResult> result = Future.future();

        getCompleteRegistrationService().addDevice(TENANT, DEVICE, new JsonObject(), result.completer());
        result.compose(response -> {
            ctx.assertEquals(HttpURLConnection.HTTP_CREATED, response.getStatus());
            final Future<RegistrationResult> deregisterResult = Future.future();
            getCompleteRegistrationService().removeDevice(TENANT, DEVICE, deregisterResult.completer());
            return deregisterResult;
        }).compose(response -> {
            ctx.assertEquals(HttpURLConnection.HTTP_NO_CONTENT, response.getStatus());
            final Future<RegistrationResult> getResult = Future.future();
            getCompleteRegistrationService().getDevice(TENANT, DEVICE, getResult.completer());
            return getResult;
        }).setHandler(ctx.asyncAssertSuccess(response -> {
            ctx.assertEquals(HttpURLConnection.HTTP_NOT_FOUND, response.getStatus());
        }));
    }
}
