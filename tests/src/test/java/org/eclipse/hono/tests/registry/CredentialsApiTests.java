/**
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
 */


package org.eclipse.hono.tests.registry;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.HttpURLConnection;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.eclipse.hono.client.CredentialsClient;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.CredentialsConstants;
import org.eclipse.hono.util.CredentialsObject;
import org.junit.jupiter.api.Test;

import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxTestContext;

/**
 * Common test cases for the Credentials API.
 *
 */
abstract class CredentialsApiTests extends DeviceRegistryTestBase {

    /**
     * Gets a client for interacting with the Credentials service.
     * 
     * @param tenant The tenant to scope the client to.
     * @return The client.
     */
    protected abstract Future<CredentialsClient> getClient(String tenant);

    /**
     * Verify that a request to retrieve credentials for a non-existing authentication
     * ID fails with a 404.
     *
     * @param ctx The vert.x test context.
     */
    @Timeout(value = 5, timeUnit = TimeUnit.SECONDS)
    @Test
    public void testGetCredentialsFailsForNonExistingAuthId(final VertxTestContext ctx) {

        getClient(Constants.DEFAULT_TENANT)
        .compose(client -> client.get(CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD, "nonExisting"))
        .setHandler(ctx.failing(t -> {
            ctx.verify(() -> assertErrorCode(t, HttpURLConnection.HTTP_NOT_FOUND));
            ctx.completeNow();
        }));
    }

    /**
     * Verifies that the service returns credentials for a given type and authentication ID
     * including the default value for the enabled property..
     *
     * @param ctx The vert.x test context.
     */
    @Timeout(value = 5, timeUnit = TimeUnit.SECONDS)
    @Test
    public void testGetCredentialsByTypeAndAuthId(final VertxTestContext ctx) {

        final CredentialsObject credentials = getRandomHashedPasswordCredentials();

        getHelper().registry
        .addCredentials(Constants.DEFAULT_TENANT, JsonObject.mapFrom(credentials))
        .compose(ok -> getClient(Constants.DEFAULT_TENANT))
        .compose(client -> client.get(CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD, credentials.getAuthId()))
        .setHandler(ctx.succeeding(result -> {
            ctx.verify(() -> assertStandardProperties(
                    result,
                    credentials.getDeviceId(),
                    true,
                    credentials.getAuthId(),
                    CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD,
                    2));
            ctx.completeNow();
        }));
    }

    /**
     * Verifies that the service returns credentials for a given type and authentication ID
     * including its explicitly set enabled property.
     *
     * @param ctx The vert.x test context.
     */
    @Timeout(value = 5, timeUnit = TimeUnit.SECONDS)
    @Test
    public void testGetCredentialsIncludesEnabledProperty(final VertxTestContext ctx) {

        final CredentialsObject credentials = getRandomHashedPasswordCredentials();
        credentials.setEnabled(false);

        getHelper().registry
        .addCredentials(Constants.DEFAULT_TENANT, JsonObject.mapFrom(credentials))
        .compose(ok -> getClient(Constants.DEFAULT_TENANT))
        .compose(client -> client.get(CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD, credentials.getAuthId()))
        .setHandler(ctx.succeeding(result -> {
            ctx.verify(() -> assertStandardProperties(
                    result,
                    credentials.getDeviceId(),
                    credentials.isEnabled(),
                    credentials.getAuthId(),
                    CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD,
                    2));
            ctx.completeNow();
        }));
    }

    /**
     * Verifies that the service returns credentials for a given type, authentication ID and matching client context.
     *
     * @param ctx The vert.x test context.
     */
    @Timeout(value = 5, timeUnit = TimeUnit.SECONDS)
    @Test
    public void testGetCredentialsByClientContext(final VertxTestContext ctx) {

        final CredentialsObject credentials = getRandomHashedPasswordCredentials();
        credentials.setProperty("client-id", "gateway-one");

        final JsonObject clientContext = new JsonObject()
                .put("client-id", "gateway-one");

        getHelper().registry
        .addCredentials(Constants.DEFAULT_TENANT, JsonObject.mapFrom(credentials))
        .compose(ok -> getClient(Constants.DEFAULT_TENANT))
        .compose(client -> client.get(CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD, credentials.getAuthId(), clientContext))
        .setHandler(ctx.succeeding(result -> {
            ctx.verify(() -> {
                assertStandardProperties(
                        result,
                        credentials.getDeviceId(),
                        true,
                        credentials.getAuthId(),
                        CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD,
                        2);
                assertThat((String) result.getProperty("client-id")).isEqualTo("gateway-one");
            });
            ctx.completeNow();
        }));
    }

    /**
     * Verifies that a request for credentials using a non-matching client context
     * fails with a 404.
     *
     * @param ctx The vert.x test context.
     */
    @Timeout(value = 5, timeUnit = TimeUnit.SECONDS)
    @Test
    public void testGetCredentialsFailsForNonMatchingClientContext(final VertxTestContext ctx) {

        final CredentialsObject credentials = getRandomHashedPasswordCredentials();
        credentials.setProperty("client-id", "gateway-one");

        final JsonObject clientContext = new JsonObject()
                .put("client-id", "non-matching");

        getHelper().registry
        .addCredentials(Constants.DEFAULT_TENANT, JsonObject.mapFrom(credentials))
        .compose(ok ->  getClient(Constants.DEFAULT_TENANT))
        .compose(client -> client.get(CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD, credentials.getAuthId(), clientContext))
        .setHandler(ctx.failing(t -> {
            ctx.verify(() -> assertErrorCode(t, HttpURLConnection.HTTP_NOT_FOUND));
            ctx.completeNow();
        }));
    }

    /**
     * Verifies that a request for credentials using a non-existing client context
     * fails with a 404.
     *
     * @param ctx The vert.x test context.
     */
    @Timeout(value = 5, timeUnit = TimeUnit.SECONDS)
    @Test
    public void testGetCredentialsFailsForNonExistingClientContext(final VertxTestContext ctx) {

        final CredentialsObject credentials = getRandomHashedPasswordCredentials();

        final JsonObject clientContext = new JsonObject()
                .put("client-id", "gateway-one");

        getHelper().registry
        .addCredentials(Constants.DEFAULT_TENANT, JsonObject.mapFrom(credentials))
        .compose(ok -> getClient(Constants.DEFAULT_TENANT))
        .compose(client -> client.get(CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD, credentials.getAuthId(), clientContext))
        .setHandler(ctx.failing( t -> {
            ctx.verify(() -> assertErrorCode(t, HttpURLConnection.HTTP_NOT_FOUND));
            ctx.completeNow();
        }));
    }

    private CredentialsObject getRandomHashedPasswordCredentials() {

        final String deviceId = getHelper().getRandomDeviceId(Constants.DEFAULT_TENANT);
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
            final CredentialsObject credentials,
            final String expectedDeviceId,
            final boolean expectedStatus,
            final String expectedAuthId,
            final String expectedType,
            final int expectedNumberOfSecrets) {

        assertThat(credentials.isEnabled()).isEqualTo(expectedStatus);
        assertThat(credentials.getDeviceId()).isEqualTo(expectedDeviceId);
        assertThat(credentials.getAuthId()).isEqualTo(expectedAuthId);
        assertThat(credentials.getType()).isEqualTo(expectedType);
        assertThat(credentials.getSecrets()).hasSize(expectedNumberOfSecrets);
    }
}
