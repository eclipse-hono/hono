/*******************************************************************************
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
 *******************************************************************************/

package org.eclipse.hono.adapter;

import org.eclipse.hono.service.metric.MicrometerBasedMetrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.vertx.core.Vertx;

/**
 * Micrometer based metrics for a protocol adapter.
 */
public class MicrometerBasedProtocolAdapterMetrics extends MicrometerBasedMetrics {

    /**
     * Create a new metrics instance for a protocol adapter.
     *
     * @param registry The meter registry to use.
     * @param vertx The Vert.x instance to use.
     * @param config The adapter properties.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    public MicrometerBasedProtocolAdapterMetrics(
            final MeterRegistry registry,
            final Vertx vertx,
            final ProtocolAdapterProperties config) {
        super(registry, vertx);
        setTenantIdleTimeout(config.getTenantIdleTimeout());
    }
}
