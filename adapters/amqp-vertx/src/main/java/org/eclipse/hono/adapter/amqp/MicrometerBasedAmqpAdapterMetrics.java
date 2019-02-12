/*******************************************************************************
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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

import org.eclipse.hono.service.metric.MicrometerBasedMetrics;
import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.MeterRegistry;

/**
 * Micrometer based metrics for the AMQP adapter.
 */
@Component
public class MicrometerBasedAmqpAdapterMetrics extends MicrometerBasedMetrics implements AmqpAdapterMetrics {

    /**
     * Create a new metrics instance for the AMQP adapter.
     * 
     * @param registry The meter registry to use.
     * 
     * @throws NullPointerException if either parameter is {@code null}.
     */
    public MicrometerBasedAmqpAdapterMetrics(final MeterRegistry registry) {
        super(registry);
    }
}
