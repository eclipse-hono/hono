/*******************************************************************************
 * Copyright (c) 2016, 2021 Contributors to the Eclipse Foundation
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

import org.eclipse.hono.service.metric.Metrics;
import org.eclipse.hono.service.metric.NoopBasedMetrics;

/**
 * Metrics for the MQTT adapter.
 */
public interface MqttAdapterMetrics extends Metrics {

    /**
     * A no-op implementation this specific metrics type.
     */
    final class Noop extends NoopBasedMetrics implements MqttAdapterMetrics {

        private Noop() {
        }
    }

    /**
     * The no-op implementation.
     */
    MqttAdapterMetrics NOOP = new Noop();

    // empty for now
}
