/*******************************************************************************
 * Copyright (c) 2016, 2020 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.service.credentials;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.HttpURLConnection;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.service.management.OperationResult;
import org.eclipse.hono.service.management.credentials.CommonCredential;
import org.eclipse.hono.service.management.credentials.CredentialsManagementService;
import org.eclipse.hono.service.management.credentials.PasswordCredential;
import org.eclipse.hono.service.management.credentials.PasswordSecret;
import org.eclipse.hono.service.management.credentials.PskSecret;
import org.eclipse.hono.service.management.device.Device;
import org.eclipse.hono.service.management.device.DeviceManagementService;
import org.eclipse.hono.util.CacheDirective;
import org.eclipse.hono.util.CredentialsConstants;
import org.eclipse.hono.util.CredentialsObject;
import org.eclipse.hono.util.CredentialsResult;
import org.eclipse.hono.util.RegistryManagementConstants;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.ThrowingConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opentracing.noop.NoopSpan;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.Checkpoint;
import io.vertx.junit5.VertxTestContext;
import io.vertx.junit5.VertxTestContext.ExecutionBlock;

/**
 * Abstract class used as a base for verifying behavior of {@link CredentialsService} and
 * {@link CredentialsManagementService} in device registry implementations.
 *
 */
public interface AbstractCredentialsServiceTest {

    Logger log = LoggerFactory.getLogger(AbstractCredentialsServiceTest.class);
    JsonObject CLIENT_CONTEXT = new JsonObject()
            .put("client-id", "some-client-identifier");

    /**
     * Gets credentials service being tested.
     * @return The credentials service
     */
    CredentialsService getCredentialsService();

    /**
     * Gets credentials service being tested.
     *
     * @return The credentials service
     */
    CredentialsManagementService getCredentialsManagementService();

    /**
     * Gets the device management service.
     * <p>
     * Return this device management service which is needed in order to work in coordination with the credentials
     * service.
     *
     * @return The device management service.
     */
    DeviceManagementService getDeviceManagementService();

    /**
     * Gets the cache directive that is supposed to be used for a given type of credentials.
     * <p>
     * This default implementation always returns {@code CacheDirective#noCacheDirective()}.
     *
     * @param credentialsType The type of credentials.
     * @return The expected cache directive.
     */
    default CacheDirective getExpectedCacheDirective(final String credentialsType) {
        return CacheDirective.noCacheDirective();
    }

    /**
     * Gets the information of this device registry implementation supports resource versions.
     * <p>
     * The default implementation of this method returns {@code true}. Other implementations may override this.
     *
     * @return {@code true} if the implementation supports resource versions, {@code false} otherwise.
     */
    default boolean supportsResourceVersion() {
        return true;
    }

    /**
     * Assert if the resource version is present.
     *
     * @param result The result to check.
     */
    default void assertResourceVersion(final OperationResult<?> result) {
        if (result == null) {
            return;
        }

        final var resourceVersion = result.getResourceVersion();
        assertNotNull(resourceVersion);

        if (result.isError()) {
            return;
        }

        if (!supportsResourceVersion()) {
            return;
        }

        assertTrue(resourceVersion.isPresent(), "Resource version missing");
    }

    /**
     * Verifies that the secrets of the credentials returned by
     * {@link CredentialsManagementService#readCredentials(String, String, io.opentracing.Span)}
     * contain a unique identifier but no confidential information.
     *
     * @param credentials The credentials to check.
     */
    default void assertReadCredentialsResponseProperties(final List<CommonCredential> credentials) {
        assertThat(credentials).isNotNull();
        credentials.forEach(creds -> {
            creds.getSecrets().forEach(secret -> {
                assertThat(secret.getId()).isNotNull();
                if (secret instanceof PasswordSecret) {
                    assertPasswordSecretDoesNotContainPasswordDetails((PasswordSecret) secret);
                } else if (secret instanceof PskSecret) {
                    assertThat(((PskSecret) secret).getKey())
                        .as("PSK secret does not contain shared key").isNull();
                }
            });
        });
    }

    /**
     * Verifies that a password secret does not contain a hash, salt nor hash function.
     *
     * @param secret The secret to check.
     */
    default void assertPasswordSecretDoesNotContainPasswordDetails(final PasswordSecret secret) {
        assertThat(secret.getPasswordHash()).as("password secret has no password hash").isNull();;
        assertThat(secret.getHashFunction()).as("password secret has no hash function").isNull();;
        assertThat(secret.getSalt()).as("password secret has no salt").isNull();
    }

    /**
     * Verifies that the response returned by the {@link CredentialsService#get(String, String, String)} method
     * contains the expected standard properties.
     *
     * @param response The response to check.
     * @param expectedDeviceId The expected device identifier.
     * @param expectedType The expected credentials type.
     * @param expectedAuthId The expected authentication identifier.
     */
    default void assertGetCredentialsResponseProperties(
            final JsonObject response,
            final String expectedDeviceId,
            final String expectedType,
            final String expectedAuthId) {
        assertThat(response).as("response is not empty").isNotNull();
        assertThat(response.getString(CredentialsConstants.FIELD_PAYLOAD_DEVICE_ID))
            .as("credentials contain expected device-id").isEqualTo(expectedDeviceId);
        assertThat(response.getString(CredentialsConstants.FIELD_TYPE))
            .as("credentials are of expected type").isEqualTo(expectedType);
        assertThat(response.getString(CredentialsConstants.FIELD_AUTH_ID))
            .as("credentials contain expected auth-id").isEqualTo(expectedAuthId);
    }

    /**
     * Verifies that a hashed-password secret returned by {@link CredentialsService#get(String, String, String)}
     * contains a hash, hash function, optional salt but no plaintext password.
     *
     * @param secret The secret to check.
     * @param expectSalt {@code true} if the credentials are expected to also contain a salt.
     */
    default void assertPwdSecretContainsHash(final JsonObject secret, final boolean expectSalt) {
        assertThat(secret).isNotNull();
        assertThat(secret.containsKey(RegistryManagementConstants.FIELD_SECRETS_PWD_PLAIN))
            .as("password secret does not contain plain text password").isFalse();
        assertThat(secret.containsKey(RegistryManagementConstants.FIELD_SECRETS_PWD_HASH))
            .as("password secret contains password hash").isTrue();
        assertThat(secret.containsKey(RegistryManagementConstants.FIELD_SECRETS_HASH_FUNCTION))
            .as("password secret contains hash function").isTrue();
        assertThat(secret.containsKey(RegistryManagementConstants.FIELD_SECRETS_SALT))
            .as("password secret does%s contain salt", expectSalt ? "" : " not").isEqualTo(expectSalt);
    }

    /**
     * Assert credentials, expect them to be missing.
     *
     * @param ctx The test context to report to.
     * @param tenantId The tenant to check for.
     * @param deviceId The device to check for.
     * @param authId The authentication id to check for.
     * @param type The credentials type to check for.
     * @param whenComplete Call when this assertion was successful.
     */
    default void assertGetMissing(
            final VertxTestContext ctx,
            final String tenantId,
            final String deviceId,
            final String authId,
            final String type,
            final ExecutionBlock whenComplete) {

        assertGet(ctx, tenantId, deviceId, authId, type,
                getCredentialsResult -> {
                    // the result of the get credentials operation does not need
                    // to be empty but it must not contain credentials of the given
                    // type and auth-id
                    final boolean containsMatchingCreds = Optional.ofNullable(getCredentialsResult.getPayload())
                            .map(list -> list.stream()
                                    .anyMatch(cred -> cred.getType().equals(type) && cred.getAuthId().equals(authId)))
                            .orElse(false);
                    assertFalse(containsMatchingCreds, String.format("device has credentials [type: %s, auth-id: %s]", type, authId));
                },
                r -> {
                    assertEquals(HttpURLConnection.HTTP_NOT_FOUND, r.getStatus());
                },
                whenComplete);
    }

    /**
     * Assert credentials, expect them to be present, but empty.
     *
     * @param ctx The test context to report to.
     * @param tenantId The tenant to check for.
     * @param deviceId The device to check for.
     * @param authId The authentication id to check for.
     * @param type The credentials type to check for.
     * @param whenComplete Call when this assertion was successful.
     */
    default void assertGetEmpty(final VertxTestContext ctx,
            final String tenantId, final String deviceId, final String authId, final String type,
            final ExecutionBlock whenComplete) {

        assertGet(ctx, tenantId, deviceId, authId, type,
                r -> {
                    assertEquals(HttpURLConnection.HTTP_OK, r.getStatus());
                    assertTrue(r.getPayload().isEmpty());
                },
                r -> {
                    assertEquals(HttpURLConnection.HTTP_NOT_FOUND, r.getStatus());
                },
                whenComplete);
    }

    /**
     * Assert credentials.
     *
     * @param ctx The test context to report to.
     * @param tenantId The tenant to check for.
     * @param deviceId The device to check for.
     * @param authId The authentication id to check for.
     * @param type The credentials type to check for.
     * @param mangementValidation The validation logic for the management data.
     * @param adapterValidation The validation logic for the protocol adapter data.
     * @param whenComplete Call when this assertion was successful.
     */
    default void assertGet(
            final VertxTestContext ctx,
            final String tenantId,
            final String deviceId,
            final String authId,
            final String type,
            final ThrowingConsumer<OperationResult<List<CommonCredential>>> mangementValidation,
            final ThrowingConsumer<CredentialsResult<JsonObject>> adapterValidation,
            final ExecutionBlock whenComplete) {

        getCredentialsManagementService().readCredentials(tenantId, deviceId, NoopSpan.INSTANCE)
                .onComplete(ctx.succeeding(s3 -> {
                    if (log.isTraceEnabled()) {
                        final String readResult = Optional.ofNullable(s3.getPayload())
                                .map(list -> list.stream()
                                            .map(c -> JsonObject.mapFrom(c))
                                            .collect(JsonArray::new, JsonArray::add, JsonArray::addAll))
                                .map(JsonArray::encodePrettily)
                                .orElse(null);
                            log.trace("read credentials [tenant: {}, device-id: {}] result: {}{}",
                                    tenantId, deviceId, System.lineSeparator(), readResult);
                    }

                    ctx.verify(() -> {

                        // assert a few basics, optionals may be empty
                        // but must not be null
                        assertNotNull(s3.getCacheDirective());
                        assertResourceVersion(s3);

                        mangementValidation.accept(s3);

                        getCredentialsService().get(
                                tenantId,
                                type,
                                authId,
                                NoopSpan.INSTANCE)
                                .onComplete(ctx.succeeding(s4 -> {
                                    if (log.isTraceEnabled()) {
                                        final String getResult = Optional.ofNullable(s4.getPayload())
                                                .map(JsonObject::encodePrettily)
                                                .orElse(null);
                                            log.trace("get credentials [tenant: {}, device-id: {}, auth-id: {}, type: {}] result: {}{}",
                                                    tenantId, deviceId, authId, type, System.lineSeparator(), getResult);
                                    }
                                    ctx.verify(() -> {

                                        adapterValidation.accept(s4);

                                        whenComplete.apply();
                                    });
                                }));

                    });
                }));

    }

    /**
     * Verifies that the service returns 404 if a client wants to retrieve non-existing credentials.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    default void testGetCredentialsFailsForNonExistingCredentials(final VertxTestContext ctx) {

        final var tenantId = "tenant";
        final var deviceId = UUID.randomUUID().toString();
        final var authId = UUID.randomUUID().toString();

        assertGetMissing(ctx, tenantId, deviceId, authId, CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD, ctx::completeNow);
    }

    /**
     * Verifies that the service returns credentials for an existing device.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    default void testGetCredentialsSucceedsForExistingDevice(final VertxTestContext ctx) {

        final var tenantId = "tenant";
        final var deviceId = UUID.randomUUID().toString();
        final var authId = UUID.randomUUID().toString();

        assertGetMissing(ctx, tenantId, deviceId, authId, CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD,
                () -> getDeviceManagementService()
                        .createDevice(tenantId, Optional.of(deviceId), new Device(), NoopSpan.INSTANCE)
                        .onComplete(ctx.succeeding(s -> assertGet(ctx, tenantId, deviceId, authId,
                                CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD,
                                r -> {
                                    assertEquals(HttpURLConnection.HTTP_OK, r.getStatus());
                                    assertNotNull(r.getPayload());
                                    assertTrue(r.getPayload().isEmpty());
                                },
                                r -> assertEquals(HttpURLConnection.HTTP_NOT_FOUND, r.getStatus()),
                                ctx::completeNow))));
    }

    /**
     * Verifies that the credentials set via
     * {@link CredentialsManagementService#updateCredentials(String, String, List, Optional, io.opentracing.Span)}
     * can be retrieved using
     * {@link CredentialsManagementService#readCredentials(String, String, io.opentracing.Span)} and
     * {@link CredentialsService#get(String, String, String)}.
     * Also checks that all secrets of the credentials have a unique identifier set.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    default void testUpdateCredentialsSucceeds(final VertxTestContext ctx) {

        final var tenantId = "tenant";
        final var deviceId = UUID.randomUUID().toString();
        final var authId = UUID.randomUUID().toString();
        final var pwdCredentials = Credentials.createPasswordCredential(authId, "bar");
        final var pskCredentials = Credentials.createPSKCredential(authId, "the-shared-key");

        //create device and set credentials.
        assertGetMissing(ctx, tenantId, deviceId, authId, CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD,
                () -> getDeviceManagementService()
                        .createDevice(tenantId, Optional.of(deviceId), new Device(), NoopSpan.INSTANCE)
                        .compose(response -> getCredentialsManagementService().updateCredentials(
                                tenantId,
                                deviceId,
                                List.of(pwdCredentials, pskCredentials),
                                Optional.empty(),
                                NoopSpan.INSTANCE))
                        .compose(response -> {
                            ctx.verify(() -> {
                                assertThat(response.getStatus()).isEqualTo(HttpURLConnection.HTTP_NO_CONTENT);
                                assertResourceVersion(response);
                            });
                            return getCredentialsManagementService().readCredentials(tenantId, deviceId, NoopSpan.INSTANCE);
                        })
                        .compose(response -> {
                            ctx.verify(() -> {
                                assertThat(response.getStatus()).isEqualTo(HttpURLConnection.HTTP_OK);
                                assertResourceVersion(response);
                                assertThat(response.getPayload()).hasSize(2);
                                assertReadCredentialsResponseProperties(response.getPayload());
                            });
                            return getCredentialsService().get(tenantId, CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD, authId);
                        })
                        .compose(response -> {
                            ctx.verify(() -> {
                                assertThat(response.getStatus()).isEqualTo(HttpURLConnection.HTTP_OK);
                                assertGetCredentialsResponseProperties(
                                        response.getPayload(),
                                        deviceId,
                                        CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD,
                                        authId);
                                final JsonArray secrets = response.getPayload().getJsonArray(CredentialsConstants.FIELD_SECRETS);
                                secrets.stream()
                                    .map(JsonObject.class::cast)
                                    .forEach(secret -> assertPwdSecretContainsHash(secret, false));
                            });
                            return getCredentialsService().get(tenantId, CredentialsConstants.SECRETS_TYPE_PRESHARED_KEY, authId);
                        })
                        .onComplete(ctx.succeeding(response -> {
                            ctx.verify(() -> {
                                assertThat(response.getStatus()).isEqualTo(HttpURLConnection.HTTP_OK);
                                assertGetCredentialsResponseProperties(
                                        response.getPayload(),
                                        deviceId,
                                        CredentialsConstants.SECRETS_TYPE_PRESHARED_KEY,
                                        authId);
                                final JsonArray secrets = response.getPayload().getJsonArray(CredentialsConstants.FIELD_SECRETS);
                                secrets.stream()
                                    .map(JsonObject.class::cast)
                                    .forEach(secret -> assertThat(secret.containsKey(CredentialsConstants.FIELD_SECRETS_KEY)));
                            });
                            ctx.completeNow();
                        })));
    }

    /**
     * Verifies that {@link CredentialsManagementService#updateCredentials(String, String, List, Optional, io.opentracing.Span)}
     * encodes plaintext passwords contained in hashed-password credentials.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    default void testUpdateCredentialsEncodesPlaintextPasswords(final VertxTestContext ctx) {

        final var tenantId = "tenant";
        final var deviceId = UUID.randomUUID().toString();
        final var authId = UUID.randomUUID().toString();
        final var pwdCredentials = Credentials.createPlainPasswordCredential(authId, "bar");

        //create device and set credentials.
        assertGetMissing(ctx, tenantId, deviceId, authId, RegistryManagementConstants.SECRETS_TYPE_HASHED_PASSWORD,
                () -> getDeviceManagementService()
                        .createDevice(tenantId, Optional.of(deviceId), new Device(), NoopSpan.INSTANCE)
                        .compose(response -> getCredentialsManagementService().updateCredentials(
                                tenantId,
                                deviceId,
                                List.of(pwdCredentials),
                                Optional.empty(),
                                NoopSpan.INSTANCE))
                        .onComplete(ctx.succeeding(response -> ctx.verify(() -> {

                            assertEquals(HttpURLConnection.HTTP_NO_CONTENT, response.getStatus());
                            assertResourceVersion(response);

                            assertGet(ctx, tenantId, deviceId, authId,
                                    RegistryManagementConstants.SECRETS_TYPE_HASHED_PASSWORD,
                                    r -> {
                                        assertThat(r.getStatus()).isEqualTo(HttpURLConnection.HTTP_OK);
                                        final List<CommonCredential> credentials = r.getPayload();
                                        assertEquals(1, credentials.size());
                                        final List<PasswordSecret> secrets = ((PasswordCredential) credentials
                                                .get(0)).getSecrets();
                                        assertEquals(1, secrets.size());
                                        assertNotNull(secrets.get(0).getId());
                                        assertPasswordSecretDoesNotContainPasswordDetails(secrets.get(0));
                                        assertNull(secrets.get(0).getPasswordPlain());
                                    },
                                    r -> {
                                        assertThat(r.getStatus()).isEqualTo(HttpURLConnection.HTTP_OK);
                                        assertGetCredentialsResponseProperties(
                                                r.getPayload(),
                                                deviceId,
                                                CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD,
                                                authId);
                                        final JsonArray secrets = r.getPayload().getJsonArray(CredentialsConstants.FIELD_SECRETS);
                                        secrets.stream()
                                            .map(JsonObject.class::cast)
                                            .forEach(secret -> assertPwdSecretContainsHash(secret, false));
                                    },
                                    ctx::completeNow);
                        }))));
    }

    /**
     * Verifies that {@link CredentialsManagementService#updateCredentials(String, String, List, Optional, io.opentracing.Span)}
     * replaces existing credentials with credentials having the same type and auth-id.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    default void testUpdateCredentialsReplacesCredentialsOfSameTypeAndAuthId(final VertxTestContext ctx) {

        final var tenantId = "tenant";
        final var deviceId = UUID.randomUUID().toString();
        final var authId = UUID.randomUUID().toString();
        final var initialCredentials = Credentials.createPasswordCredential(authId, "bar");

        final Checkpoint assertionsSucceeded = ctx.checkpoint(1);

        final Promise<?> phase1 = Promise.promise();

        // phase 1 - create device and set initial password credentials

        assertGetMissing(ctx, tenantId, deviceId, authId, CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD,
                () -> getDeviceManagementService()
                        .createDevice(tenantId, Optional.of(deviceId), new Device(), NoopSpan.INSTANCE)
                        .onComplete(ctx.succeeding(s -> getCredentialsManagementService()
                                .updateCredentials(tenantId, deviceId, Collections.singletonList(initialCredentials),
                                        Optional.empty(), NoopSpan.INSTANCE)
                                .onComplete(ctx.succeeding(s2 -> ctx.verify(() -> {

                                    assertResourceVersion(s2);
                                    assertEquals(HttpURLConnection.HTTP_NO_CONTENT, s2.getStatus());

                                    assertGet(ctx, tenantId, deviceId, authId,
                                            CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD,
                                            r -> assertEquals(HttpURLConnection.HTTP_OK, r.getStatus()),
                                            r -> assertEquals(HttpURLConnection.HTTP_OK, r.getStatus()),
                                            phase1::complete);

                                }))))));

        // phase 2 - try to update

        phase1.future().compose(v -> {

            final var newCredentials = Credentials.createPasswordCredential(authId, "baz");

            return getCredentialsManagementService().updateCredentials(tenantId, deviceId,
                    Collections.singletonList(newCredentials), Optional.empty(),
                    NoopSpan.INSTANCE);
        })
        .onComplete(ctx.succeeding(s -> ctx.verify(() -> {

            assertEquals(HttpURLConnection.HTTP_NO_CONTENT, s.getStatus());

            assertGet(ctx, tenantId, deviceId, authId, CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD,
                    r -> {
                        assertEquals(HttpURLConnection.HTTP_OK, r.getStatus());
                        assertThat(r.getPayload()).hasSize(1);
                    },
                    r -> {
                        assertEquals(HttpURLConnection.HTTP_OK, r.getStatus());
                        assertGetCredentialsResponseProperties(r.getPayload(), deviceId, CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD, authId);
                    },
                    assertionsSucceeded::flag);
        })));
    }

    /**
     * Verifies that {@link CredentialsManagementService#updateCredentials(String, String, List, Optional, io.opentracing.Span)}
     * returns a 412 status code if the request contains a resource version that doesn't match the
     * version of the credentials on record.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    default void testUpdateCredentialsFailsForWrongResourceVersion(final VertxTestContext ctx) {
        final var tenantId = "tenant";
        final var deviceId = UUID.randomUUID().toString();
        final var authId = UUID.randomUUID().toString();
        final var pwdCredentials = Credentials.createPasswordCredential(authId, "bar");

        final Checkpoint checkpoint = ctx.checkpoint(3);

        // phase 1 - create device

        final Promise<?> phase1 = Promise.promise();

        getDeviceManagementService()
                .createDevice(tenantId, Optional.of(deviceId), new Device(), NoopSpan.INSTANCE)
                .onComplete(ctx.succeeding(s2 -> {
                    checkpoint.flag();
                    phase1.complete();
                }));

        // phase 2 - set credentials

        final Promise<String> phase2 = Promise.promise();

        phase1.future().onSuccess(s1 -> {

                getCredentialsManagementService().updateCredentials(tenantId, deviceId,
                        List.of(pwdCredentials), Optional.empty(), NoopSpan.INSTANCE)
                        .onComplete(ctx.succeeding(s2 -> {
                            ctx.verify(() -> {
                                assertEquals(HttpURLConnection.HTTP_NO_CONTENT, s2.getStatus());
                                assertResourceVersion(s2);
                            });
                            checkpoint.flag();
                            phase2.complete(s2.getResourceVersion().get());
                        }));

        });

        // phase 3 - update with wrong version

        phase2.future()
            .compose(resourceVersion -> getCredentialsManagementService().updateCredentials(
                    tenantId,
                    deviceId,
                    List.of(pwdCredentials),
                    Optional.of("other_" + resourceVersion),
                    NoopSpan.INSTANCE))
            .onComplete(ctx.succeeding(s -> ctx.verify(() -> {
                assertEquals(HttpURLConnection.HTTP_PRECON_FAILED, s.getStatus());
                checkpoint.flag();
            })));
    }

    /**
     * Test updating a new secret.
     * Also verifies that removing a device deletes the attached credentials.
     * @param ctx The vert.x test context.
     */
    @Test
    default void testCreateAndDeletePasswordSecret(final VertxTestContext ctx) {

        final var tenantId = "tenant";
        final var deviceId = UUID.randomUUID().toString();
        final var authId = UUID.randomUUID().toString();
        final var secret = Credentials.createPasswordCredential(authId, "bar");

        final Checkpoint checkpoint = ctx.checkpoint(7);

        // phase 1 - check missing

        final Promise<?> phase1 = Promise.promise();

        assertGetMissing(ctx, tenantId, deviceId, authId, CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD,
                phase1::complete);

        // phase 2 - create device

        final Promise<?> phase2 = Promise.promise();

        phase1.future().onComplete(ctx.succeeding(s1 -> {

            checkpoint.flag();
            getDeviceManagementService()
                    .createDevice(tenantId, Optional.of(deviceId), new Device(), NoopSpan.INSTANCE)
                    .onComplete(ctx.succeeding(s2 -> {
                        checkpoint.flag();
                        phase2.complete();
                    }));

        }));

        // phase 3 - initially set credentials

        final Promise<?> phase3 = Promise.promise();

        phase2.future().onComplete(ctx.succeeding(s1 -> {

            assertGetEmpty(ctx, tenantId, deviceId, authId, CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD,
                    () -> getCredentialsManagementService().updateCredentials(tenantId, deviceId,
                            Collections.singletonList(secret), Optional.empty(),
                            NoopSpan.INSTANCE)
                            .onComplete(ctx.succeeding(s2 -> {

                                checkpoint.flag();

                                ctx.verify(() -> {

                                    assertEquals(HttpURLConnection.HTTP_NO_CONTENT, s2.getStatus());

                                    assertGet(ctx, tenantId, deviceId, authId,
                                            CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD,
                                            r -> {
                                                assertEquals(HttpURLConnection.HTTP_OK, r.getStatus());
                                            },
                                            r -> {
                                                assertEquals(HttpURLConnection.HTTP_OK, r.getStatus());
                                            },
                                            () -> {
                                                checkpoint.flag();
                                                phase3.complete();
                                            });

                                });

                            })));

        }));

        // Phase 4 verifies that when the device is deleted, the corresponding credentials are deleted as well.

        final Promise<?> phase4 = Promise.promise();

        phase3.future().onComplete(ctx.succeeding(
                v -> getDeviceManagementService()
                        .deleteDevice(tenantId, deviceId, Optional.empty(), NoopSpan.INSTANCE)
                        .onComplete(
                                ctx.succeeding(s -> ctx.verify(() -> assertGetMissing(ctx, tenantId, deviceId, authId,
                                        CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD, () -> {
                                            checkpoint.flag();
                                            phase4.complete();
                                        }))))));

        // complete

        phase4.future().onComplete(ctx.succeeding(s -> ctx.completeNow()));

    }

    /**
     * Test fetching disabled credentials for the protocol adapter.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    default void testDisableCredentials(final VertxTestContext ctx) {

        final String tenantId = UUID.randomUUID().toString();
        final String deviceId = UUID.randomUUID().toString();
        final String authId = UUID.randomUUID().toString();

        final CommonCredential credential = Credentials.createPasswordCredential(authId, "bar");
        final CommonCredential disabledCredential = Credentials.createPSKCredential(authId, "baz");
        disabledCredential.setEnabled(false);

        final List<CommonCredential> credentials = Arrays.asList(credential, disabledCredential);

        // create device & set credentials

        final Promise<?> phase1 = Promise.promise();

        getDeviceManagementService()
                .createDevice(tenantId, Optional.of(deviceId), new Device(), NoopSpan.INSTANCE)
                .onComplete(ctx.succeeding(n -> getCredentialsManagementService()
                        .updateCredentials(tenantId, deviceId, credentials, Optional.empty(), NoopSpan.INSTANCE)
                        .onComplete(ctx.succeeding(s -> phase1.complete()))));

        // validate credentials - enabled

        final Promise<?> phase2 = Promise.promise();

        phase1.future()
                .onComplete(ctx.succeeding(n -> getCredentialsService()
                        .get(tenantId, CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD, authId)
                        .onComplete(ctx.succeeding(s -> ctx.verify(() -> {

                            assertEquals(HttpURLConnection.HTTP_OK, s.getStatus());

                            final CredentialsObject creds = s.getPayload().mapTo(CredentialsObject.class);

                            assertEquals(authId, creds.getAuthId());
                            assertEquals(CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD, creds.getType());
                            assertEquals(1, creds.getSecrets().size());

                            phase2.complete();
                        })))));

        // validate credentials - disabled

        final Promise<?> phase3 = Promise.promise();

        phase2.future().onComplete(ctx.succeeding(n -> {
            getCredentialsService().get(tenantId, CredentialsConstants.SECRETS_TYPE_PRESHARED_KEY, authId)
                    .onComplete(ctx.succeeding(s -> ctx.verify(() -> {

                        assertEquals(HttpURLConnection.HTTP_NOT_FOUND, s.getStatus());

                        phase3.complete();
                    })));
        }));

        // finally complete

        phase3.future().onComplete(ctx.succeeding(s -> ctx.completeNow()));
    }

    /**
     * Verify that updating a secret using a wrong secret ID fails with a `400 Bad Request`.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    default void testUpdateSecretFailsWithWrongSecretId(final VertxTestContext ctx) {

        final String tenantId = UUID.randomUUID().toString();
        final String deviceId = UUID.randomUUID().toString();
        final String authId = UUID.randomUUID().toString();

        final CommonCredential credential = Credentials.createPasswordCredential(authId, "bar");

        // create device & set credentials

        final Promise<?> phase1 = Promise.promise();

        getDeviceManagementService()
                .createDevice(tenantId, Optional.of(deviceId), new Device(), NoopSpan.INSTANCE)
                .onComplete(ctx.succeeding(n -> getCredentialsManagementService()
                .updateCredentials(tenantId, deviceId, List.of(credential), Optional.empty(), NoopSpan.INSTANCE)
                .onComplete(ctx.succeeding(s -> phase1.complete()))));

        // re-set credentials with wrong ID
        final Promise<?> phase2 = Promise.promise();

        // Change the password
        final CommonCredential newCredential = Credentials.createPasswordCredential(authId, "foo");
        ((PasswordCredential) newCredential).getSecrets().get(0).setId("randomId");

        phase1.future().onComplete(ctx.succeeding(n -> getCredentialsManagementService()
                .updateCredentials(tenantId, deviceId, Collections.singletonList(newCredential), Optional.empty(),
                        NoopSpan.INSTANCE)
                    .onComplete(ctx.succeeding(s -> ctx.verify(() -> {

                        assertEquals(HttpURLConnection.HTTP_BAD_REQUEST, s.getStatus());
                        phase2.complete();
                    })))));

        // finally complete

        phase2.future().onComplete(ctx.succeeding(s -> ctx.completeNow()));
    }

    /**
     * Verifies that existing secrets are deleted when their IDs are not contained
     * in the request payload.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    default void testUpdateCredentialsSupportsRemovingExistingSecrets(final VertxTestContext ctx) {

        final String tenantId = UUID.randomUUID().toString();
        final String deviceId = UUID.randomUUID().toString();
        final String authId = UUID.randomUUID().toString();

        final PasswordSecret sec1 = new PasswordSecret().setPasswordPlain("bar");
        final PasswordSecret sec2 = new PasswordSecret().setPasswordPlain("foo");

        final PasswordCredential credential = new PasswordCredential(authId, List.of(sec1, sec2));

        // create device & set credentials

        final Promise<?> phase1 = Promise.promise();

        getDeviceManagementService()
                .createDevice(tenantId, Optional.of(deviceId), new Device(), NoopSpan.INSTANCE)
                .onComplete(ctx.succeeding(n -> getCredentialsManagementService()
                        .updateCredentials(tenantId, deviceId, Collections.singletonList(credential),
                                Optional.empty(),
                                NoopSpan.INSTANCE)
                        .onComplete(ctx.succeeding(s -> phase1.complete()))));

        // Retrieve credentials IDs

        final Promise<?> phase2 = Promise.promise();
        final List<String> secretIDs = new ArrayList<>();

        phase1.future()
                .onComplete(ctx.succeeding(n -> getCredentialsService()
                        .get(tenantId, CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD, authId)
                        .onComplete(ctx.succeeding(s -> ctx.verify(() -> {

                            assertEquals(HttpURLConnection.HTTP_OK, s.getStatus());

                            final CredentialsObject creds = s.getPayload().mapTo(CredentialsObject.class);

                            assertEquals(authId, creds.getAuthId());
                            assertEquals(CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD, creds.getType());
                            assertEquals(2, creds.getSecrets().size());

                            for (Object secret : creds.getSecrets()) {
                                final String id = ((JsonObject) secret).getString(RegistryManagementConstants.FIELD_ID);
                                assertNotNull(id);
                                secretIDs.add(id);
                            }

                            phase2.complete();
                        })))));

        // re-set credentials
        final Promise<?> phase3 = Promise.promise();

        phase2.future().onComplete(ctx.succeeding(n -> {
            // create a credential object with only one of the ID.
            final PasswordSecret secretWithOnlyId = new PasswordSecret();
            secretWithOnlyId.setId(secretIDs.get(0));

            final PasswordCredential credentialWithOnlyId = new PasswordCredential(authId, List.of(secretWithOnlyId));

            getCredentialsManagementService()
                    .updateCredentials(tenantId, deviceId, Collections.singletonList(credentialWithOnlyId),
                            Optional.empty(), NoopSpan.INSTANCE)
                    .onComplete(ctx.succeeding(s -> phase3.complete()));
        }));


        // Retrieve credentials again, one should be deleted.

        final Promise<?> phase4 = Promise.promise();

        phase3.future()
                .onComplete(ctx.succeeding(n -> getCredentialsService()
                        .get(tenantId, CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD, authId)
                        .onComplete(ctx.succeeding(s -> ctx.verify(() -> {

                            assertEquals(HttpURLConnection.HTTP_OK, s.getStatus());

                            final CredentialsObject creds = s.getPayload().mapTo(CredentialsObject.class);

                            assertEquals(authId, creds.getAuthId());
                            assertEquals(CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD, creds.getType());
                            assertEquals(1, creds.getSecrets().size());

                            final String id = creds.getSecrets().getJsonObject(0)
                                    .getString(RegistryManagementConstants.FIELD_ID);
                            assertEquals(id, secretIDs.get(0));

                            phase4.complete();
                        })))));

        // finally complete

        phase4.future().onComplete(ctx.succeeding(s -> ctx.completeNow()));
    }

    /**
     * Verifies that a credential's existing secrets can be updated using their IDs.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    default void testUpdateCredentialsSupportsUpdatingExistingSecrets(final VertxTestContext ctx) {

        final String tenantId = UUID.randomUUID().toString();
        final String deviceId = UUID.randomUUID().toString();
        final String authId = UUID.randomUUID().toString();

        final PasswordCredential origCredential = Credentials.createPasswordCredential(authId, "orig-password");

        final PasswordSecret origSecret = origCredential.getSecrets().get(0);
        origSecret.setNotAfter(Instant.now().plus(1, ChronoUnit.DAYS));

        // create device & set initial credentials

        final Promise<?> phase1 = Promise.promise();

        getDeviceManagementService().createDevice(tenantId, Optional.of(deviceId), new Device(), NoopSpan.INSTANCE)
                .compose(n -> getCredentialsManagementService().updateCredentials(
                        tenantId,
                        deviceId,
                        List.of(origCredential),
                        Optional.empty(),
                        NoopSpan.INSTANCE))
                .onComplete(ctx.succeeding(s -> phase1.complete()));

        // Retrieve credentials IDs

        final Promise<PasswordCredential> phase2 = Promise.promise();
        final AtomicReference<String> pwdSecretId = new AtomicReference<>();

        phase1.future()
                .compose(n -> getCredentialsManagementService().readCredentials(tenantId, deviceId, NoopSpan.INSTANCE))
                .onComplete(ctx.succeeding(s -> {
                    ctx.verify(() -> {

                        assertThat(s.getStatus()).isEqualTo(HttpURLConnection.HTTP_OK);
                        assertThat(s.getPayload()).hasSize(1);
                        final CommonCredential creds = s.getPayload().get(0);

                        assertThat(creds.getAuthId()).isEqualTo(authId);
                        assertThat(creds).isInstanceOf(PasswordCredential.class);
                        assertThat(creds.getSecrets()).as("password credentials contain a single secret").hasSize(1);

                        final String id = creds.getSecrets().get(0).getId();
                        assertThat(id).as("password secret has ID").isNotNull();
                        pwdSecretId.set(id);

                        // change the password of the existing secret (using its secret ID)
                        final PasswordSecret changedSecret = new PasswordSecret();
                        changedSecret.setId(id);
                        changedSecret.setPasswordPlain("changed-password");
                        changedSecret.setNotAfter(origSecret.getNotAfter());
                        // and add another password without an ID
                        final PasswordSecret newSecret = new PasswordSecret();
                        newSecret.setPasswordPlain("future-password");
                        newSecret.setNotBefore(origSecret.getNotAfter());
                        final PasswordCredential updatedCredential = new PasswordCredential(authId, List.of(changedSecret, newSecret));
                        phase2.complete(updatedCredential);
                    });
               }));

        // update credentials, including additional PSK credentials
        final Promise<?> phase3 = Promise.promise();

        phase2.future()
            .compose(updatedCredential -> getCredentialsManagementService().updateCredentials(
                    tenantId,
                    deviceId,
                    List.of(updatedCredential, Credentials.createPSKCredential(authId, "shared-key")),
                    Optional.empty(),
                    NoopSpan.INSTANCE))
            .onComplete(ctx.succeeding(s -> phase3.complete()));

        // Retrieve credentials again, the updated secret's ID should remain the same but the password hash should have changed.
        phase3.future()
            .onSuccess(ok -> assertGet(
                    ctx,
                    tenantId,
                    deviceId,
                    authId,
                    CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD,
                    readCredentialsResult -> {
                        assertThat(readCredentialsResult.getStatus()).isEqualTo(HttpURLConnection.HTTP_OK);
                        assertThat(readCredentialsResult.getPayload()).hasSize(2);
                        assertReadCredentialsResponseProperties(readCredentialsResult.getPayload());
                        final PasswordCredential pwdCreds = readCredentialsResult.getPayload()
                                .stream()
                                .filter(PasswordCredential.class::isInstance)
                                .map(PasswordCredential.class::cast)
                                .findAny()
                                .orElse(null);
                        assertThat(pwdCreds).isNotNull();
                        assertThat(pwdCreds.getSecrets()).hasSize(2);
                        final boolean containsPasswordSecretWithOriginalId = pwdCreds.getSecrets()
                                .stream()
                                .map(PasswordSecret::getId)
                                .anyMatch(pwdSecretId.get()::equals);
                        assertThat(containsPasswordSecretWithOriginalId);
                    },
                    getCredentialsResult -> {
                        assertThat(getCredentialsResult.getStatus()).isEqualTo(HttpURLConnection.HTTP_OK);
                        final CredentialsObject credentialsObj = getCredentialsResult.getPayload().mapTo(CredentialsObject.class);
                        assertThat(credentialsObj.getSecrets()).hasSize(2);
                        // verify that none of the secrets contains the original password hash anymore
                        credentialsObj.getSecrets()
                            .stream()
                            .map(JsonObject.class::cast)
                            .forEach(secret -> {
                                assertThat(secret.getString(CredentialsConstants.FIELD_SECRETS_PWD_HASH))
                                    .isNotEqualTo(origSecret.getPasswordHash());
                            });
                    },
                    ctx::completeNow));

    }

    /**
     * Verify that the metadata of a secret can be updated without changing the secret id.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    default void testSecretMetadataUpdateDoesntChangeSecretID(final VertxTestContext ctx) {

        final String tenantId = UUID.randomUUID().toString();
        final String deviceId = UUID.randomUUID().toString();
        final String authId = UUID.randomUUID().toString();

        final CommonCredential credential = Credentials.createPasswordCredential(authId, "bar");

        final List<CommonCredential> credentials = Collections.singletonList(credential);

        // create device & set credentials

        final Promise<?> phase1 = Promise.promise();

        getDeviceManagementService()
                .createDevice(tenantId, Optional.of(deviceId), new Device(), NoopSpan.INSTANCE)
                .onComplete(ctx.succeeding(n -> getCredentialsManagementService()
                        .updateCredentials(tenantId, deviceId, credentials, Optional.empty(), NoopSpan.INSTANCE)
                        .onComplete(ctx.succeeding(s -> phase1.complete()))));

        // Retrieve credential ID

        final Promise<?> phase2 = Promise.promise();
        final List<String> secretIDs = new ArrayList<>();

        phase1.future()
                .onComplete(ctx.succeeding(n -> getCredentialsService()
                        .get(tenantId, CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD, authId)
                        .onComplete(ctx.succeeding(s -> ctx.verify(() -> {

                            assertEquals(HttpURLConnection.HTTP_OK, s.getStatus());

                            final CredentialsObject creds = s.getPayload().mapTo(CredentialsObject.class);

                            assertEquals(authId, creds.getAuthId());
                            assertEquals(CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD, creds.getType());
                            assertEquals(1, creds.getSecrets().size());

                            final String id = (creds.getSecrets().getJsonObject(0))
                                    .getString(RegistryManagementConstants.FIELD_ID);
                            assertNotNull(id);
                            secretIDs.add(id);

                            phase2.complete();
                        })))));

        // re-set credentials
        final Promise<?> phase3 = Promise.promise();

        phase2.future().onComplete(ctx.succeeding(n -> {
            // Add some metadata to the secret
            final PasswordSecret secretWithOnlyIdAndMetadata = new PasswordSecret();
            secretWithOnlyIdAndMetadata.setId(secretIDs.get(0));
            secretWithOnlyIdAndMetadata.setComment("secret comment");

            final PasswordCredential credentialWithMetadataUpdate = new PasswordCredential(authId, List.of(secretWithOnlyIdAndMetadata));

            getCredentialsManagementService()
                    .updateCredentials(tenantId, deviceId, Collections.singletonList(credentialWithMetadataUpdate),
                            Optional.empty(), NoopSpan.INSTANCE)
                    .onComplete(ctx.succeeding(s -> phase3.complete()));
        }));


        // Retrieve credentials again, the ID should not have changed.

        final Promise<?> phase4 = Promise.promise();

        phase3.future()
                .onComplete(ctx.succeeding(n -> getCredentialsService()
                        .get(tenantId, CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD, authId)
                        .onComplete(ctx.succeeding(s -> ctx.verify(() -> {

                            assertEquals(HttpURLConnection.HTTP_OK, s.getStatus());

                            final CredentialsObject creds = s.getPayload().mapTo(CredentialsObject.class);

                            assertEquals(authId, creds.getAuthId());
                            assertEquals(CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD, creds.getType());
                            assertEquals(1, creds.getSecrets().size());

                            final String id = creds.getSecrets().getJsonObject(0)
                                    .getString(RegistryManagementConstants.FIELD_ID);
                            assertEquals(id, secretIDs.get(0));

                            final String comment = creds.getSecrets().getJsonObject(0)
                                    .getString(RegistryManagementConstants.FIELD_SECRETS_COMMENT);
                            assertEquals("secret comment", comment);

                            phase4.complete();
                        })))));

        // finally complete

        phase4.future().onComplete(ctx.succeeding(s -> ctx.completeNow()));
    }


    /**
     * Verify deleting some metadata to a secret.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    default void testSecretMetadataDeletion(final VertxTestContext ctx) {

        final String tenantId = UUID.randomUUID().toString();
        final String deviceId = UUID.randomUUID().toString();
        final String authId = UUID.randomUUID().toString();

        final CommonCredential credential = Credentials.createPasswordCredential(authId, "bar");
        ((PasswordCredential) credential).getSecrets().get(0).setNotBefore(new Date().toInstant());

        final List<CommonCredential> credentials = Collections.singletonList(credential);

        // create device & set credentials

        final Promise<?> phase1 = Promise.promise();

        getDeviceManagementService().createDevice(tenantId, Optional.of(deviceId), new Device(), NoopSpan.INSTANCE)
                .onComplete(ctx.succeeding(n -> getCredentialsManagementService()
                        .updateCredentials(tenantId, deviceId, credentials, Optional.empty(), NoopSpan.INSTANCE)
                        .onComplete(ctx.succeeding(s -> phase1.complete()))));

        // Retrieve credential ID

        final Promise<?> phase2 = Promise.promise();
        final List<String> secretIDs = new ArrayList<>();

        phase1.future()
                .onComplete(ctx.succeeding(n -> getCredentialsService()
                        .get(tenantId, CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD, authId)
                        .onComplete(ctx.succeeding(s -> ctx.verify(() -> {

                            assertEquals(HttpURLConnection.HTTP_OK, s.getStatus());

                            final CredentialsObject creds = s.getPayload().mapTo(CredentialsObject.class);

                            assertEquals(authId, creds.getAuthId());
                            assertEquals(CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD, creds.getType());
                            assertEquals(1, creds.getSecrets().size());

                            final String id = (creds.getSecrets().getJsonObject(0))
                                    .getString(RegistryManagementConstants.FIELD_ID);
                            assertNotNull(id);
                            secretIDs.add(id);
                            final String notBefore = (creds.getSecrets().getJsonObject(0))
                                    .getString(RegistryManagementConstants.FIELD_SECRETS_NOT_BEFORE);
                            assertNotNull(notBefore);

                            phase2.complete();
                        })))));

        // re-set credentials
        final Promise<?> phase3 = Promise.promise();

        phase2.future().onComplete(ctx.succeeding(n -> {
            // Add some other metadata to the secret
            final PasswordSecret secretWithOnlyIdAndMetadata = new PasswordSecret();
            secretWithOnlyIdAndMetadata.setId(secretIDs.get(0));
            secretWithOnlyIdAndMetadata.setComment("secret comment");

            final PasswordCredential credentialWithMetadataUpdate = new PasswordCredential(authId, List.of(secretWithOnlyIdAndMetadata));

            getCredentialsManagementService()
                    .updateCredentials(tenantId, deviceId, Collections.singletonList(credentialWithMetadataUpdate),
                            Optional.empty(), NoopSpan.INSTANCE)
                    .onComplete(ctx.succeeding(s -> phase3.complete()));
        }));


        // Retrieve credentials again, both metadata should be there

        final Promise<?> phase4 = Promise.promise();

        phase3.future()
                .onComplete(ctx.succeeding(n -> getCredentialsService()
                        .get(tenantId, CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD, authId)
                        .onComplete(ctx.succeeding(s -> ctx.verify(() -> {

                            assertEquals(HttpURLConnection.HTTP_OK, s.getStatus());

                            final CredentialsObject creds = s.getPayload().mapTo(CredentialsObject.class);

                            assertEquals(authId, creds.getAuthId());
                            assertEquals(CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD, creds.getType());
                            assertEquals(1, creds.getSecrets().size());

                            final String id = creds.getSecrets().getJsonObject(0)
                                    .getString(RegistryManagementConstants.FIELD_ID);
                            assertEquals(id, secretIDs.get(0));

                            final String comment = creds.getSecrets().getJsonObject(0)
                                    .getString(RegistryManagementConstants.FIELD_SECRETS_COMMENT);
                            assertEquals("secret comment", comment);

                            final String notBefore = (creds.getSecrets().getJsonObject(0))
                                    .getString(RegistryManagementConstants.FIELD_SECRETS_NOT_BEFORE);
                            assertNull(notBefore);

                            phase4.complete();
                        })))));

        // finally complete

        phase4.future().onComplete(ctx.succeeding(s -> ctx.completeNow()));
    }

    /**
     * Registers a set of credentials for a device.
     *
     * @param svc The service to register the credentials with.
     * @param tenantId The tenant that the device belongs to.
     * @param deviceId The ID of the device.
     * @param secrets The secrets to register.
     * @return A succeeded future if the credentials have been registered successfully.
     */
    default Future<OperationResult<Void>> setCredentials(
            final CredentialsManagementService svc,
            final String tenantId,
            final String deviceId,
            final List<CommonCredential> secrets) {

        return svc.updateCredentials(tenantId, deviceId, secrets, Optional.empty(), NoopSpan.INSTANCE)
                .compose(r -> {
                    if (HttpURLConnection.HTTP_NO_CONTENT == r.getStatus()) {
                        return Future.succeededFuture(r);
                    } else {
                        return Future.failedFuture(new ClientErrorException(r.getStatus()));
                    }
                });
    }

    /**
     * Verifies that credentials of a particular type are registered.
     *
     * @param svc The credentials service to probe.
     * @param tenant The tenant that the device belongs to.
     * @param authId The authentication identifier used by the device.
     * @param type The type of credentials.
     * @return A succeeded future if the credentials exist.
     */
    default Future<Void> assertRegistered(
            final CredentialsService svc,
            final String tenant,
            final String authId,
            final String type) {

        return assertGet(svc, tenant, authId, type, HttpURLConnection.HTTP_OK);
    }

    /**
     * Verifies that credentials of a particular type are not registered.
     *
     * @param svc The credentials service to probe.
     * @param tenant The tenant that the device belongs to.
     * @param authId The authentication identifier used by the device.
     * @param type The type of credentials.
     * @return A succeeded future if the credentials do not exist.
     */
    default Future<Void> assertNotRegistered(
            final CredentialsService svc,
            final String tenant,
            final String authId,
            final String type) {

        return assertGet(svc, tenant, authId, type, HttpURLConnection.HTTP_NOT_FOUND);
    }

    /**
     * Asserts a get operation.
     *
     * @param svc The service to use.
     * @param tenant The ID of the tenant.
     * @param authId The auth ID.
     * @param type The type.
     * @param expectedStatusCode The expected status code.
     *
     * @return A future, tracking the outcome of the operation.
     * @throws ClientErrorException reported by the future if the check fails
     */
    private static Future<Void> assertGet(
            final CredentialsService svc,
            final String tenant,
            final String authId,
            final String type,
            final int expectedStatusCode) {

        return svc.get(tenant, type, authId)
                .map(r -> {
                    if (r.getStatus() == expectedStatusCode) {
                        return null;
                    } else {
                        throw new ClientErrorException(HttpURLConnection.HTTP_PRECON_FAILED);
                    }
                });
    }

}
