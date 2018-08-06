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

package org.eclipse.hono.adapter.kura;

import org.eclipse.hono.adapter.mqtt.DropwizardBasedMqttAdapterMetrics;
import org.springframework.stereotype.Component;

/**
 * Metrics for the Kura adapter.
 */
@Component
public class DropwizardBasedKuraAdapterMetrics extends DropwizardBasedMqttAdapterMetrics {

    private static final String SERVICE_PREFIX = "hono.kura";

    @Override
    protected String getPrefix() {
        return SERVICE_PREFIX;
    }
}
