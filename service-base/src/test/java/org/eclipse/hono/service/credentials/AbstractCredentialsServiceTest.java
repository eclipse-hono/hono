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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

import org.eclipse.hono.auth.EncodedPassword;
import org.eclipse.hono.auth.SpringBasedHonoPasswordEncoder;
import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.service.management.OperationResult;
import org.eclipse.hono.service.management.credentials.CommonCredential;
import org.eclipse.hono.service.management.credentials.CredentialsManagementService;
import org.eclipse.hono.service.management.credentials.PasswordCredential;
import org.eclipse.hono.service.management.credentials.PasswordSecret;
import org.eclipse.hono.service.management.credentials.PskCredential;
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

import io.opentracing.noop.NoopSpan;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.Checkpoint;
import io.vertx.junit5.VertxTestContext;
import io.vertx.junit5.VertxTestContext.ExecutionBlock;

/**
 * Abstract class used as a base for verifying behavior of {@link CredentialsService} and
 * {@link CredentialsManagementService} in device registry implementations.
 *
 */
public abstract class AbstractCredentialsServiceTest {

    protected static final JsonObject CLIENT_CONTEXT = new JsonObject()
            .put("client-id", "some-client-identifier");

    /**
     * Gets credentials service being tested.
     * @return The credentials service
     */
    public abstract CredentialsService getCredentialsService();

    /**
     * Gets credentials service being tested.
     *
     * @return The credentials service
     */
    public abstract CredentialsManagementService getCredentialsManagementService();

    /**
     * Gets the device management service.
     * <p>
     * Return this device management service which is needed in order to work in coordination with the credentials
     * service.
     *
     * @return The device management service.
     */
    public abstract DeviceManagementService getDeviceManagementService();

    /**
     * Gets the cache directive that is supposed to be used for a given type of credentials.
     * <p>
     * This default implementation always returns {@code CacheDirective#noCacheDirective()}.
     *
     * @param credentialsType The type of credentials.
     * @return The expected cache directive.
     */
    protected CacheDirective getExpectedCacheDirective(final String credentialsType) {
        return CacheDirective.noCacheDirective();
    }

    /**
     * Gets the information of this device registry implementation supports resource versions.
     * <p>
     * The default implementation of this method returns {@code true}. Other implementations may override this.
     *
     * @return {@code true} if the implementation supports resource versions, {@code false} otherwise.
     */
    protected boolean supportsResourceVersion() {
        return true;
    }

    /**
     * Verifies that the service returns 404 if a client wants to retrieve non-existing credentials.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testGetCredentialsFailsForNonExistingCredentials(final VertxTestContext ctx) {

        final var tenantId = "tenant";
        final var deviceId = UUID.randomUUID().toString();
        final var authId = UUID.randomUUID().toString();

        assertGetMissing(ctx, tenantId, deviceId, authId, CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD, () -> {
            ctx.completeNow();
        });

    }

    /**
     * Verifies that the service returns credentials for an existing device.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testGetCredentialsSucceedsForExistingDevice(final VertxTestContext ctx) {

        final var tenantId = "tenant";
        final var deviceId = UUID.randomUUID().toString();
        final var authId = UUID.randomUUID().toString();

        assertGetMissing(ctx, tenantId, deviceId, authId, CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD,
                () -> getDeviceManagementService()
                        .createDevice(tenantId, Optional.of(deviceId), new Device(), NoopSpan.INSTANCE)
                        .setHandler(ctx.succeeding(s -> assertGet(ctx, tenantId, deviceId, authId,
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
     * Creates a PSK type based credential containing a psk secret.
     *
     * @param authId The authentication to use.
     * @param psk The psk to use.
     * @return The fully populated secret.
     */
    public static PskCredential createPSKCredential(final String authId, final String psk) {
        final PskCredential p = new PskCredential();
        p.setAuthId(authId);

        final PskSecret s = new PskSecret();
        s.setKey(psk.getBytes());

        p.setSecrets(Collections.singletonList(s));

        return p;
    }

    /**
     * Creates a password type based credential containing a hashed password secret.
     *
     * @param authId The authentication to use.
     * @param password The password to use.
     * @param maxBcryptIterations max bcrypt iterations to use.
     * @return The fully populated credential.
     */
    public static PasswordCredential createPasswordCredential(final String authId, final String password,
            final OptionalInt maxBcryptIterations) {
        final PasswordCredential p = new PasswordCredential();
        p.setAuthId(authId);

        p.setSecrets(Collections.singletonList(createPasswordSecret(password, maxBcryptIterations)));

        return p;
    }

    /**
     * Create a password type based credential containing a plain password secret.
     *
     * @param authId The authentication to use.
     * @param password The password to use.
     * @return The fully populated credential.
     */
    public static PasswordCredential createPlainPasswordCredential(final String authId, final String password) {
        final PasswordCredential p = new PasswordCredential();
        p.setAuthId(authId);

        final PasswordSecret secret = new PasswordSecret();
        secret.setPasswordPlain(password);

        p.setSecrets(Collections.singletonList(secret));

        return p;
    }

    private static PasswordCredential createPasswordCredential(final String authId, final String password) {
        return createPasswordCredential(authId, password, OptionalInt.empty());
    }

    /**
     * Create a new password secret.
     *
     * @param password The password to use.
     * @param maxBcryptIterations max bcrypt iterations to use.
     * @return The password secret instance.
     */
    public static PasswordSecret createPasswordSecret(final String password, final OptionalInt maxBcryptIterations) {
        final SpringBasedHonoPasswordEncoder encoder = new SpringBasedHonoPasswordEncoder(
                maxBcryptIterations.orElse(SpringBasedHonoPasswordEncoder.DEFAULT_BCRYPT_STRENGTH));
        final EncodedPassword encodedPwd = EncodedPassword.fromHonoSecret(encoder.encode(password));

        final PasswordSecret s = new PasswordSecret();
        s.setHashFunction(encodedPwd.hashFunction);
        if (encodedPwd.salt != null) {
            s.setSalt(Base64.getEncoder().encodeToString(encodedPwd.salt));
        }
        s.setPasswordHash(encodedPwd.password);
        return s;
    }

    /**
     * Verify that provided secret does contains any of the hash, the salt, or the hash function.
     * @param secret Secret to check.
     */
    public void assertPasswordSecretDoesNotContainPasswordDetails(final PasswordSecret secret) {
        assertNull(secret.getPasswordHash());
        assertNull(secret.getHashFunction());
        assertNull(secret.getSalt());
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
    protected void assertGetMissing(final VertxTestContext ctx,
            final String tenantId, final String deviceId, final String authId, final String type,
            final ExecutionBlock whenComplete) {

        assertGet(ctx, tenantId, deviceId, authId, type,
                r -> {
                    assertEquals(HttpURLConnection.HTTP_NOT_FOUND, r.getStatus());
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
    protected void assertGetEmpty(final VertxTestContext ctx,
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
    protected void assertGet(final VertxTestContext ctx,
            final String tenantId, final String deviceId, final String authId, final String type,
            final ThrowingConsumer<OperationResult<List<CommonCredential>>> mangementValidation,
            final ThrowingConsumer<CredentialsResult<JsonObject>> adapterValidation,
            final ExecutionBlock whenComplete) {

        getCredentialsManagementService().readCredentials(tenantId, deviceId, NoopSpan.INSTANCE, ctx.succeeding(s3 -> {

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
                        CLIENT_CONTEXT)
                        .setHandler(ctx.succeeding(s4 -> ctx.verify(() -> {

                            adapterValidation.accept(s4);

                            whenComplete.apply();
                        })));

            });
        }));

    }

    /**
     * Test creating a new secret.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testCreatePasswordSecret(final VertxTestContext ctx) {

        final var tenantId = "tenant";
        final var deviceId = UUID.randomUUID().toString();
        final var authId = UUID.randomUUID().toString();
        final var secret = createPasswordCredential(authId, "bar");

        assertGetMissing(ctx, tenantId, deviceId, authId, CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD, () -> {

            getCredentialsManagementService().updateCredentials(tenantId, deviceId, Collections.singletonList(secret),
                    Optional.empty(),
                    NoopSpan.INSTANCE,
                    ctx.succeeding(s2 -> ctx.verify(() -> {

                        assertEquals(HttpURLConnection.HTTP_NO_CONTENT, s2.getStatus());
                        assertResourceVersion(s2);

                        assertGet(ctx, tenantId, deviceId, authId,
                                CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD,
                                r -> {
                                    assertEquals(HttpURLConnection.HTTP_OK, r.getStatus());
                                },
                                r -> {
                                    assertEquals(HttpURLConnection.HTTP_OK, r.getStatus());
                                },
                                ctx::completeNow);

                    })));

        });

    }

    /**
     * Test creating a new plain password secret.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testCreatePlainPasswordSecret(final VertxTestContext ctx) {

        final var tenantId = "tenant";
        final var deviceId = UUID.randomUUID().toString();
        final var authId = UUID.randomUUID().toString();
        final var password = "bar";
        final var secret = createPlainPasswordCredential(authId, password);

        assertGetMissing(ctx, tenantId, deviceId, authId, CredentialsConstants.FIELD_SECRETS_PWD_PLAIN, () -> {

            getCredentialsManagementService().updateCredentials(tenantId, deviceId, Collections.singletonList(secret),
                    Optional.empty(),
                    NoopSpan.INSTANCE,
                    ctx.succeeding(s2 -> ctx.verify(() -> {

                        assertEquals(HttpURLConnection.HTTP_NO_CONTENT, s2.getStatus());
                        assertResourceVersion(s2);

                        assertGet(ctx, tenantId, deviceId, authId,
                                CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD,
                                r -> {
                                    final List<CommonCredential> credentials = r.getPayload();
                                    assertEquals(1, credentials.size());
                                    final List<PasswordSecret> secrets = ((PasswordCredential) credentials.get(0)).getSecrets();
                                    assertEquals(1, secrets.size());
                                    assertNotNull(JsonObject.mapFrom(secrets.get(0)).getString(RegistryManagementConstants.FIELD_ID));
                                    assertPasswordSecretDoesNotContainPasswordDetails(secrets.get(0));
                                    assertNull(secrets.get(0).getPasswordPlain());
                                    assertEquals(HttpURLConnection.HTTP_OK, r.getStatus());
                                },
                                r -> {
                                    assertEquals(HttpURLConnection.HTTP_OK, r.getStatus());
                                },
                                ctx::completeNow);

                    })));

        });

    }

    private void assertResourceVersion(final OperationResult<?> result) {
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
     * Test updating a new secret.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testUpdatePasswordSecret(final VertxTestContext ctx) {

        final var tenantId = "tenant";
        final var deviceId = UUID.randomUUID().toString();
        final var authId = UUID.randomUUID().toString();
        final var secret = createPasswordCredential(authId, "bar");

        final Promise<?> phase1 = Promise.promise();

        // phase 1 - initially set credentials

        assertGetMissing(ctx, tenantId, deviceId, authId, CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD, () -> {

            getCredentialsManagementService().updateCredentials(tenantId, deviceId, Collections.singletonList(secret),
                    Optional.empty(),
                    NoopSpan.INSTANCE,
                    ctx.succeeding(s2 -> ctx.verify(() -> {

                        assertResourceVersion(s2);
                        assertEquals(HttpURLConnection.HTTP_NO_CONTENT, s2.getStatus());

                        assertGet(ctx, tenantId, deviceId, authId,
                                CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD,
                                r -> {
                                    assertEquals(HttpURLConnection.HTTP_OK, r.getStatus());
                                },
                                r -> {
                                    assertEquals(HttpURLConnection.HTTP_OK, r.getStatus());
                                },
                                phase1::complete);

                    })));

        });

        // phase 2 - try to update

        phase1.future().setHandler(ctx.succeeding(v -> {

            final var newSecret = createPasswordCredential(authId, "baz");

            getCredentialsManagementService().updateCredentials(tenantId, deviceId,
                    Collections.singletonList(newSecret), Optional.empty(),
                    NoopSpan.INSTANCE,
                    ctx.succeeding(s -> ctx.verify(() -> {

                        assertEquals(HttpURLConnection.HTTP_NO_CONTENT, s.getStatus());

                        assertGet(ctx, tenantId, deviceId, authId, CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD,
                                r -> {
                                    assertEquals(HttpURLConnection.HTTP_OK, r.getStatus());
                                },
                                r -> {
                                    assertEquals(HttpURLConnection.HTTP_OK, r.getStatus());
                                },
                                ctx::completeNow);
                    })));

        }));

    }

    /**
     * Test updating a secret but providing the wrong version.
     * @param ctx The vert.x test context.
     */
    @Test
    public void testUpdateCredentialWithWrongResourceVersionFails(final VertxTestContext ctx) {
        final var tenantId = "tenant";
        final var deviceId = UUID.randomUUID().toString();
        final var authId = UUID.randomUUID().toString();
        final var secret = createPasswordCredential(authId, "bar");

        final Checkpoint checkpoint = ctx.checkpoint(3);

        // phase 1 - create device

        final Promise<?> phase1 = Promise.promise();

        getDeviceManagementService()
                .createDevice(tenantId, Optional.of(deviceId), new Device(), NoopSpan.INSTANCE)
                .setHandler(ctx.succeeding(s2 -> {
                    checkpoint.flag();
                    phase1.complete();
                }));

        // phase 2 - set credentials

        final Promise<?> phase2 = Promise.promise();

        phase1.future().setHandler(ctx.succeeding(s1 -> {

                getCredentialsManagementService().updateCredentials(tenantId, deviceId,
                        Collections.singletonList(secret), Optional.empty(),
                        NoopSpan.INSTANCE,
                        ctx.succeeding(s2 -> {

                            checkpoint.flag();

                            ctx.verify(() -> {

                                assertEquals(HttpURLConnection.HTTP_NO_CONTENT, s2.getStatus());
                                phase2.complete();
                            });
                        }));

        }));

        // phase 3 - update with wrong version

        phase2.future().setHandler(ctx.succeeding(v -> {

            getCredentialsManagementService().updateCredentials(tenantId, deviceId, Collections.singletonList(secret),
                    Optional.of(UUID.randomUUID().toString()),
                    NoopSpan.INSTANCE,
                    ctx.succeeding( s -> ctx.verify(() -> {

                        assertEquals(HttpURLConnection.HTTP_PRECON_FAILED, s.getStatus());
                        checkpoint.flag();

                    })));
        }));
    }

    /**
     * Test updating a new secret.
     * Also verifies that removing a device deletes the attached credentials.
     * @param ctx The vert.x test context.
     */
    @Test
    public void testCreateAndDeletePasswordSecret(final VertxTestContext ctx) {

        final var tenantId = "tenant";
        final var deviceId = UUID.randomUUID().toString();
        final var authId = UUID.randomUUID().toString();
        final var secret = createPasswordCredential(authId, "bar");

        final Checkpoint checkpoint = ctx.checkpoint(7);

        // phase 1 - check missing

        final Promise<?> phase1 = Promise.promise();

        assertGetMissing(ctx, tenantId, deviceId, authId, CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD,
                phase1::complete);

        // phase 2 - create device

        final Promise<?> phase2 = Promise.promise();

        phase1.future().setHandler(ctx.succeeding(s1 -> {

            checkpoint.flag();
            getDeviceManagementService()
                    .createDevice(tenantId, Optional.of(deviceId), new Device(), NoopSpan.INSTANCE)
                    .setHandler(ctx.succeeding(s2 -> {
                        checkpoint.flag();
                        phase2.complete();
                    }));

        }));

        // phase 3 - initially set credentials

        final Promise<?> phase3 = Promise.promise();

        phase2.future().setHandler(ctx.succeeding(s1 -> {

            assertGetEmpty(ctx, tenantId, deviceId, authId, CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD, () -> {

                getCredentialsManagementService().updateCredentials(tenantId, deviceId,
                        Collections.singletonList(secret), Optional.empty(),
                        NoopSpan.INSTANCE,
                        ctx.succeeding(s2 -> {

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

                        }));
            });

        }));

        // Phase 4 verifies that when the device is deleted, the corresponding credentials are deleted as well.

        final Promise<?> phase4 = Promise.promise();

        phase3.future().setHandler(ctx.succeeding(
                v -> getDeviceManagementService()
                        .deleteDevice(tenantId, deviceId, Optional.empty(), NoopSpan.INSTANCE)
                        .setHandler(
                                ctx.succeeding(s -> ctx.verify(() -> assertGetMissing(ctx, tenantId, deviceId, authId,
                                        CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD, () -> {
                                            checkpoint.flag();
                                            phase4.complete();
                                        }))))));

        // complete

        phase4.future().setHandler(ctx.succeeding(s -> ctx.completeNow()));

    }

    /**
     * Test fetching disabled credentials for the protocol adapter.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testDisableCredentials(final VertxTestContext ctx) {

        final String tenantId = UUID.randomUUID().toString();
        final String deviceId = UUID.randomUUID().toString();
        final String authId = UUID.randomUUID().toString();

        final CommonCredential credential = createPasswordCredential(authId, "bar");
        final CommonCredential disabledCredential = createPSKCredential(authId, "baz");
        disabledCredential.setEnabled(false);

        final List<CommonCredential> credentials = Arrays.asList(credential, disabledCredential);

        // create device & set credentials

        final Promise<?> phase1 = Promise.promise();

        getDeviceManagementService()
                .createDevice(tenantId, Optional.of(deviceId), new Device(), NoopSpan.INSTANCE)
                .setHandler(ctx.succeeding(n -> getCredentialsManagementService()
                        .updateCredentials(tenantId, deviceId, credentials, Optional.empty(), NoopSpan.INSTANCE,
                                ctx.succeeding(s -> phase1.complete()))));

        // validate credentials - enabled

        final Promise<?> phase2 = Promise.promise();

        phase1.future()
                .setHandler(ctx.succeeding(n -> getCredentialsService()
                        .get(tenantId, CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD, authId)
                        .setHandler(ctx.succeeding(s -> ctx.verify(() -> {

                            assertEquals(HttpURLConnection.HTTP_OK, s.getStatus());

                            final CredentialsObject creds = s.getPayload().mapTo(CredentialsObject.class);

                            assertEquals(authId, creds.getAuthId());
                            assertEquals(CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD, creds.getType());
                            assertEquals(1, creds.getSecrets().size());

                            phase2.complete();
                        })))));

        // validate credentials - disabled

        final Promise<?> phase3 = Promise.promise();

        phase2.future().setHandler(ctx.succeeding(n -> {
            getCredentialsService().get(tenantId, CredentialsConstants.SECRETS_TYPE_PRESHARED_KEY, authId)
                    .setHandler(ctx.succeeding(s -> ctx.verify(() -> {

                        assertEquals(HttpURLConnection.HTTP_NOT_FOUND, s.getStatus());

                        phase3.complete();
                    })));
        }));

        // finally complete

        phase3.future().setHandler(ctx.succeeding(s -> ctx.completeNow()));
    }

    /**
     * Verify that created secrets contains an ID.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testReturnedSecretContainAnId(final VertxTestContext ctx) {

        final String tenantId = UUID.randomUUID().toString();
        final String deviceId = UUID.randomUUID().toString();
        final String authId = UUID.randomUUID().toString();

        final CommonCredential credential = createPasswordCredential(authId, "bar");

        final List<CommonCredential> credentials = Arrays.asList(credential);

        // create device & set credentials

        final Promise<?> phase1 = Promise.promise();

        getDeviceManagementService()
                .createDevice(tenantId, Optional.of(deviceId), new Device(), NoopSpan.INSTANCE)
                .setHandler(ctx.succeeding(n -> getCredentialsManagementService()
                        .updateCredentials(tenantId, deviceId, credentials, Optional.empty(), NoopSpan.INSTANCE,
                                ctx.succeeding(s -> phase1.complete()))));

        // validate credentials - contains an ID.

        final Promise<?> phase2 = Promise.promise();

        phase1.future().setHandler(ctx.succeeding(n -> {
            getCredentialsService().get(tenantId, CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD, authId)
                    .setHandler(ctx.succeeding(s -> ctx.verify(() -> {

                        assertEquals(HttpURLConnection.HTTP_OK, s.getStatus());

                        final CredentialsObject creds = s.getPayload().mapTo(CredentialsObject.class);

                        assertEquals(authId, creds.getAuthId());
                        assertEquals(CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD, creds.getType());
                        assertEquals(1, creds.getSecrets().size());
                        assertNotNull(
                                creds.getSecrets().getJsonObject(0).getString(RegistryManagementConstants.FIELD_ID));

                        phase2.complete();
                    })));
        }));

        // finally complete

        phase2.future().setHandler(ctx.succeeding(s -> ctx.completeNow()));
    }

    /**
     * Verify that updating a secret using a wrong secret ID fails with a `400 Bad Request`.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testUpdateSecretFailsWithWrongSecretId(final VertxTestContext ctx) {

        final String tenantId = UUID.randomUUID().toString();
        final String deviceId = UUID.randomUUID().toString();
        final String authId = UUID.randomUUID().toString();

        final CommonCredential credential = createPasswordCredential(authId, "bar");

        final List<CommonCredential> credentials = Arrays.asList(credential);

        // create device & set credentials

        final Promise<?> phase1 = Promise.promise();

        getDeviceManagementService()
                .createDevice(tenantId, Optional.of(deviceId), new Device(), NoopSpan.INSTANCE)
                .setHandler(ctx.succeeding(n -> getCredentialsManagementService()
                        .updateCredentials(tenantId, deviceId, credentials, Optional.empty(), NoopSpan.INSTANCE,
                                ctx.succeeding(s -> phase1.complete()))));

        // re-set credentials with wrong ID
        final Promise<?> phase2 = Promise.promise();

        // Change the password
        final CommonCredential newCredential = createPasswordCredential(authId, "foo");
        ((PasswordCredential) newCredential).getSecrets().get(0).setId("randomId");

        phase1.future().setHandler(ctx.succeeding(n -> getCredentialsManagementService()
                .updateCredentials(tenantId, deviceId, Collections.singletonList(newCredential), Optional.empty(),
                        NoopSpan.INSTANCE,
                        ctx.succeeding(s -> ctx.verify(() -> {

                            assertEquals(HttpURLConnection.HTTP_BAD_REQUEST, s.getStatus());
                            phase2.complete();
                        })))));

        // finally complete

        phase2.future().setHandler(ctx.succeeding(s -> ctx.completeNow()));
    }

    /**
     * Verify that secrets returned by the management methods do not
     * contains secrets details for hashed passwords.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testHashedPasswordsDetailsAreRemoved(final VertxTestContext ctx) {

        final String tenantId = UUID.randomUUID().toString();
        final String deviceId = UUID.randomUUID().toString();
        final String authId = UUID.randomUUID().toString();

        final CommonCredential credential = createPasswordCredential(authId, "bar");

        final List<CommonCredential> credentials = Arrays.asList(credential);

        // create device & set credentials

        final Promise<?> phase1 = Promise.promise();

        getDeviceManagementService()
                .createDevice(tenantId, Optional.of(deviceId), new Device(), NoopSpan.INSTANCE)
                .setHandler(ctx.succeeding(n -> getCredentialsManagementService()
                        .updateCredentials(tenantId, deviceId, credentials, Optional.empty(), NoopSpan.INSTANCE,
                                ctx.succeeding(s -> phase1.complete()))));

        // validate credentials - do not contain the secret details.

        final Promise<?> phase2 = Promise.promise();

        phase1.future().setHandler(ctx.succeeding(n -> {
            getCredentialsManagementService().readCredentials(tenantId, deviceId,
                    NoopSpan.INSTANCE, ctx.succeeding(s -> ctx.verify(() -> {

                        assertEquals(HttpURLConnection.HTTP_OK, s.getStatus());

                        final List<CommonCredential> creds = s.getPayload();
                        assertEquals(1, creds.size());

                        final CommonCredential cred = creds.get(0);
                        assertEquals(authId, cred.getAuthId());
                        assertTrue(cred instanceof PasswordCredential);
                        assertEquals(1, ((PasswordCredential) cred).getSecrets().size());
                        final PasswordSecret secret = ((PasswordCredential) cred).getSecrets().get(0);
                        assertNull(secret.getPasswordHash());
                        assertNull(secret.getSalt());
                        assertNull(secret.getHashFunction());

                        phase2.complete();
                    })));
        }));

        // finally complete

        phase2.future().setHandler(ctx.succeeding(s -> ctx.completeNow()));
    }

    /**
     * Verify that an existing secret is deleted when it's ID is removed
     * from the SET payload.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testSecretsWithMissingIDsAreRemoved(final VertxTestContext ctx) {

        final String tenantId = UUID.randomUUID().toString();
        final String deviceId = UUID.randomUUID().toString();
        final String authId = UUID.randomUUID().toString();

        final PasswordCredential credential = new PasswordCredential();
        credential.setAuthId(authId);
        final PasswordSecret sec1 = new PasswordSecret().setPasswordPlain("bar");
        final PasswordSecret sec2 = new PasswordSecret().setPasswordPlain("foo");

        credential.setSecrets(Arrays.asList(sec1, sec2));

        // create device & set credentials

        final Promise<?> phase1 = Promise.promise();

        getDeviceManagementService()
                .createDevice(tenantId, Optional.of(deviceId), new Device(), NoopSpan.INSTANCE)
                .setHandler(ctx.succeeding(n -> getCredentialsManagementService()
                        .updateCredentials(tenantId, deviceId, Collections.singletonList(credential),
                                Optional.empty(),
                                NoopSpan.INSTANCE,
                                ctx.succeeding(s -> phase1.complete()))));

        // Retrieve credentials IDs

        final Promise<?> phase2 = Promise.promise();
        final List<String> secretIDs = new ArrayList<>();

        phase1.future()
                .setHandler(ctx.succeeding(n -> getCredentialsService()
                        .get(tenantId, CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD, authId)
                        .setHandler(ctx.succeeding(s -> ctx.verify(() -> {

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

        // create a credential object with only one of the ID.
        final PasswordCredential credentialWithOnlyId = new PasswordCredential();
        credentialWithOnlyId.setAuthId(authId);

        final PasswordSecret secretWithOnlyId = new PasswordSecret();
        secretWithOnlyId.setId(secretIDs.get(0));

        credentialWithOnlyId.setSecrets(Collections.singletonList(secretWithOnlyId));

        phase2.future().setHandler(ctx.succeeding(n -> getCredentialsManagementService()
                .updateCredentials(tenantId, deviceId, Collections.singletonList(credentialWithOnlyId),
                        Optional.empty(), NoopSpan.INSTANCE, ctx.succeeding(s -> phase3.complete()))));


        // Retrieve credentials again, one should be deleted.

        final Promise<?> phase4 = Promise.promise();

        phase3.future()
                .setHandler(ctx.succeeding(n -> getCredentialsService()
                        .get(tenantId, CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD, authId)
                        .setHandler(ctx.succeeding(s -> ctx.verify(() -> {

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

        phase4.future().setHandler(ctx.succeeding(s -> ctx.completeNow()));
    }

    /**
     * Verify that the ID of a secret is still the same when the secret is updated.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testSecretsIDisNotChangedWhenSecretIsUpdated(final VertxTestContext ctx) {

        final String tenantId = UUID.randomUUID().toString();
        final String deviceId = UUID.randomUUID().toString();
        final String authId = UUID.randomUUID().toString();

        final CommonCredential credential = createPasswordCredential(authId, "bar");

        final List<CommonCredential> credentials = Collections.singletonList(credential);

        // create device & set credentials

        final Promise<?> phase1 = Promise.promise();

        getDeviceManagementService().createDevice(tenantId, Optional.of(deviceId), new Device(), NoopSpan.INSTANCE)
                .setHandler(ctx.succeeding(n -> getCredentialsManagementService()
                        .updateCredentials(tenantId, deviceId, credentials, Optional.empty(), NoopSpan.INSTANCE,
                                ctx.succeeding(s -> phase1.complete()))));

        // Retrieve credentials IDs

        final Promise<?> phase2 = Promise.promise();
        final List<String> secretIDs = new ArrayList<>();

        phase1.future()
                .setHandler(ctx.succeeding(n -> getCredentialsService()
                        .get(tenantId, CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD, authId)
                        .setHandler(ctx.succeeding(s -> ctx.verify(() -> {

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

        // Change the password
        final CommonCredential newCredential = createPasswordCredential(authId, "foo");
        ((PasswordCredential) newCredential).getSecrets().get(0).setId(secretIDs.get(0));

        phase2.future().setHandler(ctx.succeeding(n -> getCredentialsManagementService()
                .updateCredentials(tenantId, deviceId, Collections.singletonList(newCredential), Optional.empty(),
                        NoopSpan.INSTANCE, ctx.succeeding(s -> phase3.complete()))));


        // Retrieve credentials again, the ID should have changed.

        final Promise<?> phase4 = Promise.promise();

        phase3.future()
                .setHandler(ctx.succeeding(n -> getCredentialsService()
                        .get(tenantId, CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD, authId)
                        .setHandler(ctx.succeeding(s -> ctx.verify(() -> {

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

        phase4.future().setHandler(ctx.succeeding(s -> ctx.completeNow()));
    }

    /**
     * Verify that the metadata of a secret can be updated without changing the secret id.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testSecretMetadataUpdateDoesntChangeSecretID(final VertxTestContext ctx) {

        final String tenantId = UUID.randomUUID().toString();
        final String deviceId = UUID.randomUUID().toString();
        final String authId = UUID.randomUUID().toString();

        final CommonCredential credential = createPasswordCredential(authId, "bar");

        final List<CommonCredential> credentials = Collections.singletonList(credential);

        // create device & set credentials

        final Promise<?> phase1 = Promise.promise();

        getDeviceManagementService()
                .createDevice(tenantId, Optional.of(deviceId), new Device(), NoopSpan.INSTANCE)
                .setHandler(ctx.succeeding(n -> getCredentialsManagementService()
                        .updateCredentials(tenantId, deviceId, credentials, Optional.empty(), NoopSpan.INSTANCE,
                                ctx.succeeding(s -> phase1.complete()))));

        // Retrieve credential ID

        final Promise<?> phase2 = Promise.promise();
        final List<String> secretIDs = new ArrayList<>();

        phase1.future()
                .setHandler(ctx.succeeding(n -> getCredentialsService()
                        .get(tenantId, CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD, authId)
                        .setHandler(ctx.succeeding(s -> ctx.verify(() -> {

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

        // Add some metadata to the secret
        final PasswordCredential credentialWithMetadataUpdate = new PasswordCredential();
        credentialWithMetadataUpdate.setAuthId(authId);

        final PasswordSecret secretWithOnlyIdAndMetadata = new PasswordSecret();
        secretWithOnlyIdAndMetadata.setId(secretIDs.get(0));
        secretWithOnlyIdAndMetadata.setComment("secret comment");


        credentialWithMetadataUpdate.setSecrets(Collections.singletonList(secretWithOnlyIdAndMetadata));


        phase2.future().setHandler(ctx.succeeding(n -> {
            getCredentialsManagementService()
                    .updateCredentials(tenantId, deviceId, Collections.singletonList(credentialWithMetadataUpdate),
                            Optional.empty(),
                            NoopSpan.INSTANCE, ctx.succeeding(s -> phase3.complete()));
        }));


        // Retrieve credentials again, the ID should not have changed.

        final Promise<?> phase4 = Promise.promise();

        phase3.future()
                .setHandler(ctx.succeeding(n -> getCredentialsService()
                        .get(tenantId, CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD, authId)
                        .setHandler(ctx.succeeding(s -> ctx.verify(() -> {

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

        phase4.future().setHandler(ctx.succeeding(s -> ctx.completeNow()));
    }


    /**
     * Verify deleting some metadata to a secret.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testSecretMetadataDeletion(final VertxTestContext ctx) {

        final String tenantId = UUID.randomUUID().toString();
        final String deviceId = UUID.randomUUID().toString();
        final String authId = UUID.randomUUID().toString();

        final CommonCredential credential = createPasswordCredential(authId, "bar");
        ((PasswordCredential) credential).getSecrets().get(0).setNotBefore(new Date().toInstant());

        final List<CommonCredential> credentials = Collections.singletonList(credential);

        // create device & set credentials

        final Promise<?> phase1 = Promise.promise();

        getDeviceManagementService().createDevice(tenantId, Optional.of(deviceId), new Device(), NoopSpan.INSTANCE)
                .setHandler(ctx.succeeding(n -> getCredentialsManagementService()
                        .updateCredentials(tenantId, deviceId, credentials, Optional.empty(), NoopSpan.INSTANCE,
                                ctx.succeeding(s -> phase1.complete()))));

        // Retrieve credential ID

        final Promise<?> phase2 = Promise.promise();
        final List<String> secretIDs = new ArrayList<>();

        phase1.future()
                .setHandler(ctx.succeeding(n -> getCredentialsService()
                        .get(tenantId, CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD, authId)
                        .setHandler(ctx.succeeding(s -> ctx.verify(() -> {

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

        // Add some other metadata to the secret
        final PasswordCredential credentialWithMetadataUpdate = new PasswordCredential();
        credentialWithMetadataUpdate.setAuthId(authId);

        final PasswordSecret secretWithOnlyIdAndMetadata = new PasswordSecret();
        secretWithOnlyIdAndMetadata.setId(secretIDs.get(0));
        secretWithOnlyIdAndMetadata.setComment("secret comment");

        credentialWithMetadataUpdate.setSecrets(Collections.singletonList(secretWithOnlyIdAndMetadata));


        phase2.future().setHandler(ctx.succeeding(n -> {
            getCredentialsManagementService()
                    .updateCredentials(tenantId, deviceId, Collections.singletonList(credentialWithMetadataUpdate),
                            Optional.empty(),
                            NoopSpan.INSTANCE, ctx.succeeding(s -> phase3.complete()));
        }));


        // Retrieve credentials again, both metadata should be there

        final Promise<?> phase4 = Promise.promise();

        phase3.future()
                .setHandler(ctx.succeeding(n -> getCredentialsService()
                        .get(tenantId, CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD, authId)
                        .setHandler(ctx.succeeding(s -> ctx.verify(() -> {

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

        phase4.future().setHandler(ctx.succeeding(s -> ctx.completeNow()));
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
    protected static Future<OperationResult<Void>> setCredentials(
            final CredentialsManagementService svc,
            final String tenantId,
            final String deviceId,
            final List<CommonCredential> secrets) {

        final Promise<OperationResult<Void>> result = Promise.promise();
        svc.updateCredentials(tenantId, deviceId, secrets, Optional.empty(), NoopSpan.INSTANCE, result);
        return result.future().map(r -> {
            if (HttpURLConnection.HTTP_NO_CONTENT == r.getStatus()) {
                return r;
            } else {
                throw new ClientErrorException(r.getStatus());
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
    protected static Future<Void> assertRegistered(
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
    protected static Future<Void> assertNotRegistered(
            final CredentialsService svc,
            final String tenant,
            final String authId,
            final String type) {

        return assertGet(svc, tenant, authId, type, HttpURLConnection.HTTP_NOT_FOUND);
    }

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
