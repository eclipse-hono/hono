/**
 * Copyright (c) 2016, 2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 */
package org.eclipse.hono.deviceregistry;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.file.FileSystem;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;

import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.RegistrationConstants;
import org.eclipse.hono.util.RegistrationResult;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;

import static java.net.HttpURLConnection.*;
import static org.eclipse.hono.util.RegistrationConstants.*;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;

import java.nio.charset.StandardCharsets;

/**
 * Tests {@link FileBasedRegistrationService}.
 */
@RunWith(VertxUnitRunner.class)
public class FileBasedRegistrationServiceTest {

    private static final String TENANT = Constants.DEFAULT_TENANT;
    private static final String DEVICE = "4711";
    private static final String GW = "gw-1";
    private static final String FILE_NAME = "/device-identities.json";

    /**
     * Time out all tests after 5 secs.
     */
    @Rule
    public Timeout timeout = Timeout.seconds(5);

    private FileBasedRegistrationConfigProperties props;
    private FileBasedRegistrationService registrationService;
    private Vertx vertx;
    private EventBus eventBus;
    private FileSystem fileSystem;

    /**
     * Sets up the fixture.
     */
    @Before
    public void setUp() {
        fileSystem = mock(FileSystem.class);
        Context ctx = mock(Context.class);
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

    /**
     * Verifies that the registration service creates a file for persisting device registration
     * data if it does not exist yet.
     * 
     * @param ctx The vert.x context.
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Test
    public void testSaveToFileCreatesFile(final TestContext ctx) {

        // GIVEN a registration service configured with a non-existing file
        props.setSaveToFile(true);
        doAnswer(invocation -> {
            Handler handler = invocation.getArgumentAt(2, Handler.class);
            handler.handle(Future.succeededFuture());
            return null;
        }).when(fileSystem).writeFile(eq(props.getFilename()), any(Buffer.class), any(Handler.class));
        when(fileSystem.existsBlocking(props.getFilename())).thenReturn(Boolean.FALSE);
        doAnswer(invocation -> {
            Handler handler = invocation.getArgumentAt(1, Handler.class);
            handler.handle(Future.succeededFuture());
            return null;
        }).when(fileSystem).createFile(eq(props.getFilename()), any(Handler.class));

        // WHEN persisting a dirty registry
        registrationService.addDevice(TENANT, DEVICE, null);
        Async saving = ctx.async();
        registrationService.saveToFile().setHandler(ctx.asyncAssertSuccess(s -> {
            saving.complete();
        }));

        // THEN the file has been created
        saving.await();
        verify(fileSystem).createFile(eq(props.getFilename()), any(Handler.class));
    }

    /**
     * Verifies that the registration service creates a file for persisting device registration
     * data if it does not exist yet during startup.
     * 
     * @param ctx The vert.x context.
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Test
    public void testDoStartCreatesFile(final TestContext ctx) {

        // GIVEN a registration service configured to persist data to a not yet existing file
        props.setSaveToFile(true);
        when(fileSystem.existsBlocking(props.getFilename())).thenReturn(Boolean.FALSE);
        doAnswer(invocation -> {
            Handler handler = invocation.getArgumentAt(1, Handler.class);
            handler.handle(Future.succeededFuture());
            return null;
        }).when(fileSystem).createFile(eq(props.getFilename()), any(Handler.class));
        doAnswer(invocation -> {
            Handler handler = invocation.getArgumentAt(1, Handler.class);
            handler.handle(Future.failedFuture("malformed file"));
            return null;
        }).when(fileSystem).readFile(eq(props.getFilename()), any(Handler.class));

        // WHEN starting the service
        Async startup = ctx.async();
        Future<Void> startupTracker = Future.future();
        startupTracker.setHandler(ctx.asyncAssertSuccess(started -> {
            startup.complete();
        }));
        registrationService.doStart(startupTracker);

        // THEN the file gets created
        startup.await();
        verify(fileSystem).createFile(eq(props.getFilename()), any(Handler.class));
    }

    /**
     * Verifies that the registration service fails to start if it cannot create the file for
     * persisting device registration data during startup.
     * 
     * @param ctx The vert.x context.
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Test
    public void testDoStartFailsIfFileCannotBeCreated(final TestContext ctx) {

        // GIVEN a registration service configured to persist data to a not yet existing file
        props.setSaveToFile(true);
        when(fileSystem.existsBlocking(props.getFilename())).thenReturn(Boolean.FALSE);

        // WHEN starting the service but the file cannot be created
        doAnswer(invocation -> {
            Handler handler = invocation.getArgumentAt(1, Handler.class);
            handler.handle(Future.failedFuture("no access"));
            return null;
        }).when(fileSystem).createFile(eq(props.getFilename()), any(Handler.class));
        Async startup = ctx.async();
        Future<Void> startupTracker = Future.future();
        startupTracker.setHandler(ctx.asyncAssertFailure(started -> {
            startup.complete();
        }));
        registrationService.doStart(startupTracker);

        // THEN startup has failed
        startup.await();
    }

    /**
     * Verifies that the registration service successfully starts up even if
     * the file to read device information from contains malformed JSON.
     * 
     * @param ctx The vert.x context.
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Test
    public void testDoStartIgnoresMalformedJson(final TestContext ctx) {

        // GIVEN a registration service configured to read data from a file
        // that contains malformed JSON
        when(fileSystem.existsBlocking(props.getFilename())).thenReturn(Boolean.TRUE);
        doAnswer(invocation -> {
            final Buffer data = mock(Buffer.class);
            when(data.getBytes()).thenReturn("NO JSON".getBytes(StandardCharsets.UTF_8));
            Handler handler = invocation.getArgumentAt(1, Handler.class);
            handler.handle(Future.succeededFuture(data));
            return null;
        }).when(fileSystem).readFile(eq(props.getFilename()), any(Handler.class));

        // WHEN starting the service
        Async startup = ctx.async();
        Future<Void> startupTracker = Future.future();
        startupTracker.setHandler(ctx.asyncAssertSuccess(started -> {
            startup.complete();
        }));
        registrationService.doStart(startupTracker);

        // THEN startup succeeds
        startup.await();
    }

    /**
     * Verifies that device identities are successfully loaded from file during startup.
     *
     * @param ctx The test context.
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Test
    public void testDoStartLoadsDeviceIdentities(final TestContext ctx) {

        // GIVEN a service configured with a file name
        when(fileSystem.existsBlocking(props.getFilename())).thenReturn(Boolean.TRUE);
        doAnswer(invocation -> {
            final Buffer data = DeviceRegistryTestUtils.readFile(FILE_NAME);
            Handler handler = invocation.getArgumentAt(1, Handler.class);
            handler.handle(Future.succeededFuture(data));
            return null;
        }).when(fileSystem).readFile(eq(props.getFilename()), any(Handler.class));

        // WHEN the service is started
        Async startup = ctx.async();
        Future<Void> startFuture = Future.future();
        startFuture.setHandler(ctx.asyncAssertSuccess(s -> {
            startup.complete();
        }));
        registrationService.doStart(startFuture);

        // THEN the device identities from the file are loaded
        startup.await();
        registrationService.getDevice(TENANT, DEVICE, ctx.asyncAssertSuccess());
        registrationService.getDevice(TENANT, "4712", ctx.asyncAssertSuccess(result -> {
            final JsonObject data = result.getPayload().getJsonObject(RegistrationConstants.FIELD_DATA);
            ctx.assertEquals(data.getString(FileBasedRegistrationService.PROPERTY_VIA), GW);
        }));
        registrationService.getDevice(TENANT, GW, ctx.asyncAssertSuccess());

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
        RegistrationResult result = registrationService.addDevice(TENANT, "newDevice", null);

        // THEN the result contains a FORBIDDEN status code and the device has not been added to the registry
        assertThat(result.getStatus(), is(HTTP_FORBIDDEN));
        assertThat(registrationService.getDevice(TENANT, "newDevice").getStatus(), is(HTTP_NOT_FOUND));
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
        RegistrationResult result = registrationService.updateDevice(TENANT, DEVICE, new JsonObject().put("updated", true));

        // THEN the result contains a FORBIDDEN status code and the device has not been updated
        assertThat(result.getStatus(), is(HTTP_FORBIDDEN));
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
        RegistrationResult result = registrationService.removeDevice(TENANT, DEVICE);

        // THEN the result contains a FORBIDDEN status code and the device has not been removed
        assertThat(result.getStatus(), is(HTTP_FORBIDDEN));
        assertThat(registrationService.getDevice(TENANT, DEVICE).getStatus(), is(HTTP_OK));
    }

    /**
     * Verifies that the registry returns 404 when getting an unknown device.
     */
    @Test
    public void testGetUnknownDeviceReturnsNotFound() {
        processMessageAndExpectResponse(mockMsg(ACTION_GET), getServiceReplyAsJson(HTTP_NOT_FOUND, TENANT, DEVICE));
    }

    /**
     * Verifies that the registry returns 404 when unregistering an unknown device.
     */
    @Test
    public void testDeregisterUnknownDeviceReturnsNotFound() {
        processMessageAndExpectResponse(mockMsg(ACTION_DEREGISTER), getServiceReplyAsJson(HTTP_NOT_FOUND, TENANT, DEVICE));
    }

    /**
     * Verifies that the registry returns 400 when issuing a request with an unsupported action.
     */
    @Test
    public void testProcessRegisterMessageFailsWithUnsupportedAction() {
        processMessageAndExpectResponse(mockMsg("unknown-action"), getServiceReplyAsJson(HTTP_BAD_REQUEST, TENANT, DEVICE));
    }

    /**
     * Verifies that the registry returns 409 when trying to register a device twice.
     */
    @Test
    public void testDuplicateRegistrationFails() {
        processMessageAndExpectResponse(mockMsg(ACTION_REGISTER), getServiceReplyAsJson(HTTP_CREATED, TENANT, DEVICE));
        processMessageAndExpectResponse(mockMsg(ACTION_REGISTER), getServiceReplyAsJson(HTTP_CONFLICT, TENANT, DEVICE));
    }

    /**
     * Verifies that the registry returns 200 when getting an existing device.
     */
    @Test
    public void testGetSucceedsForRegisteredDevice() {
        processMessageAndExpectResponse(mockMsg(ACTION_REGISTER), getServiceReplyAsJson(HTTP_CREATED, TENANT, DEVICE));
        processMessageAndExpectResponse(mockMsg(ACTION_GET), getServiceReplyAsJson(HTTP_OK, TENANT, DEVICE, expectedPayload(DEVICE)));
    }

    /**
     * Verifies that the registry returns 404 when getting an unregistered device.
     */
    @Test
    public void testGetFailsForDeregisteredDevice() {
        processMessageAndExpectResponse(mockMsg(ACTION_REGISTER), getServiceReplyAsJson(HTTP_CREATED, TENANT, DEVICE));
        processMessageAndExpectResponse(mockMsg(ACTION_DEREGISTER), getServiceReplyAsJson(HTTP_NO_CONTENT, TENANT, DEVICE));
        processMessageAndExpectResponse(mockMsg(ACTION_GET), getServiceReplyAsJson(HTTP_NOT_FOUND, TENANT, DEVICE));
    }

    /**
     * Verifies that setting the <em>saveToFile</em> configuration property to <em>false</em> prevents
     * the registration service to write its content to the file system periodically.
     * 
     * @param ctx The vert.x test context.
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Test
    public void testPeriodicSafeJobIsNotScheduledIfSavingIfDisabled(final TestContext ctx) {

        props.setSaveToFile(false);
        when(fileSystem.existsBlocking(props.getFilename())).thenReturn(Boolean.TRUE);
        doAnswer(invocation -> {
            Handler handler = invocation.getArgumentAt(1, Handler.class);
            handler.handle(Future.failedFuture("malformed file"));
            return null;
        }).when(fileSystem).readFile(eq(props.getFilename()), any(Handler.class));

        Async startup = ctx.async();
        Future<Void> startupTracker = Future.future();
        startupTracker.setHandler(ctx.asyncAssertSuccess(done -> {
            startup.complete();
        }));
        registrationService.doStart(startupTracker);

        startup.await();
        verify(vertx, never()).setPeriodic(anyLong(), any(Handler.class));
    }

    /**
     * Verifies that setting the <em>saveToFile</em> configuration property to <em>false</em> prevents
     * the registration service to write its content to the file system during shutdown.
     * 
     * @param ctx The vert.x test context.
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Test
    public void testContentsNotSavedOnShutdownIfSavingIfDisabled(final TestContext ctx) {

        // GIVEN a registration service configured to not persist data
        props.setSaveToFile(false);
        when(fileSystem.existsBlocking(props.getFilename())).thenReturn(Boolean.TRUE);
        doAnswer(invocation -> {
            Handler handler = invocation.getArgumentAt(1, Handler.class);
            handler.handle(Future.failedFuture("malformed data"));
            return null;
        }).when(fileSystem).readFile(eq(props.getFilename()), any(Handler.class));
        Async startup = ctx.async();
        Future<Void> startupTracker = Future.future();
        startupTracker.setHandler(ctx.asyncAssertSuccess(started -> {
            startup.complete();
        }));
        registrationService.doStart(startupTracker);
        startup.await();

        // WHEN adding a device
        registrationService.addDevice(TENANT, DEVICE, new JsonObject());
        // and shutting down the service
        Async shutdown = ctx.async();
        Future<Void> shutdownTracker = Future.future();
        shutdownTracker.setHandler(ctx.asyncAssertSuccess(done -> {
            shutdown.complete();
        }));
        registrationService.doStop(shutdownTracker);

        // THEN no data has been written to the file system
        shutdown.await();
        verify(fileSystem, never()).createFile(eq(props.getFilename()), any(Handler.class));
    }

    private static JsonObject expectedPayload(final String id) {
        return new JsonObject()
                .put(FIELD_DEVICE_ID, id)
                .put(FIELD_DATA, new JsonObject().put(FIELD_ENABLED, Boolean.TRUE));
    }

    private static Message<JsonObject> mockMsg(final String action) {
        return mockMsg(action, TENANT);
    }

    @SuppressWarnings("unchecked")
    private static Message<JsonObject> mockMsg(final String action, final String tenant) {
        final JsonObject registrationJson = getServiceRequestAsJson(action, tenant, DEVICE);
        final Message<JsonObject> message = mock(Message.class);
        when(message.body()).thenReturn(registrationJson);
        return message;
    }

    private void processMessageAndExpectResponse(final Message<JsonObject> request, final JsonObject expectedResponse) {
        registrationService.processRegistrationMessage(request);
        verify(request).reply(expectedResponse);
    }

}
