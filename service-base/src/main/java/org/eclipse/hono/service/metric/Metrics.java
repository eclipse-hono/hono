/*******************************************************************************
 * Copyright (c) 2016, 2021 Contributors to the Eclipse Foundation
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

import org.eclipse.hono.util.TenantObject;

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
     * Reports the outcome of an authenticated device's attempt to establish a connection
     * to a protocol adapter.
     *
     * @param outcome The outcome of the connection attempt.
     * @param tenantId The tenant that the device belongs to or {@code null} if
     *                 the tenant could not (yet) be determined.
     * @throws NullPointerException if outcome is {@code null}.
     */
    void reportConnectionAttempt(MetricsTags.ConnectionAttemptOutcome outcome, String tenantId);

    /**
     * Reports the outcome of an authenticated device's attempt to establish a connection
     * to a protocol adapter.
     *
     * @param outcome The outcome of the connection attempt.
     * @param tenantId The tenant that the device belongs to or {@code null} if
     *                 the tenant could not (yet) be determined.
     * @param cipherSuite The name of the cipher suite used in the connection attempt or {@code null}
     *                    if the connection does not use TLS or the cipher suite could not (yet) be determined.
     * @throws NullPointerException if outcome is {@code null}.
     */
    void reportConnectionAttempt(MetricsTags.ConnectionAttemptOutcome outcome, String tenantId, String cipherSuite);

    /**
     * Gets the total number of current connections - authenticated for all tenants and unauthenticated.
     *
     * @return total number of connections.
     */
    int getNumberOfConnections();

    /**
     * Starts a new timer.
     *
     * @return The newly created timer.
     */
    Sample startTimer();

    /**
     * Reports a telemetry message or event received from a device. 
     * <p> 
     * The payload size of the message is calculated based on the configured 
     * minimum message size and the calculated size is reported. 
     * <p>
     * The configured minimum message size is retrieved from the tenant 
     * configuration object.
     *
     * @param type The type of message received, e.g. <em>telemetry</em> or <em>event</em>.
     * @param tenantId The tenant that the device belongs to.
     * @param tenantObject The tenant configuration object or {@code null}.
     * @param outcome The outcome of processing the message.
     * @param qos The delivery semantics used for sending the message downstream.
     * @param payloadSize The number of bytes contained in the message's payload.
     * @param timer The timer indicating the amount of time used
     *              for processing the message.
     * @throws NullPointerException if any of the parameters except the tenant object are {@code null}.
     * @throws IllegalArgumentException if type is neither telemetry nor event or
     *                    if payload size is negative.
     */
    void reportTelemetry(
            MetricsTags.EndpointType type,
            String tenantId,
            TenantObject tenantObject,
            MetricsTags.ProcessingOutcome outcome,
            MetricsTags.QoS qos,
            int payloadSize,
            Sample timer);

    /**
     * Reports a telemetry message or event received from a device.
     * <p> 
     * The payload size of the message is calculated based on the configured 
     * minimum message size and the calculated size is reported. 
     * <p>
     * The configured minimum message size is retrieved from the tenant 
     * configuration object.
     *
     * @param type The type of message received, e.g. <em>telemetry</em> or <em>event</em>.
     * @param tenantId The tenant that the device belongs to.
     * @param tenantObject The tenant configuration object or {@code null}.
     * @param outcome The outcome of processing the message.
     * @param qos The delivery semantics used for sending the message downstream.
     * @param payloadSize The number of bytes contained in the message's payload.
     * @param ttdStatus The outcome of processing the TTD value contained in the message.
     * @param timer The timer indicating the amount of time used
     *              for processing the message.
     * @throws NullPointerException if any of the parameters except the tenant object are {@code null}.
     * @throws IllegalArgumentException if type is neither telemetry nor event or
     *                    if payload size is negative.
     */
    void reportTelemetry(
            MetricsTags.EndpointType type,
            String tenantId,
            TenantObject tenantObject,
            MetricsTags.ProcessingOutcome outcome,
            MetricsTags.QoS qos,
            int payloadSize,
            MetricsTags.TtdStatus ttdStatus,
            Sample timer);

    /**
     * Reports a command &amp; control message being transferred to/from a device.
     * <p> 
     * The payload size of the message is calculated based on the configured 
     * minimum message size and the calculated size is reported. 
     * <p>
     * The configured minimum message size is retrieved from the tenant 
     * configuration object.
     *
     * @param direction The command message's direction.
     * @param tenantId The tenant that the device belongs to.
     * @param tenantObject The tenant configuration object or {@code null}.
     * @param outcome The outcome of processing the message.
     * @param payloadSize The number of bytes contained in the message's payload.
     * @param timer The timer indicating the amount of time used
     *              for processing the message.
     * @throws NullPointerException if any of the parameters except the tenant object are {@code null}.
     * @throws IllegalArgumentException if payload size is negative.
     */
    void reportCommand(
            MetricsTags.Direction direction,
            String tenantId,
            TenantObject tenantObject,
            MetricsTags.ProcessingOutcome outcome,
            int payloadSize,
            Sample timer);
}
