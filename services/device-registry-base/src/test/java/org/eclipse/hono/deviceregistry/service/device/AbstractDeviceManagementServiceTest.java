/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package org.eclipse.hono.deviceregistry.service.device;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static com.google.common.truth.Truth.assertThat;

import java.net.HttpURLConnection;
import java.util.List;
import java.util.Optional;

import org.eclipse.hono.notification.NotificationEventBusSupport;
import org.eclipse.hono.notification.deviceregistry.AllDevicesOfTenantDeletedNotification;
import org.eclipse.hono.notification.deviceregistry.DeviceChangeNotification;
import org.eclipse.hono.notification.deviceregistry.LifecycleChange;
import org.eclipse.hono.service.management.Id;
import org.eclipse.hono.service.management.OperationResult;
import org.eclipse.hono.service.management.Result;
import org.eclipse.hono.service.management.device.Device;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;

import io.opentracing.Span;
import io.opentracing.noop.NoopSpan;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

/**
 * Verifies the behavior of {@link AbstractDeviceManagementService}.
 */
@ExtendWith(VertxExtension.class)
public class AbstractDeviceManagementServiceTest {

    private static final String DEFAULT_TENANT_ID = "test-tenant";
    private static final String DEFAULT_DEVICE_ID = "test-device";
    private static final Span SPAN = NoopSpan.INSTANCE;

    private TestDeviceManagementService deviceManagementService;
    private EventBus eventBus;

    @BeforeEach
    void setUp() {
        eventBus = mock(EventBus.class);
        final Vertx vertx = mock(Vertx.class);
        when(vertx.eventBus()).thenReturn(eventBus);
        deviceManagementService = new TestDeviceManagementService(vertx);
    }

    /**
     * Verifies that the search devices operation verifies that all required parameters
     * are non {@code null}.
     */
    @Test
    public void testSearchDevicesRejectsNullParameters() {
        assertAll(
                () -> assertThrows(
                            NullPointerException.class,
                            () -> deviceManagementService.searchDevices(
                                    DEFAULT_TENANT_ID, 10, 0, null, List.of(), Optional.empty(), SPAN)
                            ),
                () -> assertThrows(
                            NullPointerException.class,
                            () -> deviceManagementService.searchDevices(
                                    DEFAULT_TENANT_ID, 10, 0, List.of(), null, Optional.empty(), SPAN)
                            ),
                () -> assertThrows(
                            NullPointerException.class,
                            () -> deviceManagementService.searchDevices(
                                    DEFAULT_TENANT_ID, 10, 0, List.of(), List.of(), null, SPAN)
                            )
                );
    }

    /**
     * Verifies that {@link AbstractDeviceManagementService#createDevice(String, Optional, Device, Span)} publishes the
     * expected notification.
     *
     * @param context The vert.x test context.
     */
    @Test
    public void testNotificationOnCreateDevice(final VertxTestContext context) {
        final var notificationArgumentCaptor = ArgumentCaptor.forClass(DeviceChangeNotification.class);
        deviceManagementService
                .createDevice(DEFAULT_TENANT_ID, Optional.of(DEFAULT_DEVICE_ID), new Device().setEnabled(false), SPAN)
                .onComplete(context.succeeding(result -> {
                    context.verify(() -> {
                        verify(eventBus).publish(
                                eq(NotificationEventBusSupport.getEventBusAddress(DeviceChangeNotification.TYPE)),
                                notificationArgumentCaptor.capture(),
                                any());

                        assertThat(notificationArgumentCaptor.getAllValues().size()).isEqualTo(1);
                        final var notification = notificationArgumentCaptor.getValue();
                        assertThat(notification).isNotNull();
                        assertThat(notification.getChange()).isEqualTo(LifecycleChange.CREATE);
                        assertThat(notification.getTenantId()).isEqualTo(DEFAULT_TENANT_ID);
                        assertThat(notification.getDeviceId()).isEqualTo(DEFAULT_DEVICE_ID);
                        assertThat(notification.getCreationTime()).isNotNull();
                        assertThat(notification.isDeviceEnabled()).isFalse();
                    });
                    context.completeNow();
                }));
    }

    /**
     * Verifies that {@link AbstractDeviceManagementService#updateDevice(String, String, Device, Optional, Span)}
     * publishes the expected notification.
     *
     * @param context The vert.x test context.
     */
    @Test
    public void testNotificationOnUpdateDevice(final VertxTestContext context) {
        final var notificationArgumentCaptor = ArgumentCaptor.forClass(DeviceChangeNotification.class);
        deviceManagementService
                .createDevice(DEFAULT_TENANT_ID, Optional.of(DEFAULT_DEVICE_ID), new Device(), SPAN)
                .compose(result -> deviceManagementService.updateDevice(DEFAULT_TENANT_ID, DEFAULT_DEVICE_ID,
                        new Device().setEnabled(false), Optional.empty(), SPAN))
                .onComplete(context.succeeding(result -> {
                    context.verify(() -> {
                        verify(eventBus, times(2)).publish(
                                eq(NotificationEventBusSupport.getEventBusAddress(DeviceChangeNotification.TYPE)),
                                notificationArgumentCaptor.capture(),
                                any());

                        assertThat(notificationArgumentCaptor.getAllValues().size()).isEqualTo(2);
                        final var notification = notificationArgumentCaptor.getValue();
                        assertThat(notification).isNotNull();
                        assertThat(notification.getChange()).isEqualTo(LifecycleChange.UPDATE);
                        assertThat(notification.getTenantId()).isEqualTo(DEFAULT_TENANT_ID);
                        assertThat(notification.getDeviceId()).isEqualTo(DEFAULT_DEVICE_ID);
                        assertThat(notification.getCreationTime()).isNotNull();
                        assertThat(notification.isDeviceEnabled()).isFalse();
                    });
                    context.completeNow();
                }));
    }

    /**
     * Verifies that {@link AbstractDeviceManagementService#deleteDevice(String, String, Optional, Span)} publishes the
     * expected notification.
     *
     * @param context The vert.x test context.
     */
    @Test
    public void testNotificationOnDeleteDevice(final VertxTestContext context) {
        final var notificationArgumentCaptor = ArgumentCaptor.forClass(DeviceChangeNotification.class);
        deviceManagementService
                .createDevice(DEFAULT_TENANT_ID, Optional.of(DEFAULT_DEVICE_ID), new Device(), SPAN)
                .compose(result -> deviceManagementService.deleteDevice(DEFAULT_TENANT_ID, DEFAULT_DEVICE_ID,
                        Optional.empty(), SPAN))
                .onComplete(context.succeeding(result -> {
                    context.verify(() -> {
                        verify(eventBus, times(2)).publish(
                                eq(NotificationEventBusSupport.getEventBusAddress(DeviceChangeNotification.TYPE)),
                                notificationArgumentCaptor.capture(),
                                any());

                        assertThat(notificationArgumentCaptor.getAllValues().size()).isEqualTo(2);
                        final var notification = notificationArgumentCaptor.getValue();
                        assertThat(notification).isNotNull();
                        assertThat(notification.getChange()).isEqualTo(LifecycleChange.DELETE);
                        assertThat(notification.getTenantId()).isEqualTo(DEFAULT_TENANT_ID);
                        assertThat(notification.getDeviceId()).isEqualTo(DEFAULT_DEVICE_ID);
                        assertThat(notification.getCreationTime()).isNotNull();
                        assertThat(notification.isDeviceEnabled()).isFalse();
                    });
                    context.completeNow();
                }));
    }

    /**
     * Verifies that {@link AbstractDeviceManagementService#deleteDevicesOfTenant(String, Span)} publishes the expected
     * notification.
     *
     * @param context The vert.x test context.
     */
    @Test
    public void testNotificationOnDeleteDevicesOfTenant(final VertxTestContext context) {
        final var notificationArgumentCaptor = ArgumentCaptor.forClass(AllDevicesOfTenantDeletedNotification.class);
        deviceManagementService
                .createDevice(DEFAULT_TENANT_ID, Optional.of(DEFAULT_DEVICE_ID), new Device(), SPAN)
                .compose(result -> deviceManagementService.deleteDevicesOfTenant(DEFAULT_TENANT_ID, SPAN))
                .onComplete(context.succeeding(result -> {
                    context.verify(() -> {
                        verify(eventBus).publish(
                                eq(NotificationEventBusSupport.getEventBusAddress(AllDevicesOfTenantDeletedNotification.TYPE)),
                                notificationArgumentCaptor.capture(),
                                any());

                        assertThat(notificationArgumentCaptor.getAllValues().size()).isEqualTo(1);
                        final var notification = notificationArgumentCaptor.getValue();
                        assertThat(notification).isNotNull();
                        assertThat(notification.getTenantId()).isEqualTo(DEFAULT_TENANT_ID);
                        assertThat(notification.getCreationTime()).isNotNull();
                    });
                    context.completeNow();
                }));
    }

    private static class TestDeviceManagementService extends AbstractDeviceManagementService {

        TestDeviceManagementService(final Vertx vertx) {
            super(vertx);
        }

        @Override
        protected Future<OperationResult<Id>> processCreateDevice(final DeviceKey key, final Device device,
                final Span span) {
            return Future.succeededFuture(OperationResult.ok(HttpURLConnection.HTTP_CREATED, Id.of(key.getDeviceId()),
                    Optional.empty(), Optional.empty()));
        }

        @Override
        protected Future<OperationResult<Device>> processReadDevice(final DeviceKey key, final Span span) {
            return Future.succeededFuture(
                    OperationResult.ok(HttpURLConnection.HTTP_OK, new Device(), Optional.empty(), Optional.empty()));
        }

        @Override
        protected Future<OperationResult<Id>> processUpdateDevice(final DeviceKey key, final Device device,
                final Optional<String> resourceVersion, final Span span) {
            return Future.succeededFuture(OperationResult.ok(HttpURLConnection.HTTP_NO_CONTENT,
                    Id.of(key.getDeviceId()), Optional.empty(), Optional.empty()));
        }

        @Override
        protected Future<Result<Void>> processDeleteDevice(final DeviceKey key, final Optional<String> resourceVersion,
                final Span span) {
            return Future.succeededFuture(Result.from(HttpURLConnection.HTTP_NO_CONTENT));
        }

        @Override
        protected Future<Result<Void>> processDeleteDevicesOfTenant(final String tenantId, final Span span) {
            return Future.succeededFuture(Result.from(HttpURLConnection.HTTP_NO_CONTENT));
        }
    }
}
