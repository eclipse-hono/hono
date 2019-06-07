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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.net.HttpURLConnection;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.eclipse.hono.client.CredentialsClient;
import org.eclipse.hono.service.credentials.AbstractCredentialsServiceTest;
import org.eclipse.hono.service.management.credentials.CommonCredential;
import org.eclipse.hono.service.management.credentials.PasswordCredential;
import org.eclipse.hono.tests.IntegrationTestSupport;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.CredentialsConstants;
import org.eclipse.hono.util.CredentialsObject;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import io.vertx.core.Future;
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
     * Verify that a request to retrieve credentials for a non-existing authentication ID fails with a 404.
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
     * Verifies that the service returns credentials for a given type and authentication ID including the default value
     * for the enabled property..
     *
     * @param ctx The vert.x test context.
     */
    @Timeout(value = 5, timeUnit = TimeUnit.SECONDS)
    @Test
    public void testGetCredentialsByTypeAndAuthId(final VertxTestContext ctx) {

        final String deviceId = getHelper().getRandomDeviceId(Constants.DEFAULT_TENANT);
        final String authId = UUID.randomUUID().toString();
        final Collection<CommonCredential> credentials = getRandomHashedPasswordCredentials(authId);

        getHelper().registry
                .registerDevice(Constants.DEFAULT_TENANT, deviceId)
                .compose(ok -> {
                    return getHelper().registry
                            .addCredentials(Constants.DEFAULT_TENANT, deviceId, credentials)
                            .compose(ok2 -> getClient(Constants.DEFAULT_TENANT))
                            .compose(client -> client.get(CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD, authId));
                })
                .setHandler(ctx.succeeding(result -> {
                    ctx.verify(() -> assertStandardProperties(
                            result,
                            deviceId,
                            authId,
                            CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD,
                            2));
                    ctx.completeNow();
                }));
    }

    /**
     * Verifies that the service fails when the credentials set is disabled.
     *
     * @param ctx The vert.x test context.
     */
    @Timeout(value = 5, timeUnit = TimeUnit.SECONDS)
    @Test
    public void testGetCredentialsFailsForDisabledCredentials(final VertxTestContext ctx) {

        final String deviceId = getHelper().getRandomDeviceId(Constants.DEFAULT_TENANT);
        final String authId = UUID.randomUUID().toString();

        final CommonCredential credential = getRandomHashedPasswordCredential(authId);
        credential.setEnabled(false);

        getHelper().registry
                .registerDevice(Constants.DEFAULT_TENANT, deviceId)
                .compose(ok -> {
                    return getHelper().registry
                            .addCredentials(Constants.DEFAULT_TENANT, deviceId, Collections.singleton(credential))
                            .compose(ok2 -> getClient(Constants.DEFAULT_TENANT))
                            .compose(client -> client.get(CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD, authId));
                })
                .setHandler(ctx.failing(t -> {
                    ctx.verify(() -> assertErrorCode(t, HttpURLConnection.HTTP_NOT_FOUND));
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
    @Disabled("credentials ext concept")
    public void testGetCredentialsByClientContext(final VertxTestContext ctx) {

        final String deviceId = getHelper().getRandomDeviceId(Constants.DEFAULT_TENANT);
        final String authId = UUID.randomUUID().toString();
        final Collection<CommonCredential> credentials = getRandomHashedPasswordCredentials(authId);

        // FIXME: credentials.setProperty("client-id", "gateway-one");

        final JsonObject clientContext = new JsonObject()
                .put("client-id", "gateway-one");

        getHelper().registry
                .addCredentials(Constants.DEFAULT_TENANT, deviceId, credentials)
                .compose(ok -> getClient(Constants.DEFAULT_TENANT))
                .compose(client -> client.get(CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD, authId, clientContext))
                .setHandler(ctx.succeeding(result -> {
                    ctx.verify(() -> {
                        assertStandardProperties(
                                result,
                                deviceId,
                                authId,
                                CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD,
                                2);
                        assertThat(result.getProperty("client-id", String.class)).isEqualTo("gateway-one");
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
    @Disabled("credentials ext concept")
    public void testGetCredentialsFailsForNonMatchingClientContext(final VertxTestContext ctx) {

        final String deviceId = getHelper().getRandomDeviceId(Constants.DEFAULT_TENANT);
        final String authId = UUID.randomUUID().toString();
        final Collection<CommonCredential> credentials = getRandomHashedPasswordCredentials(authId);
        // FIXME: credentials.setProperty("client-id", "gateway-one");

        final JsonObject clientContext = new JsonObject()
                .put("client-id", "non-matching");

        getHelper().registry
                .addCredentials(Constants.DEFAULT_TENANT, deviceId, credentials)
                .compose(ok -> getClient(Constants.DEFAULT_TENANT))
                .compose(client -> client.get(CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD, authId, clientContext))
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

        final String deviceId = getHelper().getRandomDeviceId(Constants.DEFAULT_TENANT);
        final String authId = UUID.randomUUID().toString();
        final Collection<CommonCredential> credentials = getRandomHashedPasswordCredentials(authId);

        final JsonObject clientContext = new JsonObject()
                .put("client-id", "gateway-one");

        getHelper().registry
                .addCredentials(Constants.DEFAULT_TENANT, deviceId, credentials)
                .compose(ok -> getClient(Constants.DEFAULT_TENANT))
                .compose(client -> client.get(CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD, authId, clientContext))
                .setHandler(ctx.failing(t -> {
                    ctx.verify(() -> assertErrorCode(t, HttpURLConnection.HTTP_NOT_FOUND));
                    ctx.completeNow();
        }));
    }

    private Collection<CommonCredential> getRandomHashedPasswordCredentials(final String authId) {
        return Collections.singleton(getRandomHashedPasswordCredential(authId));
    }

    private CommonCredential getRandomHashedPasswordCredential(final String authId) {

        final var secret1 = AbstractCredentialsServiceTest.createPasswordSecret("ClearTextPWD",
                OptionalInt.of(IntegrationTestSupport.MAX_BCRYPT_ITERATIONS));
        secret1.setNotBefore(Instant.parse("2017-05-01T14:00:00Z"));
        secret1.setNotAfter(Instant.parse("2037-06-01T14:00:00Z"));

        final var secret2 = AbstractCredentialsServiceTest.createPasswordSecret("hono-password",
                OptionalInt.of(IntegrationTestSupport.MAX_BCRYPT_ITERATIONS));

        final var credential = new PasswordCredential();
        credential.setAuthId(authId);
        credential.setSecrets(Arrays.asList(secret1, secret2));

        return credential;
    }

    private static void assertStandardProperties(
            final CredentialsObject credentials,
            final String expectedDeviceId,
            final String expectedAuthId,
            final String expectedType,
            final int expectedNumberOfSecrets) {

        assertNotNull(credentials);
        // only enabled credentials must be returned
        assertTrue(credentials.isEnabled());
        assertThat(credentials.getDeviceId()).isEqualTo(expectedDeviceId);
        assertThat(credentials.getAuthId()).isEqualTo(expectedAuthId);
        assertThat(credentials.getType()).isEqualTo(expectedType);
        assertNotNull(credentials.getSecrets());
        assertThat(credentials.getSecrets()).hasSize(expectedNumberOfSecrets);

    }

}
