/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.notification;

import static com.google.common.truth.Truth.assertThat;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

import org.eclipse.hono.notification.deviceregistry.LifecycleChange;
import org.eclipse.hono.notification.deviceregistry.TenantChangeNotification;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.Vertx;
import io.vertx.junit5.Checkpoint;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

/**
 * Tests verifying the behavior of {@link NotificationEventBusSupport}.
 *
 */
@ExtendWith(VertxExtension.class)
@Timeout(value = 5, timeUnit = TimeUnit.SECONDS)
public class NotificationEventBusSupportTest {

    /**
     * Verifies that a notification published on the vert.x event bus is getting received by consumers.
     *
     * @param ctx The vert.x test context.
     * @param vertx The vert.x instance.
     */
    @Test
    public void testPublishedNotificationIsReceived(final VertxTestContext ctx, final Vertx vertx) {

        final TenantChangeNotification notification = new TenantChangeNotification(LifecycleChange.CREATE, "my-tenant",
                Instant.parse("2007-12-03T10:15:30Z"), false, false);

        final int consumerCount = 2;
        final Checkpoint checkpoint = ctx.checkpoint(consumerCount);
        for (int i = 0; i < consumerCount; i++) {
            NotificationEventBusSupport.registerConsumer(vertx, notification.getType(), receivedNotification -> {
                ctx.verify(() -> assertThat(receivedNotification).isEqualTo(notification));
                checkpoint.flag();
            });
        }

        NotificationEventBusSupport.getNotificationSender(vertx).handle(notification);
    }

}
