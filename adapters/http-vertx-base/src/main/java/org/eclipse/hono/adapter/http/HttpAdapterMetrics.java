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

package org.eclipse.hono.adapter.http;

import org.eclipse.hono.service.metric.Metrics;

/**
 * Metrics for the HTTP based adapters.
 */
public interface HttpAdapterMetrics extends Metrics {

    /**
     * Reports a command being delivered to a device.
     * 
     * @param tenantId The tenant that the device belongs to.
     */
    void incrementCommandDeliveredToDevice(String tenantId);

    /**
     * Reports a TTD having expired without a command being delivered
     * to a device.
     * 
     * @param tenantId The tenant that the device belongs to.
     */
    void incrementNoCommandReceivedAndTTDExpired(String tenantId);

    /**
     * Reports a response to a command being delivered to an application.
     * 
     * @param tenantId The tenant to which the device belongs from which the response
     *                 has been received.
     */
    void incrementCommandResponseDeliveredToApplication(String tenantId);
}
