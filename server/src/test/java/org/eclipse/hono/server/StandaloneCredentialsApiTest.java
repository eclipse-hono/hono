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
package org.eclipse.hono.server;

import static java.net.HttpURLConnection.*;
import static org.eclipse.hono.util.Constants.DEFAULT_TENANT;
import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.hono.TestSupport;
import org.eclipse.hono.auth.Activity;
import org.eclipse.hono.auth.AuthoritiesImpl;
import org.eclipse.hono.auth.HonoUser;
import org.eclipse.hono.client.CredentialsClient;
import org.eclipse.hono.client.HonoClient;
import org.eclipse.hono.client.impl.HonoClientImpl;
import org.eclipse.hono.connection.ConnectionFactoryImpl.ConnectionFactoryBuilder;
import org.eclipse.hono.service.auth.HonoSaslAuthenticatorFactory;
import org.eclipse.hono.service.credentials.CredentialsEndpoint;
import org.eclipse.hono.service.credentials.impl.FileBasedCredentialsService;
import org.eclipse.hono.util.CredentialsConstants;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.proton.ProtonClientOptions;

/**
 * Tests validating Hono's Credentials API using a stand alone server.
 */
@RunWith(VertxUnitRunner.class)
public class StandaloneCredentialsApiTest {

    private static final int                   TIMEOUT = 5000; // milliseconds
    private static final String                USER    = "hono-client";
    private static final String                PWD     = "secret";
    private static final  String                CREDENTIALS_USER = "billie";
    private static final  String                CREDENTIALS_TYPE = "hashed-password";
    private static final  String                CREDENTIALS_USER_PASSWORD = "hono-secret";
    private static final  String                CREDENTIALS_PASSWORD_SALT = "hono";
    private static final  String                DEFAULT_DEVICE_ID = "4711";

    private static Vertx                       vertx = Vertx.vertx();
    private static HonoServer                  server;
    private static FileBasedCredentialsService credentialsAdapter;
    private static HonoClient                  client;
    private static CredentialsClient           credentialsClient;

    @BeforeClass
    public static void prepareHonoServer(final TestContext ctx) throws Exception {

        HonoServerConfigProperties configProperties = new HonoServerConfigProperties();
        configProperties.setInsecurePortEnabled(true);
        configProperties.setInsecurePort(0);

        server = new HonoServer();
        server.setSaslAuthenticatorFactory(new HonoSaslAuthenticatorFactory(vertx, TestSupport.createAuthenticationService(createUser())));
        server.setConfig(configProperties);
        server.addEndpoint(new CredentialsEndpoint(vertx));
        credentialsAdapter = new FileBasedCredentialsService();

        Future<CredentialsClient> setupTracker = Future.future();
        setupTracker.setHandler(ctx.asyncAssertSuccess(r -> {
            credentialsClient = r;
        }));

        Future<String> credentialsTracker = Future.future();
        vertx.deployVerticle(credentialsAdapter, credentialsTracker.completer());

        credentialsTracker.compose(r -> {
            Future<String> serverTracker = Future.future();
            vertx.deployVerticle(server, serverTracker.completer());
            return serverTracker;
        }).compose(s -> {
            client = new HonoClientImpl(vertx, ConnectionFactoryBuilder.newBuilder()
                    .vertx(vertx)
                    .name("test")
                    .host(server.getInsecurePortBindAddress())
                    .port(server.getInsecurePort())
                    .user(USER)
                    .password(PWD)
                    .build());

            Future<HonoClient> clientTracker = Future.future();
            client.connect(new ProtonClientOptions(), clientTracker.completer());
            return clientTracker;
        }).compose(c -> {
            c.createCredentialsClient(DEFAULT_TENANT, setupTracker.completer());
        }, setupTracker);
    }

    /**
     * Creates a Hono user containing all authorities required for running this test class.
     * 
     * @return The user.
     */
    private static HonoUser createUser() {

        AuthoritiesImpl authorities = new AuthoritiesImpl()
                .addResource(CredentialsConstants.CREDENTIALS_ENDPOINT, "*", Activity.READ, Activity.WRITE)
                .addOperation(CredentialsConstants.CREDENTIALS_ENDPOINT, "*", "*");
        HonoUser user = mock(HonoUser.class);
        when(user.isExpired()).thenReturn(Boolean.FALSE);
        when(user.getAuthorities()).thenReturn(authorities);
        when(user.getName()).thenReturn("test-client");
        return user;
    }

    @AfterClass
    public static void shutdown(final TestContext ctx) {

        Future<Void> done = Future.future();
        done.setHandler(ctx.asyncAssertSuccess());

        if (client != null) {
            Future<Void> closeTracker = Future.future();
            if (credentialsClient != null) {
                credentialsClient.close(closeTracker.completer());
            } else {
                closeTracker.complete();
            }
            closeTracker.compose(c -> {
                client.shutdown(done.completer());
            }, done);
        }
    }

    /**
     * Verify that a not existing authId is responded with HTTP_NOT_FOUND.
     */
    @Test(timeout = TIMEOUT)
    public void testGetCredentialsNotExistingAuthId(final TestContext ctx) {
        final Async ok = ctx.async();

        credentialsClient.get(CREDENTIALS_TYPE, "notExisting", s -> {
            ctx.assertTrue(s.succeeded());
            ctx.assertEquals(s.result().getStatus(), HTTP_NOT_FOUND);
            ok.complete();
        });
    }

    /**
     * Verify that a null type is responded with HTTP_BAD_REQUEST.
     */
    @Test(timeout = TIMEOUT)
    public void testGetCredentialsNullType(final TestContext ctx) {
        final Async ok = ctx.async();

        credentialsClient.get(null, "notExisting", s -> {
            ctx.assertTrue(s.succeeded());
            ctx.assertEquals(s.result().getStatus(), HTTP_BAD_REQUEST);
            ok.complete();
        });
    }

    /**
     * Verify that a null authId is responded with HTTP_BAD_REQUEST.
     */
    @Test(timeout = TIMEOUT)
    public void testGetCredentialsNullAuthId(final TestContext ctx) {
        final Async ok = ctx.async();

        credentialsClient.get(CREDENTIALS_TYPE, null, s -> {
            ctx.assertTrue(s.succeeded());
            ctx.assertEquals(s.result().getStatus(), HTTP_BAD_REQUEST);
            ok.complete();
        });
    }

    /**
     * Verify that setting authId and type to existing credentials is responded with HTTP_OK.
     * Check that the payload contains exactly the given type and authId afterwards.
     */
    @Test(timeout = TIMEOUT)
    public void testGetCredentialsReturnsCredentialsTypeAndAuthId(final TestContext ctx) {
        final Async ok = ctx.async();

        credentialsClient.get(CREDENTIALS_TYPE, CREDENTIALS_USER, s -> {
            ctx.assertTrue(s.succeeded());
            ctx.assertEquals(s.result().getStatus(), HTTP_OK);
            JsonObject payload = s.result().getPayload();

            checkPayloadGetCredentialsContainsAuthIdAndType(payload);
            ok.complete();
        });
    }

    /**
     * Verify that setting authId and type to existing credentials is responded with HTTP_OK.
     * Check that the payload contains the default deviceId and is enabled.
     */
    @Test(timeout = TIMEOUT)
    public void testGetCredentialsReturnsCredentialsDefaultDeviceIdAndIsEnabled(final TestContext ctx) {
        final Async ok = ctx.async();

        credentialsClient.get(CREDENTIALS_TYPE, CREDENTIALS_USER, s -> {
            ctx.assertTrue(s.succeeded());
            ctx.assertEquals(s.result().getStatus(), HTTP_OK);
            JsonObject payload = s.result().getPayload();

            checkPayloadGetCredentialsContainsDefaultDeviceIdAndIsEnabled(payload);
            ok.complete();
        });
    }

    /**
     * Verify that setting authId and type to existing credentials is responded with HTTP_OK.
     * Check that the payload contains multiple secrets (more than one).
     */
    @Test(timeout = TIMEOUT)
    public void testGetCredentialsReturnsMultipleSecrets(final TestContext ctx) {
        final Async ok = ctx.async();

        credentialsClient.get(CREDENTIALS_TYPE, CREDENTIALS_USER, s -> {
            ctx.assertTrue(s.succeeded());
            ctx.assertEquals(s.result().getStatus(), HTTP_OK);
            JsonObject payload = s.result().getPayload();

            checkPayloadGetCredentialsReturnsMultipleSecrets(payload);
            ok.complete();
        });
    }

    /**
     * Verify that setting authId and type to existing credentials is responded with HTTP_OK.
     * Check that the payload contains the exepected hash-function,salt and encrypted password.
     */
    @Test(timeout = TIMEOUT)
    public void testGetCredentialsFirstSecretCorrectPassword(final TestContext ctx) throws UnsupportedEncodingException, NoSuchAlgorithmException {
        final Async ok = ctx.async();

        final AtomicReference<JsonObject> payloadRef = new AtomicReference<>();

        credentialsClient.get(CREDENTIALS_TYPE, CREDENTIALS_USER, s -> {
            ctx.assertTrue(s.succeeded());
            ctx.assertEquals(s.result().getStatus(), HTTP_OK);
            payloadRef.set(s.result().getPayload());
            ok.complete();
        });
        ok.await(200);
        checkPayloadGetCredentialsReturnsFirstSecretWithCorrectPassword(payloadRef.get());
    }

    /**
     * Verify that setting authId and type to existing credentials is responded with HTTP_OK.
     * Check that the payload contains NOT_BEFORE and NOT_AFTER entries which denote a currently active time interval.
     */
    @Test(timeout = TIMEOUT)
    public void testGetCredentialsFirstSecretCurrentlyActiveTimeInterval(final TestContext ctx) {
        final Async ok = ctx.async();

        credentialsClient.get(CREDENTIALS_TYPE, CREDENTIALS_USER, s -> {
            ctx.assertTrue(s.succeeded());
            ctx.assertEquals(s.result().getStatus(), HTTP_OK);
            JsonObject payload = s.result().getPayload();

            checkPayloadGetCredentialsReturnsFirstSecretWithCurrentlyActiveTimeInterval(payload);
            ok.complete();
        });
    }


    private JsonObject pickFirstSecretFromPayload(final JsonObject payload) {
        // secrets: first entry is expected to be valid, second entry may have timestamps not yet active (not checked), more entries may be avail
        assertTrue(payload.containsKey(CredentialsConstants.FIELD_SECRETS));
        JsonArray secrets = payload.getJsonArray(CredentialsConstants.FIELD_SECRETS);
        assertNotNull(secrets);
        assertTrue(secrets.size() > 0);

        JsonObject firstSecret = secrets.getJsonObject(0);
        assertNotNull(firstSecret);
        return firstSecret;
    }

    private void checkPayloadGetCredentialsReturnsFirstSecretWithCorrectPassword(final JsonObject payload) throws UnsupportedEncodingException, NoSuchAlgorithmException {
        assertNotNull(payload);
        JsonObject firstSecret = pickFirstSecretFromPayload(payload);
        assertNotNull(firstSecret);

        String hashFunction = firstSecret.getString(CredentialsConstants.FIELD_SECRETS_HASH_FUNCTION);
        assertNotNull(hashFunction);
        assertEquals(hashFunction,firstSecret.getString(CredentialsConstants.FIELD_SECRETS_HASH_FUNCTION));

        String salt = firstSecret.getString(CredentialsConstants.FIELD_SECRETS_SALT);
        assertNotNull(salt);
        byte[] decodedSalt = Base64.getDecoder().decode(salt);
        assertEquals(new String(decodedSalt), CREDENTIALS_PASSWORD_SALT);  // see file, this should be the salt

        String pwdHash = firstSecret.getString(CredentialsConstants.FIELD_SECRETS_PWD_HASH);
        assertNotNull(pwdHash);
        byte[] password = Base64.getDecoder().decode(pwdHash);
        assertNotNull(password);

        byte[] hashedPassword = hashPassword(hashFunction,new String(decodedSalt), CREDENTIALS_USER_PASSWORD);
        // check if the password is the hashed version of "hono-secret"
        assertTrue(Arrays.equals(password,hashedPassword));
    }

    private void checkPayloadGetCredentialsReturnsFirstSecretWithCurrentlyActiveTimeInterval(final JsonObject payload) {
        assertNotNull(payload);
        JsonObject firstSecret = pickFirstSecretFromPayload(payload);
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

    private void checkPayloadGetCredentialsReturnsMultipleSecrets(final JsonObject payload) {
        assertNotNull(payload);

        // secrets: first entry is expected to be valid, second entry may have timestamps not yet active (not checked), more entries may be avail
        assertTrue(payload.containsKey(CredentialsConstants.FIELD_SECRETS));
        JsonArray secrets = payload.getJsonArray(CredentialsConstants.FIELD_SECRETS);
        assertNotNull(secrets);
        assertTrue(secrets.size() > 1); // at least 2 entries to test multiple entries
    }

    private void checkPayloadGetCredentialsContainsAuthIdAndType(final JsonObject payload) {
        assertNotNull(payload);

        assertTrue(payload.containsKey(CredentialsConstants.FIELD_AUTH_ID));
        assertEquals(payload.getString(CredentialsConstants.FIELD_AUTH_ID), CREDENTIALS_USER);

        assertTrue(payload.containsKey(CredentialsConstants.FIELD_TYPE));
        assertEquals(payload.getString(CredentialsConstants.FIELD_TYPE),CREDENTIALS_TYPE);
    }

    private void checkPayloadGetCredentialsContainsDefaultDeviceIdAndIsEnabled(final JsonObject payload) {
        assertNotNull(payload);

        assertTrue(payload.containsKey(CredentialsConstants.FIELD_DEVICE_ID));
        assertEquals(payload.getString(CredentialsConstants.FIELD_DEVICE_ID), DEFAULT_DEVICE_ID);

        assertTrue(payload.getBoolean(CredentialsConstants.FIELD_ENABLED));
    }

    private byte[] hashPassword(final String hashFunction,final String hashSalt,final String passwordToHash) throws NoSuchAlgorithmException, UnsupportedEncodingException {
            MessageDigest messageDigest = MessageDigest.getInstance(hashFunction);
            messageDigest.update(hashSalt.getBytes("UTF-8"));
            byte[] theHashedPassword = messageDigest.digest(passwordToHash.getBytes());
            return theHashedPassword;
    }
}
