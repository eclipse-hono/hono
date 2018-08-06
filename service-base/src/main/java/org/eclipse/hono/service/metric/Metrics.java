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
     * Reports a message received from a device as <em>processed</em>.
     *
     * @param resourceId The ID of the resource to track.
     * @param tenantId The tenant that the device belongs to.
     */
    void incrementProcessedMessages(String resourceId, String tenantId);

    /**
     * Reports a message received from a device as <em>undeliverable</em>.
     *
     * @param resourceId The ID of the resource to track.
     * @param tenantId The tenant that the device belongs to.
     */
    void incrementUndeliverableMessages(String resourceId, String tenantId);

    /**
     * Reports the size of a processed message's payload that has been received
     * from a device.
     *
     * @param resourceId The ID of the resource to track.
     * @param tenantId The tenant that the device belongs to.
     * @param payloadSize The size of the payload in bytes.
     */
    void incrementProcessedPayload(String resourceId, String tenantId, long payloadSize);
}
