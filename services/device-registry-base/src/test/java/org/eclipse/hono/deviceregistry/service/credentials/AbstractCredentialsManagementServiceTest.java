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

package org.eclipse.hono.deviceregistry.service.credentials;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static com.google.common.truth.Truth.assertThat;

import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.eclipse.hono.auth.HonoPasswordEncoder;
import org.eclipse.hono.deviceregistry.service.device.DeviceKey;
import org.eclipse.hono.notification.AbstractNotification;
import org.eclipse.hono.notification.NotificationSender;
import org.eclipse.hono.notification.deviceregistry.CredentialsChangeNotification;
import org.eclipse.hono.service.management.OperationResult;
import org.eclipse.hono.service.management.credentials.CommonCredential;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;

import io.opentracing.Span;
import io.opentracing.noop.NoopSpan;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

/**
 * Verifies the behavior of {@link AbstractCredentialsManagementService}.
 */
@ExtendWith(VertxExtension.class)
public class AbstractCredentialsManagementServiceTest {

    private static final String DEFAULT_TENANT_ID = "test-tenant";
    private static final String DEFAULT_DEVICE_ID = "test-device";
    private static final Span SPAN = NoopSpan.INSTANCE;

    private TestCredentialsManagementService credentialsManagementService;

    private final NotificationSender notificationSender = mock(NotificationSender.class);
    private final ArgumentCaptor<AbstractNotification> notificationArgumentCaptor = ArgumentCaptor
            .forClass(AbstractNotification.class);

    @BeforeEach
    void setUp() {
        credentialsManagementService = new TestCredentialsManagementService(mock(Vertx.class),
                mock(HonoPasswordEncoder.class), 9, new HashSet<>());
        credentialsManagementService.setNotificationSender(notificationSender);
    }

    /**
     * Verifies that {@link AbstractCredentialsManagementService#setNotificationSender(NotificationSender)} throws a
     * null pointer exception if the notification sender is {@code null}.
     */
    @Test
    public void setNotificationSender() {
        assertThrows(NullPointerException.class, () -> credentialsManagementService.setNotificationSender(null));
    }

    /**
     * Verifies that {@link AbstractCredentialsManagementService#start()} starts the notification sender.
     *
     * @param context The vert.x test context.
     */
    @Test
    public void start(final VertxTestContext context) {
        when(notificationSender.start()).thenReturn(Future.succeededFuture());

        credentialsManagementService.start()
                .onComplete(context.succeeding(result -> context.verify(() -> {
                    verify(notificationSender).start();
                    context.completeNow();
                })));
    }

    /**
     * Verifies that {@link AbstractCredentialsManagementService#stop()} stops the notification sender.
     *
     * @param context The vert.x test context.
     */
    @Test
    public void stop(final VertxTestContext context) {
        when(notificationSender.stop()).thenReturn(Future.succeededFuture());

        credentialsManagementService.stop()
                .onComplete(context.succeeding(result -> context.verify(() -> {
                    verify(notificationSender).stop();
                    context.completeNow();
                })));
    }

    /**
     * Verifies that
     * {@link AbstractCredentialsManagementService#updateCredentials(String, String, List, Optional, Span)} publishes
     * the expected notification.
     *
     * @param context The vert.x test context.
     */
    @Test
    public void testNotificationOnUpdateCredentials(final VertxTestContext context) {
        credentialsManagementService
                .updateCredentials(DEFAULT_TENANT_ID, DEFAULT_DEVICE_ID, new ArrayList<>(), Optional.empty(), SPAN)
                .onComplete(context.succeeding(result -> context.verify(() -> {
                    verify(notificationSender).publish(notificationArgumentCaptor.capture());

                    final var notification = (CredentialsChangeNotification) notificationArgumentCaptor.getValue();
                    assertThat(notification.getTenantId()).isEqualTo(DEFAULT_TENANT_ID);
                    assertThat(notification.getDeviceId()).isEqualTo(DEFAULT_DEVICE_ID);
                    assertThat(notification.getCreationTime()).isNotNull();
                    context.completeNow();
                })));
    }

    private static class TestCredentialsManagementService extends AbstractCredentialsManagementService {

        TestCredentialsManagementService(final Vertx vertx, final HonoPasswordEncoder passwordEncoder,
                final int maxBcryptCostfactor, final Set<String> hashAlgorithmsWhitelist) {
            super(vertx, passwordEncoder, maxBcryptCostfactor, hashAlgorithmsWhitelist);
        }

        protected Future<OperationResult<Void>> processUpdateCredentials(final DeviceKey key,
                final List<CommonCredential> credentials, final Optional<String> resourceVersion, final Span span) {

            return Future.succeededFuture(
                    OperationResult.ok(HttpURLConnection.HTTP_NO_CONTENT, null, Optional.empty(), Optional.empty()));
        }

        @Override
        protected Future<OperationResult<List<CommonCredential>>> processReadCredentials(final DeviceKey key,
                final Span span) {

            return Future.succeededFuture(OperationResult.ok(HttpURLConnection.HTTP_OK, new ArrayList<>(),
                    Optional.empty(), Optional.empty()));
        }
    }
}
