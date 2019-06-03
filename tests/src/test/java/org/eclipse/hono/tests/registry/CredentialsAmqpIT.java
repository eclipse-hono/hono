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
package org.eclipse.hono.tests.registry;

import java.net.HttpURLConnection;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.eclipse.hono.client.CredentialsClient;
import org.eclipse.hono.client.CredentialsClientFactory;
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.tests.IntegrationTestSupport;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.CredentialsConstants;
import org.eclipse.hono.util.CredentialsObject;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;

/**
 * Tests verifying the behavior of the Device Registry component's Credentials AMQP endpoint.
 */
@RunWith(VertxUnitRunner.class)
public class CredentialsAmqpIT {

    private static final Vertx vertx = Vertx.vertx();

    private static CredentialsClientFactory client;
    private static CredentialsClient credentialsClient;
    private static IntegrationTestSupport helper;

    /**
     * Global timeout for all test cases.
     */
    @Rule
    public Timeout globalTimeout = new Timeout(5, TimeUnit.SECONDS);

    /**
     * Starts the device registry and connects a client.
     *
     * @param ctx The vert.x test context.
     */
    @BeforeClass
    public static void prepareDeviceRegistry(final TestContext ctx) {

        helper = new IntegrationTestSupport(vertx);
        helper.initRegistryClient(ctx);

        client = DeviceRegistryAmqpTestSupport.prepareCredentialsClientFactory(vertx,
                IntegrationTestSupport.HONO_USER, IntegrationTestSupport.HONO_PWD);

        client.connect()
            .compose(c -> client.getOrCreateCredentialsClient(Constants.DEFAULT_TENANT))
            .setHandler(ctx.asyncAssertSuccess(r -> {
                credentialsClient = r;
            }));
    }

    /**
     * Remove the fixture from the device registry if the test had set up any.
     *
     * @param ctx The vert.x test context.
     */
    @After
    public void cleanupDeviceRegistry(final TestContext ctx){
        helper.deleteObjects(ctx);
    }

    /**
     * Shuts down the device registry and closes the client.
     *
     * @param ctx The vert.x test context.
     */
    @AfterClass
    public static void shutdown(final TestContext ctx) {

        DeviceRegistryAmqpTestSupport.disconnect(ctx, client);
    }

    /**
     * Verify that a request to retrieve credentials for a non-existing authentication
     * ID fails with a 404.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testGetCredentialsFailsForNonExistingAuthId(final TestContext ctx) {

        credentialsClient
        .get(CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD, "nonExisting")
        .setHandler(ctx.asyncAssertFailure(t -> {
            ctx.assertEquals(
                    HttpURLConnection.HTTP_NOT_FOUND,
                    ((ServiceInvocationException) t).getErrorCode());
        }));
    }

    /**
     * Verifies that the service returns credentials for a given type and authentication ID
     * including the default value for the enabled property..
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testGetCredentialsByTypeAndAuthId(final TestContext ctx) {

        final CredentialsObject credentials = getRandomHashedPasswordCredentials();

        helper.registry
        .addCredentials(Constants.DEFAULT_TENANT, JsonObject.mapFrom(credentials))
        .compose(ok -> credentialsClient.get(CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD, credentials.getAuthId()))
        .setHandler(ctx.asyncAssertSuccess(result -> {
            assertStandardProperties(
                    ctx,
                    result,
                    credentials.getDeviceId(),
                    true,
                    credentials.getAuthId(),
                    CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD,
                    2);
        }));
    }

    /**
     * Verifies that the service returns credentials for a given type and authentication ID
     * including its explicitly set enabled property.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testGetCredentialsIncludesEnabledProperty(final TestContext ctx) {

        final CredentialsObject credentials = getRandomHashedPasswordCredentials();
        credentials.setEnabled(false);

        helper.registry
        .addCredentials(Constants.DEFAULT_TENANT, JsonObject.mapFrom(credentials))
        .compose(r -> credentialsClient.get(CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD, credentials.getAuthId()))
        .setHandler(ctx.asyncAssertSuccess(result -> {
            assertStandardProperties(
                    ctx,
                    result,
                    credentials.getDeviceId(),
                    credentials.isEnabled(),
                    credentials.getAuthId(),
                    CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD,
                    2);
        }));
    }

    /**
     * Verifies that the service returns credentials for a given type, authentication ID and matching client context.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testGetCredentialsByClientContext(final TestContext ctx) {

        final CredentialsObject credentials = getRandomHashedPasswordCredentials();
        credentials.setProperty("client-id", "gateway-one");

        final JsonObject clientContext = new JsonObject()
                .put("client-id", "gateway-one");

        helper.registry
        .addCredentials(Constants.DEFAULT_TENANT, JsonObject.mapFrom(credentials))
        .compose(ok -> credentialsClient.get(CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD, credentials.getAuthId(), clientContext))
        .setHandler(ctx.asyncAssertSuccess(result -> {
            assertStandardProperties(
                    ctx,
                    result,
                    credentials.getDeviceId(),
                    true,
                    credentials.getAuthId(),
                    CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD,
                    2);
            ctx.assertEquals("gateway-one", result.getProperty("client-id"));
        }));
    }

    /**
     * Verifies that a request for credentials using a non-matching client context
     * fails with a 404.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testGetCredentialsFailsForNonMatchingClientContext(final TestContext ctx) {

        final CredentialsObject credentials = getRandomHashedPasswordCredentials();
        credentials.setProperty("client-id", "gateway-one");

        final JsonObject clientContext = new JsonObject()
                .put("client-id", "non-matching");

        helper.registry
        .addCredentials(Constants.DEFAULT_TENANT, JsonObject.mapFrom(credentials))
        .compose(ok ->  credentialsClient.get(CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD, credentials.getAuthId(), clientContext))
        .setHandler(ctx.asyncAssertFailure( t -> {
            ctx.assertEquals(
                    HttpURLConnection.HTTP_NOT_FOUND,
                    ((ServiceInvocationException) t).getErrorCode());
        }));
    }

    /**
     * Verifies that a request for credentials using a non-existing client context
     * fails with a 404.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testGetCredentialsFailsForNonExistingClientContext(final TestContext ctx) {

        final CredentialsObject credentials = getRandomHashedPasswordCredentials();

        final JsonObject clientContext = new JsonObject()
                .put("client-id", "gateway-one");

        helper.registry
        .addCredentials(Constants.DEFAULT_TENANT, JsonObject.mapFrom(credentials))
        .compose(ok -> credentialsClient.get(CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD, credentials.getAuthId(), clientContext))
        .setHandler(ctx.asyncAssertFailure( t -> {
            ctx.assertEquals(
                    HttpURLConnection.HTTP_NOT_FOUND,
                    ((ServiceInvocationException) t).getErrorCode());
        }));
    }

    private CredentialsObject getRandomHashedPasswordCredentials() {

        final String deviceId = helper.getRandomDeviceId(Constants.DEFAULT_TENANT);
        final String authId = UUID.randomUUID().toString();
        final CredentialsObject credential = new CredentialsObject(deviceId, authId, CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD);
        setSecrets(credential);
        return credential;
    }

    private void setSecrets(final CredentialsObject credential) {

             new JsonArray()
             .add(CredentialsObject.hashedPasswordSecretForClearTextPassword(
                     "ClearTextPWD",
                     Instant.parse("2017-05-01T14:00:00Z"),
                     Instant.parse("2037-06-01T14:00:00Z")))
             .add(CredentialsObject.hashedPasswordSecretForClearTextPassword(
                     "hono-password",
                     null,
                     null))
             .forEach(secret -> credential.addSecret((JsonObject) secret));
    }

    private void assertStandardProperties(
            final TestContext ctx,
            final CredentialsObject credentials,
            final String expectedDeviceId,
            final boolean expectedStatus,
            final String expectedAuthId,
            final String expectedType,
            final int expectedNumberOfSecrets) {

        ctx.assertTrue(expectedStatus == credentials.isEnabled());
        ctx.assertEquals(expectedDeviceId, credentials.getDeviceId());
        ctx.assertEquals(expectedAuthId, credentials.getAuthId());
        ctx.assertEquals(expectedType, credentials.getType());
        ctx.assertNotNull(credentials.getSecrets());
        ctx.assertTrue(credentials.getSecrets().size() == expectedNumberOfSecrets);
    }
}
