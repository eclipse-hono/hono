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

package org.eclipse.hono.deviceregistry;

import io.opentracing.noop.NoopSpan;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Optional;
import java.util.OptionalInt;

import org.eclipse.hono.auth.SpringBasedHonoPasswordEncoder;
import org.eclipse.hono.service.management.OperationResult;
import org.eclipse.hono.service.credentials.AbstractCredentialsServiceTest;
import org.eclipse.hono.service.credentials.CredentialsService;
import org.eclipse.hono.service.management.credentials.CommonCredential;
import org.eclipse.hono.service.management.credentials.CredentialsManagementService;
import org.eclipse.hono.service.management.credentials.PasswordCredential;
import org.eclipse.hono.service.management.credentials.PasswordSecret;
import org.eclipse.hono.service.management.credentials.PskCredential;
import org.eclipse.hono.service.management.credentials.PskSecret;
import org.eclipse.hono.service.management.device.DeviceManagementService;
import org.eclipse.hono.util.CacheDirective;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.CredentialsConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.AsyncResult;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.file.FileSystem;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

/**
 * Tests verifying behavior of {@link FileBasedCredentialsService}.
 *
 */
@ExtendWith(VertxExtension.class)
public class FileBasedCredentialsServiceTest extends AbstractCredentialsServiceTest {

    private static final Logger log = LoggerFactory.getLogger(FileBasedCredentialsServiceTest.class);

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
        final Context ctx = mock(Context.class);
        eventBus = mock(EventBus.class);
        vertx = mock(Vertx.class);
        when(vertx.eventBus()).thenReturn(eventBus);
        when(vertx.fileSystem()).thenReturn(fileSystem);

        this.registrationConfig = new FileBasedRegistrationConfigProperties();
        this.registrationConfig.setCacheMaxAge(30);
        this.credentialsConfig = new FileBasedCredentialsConfigProperties();
        this.credentialsConfig.setCacheMaxAge(30);

        this.registrationService = new FileBasedRegistrationService();
        this.registrationService.setConfig(registrationConfig);
        this.registrationService.init(this.vertx, ctx);

        this.credentialsService = new FileBasedCredentialsService();
        this.credentialsService.setPasswordEncoder(new SpringBasedHonoPasswordEncoder());
        this.credentialsService.setConfig(credentialsConfig);
        this.credentialsService.init(this.vertx, ctx);

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
        switch(credentialsType) {
        case CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD:
        case CredentialsConstants.SECRETS_TYPE_X509_CERT:
            return CacheDirective.maxAgeDirective(registrationConfig.getCacheMaxAge());
        default:
            return CacheDirective.noCacheDirective();
        }
    }

    private void start(final Future<?> startupTracker) {

        final Future<Void> registrationStartupTracker = Future.future();
        final Future<Void> credentialsStartupTracker = Future.future();

        this.registrationService.start(registrationStartupTracker);
        this.credentialsService.start(credentialsStartupTracker);

        CompositeFuture
                .all(registrationStartupTracker, credentialsStartupTracker)
                .setHandler(result -> {
                    log.debug("Startup complete", result.cause());
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
        final Future<?> startupTracker = Future.future();
        startupTracker.setHandler(ctx.succeeding(started -> ctx.verify(() -> {
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

        final Future<Void> startupTracker = Future.future();
        startupTracker.setHandler(ctx.failing(started -> {
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
        final Future<Void> startupTracker = Future.future();
        startupTracker.setHandler(ctx.succeeding(started -> {
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
        final Future<Void> startFuture = Future.future();
        startFuture
                // THEN the credentials from the file are read in
                .compose(s -> assertRegistered(svc,
                        Constants.DEFAULT_TENANT, "sensor1",
                        CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD))
                .setHandler(ctx.completing());

        start(startFuture);
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
        final Future<Void> startFuture = Future.future();
        startFuture.setHandler(ctx.succeeding(s -> ctx.verify(() -> {
            // THEN the credentials from the file are not loaded
            verify(fileSystem, never()).readFile(anyString(), any(Handler.class));
            ctx.completeNow();
        })));
        start(startFuture);
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
        final PasswordCredential passwordCredential = new PasswordCredential();
        passwordCredential.setAuthId("bumlux");

        final PasswordSecret hashedPassword = new PasswordSecret();
        hashedPassword.setPasswordHash("$2a$10$UK9lmSMlYmeXqABkTrDRsu1nlZRnAmGnBdPIWZoDajtjyxX18Dry.");
        hashedPassword.setHashFunction(CredentialsConstants.HASH_FUNCTION_BCRYPT);
        passwordCredential.setSecrets(Collections.singletonList(hashedPassword));

        // 4711
        final PskCredential pskCredential = new PskCredential();
        pskCredential.setAuthId("sensor1");

        final PskSecret pskSecret = new PskSecret();
        pskSecret.setKey("sharedkey".getBytes(StandardCharsets.UTF_8));
        pskCredential.setSecrets(Collections.singletonList(pskSecret));

        setCredentials(getCredentialsManagementService(),
                Constants.DEFAULT_TENANT, "4700",
                Collections.<CommonCredential> singletonList(pskCredential))

                        .compose(ok -> {
                            return setCredentials(getCredentialsManagementService(),
                                    "OTHER_TENANT", "4711",
                                    Collections.<CommonCredential> singletonList(passwordCredential));
                        })

                        .compose(ok -> {

                            // WHEN saving the registry content to the file
                            final Future<Void> write = Future.future();
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
                            return write;
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
                            final Future<Void> read = Future.future();
                            doAnswer(invocation -> {
                                final Handler<AsyncResult<Buffer>> handler = invocation.getArgument(1);
                                handler.handle(Future.succeededFuture(b));
                                read.complete();
                                return null;
                            }).when(fileSystem).readFile(eq(credentialsConfig.getFilename()), any(Handler.class));

                            svc.loadFromFile();

                            return read;
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
                        .setHandler(ctx.completing());
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
                    final Future<OperationResult<Void>> result = Future.future();
                    // WHEN trying to update the credentials
                    final PasswordCredential newSecret = createPasswordCredential("myId", "baz", OptionalInt.empty());
                    svc.set("tenant", "device",
                            Optional.empty(),
                            Collections.singletonList(newSecret),
                            NoopSpan.INSTANCE,
                            result);
                    return result;
                })
                .setHandler(ctx.succeeding(s -> ctx.verify(() -> {
                    // THEN the update fails with a 403
                    assertEquals(HttpURLConnection.HTTP_FORBIDDEN, s.getStatus());
                    ctx.completeNow();
                })));
    }

}
