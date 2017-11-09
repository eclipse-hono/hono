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
package org.eclipse.hono.deviceregistry;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.proton.ProtonClientOptions;
import org.eclipse.hono.auth.Activity;
import org.eclipse.hono.auth.AuthoritiesImpl;
import org.eclipse.hono.auth.HonoUser;
import org.eclipse.hono.client.CredentialsClient;
import org.eclipse.hono.client.HonoClient;
import org.eclipse.hono.client.impl.HonoClientImpl;
import org.eclipse.hono.config.ServiceConfigProperties;
import org.eclipse.hono.connection.ConnectionFactoryImpl.ConnectionFactoryBuilder;
import org.eclipse.hono.service.auth.AuthenticationService;
import org.eclipse.hono.service.auth.HonoSaslAuthenticatorFactory;

import org.eclipse.hono.service.credentials.CredentialsAmqpEndpoint;
import org.eclipse.hono.util.CredentialsConstants;
import org.eclipse.hono.util.CredentialsObject;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static java.net.HttpURLConnection.*;
import static org.eclipse.hono.util.Constants.DEFAULT_TENANT;
import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests validating {@link FileBasedCredentialsService} using a stand alone server.
 */
@RunWith(VertxUnitRunner.class)
public class StandaloneCredentialsApiTest {

    private static final int                   TIMEOUT = 5000; // milliseconds
    private static final String                USER    = "hono-client";
    private static final String                PWD     = "secret";
    private static final String                CREDENTIALS_AUTHID1 = "sensor1";
    private static final String                CREDENTIALS_AUTHID2 = "little-sensor2";
    private static final String                CREDENTIALS_TYPE_HASHED_PASSWORD = "hashed-password";
    private static final String                CREDENTIALS_TYPE_PRESHARED_KEY = "psk";
    private static final String                CREDENTIALS_USER_PASSWORD = "hono-secret";
    private static final String                CREDENTIALS_PASSWORD_SALT = "hono";
    private static final String                DEFAULT_DEVICE_ID = "4711";

    private static Vertx                       vertx = Vertx.vertx();
    private static DeviceRegistryAmqpServer server;

    private static HonoClient                  client;
    private static CredentialsClient           credentialsClient;

    @BeforeClass
    public static void prepareDeviceRegistry(final TestContext ctx) throws Exception {

        ServiceConfigProperties props = new ServiceConfigProperties();
        props.setInsecurePortEnabled(true);
        props.setInsecurePort(0);

        FileBasedCredentialsConfigProperties credentialsProperties = new FileBasedCredentialsConfigProperties();
        credentialsProperties.setCredentialsFilename("credentials.json");

        server = new DeviceRegistryAmqpServer();
        server.setSaslAuthenticatorFactory(new HonoSaslAuthenticatorFactory(vertx,createAuthenticationService(createUser())));
        server.setConfig(props);
        server.addEndpoint(new CredentialsAmqpEndpoint(vertx));
        FileBasedCredentialsService deviceRegistryImpl = new FileBasedCredentialsService();
        deviceRegistryImpl.setConfig(credentialsProperties);

        Future<CredentialsClient> setupTracker = Future.future();
        setupTracker.setHandler(ctx.asyncAssertSuccess(r -> {
            credentialsClient = r;
        }));

        Future<String> credentialsTracker = Future.future();
        vertx.deployVerticle(deviceRegistryImpl,credentialsTracker.completer());
        credentialsTracker.compose(r -> {
            Future<String> serviceTracker = Future.future();
            vertx.deployVerticle(server, serviceTracker.completer());
            return serviceTracker;
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
            c.getOrCreateCredentialsClient(DEFAULT_TENANT, setupTracker.completer());
        }, setupTracker);
    }

    /**
     * Creates a Hono user containing all authorities required for running this test class.
     * 
     * @return The user.
     */
    private static HonoUser createUser() {

        AuthoritiesImpl authorities = new AuthoritiesImpl()
                .addResource(CredentialsConstants.CREDENTIALS_ENDPOINT, "*", new Activity[]{ Activity.READ, Activity.WRITE })
                .addOperation(CredentialsConstants.CREDENTIALS_ENDPOINT, "*", "*");
        HonoUser user = mock(HonoUser.class);
        when(user.getAuthorities()).thenReturn(authorities);
        return user;
    }

    public static AuthenticationService createAuthenticationService(final HonoUser returnedUser) {
        return new AuthenticationService() {

            @Override
            public void authenticate(final JsonObject authRequest, final Handler<AsyncResult<HonoUser>> authenticationResultHandler) {
                authenticationResultHandler.handle(Future.succeededFuture(returnedUser));
            }
        };
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

        credentialsClient.get(CREDENTIALS_TYPE_HASHED_PASSWORD, "notExisting", s -> {
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

        credentialsClient.get(CREDENTIALS_TYPE_HASHED_PASSWORD, null, s -> {
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

        credentialsClient.get(CREDENTIALS_TYPE_HASHED_PASSWORD, CREDENTIALS_AUTHID1, s -> {
            ctx.assertTrue(s.succeeded());
            ctx.assertEquals(s.result().getStatus(), HTTP_OK);
            CredentialsObject payload = s.result().getPayload();

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

        credentialsClient.get(CREDENTIALS_TYPE_HASHED_PASSWORD, CREDENTIALS_AUTHID1, s -> {
            ctx.assertTrue(s.succeeded());
            ctx.assertEquals(s.result().getStatus(), HTTP_OK);
            CredentialsObject payload = s.result().getPayload();

            assertTrue(checkPayloadGetCredentialsContainsDefaultDeviceIdAndReturnEnabled(payload));
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

        credentialsClient.get(CREDENTIALS_TYPE_HASHED_PASSWORD, CREDENTIALS_AUTHID1, s -> {
            ctx.assertTrue(s.succeeded());
            ctx.assertEquals(s.result().getStatus(), HTTP_OK);
            CredentialsObject payload = s.result().getPayload();

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

        final AtomicReference<CredentialsObject> payloadRef = new AtomicReference<>();

        credentialsClient.get(CREDENTIALS_TYPE_HASHED_PASSWORD, CREDENTIALS_AUTHID1, s -> {
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

        credentialsClient.get(CREDENTIALS_TYPE_HASHED_PASSWORD, CREDENTIALS_AUTHID1, s -> {
            ctx.assertTrue(s.succeeded());
            ctx.assertEquals(s.result().getStatus(), HTTP_OK);
            CredentialsObject payload = s.result().getPayload();

            checkPayloadGetCredentialsReturnsFirstSecretWithCurrentlyActiveTimeInterval(payload);
            ok.complete();
        });
    }

    /**
     * Verify that setting authId and type preshared-key to existing credentials is responded with HTTP_OK.
     * Check that the payload contains NOT_BEFORE and NOT_AFTER entries which denote a currently active time interval.
     */
    @Test(timeout = TIMEOUT)
    public void testGetCredentialsPresharedKeyIsNotEnabled(final TestContext ctx) {
        final Async ok = ctx.async();

        credentialsClient.get(CREDENTIALS_TYPE_PRESHARED_KEY, CREDENTIALS_AUTHID2, s -> {
            ctx.assertTrue(s.succeeded());
            ctx.assertEquals(s.result().getStatus(), HTTP_OK);
            CredentialsObject payload = s.result().getPayload();

            assertFalse(checkPayloadGetCredentialsContainsDefaultDeviceIdAndReturnEnabled(payload));
            ok.complete();
        });
    }

    private Map<String, String> pickFirstSecretFromPayload(final CredentialsObject payload) {
        // secrets: first entry is expected to be valid, second entry may have timestamps not yet active (not checked), more entries may be avail
        List<Map<String, String>> secrets = payload.getSecrets();
        assertNotNull(secrets);
        assertTrue(secrets.size() > 0);

        Map<String, String> firstSecret = secrets.get(0);
        assertNotNull(firstSecret);
        return firstSecret;
    }

    private void checkPayloadGetCredentialsReturnsFirstSecretWithCorrectPassword(final CredentialsObject payload) throws UnsupportedEncodingException, NoSuchAlgorithmException {
        assertNotNull(payload);
        Map<String, String> firstSecret = pickFirstSecretFromPayload(payload);
        assertNotNull(firstSecret);

        String hashFunction = firstSecret.get(CredentialsConstants.FIELD_SECRETS_HASH_FUNCTION);
        assertNotNull(hashFunction);
        assertEquals(hashFunction,firstSecret.get(CredentialsConstants.FIELD_SECRETS_HASH_FUNCTION));

        String salt = firstSecret.get(CredentialsConstants.FIELD_SECRETS_SALT);
        assertNotNull(salt);
        byte[] decodedSalt = Base64.getDecoder().decode(salt);
        assertEquals(new String(decodedSalt), CREDENTIALS_PASSWORD_SALT);  // see file, this should be the salt

        String pwdHash = firstSecret.get(CredentialsConstants.FIELD_SECRETS_PWD_HASH);
        assertNotNull(pwdHash);
        byte[] password = Base64.getDecoder().decode(pwdHash);
        assertNotNull(password);

        byte[] hashedPassword = hashPassword(hashFunction,new String(decodedSalt), CREDENTIALS_USER_PASSWORD);
        // check if the password is the hashed version of "hono-secret"
        assertTrue(Arrays.equals(password,hashedPassword));
    }

    private void checkPayloadGetCredentialsReturnsFirstSecretWithCurrentlyActiveTimeInterval(final CredentialsObject payload) {
        assertNotNull(payload);
        Map<String, String> firstSecret = pickFirstSecretFromPayload(payload);
        assertNotNull(firstSecret);

        LocalDateTime now = LocalDateTime.now();

        assertTrue(firstSecret.containsKey(CredentialsConstants.FIELD_SECRETS_NOT_BEFORE));
        String notBefore = firstSecret.get(CredentialsConstants.FIELD_SECRETS_NOT_BEFORE);
        LocalDateTime notBeforeLocalDate = LocalDateTime.parse(notBefore, DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        assertTrue(now.compareTo(notBeforeLocalDate) >= 0);

        assertTrue(firstSecret.containsKey(CredentialsConstants.FIELD_SECRETS_NOT_AFTER));
        String notAfter = firstSecret.get(CredentialsConstants.FIELD_SECRETS_NOT_AFTER);
        LocalDateTime notAfterLocalDate = LocalDateTime.parse(notAfter, DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        assertTrue(now.compareTo(notAfterLocalDate) <= 0);
    }

    private void checkPayloadGetCredentialsReturnsMultipleSecrets(final CredentialsObject payload) {
        assertNotNull(payload);

        // secrets: first entry is expected to be valid, second entry may have timestamps not yet active (not checked), more entries may be avail
        List<Map<String, String>> secrets = payload.getSecrets();
        assertNotNull(secrets);
        assertTrue(secrets.size() > 1); // at least 2 entries to test multiple entries
    }

    private void checkPayloadGetCredentialsContainsAuthIdAndType(final CredentialsObject payload) {
        assertNotNull(payload);

        assertNotNull(payload.getAuthId());
        assertEquals(payload.getAuthId(), CREDENTIALS_AUTHID1);

        assertNotNull(payload.getType());
        assertEquals(payload.getType(), CREDENTIALS_TYPE_HASHED_PASSWORD);
    }

    private boolean checkPayloadGetCredentialsContainsDefaultDeviceIdAndReturnEnabled(final CredentialsObject payload) {
        assertNotNull(payload);

        assertNotNull(payload.getDeviceId());
        assertEquals(payload.getDeviceId(), DEFAULT_DEVICE_ID);

        return (payload.getEnabled());
    }

    private byte[] hashPassword(final String hashFunction,final String hashSalt, final String passwordToHash) throws NoSuchAlgorithmException, UnsupportedEncodingException {
            MessageDigest messageDigest = MessageDigest.getInstance(hashFunction);
            messageDigest.update(hashSalt.getBytes("UTF-8"));
            byte[] theHashedPassword = messageDigest.digest(passwordToHash.getBytes());
            return theHashedPassword;
    }
}
