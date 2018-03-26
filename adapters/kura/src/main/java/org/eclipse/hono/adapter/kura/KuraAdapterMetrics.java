/**
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.adapter.kura;

import org.eclipse.hono.adapter.mqtt.MqttAdapterMetrics;
import org.springframework.stereotype.Component;

/**
 * Metrics for the Kura adapter.
 */
@Component
public class KuraAdapterMetrics extends MqttAdapterMetrics {

    private static final String SERVICE_PREFIX = "hono.kura";

    @Override
    protected String getPrefix() {
        return SERVICE_PREFIX;
    }
}
