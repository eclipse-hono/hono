/*******************************************************************************
 * Copyright (c) 2021, 2022 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.adapter.lora;

import java.util.Objects;

/**
 * The key to identify a subscription.
 * To be used for a kind of subscription where there can only be one subscription for a given device.
 */
public class SubscriptionKey {
    private final String tenant;
    private final String deviceId;

    /**
     * Creates a new Key.
     *
     * @param tenant The tenant identifier.
     * @param deviceId The device identifier.
     * @throws NullPointerException If any of the parameters is {@code null}.
     */
    public SubscriptionKey(final String tenant, final String deviceId) {
        this.tenant = Objects.requireNonNull(tenant);
        this.deviceId = Objects.requireNonNull(deviceId);
    }

    public String getTenant() {
        return tenant;
    }

    public String getDeviceId() {
        return deviceId;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || !getClass().isInstance(o)) {
            return false;
        }
        final SubscriptionKey that = (SubscriptionKey) o;
        return tenant.equals(that.tenant) && deviceId.equals(that.deviceId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tenant, deviceId);
    }
}
