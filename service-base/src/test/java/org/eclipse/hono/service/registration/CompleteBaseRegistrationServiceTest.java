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

import static org.mockito.Mockito.mock;

import java.net.HttpURLConnection;

import org.eclipse.hono.config.ServiceConfigProperties;
import org.eclipse.hono.config.SignatureSupportingConfigProperties;
import org.eclipse.hono.util.Constants;

import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;


/**
 * Tests verifying behavior of {@link CompleteBaseRegistrationService}.
 *
 *
 */
@RunWith(VertxUnitRunner.class)
public class CompleteBaseRegistrationServiceTest {

    /**
     * Time out each test case after 5 secs.
     */
    @Rule
    public Timeout timeout = Timeout.seconds(5);

    private static String secret = "dafhkjsdahfuksahuioahgfdahsgjkhfdjkg";
    private static SignatureSupportingConfigProperties props;
    private static Vertx vertx;

    /**
     * Initializes common properties.
     */
    @BeforeClass
    public static void init() {
        vertx = mock(Vertx.class);
        props = new SignatureSupportingConfigProperties();
        props.setSharedSecret(secret);
    }

    /**
     * Verifies that the service cannot be started without either <em>signingSecret</em> or
     * <em>signingKeyPath</em> being set.
     * 
     * @param ctx The vertx unit test context.
     */
    @Test
    public void testStartupFailsIfNoRegistrationAssertionFactoryIsSet(final TestContext ctx) {

        // GIVEN a registry without an assertion factory being set
        final CompleteBaseRegistrationService<ServiceConfigProperties> registrationService = newCompleteRegistrationService();

        // WHEN starting the service
        final Async startupFailure = ctx.async();
        final Future<Void> startFuture = Future.future();
        startFuture.setHandler(ctx.asyncAssertFailure(t -> startupFailure.complete()));
        registrationService.doStart(startFuture);

        // THEN startup fails
        startupFailure.await();
    }

    /**
     * Verifies that the addDevice method returns not implemented.
     *
     * @param ctx The vertx unit test context.
     */
    @Test
    public void testAddDevice(final TestContext ctx) {

        // GIVEN an empty registry
        final CompleteBaseRegistrationService<ServiceConfigProperties> registrationService = newCompleteRegistrationService();
        registrationService.setRegistrationAssertionFactory(RegistrationAssertionHelperImpl.forSigning(vertx, props));

        // WHEN trying to add a new device
        registrationService.addDevice(Constants.DEFAULT_TENANT, "4711", new JsonObject(), ctx.asyncAssertSuccess(result -> {
            // THEN the response contain a JWT token with an empty result with status code 501.
            ctx.assertEquals(result.getStatus(), HttpURLConnection.HTTP_NOT_IMPLEMENTED);
            ctx.assertNull(result.getPayload());
        }));
    }

    /**
     * Verifies that the updateDevice method returns not implemented.
     *
     * @param ctx The vertx unit test context.
     */
    @Test
    public void testUpdateDevice(final TestContext ctx) {

        // GIVEN an empty registry
        final CompleteBaseRegistrationService<ServiceConfigProperties> registrationService = newCompleteRegistrationService();
        registrationService.setRegistrationAssertionFactory(RegistrationAssertionHelperImpl.forSigning(vertx, props));

        // WHEN trying to update a device
        registrationService.updateDevice(Constants.DEFAULT_TENANT, "4711", new JsonObject(), ctx.asyncAssertSuccess(result -> {
            // THEN the response contain a JWT token with an empty result with status code 501.
            ctx.assertEquals(result.getStatus(), HttpURLConnection.HTTP_NOT_IMPLEMENTED);
            ctx.assertNull(result.getPayload());
        }));
    }

    /**
     * Verifies that the removeDevice method returns not implemented.
     *
     * @param ctx The vertx unit test context.
     */
    @Test
    public void testRemoveDevice(final TestContext ctx) {

        // GIVEN an empty registry
        final CompleteBaseRegistrationService<ServiceConfigProperties> registrationService = newCompleteRegistrationService();
        registrationService.setRegistrationAssertionFactory(RegistrationAssertionHelperImpl.forSigning(vertx, props));

        // WHEN trying to remove a device
        registrationService.removeDevice(Constants.DEFAULT_TENANT, "4711", ctx.asyncAssertSuccess(result -> {
            // THEN the response contain a JWT token with an empty result with status code 501.
            ctx.assertEquals(result.getStatus(), HttpURLConnection.HTTP_NOT_IMPLEMENTED);
            ctx.assertNull(result.getPayload());
        }));
    }

    /**
    * Verifies that the getDevice method returns not implemented.
    *
    * @param ctx The vertx unit test context.
    */
    @Test
    public void testGetDevice(final TestContext ctx) {

        // GIVEN an empty registry
        final CompleteBaseRegistrationService<ServiceConfigProperties> registrationService = newCompleteRegistrationService();
        registrationService.setRegistrationAssertionFactory(RegistrationAssertionHelperImpl.forSigning(vertx, props));

        // WHEN trying to get a device's data
        registrationService.getDevice(Constants.DEFAULT_TENANT, "4711", ctx.asyncAssertSuccess(result -> {
            // THEN the response contain a JWT token with an empty result with status code 501.
            ctx.assertEquals(result.getStatus(), HttpURLConnection.HTTP_NOT_IMPLEMENTED);
            ctx.assertNull(result.getPayload());
        }));
    }


    private CompleteBaseRegistrationService<ServiceConfigProperties> newCompleteRegistrationService() {

        return new CompleteBaseRegistrationService<ServiceConfigProperties>() {

            @Override
            protected String getEventBusAddress() {
                return "requests.in";
            }

            @Override
            public void setConfig(final ServiceConfigProperties configuration) {
                setSpecificConfig(configuration);
            }
        };
    }
}
