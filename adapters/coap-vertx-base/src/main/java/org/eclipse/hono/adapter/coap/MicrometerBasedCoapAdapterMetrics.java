/*******************************************************************************
 * Copyright (c) 2018 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.adapter.coap;

import org.eclipse.hono.service.metric.MicrometerBasedMetrics;
import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.MeterRegistry;

/**
 * Metrics for the COAP based adapters.
 */
@Component
public class MicrometerBasedCoapAdapterMetrics extends MicrometerBasedMetrics implements CoapAdapterMetrics {

    /**
     * Create a new metrics instance for COAP adapters.
     * 
     * @param registry The meter registry to use.
     * 
     * @throws NullPointerException if either parameter is {@code null}.
     */
    public MicrometerBasedCoapAdapterMetrics(final MeterRegistry registry) {
        super(registry);
    }
}
