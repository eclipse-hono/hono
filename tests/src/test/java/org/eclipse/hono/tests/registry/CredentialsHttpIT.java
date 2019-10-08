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

import static org.hamcrest.Matchers.arrayWithSize;
import static org.junit.Assert.assertNotNull;

import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.eclipse.hono.service.management.credentials.CommonCredential;
import org.eclipse.hono.service.management.credentials.GenericCredential;
import org.eclipse.hono.service.management.credentials.GenericSecret;
import org.eclipse.hono.service.management.credentials.PasswordCredential;
import org.eclipse.hono.service.management.credentials.PasswordSecret;
import org.eclipse.hono.service.management.credentials.PskCredential;
import org.eclipse.hono.service.management.credentials.PskSecret;
import org.eclipse.hono.tests.CrudHttpClient;
import org.eclipse.hono.tests.DeviceRegistryHttpClient;
import org.eclipse.hono.tests.IntegrationTestSupport;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.CredentialsConstants;
import org.eclipse.hono.util.CredentialsObject;
import org.eclipse.hono.util.RegistryManagementConstants;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;

/**
 * Tests verifying the Device Registry component by making HTTP requests to its
 * Credentials HTTP endpoint and validating the corresponding responses.
 */
@RunWith(VertxUnitRunner.class)
public class CredentialsHttpIT {

    private static final String HTTP_HEADER_ETAG = HttpHeaders.ETAG.toString();

    private static final String TENANT = Constants.DEFAULT_TENANT;
    private static final String TEST_AUTH_ID = "sensor20";
    private static final Vertx vertx = Vertx.vertx();

    private static final String ORIG_BCRYPT_PWD;
    private static DeviceRegistryHttpClient registry;

    static {
        final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(IntegrationTestSupport.MAX_BCRYPT_ITERATIONS);
        ORIG_BCRYPT_PWD = encoder.encode("thePassword");
    }

    /**
     * Time out each test after 5 secs.
     */
    @Rule
    public final Timeout timeout = Timeout.seconds(5);

    private String deviceId;
    private String authId;
    private PasswordCredential hashedPasswordCredential;
    private PskCredential pskCredentials;

    /**
     * Creates the HTTP client for accessing the registry.
     * 
     * @param ctx The vert.x test context.
     */
    @BeforeClass
    public static void setUpClient(final TestContext ctx) {

        registry = new DeviceRegistryHttpClient(
                vertx,
                IntegrationTestSupport.HONO_DEVICEREGISTRY_HOST,
                IntegrationTestSupport.HONO_DEVICEREGISTRY_HTTP_PORT);

    }

    /**
     * Sets up the fixture.
     * 
     * @param ctx The test context.
     */
    @Before
    public void setUp(final TestContext ctx) {
        deviceId = UUID.randomUUID().toString();
        authId = getRandomAuthId(TEST_AUTH_ID);
        hashedPasswordCredential = IntegrationTestSupport.createPasswordCredential(authId, ORIG_BCRYPT_PWD);
        pskCredentials = newPskCredentials(authId);
        final Async creation = ctx.async();
        registry
                .registerDevice(Constants.DEFAULT_TENANT, deviceId)
                .otherwise(t -> {
                    ctx.fail(t);
                    return null;
                })
                .setHandler(attempt -> creation.complete());

        creation.await();
    }

    /**
     * Removes the device that have been added by the test.
     * 
     * @param ctx The vert.x test context.
     */
    @After
    public void removeCredentials(final TestContext ctx) {
        final Async deletion = ctx.async();
        registry
                .deregisterDevice(TENANT, deviceId)
                .setHandler(attempt -> deletion.complete());
        deletion.await();
    }

    /**
     * Shuts down the client.
     * 
     * @param context The vert.x test context.
     */
    @AfterClass
    public static void tearDown(final TestContext context) {
        vertx.close(context.asyncAssertSuccess());
    }

    /**
     * Verifies that the service accepts an add credentials request containing valid credentials.
     * 
     * @param context The vert.x test context.
     */
    @Test
    public void testAddCredentialsSucceeds(final TestContext context)  {

        registry
                .updateCredentials(TENANT, deviceId, Collections.singleton(hashedPasswordCredential),
                        HttpURLConnection.HTTP_NO_CONTENT)
                .setHandler(context.asyncAssertSuccess());
    }

    /**
     * Verifies that when a device is created, an associated entry is created in the credential Service.
     *
     * @param context The vert.x test context.
     */
    @Test
    public void testNewDeviceReturnsEmptyCredentials(final TestContext context) {

        registry
                .getCredentials(TENANT, deviceId)
                .setHandler(context.asyncAssertSuccess(ok2 -> {
                    context.verify(v -> {
                        final CommonCredential[] credentials = Json.decodeValue(ok2,
                                CommonCredential[].class);
                        Assert.assertThat(credentials, arrayWithSize(0));
                    });
                }));

    }

    /**
     * Verifies that the service accepts an add credentials request containing a clear text password.
     * 
     * @param context The vert.x test context.
     */
    @Test
    public void testAddCredentialsSucceedsForAdditionalProperties(final TestContext context) {

        final PasswordCredential credential = IntegrationTestSupport.createPasswordCredential(authId, "thePassword");
        credential.getExtensions().put("client-id", "MQTT-client-2384236854");

        registry.addCredentials(TENANT, deviceId, Collections.singleton(credential))
                .compose(createAttempt -> registry.getCredentials(TENANT, deviceId))
                .setHandler(context.asyncAssertSuccess(b -> {
                    context.assertEquals(1, b.toJsonArray().size());
                    final JsonObject credentialObject = b.toJsonArray().getJsonObject(0);
                    final var ext = credentialObject.getJsonObject(RegistryManagementConstants.FIELD_EXT);
                    context.assertNotNull(ext);
                    context.assertEquals("MQTT-client-2384236854", ext.getString("client-id"));

                    // the device-id must not be part of the "ext" section
                    context.assertNull(ext.getString("device-id"));
                }));
    }

    /**
     * Verifies that the service returns a 400 status code for an add credentials request with a Content-Type other than
     * application/json.
     * 
     * @param context The vert.x test context.
     */
    @Test
    public void testAddCredentialsFailsForWrongContentType(final TestContext context) {

        registry
                .updateCredentials(
                        TENANT,
                        deviceId,
                        Collections.singleton(hashedPasswordCredential),
                        "application/x-www-form-urlencoded",
                        HttpURLConnection.HTTP_BAD_REQUEST)
                .setHandler(context.asyncAssertSuccess());

    }

    /**
     * Verifies that the service rejects a request to update a credentials set if the resource Version value is
     * outdated.
     * 
     * @param context The vert.x test context.
     */
    @Test
    public void testUpdateCredentialsWithOutdatedResourceVersionFails(final TestContext context) {

        registry
                .updateCredentials(
                        TENANT, deviceId, Collections.singleton(hashedPasswordCredential),
                        HttpURLConnection.HTTP_NO_CONTENT)
                .compose(ar -> {
                    final var etag = ar.get(HTTP_HEADER_ETAG);
                    assertNotNull("missing etag header", etag);
                    // now try to update credentials with the same version
                    return registry.updateCredentialsWithVersion(TENANT, deviceId, Collections.singleton(hashedPasswordCredential),
                            etag+10, HttpURLConnection.HTTP_PRECON_FAILED);
                })
                .setHandler(context.asyncAssertSuccess());

    }

    /**
     * Verifies that the service returns a 400 status code for an add credentials request with hashed password
     * credentials that use a BCrypt hash with more than the configured max iterations.
     * 
     * @param context The vert.x test context.
     */
    @Test
    public void testAddCredentialsFailsForBCryptWithTooManyIterations(final TestContext context)  {

        // GIVEN a hashed password using bcrypt with more than the configured max iterations
        final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(IntegrationTestSupport.MAX_BCRYPT_ITERATIONS + 1);

        final PasswordCredential credential = new PasswordCredential();
        credential.setAuthId(authId);

        final PasswordSecret secret = new PasswordSecret();
        secret.setHashFunction(CredentialsConstants.HASH_FUNCTION_BCRYPT);
        secret.setPasswordHash(encoder.encode("thePassword"));
        credential.setSecrets(Collections.singletonList(secret));

        // WHEN adding the credentials
        registry
                .updateCredentials(
                        TENANT,
                        deviceId,
                        Collections.singleton(credential),
                        // THEN the request fails with 400
                        HttpURLConnection.HTTP_BAD_REQUEST)
                .setHandler(context.asyncAssertSuccess());
    }

    /**
     * Verifies that the service returns a 400 status code for an add credentials request with an empty body.
     * 
     * @param context The vert.x test context.
     */
    @Test
    public void testAddCredentialsFailsForEmptyBody(final TestContext context) {

        registry.updateCredentialsRaw(TENANT, deviceId, null, CrudHttpClient.CONTENT_TYPE_JSON,
                HttpURLConnection.HTTP_BAD_REQUEST)
                .setHandler(context.asyncAssertSuccess());

    }

    /**
     * Verify that a json payload to add credentials that does not contain a {@link CredentialsConstants#FIELD_TYPE}
     * is not accepted and responded with {@link HttpURLConnection#HTTP_BAD_REQUEST}
     * and a non empty error response message.
     * 
     * @param context The vert.x test context.
     */
    @Test
    public void testAddCredentialsFailsForMissingType(final TestContext context) {
        testAddCredentialsWithMissingPayloadParts(context, CredentialsConstants.FIELD_TYPE);
    }

    /**
     * Verify that a json payload to add credentials that does not contain a {@link CredentialsConstants#FIELD_AUTH_ID}
     * is not accepted and responded with {@link HttpURLConnection#HTTP_BAD_REQUEST}
     * and a non empty error response message.
     * 
     * @param context The vert.x test context.
     */
    @Test
    public void testAddCredentialsFailsForMissingAuthId(final TestContext context) {
        testAddCredentialsWithMissingPayloadParts(context, CredentialsConstants.FIELD_AUTH_ID);
    }

    private void testAddCredentialsWithMissingPayloadParts(final TestContext context, final String fieldMissing) {

        final JsonObject json = JsonObject.mapFrom(hashedPasswordCredential);
        json.remove(fieldMissing);
        final JsonArray payload = new JsonArray()
                .add(json);

        registry
                .updateCredentialsRaw(TENANT, deviceId, payload.toBuffer(),
                        CrudHttpClient.CONTENT_TYPE_JSON, HttpURLConnection.HTTP_BAD_REQUEST)
                .setHandler(context.asyncAssertSuccess());
    }

    /**
     * Verifies that the service accepts an update credentials request for existing credentials.
     * 
     * @param context The vert.x test context.
     */
    @Test
    public void testUpdateCredentialsSucceeds(final TestContext context) {

        final PasswordCredential altered = JsonObject
                .mapFrom(hashedPasswordCredential)
                .mapTo(PasswordCredential.class);
        altered.getSecrets().get(0).setComment("test");

        registry.updateCredentials(TENANT, deviceId, Collections.singleton(hashedPasswordCredential),
                HttpURLConnection.HTTP_NO_CONTENT)
                .compose(ar -> registry.updateCredentials(TENANT, deviceId, Collections.singleton(altered),
                        HttpURLConnection.HTTP_NO_CONTENT))
            .compose(ur -> registry.getCredentials(TENANT, deviceId))
            .setHandler(context.asyncAssertSuccess(gr -> {
                final JsonObject retrievedSecret = gr.toJsonArray().getJsonObject(0).getJsonArray("secrets").getJsonObject(0);
                context.assertEquals("test", retrievedSecret.getString("comment"));
            }));
    }

    /**
     * Verifies that the service accepts an update credentials request for existing credentials.
     * 
     * @param context The vert.x test context.
     */
    @Test
    @Ignore("Requires support for clear text passwords")
    public void testUpdateCredentialsSucceedsForClearTextPassword(final TestContext context) {

        final PasswordCredential secret = IntegrationTestSupport.createPasswordCredential(authId, "newPassword");

        registry.addCredentials(TENANT, deviceId, Collections.<CommonCredential> singleton(hashedPasswordCredential))
                .compose(ar -> registry.updateCredentials(TENANT, deviceId, secret))
                .compose(ur -> registry.getCredentials(TENANT, deviceId))
                .setHandler(context.asyncAssertSuccess(gr -> {
                    final CredentialsObject o = extractFirstCredential(gr.toJsonObject())
                            .mapTo(CredentialsObject.class);
                    context.assertEquals(authId, o.getAuthId());
                    context.assertFalse(o.getCandidateSecrets(s -> CredentialsConstants.getPasswordHash(s))
                            .stream().anyMatch(hash -> ORIG_BCRYPT_PWD.equals(hash)));
                }));
    }

    /**
     * Verifies that the service rejects an update request for non-existing credentials.
     * 
     * @param context The vert.x test context.
     */
    @Test
    public void testUpdateCredentialsFailsForNonExistingCredentials(final TestContext context) {
        registry
                .updateCredentialsWithVersion(
                        TENANT,
                        deviceId,
                        Collections.singleton(hashedPasswordCredential),
                        "3",
                        HttpURLConnection.HTTP_PRECON_FAILED)
                .setHandler(context.asyncAssertSuccess());
    }

    /**
     * Verify that a correctly added credentials record can be successfully looked up again by using the type and
     * authId.
     * 
     * @param context The vert.x test context.
     */
    @Test
    public void testGetAddedCredentials(final TestContext context) {

        registry.updateCredentials(TENANT, deviceId, Collections.singleton(hashedPasswordCredential),
                HttpURLConnection.HTTP_NO_CONTENT)
                .compose(ar -> registry.getCredentials(TENANT, deviceId))
                .setHandler(context.asyncAssertSuccess(b -> {
                    context.assertEquals(
                            new JsonArray()
                                    .add(JsonObject.mapFrom(hashedPasswordCredential)),
                            b.toJsonArray());
                }));

    }

    /**
     * Verifies that the service accepts an add credentials and assign it with an Etag value.
     *
     * @param context The vert.x test context.
     */
    @Test
    public void testAddedCredentialsContainsEtag(final TestContext context)  {

        registry
                .updateCredentials(TENANT, deviceId, Collections.singleton(hashedPasswordCredential),
                        HttpURLConnection.HTTP_NO_CONTENT)
                .setHandler(context.asyncAssertSuccess(res -> {
                    context.assertNotNull("etag header missing", res.get(HTTP_HEADER_ETAG));
                }));
    }

    /**
     * Verify that multiple (2) correctly added credentials records of the same authId can be successfully looked up by
     * single requests using their type and authId again.
     * 
     * @param context The vert.x test context.
     */
    @Test
    public void testGetAddedCredentialsMultipleTypesSingleRequests(final TestContext context) {

        final List<CommonCredential> credentialsListToAdd = new ArrayList<>();
        credentialsListToAdd.add(hashedPasswordCredential);
        credentialsListToAdd.add(pskCredentials);

        registry
                .addCredentials(TENANT, deviceId, credentialsListToAdd)

                .compose(ar -> registry.getCredentials(TENANT, deviceId))
                .setHandler(context.asyncAssertSuccess(b -> {
                    assertResponseBodyContainsAllCredentials(context, b.toJsonArray(), credentialsListToAdd);
        }));
    }

    /**
     * Verifies that the service returns all credentials registered for a given device regardless of authentication identifier.
     * <p>
     * The returned JsonArray must consist exactly the same credentials as originaly added.
     * 
     * @param context The vert.x test context.
     * @throws InterruptedException if registration of credentials is interrupted.
     */
    @Test
    public void testGetAllCredentialsForDeviceSucceeds(final TestContext context) throws InterruptedException {

        final List<CommonCredential> credentialsListToAdd = new ArrayList<>();
        credentialsListToAdd.add(newPskCredentials("auth"));
        credentialsListToAdd.add(newPskCredentials("other-auth"));

        registry.addCredentials(TENANT, deviceId, credentialsListToAdd)
            .compose(ar -> registry.getCredentials(TENANT, deviceId))
            .setHandler(context.asyncAssertSuccess(b -> {
                assertResponseBodyContainsAllCredentials(context, b.toJsonArray(), credentialsListToAdd);
            }));
    }

    /**
     * Verifies that the service returns all credentials registered for a given device regardless of type.
     * <p>
     * The returned JsonArray must contain all the credentials previously added to the registry.
     * 
     * @param context The vert.x test context.
     * @throws InterruptedException if registration of credentials is interrupted.
     */
    @Test
    public void testGetCredentialsForDeviceRegardlessOfType(final TestContext context) throws InterruptedException {

        final String pskAuthId = getRandomAuthId(TEST_AUTH_ID);
        final List<CommonCredential> credentialsToAdd = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            final GenericCredential credential = new GenericCredential();
            credential.setAuthId(pskAuthId);
            credential.setType("type" + i);

            final GenericSecret secret = new GenericSecret();
            secret.getAdditionalProperties().put("field" + i, "setec astronomy");

            credential.setSecrets(List.of(secret));
            credentialsToAdd.add(credential);
        }

        registry.addCredentials(TENANT, deviceId, credentialsToAdd)
            .compose(ar -> registry.getCredentials(TENANT, deviceId))
            .setHandler(context.asyncAssertSuccess(b -> {
                assertResponseBodyContainsAllCredentials(context, b.toJsonArray(), credentialsToAdd);
            }));
    }

    /**
     * Verify that a correctly added credentials record is not found when looking it up again with a wrong type.
     * 
     * @param context The vert.x test context.
     */
    @Test
    public void testGetAddedCredentialsButWithWrongType(final TestContext context)  {

        registry.updateCredentials(TENANT, deviceId, Collections.singleton(hashedPasswordCredential),
                HttpURLConnection.HTTP_NO_CONTENT)
            .compose(ar -> registry.getCredentials(TENANT, authId, "wrong-type", HttpURLConnection.HTTP_NOT_FOUND))
            .setHandler(context.asyncAssertSuccess());
    }

    /**
     * Verify that a correctly added credentials record is not found when looking it up again with a wrong authId.
     * 
     * @param context The vert.x test context.
     */
    @Test
    public void testGetAddedCredentialsButWithWrongAuthId(final TestContext context)  {

        registry.updateCredentials(TENANT, deviceId, Collections.singleton(hashedPasswordCredential),
                HttpURLConnection.HTTP_NO_CONTENT)
            .compose(ar -> registry.getCredentials(
                    TENANT,
                    "wrong-auth-id",
                    CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD,
                    HttpURLConnection.HTTP_NOT_FOUND))
            .setHandler(context.asyncAssertSuccess());
    }

    private static void assertResponseBodyContainsAllCredentials(final TestContext context, final JsonArray responseBody,
            final List<CommonCredential> expected) {

        final JsonArray expectedArray = new JsonArray();
        expected.stream().forEach(credential -> expectedArray.add(JsonObject.mapFrom(credential)));
        context.assertEquals(expectedArray, responseBody);
    }

    private static String getRandomAuthId(final String authIdPrefix) {
        return authIdPrefix + "." + UUID.randomUUID();
    }

    private static PskCredential newPskCredentials(final String authId) {

        final PskCredential credential = new PskCredential();
        credential.setAuthId(authId);

        final PskSecret secret = new PskSecret();
        secret.setKey("secret".getBytes(StandardCharsets.UTF_8));
        credential.setSecrets(Collections.singletonList(secret));

        return credential;

    }

    private JsonObject extractFirstCredential(final JsonObject json) {
        return json.getJsonArray(CredentialsConstants.CREDENTIALS_ENDPOINT).getJsonObject(0);
    }
}
