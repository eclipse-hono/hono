/*******************************************************************************
 * Copyright (c) 2016, 2018 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.adapter.amqp;

import org.eclipse.hono.service.metric.Metrics;
import org.eclipse.hono.service.metric.NoopBasedMetrics;

/**
 * Metrics for the AMQP adapter.
 */
public interface AmqpAdapterMetrics extends Metrics {

    /**
     * A no-op implementation this specific metrics type.
     */
    final class Noop extends NoopBasedMetrics implements AmqpAdapterMetrics {

        private Noop() {
        }
    }

    AmqpAdapterMetrics NOOP = new Noop();

    // empty for now
}
