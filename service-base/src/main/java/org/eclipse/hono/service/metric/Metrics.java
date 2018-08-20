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

package org.eclipse.hono.service.metric;

/**
 * A collector for metrics.
 */
public interface Metrics {

    /**
     * Reports a newly established connection with an authenticated device.
     * 
     * @param tenantId The tenant that the device belongs to.
     */
    void incrementConnections(String tenantId);

    /**
     * Reports a connection to an authenticated device being closed.
     * 
     * @param tenantId The tenant that the device belongs to.
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
     * Reports a message received from a device as <em>processed</em>.
     *
     * @param type The type of message received, e.g. <em>telemetry</em> or <em>event</em>.
     * @param tenantId The tenant that the device belongs to.
     */
    void incrementProcessedMessages(String type, String tenantId);

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
     */
    void incrementUndeliverableMessages(String type, String tenantId);

    /**
     * Reports the size of a processed message's payload that has been received
     * from a device.
     *
     * @param type The type of message received, e.g. <em>telemetry</em> or <em>event</em>.
     * @param tenantId The tenant that the device belongs to.
     * @param payloadSize The size of the payload in bytes.
     */
    void incrementProcessedPayload(String type, String tenantId, long payloadSize);

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
