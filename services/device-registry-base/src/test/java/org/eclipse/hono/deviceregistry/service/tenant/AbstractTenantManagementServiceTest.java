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

package org.eclipse.hono.deviceregistry.service.tenant;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static com.google.common.truth.Truth.assertThat;

import java.net.HttpURLConnection;
import java.util.Optional;

import org.eclipse.hono.notification.AbstractNotification;
import org.eclipse.hono.notification.NotificationSender;
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

    private final NotificationSender notificationSender = mock(NotificationSender.class);
    private final ArgumentCaptor<AbstractNotification> notificationArgumentCaptor = ArgumentCaptor
            .forClass(AbstractNotification.class);

    @BeforeEach
    void setUp() {
        tenantManagementService = new TestTenantManagementService();
        tenantManagementService.setNotificationSender(notificationSender);
    }

    /**
     * Verifies that {@link AbstractTenantManagementService#setNotificationSender(NotificationSender)} throws a null
     * pointer exception if the notification sender is {@code null}.
     */
    @Test
    public void setNotificationSender() {
        assertThrows(NullPointerException.class, () -> tenantManagementService.setNotificationSender(null));
    }

    /**
     * Verifies that {@link AbstractTenantManagementService#start()} starts the notification sender.
     *
     * @param context The vert.x test context.
     */
    @Test
    public void start(final VertxTestContext context) {
        when(notificationSender.start()).thenReturn(Future.succeededFuture());

        tenantManagementService.start()
                .onComplete(context.succeeding(result -> context.verify(() -> {
                    verify(notificationSender).start();
                    context.completeNow();
                })));
    }

    /**
     * Verifies that {@link AbstractTenantManagementService#stop()} stops the notification sender.
     *
     * @param context The vert.x test context.
     */
    @Test
    public void stop(final VertxTestContext context) {
        when(notificationSender.stop()).thenReturn(Future.succeededFuture());

        tenantManagementService.stop()
                .onComplete(context.succeeding(result -> context.verify(() -> {
                    verify(notificationSender).stop();
                    context.completeNow();
                })));
    }

    /**
     * Verifies that {@link AbstractTenantManagementService#createTenant(Optional, Tenant, Span)} publishes the expected
     * notification.
     *
     * @param context The vert.x test context.
     */
    @Test
    public void testNotificationOnCreateTenant(final VertxTestContext context) {
        tenantManagementService
                .createTenant(Optional.of(DEFAULT_TENANT_ID), new Tenant().setEnabled(false), SPAN)
                .onComplete(context.succeeding(result -> context.verify(() -> {

                    verify(notificationSender).publish(notificationArgumentCaptor.capture());

                    final var notification = (TenantChangeNotification) notificationArgumentCaptor.getValue();
                    assertThat(notification.getChange()).isEqualTo(LifecycleChange.CREATE);
                    assertThat(notification.getTenantId()).isEqualTo(DEFAULT_TENANT_ID);
                    assertThat(notification.getCreationTime()).isNotNull();
                    assertThat(notification.isEnabled()).isFalse();
                    context.completeNow();
                })));
    }

    /**
     * Verifies that {@link AbstractTenantManagementService#updateTenant(String, Tenant, Optional, Span)} publishes the
     * expected notification.
     *
     * @param context The vert.x test context.
     */
    @Test
    public void testNotificationOnUpdateTenant(final VertxTestContext context) {
        tenantManagementService
                .createTenant(Optional.of(DEFAULT_TENANT_ID), new Tenant(), SPAN)
                .compose(result -> tenantManagementService.updateTenant(DEFAULT_TENANT_ID,
                        new Tenant().setEnabled(false), Optional.empty(), SPAN))
                .onComplete(context.succeeding(result -> context.verify(() -> {
                    verify(notificationSender, times(2)).publish(notificationArgumentCaptor.capture());

                    final var notification = (TenantChangeNotification) notificationArgumentCaptor.getValue();
                    assertThat(notification.getChange()).isEqualTo(LifecycleChange.UPDATE);
                    assertThat(notification.getTenantId()).isEqualTo(DEFAULT_TENANT_ID);
                    assertThat(notification.getCreationTime()).isNotNull();
                    assertThat(notification.isEnabled()).isFalse();
                    context.completeNow();
                })));
    }

    /**
     * Verifies that {@link AbstractTenantManagementService#deleteTenant(String, Optional, Span)} publishes the expected
     * notification.
     *
     * @param context The vert.x test context.
     */
    @Test
    public void testNotificationOnDeleteTenant(final VertxTestContext context) {
        tenantManagementService
                .createTenant(Optional.of(DEFAULT_TENANT_ID), new Tenant(), SPAN)
                .compose(result -> tenantManagementService.deleteTenant(DEFAULT_TENANT_ID, Optional.empty(), SPAN))
                .onComplete(context.succeeding(result -> context.verify(() -> {
                    verify(notificationSender, times(2)).publish(notificationArgumentCaptor.capture());

                    final var notification = (TenantChangeNotification) notificationArgumentCaptor.getValue();
                    assertThat(notification.getChange()).isEqualTo(LifecycleChange.DELETE);
                    assertThat(notification.getTenantId()).isEqualTo(DEFAULT_TENANT_ID);
                    assertThat(notification.getCreationTime()).isNotNull();
                    assertThat(notification.isEnabled()).isFalse();
                    context.completeNow();
                })));
    }

    private static class TestTenantManagementService extends AbstractTenantManagementService {

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
