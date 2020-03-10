/*******************************************************************************
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.hono.service.util;

import org.eclipse.hono.util.TenantObject;

/**
 * A class offering utility methods for the service base classes.
 */
public final class ServiceBaseUtils {

    private ServiceBaseUtils() {
        // prevents instantiation
    }

    /**
     * Calculates the payload size based on the configured minimum message size.
     * <p>
     * If no minimum message size is configured for a tenant then the actual
     * payload size of the message is returned.
     * <p>
     * Example: The minimum message size for a tenant is configured as 4096 bytes (4KB).
     * So the payload size of a message of size 1KB is calculated as 4KB and for
     * message of size 10KB is calculated as 12KB.
     *
     * @param payloadSize The size of the message payload in bytes.
     * @param tenantObject The TenantObject.
     *
     * @return The calculated payload size.
     */
    public static long calculatePayloadSize(final long payloadSize, final TenantObject tenantObject) {

        if (tenantObject == null) {
            return payloadSize;
        }

        final long minimumMessageSize = tenantObject.getMinimumMessageSize();
        if (minimumMessageSize > 0 && payloadSize > 0) {
            final long modValue = payloadSize % minimumMessageSize;
            if (modValue == 0) {
                return payloadSize;
            } else {
                return payloadSize + (minimumMessageSize - modValue);
            }
        }
        return payloadSize;
    }
}
