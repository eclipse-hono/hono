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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;

import org.eclipse.hono.auth.HonoPasswordEncoder;
import org.eclipse.hono.service.credentials.AbstractCompleteCredentialsServiceTest;
import org.eclipse.hono.service.credentials.CompleteBaseCredentialsService;
import org.eclipse.hono.util.CacheDirective;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.CredentialsConstants;
import org.eclipse.hono.util.CredentialsObject;
import org.eclipse.hono.util.CredentialsResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.file.FileSystem;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxTestContext;
import io.vertx.junit5.VertxExtension;

/**
 * Tests verifying behavior of {@link FileBasedCredentialsService}.
 *
 */
@ExtendWith(VertxExtension.class)
public class FileBasedCredentialsServiceTest extends AbstractCompleteCredentialsServiceTest {

    private static final String FILE_NAME = "/credentials.json";

    private Vertx vertx;
    private EventBus eventBus;
    private FileSystem fileSystem;
    private FileBasedCredentialsConfigProperties props;
    private FileBasedCredentialsService svc;

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

        props = new FileBasedCredentialsConfigProperties();
        props.setCacheMaxAge(30);
        svc = new FileBasedCredentialsService(mock(HonoPasswordEncoder.class));
        svc.setConfig(props);
        svc.init(vertx, ctx);
    }

    @Override
    public CompleteBaseCredentialsService<FileBasedCredentialsConfigProperties> getCompleteCredentialsService() {
        return svc;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected CacheDirective getExpectedCacheDirective(final String credentialsType) {
        switch(credentialsType) {
        case CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD:
        case CredentialsConstants.SECRETS_TYPE_X509_CERT:
            return CacheDirective.maxAgeDirective(props.getCacheMaxAge());
        default:
            return CacheDirective.noCacheDirective();
        }
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
        props.setSaveToFile(true);
        props.setFilename(FILE_NAME);
        when(fileSystem.existsBlocking(FILE_NAME)).thenReturn(Boolean.FALSE);
        doAnswer(invocation -> {
            final Handler handler = invocation.getArgument(1);
            handler.handle(Future.succeededFuture());
            return null;
        }).when(fileSystem).createFile(eq(props.getFilename()), any(Handler.class));
        doAnswer(invocation -> {
            final Handler handler = invocation.getArgument(1);
            handler.handle(Future.failedFuture("malformed file"));
            return null;
        }).when(fileSystem).readFile(eq(props.getFilename()), any(Handler.class));

        // WHEN starting the service
        final Future<Void> startupTracker = Future.future();
        startupTracker.setHandler(ctx.succeeding(started -> ctx.verify(() -> {
            // THEN the file gets created
            verify(fileSystem).createFile(eq(FILE_NAME), any(Handler.class));
            ctx.completeNow();
        })));
        svc.doStart(startupTracker);


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
        props.setSaveToFile(true);
        props.setFilename(FILE_NAME);
        when(fileSystem.existsBlocking(FILE_NAME)).thenReturn(Boolean.FALSE);

        // WHEN starting the service but the file cannot be created
        doAnswer(invocation -> {
            final Handler<AsyncResult<Void>> handler = invocation.getArgument(1);
            handler.handle(Future.failedFuture("no access"));
            return null;
        }).when(fileSystem).createFile(eq(props.getFilename()), any(Handler.class));

        final Future<Void> startupTracker = Future.future();
        startupTracker.setHandler(ctx.failing(started -> {
            ctx.completeNow();
        }));
        svc.doStart(startupTracker);
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
        props.setFilename(FILE_NAME);
        when(fileSystem.existsBlocking(FILE_NAME)).thenReturn(Boolean.TRUE);
        doAnswer(invocation -> {
            final Handler<AsyncResult<Buffer>> handler = invocation.getArgument(1);
            handler.handle(Future.succeededFuture(Buffer.buffer("NO JSON")));
            return null;
        }).when(fileSystem).readFile(eq(props.getFilename()), any(Handler.class));

        // WHEN starting the service
        final Future<Void> startupTracker = Future.future();
        startupTracker.setHandler(ctx.succeeding(started -> {
            // THEN startup succeeds
            ctx.completeNow();
        }));
        svc.doStart(startupTracker);
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
        props.setFilename(FILE_NAME);
        when(fileSystem.existsBlocking(props.getFilename())).thenReturn(Boolean.TRUE);
        doAnswer(invocation -> {
            final Buffer data = DeviceRegistryTestUtils.readFile(FILE_NAME);
            final Handler<AsyncResult<Buffer>> handler = invocation.getArgument(1);
            handler.handle(Future.succeededFuture(data));
            return null;
        }).when(fileSystem).readFile(eq(props.getFilename()), any(Handler.class));

        // WHEN the service is started
        final Future<Void> startFuture = Future.future();
        startFuture
        // THEN the credentials ffrom the file are read in
        .compose(s -> assertRegistered(svc, Constants.DEFAULT_TENANT, "sensor1", CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD))
        .setHandler(ctx.completing());

        svc.doStart(startFuture);
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
        props.setFilename(FILE_NAME);
        props.setStartEmpty(true);
        when(fileSystem.existsBlocking(props.getFilename())).thenReturn(Boolean.TRUE);

        // WHEN the service is started
        final Future<Void> startFuture = Future.future();
        startFuture.setHandler(ctx.succeeding(s -> ctx.verify(() -> {
            // THEN the credentials from the file are not loaded
            verify(fileSystem, never()).readFile(anyString(), any(Handler.class));
            ctx.completeNow();
        })));
        svc.doStart(startFuture);
    }


    /**
     * Verifies that the file written by the registry when persisting the registry's contents can
     * be loaded in again.
     *
     * @param ctx The vert.x test context.
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Test
    public void testLoadCredentialsCanReadOutputOfSaveToFile(final VertxTestContext ctx){

        // GIVEN a service configured to persist credentials to file
        // that contains some credentials
        props.setFilename(FILE_NAME);
        props.setSaveToFile(true);
        when(fileSystem.existsBlocking(FILE_NAME)).thenReturn(Boolean.TRUE);

        final CredentialsObject hashedPassword = CredentialsObject.fromHashedPassword(
                "4700",
                "bumlux",
                "$2a$10$UK9lmSMlYmeXqABkTrDRsu1nlZRnAmGnBdPIWZoDajtjyxX18Dry.",
                CredentialsConstants.HASH_FUNCTION_BCRYPT,
                null, null, null);
        final CredentialsObject psk = CredentialsObject.fromPresharedKey(
                "4711", "sensor1", "sharedkey".getBytes(StandardCharsets.UTF_8), null, null);

        register(getCompleteCredentialsService(), Constants.DEFAULT_TENANT, JsonObject.mapFrom(psk))
        .compose(ok -> register(getCompleteCredentialsService(), "OTHER_TENANT", JsonObject.mapFrom(hashedPassword)))
        .compose(ok -> {

            // WHEN saving the registry content to the file
            final Future<Void> write = Future.future();
            doAnswer(invocation -> {
                final Handler handler = invocation.getArgument(2);
                handler.handle(Future.succeededFuture());
                write.complete();
                return null;
            }).when(fileSystem).writeFile(eq(FILE_NAME), any(Buffer.class), any(Handler.class));

            svc.saveToFile();
            // and clearing the registry
            svc.clear();
            return write;
        })
        .compose(w -> assertNotRegistered(
                getCompleteCredentialsService(),
                Constants.DEFAULT_TENANT,
                "sensor1",
                CredentialsConstants.SECRETS_TYPE_PRESHARED_KEY))
        .map(w -> {
            final ArgumentCaptor<Buffer> buffer = ArgumentCaptor.forClass(Buffer.class);
            ctx.verify(() -> {
                verify(fileSystem).writeFile(eq(FILE_NAME), buffer.capture(), any(Handler.class));
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
            }).when(fileSystem).readFile(eq(FILE_NAME), any(Handler.class));

            svc.loadCredentials();
            return read;
        })
        // and the credentials can be looked up again
        .compose(r -> assertRegistered(
                getCompleteCredentialsService(),
                Constants.DEFAULT_TENANT,
                "sensor1",
                CredentialsConstants.SECRETS_TYPE_PRESHARED_KEY))
        .compose(ok -> assertRegistered(
                getCompleteCredentialsService(),
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
        props.setModificationEnabled(false);
        // containing a set of credentials
        register(getCompleteCredentialsService(), "tenant", "device", "myId", "myType")
        .compose(ok -> {
            final Future<CredentialsResult<JsonObject>> result = Future.future();
            // WHEN trying to update the credentials
            svc.update("tenant", new JsonObject(), result);
            return result;
        })
        .setHandler(ctx.succeeding(s -> ctx.verify(() -> {
            // THEN the update fails with a 403
            assertEquals(HttpURLConnection.HTTP_FORBIDDEN, s.getStatus());
            ctx.completeNow();
        })));
    }

    /**
     * Verifies that the <em>modificationEnabled</em> property prevents removing an existing entry.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testRemoveDeviceFailsIfModificationIsDisabled(final VertxTestContext ctx) {

        // GIVEN a registry that has been configured to not allow modification of entries
        props.setModificationEnabled(false);
        // containing a set of credentials
        register(getCompleteCredentialsService(), "tenant", "device", "myId", "myType")
        .compose(ok -> {
            final Future<CredentialsResult<JsonObject>> result = Future.future();
            // WHEN trying to remove the credentials
            svc.remove("tenant", "myType", "myId", result);
            return result;
        })
        .setHandler(ctx.succeeding(s -> ctx.verify(() -> {
            // THEN the removal fails with a 403
            assertEquals(HttpURLConnection.HTTP_FORBIDDEN, s.getStatus());
            ctx.completeNow();
        })));
    }
}
