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
package org.eclipse.hono.deviceregistry;

import static org.junit.Assert.*;

import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.eclipse.hono.auth.Activity;
import org.eclipse.hono.auth.Authorities;
import org.eclipse.hono.auth.AuthoritiesImpl;
import org.eclipse.hono.auth.HonoUser;
import org.eclipse.hono.auth.HonoUserAdapter;
import org.eclipse.hono.client.CredentialsClient;
import org.eclipse.hono.client.HonoClient;
import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.client.impl.HonoClientImpl;
import org.eclipse.hono.config.ClientConfigProperties;
import org.eclipse.hono.config.ServiceConfigProperties;
import org.eclipse.hono.service.auth.AuthenticationService;
import org.eclipse.hono.service.auth.HonoSaslAuthenticatorFactory;
import org.eclipse.hono.service.credentials.CredentialsAmqpEndpoint;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.CredentialsConstants;
import org.eclipse.hono.util.CredentialsObject;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.proton.ProtonClientOptions;

/**
 * Tests validating {@link FileBasedCredentialsService} using a stand alone server.
 */
@RunWith(VertxUnitRunner.class)
public class StandaloneCredentialsApiTest {

    private static final String USER    = "hono-client";
    private static final String PWD     = "secret";
    private static final String CREDENTIALS_AUTHID1 = "sensor1";
    private static final String CREDENTIALS_AUTHID2 = "little-sensor2";
    private static final String CREDENTIALS_TYPE_HASHED_PASSWORD = "hashed-password";
    private static final String CREDENTIALS_TYPE_PRESHARED_KEY = "psk";
    private static final String CREDENTIALS_USER_PASSWORD = "hono-secret";
    private static final String CREDENTIALS_PASSWORD_SALT = "hono";
    private static final String DEFAULT_DEVICE_ID = "4711";
    private static final Vertx  vertx = Vertx.vertx();

    private static DeviceRegistryAmqpServer server;
    private static HonoClient               client;
    private static CredentialsClient        credentialsClient;

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

        final ServiceConfigProperties props = new ServiceConfigProperties();
        props.setInsecurePort(0);

        final FileBasedCredentialsConfigProperties credentialsProperties = new FileBasedCredentialsConfigProperties();
        credentialsProperties.setCredentialsFilename("credentials.json");

        final FileBasedCredentialsService deviceRegistryImpl = new FileBasedCredentialsService();
        deviceRegistryImpl.setConfig(credentialsProperties);

        server = new DeviceRegistryAmqpServer();
        server.setSaslAuthenticatorFactory(new HonoSaslAuthenticatorFactory(createAuthenticationService(createUser())));
        server.setConfig(props);
        server.addEndpoint(new CredentialsAmqpEndpoint(vertx));

        Future<String> credentialsTracker = Future.future();
        vertx.deployVerticle(deviceRegistryImpl,credentialsTracker.completer());
        credentialsTracker.compose(r -> {
            Future<String> serviceTracker = Future.future();
            vertx.deployVerticle(server, serviceTracker.completer());
            return serviceTracker;
        }).compose(s -> {
            final ClientConfigProperties clientProps = new ClientConfigProperties();
            clientProps.setName("test");
            clientProps.setHost(server.getInsecurePortBindAddress());
            clientProps.setPort(server.getInsecurePort());
            clientProps.setUsername(USER);
            clientProps.setPassword(PWD);
            client = new HonoClientImpl(vertx, clientProps);

            return client.connect(new ProtonClientOptions());
        }).compose(c -> c.getOrCreateCredentialsClient(Constants.DEFAULT_TENANT)).setHandler(ctx.asyncAssertSuccess(r -> {
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

        final Future<Void> clientTracker = Future.future();
        if (client != null) {
            client.shutdown(clientTracker.completer());
        } else {
            clientTracker.complete();
        }
        clientTracker.otherwiseEmpty().compose(s -> {
            final Future<Void> vertxTracker = Future.future();
            vertx.close(vertxTracker.completer());
            return vertxTracker;
        }).setHandler(ctx.asyncAssertSuccess());
    }

    /**
     * Creates a Hono user containing all authorities required for running this test class.
     * 
     * @return The user.
     */
    private static HonoUser createUser() {

        final AuthoritiesImpl authorities = new AuthoritiesImpl()
                .addResource(CredentialsConstants.CREDENTIALS_ENDPOINT, "*", new Activity[]{ Activity.READ, Activity.WRITE })
                .addOperation(CredentialsConstants.CREDENTIALS_ENDPOINT, "*", "*");
        return new HonoUserAdapter() {
            @Override
            public Authorities getAuthorities() {
                return authorities;
            }
        };
    }

    private static AuthenticationService createAuthenticationService(final HonoUser returnedUser) {

        return new AuthenticationService() {

            @Override
            public void authenticate(final JsonObject authRequest, final Handler<AsyncResult<HonoUser>> authenticationResultHandler) {
                authenticationResultHandler.handle(Future.succeededFuture(returnedUser));
            }
        };
    }

    /**
     * Verify that a not existing authId is responded with HTTP_NOT_FOUND.
     * 
     * @param ctx The vert.x test context.
     */
    @Test
    public void testGetCredentialsNotExistingAuthId(final TestContext ctx) {

        credentialsClient.get(CREDENTIALS_TYPE_HASHED_PASSWORD, "notExisting").setHandler(ctx.asyncAssertFailure(t -> {
            ctx.assertEquals(HttpURLConnection.HTTP_NOT_FOUND, ((ServiceInvocationException) t).getErrorCode());
        }));
    }

    /**
     * Verify that setting authId and type to existing credentials is responded with HTTP_OK.
     * Check that the payload contains exactly the given type and authId afterwards.
     * 
     * @param ctx The vert.x test context.
     */
    @Test
    public void testGetCredentialsReturnsCredentialsTypeAndAuthId(final TestContext ctx) {

        credentialsClient.get(CREDENTIALS_TYPE_HASHED_PASSWORD, CREDENTIALS_AUTHID1).setHandler(ctx.asyncAssertSuccess(result -> {
            checkPayloadGetCredentialsContainsAuthIdAndType(result);
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

        credentialsClient.get(CREDENTIALS_TYPE_HASHED_PASSWORD, CREDENTIALS_AUTHID1).setHandler(ctx.asyncAssertSuccess(result -> {
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

        credentialsClient.get(CREDENTIALS_TYPE_HASHED_PASSWORD, CREDENTIALS_AUTHID1).setHandler(ctx.asyncAssertSuccess(result -> {
            checkPayloadGetCredentialsReturnsMultipleSecrets(result);
        }));
    }

    /**
     * Verify that setting authId and type to existing credentials is responded with HTTP_OK.
     * Check that the payload contains the exepected hash-function,salt and encrypted password.
     * 
     * @param ctx The vert.x test context.
     */
    @Test
    public void testGetCredentialsFirstSecretCorrectPassword(final TestContext ctx) {

        credentialsClient.get(CREDENTIALS_TYPE_HASHED_PASSWORD, CREDENTIALS_AUTHID1).setHandler(ctx.asyncAssertSuccess(result -> {
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

        credentialsClient.get(CREDENTIALS_TYPE_HASHED_PASSWORD, CREDENTIALS_AUTHID1).setHandler(ctx.asyncAssertSuccess(result -> {
            checkPayloadGetCredentialsReturnsFirstSecretWithCurrentlyActiveTimeInterval(result);
        }));
    }

    /**
     * Verify that setting authId and type preshared-key to existing credentials is responded with HTTP_OK.
     * Check that the payload contains NOT_BEFORE and NOT_AFTER entries which denote a currently active time interval.
     * 
     * @param ctx The vert.x test context.
     */
    @Test
    public void testGetCredentialsPresharedKeyIsNotEnabled(final TestContext ctx) {

        credentialsClient.get(CREDENTIALS_TYPE_PRESHARED_KEY, CREDENTIALS_AUTHID2).setHandler(ctx.asyncAssertSuccess(result -> {
            assertFalse(checkPayloadGetCredentialsContainsDefaultDeviceIdAndReturnEnabled(result));
        }));
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

    private void checkPayloadGetCredentialsReturnsFirstSecretWithCorrectPassword(final CredentialsObject payload) {

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

    private byte[] hashPassword(final String hashFunction,final String hashSalt, final String passwordToHash) {
            try {
                MessageDigest messageDigest = MessageDigest.getInstance(hashFunction);
                messageDigest.update(hashSalt.getBytes(StandardCharsets.UTF_8));
                byte[] theHashedPassword = messageDigest.digest(passwordToHash.getBytes());
                return theHashedPassword;
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException("VM does not support hash function: " + hashFunction);
            }
    }
}
