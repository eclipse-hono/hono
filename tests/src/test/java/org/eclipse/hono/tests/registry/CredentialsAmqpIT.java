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
package org.eclipse.hono.tests.registry;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.eclipse.hono.client.CredentialsClient;
import org.eclipse.hono.client.HonoClient;
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.tests.IntegrationTestSupport;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.CredentialsConstants;
import org.eclipse.hono.util.CredentialsObject;
import org.junit.After;
import org.junit.BeforeClass;
import org.junit.AfterClass;
import org.junit.Test;
import org.junit.Rule;
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

    private static final Vertx vertx = Vertx.vertx();

    private static HonoClient client;
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

        client = DeviceRegistryAmqpTestSupport.prepareDeviceRegistryClient(vertx,
                IntegrationTestSupport.HONO_USER, IntegrationTestSupport.HONO_PWD);

        client.connect(new ProtonClientOptions())
            .compose(c -> c.getOrCreateCredentialsClient(Constants.DEFAULT_TENANT))
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

        final CredentialsObject payload = getRandomHashedPasswordCredential();

        helper.registry.addCredentials(Constants.DEFAULT_TENANT, JsonObject.mapFrom(payload))
                .compose(ok -> credentialsClient.get(CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD, payload.getAuthId()))
                .setHandler(ctx.asyncAssertSuccess(result -> {
                    ctx.assertEquals(payload.getAuthId(), result.getAuthId());
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

        final CredentialsObject payload = getRandomHashedPasswordCredential();

        final JsonObject clientContext = new JsonObject()
                .put("client-id", "gateway-one");

        payload.setProperty("client-id", "gateway-one");

        helper.registry.addCredentials(Constants.DEFAULT_TENANT, JsonObject.mapFrom(payload))
                .compose(ok -> credentialsClient.get(CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD, payload.getAuthId(), clientContext))
                .setHandler(ctx.asyncAssertSuccess(result -> {
                    ctx.assertEquals(payload.getAuthId(), result.getAuthId());
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

        final CredentialsObject payload = getRandomHashedPasswordCredential();

        final JsonObject clientContext = new JsonObject()
                .put("client-id", "gateway-two");

        payload.setProperty("client-id", "gateway-one");

        helper.registry.addCredentials(Constants.DEFAULT_TENANT, JsonObject.mapFrom(payload))
                .compose(ok ->  credentialsClient.get(CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD, payload.getAuthId(), clientContext))
                .setHandler(ctx.asyncAssertFailure( t -> {
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

        final CredentialsObject payload = getRandomHashedPasswordCredential();

        final JsonObject clientContext = new JsonObject()
                .put("client-id", "gateway-one");

        helper.registry.addCredentials(Constants.DEFAULT_TENANT, JsonObject.mapFrom(payload))
                .compose(ok -> credentialsClient.get(CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD, payload.getAuthId(), clientContext))
                .setHandler(ctx.asyncAssertFailure( t -> {
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

        final CredentialsObject payload = getRandomHashedPasswordCredential();

        helper.registry.addCredentials(Constants.DEFAULT_TENANT, JsonObject.mapFrom(payload))
                .compose(ok -> credentialsClient.get(CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD, payload.getAuthId()))
                .setHandler(ctx.asyncAssertSuccess(result -> {
                    assertTrue(checkPayloadGetCredentialsContainsDeviceIdAndReturnEnabled(result, payload.getDeviceId()));
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

        final CredentialsObject payload = getRandomHashedPasswordCredential();

        helper.registry.addCredentials(Constants.DEFAULT_TENANT, JsonObject.mapFrom(payload))
                .compose(ok -> credentialsClient.get(CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD, payload.getAuthId()))
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

        final CredentialsObject payload = getRandomHashedPasswordCredential();

        helper.registry.addCredentials(Constants.DEFAULT_TENANT, JsonObject.mapFrom(payload))
                .compose(ok -> credentialsClient.get(CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD, payload.getAuthId()))
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

        final CredentialsObject payload = getRandomHashedPasswordCredential();

        helper.registry.addCredentials(Constants.DEFAULT_TENANT, JsonObject.mapFrom(payload))
                .compose(ok -> credentialsClient.get(CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD, payload.getAuthId()))
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

        final String deviceId = helper.getRandomDeviceId(Constants.DEFAULT_TENANT);
        final CredentialsObject payload = CredentialsObject.fromPresharedKey(
                deviceId,
                CREDENTIALS_AUTHID2,
                "c2VjcmV0S2V5Mg==".getBytes(),
                null, null);
        payload.setEnabled(false);


        helper.registry.addCredentials(Constants.DEFAULT_TENANT, JsonObject.mapFrom(payload))
                .compose(r -> credentialsClient.get(CredentialsConstants.SECRETS_TYPE_PRESHARED_KEY, CREDENTIALS_AUTHID2))
                .setHandler(ctx.asyncAssertSuccess(result -> {
                    assertFalse(checkPayloadGetCredentialsContainsDeviceIdAndReturnEnabled(result, deviceId));
                }));
    }

    private CredentialsObject getRandomHashedPasswordCredential(){
        final String deviceId = helper.getRandomDeviceId(Constants.DEFAULT_TENANT);
        final String authId = UUID.randomUUID().toString();
        final CredentialsObject credential = new CredentialsObject(deviceId, authId, CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD);
        setSecrets(credential);
        return credential;
    }

    private void setSecrets(final CredentialsObject credential){

             new JsonArray()
                //.add( CredentialsObject.hashedPasswordSecretForClearTextPassword("hono-secret",null, null))
                .add( CredentialsObject.hashedPasswordSecretForPasswordHash(
                        "C9/T62m1tT4ZxxqyIiyN9fvoEqmL0qnM4/+M+GHHDzr0QzzkAUdGYyJBfxRSe4upDzb6TSC4k5cpZG17p4QCvA==",
                        CredentialsConstants.HASH_FUNCTION_SHA512,
                        Instant.parse("2017-05-01T14:00:00Z"),
                        Instant.parse("2037-06-01T14:00:00Z"),
                        CREDENTIALS_PASSWORD_SALT))
                 .add(CredentialsObject.hashedPasswordSecretForClearTextPassword(
                         "hono-password",
                         null,
                         null))
                .forEach( secret -> credential.addSecret((JsonObject) secret));
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

        final String hashFunction = CredentialsConstants.getHashFunction(firstSecret);
        assertThat(hashFunction, is(CredentialsConstants.HASH_FUNCTION_SHA512));

        final String salt = CredentialsConstants.getPasswordSalt(firstSecret);
        assertNotNull(salt);
        final byte[] decodedSalt = Base64.getDecoder().decode(salt);
        assertThat(decodedSalt, is(CREDENTIALS_PASSWORD_SALT)); // see file, this should be the salt

        final String pwdHashOnRecord = CredentialsConstants.getPasswordHash(firstSecret);
        assertNotNull(pwdHashOnRecord);

        final String pwdHash = IntegrationTestSupport.getBase64EncodedDigestPasswordHash(
                CredentialsConstants.HASH_FUNCTION_SHA512,
                CREDENTIALS_PASSWORD_SALT,
                CREDENTIALS_USER_PASSWORD);
        // check if the password is the hashed version of "hono-secret"
        assertThat(pwdHashOnRecord, is(pwdHash));
    }

    private void checkPayloadGetCredentialsReturnsFirstSecretWithCurrentlyActiveTimeInterval(final CredentialsObject payload) {

        assertNotNull(payload);
        final JsonObject firstSecret = pickFirstSecretFromPayload(payload);
        assertNotNull(firstSecret);

        final LocalDateTime now = LocalDateTime.now();

        assertTrue(firstSecret.containsKey(CredentialsConstants.FIELD_SECRETS_NOT_BEFORE));
        final String notBefore = firstSecret.getString(CredentialsConstants.FIELD_SECRETS_NOT_BEFORE);
        final LocalDateTime notBeforeLocalDate = LocalDateTime.parse(notBefore, DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        assertTrue(now.compareTo(notBeforeLocalDate) >= 0);

        assertTrue(firstSecret.containsKey(CredentialsConstants.FIELD_SECRETS_NOT_AFTER));
        final String notAfter = firstSecret.getString(CredentialsConstants.FIELD_SECRETS_NOT_AFTER);
        final LocalDateTime notAfterLocalDate = LocalDateTime.parse(notAfter, DateTimeFormatter.ISO_OFFSET_DATE_TIME);
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

    private boolean checkPayloadGetCredentialsContainsDeviceIdAndReturnEnabled(final CredentialsObject payload, final String deviceId) {
        assertNotNull(payload);

        assertNotNull(payload.getDeviceId());
        assertEquals(payload.getDeviceId(), deviceId);

        return payload.isEnabled();
    }

}
