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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.HttpURLConnection;

import org.eclipse.hono.service.registration.AbstractCompleteRegistrationServiceTest;
import org.eclipse.hono.service.registration.CompleteBaseRegistrationService;
import org.eclipse.hono.util.RegistrationConstants;
import org.eclipse.hono.util.RegistrationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.file.FileSystem;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

/**
 * Tests {@link FileBasedRegistrationService}.
 */
@ExtendWith(VertxExtension.class)
public class FileBasedRegistrationServiceTest extends AbstractCompleteRegistrationServiceTest {

    private static final String FILE_NAME = "/device-identities.json";


    private FileBasedRegistrationConfigProperties props;
    private FileBasedRegistrationService registrationService;
    private Vertx vertx;
    private EventBus eventBus;
    private FileSystem fileSystem;

    /**
     * Sets up the fixture.
     */
    @BeforeEach
    public void setUp() {
        fileSystem = mock(FileSystem.class);
        final Context ctx = mock(Context.class);
        eventBus = mock(EventBus.class);
        vertx = mock(Vertx.class);
        when(vertx.eventBus()).thenReturn(eventBus);
        when(vertx.fileSystem()).thenReturn(fileSystem);

        props = new FileBasedRegistrationConfigProperties();
        props.setFilename(FILE_NAME);
        registrationService = new FileBasedRegistrationService();
        registrationService.setConfig(props);
        registrationService.init(vertx, ctx);
    }

    @Override
    public CompleteBaseRegistrationService<FileBasedRegistrationConfigProperties> getCompleteRegistrationService() {
        return registrationService;
    }

    /**
     * Verifies that the registration service creates a file for persisting device registration
     * data if it does not exist yet.
     * 
     * @param ctx The vert.x context.
     */
    @SuppressWarnings({ "unchecked" })
    @Test
    public void testSaveToFileCreatesFile(final VertxTestContext ctx) {

        // GIVEN a registration service configured with a non-existing file
        props.setSaveToFile(true);
        doAnswer(invocation -> {
            final Handler<AsyncResult<Void>> handler = invocation.getArgument(2);
            handler.handle(Future.succeededFuture());
            return null;
        }).when(fileSystem).writeFile(eq(props.getFilename()), any(Buffer.class), any(Handler.class));
        when(fileSystem.existsBlocking(props.getFilename())).thenReturn(Boolean.FALSE);
        doAnswer(invocation -> {
            final Handler<AsyncResult<Void>> handler = invocation.getArgument(1);
            handler.handle(Future.succeededFuture());
            return null;
        }).when(fileSystem).createFile(eq(props.getFilename()), any(Handler.class));

        // WHEN persisting a dirty registry
        registrationService.addDevice(TENANT, DEVICE, null);
        registrationService.saveToFile().setHandler(ctx.succeeding(s -> ctx.verify(() -> {
            // THEN the file has been created
            verify(fileSystem).createFile(eq(props.getFilename()), any(Handler.class));
            ctx.completeNow();
        })));
    }

    /**
     * Verifies that the registration service creates a file for persisting device registration
     * data if it does not exist yet during startup.
     * 
     * @param ctx The vert.x context.
     */
    @SuppressWarnings({ "unchecked" })
    @Test
    public void testDoStartCreatesFile(final VertxTestContext ctx) {

        // GIVEN a registration service configured to persist data to a not yet existing file
        props.setSaveToFile(true);
        when(fileSystem.existsBlocking(props.getFilename())).thenReturn(Boolean.FALSE);
        doAnswer(invocation -> {
            final Handler<AsyncResult<Void>> handler = invocation.getArgument(1);
            handler.handle(Future.succeededFuture());
            return null;
        }).when(fileSystem).createFile(eq(props.getFilename()), any(Handler.class));
        doAnswer(invocation -> {
            final Handler<AsyncResult<Buffer>> handler = invocation.getArgument(1);
            handler.handle(Future.failedFuture("malformed file"));
            return null;
        }).when(fileSystem).readFile(eq(props.getFilename()), any(Handler.class));

        // WHEN starting the service
        final Future<Void> startupTracker = Future.future();
        startupTracker.setHandler(ctx.succeeding(started -> ctx.verify(() -> {
            // THEN the file gets created
            verify(fileSystem).createFile(eq(props.getFilename()), any(Handler.class));
            ctx.completeNow();
        })));
        registrationService.doStart(startupTracker);
    }

    /**
     * Verifies that the registration service fails to start if it cannot create the file for
     * persisting device registration data during startup.
     * 
     * @param ctx The vert.x context.
     */
    @SuppressWarnings({ "unchecked" })
    @Test
    public void testDoStartFailsIfFileCannotBeCreated(final VertxTestContext ctx) {

        // GIVEN a registration service configured to persist data to a not yet existing file
        props.setSaveToFile(true);
        when(fileSystem.existsBlocking(props.getFilename())).thenReturn(Boolean.FALSE);

        // WHEN starting the service but the file cannot be created
        doAnswer(invocation -> {
            final Handler<AsyncResult<Void>> handler = invocation.getArgument(1);
            handler.handle(Future.failedFuture("no access"));
            return null;
        }).when(fileSystem).createFile(eq(props.getFilename()), any(Handler.class));

        final Future<Void> startupTracker = Future.future();
        startupTracker.setHandler(ctx.failing(started -> {
            // THEN startup has failed
            ctx.completeNow();
        }));
        registrationService.doStart(startupTracker);



    }

    /**
     * Verifies that the registration service successfully starts up even if
     * the file to read device information from contains malformed JSON.
     * 
     * @param ctx The vert.x context.
     */
    @SuppressWarnings({ "unchecked" })
    @Test
    public void testDoStartIgnoresMalformedJson(final VertxTestContext ctx) {

        // GIVEN a registration service configured to read data from a file
        // that contains malformed JSON
        when(fileSystem.existsBlocking(props.getFilename())).thenReturn(Boolean.TRUE);
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
        registrationService.doStart(startupTracker);
    }

    /**
     * Verifies that device identities are successfully loaded from file during startup.
     *
     * @param ctx The test context.
     */
    @SuppressWarnings({ "unchecked" })
    @Test
    public void testDoStartLoadsDeviceIdentities(final VertxTestContext ctx) {

        // GIVEN a service configured with a file name
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
        .compose(ok -> assertRegistered(TENANT, DEVICE))
        .compose(ok -> assertRegistered(TENANT, GW))
        .compose(ok -> assertRegistered(TENANT, "4712"))
        .setHandler(ctx.succeeding(result -> {
            ctx.verify(() -> {
                final JsonObject data = result.getPayload().getJsonObject(RegistrationConstants.FIELD_DATA);
                assertEquals(data.getString(RegistrationConstants.FIELD_VIA), GW);
            });
            ctx.completeNow();
        }));
        registrationService.doStart(startFuture);
    }

    /**
     * Verifies that device identities in file are ignored if startEmpty is set to true.
     *
     * @param ctx The test context.
     */
    @SuppressWarnings({ "unchecked" })
    @Test
    public void testDoStartIgnoreIdentitiesIfStartEmptyIsSet(final VertxTestContext ctx) {

        // GIVEN a service configured with a file name and startEmpty set to true
        props.setFilename(FILE_NAME);
        props.setStartEmpty(true);
        when(fileSystem.existsBlocking(props.getFilename())).thenReturn(Boolean.TRUE);

        // WHEN the service is started
        final Future<Void> startFuture = Future.future();
        startFuture.setHandler(ctx.succeeding(s -> ctx.verify(() -> {
            // THEN the device identities from the file are not loaded
            verify(fileSystem, never()).readFile(anyString(), any(Handler.class));
            ctx.completeNow();
        })));
        registrationService.doStart(startFuture);
    }

    /**
     * Verifies that the registry enforces the maximum devices per tenant limit.
     */
    @Test
    public void testAddDeviceFailsIfDeviceLimitIsReached() {

        // GIVEN a registry whose devices-per-tenant limit has been reached
        props.setMaxDevicesPerTenant(1);
        registrationService.addDevice(TENANT, DEVICE, null);

        // WHEN registering an additional device for the tenant
        final RegistrationResult result = registrationService.addDevice(TENANT, "newDevice", null);

        // THEN the result contains a FORBIDDEN status code and the device has not been added to the registry
        assertEquals(HttpURLConnection.HTTP_FORBIDDEN, result.getStatus());
        assertEquals(HttpURLConnection.HTTP_NOT_FOUND, registrationService.getDevice(TENANT, "newDevice").getStatus());
    }

    /**
     * Verifies that the <em>modificationEnabled</em> property prevents updating an existing entry.
     */
    @Test
    public void testUpdateDeviceFailsIfModificationIsDisabled() {

        // GIVEN a registry that has been configured to not allow modification of entries
        // which contains a device
        props.setModificationEnabled(false);
        registrationService.addDevice(TENANT, DEVICE, null);

        // WHEN trying to update the device
        final RegistrationResult result = registrationService.updateDevice(TENANT, DEVICE, new JsonObject().put("updated", true));

        // THEN the result contains a FORBIDDEN status code and the device has not been updated
        assertEquals(HttpURLConnection.HTTP_FORBIDDEN, result.getStatus());
        assertFalse(registrationService.getDevice(TENANT, DEVICE).getPayload().containsKey("updated"));
    }

    /**
     * Verifies that the <em>modificationEnabled</em> property prevents removing an existing entry.
     */
    @Test
    public void testRemoveDeviceFailsIfModificationIsDisabled() {

        // GIVEN a registry that has been configured to not allow modification of entries
        // which contains a device
        props.setModificationEnabled(false);
        registrationService.addDevice(TENANT, DEVICE, null);

        // WHEN trying to remove the device
        final RegistrationResult result = registrationService.removeDevice(TENANT, DEVICE);

        // THEN the result contains a FORBIDDEN status code and the device has not been removed
        assertEquals(HttpURLConnection.HTTP_FORBIDDEN, result.getStatus());
        assertEquals(HttpURLConnection.HTTP_OK, registrationService.getDevice(TENANT, DEVICE).getStatus());
    }

    /**
     * Verifies that setting the <em>saveToFile</em> configuration property to <em>false</em> prevents
     * the registration service to write its content to the file system periodically.
     * 
     * @param ctx The vert.x test context.
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Test
    public void testPeriodicSafeJobIsNotScheduledIfSavingIfDisabled(final VertxTestContext ctx) {

        props.setSaveToFile(false);
        when(fileSystem.existsBlocking(props.getFilename())).thenReturn(Boolean.TRUE);
        doAnswer(invocation -> {
            final Handler handler = invocation.getArgument(1);
            handler.handle(Future.failedFuture("malformed file"));
            return null;
        }).when(fileSystem).readFile(eq(props.getFilename()), any(Handler.class));

        final Future<Void> startupTracker = Future.future();
        startupTracker.setHandler(ctx.succeeding(done -> ctx.verify(() -> {
            verify(vertx, never()).setPeriodic(anyLong(), any(Handler.class));
            ctx.completeNow();
        })));
        registrationService.doStart(startupTracker);
    }

    /**
     * Verifies that setting the <em>saveToFile</em> configuration property to <em>false</em> prevents
     * the registration service to write its content to the file system during shutdown.
     * 
     * @param ctx The vert.x test context.
     */
    @SuppressWarnings({ "unchecked" })
    @Test
    public void testContentsNotSavedOnShutdownIfSavingIfDisabled(final VertxTestContext ctx) {

        // GIVEN a registration service configured to not persist data
        props.setSaveToFile(false);
        when(fileSystem.existsBlocking(props.getFilename())).thenReturn(Boolean.TRUE);
        doAnswer(invocation -> {
            final Handler<AsyncResult<Buffer>> handler = invocation.getArgument(1);
            handler.handle(Future.failedFuture("malformed data"));
            return null;
        }).when(fileSystem).readFile(eq(props.getFilename()), any(Handler.class));

        final Future<Void> startupTracker = Future.future();
        startupTracker
        .compose(ok -> {
            // WHEN adding a device
            registrationService.addDevice(TENANT, DEVICE, new JsonObject());
            final Future<Void> shutdownTracker = Future.future();
            registrationService.doStop(shutdownTracker);
            return shutdownTracker;
        })
        .setHandler(ctx.succeeding(shutDown -> ctx.verify(() -> {
            // THEN no data has been written to the file system
            verify(fileSystem, never()).createFile(eq(props.getFilename()), any(Handler.class));
            ctx.completeNow();
        })));
        registrationService.doStart(startupTracker);
    }
}
