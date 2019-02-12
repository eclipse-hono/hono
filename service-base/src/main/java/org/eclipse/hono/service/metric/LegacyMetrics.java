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

package org.eclipse.hono.service.metric;

/**
 * A service for reporting legacy metrics.
 */
interface LegacyMetrics {

    /**
     * Reports a message received from a device as <em>processed</em>.
     *
     * @param type The type of message received, e.g. <em>telemetry</em> or <em>event</em>.
     * @param tenantId The tenant that the device belongs to.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    void incrementProcessedMessages(MetricsTags.EndpointType type, String tenantId);

    /**
     * Reports a message received from a device as <em>undeliverable</em>.
     * <p>
     * A message is considered undeliverable if the failure to deliver has not been caused by the device
     * that the message originates from. In particular, messages that cannot be authorized or
     * that are published to an unsupported/unrecognized endpoint do not fall into this category.
     * Such messages should be silently discarded instead.
     * 
     * @param type The type of message received, e.g. <em>telemetry</em> or <em>event</em>.
     * @param tenantId The tenant that the device belongs to.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    void incrementUndeliverableMessages(MetricsTags.EndpointType type, String tenantId);

    /**
     * Reports a TTD having expired without a command being delivered
     * to a device.
     * 
     * @param tenantId The tenant that the device belongs to.
     * @throws NullPointerException if tenant is {@code null}.
     */
    void incrementNoCommandReceivedAndTTDExpired(String tenantId);

    /**
     * Reports a response to a command being delivered to an application.
     * 
     * @param tenantId The tenant to which the device belongs from which the response
     *                 has been received.
     * @throws NullPointerException if tenant is {@code null}.
     */
    void incrementCommandResponseDeliveredToApplication(String tenantId);

    /**
     * Reports a command being delivered to a device.
     * 
     * @param tenantId The tenant that the device belongs to.
     * @throws NullPointerException if tenant is {@code null}.
     */
    void incrementCommandDeliveredToDevice(String tenantId);
}
