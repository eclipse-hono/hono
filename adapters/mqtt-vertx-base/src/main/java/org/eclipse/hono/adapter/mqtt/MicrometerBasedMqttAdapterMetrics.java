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

package org.eclipse.hono.adapter.mqtt;

import org.eclipse.hono.service.metric.MicrometerBasedMetrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.vertx.core.Vertx;

/**
 * Metrics for the MQTT based adapters.
 */
public class MicrometerBasedMqttAdapterMetrics extends MicrometerBasedMetrics implements MqttAdapterMetrics {

    /**
     * Create a new metrics instance for MQTT adapters.
     *
     * @param registry The meter registry to use.
     * @param vertx The Vert.x instance to use.
     *
     * @throws NullPointerException if either parameter is {@code null}.
     */
    public MicrometerBasedMqttAdapterMetrics(final MeterRegistry registry, final Vertx vertx) {
        super(registry, vertx);
    }
}
