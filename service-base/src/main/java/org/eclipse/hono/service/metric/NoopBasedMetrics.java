/*******************************************************************************
 * Copyright (c) 2018, 2021 Contributors to the Eclipse Foundation
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

import java.util.Objects;

import org.eclipse.hono.service.metric.MetricsTags.ConnectionAttemptOutcome;
import org.eclipse.hono.service.metric.MetricsTags.Direction;
import org.eclipse.hono.service.metric.MetricsTags.EndpointType;
import org.eclipse.hono.service.metric.MetricsTags.ProcessingOutcome;
import org.eclipse.hono.util.TenantObject;

import io.micrometer.core.instrument.Timer.Sample;

/**
 * A no-op metrics implementation.
 */
public class NoopBasedMetrics implements Metrics {

    /**
     * Creates a new instance.
     */
    protected NoopBasedMetrics() {
        // empty default implementation
    }

    @Override
    public void incrementUnauthenticatedConnections() {
        // empty default implementation
    }

    @Override
    public void decrementUnauthenticatedConnections() {
        // empty default implementation
    }

    @Override
    public void incrementConnections(final String tenantId) {
        Objects.requireNonNull(tenantId);
    }

    @Override
    public void decrementConnections(final String tenantId) {
        Objects.requireNonNull(tenantId);
    }

    @Override
    public void reportConnectionAttempt(final ConnectionAttemptOutcome outcome, final String tenantId) {
        Objects.requireNonNull(outcome);
    }

    @Override
    public void reportConnectionAttempt(final ConnectionAttemptOutcome outcome, final String tenantId, final String cipherSuite) {
        Objects.requireNonNull(outcome);
    }

    @Override
    public int getNumberOfConnections() {
        return 0;
    }

    @Override
    public Sample startTimer() {
        return null;
    }

    @Override
    public void reportTelemetry(
            final MetricsTags.EndpointType type,
            final String tenantId,
            final TenantObject tenantObject,
            final ProcessingOutcome outcome,
            final MetricsTags.QoS qos,
            final int payloadSize,
            final Sample timer) {

        Objects.requireNonNull(type);
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(outcome);
        Objects.requireNonNull(qos);
        Objects.requireNonNull(timer);

        if (EndpointType.TELEMETRY != type && EndpointType.EVENT != type) {
            throw new IllegalArgumentException("type must be either TELEMETRY or EVENT");
        }
        if (payloadSize < 0) {
            throw new IllegalArgumentException("payload size must not be negative");
        }
    }

    @Override
    public void reportTelemetry(
            final MetricsTags.EndpointType type,
            final String tenantId,
            final TenantObject tenantObject,
            final ProcessingOutcome outcome,
            final MetricsTags.QoS qos,
            final int payloadSize,
            final MetricsTags.TtdStatus ttdStatus,
            final Sample timer) {
        reportTelemetry(type, tenantId, tenantObject, outcome, qos, payloadSize, timer);
    }

    @Override
    public void reportCommand(
            final Direction direction,
            final String tenantId,
            final TenantObject tenantObject,
            final ProcessingOutcome outcome,
            final int payloadSize,
            final Sample timer) {

        Objects.requireNonNull(direction);
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(outcome);
        Objects.requireNonNull(timer);

        if (payloadSize < 0) {
            throw new IllegalArgumentException("payload size must not be negative");
        }
    }
}
