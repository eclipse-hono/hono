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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.eclipse.hono.adapter.MessagingClientSet;
import org.eclipse.hono.adapter.MessagingClients;
import org.eclipse.hono.adapter.client.command.CommandResponseSender;
import org.eclipse.hono.adapter.client.telemetry.EventSender;
import org.eclipse.hono.adapter.client.telemetry.TelemetrySender;
import org.eclipse.hono.deviceregistry.DeviceRegistryTestUtils;
import org.eclipse.hono.deviceregistry.service.device.AutoProvisioner;
import org.eclipse.hono.deviceregistry.service.device.AutoProvisionerConfigProperties;
import org.eclipse.hono.deviceregistry.service.tenant.TenantInformationService;
import org.eclipse.hono.service.management.Id;
import org.eclipse.hono.service.management.OperationResult;
import org.eclipse.hono.service.management.Result;
import org.eclipse.hono.service.management.device.Device;
import org.eclipse.hono.service.management.device.DeviceManagementService;
import org.eclipse.hono.service.management.device.Status;
import org.eclipse.hono.service.registration.AbstractRegistrationServiceTest;
import org.eclipse.hono.service.registration.RegistrationService;
import org.eclipse.hono.util.MessagingType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;

import io.opentracing.noop.NoopSpan;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.file.FileSystem;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

/**
 * Tests {@link FileBasedRegistrationService}.
 */
@ExtendWith(VertxExtension.class)
public class FileBasedRegistrationServiceTest implements AbstractRegistrationServiceTest {

    private static final String FILE_NAME = "/device-identities.json";

    private FileBasedRegistrationService registrationService;
    private FileBasedRegistrationConfigProperties registrationConfig;
    private Vertx vertx;
    private FileSystem fileSystem;

    /**
     * Sets up the fixture.
     */
    @BeforeEach
    public void setUp() {
        fileSystem = mock(FileSystem.class);
        vertx = mock(Vertx.class);
        when(vertx.fileSystem()).thenReturn(fileSystem);

        registrationConfig = new FileBasedRegistrationConfigProperties();
        registrationConfig.setFilename(FILE_NAME);

        registrationService = new FileBasedRegistrationService(vertx);
        registrationService.setConfig(registrationConfig);

        final AutoProvisioner autoProvisioner = new AutoProvisioner();
        autoProvisioner.setVertx(vertx);
        autoProvisioner.setTenantInformationService(mock(TenantInformationService.class));
        autoProvisioner.setDeviceManagementService(registrationService);
        autoProvisioner.setConfig(new AutoProvisionerConfigProperties());

        final MessagingClients messagingClients = mockMessagingClients();

        autoProvisioner.setMessagingClients(messagingClients);

        registrationService.setAutoProvisioner(autoProvisioner);
    }

    private MessagingClients mockMessagingClients() {
        final EventSender eventSender = mock(EventSender.class);
        when(eventSender.start()).thenReturn(Future.succeededFuture());
        when(eventSender.stop()).thenReturn(Future.succeededFuture());

        final TelemetrySender telemetrySender = mock(TelemetrySender.class);
        when(telemetrySender.start()).thenReturn(Future.succeededFuture());
        when(telemetrySender.stop()).thenReturn(Future.succeededFuture());

        final CommandResponseSender commandResponseSender = mock(CommandResponseSender.class);
        when(commandResponseSender.start()).thenReturn(Future.succeededFuture());
        when(commandResponseSender.stop()).thenReturn(Future.succeededFuture());

        final MessagingClientSet messagingClientSet = new MessagingClientSet(MessagingType.amqp,
                eventSender,
                telemetrySender,
                commandResponseSender);

        final MessagingClients messagingClients = new MessagingClients();
        messagingClients.addClientSet(messagingClientSet);

        return messagingClients;
    }

    @Override
    public RegistrationService getRegistrationService() {
        return this.registrationService;
    }

    @Override
    public DeviceManagementService getDeviceManagementService() {
        return this.registrationService;
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
        registrationConfig.setSaveToFile(true);
        doAnswer(invocation -> {
            final Handler<AsyncResult<Void>> handler = invocation.getArgument(2);
            handler.handle(Future.succeededFuture());
            return null;
        }).when(fileSystem).writeFile(eq(registrationConfig.getFilename()), any(Buffer.class), any(Handler.class));
        when(fileSystem.existsBlocking(registrationConfig.getFilename())).thenReturn(Boolean.FALSE);
        doAnswer(invocation -> {
            final Handler<AsyncResult<Void>> handler = invocation.getArgument(1);
            handler.handle(Future.succeededFuture());
            return null;
        }).when(fileSystem).createFile(eq(registrationConfig.getFilename()), any(Handler.class));

        // WHEN persisting a dirty registry
        registrationService.createDevice(TENANT, Optional.of(DEVICE), new Device(), NoopSpan.INSTANCE);
        registrationService.saveToFile().onComplete(ctx.succeeding(s -> ctx.verify(() -> {
            // THEN the file has been created
            verify(fileSystem).createFile(eq(registrationConfig.getFilename()), any(Handler.class));
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
        registrationConfig.setSaveToFile(true);
        when(fileSystem.existsBlocking(registrationConfig.getFilename())).thenReturn(Boolean.FALSE);
        doAnswer(invocation -> {
            final Handler<AsyncResult<Void>> handler = invocation.getArgument(1);
            handler.handle(Future.succeededFuture());
            return null;
        }).when(fileSystem).createFile(eq(registrationConfig.getFilename()), any(Handler.class));
        doAnswer(invocation -> {
            final Handler<AsyncResult<Buffer>> handler = invocation.getArgument(1);
            handler.handle(Future.failedFuture("malformed file"));
            return null;
        }).when(fileSystem).readFile(eq(registrationConfig.getFilename()), any(Handler.class));

        // WHEN starting the service
        final Promise<Void> startupTracker = Promise.promise();
        startupTracker.future().onComplete(ctx.succeeding(started -> ctx.verify(() -> {
            // THEN the file gets created
            verify(fileSystem).createFile(eq(registrationConfig.getFilename()), any(Handler.class));
            ctx.completeNow();
        })));
        registrationService.start().onComplete(startupTracker);
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
        registrationConfig.setSaveToFile(true);
        when(fileSystem.existsBlocking(registrationConfig.getFilename())).thenReturn(Boolean.FALSE);

        // WHEN starting the service but the file cannot be created
        doAnswer(invocation -> {
            final Handler<AsyncResult<Void>> handler = invocation.getArgument(1);
            handler.handle(Future.failedFuture("no access"));
            return null;
        }).when(fileSystem).createFile(eq(registrationConfig.getFilename()), any(Handler.class));

        final Promise<Void> startupTracker = Promise.promise();
        startupTracker.future().onComplete(ctx.failing(started -> {
            // THEN startup has failed
            ctx.completeNow();
        }));
        registrationService.start().onComplete(startupTracker);

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
        registrationService.start().onComplete(startupTracker);

    }

    /**
     * Verifies that device identities are successfully loaded from file during startup.
     *
     * @param ctx The test context.
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testDoStartLoadsDeviceIdentities(final VertxTestContext ctx) {

        // GIVEN a service configured with a file name
        when(fileSystem.existsBlocking(registrationConfig.getFilename())).thenReturn(Boolean.TRUE);
        doAnswer(invocation -> {
            final Buffer data = DeviceRegistryTestUtils.readFile(FILE_NAME);
            final Handler<AsyncResult<Buffer>> handler = invocation.getArgument(1);
            handler.handle(Future.succeededFuture(data));
            return null;
        }).when(fileSystem).readFile(eq(registrationConfig.getFilename()), any(Handler.class));

        // WHEN the service is started
        final Promise<Void> startupTracker = Promise.promise();
        startupTracker.future()
                .compose(ok -> assertCanReadDevice(TENANT, DEVICE))
                .compose(ok -> assertCanReadDevice(TENANT, GW))
                .compose(ok -> assertDevice(TENANT, "4712", Optional.of(GW),
                        r -> {
                            assertEquals(HttpURLConnection.HTTP_OK, r.getStatus());
                            assertNotNull(r.getPayload());
                            assertEquals(Collections.singletonList(GW), r.getPayload().getVia());
                            final Status<?> status = r.getPayload().getStatus();
                            assertEquals(status.getCreationTime(), Instant.parse("2020-09-16T12:33:43.000000Z"));
                            assertEquals(status.getLastUpdate(), Instant.parse("2020-09-16T12:33:53.000000Z"));
                        },
                        r -> {
                            assertEquals(HttpURLConnection.HTTP_OK, r.getStatus());
                        }))
                .compose(ok -> assertDevice(TENANT, "4713", Optional.of(GW),
                        r -> {
                            assertEquals(HttpURLConnection.HTTP_OK, r.getStatus());
                            assertNotNull(r.getPayload());
                            assertEquals(Collections.singletonList(GW), r.getPayload().getVia());
                            final Status<?> status = r.getPayload().getStatus();
                            assertNull(status.getCreationTime());
                            assertNull(status.getLastUpdate());
                            assertNull(status.getLastUser());
                        },
                        r -> {
                            assertEquals(HttpURLConnection.HTTP_OK, r.getStatus());
                        })
                .onComplete(ctx.succeeding(s -> ctx.completeNow())));

        registrationService.start().onComplete(startupTracker);

    }


    /**
     * Verifies that the file written by the registry when persisting the registry's contents can be loaded in again.
     *
     * @param ctx The vert.x test context.
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Test
    public void testLoadDevicesCanReadOutputOfSaveToFile(final VertxTestContext ctx) {

        // GIVEN a service configured to persist credentials to file
        // that contains some credentials
        registrationConfig.setFilename(FILE_NAME);
        registrationConfig.setSaveToFile(true);
        when(fileSystem.existsBlocking(registrationConfig.getFilename())).thenReturn(Boolean.TRUE);

        // set up device information

        final String deviceId1 = UUID.randomUUID().toString();

        final Map<String, Device> devices = new HashMap<>();

        // device #1
        devices.put(deviceId1, new Device());

        // device #2

        final Device device2 = new Device();
        device2.setEnabled(false);
        device2.setVia(Arrays.asList(deviceId1));
        device2.setMapper("mapper");
        devices.put(UUID.randomUUID().toString(), device2);

        // run test

        createDevices(devices)

                // ensure that the devices can be looked up

                .compose(ok -> assertDevices(devices))

                .compose(ok -> {
                    // WHEN saving the registry content to the file
                    final Promise<Void> write = Promise.promise();
                    doAnswer(invocation -> {
                        final Handler handler = invocation.getArgument(2);
                        handler.handle(Future.succeededFuture());
                        write.complete();
                        return null;
                    }).when(fileSystem).writeFile(eq(registrationConfig.getFilename()), any(Buffer.class),
                            any(Handler.class));

                    registrationService.saveToFile();
                    // and clearing the registry
                    registrationService.clear();
                    return write.future();
                })
                .compose(ok -> assertDevicesNotFound(devices))

                .map(w -> {
                    final ArgumentCaptor<Buffer> buffer = ArgumentCaptor.forClass(Buffer.class);
                    ctx.verify(() -> {
                        verify(fileSystem).writeFile(eq(registrationConfig.getFilename()), buffer.capture(),
                                any(Handler.class));
                    });
                    return buffer.getValue();
                })

                .compose(b -> {

                    // THEN the devices can be loaded back in from the file
                    doAnswer(invocation -> {
                        final Handler<AsyncResult<Buffer>> handler = invocation.getArgument(1);
                        handler.handle(Future.succeededFuture(b));
                        return null;
                    }).when(fileSystem).readFile(eq(registrationConfig.getFilename()), any(Handler.class));

                    return registrationService.loadRegistrationData();

                })

                // and the devices can be looked up - again

                .compose(ok -> assertDevices(devices))

                // complete

                .onComplete(ctx.completing());

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
        registrationConfig.setFilename(FILE_NAME);
        registrationConfig.setStartEmpty(true);
        when(fileSystem.existsBlocking(registrationConfig.getFilename())).thenReturn(Boolean.TRUE);

        // WHEN the service is started
        final Promise<Void> startupTracker = Promise.promise();
        startupTracker.future().onComplete(ctx.succeeding(s -> ctx.verify(() -> {
            // THEN the device identities from the file are not loaded
            verify(fileSystem, never()).readFile(anyString(), any(Handler.class));
            ctx.completeNow();
        })));
        registrationService.start().onComplete(startupTracker);

    }

    /**
     * Verifies that the registry enforces the maximum devices per tenant limit.
     */
    @Test
    public void testCreateDeviceFailsIfDeviceLimitIsReached() {

        // GIVEN a registry whose devices-per-tenant limit has been reached
        registrationConfig.setMaxDevicesPerTenant(1);
        registrationService.createDevice(TENANT, Optional.of(DEVICE), new Device(), NoopSpan.INSTANCE);

        // WHEN registering an additional device for the tenant
        final OperationResult<Id> result = registrationService.processCreateDevice(TENANT, Optional.of("newDevice"),
                false, new Device(), NoopSpan.INSTANCE);

        // THEN the result contains a FORBIDDEN status code and the device has not been added to the registry
        assertEquals(HttpURLConnection.HTTP_FORBIDDEN, result.getStatus());
        assertEquals(HttpURLConnection.HTTP_NOT_FOUND,
                registrationService.processReadDevice(TENANT, "newDevice", NoopSpan.INSTANCE).getStatus());
    }

    /**
     * Verifies that the <em>modificationEnabled</em> property prevents updating an existing entry.
     */
    @Test
    public void testUpdateDeviceFailsIfModificationIsDisabled() {

        // GIVEN a registry that has been configured to not allow modification of entries
        // which contains a device
        registrationConfig.setModificationEnabled(false);
        registrationService.createDevice(TENANT, Optional.of(DEVICE), new Device().putExtension("value", "1"),
                NoopSpan.INSTANCE);

        // WHEN trying to update the device
        final OperationResult<Id> result = registrationService
                .processUpdateDevice(TENANT, DEVICE, new Device().putExtension("value", "2"), Optional.empty(),
                        NoopSpan.INSTANCE);

        // THEN the result contains a FORBIDDEN status code and the device has not been updated
        assertEquals(HttpURLConnection.HTTP_FORBIDDEN, result.getStatus());
        final var device = registrationService.processReadDevice(TENANT, DEVICE, NoopSpan.INSTANCE);
        assertNotNull(device);
        assertNotNull(device.getPayload());
        assertNotNull(device.getPayload().getExtensions());
        Assertions.assertEquals("1", device.getPayload().getExtensions().get("value"));
    }

    /**
     * Verifies that the <em>modificationEnabled</em> property prevents deleting an existing entry.
     */
    @Test
    public void testDeleteDeviceFailsIfModificationIsDisabled() {

        // GIVEN a registry that has been configured to not allow modification of entries
        // which contains a device
        registrationConfig.setModificationEnabled(false);
        registrationService.createDevice(TENANT, Optional.of(DEVICE), new Device(), NoopSpan.INSTANCE);

        // WHEN trying to remove the device
        final Result<Void> result = registrationService.processDeleteDevice(TENANT, DEVICE, Optional.empty(),
                NoopSpan.INSTANCE);

        // THEN the result contains a FORBIDDEN status code and the device has not been removed
        assertEquals(HttpURLConnection.HTTP_FORBIDDEN, result.getStatus());
        assertEquals(HttpURLConnection.HTTP_OK,
                registrationService.processReadDevice(TENANT, DEVICE, NoopSpan.INSTANCE).getStatus());
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

        registrationConfig.setSaveToFile(false);
        when(fileSystem.existsBlocking(registrationConfig.getFilename())).thenReturn(Boolean.TRUE);
        doAnswer(invocation -> {
            final Handler handler = invocation.getArgument(1);
            handler.handle(Future.failedFuture("malformed file"));
            return null;
        }).when(fileSystem).readFile(eq(registrationConfig.getFilename()), any(Handler.class));

        final Promise<Void> startupTracker = Promise.promise();
        startupTracker.future().onComplete(ctx.succeeding(done -> ctx.verify(() -> {
            verify(vertx, never()).setPeriodic(anyLong(), any(Handler.class));
            ctx.completeNow();
        })));
        registrationService.start().onComplete(startupTracker);
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
        registrationConfig.setSaveToFile(false);
        when(fileSystem.existsBlocking(registrationConfig.getFilename())).thenReturn(Boolean.TRUE);
        doAnswer(invocation -> {
            final Handler<AsyncResult<Buffer>> handler = invocation.getArgument(1);
            handler.handle(Future.failedFuture("malformed data"));
            return null;
        }).when(fileSystem).readFile(eq(registrationConfig.getFilename()), any(Handler.class));

        final Promise<Void> startupTracker = Promise.promise();
        startupTracker.future()
        .compose(ok -> {
            // WHEN adding a device
                    registrationService.createDevice(TENANT, Optional.of(DEVICE), new Device(), NoopSpan.INSTANCE);
            final Promise<Void> shutdownTracker = Promise.promise();
                    registrationService.stop().onComplete(shutdownTracker);
            return shutdownTracker.future();
        })
        .onComplete(ctx.succeeding(shutDown -> ctx.verify(() -> {
            // THEN no data has been written to the file system
                    verify(fileSystem, never()).createFile(eq(registrationConfig.getFilename()), any(Handler.class));
            ctx.completeNow();
        })));
        registrationService.start().onComplete(startupTracker);
    }
}
