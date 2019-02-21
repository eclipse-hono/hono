/*******************************************************************************
 * Copyright (c) 2016, 2018 Contributors to the Eclipse Foundation
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;

import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.eclipse.hono.auth.HonoPasswordEncoder;
import org.eclipse.hono.service.credentials.AbstractCompleteCredentialsServiceTest;
import org.eclipse.hono.service.credentials.CompleteBaseCredentialsService;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.CredentialsConstants;
import org.eclipse.hono.util.CredentialsObject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.file.FileSystem;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;


/**
 * Tests verifying behavior of {@link FileBasedCredentialsService}.
 *
 */
@RunWith(VertxUnitRunner.class)
public class FileBasedCredentialsServiceTest extends AbstractCompleteCredentialsServiceTest {

    private static final String FILE_NAME = "/credentials.json";

    /**
     * Time out each test case after 5 seconds.
     */
    @Rule
    public Timeout timeout = Timeout.seconds(5);

    private Vertx vertx;
    private EventBus eventBus;
    private FileSystem fileSystem;
    private FileBasedCredentialsConfigProperties props;
    private FileBasedCredentialsService svc;

    /**
     * Sets up fixture.
     */
    @Before
    public void setUp() {
        fileSystem = mock(FileSystem.class);
        final Context ctx = mock(Context.class);
        eventBus = mock(EventBus.class);
        vertx = mock(Vertx.class);
        when(vertx.eventBus()).thenReturn(eventBus);
        when(vertx.fileSystem()).thenReturn(fileSystem);

        props = new FileBasedCredentialsConfigProperties();
        svc = new FileBasedCredentialsService(mock(HonoPasswordEncoder.class));
        svc.setConfig(props);
        svc.init(vertx, ctx);
    }

    @Override
    public CompleteBaseCredentialsService getCompleteCredentialsService() {
        return svc;
    }

    /**
     * Verifies that the credentials service creates a file for persisting credentials
     * data if it does not exist yet during startup.
     *
     * @param ctx The vert.x context.
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Test
    public void testDoStartCreatesFile(final TestContext ctx) {

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
        final Async startup = ctx.async();
        final Future<Void> startupTracker = Future.future();
        startupTracker.setHandler(ctx.asyncAssertSuccess(started -> {
            startup.complete();
        }));
        svc.doStart(startupTracker);

        // THEN the file gets created
        startup.await();
        verify(fileSystem).createFile(eq(FILE_NAME), any(Handler.class));
    }

    /**
     * Verifies that the credentials service fails to start if it cannot create the file for
     * persisting credentials data during startup.
     *
     * @param ctx The vert.x context.
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Test
    public void testDoStartFailsIfFileCannotBeCreated(final TestContext ctx) {

        // GIVEN a registration service configured to persist data to a not yet existing file
        props.setSaveToFile(true);
        props.setFilename(FILE_NAME);
        when(fileSystem.existsBlocking(FILE_NAME)).thenReturn(Boolean.FALSE);

        // WHEN starting the service but the file cannot be created
        doAnswer(invocation -> {
            final Handler handler = invocation.getArgument(1);
            handler.handle(Future.failedFuture("no access"));
            return null;
        }).when(fileSystem).createFile(eq(props.getFilename()), any(Handler.class));
        final Async startup = ctx.async();
        final Future<Void> startupTracker = Future.future();
        startupTracker.setHandler(ctx.asyncAssertFailure(started -> {
            startup.complete();
        }));
        svc.doStart(startupTracker);

        // THEN startup has failed
        startup.await();
    }

    /**
     * Verifies that the credentials service successfully starts up even if
     * the file to read credentials from contains malformed JSON.
     *
     * @param ctx The vert.x context.
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Test
    public void testDoStartIgnoresMalformedJson(final TestContext ctx) {

        // GIVEN a registration service configured to read data from a file
        // that contains malformed JSON
        props.setFilename(FILE_NAME);
        when(fileSystem.existsBlocking(FILE_NAME)).thenReturn(Boolean.TRUE);
        doAnswer(invocation -> {
            final Buffer data = mock(Buffer.class);
            when(data.getBytes()).thenReturn("NO JSON".getBytes(StandardCharsets.UTF_8));
            final Handler handler = invocation.getArgument(1);
            handler.handle(Future.succeededFuture(data));
            return null;
        }).when(fileSystem).readFile(eq(props.getFilename()), any(Handler.class));

        // WHEN starting the service
        final Async startup = ctx.async();
        final Future<Void> startupTracker = Future.future();
        startupTracker.setHandler(ctx.asyncAssertSuccess(started -> {
            startup.complete();
        }));
        svc.doStart(startupTracker);

        // THEN startup succeeds
        startup.await();
    }

    /**
     * Verifies that credentials are successfully loaded from file during startup.
     *
     * @param ctx The test context.
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Test
    public void testDoStartLoadsCredentials(final TestContext ctx) {

        // GIVEN a service configured with a file name
        props.setFilename(FILE_NAME);
        when(fileSystem.existsBlocking(props.getFilename())).thenReturn(Boolean.TRUE);
        doAnswer(invocation -> {
            final Buffer data = DeviceRegistryTestUtils.readFile(FILE_NAME);
            final Handler handler = invocation.getArgument(1);
            handler.handle(Future.succeededFuture(data));
            return null;
        }).when(fileSystem).readFile(eq(props.getFilename()), any(Handler.class));

        // WHEN the service is started
        final Async startup = ctx.async();
        final Future<Void> startFuture = Future.future();
        startFuture.setHandler(ctx.asyncAssertSuccess(s -> {
            startup.complete();
        }));
        svc.doStart(startFuture);

        // THEN the credentials from the file are loaded
        startup.await();
        assertRegistered(svc, Constants.DEFAULT_TENANT, "sensor1", CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD, ctx);
    }

    /**
     * Verifies that credentials are ignored if the startEmpty property is set.
     *
     * @param ctx The test context.
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Test
    public void testDoStartIgnoreCredentialIfStartEmptyIsSet(final TestContext ctx) {

        // GIVEN a service configured with a file name and startEmpty set to true
        props.setFilename(FILE_NAME);
        props.setStartEmpty(true);
        when(fileSystem.existsBlocking(props.getFilename())).thenReturn(Boolean.TRUE);

        // WHEN the service is started
        final Async startup = ctx.async();
        final Future<Void> startFuture = Future.future();
        startFuture.setHandler(ctx.asyncAssertSuccess(s -> {
            startup.complete();
        }));
        svc.doStart(startFuture);

        // THEN the credentials from the file are not loaded
        startup.await();
        verify(fileSystem, never()).readFile(anyString(), any(Handler.class));
    }


    /**
     * Verifies that the file written by the registry when persisting the registry's contents can
     * be loaded in again.
     *
     * @param ctx The vert.x test context.
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Test
    public void testLoadCredentialsCanReadOutputOfSaveToFile(final TestContext ctx) {

        // GIVEN a service configured to persist credentials to file
        // that contains some credentials
        props.setFilename(FILE_NAME);
        props.setSaveToFile(true);
        when(fileSystem.existsBlocking(FILE_NAME)).thenReturn(Boolean.TRUE);
        final Async add = ctx.async(2);
        final CredentialsObject hashedPassword = CredentialsObject.fromHashedPassword(
                "4700",
                "bumlux",
                "$2a$10$UK9lmSMlYmeXqABkTrDRsu1nlZRnAmGnBdPIWZoDajtjyxX18Dry.",
                CredentialsConstants.HASH_FUNCTION_BCRYPT,
                null, null, null);
        final CredentialsObject psk = CredentialsObject.fromPresharedKey(
                "4711", "sensor1", "sharedkey".getBytes(StandardCharsets.UTF_8), null, null);
        svc.add(
                Constants.DEFAULT_TENANT,
                JsonObject.mapFrom(psk),
                ctx.asyncAssertSuccess(s -> {
                    ctx.assertEquals(HttpURLConnection.HTTP_CREATED, s.getStatus());
                    add.countDown();
                }));
        svc.add(
                "OTHER_TENANT",
                JsonObject.mapFrom(hashedPassword),
                ctx.asyncAssertSuccess(s -> {
                    ctx.assertEquals(HttpURLConnection.HTTP_CREATED, s.getStatus());
                    add.countDown();
                }));
        add.await();

        // WHEN saving the registry content to the file and clearing the registry
        final Async write = ctx.async();
        doAnswer(invocation -> {
            final Handler handler = invocation.getArgument(2);
            handler.handle(Future.succeededFuture());
            write.complete();
            return null;
        }).when(fileSystem).writeFile(eq(FILE_NAME), any(Buffer.class), any(Handler.class));

        svc.saveToFile();
        write.await();
        final ArgumentCaptor<Buffer> buffer = ArgumentCaptor.forClass(Buffer.class);
        verify(fileSystem).writeFile(eq(FILE_NAME), buffer.capture(), any(Handler.class));
        svc.clear();
        assertNotRegistered(svc, Constants.DEFAULT_PATH_SEPARATOR, "sensor1", CredentialsConstants.SECRETS_TYPE_PRESHARED_KEY, ctx);

        // THEN the credentials can be loaded back in from the file
        final Async read = ctx.async();
        doAnswer(invocation -> {
            final Handler handler = invocation.getArgument(1);
            handler.handle(Future.succeededFuture(buffer.getValue()));
            read.complete();
            return null;
        }).when(fileSystem).readFile(eq(FILE_NAME), any(Handler.class));
        svc.loadCredentials();
        read.await();
        assertRegistered(svc, Constants.DEFAULT_TENANT, "sensor1", CredentialsConstants.SECRETS_TYPE_PRESHARED_KEY, ctx);
        assertRegistered(svc, "OTHER_TENANT", "bumlux", CredentialsConstants.SECRETS_TYPE_HASHED_PASSWORD, ctx);

    }

    /**
     * Verifies that the <em>modificationEnabled</em> property prevents updating an existing entry.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testUpdateCredentialsFailsIfModificationIsDisabled(final TestContext ctx) {

        // GIVEN a registry containing a set of credentials
        // that has been configured to not allow modification of entries
        props.setModificationEnabled(false);
        register(getCompleteCredentialsService(), "tenant", "device", "myId", "myType", ctx);

        // WHEN trying to update the credentials
        final Async updateFailure = ctx.async();
        svc.update("tenant", new JsonObject(), ctx.asyncAssertSuccess(s -> {
            ctx.assertEquals(HttpURLConnection.HTTP_FORBIDDEN, s.getStatus());
            updateFailure.complete();
        }));

        // THEN the update fails
        updateFailure.await();
    }

    /**
     * Verifies that the <em>modificationEnabled</em> property prevents removing an existing entry.
     *
     * @param ctx The vert.x test context.
     */
    @Test
    public void testRemoveDeviceFailsIfModificationIsDisabled(final TestContext ctx) {

        // GIVEN a registry containing a set of credentials
        // that has been configured to not allow modification of entries
        props.setModificationEnabled(false);
        register(getCompleteCredentialsService(), "tenant", "device", "myId", "myType", ctx);

        // WHEN trying to remove the credentials
        final Async removeFailure = ctx.async();
        svc.update("tenant", new JsonObject(), ctx.asyncAssertSuccess(s -> {
            ctx.assertEquals(HttpURLConnection.HTTP_FORBIDDEN, s.getStatus());
            removeFailure.complete();
        }));

        // THEN the removal fails
        removeFailure.await();
    }
}
