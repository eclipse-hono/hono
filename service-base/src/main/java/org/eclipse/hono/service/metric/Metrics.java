/*******************************************************************************
 * Copyright (c) 2016, 2019 Contributors to the Eclipse Foundation
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

import org.eclipse.hono.util.EndpointType;

import io.micrometer.core.instrument.Timer.Sample;

/**
 * A collector for metrics.
 */
public interface Metrics {

    /**
     * Reports a newly established connection with an authenticated device.
     * 
     * @param tenantId The tenant that the device belongs to.
     * @throws NullPointerException if tenant is {@code null}.
     */
    void incrementConnections(String tenantId);

    /**
     * Reports a connection to an authenticated device being closed.
     * 
     * @param tenantId The tenant that the device belongs to.
     * @throws NullPointerException if tenant is {@code null}.
     */
    void decrementConnections(String tenantId);

    /**
     * Reports a newly established connection with an unauthenticated device.
     */
    void incrementUnauthenticatedConnections();

    /**
     * Reports a connection to an unauthenticated device being closed.
     */
    void decrementUnauthenticatedConnections();

    /**
     * Gets the total number of current connections - authenticated for all tenants and unauthenticated.
     *
     * @return total number of connections.
     */
    long getNumberOfConnections();

    /**
     * Starts a new timer.
     * 
     * @return The newly created timer.
     */
    Sample startTimer();

    /**
     * Reports a telemetry message or event received from a device.
     *
     * @param type The type of message received, e.g. <em>telemetry</em> or <em>event</em>.
     * @param tenantId The tenant that the device belongs to.
     * @param outcome The outcome of processing the message.
     * @param qos The delivery semantics used for sending the message downstream.
     * @param timer The timer indicating the amount of time used
     *              for processing the message.
     * @throws NullPointerException if any of the parameters are {@code null}.
     * @throws IllegalArgumentException if type is neither telemetry nor event.
     */
    void reportTelemetry(
            EndpointType type,
            String tenantId,
            MetricsTags.ProcessingOutcome outcome,
            MetricsTags.QoS qos,
            Sample timer);

    /**
     * Reports a telemetry message or event received from a device.
     *
     * @param type The type of message received, e.g. <em>telemetry</em> or <em>event</em>.
     * @param tenantId The tenant that the device belongs to.
     * @param outcome The outcome of processing the message.
     * @param qos The delivery semantics used for sending the message downstream.
     * @param ttdStatus The outcome of processing the TTD value contained in the message.
     * @param timer The timer indicating the amount of time used
     *              for processing the message.
     * @throws NullPointerException if any of the parameters are {@code null}.
     * @throws IllegalArgumentException if type is neither telemetry nor event.
     */
    void reportTelemetry(
            EndpointType type,
            String tenantId,
            MetricsTags.ProcessingOutcome outcome,
            MetricsTags.QoS qos,
            MetricsTags.TtdStatus ttdStatus,
            Sample timer);

    /**
     * Reports the size of a processed message's payload that has been received
     * from a device.
     *
     * @param type The type of message received, e.g. <em>telemetry</em> or <em>event</em>.
     * @param tenantId The tenant that the device belongs to.
     * @param payloadSize The size of the payload in bytes.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    void incrementProcessedPayload(EndpointType type, String tenantId, long payloadSize);

    /**
     * Reports a command being delivered to a device.
     * 
     * @param tenantId The tenant that the device belongs to.
     * @throws NullPointerException if tenant is {@code null}.
     */
    void incrementCommandDeliveredToDevice(String tenantId);

    /**
     * Reports a response to a command being delivered to an application.
     * 
     * @param tenantId The tenant to which the device belongs from which the response
     *                 has been received.
     * @throws NullPointerException if tenant is {@code null}.
     */
    void incrementCommandResponseDeliveredToApplication(String tenantId);
}
