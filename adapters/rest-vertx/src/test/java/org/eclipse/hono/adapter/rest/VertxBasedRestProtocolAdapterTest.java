/**
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 */

package org.eclipse.hono.adapter.rest;

import static org.mockito.Mockito.*;

import io.vertx.core.*;
import org.eclipse.hono.client.HonoClient;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;

import java.util.Base64;

import static org.eclipse.hono.service.http.HttpEndpointUtils.CONTENT_TYPE_JSON;

/**
 * Verifies behavior of {@link VertxBasedRestProtocolAdapter}.
 *
 */
@RunWith(VertxUnitRunner.class)
public class VertxBasedRestProtocolAdapterTest {

    private static final String HOST                 = "localhost";
    private static final String AUTHORIZATION_HEADER = "Authorization";

    private static HonoClient messagingClient;
    private static HonoClient registrationClient;
    private static HonoClient credentialsClient;

    private static RestProtocolAdapterProperties config;
    private static VertxBasedRestProtocolAdapter restAdapter;

    private static Vertx vertx;

    @After
    public void shutDown() {
        vertx.close();
    }

    @Before
    public void setup(TestContext context) {
        vertx = Vertx.vertx();

        Future<String> setupTracker = Future.future();
        setupTracker.setHandler(context.asyncAssertSuccess());

        messagingClient = mock(HonoClient.class);
        registrationClient = mock(HonoClient.class);
        credentialsClient = mock(HonoClient.class);
        config = new RestProtocolAdapterProperties();
        config.setInsecurePortEnabled(true);
        config.setAuthenticationRequired(true);
        restAdapter = spy(VertxBasedRestProtocolAdapter.class);
        restAdapter.setConfig(config);
        restAdapter.setHonoMessagingClient(messagingClient);
        restAdapter.setRegistrationServiceClient(registrationClient);
        restAdapter.setCredentialsServiceClient(credentialsClient);

        Future<String> restServerDeploymentTracker = Future.future();
        vertx.deployVerticle(restAdapter, restServerDeploymentTracker.completer());
        restServerDeploymentTracker.compose(c -> setupTracker.complete(), setupTracker);
    }

    @Test
    public void testBasicAuthFailsEmptyHeader(final TestContext context) throws Exception {
        final Async async = context.async();

        vertx.createHttpClient().get(restAdapter.getInsecurePort(), HOST, "/somenonexistingroute")
                .putHeader("content-type", CONTENT_TYPE_JSON).handler(response -> {
                    context.assertEquals(401, response.statusCode());
                    response.bodyHandler(totalBuffer -> {
                        async.complete();
                    });
                }).exceptionHandler(context::fail).end();
    }

    @Test
    public void testBasicAuthFailsWrongCredentials(final TestContext context) throws Exception {
        final Async async = context.async();
        final String encodedUserPass = Base64.getEncoder()
                .encodeToString("testuser@DEFAULT_TENANT:password123".getBytes("utf-8"));

        Future<String> validationResult = Future.future();
        validationResult.fail("");

        doReturn(validationResult).when(restAdapter).validateCredentialsForDevice(anyObject(), anyObject(), anyObject(),
                anyObject());

        vertx.createHttpClient().put(restAdapter.getInsecurePort(), HOST, "/somenonexistingroute")
                .putHeader("content-type", CONTENT_TYPE_JSON)
                .putHeader(AUTHORIZATION_HEADER, "Basic " + encodedUserPass).handler(response -> {
                    context.assertEquals(401, response.statusCode());
                    response.bodyHandler(totalBuffer -> {
                        async.complete();
                    });
                }).exceptionHandler(context::fail).end();
    }

    @Test
    public void testBasicAuthSuccess(final TestContext context) throws Exception {
        final Async async = context.async();
        final String encodedUserPass = Base64.getEncoder()
                .encodeToString("existinguser@DEFAULT_TENANT:password123".getBytes("utf-8"));

        Future<String> validationResult = Future.future();
        validationResult.complete("device_1");

        doReturn(validationResult).when(restAdapter).validateCredentialsForDevice(anyObject(), anyObject(), anyObject(),
                anyObject());

        vertx.createHttpClient().get(restAdapter.getInsecurePort(), HOST, "/somenonexistingroute")
                .putHeader("content-type", CONTENT_TYPE_JSON)
                .putHeader(AUTHORIZATION_HEADER, "Basic " + encodedUserPass).handler(response -> {
                    context.assertEquals(404, response.statusCode());
                    response.bodyHandler(totalBuffer -> {
                        async.complete();
                    });
                }).exceptionHandler(context::fail).end();
    }
}
