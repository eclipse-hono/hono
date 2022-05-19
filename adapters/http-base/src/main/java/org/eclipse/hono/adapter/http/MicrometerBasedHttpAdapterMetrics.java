/*******************************************************************************
 * Copyright (c) 2018, 2022 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.adapter.http;

import org.eclipse.hono.adapter.MicrometerBasedProtocolAdapterMetrics;
import org.eclipse.hono.adapter.ProtocolAdapterProperties;

import io.micrometer.core.instrument.MeterRegistry;
import io.vertx.core.Vertx;

/**
 * Metrics for the HTTP based adapters.
 */
public class MicrometerBasedHttpAdapterMetrics extends MicrometerBasedProtocolAdapterMetrics implements HttpAdapterMetrics {

    /**
     * Create a new metrics instance for HTTP adapters.
     *
     * @param registry The meter registry to use.
     * @param vertx The Vert.x instance to use.
     * @param config The adapter properties.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    public MicrometerBasedHttpAdapterMetrics(
            final MeterRegistry registry,
            final Vertx vertx,
            final ProtocolAdapterProperties config) {
        super(registry, vertx, config);
    }
}
