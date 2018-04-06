/**
 * Copyright (c) 2017, 2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 */
package org.eclipse.hono.tests.registry;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;

import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

import org.eclipse.hono.client.CredentialsClient;
import org.eclipse.hono.client.HonoClient;
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.CredentialsConstants;
import org.eclipse.hono.util.CredentialsObject;
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
import io.vertx.proton.ProtonClientOptions;

/**
 * Tests verifying the behavior of the Device Registry component's Credentials AMQP endpoint.
 */
@RunWith(VertxUnitRunner.class)
public class CredentialsAmqpIT {

    private static final String CREDENTIALS_AUTHID1 = "sensor1";
    private static final String CREDENTIALS_AUTHID2 = "little-sensor2";
    private static final String CREDENTIALS_USER_PASSWORD = "hono-secret";
    private static final byte[] CREDENTIALS_PASSWORD_SALT = "hono".getBytes(StandardCharsets.UTF_8);
    private static final String DEFAULT_DEVICE_ID = "4711";

    private static final Vertx vertx = Vertx.vertx();

    private static HonoClient client;
    private static CredentialsClient credentialsClient;

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

        client = DeviceRegistryAmqpTestSupport.prepareDeviceRegistryClient(vertx);

        client.connect(new ProtonClientOptions())
            .compose(c -> c.getOrCreateCredentialsClient(Constants.DEFAULT_TENANT))
            .setHandler(ctx.asyncAssertSuccess(r -> {
                credentialsClient = r;
            }));
    }

    /**
     * Shuts down the device registry and closes the client.
     * 
     * @param ctx The vert.x test context.
     */
    @AfterClass
    public static void shutdown(final TestContext ctx) {

        DeviceRegistryAmqpTestSupport.shutdownDeviceRegistryClient(ctx, vertx, client);

    }

    /**
     * Verify that a not existing authId is responded with HTTP_NOT_FOUND.
     * 
     * @param ctx The vert.x test context.
     */
    @Test
    public void testGetCredentialsNotExistingAuthId(final TestContext ctx) {

        credentialsClient
            .get(CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD, "notExisting")
            .setHandler(ctx.asyncAssertFailure(t -> {
                ctx.assertEquals(
                        HttpURLConnection.HTTP_NOT_FOUND,
                        ((ServiceInvocationException) t).getErrorCode());
            }));
    }

    /**
     * Verifies that the service returns credentials for a given type and authentication ID.
     * 
     * @param ctx The vert.x test context.
     */
    @Test
    public void testGetCredentialsReturnsCredentialsTypeAndAuthId(final TestContext ctx) {

        credentialsClient
            .get(CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD, CREDENTIALS_AUTHID1)
            .setHandler(ctx.asyncAssertSuccess(result -> {
                ctx.assertEquals(CREDENTIALS_AUTHID1, result.getAuthId());
                ctx.assertEquals(CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD, result.getType());
            }));
    }

    /**
     * Verifies that the service returns credentials for a given type, authentication ID and matching client context.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testGetCredentialsExistingClientContext(final TestContext ctx) {

        JsonObject clientContext = new JsonObject()
                .put("client-id", "gateway-one");

        credentialsClient
                .get(CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD, "gw", clientContext)
                .setHandler(ctx.asyncAssertSuccess(result -> {
                    ctx.assertEquals("gw", result.getAuthId());
                    ctx.assertEquals(CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD, result.getType());
                }));
    }

    /**
     * Verify that a non-matching client context is responded with HTTP_NOT_FOUND.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testGetCredentialsNotMatchingClientContext(final TestContext ctx) {

        JsonObject clientContext = new JsonObject()
                .put("client-id", "gateway-two");

        credentialsClient
                .get(CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD, "gw", clientContext)
                .setHandler(ctx.asyncAssertFailure(t -> {
                    ctx.assertEquals(
                            HttpURLConnection.HTTP_NOT_FOUND,
                            ((ServiceInvocationException) t).getErrorCode());
                }));
    }

    /**
     * Verify that a not existing client context is responded with HTTP_NOT_FOUND.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testGetCredentialsNotExistingClientContext(final TestContext ctx) {

        JsonObject clientContext = new JsonObject()
                .put("client-id", "gateway-one");

        credentialsClient
                .get(CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD, CREDENTIALS_AUTHID1, clientContext)
                .setHandler(ctx.asyncAssertFailure(t -> {
                    ctx.assertEquals(
                            HttpURLConnection.HTTP_NOT_FOUND,
                            ((ServiceInvocationException) t).getErrorCode());
                }));
    }

    /**
     * Verify that setting authId and type to existing credentials is responded with HTTP_OK.
     * Check that the payload contains the default deviceId and is enabled.
     * 
     * @param ctx The vert.x test context.
     */
    @Test
    public void testGetCredentialsReturnsCredentialsDefaultDeviceIdAndIsEnabled(final TestContext ctx) {

        credentialsClient
            .get(CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD, CREDENTIALS_AUTHID1)
            .setHandler(ctx.asyncAssertSuccess(result -> {
                assertTrue(checkPayloadGetCredentialsContainsDefaultDeviceIdAndReturnEnabled(result));
            }));
    }

    /**
     * Verify that setting authId and type to existing credentials is responded with HTTP_OK.
     * Check that the payload contains multiple secrets (more than one).
     * 
     * @param ctx The vert.x test context.
     */
    @Test
    public void testGetCredentialsReturnsMultipleSecrets(final TestContext ctx) {

        credentialsClient
            .get(CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD, CREDENTIALS_AUTHID1)
            .setHandler(ctx.asyncAssertSuccess(result -> {
                checkPayloadGetCredentialsReturnsMultipleSecrets(result);
            }));
    }

    /**
     * Verify that setting authId and type to existing credentials is responded with HTTP_OK.
     * Check that the payload contains the expected hash-function, salt and encrypted password.
     * 
     * @param ctx The vert.x test context.
     */
    @Test
    public void testGetCredentialsFirstSecretCorrectPassword(final TestContext ctx) {

        credentialsClient
            .get(CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD, CREDENTIALS_AUTHID1)
            .setHandler(ctx.asyncAssertSuccess(result -> {
                checkPayloadGetCredentialsReturnsFirstSecretWithCorrectPassword(result);
            }));
    }

    /**
     * Verify that setting authId and type to existing credentials is responded with HTTP_OK.
     * Check that the payload contains NOT_BEFORE and NOT_AFTER entries which denote a currently active time interval.
     * 
     * @param ctx The vert.x test context.
     */
    @Test
    public void testGetCredentialsFirstSecretCurrentlyActiveTimeInterval(final TestContext ctx) {

        credentialsClient
            .get(CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD, CREDENTIALS_AUTHID1)
            .setHandler(ctx.asyncAssertSuccess(result -> {
                checkPayloadGetCredentialsReturnsFirstSecretWithCurrentlyActiveTimeInterval(result);
            }));
    }

    /**
     * Verify that setting authId and type PreSharedKey to existing credentials is responded with HTTP_OK.
     * Check that the payload contains NOT_BEFORE and NOT_AFTER entries which denote a currently active time interval.
     * 
     * @param ctx The vert.x test context.
     */
    @Test
    public void testGetCredentialsPresharedKeyIsNotEnabled(final TestContext ctx) {

        credentialsClient
            .get(CredentialsConstants.SECRETS_TYPE_PRESHARED_KEY, CREDENTIALS_AUTHID2)
            .setHandler(ctx.asyncAssertSuccess(result -> {
                assertFalse(checkPayloadGetCredentialsContainsDefaultDeviceIdAndReturnEnabled(result));
            }));
    }

    private JsonObject pickFirstSecretFromPayload(final CredentialsObject payload) {
        // secrets: first entry is expected to be valid,
        // second entry may have time stamps not yet active (not checked),
        // more entries may be avail
        final JsonArray secrets = payload.getSecrets();
        assertNotNull(secrets);
        assertTrue(secrets.size() > 0);

        final JsonObject firstSecret = secrets.getJsonObject(0);
        assertNotNull(firstSecret);
        return firstSecret;
    }

    private void checkPayloadGetCredentialsReturnsFirstSecretWithCorrectPassword(final CredentialsObject payload) {

        assertNotNull(payload);
        final JsonObject firstSecret = pickFirstSecretFromPayload(payload);
        assertNotNull(firstSecret);

        final String hashFunction = firstSecret.getString(CredentialsConstants.FIELD_SECRETS_HASH_FUNCTION);
        assertThat(hashFunction, is("sha-512"));

        final String salt = firstSecret.getString(CredentialsConstants.FIELD_SECRETS_SALT);
        assertNotNull(salt);
        final byte[] decodedSalt = Base64.getDecoder().decode(salt);
        assertThat(decodedSalt, is(CREDENTIALS_PASSWORD_SALT)); // see file, this should be the salt

        final String pwdHash = firstSecret.getString(CredentialsConstants.FIELD_SECRETS_PWD_HASH);
        assertNotNull(pwdHash);
        final byte[] decodedPassword = Base64.getDecoder().decode(pwdHash);

        try {
            final byte[] hashedPassword = CredentialsObject.getHashedPassword("sha-512", CREDENTIALS_PASSWORD_SALT, CREDENTIALS_USER_PASSWORD);
            // check if the password is the hashed version of "hono-secret"
            assertThat(hashedPassword, is(decodedPassword));
        } catch (NoSuchAlgorithmException e) {
            fail(e.getMessage());
        }
    }

    private void checkPayloadGetCredentialsReturnsFirstSecretWithCurrentlyActiveTimeInterval(final CredentialsObject payload) {

        assertNotNull(payload);
        final JsonObject firstSecret = pickFirstSecretFromPayload(payload);
        assertNotNull(firstSecret);

        LocalDateTime now = LocalDateTime.now();

        assertTrue(firstSecret.containsKey(CredentialsConstants.FIELD_SECRETS_NOT_BEFORE));
        String notBefore = firstSecret.getString(CredentialsConstants.FIELD_SECRETS_NOT_BEFORE);
        LocalDateTime notBeforeLocalDate = LocalDateTime.parse(notBefore, DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        assertTrue(now.compareTo(notBeforeLocalDate) >= 0);

        assertTrue(firstSecret.containsKey(CredentialsConstants.FIELD_SECRETS_NOT_AFTER));
        String notAfter = firstSecret.getString(CredentialsConstants.FIELD_SECRETS_NOT_AFTER);
        LocalDateTime notAfterLocalDate = LocalDateTime.parse(notAfter, DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        assertTrue(now.compareTo(notAfterLocalDate) <= 0);
    }

    private void checkPayloadGetCredentialsReturnsMultipleSecrets(final CredentialsObject payload) {
        assertNotNull(payload);

        // secrets: first entry is expected to be valid,
        // second entry may have time stamps not yet active (not checked),
        // more entries may be avail
        final JsonArray secrets = payload.getSecrets();
        assertNotNull(secrets);
        assertTrue(secrets.size() > 1); // at least 2 entries to test multiple entries
    }

    private boolean checkPayloadGetCredentialsContainsDefaultDeviceIdAndReturnEnabled(final CredentialsObject payload) {
        assertNotNull(payload);

        assertNotNull(payload.getDeviceId());
        assertEquals(payload.getDeviceId(), DEFAULT_DEVICE_ID);

        return (payload.isEnabled());
    }
}
