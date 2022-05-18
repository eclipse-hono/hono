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

package org.eclipse.hono.deviceregistry.service.credentials;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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

import org.eclipse.hono.deviceregistry.service.device.DeviceKey;
import org.eclipse.hono.notification.NotificationEventBusSupport;
import org.eclipse.hono.notification.deviceregistry.CredentialsChangeNotification;
import org.eclipse.hono.service.auth.HonoPasswordEncoder;
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
import io.vertx.core.eventbus.EventBus;
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
    private EventBus eventBus;

    @BeforeEach
    void setUp() {
        eventBus = mock(EventBus.class);
        final Vertx vertx = mock(Vertx.class);
        when(vertx.eventBus()).thenReturn(eventBus);
        credentialsManagementService = new TestCredentialsManagementService(vertx,
                mock(HonoPasswordEncoder.class), 9, new HashSet<>());
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
        final var notificationArgumentCaptor = ArgumentCaptor.forClass(CredentialsChangeNotification.class);
        credentialsManagementService
                .updateCredentials(DEFAULT_TENANT_ID, DEFAULT_DEVICE_ID, new ArrayList<>(), Optional.empty(), SPAN)
                .onComplete(context.succeeding(result -> {
                    context.verify(() -> {
                        verify(eventBus).publish(
                                eq(NotificationEventBusSupport.getEventBusAddress(CredentialsChangeNotification.TYPE)),
                                notificationArgumentCaptor.capture(),
                                any());

                        assertThat(notificationArgumentCaptor.getAllValues().size()).isEqualTo(1);
                        final var notification = notificationArgumentCaptor.getValue();
                        assertThat(notification).isNotNull();
                        assertThat(notification.getTenantId()).isEqualTo(DEFAULT_TENANT_ID);
                        assertThat(notification.getDeviceId()).isEqualTo(DEFAULT_DEVICE_ID);
                        assertThat(notification.getCreationTime()).isNotNull();
                    });
                    context.completeNow();
                }));
    }

    private static class TestCredentialsManagementService extends AbstractCredentialsManagementService {

        TestCredentialsManagementService(final Vertx vertx, final HonoPasswordEncoder passwordEncoder,
                final int maxBcryptCostfactor, final Set<String> hashAlgorithmsWhitelist) {
            super(vertx, passwordEncoder, maxBcryptCostfactor, hashAlgorithmsWhitelist);
        }

        @Override
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
