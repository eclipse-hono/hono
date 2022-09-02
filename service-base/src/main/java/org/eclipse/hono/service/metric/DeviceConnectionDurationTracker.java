/*******************************************************************************
 * Copyright (c) 2019, 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.hono.service.metric;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Vertx;

/**
 *  A class that tracks and reports the device connection duration for tenants.
 */
public final class DeviceConnectionDurationTracker {

    private static final Logger LOG = LoggerFactory.getLogger(DeviceConnectionDurationTracker.class);

    private final AtomicLong noOfDeviceConnections;
    private final Consumer<Long> recorder;
    private final AtomicReference<Instant> startInstant = new AtomicReference<>();
    private final String tenantId;
    private final long timerId;
    private final Vertx vertx;

    private DeviceConnectionDurationTracker(
            final String tenantId, final Vertx vertx,
            final long noOfDeviceConnections,
            final Consumer<Long> recorder,
            final long recordingIntervalInMs) {
        this.tenantId = tenantId;
        this.noOfDeviceConnections = new AtomicLong(noOfDeviceConnections);
        this.recorder = recorder;
        this.startInstant.set(Instant.now());
        this.vertx = vertx;
        this.timerId = this.vertx.setPeriodic(recordingIntervalInMs, id -> report());
        LOG.trace("Started a device connection duration tracker for the tenant [{}].", tenantId);
    }

    /**
     * Reports the device connection duration and then updates the number of device connections with the given value.
     *
     * @param noOfDeviceConnections The number of device connections.
     * @return a reference to this for fluent use or {@code null} if the number of device connections is zero.
     * @throws IllegalArgumentException if the noOfDeviceConnections is negative.
     */
    public DeviceConnectionDurationTracker updateNoOfDeviceConnections(final long noOfDeviceConnections) {
        if (noOfDeviceConnections < 0) {
            throw new IllegalArgumentException("number of device connections must be >= 0");
        }
        report();
        this.noOfDeviceConnections.set(noOfDeviceConnections);
        LOG.trace("Updated number of device connections for the tenant [{}] to [{}]", tenantId,
                noOfDeviceConnections);
        if (noOfDeviceConnections == 0) {
            vertx.cancelTimer(timerId);
            LOG.trace("Stopped the device connection duration tracker for the tenant [{}].", tenantId);
            return null;
        }

        return this;
    }

    private void report() {
        final long deviceConnectionDuration = noOfDeviceConnections.get()
                * Duration.between(startInstant.getAndSet(Instant.now()), Instant.now()).toMillis();
        recorder.accept(deviceConnectionDuration);
        LOG.trace(
                "Reported device connection duration [tenant : {}, noOfDeviceConnections: {}, connectionDurationInMs: {}].",
                tenantId, noOfDeviceConnections.get(), deviceConnectionDuration);
    }

    /**
     * A builder class for {@link DeviceConnectionDurationTracker}.
     */
    public static final class Builder {

        private long noOfDeviceConnections;
        private Consumer<Long> recorder;
        private long recordingIntervalInMs;
        private String tenantId;
        private Vertx vertx;

        private Builder() {
            // prevent instantiation
        }

        /**
         * Creates an instance of the Builder for the given tenant.
         *
         * @param tenantId The tenant id that the devices belongs to.
         * @return an instance of the Builder.
         * @throws NullPointerException if the tenantId is {@code null}.
         */
        public static Builder forTenant(final String tenantId) {
            final Builder builder = new Builder();
            builder.tenantId = Objects.requireNonNull(tenantId);
            return builder;
        }

        /**
         * Sets the recording interval for the device connection duration.
         *
         * @param recordingIntervalInMs The recording interval in milliseconds.
         * @return an instance of the Builder.
         * @throws IllegalArgumentException if the recording interval is negative.
         */
        public Builder withRecordingInterval(final long recordingIntervalInMs) {
            if (recordingIntervalInMs < 0) {
                throw new IllegalArgumentException("recording interval must be >= 0");
            }
            this.recordingIntervalInMs = recordingIntervalInMs;
            return this;
        }

        /**
         * Sets the number of device connections.
         *
         * @param noOfDeviceConnections The number of device connections.
         * @return a reference to this for fluent use.
         * @throws IllegalArgumentException if the noOfDeviceConnections is negative.
         */
        public Builder withNumberOfDeviceConnections(final long noOfDeviceConnections) {
            if (noOfDeviceConnections < 0) {
                throw new IllegalArgumentException("number of device connections must be >= 0");
            }
            this.noOfDeviceConnections = noOfDeviceConnections;
            return this;
        }

        /**
         * Sets the vert.x instance.
         *
         * @param vertx The vert.x instance.
         * @return a reference to this for fluent use.
         * @throws NullPointerException if vert.x is {@code null}.
         */
        public Builder withVertx(final Vertx vertx) {
            this.vertx = Objects.requireNonNull(vertx);
            return this;
        }

        /**
         * Sets the consumer to consume the calculated device connection duration.
         *
         * @param consumer The consumer that makes use of the calculated device connection duration.
         * @return a reference to this for fluent use.
         * @throws NullPointerException if the consumer is {@code null}.
         */
        public Builder recordUsing(final Consumer<Long> consumer) {
            this.recorder = Objects.requireNonNull(consumer);
            return this;
        }

        /**
         * Returns an instance of the device connection duration tracker.
         *
         * @return an instance of the {@link DeviceConnectionDurationTracker}.
         */
        public DeviceConnectionDurationTracker start() {
            return new DeviceConnectionDurationTracker(tenantId, vertx, noOfDeviceConnections, recorder,
                    recordingIntervalInMs);
        }
    }
}
