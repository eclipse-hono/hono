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

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;

import org.eclipse.hono.client.ServiceInvocationException;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.EventBusMessage;
import org.eclipse.hono.util.RegistrationConstants;
import org.eclipse.hono.util.RegistrationResult;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;

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
import io.vertx.ext.unit.junit.VertxUnitRunner;

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
     * Time out each test after 5 seconds.
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
            Handler handler = invocation.getArgument(2);
            handler.handle(Future.succeededFuture());
            return null;
        }).when(fileSystem).writeFile(eq(props.getFilename()), any(Buffer.class), any(Handler.class));
        when(fileSystem.existsBlocking(props.getFilename())).thenReturn(Boolean.FALSE);
        doAnswer(invocation -> {
            Handler handler = invocation.getArgument(1);
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
            Handler handler = invocation.getArgument(1);
            handler.handle(Future.succeededFuture());
            return null;
        }).when(fileSystem).createFile(eq(props.getFilename()), any(Handler.class));
        doAnswer(invocation -> {
            Handler handler = invocation.getArgument(1);
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
            Handler handler = invocation.getArgument(1);
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
            Handler handler = invocation.getArgument(1);
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
            Handler handler = invocation.getArgument(1);
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
        assertThat(result.getStatus(), is(HttpURLConnection.HTTP_FORBIDDEN));
        assertThat(registrationService.getDevice(TENANT, "newDevice").getStatus(), is(HttpURLConnection.HTTP_NOT_FOUND));
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
        assertThat(result.getStatus(), is(HttpURLConnection.HTTP_FORBIDDEN));
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
        assertThat(result.getStatus(), is(HttpURLConnection.HTTP_FORBIDDEN));
        assertThat(registrationService.getDevice(TENANT, DEVICE).getStatus(), is(HttpURLConnection.HTTP_OK));
    }

    /**
     * Verifies that the registry returns 404 when getting an unknown device.
     * 
     * @param ctx The vert.x test context.
     */
    @Test
    public void testGetUnknownDeviceReturnsNotFound(final TestContext ctx) {

        registrationService
            .processRequest(newRequest(RegistrationConstants.ACTION_GET, TENANT))
            .setHandler(ctx.asyncAssertSuccess(response -> {
                ctx.assertEquals(HttpURLConnection.HTTP_NOT_FOUND, response.getStatus());
            }));
    }

    /**
     * Verifies that the registry returns 404 when unregistering an unknown device.
     * 
     * @param ctx The vert.x test context.
     */
    @Test
    public void testDeregisterUnknownDeviceReturnsNotFound(final TestContext ctx) {

        registrationService
            .processRequest(newRequest(RegistrationConstants.ACTION_DEREGISTER, TENANT))
            .setHandler(ctx.asyncAssertSuccess(response -> {
                ctx.assertEquals(HttpURLConnection.HTTP_NOT_FOUND, response.getStatus());
            }));
    }

    /**
     * Verifies that the registry returns 400 when issuing a request with an unsupported action.
     * 
     * @param ctx The vert.x test context.
     */
    @Test
    public void testProcessRequestFailsWithUnsupportedAction(final TestContext ctx) {

        registrationService
            .processRequest(EventBusMessage.forOperation("unknown-action"))
            .setHandler(ctx.asyncAssertFailure(t -> {
                ctx.assertEquals(HttpURLConnection.HTTP_BAD_REQUEST, ((ServiceInvocationException) t).getErrorCode());
            }));
    }

    /**
     * Verifies that the registry returns 409 when trying to register a device twice.
     * 
     * @param ctx The vert.x test context.
     */
    @Test
    public void testDuplicateRegistrationFails(final TestContext ctx) {

        final EventBusMessage createRequest = newRequest(RegistrationConstants.ACTION_REGISTER, TENANT);

        registrationService.processRequest(createRequest).map(response -> {
            ctx.assertEquals(HttpURLConnection.HTTP_CREATED, response.getStatus());
            return response;
        }).compose(ok -> registrationService.processRequest(createRequest)).setHandler(ctx.asyncAssertSuccess(response -> {
            ctx.assertEquals(HttpURLConnection.HTTP_CONFLICT, response.getStatus());
        }));
    }

    /**
     * Verifies that the registry returns 200 when getting an existing device.
     * 
     * @param ctx The vert.x test context.
     */
    @Test
    public void testGetSucceedsForRegisteredDevice(final TestContext ctx) {

        final EventBusMessage createRequest = newRequest(RegistrationConstants.ACTION_REGISTER, TENANT);
        final EventBusMessage getRequest = newRequest(RegistrationConstants.ACTION_GET, TENANT);

        registrationService.processRequest(createRequest).compose(response -> {
            ctx.assertEquals(HttpURLConnection.HTTP_CREATED, response.getStatus());
            return registrationService.processRequest(getRequest);
        }).setHandler(ctx.asyncAssertSuccess(response -> {
            ctx.assertEquals(HttpURLConnection.HTTP_OK, response.getStatus());
            ctx.assertNotNull(response.getJsonPayload());
        }));
    }

    /**
     * Verifies that the registry returns 404 when getting an unregistered device.
     * 
     * @param ctx The vert.x test context.
     */
    @Test
    public void testGetFailsForDeregisteredDevice(final TestContext ctx) {

        final EventBusMessage createRequest = newRequest(RegistrationConstants.ACTION_REGISTER, TENANT);
        final EventBusMessage deregisterRequest = newRequest(RegistrationConstants.ACTION_DEREGISTER, TENANT);
        final EventBusMessage getRequest = newRequest(RegistrationConstants.ACTION_GET, TENANT);

        registrationService.processRequest(createRequest).compose(response -> {
            ctx.assertEquals(HttpURLConnection.HTTP_CREATED, response.getStatus());
            return registrationService.processRequest(deregisterRequest);
        }).compose(response -> {
            ctx.assertEquals(HttpURLConnection.HTTP_NO_CONTENT, response.getStatus());
            return registrationService.processRequest(getRequest);
        }).setHandler(ctx.asyncAssertSuccess(response -> {
            ctx.assertEquals(HttpURLConnection.HTTP_NOT_FOUND, response.getStatus());
        }));
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
            Handler handler = invocation.getArgument(1);
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
            Handler handler = invocation.getArgument(1);
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

    private static EventBusMessage newRequest(final String operation, final String tenant) {
        return EventBusMessage.forOperation(operation).setTenant(tenant).setDeviceId(DEVICE);
    }
}
