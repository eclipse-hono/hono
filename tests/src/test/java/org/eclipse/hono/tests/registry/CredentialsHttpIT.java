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

import static org.assertj.core.api.Assertions.assertThat;

import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

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
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

/**
 * Tests verifying the Device Registry component by making HTTP requests to its
 * Credentials HTTP endpoint and validating the corresponding responses.
 */
@ExtendWith(VertxExtension.class)
@Timeout(value = 5, timeUnit = TimeUnit.SECONDS)
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

    private String deviceId;
    private String authId;
    private PasswordCredential hashedPasswordCredential;
    private PskCredential pskCredentials;

    /**
     * Creates the HTTP client for accessing the registry.
     */
    @BeforeAll
    public static void setUpClient() {

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
    @BeforeEach
    public void setUp(final VertxTestContext ctx) {

        deviceId = UUID.randomUUID().toString();
        authId = getRandomAuthId(TEST_AUTH_ID);
        hashedPasswordCredential = IntegrationTestSupport.createPasswordCredential(authId, ORIG_BCRYPT_PWD);
        pskCredentials = newPskCredentials(authId);
        registry.registerDevice(Constants.DEFAULT_TENANT, deviceId)
        .onComplete(ctx.completing());
    }

    /**
     * Removes the device that have been added by the test.
     * 
     * @param ctx The vert.x test context.
     */
    @AfterEach
    public void removeCredentials(final VertxTestContext ctx) {

        registry.deregisterDevice(TENANT, deviceId).onComplete(ctx.completing());
    }

    /**
     * Shuts down the client.
     * 
     * @param context The vert.x test context.
     */
    @AfterAll
    public static void tearDown(final VertxTestContext context) {
        vertx.close(context.completing());
    }

    /**
     * Verifies that the service accepts an add credentials request containing valid credentials.
     * 
     * @param context The vert.x test context.
     */
    @Test
    public void testAddCredentialsSucceeds(final VertxTestContext context)  {

        registry
                .updateCredentials(TENANT, deviceId, Collections.singleton(hashedPasswordCredential),
                        HttpURLConnection.HTTP_NO_CONTENT)
                .onComplete(context.completing());
    }

    /**
     * Verifies that when a device is created, an associated entry is created in the credential Service.
     *
     * @param context The vert.x test context.
     */
    @Test
    public void testNewDeviceReturnsEmptyCredentials(final VertxTestContext context) {

        registry
                .getCredentials(TENANT, deviceId)
                .onComplete(context.succeeding(ok2 -> {
                    context.verify(() -> {
                        final CommonCredential[] credentials = Json.decodeValue(ok2,
                                CommonCredential[].class);
                        assertThat(credentials).hasSize(0);
                    });
                    context.completeNow();
                }));

    }

    /**
     * Verifies that the service accepts an add credentials request containing a clear text password.
     * 
     * @param context The vert.x test context.
     */
    @Test
    public void testAddCredentialsSucceedsForAdditionalProperties(final VertxTestContext context) {

        final PasswordCredential credential = IntegrationTestSupport.createPasswordCredential(authId, "thePassword");
        credential.getExtensions().put("client-id", "MQTT-client-2384236854");

        registry.addCredentials(TENANT, deviceId, Collections.singleton(credential))
                .compose(createAttempt -> registry.getCredentials(TENANT, deviceId))
                .onComplete(context.succeeding(b -> {
                    context.verify(() -> {
                        final JsonArray response = b.toJsonArray();
                        assertThat(response.size()).isEqualTo(1);
                        final JsonObject credentialObject = response.getJsonObject(0);
                        final var ext = credentialObject.getJsonObject(RegistryManagementConstants.FIELD_EXT);
                        assertThat(ext).isNotNull();
                        assertThat(ext.getString("client-id")).isEqualTo("MQTT-client-2384236854");

                        // the device-id must not be part of the "ext" section
                        assertThat(ext.getString("device-id")).isNull();;
                    });
                    context.completeNow();
                }));
    }

    /**
     * Verifies that the service returns a 400 status code for an add credentials request with a Content-Type other than
     * application/json.
     * 
     * @param context The vert.x test context.
     */
    @Test
    public void testAddCredentialsFailsForWrongContentType(final VertxTestContext context) {

        registry
                .updateCredentials(
                        TENANT,
                        deviceId,
                        Collections.singleton(hashedPasswordCredential),
                        "application/x-www-form-urlencoded",
                        HttpURLConnection.HTTP_BAD_REQUEST)
                .onComplete(context.completing());

    }

    /**
     * Verifies that the service rejects a request to update a credentials set if the resource Version value is
     * outdated.
     * 
     * @param context The vert.x test context.
     */
    @Test
    public void testUpdateCredentialsWithOutdatedResourceVersionFails(final VertxTestContext context) {

        registry
                .updateCredentials(
                        TENANT, deviceId, Collections.singleton(hashedPasswordCredential),
                        HttpURLConnection.HTTP_NO_CONTENT)
                .compose(ar -> {
                    final var etag = ar.get(HTTP_HEADER_ETAG);
                    context.verify(() -> assertThat(etag).as("missing etag header").isNotNull());
                    // now try to update credentials with other version
                    return registry.updateCredentialsWithVersion(TENANT, deviceId, Collections.singleton(hashedPasswordCredential),
                            etag + 10, HttpURLConnection.HTTP_PRECON_FAILED);
                })
                .onComplete(context.completing());

    }

    /**
     * Verifies that the service returns a 400 status code for an add credentials request with hashed password
     * credentials that use a BCrypt hash with more than the configured max iterations.
     * 
     * @param context The vert.x test context.
     */
    @Test
    public void testAddCredentialsFailsForBCryptWithTooManyIterations(final VertxTestContext context)  {

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
                .onComplete(context.completing());
    }

    /**
     * Verifies that the service returns a 400 status code for an add credentials request with an empty body.
     * 
     * @param context The vert.x test context.
     */
    @Test
    public void testAddCredentialsFailsForEmptyBody(final VertxTestContext context) {

        registry.updateCredentialsRaw(TENANT, deviceId, null, CrudHttpClient.CONTENT_TYPE_JSON,
                HttpURLConnection.HTTP_BAD_REQUEST)
                .onComplete(context.completing());

    }

    /**
     * Verify that a json payload to add credentials that does not contain a {@link CredentialsConstants#FIELD_TYPE}
     * is not accepted and responded with {@link HttpURLConnection#HTTP_BAD_REQUEST}
     * and a non empty error response message.
     * 
     * @param context The vert.x test context.
     */
    @Test
    public void testAddCredentialsFailsForMissingType(final VertxTestContext context) {
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
    public void testAddCredentialsFailsForMissingAuthId(final VertxTestContext context) {
        testAddCredentialsWithMissingPayloadParts(context, CredentialsConstants.FIELD_AUTH_ID);
    }

    private void testAddCredentialsWithMissingPayloadParts(final VertxTestContext context, final String fieldMissing) {

        final JsonObject json = JsonObject.mapFrom(hashedPasswordCredential);
        json.remove(fieldMissing);
        final JsonArray payload = new JsonArray()
                .add(json);

        registry
                .updateCredentialsRaw(TENANT, deviceId, payload.toBuffer(),
                        CrudHttpClient.CONTENT_TYPE_JSON, HttpURLConnection.HTTP_BAD_REQUEST)
                .onComplete(context.completing());
    }

    /**
     * Verifies that the service accepts an update credentials request for existing credentials.
     * 
     * @param context The vert.x test context.
     */
    @Test
    public void testUpdateCredentialsSucceeds(final VertxTestContext context) {

        final PasswordCredential altered = JsonObject
                .mapFrom(hashedPasswordCredential)
                .mapTo(PasswordCredential.class);
        altered.getSecrets().get(0).setComment("test");

        registry.updateCredentials(TENANT, deviceId, Collections.singleton(hashedPasswordCredential),
                HttpURLConnection.HTTP_NO_CONTENT)
                .compose(ar -> registry.updateCredentials(TENANT, deviceId, Collections.singleton(altered),
                        HttpURLConnection.HTTP_NO_CONTENT))
            .compose(ur -> registry.getCredentials(TENANT, deviceId))
            .onComplete(context.succeeding(gr -> {
                final JsonObject retrievedSecret = gr.toJsonArray().getJsonObject(0).getJsonArray("secrets").getJsonObject(0);
                context.verify(() -> assertThat(retrievedSecret.getString("comment")).isEqualTo("test"));
                context.completeNow();
            }));
    }

    /**
     * Verifies that the service accepts an update credentials request for existing credentials.
     * 
     * @param context The vert.x test context.
     */
    @Test
    @Disabled("Requires support for clear text passwords")
    public void testUpdateCredentialsSucceedsForClearTextPassword(final VertxTestContext context) {

        final PasswordCredential secret = IntegrationTestSupport.createPasswordCredential(authId, "newPassword");

        registry.addCredentials(TENANT, deviceId, Collections.<CommonCredential> singleton(hashedPasswordCredential))
                .compose(ar -> registry.updateCredentials(TENANT, deviceId, secret))
                .compose(ur -> registry.getCredentials(TENANT, deviceId))
                .onComplete(context.succeeding(gr -> {
                    final CredentialsObject o = extractFirstCredential(gr.toJsonObject())
                            .mapTo(CredentialsObject.class);
                    context.verify(() -> {
                        assertThat(o.getAuthId()).isEqualTo(authId);
                        assertThat(o.getCandidateSecrets(s -> CredentialsConstants.getPasswordHash(s))
                                .stream().anyMatch(hash -> ORIG_BCRYPT_PWD.equals(hash)))
                        .isFalse();
                    });
                    context.completeNow();
                }));
    }

    /**
     * Verifies that the service rejects an update request for non-existing credentials.
     * 
     * @param context The vert.x test context.
     */
    @Test
    public void testUpdateCredentialsFailsForNonExistingCredentials(final VertxTestContext context) {
        registry
                .updateCredentialsWithVersion(
                        TENANT,
                        deviceId,
                        Collections.singleton(hashedPasswordCredential),
                        "3",
                        HttpURLConnection.HTTP_PRECON_FAILED)
                .onComplete(context.completing());
    }

    /**
     * Verify that a correctly added credentials record can be successfully looked up again by using the type and
     * authId.
     * 
     * @param context The vert.x test context.
     */
    @Test
    public void testGetAddedCredentials(final VertxTestContext context) {

        registry.updateCredentials(TENANT, deviceId, Collections.singleton(hashedPasswordCredential),
                HttpURLConnection.HTTP_NO_CONTENT)
                .compose(ar -> registry.getCredentials(TENANT, deviceId))
                .onComplete(context.succeeding(b -> {
                    final PasswordCredential cred = b.toJsonArray().getJsonObject(0).mapTo(PasswordCredential.class);
                    cred.getSecrets().forEach(secret -> {
                        context.verify(() -> assertThat(secret.getId()).isNotNull());
                    });
                    context.completeNow();;
                }));

    }

    /**
     * Verifies that the service accepts an add credentials and assign it with an Etag value.
     *
     * @param context The vert.x test context.
     */
    @Test
    public void testAddedCredentialsContainsEtag(final VertxTestContext context)  {

        registry
                .updateCredentials(TENANT, deviceId, Collections.singleton(hashedPasswordCredential),
                        HttpURLConnection.HTTP_NO_CONTENT)
                .onComplete(context.succeeding(res -> {
                    context.verify(() -> assertThat(res.get(HTTP_HEADER_ETAG)).as("etag header missing").isNotNull());
                    context.completeNow();
                }));
    }

    /**
     * Verify that multiple (2) correctly added credentials records of the same authId can be successfully looked up by
     * single requests using their type and authId again.
     * 
     * @param context The vert.x test context.
     */
    @Test
    public void testGetAddedCredentialsMultipleTypesSingleRequests(final VertxTestContext context) {

        final List<CommonCredential> credentialsListToAdd = new ArrayList<>();
        credentialsListToAdd.add(hashedPasswordCredential);
        credentialsListToAdd.add(pskCredentials);

        registry
                .addCredentials(TENANT, deviceId, credentialsListToAdd)

                .compose(ar -> registry.getCredentials(TENANT, deviceId))
                .onComplete(context.succeeding(b -> {
                    assertResponseBodyContainsAllCredentials(context, b.toJsonArray(), credentialsListToAdd);
                    context.completeNow();
        }));
    }

    /**
     * Verifies that the service returns all credentials registered for a given device regardless of authentication identifier.
     * <p>
     * The returned JsonArray must consist exactly the same credentials as originally added.
     * 
     * @param context The vert.x test context.
     * @throws InterruptedException if registration of credentials is interrupted.
     */
    @Test
    public void testGetAllCredentialsForDeviceSucceeds(final VertxTestContext context) throws InterruptedException {

        final List<CommonCredential> credentialsListToAdd = new ArrayList<>();
        credentialsListToAdd.add(newPskCredentials("auth"));
        credentialsListToAdd.add(newPskCredentials("other-auth"));

        registry.addCredentials(TENANT, deviceId, credentialsListToAdd)
            .compose(ar -> registry.getCredentials(TENANT, deviceId))
            .onComplete(context.succeeding(b -> {
                assertResponseBodyContainsAllCredentials(context, b.toJsonArray(), credentialsListToAdd);
                context.completeNow();
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
    public void testGetCredentialsForDeviceRegardlessOfType(final VertxTestContext context) throws InterruptedException {

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
            .onComplete(context.succeeding(b -> {
                assertResponseBodyContainsAllCredentials(context, b.toJsonArray(), credentialsToAdd);
                context.completeNow();
            }));
    }

    /**
     * Verify that a correctly added credentials record is not found when looking it up again with a wrong type.
     * 
     * @param context The vert.x test context.
     */
    @Test
    public void testGetAddedCredentialsButWithWrongType(final VertxTestContext context)  {

        registry.updateCredentials(TENANT, deviceId, Collections.singleton(hashedPasswordCredential),
                HttpURLConnection.HTTP_NO_CONTENT)
            .compose(ar -> registry.getCredentials(TENANT, authId, "wrong-type", HttpURLConnection.HTTP_NOT_FOUND))
            .onComplete(context.completing());
    }

    /**
     * Verify that a correctly added credentials record is not found when looking it up again with a wrong authId.
     * 
     * @param context The vert.x test context.
     */
    @Test
    public void testGetAddedCredentialsButWithWrongAuthId(final VertxTestContext context)  {

        registry.updateCredentials(TENANT, deviceId, Collections.singleton(hashedPasswordCredential),
                HttpURLConnection.HTTP_NO_CONTENT)
            .compose(ar -> registry.getCredentials(
                    TENANT,
                    "wrong-auth-id",
                    CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD,
                    HttpURLConnection.HTTP_NOT_FOUND))
            .onComplete(context.completing());
    }

    private static void assertResponseBodyContainsAllCredentials(final VertxTestContext context, final JsonArray responseBody,
            final List<CommonCredential> expected) {

        assertThat(expected.size()).isEqualTo(responseBody.size());

        responseBody.forEach(credential -> {
            JsonObject.mapFrom(credential).getJsonArray(CredentialsConstants.FIELD_SECRETS)
                    .forEach(secret -> {
                        // each secret should contain an ID.
                        assertThat(JsonObject.mapFrom(secret)
                                .getString(RegistryManagementConstants.FIELD_ID)).isNotNull();
                    });
        });

        // secrets id were added by registry, strip it so we can compare other fields.
        responseBody.forEach(credential -> {
            ((JsonObject) credential).getJsonArray(CredentialsConstants.FIELD_SECRETS)
                    .forEach(secret -> {
                        ((JsonObject) secret).remove(RegistryManagementConstants.FIELD_ID);
                    });
        });

        // The returned secrets won't contains the hashed password details fields, strip them from the expected values.
        final JsonArray expectedArray = new JsonArray();
        expected.stream().forEach(credential -> {
            final JsonObject jsonCredential = JsonObject.mapFrom(credential);
            expectedArray.add(stripHashAndSaltFromPasswordSecret(jsonCredential));
        });

        // now compare
        context.verify(() -> assertThat(responseBody).isEqualTo(expectedArray));
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

    private static JsonObject stripHashAndSaltFromPasswordSecret(final JsonObject credential) {
        if (credential.getString(CredentialsConstants.FIELD_TYPE).equals(CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD)) {

            credential.getJsonArray(CredentialsConstants.FIELD_SECRETS)
                    .forEach(secret -> {
                        // password details should not be expected from the registry as well
                        ((JsonObject) secret).remove(CredentialsConstants.FIELD_SECRETS_HASH_FUNCTION);
                        ((JsonObject) secret).remove(CredentialsConstants.FIELD_SECRETS_PWD_HASH);
                        ((JsonObject) secret).remove(CredentialsConstants.FIELD_SECRETS_SALT);
                        ((JsonObject) secret).remove(CredentialsConstants.FIELD_SECRETS_PWD_PLAIN);
                    });
        }

        return credential;
    }
}
