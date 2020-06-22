/*******************************************************************************
 * Copyright (c) 2018, 2020 Contributors to the Eclipse Foundation
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

import org.eclipse.hono.service.metric.MetricsTags.Direction;
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
    }

    @Override
    public void incrementUnauthenticatedConnections() {
    }

    @Override
    public void incrementConnections(final String tenantId) {
    }

    @Override
    public void decrementUnauthenticatedConnections() {
    }

    @Override
    public void incrementRejectedConnectionsDueToAdapterConnectionLimit() {
    }

    @Override
    public void decrementConnections(final String tenantId) {
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
    }

    @Override
    public void reportCommand(
            final Direction direction,
            final String tenantId,
            final TenantObject tenantObject,
            final ProcessingOutcome outcome,
            final int payloadSize,
            final Sample timer) {
    }
}
