/**
 * Copyright (c) 2018 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 1.0 which is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * SPDX-License-Identifier: EPL-1.0
 */

package org.eclipse.hono.adapter.coap;

import org.eclipse.hono.service.metric.Metrics;
import org.springframework.stereotype.Component;

/**
 * Metrics for the COAP based adapters.
 */
@Component
public class CoapAdapterMetrics extends Metrics {

    private static final String SERVICE_PREFIX = "hono.coap";

    @Override
    protected String getPrefix() {
        return SERVICE_PREFIX;
    }

    void incrementProcessedCoapMessages(final String resourceId, final String tenantId) {
        counterService
                .increment(METER_PREFIX + getPrefix() + MESSAGES + mergeAsMetric(resourceId, tenantId) + PROCESSED);
    }

    void incrementUndeliverableCoapMessages(final String resourceId, final String tenantId) {
        counterService.increment(getPrefix() + MESSAGES + mergeAsMetric(resourceId, tenantId) + UNDELIVERABLE);
    }

}
