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
package org.eclipse.hono.service.credentials;

import static java.net.HttpURLConnection.HTTP_NOT_FOUND;
import static java.net.HttpURLConnection.HTTP_NO_CONTENT;
import static java.net.HttpURLConnection.HTTP_OK;
import static java.net.HttpURLConnection.HTTP_PRECON_FAILED;
import static org.junit.Assert.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.opentracing.noop.NoopSpan;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.Checkpoint;
import io.vertx.junit5.VertxTestContext;
import io.vertx.junit5.VertxTestContext.ExecutionBlock;

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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.ThrowingConsumer;

import java.net.HttpURLConnection;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

/**
 * Abstract class used as a base for verifying behavior of {@link CredentialsService} and
 * {@link CredentialsManagementService} in device registry implementations.
 *
 */
public abstract class AbstractCredentialsServiceTest {

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
     * Verifies that the service returns 404 if a client wants to retrieve non-existing credentials.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testGetCredentialsSucceedsForExistingDevice(final VertxTestContext ctx) {

        final var tenantId = "tenant";
        final var deviceId = UUID.randomUUID().toString();
        final var authId = UUID.randomUUID().toString();

        assertGetMissing(ctx, tenantId, deviceId, authId, CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD, () -> {

            getDeviceManagementService().createDevice(tenantId, Optional.of(deviceId), new Device(), NoopSpan.INSTANCE,
                    ctx.succeeding(s -> {

                        assertGet(ctx, tenantId, deviceId, authId, CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD,
                                r -> {
                                    assertEquals(HTTP_OK, r.getStatus());
                                    assertNotNull(r.getPayload());
                                    assertTrue(r.getPayload().isEmpty());
                                },
                                r -> {
                                    assertEquals(HTTP_NOT_FOUND, r.getStatus());
                                },
                                ctx::completeNow);

            }));

        });

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
                    assertEquals(HTTP_NOT_FOUND, r.getStatus());
                },
                r -> {
                    assertEquals(HTTP_NOT_FOUND, r.getStatus());
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
                    assertEquals(HTTP_OK, r.getStatus());
                    assertTrue(r.getPayload().isEmpty());
                },
                r -> {
                    assertEquals(HTTP_NOT_FOUND, r.getStatus());
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

        getCredentialsManagementService().get(tenantId, deviceId, NoopSpan.INSTANCE, ctx.succeeding(s3 -> {

            ctx.verify(() -> {

                // assert a few basics, optionals may be empty
                // but must not be null
                assertNotNull(s3.getCacheDirective());
                assertResourceVersion(s3);

                mangementValidation.accept(s3);

                getCredentialsService().get(tenantId, type,
                        authId,
                        ctx.succeeding(s4 -> ctx.verify(() -> {

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

            getCredentialsManagementService().set(tenantId, deviceId, Optional.empty(),
                    Collections.singletonList(secret), NoopSpan.INSTANCE,
                    ctx.succeeding(s2 -> ctx.verify(() -> {

                        assertEquals(HTTP_NO_CONTENT, s2.getStatus());
                        assertResourceVersion(s2);

                        assertGet(ctx, tenantId, deviceId, authId,
                                CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD,
                                r -> {
                                    assertEquals(HTTP_OK, r.getStatus());
                                },
                                r -> {
                                    assertEquals(HTTP_OK, r.getStatus());
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

        final Future<?> phase1 = Future.future();

        // phase 1 - initially set credentials

        assertGetMissing(ctx, tenantId, deviceId, authId, CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD, () -> {

            getCredentialsManagementService().set(tenantId, deviceId, Optional.empty(),
                    Collections.singletonList(secret), NoopSpan.INSTANCE,
                    ctx.succeeding(s2 -> ctx.verify(() -> {

                        assertResourceVersion(s2);
                        assertEquals(HTTP_NO_CONTENT, s2.getStatus());

                        assertGet(ctx, tenantId, deviceId, authId,
                                CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD,
                                r -> {
                                    assertEquals(HTTP_OK, r.getStatus());
                                },
                                r -> {
                                    assertEquals(HTTP_OK, r.getStatus());
                                },
                                phase1::complete);

                    })));

        });

        // phase 2 - try to update

        phase1.setHandler(ctx.succeeding(v -> {

            final var newSecret = createPasswordCredential(authId, "baz");

            getCredentialsManagementService().set(tenantId, deviceId, Optional.empty(),
                    Collections.singletonList(newSecret), NoopSpan.INSTANCE,
                    ctx.succeeding(s -> ctx.verify(() -> {

                        assertEquals(HTTP_NO_CONTENT, s.getStatus());

                        assertGet(ctx, tenantId, deviceId, authId, CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD,
                                r -> {
                                    assertEquals(HTTP_OK, r.getStatus());
                                },
                                r -> {
                                    assertEquals(HTTP_OK, r.getStatus());
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

        final Future<?> phase1 = Future.future();

            getDeviceManagementService().createDevice(tenantId, Optional.of(deviceId), new Device(), NoopSpan.INSTANCE,
                    ctx.succeeding(s2 -> {
                        checkpoint.flag();
                        phase1.complete();
                    }));


        // phase 2 - set credentials

        final Future<?> phase2 = Future.future();

        phase1.setHandler(ctx.succeeding(s1 -> {

                getCredentialsManagementService().set(tenantId, deviceId, Optional.empty(),
                        Collections.singletonList(secret), NoopSpan.INSTANCE,
                        ctx.succeeding(s2 -> {

                            checkpoint.flag();

                            ctx.verify(() -> {

                                assertEquals(HTTP_NO_CONTENT, s2.getStatus());
                                phase2.complete();
                            });
                        }));

        }));

        // phase 3 - update with wrong version

        phase2.setHandler(ctx.succeeding(v -> {

            getCredentialsManagementService().set(tenantId, deviceId, Optional.of(UUID.randomUUID().toString()),
                    Collections.singletonList(secret), NoopSpan.INSTANCE,
                    ctx.succeeding( s -> ctx.verify(() -> {

                        assertEquals(HTTP_PRECON_FAILED, s.getStatus());
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

        final Future<?> phase1 = Future.future();

        assertGetMissing(ctx, tenantId, deviceId, authId, CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD,
                phase1::complete);

        // phase 2 - create device

        final Future<?> phase2 = Future.future();

        phase1.setHandler(ctx.succeeding(s1 -> {

            checkpoint.flag();
            getDeviceManagementService().createDevice(tenantId, Optional.of(deviceId), new Device(), NoopSpan.INSTANCE,
                    ctx.succeeding(s2 -> {
                        checkpoint.flag();
                        phase2.complete();
                    }));

        }));

        // phase 3 - initially set credentials

        final Future<?> phase3 = Future.future();

        phase2.setHandler(ctx.succeeding(s1 -> {

            assertGetEmpty(ctx, tenantId, deviceId, authId, CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD, () -> {

                getCredentialsManagementService().set(tenantId, deviceId, Optional.empty(),
                        Collections.singletonList(secret), NoopSpan.INSTANCE,
                        ctx.succeeding(s2 -> {

                            checkpoint.flag();

                            ctx.verify(() -> {

                                assertEquals(HTTP_NO_CONTENT, s2.getStatus());

                                assertGet(ctx, tenantId, deviceId, authId,
                                        CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD,
                                        r -> {
                                            assertEquals(HTTP_OK, r.getStatus());
                                        },
                                        r -> {
                                            assertEquals(HTTP_OK, r.getStatus());
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

        final Future<?> phase4 = Future.future();

        phase3.setHandler(ctx.succeeding(v -> {
            getDeviceManagementService().deleteDevice(tenantId, deviceId, Optional.empty(), NoopSpan.INSTANCE,
                    ctx.succeeding(s -> ctx.verify(() -> {
                        assertGetMissing(ctx, tenantId, deviceId, authId,
                                CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD, () -> {
                                    checkpoint.flag();
                                    phase4.complete();
                                });
                    })));
        }));

        // complete

        phase4.setHandler(ctx.succeeding(s -> ctx.completeNow()));

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

        // create device

        final Future<?> phase1 = Future.future();
        getDeviceManagementService().createDevice(
                tenantId, Optional.of(deviceId), new Device(), NoopSpan.INSTANCE,
                ctx.succeeding(s -> phase1.complete()));

        // set credentials

        final Future<?> phase2 = Future.future();

        phase1.setHandler(ctx.succeeding(n -> {
            getCredentialsManagementService()
                    .set(tenantId, deviceId, Optional.empty(), credentials, NoopSpan.INSTANCE,
                            ctx.succeeding(s -> phase2.complete()));
        }));

        // validate credentials - enabled

        final Future<?> phase3 = Future.future();

        phase2.setHandler(ctx.succeeding(n -> {
            getCredentialsService().get(tenantId, CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD,
                    authId, ctx.succeeding(s -> ctx.verify(() -> {

                        assertEquals(HttpURLConnection.HTTP_OK, s.getStatus());

                        final CredentialsObject creds = s.getPayload().mapTo(CredentialsObject.class);

                        assertEquals(authId, creds.getAuthId());
                        assertEquals(CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD, creds.getType());
                        assertEquals(1, creds.getSecrets().size());

                        phase3.complete();
            })));
        }));

        // validate credentials - disabled

        final Future<?> phase4 = Future.future();

        phase3.setHandler(ctx.succeeding(n -> {
            getCredentialsService().get(tenantId, CredentialsConstants.SECRETS_TYPE_PRESHARED_KEY,
                    authId, ctx.succeeding(s -> ctx.verify(() -> {

                        assertEquals(HttpURLConnection.HTTP_NOT_FOUND, s.getStatus());

                        phase4.complete();
                    })));
        }));

        // finally complete

        phase4.setHandler(ctx.succeeding(s -> ctx.completeNow()));
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

        final Future<OperationResult<Void>> result = Future.future();
        svc.set(tenantId, deviceId, Optional.empty(), secrets, NoopSpan.INSTANCE, result);
        return result.map(r -> {
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

        final Future<CredentialsResult<JsonObject>> result = Future.future();
        svc.get(tenant, type, authId, result);
        return result.map(r -> {
            if (r.getStatus() == expectedStatusCode) {
                return null;
            } else {
                throw new ClientErrorException(HttpURLConnection.HTTP_PRECON_FAILED);
            }
        });
    }

}
