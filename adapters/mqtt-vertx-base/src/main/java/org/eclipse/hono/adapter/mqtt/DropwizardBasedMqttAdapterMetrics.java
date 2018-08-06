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

package org.eclipse.hono.adapter.mqtt;

import org.eclipse.hono.service.metric.DropwizardBasedMetrics;
import org.springframework.stereotype.Component;

/**
 * Metrics for the MQTT adapter.
 */
@Component
public class DropwizardBasedMqttAdapterMetrics extends DropwizardBasedMetrics implements MqttAdapterMetrics {

    private static final String SERVICE_PREFIX = "hono.mqtt";

    @Override
    protected String getPrefix() {
        return SERVICE_PREFIX;
    }

    /**
     * Increments the number of MQTT connections that have been established
     * with an authenticated device by one.
     * 
     * @param tenantId The tenant that the device belongs to.
     */
    @Override
    public final void incrementMqttConnections(final String tenantId) {
        counterService.increment(getPrefix() + CONNECTIONS + tenantId);
    }

    /**
     * Decrements the number of MQTT connections that have been established
     * with an authenticated device by one.
     * 
     * @param tenantId The tenant that the device belongs to.
     */
    @Override
    public final void decrementMqttConnections(final String tenantId) {
        counterService.decrement(getPrefix() + CONNECTIONS + tenantId);
    }

    /**
     * Increments the number of MQTT connections that have been established
     * with an <em>unauthenticated</em> device by one.
     */
    @Override
    public final void incrementUnauthenticatedMqttConnections() {
        counterService.increment(getPrefix() + UNAUTHENTICATED_CONNECTIONS);
    }

    /**
     * Decrements the number of MQTT connections that have been established
     * with an <em>unauthenticated</em> device by one.
     */
    @Override
    public final void decrementUnauthenticatedMqttConnections() {
        counterService.decrement(getPrefix() + UNAUTHENTICATED_CONNECTIONS);
    }
}
