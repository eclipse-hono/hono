/**
 * Copyright (c) 2020, 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.hono.service.metric;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.vertx.core.Handler;
import io.vertx.core.Vertx;

/**
 * Verifies behavior of {@link DeviceConnectionDurationTracker}.
 *
 */
public class DeviceConnectionDurationTrackerTest {

    private Vertx vertx;

    /**
     * Sets up the fixture.
     */
    @BeforeEach
    public void setup() {
        vertx = mock(Vertx.class);
    }

    /**
     * Verifies that the recording of the connection duration is scheduled with the specified interval.
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testRecordingOfConnectionDurationIsScheduled() {
        DeviceConnectionDurationTracker.Builder
                .forTenant("TEST_TENANT")
                .withNumberOfDeviceConnections(1)
                .withVertx(vertx)
                .withRecordingInterval(10L)
                .recordUsing(duration -> {
                })
                .start();

        verify(vertx).setPeriodic(eq(10L), any(Handler.class));
    }

    /**
     * Verifies that the scheduled recording of the connection duration is cancelled
     * when there are no devices connected for the given tenant.
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testScheduledRecordingOfConnectionDurationIsCancelled() {
        final DeviceConnectionDurationTracker tracker = DeviceConnectionDurationTracker.Builder
                .forTenant("TEST_TENANT")
                .withNumberOfDeviceConnections(1)
                .withVertx(vertx)
                .withRecordingInterval(10)
                .recordUsing(duration -> {
                })
                .start();

        verify(vertx).setPeriodic(eq(10L), any(Handler.class));
        assertNull(tracker.updateNoOfDeviceConnections(0));
        verify(vertx).cancelTimer(anyLong());
    }

    /**
     * Verifies that you cannot pass negative value for recording interval.
     */
    @Test
    public void testWithNegativeRecordingInterval() {
        assertThrows(IllegalArgumentException.class, () -> DeviceConnectionDurationTracker.Builder
                .forTenant("TEST_TENANT")
                .withRecordingInterval(-100));
    }

    /**
     * Verifies that you cannot pass negative value for number of device connections.
     */
    @Test
    public void testWithNegativeNumberOfDeviceConnections() {
        assertThrows(IllegalArgumentException.class, () -> DeviceConnectionDurationTracker.Builder
                .forTenant("TEST_TENANT")
                .withNumberOfDeviceConnections(-100));
    }
}
