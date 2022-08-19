/*
 * Copyright (c) 2021, 2022 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.deviceregistry.service.tenant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static com.google.common.truth.Truth.assertThat;

import java.net.HttpURLConnection;
import java.util.Optional;

import org.eclipse.hono.notification.NotificationEventBusSupport;
import org.eclipse.hono.notification.deviceregistry.LifecycleChange;
import org.eclipse.hono.notification.deviceregistry.TenantChangeNotification;
import org.eclipse.hono.service.management.Id;
import org.eclipse.hono.service.management.OperationResult;
import org.eclipse.hono.service.management.Result;
import org.eclipse.hono.service.management.tenant.Tenant;
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
 * Verifies the behavior of {@link AbstractTenantManagementService}.
 */
@ExtendWith(VertxExtension.class)
public class AbstractTenantManagementServiceTest {

    private static final String DEFAULT_TENANT_ID = "test-tenant";
    private static final Span SPAN = NoopSpan.INSTANCE;

    private TestTenantManagementService tenantManagementService;
    private EventBus eventBus;

    @BeforeEach
    void setUp() {
        eventBus = mock(EventBus.class);
        final Vertx vertx = mock(Vertx.class);
        when(vertx.eventBus()).thenReturn(eventBus);
        tenantManagementService = new TestTenantManagementService(vertx);
    }

    /**
     * Verifies that {@link AbstractTenantManagementService#createTenant(Optional, Tenant, Span)} publishes the expected
     * notification.
     *
     * @param context The vert.x test context.
     */
    @Test
    public void testNotificationOnCreateTenant(final VertxTestContext context) {
        final var notificationArgumentCaptor = ArgumentCaptor.forClass(TenantChangeNotification.class);
        tenantManagementService
                .createTenant(Optional.of(DEFAULT_TENANT_ID), new Tenant().setEnabled(false), SPAN)
                .onComplete(context.succeeding(result -> {
                    context.verify(() -> {
                        verify(eventBus).publish(
                                eq(NotificationEventBusSupport.getEventBusAddress(TenantChangeNotification.TYPE)),
                                notificationArgumentCaptor.capture(),
                                any());

                        assertThat(notificationArgumentCaptor.getAllValues().size()).isEqualTo(1);
                        final var notification = notificationArgumentCaptor.getValue();
                        assertThat(notification).isNotNull();
                        assertThat(notification.getChange()).isEqualTo(LifecycleChange.CREATE);
                        assertThat(notification.getTenantId()).isEqualTo(DEFAULT_TENANT_ID);
                        assertThat(notification.getCreationTime()).isNotNull();
                        assertThat(notification.isTenantEnabled()).isFalse();
                    });
                    context.completeNow();
                }));
    }

    /**
     * Verifies that {@link AbstractTenantManagementService#updateTenant(String, Tenant, Optional, Span)} publishes the
     * expected notification.
     *
     * @param context The vert.x test context.
     */
    @Test
    public void testNotificationOnUpdateTenant(final VertxTestContext context) {
        final var notificationArgumentCaptor = ArgumentCaptor.forClass(TenantChangeNotification.class);
        tenantManagementService
                .createTenant(Optional.of(DEFAULT_TENANT_ID), new Tenant(), SPAN)
                .compose(result -> tenantManagementService.updateTenant(DEFAULT_TENANT_ID,
                        new Tenant().setEnabled(false), Optional.empty(), SPAN))
                .onComplete(context.succeeding(result -> {
                    context.verify(() -> {
                        verify(eventBus, times(2)).publish(
                                eq(NotificationEventBusSupport.getEventBusAddress(TenantChangeNotification.TYPE)),
                                notificationArgumentCaptor.capture(),
                                any());

                        assertThat(notificationArgumentCaptor.getAllValues().size()).isEqualTo(2);
                        final var notification = notificationArgumentCaptor.getValue();
                        assertThat(notification).isNotNull();
                        assertThat(notification.getChange()).isEqualTo(LifecycleChange.UPDATE);
                        assertThat(notification.getTenantId()).isEqualTo(DEFAULT_TENANT_ID);
                        assertThat(notification.getCreationTime()).isNotNull();
                        assertThat(notification.isTenantEnabled()).isFalse();
                    });
                    context.completeNow();
                }));
    }

    /**
     * Verifies that {@link AbstractTenantManagementService#deleteTenant(String, Optional, Span)} publishes the expected
     * notification.
     *
     * @param context The vert.x test context.
     */
    @Test
    public void testNotificationOnDeleteTenant(final VertxTestContext context) {
        final var notificationArgumentCaptor = ArgumentCaptor.forClass(TenantChangeNotification.class);
        tenantManagementService
                .createTenant(Optional.of(DEFAULT_TENANT_ID), new Tenant(), SPAN)
                .compose(result -> tenantManagementService.deleteTenant(DEFAULT_TENANT_ID, Optional.empty(), SPAN))
                .onComplete(context.succeeding(result -> {
                    context.verify(() -> {
                        verify(eventBus, times(2)).publish(
                                eq(NotificationEventBusSupport.getEventBusAddress(TenantChangeNotification.TYPE)),
                                notificationArgumentCaptor.capture(),
                                any());

                        assertThat(notificationArgumentCaptor.getAllValues().size()).isEqualTo(2);
                        final var notification = notificationArgumentCaptor.getValue();
                        assertThat(notification).isNotNull();
                        assertThat(notification.getChange()).isEqualTo(LifecycleChange.DELETE);
                        assertThat(notification.getTenantId()).isEqualTo(DEFAULT_TENANT_ID);
                        assertThat(notification.getCreationTime()).isNotNull();
                        assertThat(notification.isTenantEnabled()).isFalse();
                    });
                    context.completeNow();
                }));
    }

    private static class TestTenantManagementService extends AbstractTenantManagementService {

        TestTenantManagementService(final Vertx vertx) {
            super(vertx);
        }

        @Override
        protected Future<OperationResult<Id>> processCreateTenant(final String tenantId, final Tenant tenantObj,
                final Span span) {
            return Future.succeededFuture(OperationResult.ok(HttpURLConnection.HTTP_CREATED, Id.of(tenantId),
                    Optional.empty(), Optional.empty()));
        }

        @Override
        protected Future<OperationResult<Tenant>> processReadTenant(final String tenantId, final Span span) {
            return Future.succeededFuture(
                    OperationResult.ok(HttpURLConnection.HTTP_OK, new Tenant(), Optional.empty(), Optional.empty()));
        }

        @Override
        protected Future<OperationResult<Void>> processUpdateTenant(final String tenantId, final Tenant tenantObj,
                final Optional<String> resourceVersion, final Span span) {
            return Future.succeededFuture(
                    OperationResult.ok(HttpURLConnection.HTTP_NO_CONTENT, null, Optional.empty(), Optional.empty()));
        }

        @Override
        protected Future<Result<Void>> processDeleteTenant(final String tenantId,
                final Optional<String> resourceVersion, final Span span) {
            return Future.succeededFuture(Result.from(HttpURLConnection.HTTP_NO_CONTENT));
        }
    }
}
