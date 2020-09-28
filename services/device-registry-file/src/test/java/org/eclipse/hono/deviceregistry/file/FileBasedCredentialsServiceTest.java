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

package org.eclipse.hono.deviceregistry.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.HttpURLConnection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.eclipse.hono.auth.SpringBasedHonoPasswordEncoder;
import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.deviceregistry.DeviceRegistryTestUtils;
import org.eclipse.hono.service.credentials.AbstractCredentialsServiceTest;
import org.eclipse.hono.service.credentials.Credentials;
import org.eclipse.hono.service.credentials.CredentialsService;
import org.eclipse.hono.service.management.credentials.CommonCredential;
import org.eclipse.hono.service.management.credentials.CredentialsManagementService;
import org.eclipse.hono.service.management.credentials.PasswordCredential;
import org.eclipse.hono.service.management.credentials.PskCredential;
import org.eclipse.hono.service.management.device.DeviceManagementService;
import org.eclipse.hono.util.CacheDirective;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.CredentialsConstants;
import org.eclipse.hono.util.RegistryManagementConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opentracing.noop.NoopSpan;
import io.vertx.core.AsyncResult;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.file.FileSystem;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

/**
 * Tests verifying behavior of {@link FileBasedCredentialsService}.
 *
 */
@ExtendWith(VertxExtension.class)
@Timeout(value = 5, timeUnit = TimeUnit.SECONDS)
public class FileBasedCredentialsServiceTest extends AbstractCredentialsServiceTest {

    private static final Logger LOG = LoggerFactory.getLogger(FileBasedCredentialsServiceTest.class);

    private static final String REGISTRATION_FILE_NAME = "/device-identities.json";
    private static final String CREDENTIALS_FILE_NAME = "/credentials.json";

    private Vertx vertx;
    private EventBus eventBus;
    private FileSystem fileSystem;

    private FileBasedRegistrationConfigProperties registrationConfig;
    private FileBasedCredentialsConfigProperties credentialsConfig;

    private FileBasedRegistrationService registrationService;
    private FileBasedCredentialsService credentialsService;

    private FileBasedDeviceBackend svc;

    /**
     * Sets up fixture.
     */
    @BeforeEach
    public void setUp() {
        fileSystem = mock(FileSystem.class);
        eventBus = mock(EventBus.class);
        vertx = mock(Vertx.class);
        when(vertx.eventBus()).thenReturn(eventBus);
        when(vertx.fileSystem()).thenReturn(fileSystem);

        this.registrationConfig = new FileBasedRegistrationConfigProperties();
        this.registrationConfig.setCacheMaxAge(30);
        this.credentialsConfig = new FileBasedCredentialsConfigProperties();
        this.credentialsConfig.setCacheMaxAge(30);

        this.registrationService = new FileBasedRegistrationService(vertx);
        this.registrationService.setConfig(registrationConfig);

        this.credentialsService = new FileBasedCredentialsService(vertx);
        this.credentialsService.setPasswordEncoder(new SpringBasedHonoPasswordEncoder());
        this.credentialsService.setConfig(credentialsConfig);

        this.svc = new FileBasedDeviceBackend(this.registrationService, this.credentialsService);
    }

    @Override
    public CredentialsService getCredentialsService() {
        return this.svc;
    }

    @Override
    public CredentialsManagementService getCredentialsManagementService() {
        return this.svc;
    }

    @Override
    public DeviceManagementService getDeviceManagementService() {
        return this.svc;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected CacheDirective getExpectedCacheDirective(final String credentialsType) {
        switch (credentialsType) {
        case CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD:
        case CredentialsConstants.SECRETS_TYPE_X509_CERT:
            return CacheDirective.maxAgeDirective(registrationConfig.getCacheMaxAge());
        default:
            return CacheDirective.noCacheDirective();
        }
    }

    private void start(final Promise<?> startupTracker) {

        CompositeFuture.all(registrationService.start(), credentialsService.start())
            .onComplete(result -> {
                LOG.debug("Startup complete", result.cause());
                if (result.failed()) {
                    startupTracker.fail(result.cause());
                } else {
                    startupTracker.complete();
                }
            });
    }

    /**
     * Verifies that the credentials service creates a file for persisting credentials
     * data if it does not exist yet during startup.
     *
     * @param ctx The vert.x context.
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Test
    public void testDoStartCreatesFile(final VertxTestContext ctx) {

        // GIVEN a registration service configured to persist data to a not yet existing file
        credentialsConfig.setSaveToFile(true);
        credentialsConfig.setFilename(CREDENTIALS_FILE_NAME);
        registrationConfig.setSaveToFile(true);
        registrationConfig.setFilename(REGISTRATION_FILE_NAME);

        when(fileSystem.existsBlocking(credentialsConfig.getFilename())).thenReturn(Boolean.FALSE);
        when(fileSystem.existsBlocking(registrationConfig.getFilename())).thenReturn(Boolean.FALSE);
        doAnswer(invocation -> {
            final Handler handler = invocation.getArgument(1);
            handler.handle(Future.succeededFuture());
            return null;
        }).when(fileSystem).createFile(eq(credentialsConfig.getFilename()), any(Handler.class));
        doAnswer(invocation -> {
            final Handler handler = invocation.getArgument(1);
            handler.handle(Future.failedFuture("malformed file"));
            return null;
        }).when(fileSystem).readFile(eq(credentialsConfig.getFilename()), any(Handler.class));
        doAnswer(invocation -> {
            final Handler handler = invocation.getArgument(1);
            handler.handle(Future.succeededFuture());
            return null;
        }).when(fileSystem).createFile(eq(registrationConfig.getFilename()), any(Handler.class));
        doAnswer(invocation -> {
            final Handler handler = invocation.getArgument(1);
            handler.handle(Future.failedFuture("malformed file"));
            return null;
        }).when(fileSystem).readFile(eq(registrationConfig.getFilename()), any(Handler.class));

        // WHEN starting the service
        final Promise<?> startupTracker = Promise.promise();
        startupTracker.future().onComplete(ctx.succeeding(started -> ctx.verify(() -> {
            // THEN the file gets created
            verify(fileSystem).createFile(eq(credentialsConfig.getFilename()), any(Handler.class));
            ctx.completeNow();
        })));

        start(startupTracker);
    }

    /**
     * Verifies that the credentials service fails to start if it cannot create the file for
     * persisting credentials data during startup.
     *
     * @param ctx The vert.x context.
     */
    @SuppressWarnings({ "unchecked" })
    @Test
    public void testDoStartFailsIfFileCannotBeCreated(final VertxTestContext ctx) {

        // GIVEN a registration service configured to persist data to a not yet existing file
        credentialsConfig.setSaveToFile(true);
        credentialsConfig.setFilename(CREDENTIALS_FILE_NAME);
        when(fileSystem.existsBlocking(credentialsConfig.getFilename())).thenReturn(Boolean.FALSE);

        // WHEN starting the service but the file cannot be created
        doAnswer(invocation -> {
            final Handler<AsyncResult<Void>> handler = invocation.getArgument(1);
            handler.handle(Future.failedFuture("no access"));
            return null;
        }).when(fileSystem).createFile(eq(credentialsConfig.getFilename()), any(Handler.class));

        final Promise<Void> startupTracker = Promise.promise();
        startupTracker.future().onComplete(ctx.failing(started -> {
            ctx.completeNow();
        }));
        start(startupTracker);
    }

    /**
     * Verifies that the credentials service successfully starts up even if
     * the file to read credentials from contains malformed JSON.
     *
     * @param ctx The vert.x context.
     */
    @SuppressWarnings({ "unchecked" })
    @Test
    public void testDoStartIgnoresMalformedJson(final VertxTestContext ctx) {

        // GIVEN a registration service configured to read data from a file
        // that contains malformed JSON
        credentialsConfig.setFilename(CREDENTIALS_FILE_NAME);
        when(fileSystem.existsBlocking(credentialsConfig.getFilename())).thenReturn(Boolean.TRUE);
        doAnswer(invocation -> {
            final Handler<AsyncResult<Buffer>> handler = invocation.getArgument(1);
            handler.handle(Future.succeededFuture(Buffer.buffer("NO JSON")));
            return null;
        }).when(fileSystem).readFile(eq(credentialsConfig.getFilename()), any(Handler.class));

        registrationConfig.setFilename(REGISTRATION_FILE_NAME);
        when(fileSystem.existsBlocking(registrationConfig.getFilename())).thenReturn(Boolean.TRUE);
        doAnswer(invocation -> {
            final Handler<AsyncResult<Buffer>> handler = invocation.getArgument(1);
            handler.handle(Future.succeededFuture(Buffer.buffer("NO JSON")));
            return null;
        }).when(fileSystem).readFile(eq(registrationConfig.getFilename()), any(Handler.class));

        // WHEN starting the service
        final Promise<Void> startupTracker = Promise.promise();
        startupTracker.future().onComplete(ctx.succeeding(started -> {
            // THEN startup succeeds
            ctx.completeNow();
        }));
        start(startupTracker);
    }

    /**
     * Verifies that credentials are successfully loaded from file during startup.
     *
     * @param ctx The test context.
     */
    @SuppressWarnings({ "unchecked" })
    @Test
    public void testDoStartLoadsCredentials(final VertxTestContext ctx) {
        // GIVEN a service configured with a file name
        credentialsConfig.setFilename(CREDENTIALS_FILE_NAME);
        when(fileSystem.existsBlocking(credentialsConfig.getFilename())).thenReturn(Boolean.TRUE);
        doAnswer(invocation -> {
            final Buffer data = DeviceRegistryTestUtils.readFile(credentialsConfig.getFilename());
            final Handler<AsyncResult<Buffer>> handler = invocation.getArgument(1);
            handler.handle(Future.succeededFuture(data));
            return null;
        }).when(fileSystem).readFile(eq(credentialsConfig.getFilename()), any(Handler.class));

        registrationConfig.setFilename(REGISTRATION_FILE_NAME);
        when(fileSystem.existsBlocking(registrationConfig.getFilename())).thenReturn(Boolean.TRUE);
        doAnswer(invocation -> {
            final Buffer data = DeviceRegistryTestUtils.readFile(registrationConfig.getFilename());
            final Handler<AsyncResult<Buffer>> handler = invocation.getArgument(1);
            handler.handle(Future.succeededFuture(data));
            return null;
        }).when(fileSystem).readFile(eq(registrationConfig.getFilename()), any(Handler.class));

        // WHEN the service is started
        final Promise<Void> startTracker = Promise.promise();
        startTracker.future()
                // THEN the credentials from the file are read in
                .compose(s -> assertRegistered(svc,
                        Constants.DEFAULT_TENANT, "sensor1",
                        CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD))
                .compose(s -> getCredentialsManagementService()
                        .readCredentials(Constants.DEFAULT_TENANT, "4711", NoopSpan.INSTANCE)
                        .map(r -> {
                            if (r.getStatus() == HttpURLConnection.HTTP_OK) {
                                return null;
                            } else {
                                throw new ClientErrorException(HttpURLConnection.HTTP_PRECON_FAILED);
                            }
                        }))
                .onComplete(ctx.completing());

        start(startTracker);
    }

    /**
     * Verifies that credentials are ignored if the startEmpty property is set.
     *
     * @param ctx The test context.
     */
    @SuppressWarnings({ "unchecked" })
    @Test
    public void testDoStartIgnoreCredentialIfStartEmptyIsSet(final VertxTestContext ctx) {

        // GIVEN a service configured with a file name and startEmpty set to true
        credentialsConfig.setFilename(CREDENTIALS_FILE_NAME);
        credentialsConfig.setStartEmpty(true);
        registrationConfig.setFilename(REGISTRATION_FILE_NAME);
        registrationConfig.setStartEmpty(true);

        when(fileSystem.existsBlocking(credentialsConfig.getFilename())).thenReturn(Boolean.TRUE);
        when(fileSystem.existsBlocking(registrationConfig.getFilename())).thenReturn(Boolean.TRUE);

        // WHEN the service is started
        final Promise<Void> startTracker = Promise.promise();
        startTracker.future().onComplete(ctx.succeeding(s -> ctx.verify(() -> {
            // THEN the credentials from the file are not loaded
            verify(fileSystem, never()).readFile(anyString(), any(Handler.class));
            ctx.completeNow();
        })));
        start(startTracker);
    }


    /**
     * Verifies that the file written by the registry when persisting the registry's contents can be loaded in again.
     *
     * @param ctx The vert.x test context.
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Test
    public void testLoadCredentialsCanReadOutputOfSaveToFile(final VertxTestContext ctx) {

        // GIVEN a service configured to persist credentials to file
        // that contains some credentials
        credentialsConfig.setFilename(CREDENTIALS_FILE_NAME);
        credentialsConfig.setSaveToFile(true);
        when(fileSystem.existsBlocking(credentialsConfig.getFilename())).thenReturn(Boolean.TRUE);

        // 4700
        final PasswordCredential passwordCredential = Credentials.createPasswordCredential("bumlux", "thepwd");

        // 4711RegistryManagementConstants.FIELD_ID
        final PskCredential pskCredential = Credentials.createPSKCredential("sensor1", "sharedkey");

        setCredentials(getCredentialsManagementService(),
                Constants.DEFAULT_TENANT, "4700",
                List.of(pskCredential))

                        .compose(ok -> {
                            return setCredentials(getCredentialsManagementService(),
                                    "OTHER_TENANT", "4711",
                                    List.of(passwordCredential));
                        })

                        .compose(ok -> {

                            // WHEN saving the registry content to the file
                            final Promise<Void> write = Promise.promise();
                            doAnswer(invocation -> {
                                final Handler handler = invocation.getArgument(2);
                                handler.handle(Future.succeededFuture());
                                write.complete();
                                return null;
                            }).when(fileSystem).writeFile(eq(credentialsConfig.getFilename()), any(Buffer.class),
                                    any(Handler.class));

                            svc.saveToFile();
                            // and clearing the registry
                            svc.clear();
                            return write.future();
                        })

                        .compose(w -> assertNotRegistered(
                                getCredentialsService(),
                                Constants.DEFAULT_TENANT,
                                "sensor1",
                                CredentialsConstants.SECRETS_TYPE_PRESHARED_KEY))

                        .map(w -> {
                            final ArgumentCaptor<Buffer> buffer = ArgumentCaptor.forClass(Buffer.class);
                            ctx.verify(() -> {
                                verify(fileSystem).writeFile(eq(credentialsConfig.getFilename()), buffer.capture(),
                                        any(Handler.class));
                            });
                            return buffer.getValue();
                        })

                        .compose(b -> {

                            // THEN the credentials can be loaded back in from the file
                            final Promise<Void> read = Promise.promise();
                            doAnswer(invocation -> {
                                final Handler<AsyncResult<Buffer>> handler = invocation.getArgument(1);
                                handler.handle(Future.succeededFuture(b));
                                read.complete();
                                return null;
                            }).when(fileSystem).readFile(eq(credentialsConfig.getFilename()), any(Handler.class));

                            svc.loadFromFile();

                            return read.future();
                        })

                        // and the credentials can be looked up again
                        .compose(r -> assertRegistered(
                                getCredentialsService(),
                                Constants.DEFAULT_TENANT,
                                "sensor1",
                                CredentialsConstants.SECRETS_TYPE_PRESHARED_KEY))
                        .compose(ok -> assertRegistered(
                                getCredentialsService(),
                                "OTHER_TENANT",
                                "bumlux",
                                CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD))
                        .onComplete(ctx.completing());
    }

    /**
     * Verifies that the <em>modificationEnabled</em> property prevents updating an existing entry.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testUpdateCredentialsFailsIfModificationIsDisabled(final VertxTestContext ctx) {

        // GIVEN a registry that has been configured to not allow modification of entries
        credentialsConfig.setModificationEnabled(false);

        final CommonCredential secret = createPasswordCredential("myId", "bar", OptionalInt.empty());

        // containing a set of credentials
        setCredentials(getCredentialsManagementService(), "tenant", "device", Collections.singletonList(secret))
                .compose(ok -> {
                    // WHEN trying to update the credentials
                    final PasswordCredential newSecret = createPasswordCredential("myId", "baz", OptionalInt.empty());
                    return svc.updateCredentials("tenant", "device",
                            Collections.singletonList(newSecret),
                            Optional.empty(),
                            NoopSpan.INSTANCE);
                })
                .onComplete(ctx.succeeding(s -> ctx.verify(() -> {
                    // THEN the update fails with a 403
                    assertThat(s.getStatus()).isEqualTo(HttpURLConnection.HTTP_FORBIDDEN);
                    ctx.completeNow();
                })));
    }

    /**
     * Verifies that the properties provided in a client context are matched against
     * the properties of the credentials on record for the device.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testGetCredentialsSucceedsForMatchingClientContext(final VertxTestContext ctx) {
        testGetCredentialsWithClientContext(ctx, "expected-value", "expected-value", HttpURLConnection.HTTP_OK);
    }

    /**
     * Verifies that the properties provided in a client context are matched against
     * the properties of the credentials on record for the device.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testGetCredentialsFailsForNonMatchingClientContext(final VertxTestContext ctx) {
        testGetCredentialsWithClientContext(ctx, "expected-value", "other-value", HttpURLConnection.HTTP_NOT_FOUND);
    }


    /**
     * Verifies that an unauthorised hashing algorithm is refused by the device registry.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testSetCredentialsWithUnauthorisedHashingAlgorithmFails(final VertxTestContext ctx) {

        // GIVEN a service configured to allow only SHA-256.
        final String[] whitelist = {RegistryManagementConstants.HASH_FUNCTION_SHA256};
        credentialsConfig.setHashAlgorithmsWhitelist(whitelist);

        // 4700
        final PasswordCredential passwordCredential = Credentials.createPasswordCredential("bumlux", "thepwd");

        // WHEN trying to set the credentials
            getCredentialsManagementService().updateCredentials(
                "tenant",
                "device",
                List.of(passwordCredential),
                Optional.empty(),
                NoopSpan.INSTANCE)
                .onComplete(ctx.succeeding(s -> ctx.verify(() -> {

                    // THEN the update fails with a 400 BAD REQUEST
                    assertThat(s.getStatus()).isEqualTo(HttpURLConnection.HTTP_BAD_REQUEST);
                    ctx.completeNow();
                }))
        );
    }

    private void testGetCredentialsWithClientContext(
            final VertxTestContext ctx,
            final String expectedContextValue,
            final String providedContextValue,
            final int expectedStatusCode) {

        // GIVEN a device for which credentials are on record that
        // contain a specific extension property
        final var tenantId = "tenant";
        final var deviceId = UUID.randomUUID().toString();
        final var authId = UUID.randomUUID().toString();

        final PskCredential pskCredential = Credentials.createPSKCredential(authId, "haredkey");
        pskCredential.setExtensions(Map.of("property-to-match", expectedContextValue));

        setCredentials(getCredentialsManagementService(), tenantId, deviceId, List.of(pskCredential))
                .compose(ok -> {
                    // WHEN trying to retrieve credentials for a device that provided
                    // a client context that contains a value for the property
                    return getCredentialsService().get(
                            tenantId,
                            CredentialsConstants.SECRETS_TYPE_PRESHARED_KEY,
                            authId,
                            new JsonObject().put("property-to-match", providedContextValue)
                    );
                })
                .onComplete(ctx.succeeding(s -> {
                    // THEN the request contains the expected status code
                    ctx.verify(() -> assertThat(s.getStatus()).isEqualTo(expectedStatusCode));
                    ctx.completeNow();
                }));
    }

}
