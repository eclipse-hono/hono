/**
 * Copyright (c) 2017, 2018 Bosch Software Innovations GmbH and others
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 *    Red Hat Inc
 */

package org.eclipse.hono.adapter.mqtt;

import org.eclipse.hono.service.metric.Metrics;
import org.springframework.stereotype.Component;

/**
 * Metrics for the MQTT adapter.
 */
@Component
public class MqttAdapterMetrics extends Metrics {

    private static final String SERVICE_PREFIX = "hono.mqtt";

    @Override
    protected String getPrefix() {
        return SERVICE_PREFIX;
    }

    void incrementProcessedMqttMessages(final String resourceId, final String tenantId) {
        counterService.increment(METER_PREFIX + getPrefix() + MESSAGES + mergeAsMetric(resourceId, tenantId) + PROCESSED);
    }

    void incrementUndeliverableMqttMessages(final String resourceId, final String tenantId) {
        counterService.increment(getPrefix() + MESSAGES + mergeAsMetric(resourceId, tenantId) + UNDELIVERABLE);
    }

    void incrementMqttConnections(final String tenantId) {
        counterService.increment(getPrefix() + CONNECTIONS + tenantId);
    }

    void decrementMqttConnections(final String tenantId) {
        counterService.decrement(getPrefix() + CONNECTIONS + tenantId);
    }

    void incrementUnauthenticatedMqttConnections() {
        counterService.increment(getPrefix() + UNAUTHENTICATED_CONNECTIONS);
    }

    void decrementUnauthenticatedMqttConnections() {
        counterService.decrement(getPrefix() + UNAUTHENTICATED_CONNECTIONS);
    }
}
