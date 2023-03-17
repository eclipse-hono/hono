/*******************************************************************************
 * Copyright (c) 2016, 2023 Contributors to the Eclipse Foundation
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

import static com.google.common.truth.Truth.assertThat;

import java.net.HttpURLConnection;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateEncodingException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import org.assertj.core.api.Assertions;
import org.assertj.core.api.recursive.comparison.RecursiveComparisonConfiguration;
import org.eclipse.hono.service.http.HttpUtils;
import org.eclipse.hono.service.management.credentials.CommonCredential;
import org.eclipse.hono.service.management.credentials.Credentials;
import org.eclipse.hono.service.management.credentials.GenericCredential;
import org.eclipse.hono.service.management.credentials.GenericSecret;
import org.eclipse.hono.service.management.credentials.PasswordCredential;
import org.eclipse.hono.service.management.credentials.PasswordSecret;
import org.eclipse.hono.service.management.credentials.PskCredential;
import org.eclipse.hono.service.management.credentials.X509CertificateCredential;
import org.eclipse.hono.service.management.credentials.X509CertificateSecret;
import org.eclipse.hono.tests.CrudHttpClient;
import org.eclipse.hono.tests.DeviceRegistryHttpClient;
import org.eclipse.hono.tests.IntegrationTestSupport;
import org.eclipse.hono.util.CredentialsConstants;
import org.eclipse.hono.util.RegistryManagementConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.SelfSignedCertificate;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

/**
 * Tests verifying the behavior of the Device Registry Management API's <em>credentials</em>
 * endpoint provided by the Device Registry component.
 */
@ExtendWith(VertxExtension.class)
@Timeout(value = 5, timeUnit = TimeUnit.SECONDS)
public class CredentialsManagementIT extends DeviceRegistryTestBase {

    private static final Logger LOG = LoggerFactory.getLogger(CredentialsManagementIT.class);

    private static final String PREFIX_AUTH_ID = "my_sensor.20=ext";
    private static final String ORIG_BCRYPT_PWD = "thePassword";

    private DeviceRegistryHttpClient registry;

    private String tenantId;
    private String deviceId;
    private String authId;
    private AtomicReference<String> resourceVersion;
    private PasswordCredential hashedPasswordCredential;
    private PskCredential pskCredentials;

    /**
     * Sets up the fixture.
     * <p>
     * In particular, registered a new device for a random tenant using a random device ID.
     *
     * @param ctx The test context.
     * @param testInfo The test meta data.
     */
    @BeforeEach
    public void setUp(final VertxTestContext ctx, final TestInfo testInfo) {

        registry = getHelper().registry;
        tenantId = getHelper().getRandomTenantId();
        deviceId = getHelper().getRandomDeviceId(tenantId);
        authId = getRandomAuthId(PREFIX_AUTH_ID);
        resourceVersion = new AtomicReference<>();
        hashedPasswordCredential = Credentials.createPasswordCredential(authId, ORIG_BCRYPT_PWD);
        pskCredentials = Credentials.createPSKCredential(authId, "secret");
        registry.addTenant(tenantId)
            .compose(response -> registry.registerDevice(tenantId, deviceId))
            .onComplete(ctx.succeedingThenComplete());
    }

    private static void assertResourceVersionHasChanged(final AtomicReference<String> originalVersion, final MultiMap responseHeaders) {
        final String resourceVersion = responseHeaders.get(HttpHeaders.ETAG);
        assertThat(resourceVersion).isNotNull();
        assertThat(resourceVersion).isNotEqualTo(originalVersion.get());
        originalVersion.set(resourceVersion);
    }

    /**
     * Verifies that a newly added device has an empty set of credentials and that the
     * service successfully adds arbitrary types of credentials.
     *
     * @param context The vert.x test context.
     * @throws NoSuchAlgorithmException if the JVM does not support ECC cryptography.
     */
    @Test
    public void testAddCredentialsSucceeds(final VertxTestContext context) throws NoSuchAlgorithmException {

        final PasswordCredential pwdCredential = Credentials.createPasswordCredential(authId, "thePassword");
        pwdCredential.getExtensions().put("client-id", "MQTT-client-2384236854");

        final var pskCredential = Credentials.createPSKCredential("psk-id", "psk-key");

        final var x509Credential = X509CertificateCredential.fromAuthId(
                "emailAddress=foo@bar.com, CN=foo, O=bar",
                List.of(new X509CertificateSecret()));
        x509Credential.setComment("non-standard attribute type");

        final var generator = KeyPairGenerator.getInstance(CredentialsConstants.EC_ALG);
        final var keyPair = generator.generateKeyPair();
        final var rpkCredential = Credentials.createRPKCredential(authId, keyPair.getPublic());

        final List<CommonCredential> credentials = List.of(
                pwdCredential,
                pskCredential,
                x509Credential,
                rpkCredential);

        registry.getCredentials(tenantId, deviceId)
            .compose(httpResponse -> {
                context.verify(() -> {
                    assertResourceVersionHasChanged(resourceVersion, httpResponse.headers());
                    assertThat(httpResponse.bodyAsJsonArray()).isEmpty();
                });
                return registry.addCredentials(tenantId, deviceId, credentials);
            })
            .compose(httpResponse -> {
                context.verify(() -> assertResourceVersionHasChanged(resourceVersion, httpResponse.headers()));
                return registry.getCredentials(tenantId, deviceId);
            })
            .onComplete(context.succeeding(httpResponse -> {
                context.verify(() -> {
                    final CommonCredential[] credsOnRecord = httpResponse.bodyAsJson(CommonCredential[].class);
                    assertThat(credsOnRecord).hasLength(4);
                    Arrays.stream(credsOnRecord)
                        .forEach(creds -> {
                            assertThat(creds.getExtensions().get("device-id")).isNull();
                            if (creds instanceof PasswordCredential) {
                                assertThat(creds.getExtensions().get("client-id")).isEqualTo("MQTT-client-2384236854");
                            } else if (creds instanceof X509CertificateCredential) {
                                assertThat(creds.getComment()).isEqualTo("non-standard attribute type");
                            }
                            creds.getSecrets()
                                .forEach(secret -> {
                                    assertThat(secret.isEnabled()).isTrue();
                                    assertThat(secret.getId()).isNotNull();
                                });
                        });
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

        final PasswordCredential credential = Credentials.createPasswordCredential(authId, "thePassword");
        credential.getExtensions().put("client-id", "MQTT-client-2384236854");

        registry.addCredentials(tenantId, deviceId, List.of(credential))
                .compose(httpResponse -> {
                    context.verify(() -> assertResourceVersionHasChanged(resourceVersion, httpResponse.headers()));
                    return registry.getCredentials(tenantId, deviceId);
                })
                .onComplete(context.succeeding(httpResponse -> {
                    context.verify(() -> {
                        final JsonArray response = httpResponse.bodyAsJsonArray();
                        assertThat(response.size()).isEqualTo(1);
                        final JsonObject credentialObject = response.getJsonObject(0);
                        final var ext = credentialObject.getJsonObject(RegistryManagementConstants.FIELD_EXT);
                        assertThat(ext).isNotNull();
                        assertThat(ext.getString("client-id")).isEqualTo("MQTT-client-2384236854");

                        // the device-id must not be part of the "ext" section
                        assertThat(ext.getString("device-id")).isNull();
                    });
                    context.completeNow();
                }));
    }

    /**
     * Verifies that the service accepts a request to update an empty set of credentials with
     * raw public key credentials containing an encoded X.509 certificate.
     *
     * @param context The vert.x test context.
     */
    @Test
    public void testAddRpkCredentialsSucceedsForCertificate(final VertxTestContext context) {

        // GIVEN a device with an empty set of credentials
        // WHEN trying to add RPK credentials with a secret that contains an encoded
        // X.509 certificate
        final var selfSignedCert = SelfSignedCertificate.create();
        getHelper().getCertificate(selfSignedCert.certificatePath())
            .compose(cert -> {
                try {
                    final var secret = new JsonObject().put(
                            RegistryManagementConstants.FIELD_PAYLOAD_CERT,
                            cert.getEncoded());
                    final var payload = new JsonArray().add(new JsonObject()
                            .put(RegistryManagementConstants.FIELD_TYPE, CredentialsConstants.SECRETS_TYPE_RAW_PUBLIC_KEY)
                            .put(RegistryManagementConstants.FIELD_AUTH_ID, "bumlux")
                            .put(RegistryManagementConstants.FIELD_SECRETS, new JsonArray().add(secret)));
                    return registry.updateCredentialsRaw(
                            tenantId,
                            deviceId,
                            Optional.ofNullable(payload).map(JsonArray::toBuffer).orElse(null),
                            CrudHttpClient.CONTENT_TYPE_JSON,
                            HttpURLConnection.HTTP_NO_CONTENT);
                } catch (final CertificateEncodingException e) {
                    return Future.failedFuture(e);
                }
            })
            .onComplete(context.succeedingThenComplete());
    }

    /**
     * Verifies that the service returns a 400 status code for an add credentials request with a Content-Type other than
     * application/json.
     *
     * @param context The vert.x test context.
     */
    @Test
    public void testAddCredentialsFailsForWrongContentType(final VertxTestContext context) {

        registry.updateCredentials(
                tenantId,
                deviceId,
                List.of(hashedPasswordCredential),
                "application/x-www-form-urlencoded",
                HttpURLConnection.HTTP_BAD_REQUEST)
            .onComplete(context.succeedingThenComplete());

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
        final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(Credentials.getMaxBcryptCostFactor() + 1);

        final PasswordSecret secret = new PasswordSecret();
        secret.setHashFunction(CredentialsConstants.HASH_FUNCTION_BCRYPT);
        secret.setPasswordHash(encoder.encode("thePassword"));
        final PasswordCredential credential = new PasswordCredential(authId, List.of(secret));

        // WHEN adding the credentials
        testAddCredentialsWithErroneousPayload(
                context,
                new JsonArray().add(JsonObject.mapFrom(credential)),
                // THEN the request fails with 400
                HttpURLConnection.HTTP_BAD_REQUEST);
    }

    /**
     * Verifies that a request to add credentials that contain unsupported properties
     * fails with a 400 status code.
     *
     * @param ctx The vert.x test context
     */
    @Test
    public void testAddCredentialsFailsForUnknownProperties(final VertxTestContext ctx) {

        final JsonArray requestBody = new JsonArray()
                .add(JsonObject.mapFrom(pskCredentials).put("unexpected", "property"));

        registry.updateCredentialsRaw(
                tenantId,
                deviceId,
                requestBody.toBuffer(),
                HttpUtils.CONTENT_TYPE_JSON_UTF8,
                HttpURLConnection.HTTP_BAD_REQUEST)
            .onComplete(ctx.succeedingThenComplete());
    }

    /**
     * Verifies that the service returns a 400 status code for an add credentials request with an empty body.
     *
     * @param context The vert.x test context.
     */
    @Test
    public void testAddCredentialsFailsForEmptyBody(final VertxTestContext context) {

        testAddCredentialsWithErroneousPayload(
                context,
                null,
                HttpURLConnection.HTTP_BAD_REQUEST);
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

        final JsonObject credentials = JsonObject.mapFrom(hashedPasswordCredential);
        credentials.remove(CredentialsConstants.FIELD_TYPE);

        testAddCredentialsWithErroneousPayload(
                context,
                new JsonArray().add(credentials),
                HttpURLConnection.HTTP_BAD_REQUEST);
    }

    /**
     * Verifies that a JSON payload to add generic credentials that contain a type name that
     * does not match the type name regex is rejected with a 400.
     *
     * @param context The vert.x test context.
     */
    @Test
    public void testAddCredentialsFailsForIllegalTypeName(final VertxTestContext context) {

        final JsonObject credentials = new JsonObject()
                .put(RegistryManagementConstants.FIELD_AUTH_ID, "deviceId")
                .put(RegistryManagementConstants.FIELD_TYPE, "#illegal");

        testAddCredentialsWithErroneousPayload(
                context,
                new JsonArray().add(credentials),
                HttpURLConnection.HTTP_BAD_REQUEST);
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

        final JsonObject credentials = JsonObject.mapFrom(hashedPasswordCredential);
        credentials.remove(CredentialsConstants.FIELD_AUTH_ID);

        testAddCredentialsWithErroneousPayload(
                context,
                new JsonArray().add(credentials),
                HttpURLConnection.HTTP_BAD_REQUEST);
    }

    /**
     * Verifies that a JSON payload to add credentials that contains an authentication identifier that
     * does not match the auth-id regex is rejected with a 400.
     *
     * @param context The vert.x test context.
     */
    @Test
    public void testAddCredentialsFailsForIllegalAuthId(final VertxTestContext context) {

        final JsonObject credentials = JsonObject.mapFrom(hashedPasswordCredential)
                .put(RegistryManagementConstants.FIELD_AUTH_ID, "#illegal");

        testAddCredentialsWithErroneousPayload(
                context,
                new JsonArray().add(credentials),
                HttpURLConnection.HTTP_BAD_REQUEST);
    }

    /**
     * Verifies that the service rejects a request to update an empty set of credentials with
     * raw public key credentials containing a malformed public key.
     *
     * @param context The vert.x test context.
     */
    @Test
    public void testAddRpkCredentialsFailsForSecretWithMalformedPublicKey(final VertxTestContext context) {

        // GIVEN a device with an empty set of credentials
        // WHEN trying to add RPK credentials with a secret that contains a byte array that
        // is not a proper DER encoding of a public key
        final var secret = new JsonObject()
                .put(RegistryManagementConstants.FIELD_PAYLOAD_KEY_ALGORITHM,
                        CredentialsConstants.EC_ALG)
                .put(RegistryManagementConstants.FIELD_PAYLOAD_PUBLIC_KEY,
                        new byte[] {0x01, 0x02, 0x03});

        final var payload = new JsonArray().add(new JsonObject()
                .put(RegistryManagementConstants.FIELD_TYPE, CredentialsConstants.SECRETS_TYPE_RAW_PUBLIC_KEY)
                .put(RegistryManagementConstants.FIELD_AUTH_ID, "bumlux")
                .put(RegistryManagementConstants.FIELD_SECRETS, new JsonArray().add(secret)));

        testAddCredentialsWithErroneousPayload(context, payload, HttpURLConnection.HTTP_BAD_REQUEST);
    }

    /**
     * Verifies that the service rejects a request to update an empty set of credentials with
     * raw public key credentials containing a malformed X.509 certificate.
     *
     * @param context The vert.x test context.
     */
    @Test
    public void testAddRpkCredentialsFailsForSecretWithMalformedCertificate(final VertxTestContext context) {

        // GIVEN a device with an empty set of credentials
        // WHEN trying to add RPK credentials with a secret that contains a byte array that
        // is not a proper DER encoding of an X.509 certificate
        final var secret = new JsonObject()
                .put(RegistryManagementConstants.FIELD_PAYLOAD_KEY_ALGORITHM,
                        CredentialsConstants.EC_ALG)
                .put(RegistryManagementConstants.FIELD_PAYLOAD_CERT,
                        new byte[] {0x01, 0x02, 0x03});

        final var payload = new JsonArray().add(new JsonObject()
                .put(RegistryManagementConstants.FIELD_TYPE, CredentialsConstants.SECRETS_TYPE_RAW_PUBLIC_KEY)
                .put(RegistryManagementConstants.FIELD_AUTH_ID, "bumlux")
                .put(RegistryManagementConstants.FIELD_SECRETS, new JsonArray().add(secret)));

        testAddCredentialsWithErroneousPayload(context, payload, HttpURLConnection.HTTP_BAD_REQUEST);
    }

    /**
     * Verifies that the service rejects a request to update an empty set of credentials with
     * hashed-password credentials containing a secret that has an ID.
     *
     * @param context The vert.x test context.
     */
    @Test
    public void testAddCredentialsFailsForSecretWithId(final VertxTestContext context) {

        // GIVEN a device with an empty set of credentials
        // WHEN trying to add password credentials with a secret that has an ID
        final var secretWithId = new JsonObject().put(RegistryManagementConstants.FIELD_ID, "secret-one");

        final var payload = new JsonArray().add(new JsonObject()
                .put(RegistryManagementConstants.FIELD_TYPE, CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD)
                .put(RegistryManagementConstants.FIELD_AUTH_ID, "bumlux")
                .put(RegistryManagementConstants.FIELD_SECRETS, new JsonArray().add(secretWithId)));

        testAddCredentialsWithErroneousPayload(context, payload, HttpURLConnection.HTTP_BAD_REQUEST);
    }

    private void testAddCredentialsWithErroneousPayload(
            final VertxTestContext context,
            final JsonArray payload,
            final int expectedStatus) {

        LOG.debug("updating credentials with request body: {}",
                Optional.ofNullable(payload).map(JsonArray::encodePrettily).orElse(null));

        registry.updateCredentialsRaw(
                tenantId,
                deviceId,
                Optional.ofNullable(payload).map(JsonArray::toBuffer).orElse(null),
                CrudHttpClient.CONTENT_TYPE_JSON,
                expectedStatus)
            .onComplete(context.succeeding(response -> {
                context.verify(() -> IntegrationTestSupport.assertErrorPayload(response));
                context.completeNow();
            }));
    }

    /**
     * Verifies that the service accepts an update credentials request for existing credentials.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testUpdateCredentialsSucceeds(final VertxTestContext ctx) {

        final AtomicReference<PasswordSecret> originalSecret = new AtomicReference<>();

        // GIVEN a device with a set of hashed password credentials
        registry.addCredentials(tenantId, deviceId, List.of(hashedPasswordCredential))
            .compose(ok -> registry.getCredentials(tenantId, deviceId))
            .compose(httpResponse -> {
                ctx.verify(() -> assertResourceVersionHasChanged(resourceVersion, httpResponse.headers()));
                // WHEN updating the existing password
                final JsonArray bodyAsJsonArray = httpResponse.bodyAsJsonArray();
                LOG.debug("received original credentials list: {}", bodyAsJsonArray.encodePrettily());
                ctx.verify(() -> assertThat(bodyAsJsonArray).hasSize(1));
                final PasswordCredential existingCredentials = bodyAsJsonArray.getJsonObject(0).mapTo(PasswordCredential.class);
                ctx.verify(() -> {
                    assertThat(existingCredentials.getSecrets()).hasSize(1);
                    final PasswordSecret existingSecret = existingCredentials.getSecrets().get(0);
                    assertThat(existingSecret.getId()).isNotNull();
                    originalSecret.set(existingSecret);
                });
                final PasswordSecret changedSecret = new PasswordSecret();
                changedSecret.setId(originalSecret.get().getId());
                changedSecret.setPasswordPlain("completely-different-password");
                changedSecret.setComment("updated");
                // and adding a new one
                final PasswordSecret newSecret = new PasswordSecret();
                newSecret.setPasswordPlain("future-password");
                newSecret.setNotBefore(Instant.now().plus(1, ChronoUnit.DAYS));
                final PasswordCredential updatedCredentials = new PasswordCredential(
                        existingCredentials.getAuthId(),
                        List.of(changedSecret, newSecret));
                return registry.updateCredentialsWithVersion(
                        tenantId,
                        deviceId,
                        List.of(updatedCredentials),
                        resourceVersion.get(),
                        HttpURLConnection.HTTP_NO_CONTENT);
            })
            .compose(httpResponse -> {
                ctx.verify(() -> assertResourceVersionHasChanged(resourceVersion, httpResponse.headers()));
                return registry.getCredentials(tenantId, deviceId);
            })
            .onComplete(ctx.succeeding(httpResponse -> {
                final JsonArray bodyAsJsonArray = httpResponse.bodyAsJsonArray();
                LOG.debug("received updated credentials list: {}", bodyAsJsonArray.encodePrettily());
                ctx.verify(() -> {
                    assertThat(bodyAsJsonArray).hasSize(1);
                    final PasswordCredential updatedCredentials = bodyAsJsonArray.getJsonObject(0).mapTo(PasswordCredential.class);
                    assertThat(updatedCredentials.getSecrets()).hasSize(2);
                    // THEN the original secret has been updated
                    final PasswordSecret updatedSecret = updatedCredentials.getSecrets()
                            .stream()
                            .filter(s -> originalSecret.get().getId().equals(s.getId()))
                            .findAny()
                            .orElse(null);
                    assertThat(updatedSecret).isNotNull();
                    assertThat(updatedSecret.getComment()).isEqualTo("updated");
                });
                ctx.completeNow();
            }));
    }

    /**
     * Verifies that the service rejects a request to update a credentials set if the resource Version value is
     * outdated.
     *
     * @param context The vert.x test context.
     */
    @Test
    public void testUpdateCredentialsFailsForWrongResourceVersion(final VertxTestContext context) {

        registry.updateCredentials(
                tenantId,
                deviceId,
                List.of(hashedPasswordCredential),
                HttpURLConnection.HTTP_NO_CONTENT)
            .onFailure(context::failNow)
            .compose(httpResponse -> {
                context.verify(() -> assertResourceVersionHasChanged(resourceVersion, httpResponse.headers()));
                // now try to update credentials with other version
                return registry.updateCredentialsWithVersion(
                        tenantId,
                        deviceId,
                        List.of(hashedPasswordCredential),
                        resourceVersion.get() + "_other",
                        HttpURLConnection.HTTP_PRECON_FAILED);
            })
            .onComplete(context.succeeding(response -> {
                context.verify(() -> IntegrationTestSupport.assertErrorPayload(response));
                context.completeNow();
            }));
    }

    /**
     * Verifies that a request to update credentials with a body that exceeds the registry's max payload limit
     * fails with a {@link HttpURLConnection#HTTP_ENTITY_TOO_LARGE} status code.
     *
     * @param context The vert.x test context.
     */
    @Test
    public void testUpdateCredentialsFailsForRequestPayloadExceedingLimit(final VertxTestContext context)  {

        final var data = new char[3000];
        Arrays.fill(data, 'x');

        final List<CommonCredential> credentials = List.of(
                Credentials.createPasswordCredential("auth-id", "password", OptionalInt.of(4))
                    .setExtensions(Map.of("data", data)));

        registry.updateCredentials(tenantId, deviceId, credentials, HttpURLConnection.HTTP_ENTITY_TOO_LARGE)
            .onComplete(context.succeeding(response -> {
                context.verify(() -> IntegrationTestSupport.assertErrorPayload(response));
                context.completeNow();
            }));
    }

    /**
     * Verifies that a request to update credentials fails if the given tenant does not exist.
     *
     * @param context The vert.x test context.
     */
    @Test
    public void testUpdateCredentialsFailsForNonExistingTenant(final VertxTestContext context)  {

        final List<CommonCredential> credentials = List.of(
                Credentials.createPasswordCredential("auth-id", "password", OptionalInt.of(4)));

        registry.updateCredentials("non-existing-tenant", deviceId, credentials, HttpURLConnection.HTTP_NOT_FOUND)
            .onComplete(context.succeeding(response -> {
                context.verify(() -> IntegrationTestSupport.assertErrorPayload(response));
                context.completeNow();
            }));
    }

    /**
     * Verify that a correctly added credentials record can be successfully looked up again by using the type and
     * authId.
     *
     * @param context The vert.x test context.
     */
    @Test
    public void testGetAddedCredentials(final VertxTestContext context) {

        registry.updateCredentials(tenantId, deviceId, List.of(hashedPasswordCredential), HttpURLConnection.HTTP_NO_CONTENT)
                .compose(ar -> registry.getCredentials(tenantId, deviceId))
                .onComplete(context.succeeding(httpResponse -> {
                    context.verify(() -> {
                        final JsonArray credentials = httpResponse.bodyAsJsonArray();
                        LOG.trace("retrieved credentials [tenant-id: {}, device-id: {}]: {}",
                                tenantId, deviceId, credentials);
                        final PasswordCredential cred = credentials.getJsonObject(0).mapTo(PasswordCredential.class);
                        cred.getSecrets().forEach(secret -> assertThat(secret.getId()).isNotNull());
                    });
                    context.completeNow();
                }));

    }

    /**
     * Verifies that the service accepts an add credentials and assign it with an Etag value.
     *
     * @param context The vert.x test context.
     */
    @Test
    public void testUpdateCredentialsChangesResourceVersion(final VertxTestContext context)  {

        registry.updateCredentials(
                tenantId,
                deviceId, List.of(hashedPasswordCredential),
                HttpURLConnection.HTTP_NO_CONTENT)
            .onComplete(context.succeeding(httpResponse -> {
                context.verify(() -> assertResourceVersionHasChanged(resourceVersion, httpResponse.headers()));
                context.completeNow();
            }));
    }

    /**
     * Verifies that the service returns all credentials registered for a given device regardless of
     * authentication identifier and type.
     * <p>
     * The returned JsonArray must contain exactly the same credentials as originally added.
     *
     * @param context The vert.x test context.
     */
    @Test
    public void testGetAllCredentialsForDeviceSucceeds(final VertxTestContext context) {

        final List<CommonCredential> credentialsListToAdd = new ArrayList<>();
        credentialsListToAdd.add(pskCredentials);
        credentialsListToAdd.add(hashedPasswordCredential);
        credentialsListToAdd.add(X509CertificateCredential.fromAuthId("CN=Acme", List.of(new X509CertificateSecret())));
        for (int i = 0; i < 3; i++) {

            final GenericSecret secret = new GenericSecret();
            secret.setAdditionalProperties(Map.of("field-" + i, "setec astronomy"));

            final GenericCredential credential = new GenericCredential("type-" + i, getRandomAuthId(PREFIX_AUTH_ID), List.of(secret));

            credentialsListToAdd.add(credential);
        }

        registry.addCredentials(tenantId, deviceId, credentialsListToAdd)
            .compose(ar -> registry.getCredentials(tenantId, deviceId))
            .onComplete(context.succeeding(httpResponse -> {
                context.verify(() -> assertResponseBodyContainsAllCredentials(httpResponse.bodyAsJsonArray(), credentialsListToAdd));
                context.completeNow();
            }));
    }

    /**
     * Verifies that a request to read credentials fails if the given tenant does not exist.
     *
     * @param context The vert.x test context.
     */
    @Test
    public void testGetCredentialsFailsForNonExistingTenant(final VertxTestContext context)  {

        registry.getCredentials("non-existing-tenant", "deviceId", HttpURLConnection.HTTP_NOT_FOUND)
            .onComplete(context.succeeding(response -> {
                context.verify(() -> IntegrationTestSupport.assertErrorPayload(response));
                context.completeNow();
            }));
    }

    private static void assertResponseBodyContainsAllCredentials(final JsonArray responseBody, final List<CommonCredential> expected) {

        final List<CommonCredential> returnedCreds = responseBody.stream()
                .filter(JsonObject.class::isInstance)
                .map(JsonObject.class::cast)
                .map(json -> json.mapTo(CommonCredential.class))
                .collect(Collectors.toList());

        final RecursiveComparisonConfiguration config = RecursiveComparisonConfiguration.builder()
                .withStrictTypeChecking(true)
                .withIgnoreCollectionOrder(true)
                .withIgnoredFields("secrets.id", "secrets.key", "secrets.passwordHash", "secrets.passwordPlain", "secrets.hashFunction", "secrets.salt")
                .build();

        Assertions.assertThat(returnedCreds)
            .usingRecursiveFieldByFieldElementComparator(config)
            .containsExactlyInAnyOrderElementsOf(expected);
    }

    private static String getRandomAuthId(final String authIdPrefix) {
        return authIdPrefix + "-" + UUID.randomUUID();
    }
}
