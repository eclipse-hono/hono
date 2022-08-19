/*******************************************************************************
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.util;

import java.util.Objects;

/**
 * A CommandRouter device information holder.
 */
public class CommandRouterDeviceInfo {

    private final String tenantId;
    private final String deviceId;
    private final boolean sendEvent;

    private String stringRep;

    private CommandRouterDeviceInfo(final String tenantId, final String deviceId, final boolean sendEvent) {
        this.tenantId = Objects.requireNonNull(tenantId);
        this.deviceId = Objects.requireNonNull(deviceId);
        this.sendEvent = sendEvent;
    }

    /**
     * Creates a new tenant and device pair.
     *
     * @param tenantId The tenantId.
     * @param deviceId The deviceId.
     * @param sendEvent {@code true} if <em>notification</em> event should be sent.
     * @return The pair.
     * @throws NullPointerException if any value is {@code null}.
     */
    public static CommandRouterDeviceInfo of(final String tenantId, final String deviceId, final boolean sendEvent) {
        return new CommandRouterDeviceInfo(tenantId, deviceId, sendEvent);
    }

    /**
     * Gets the tenant identifier.
     *
     * @return The identifier.
     */
    public String tenantId() {
        return tenantId;
    }

    /**
     * Gets the device identifier.
     *
     * @return The identifier.
     */
    public String deviceId() {
        return deviceId;
    }


    /**
     * <em>notification</em> event flag.
     *
     * @return {@code true} if <em>connected notification</em> event should be sent.
     */
    public boolean sendEvent() {
        return sendEvent;
    }

    @Override
    public String toString() {
        if (stringRep == null) {
            stringRep = String.format("TenantAndDeviceId[tenantId: %s, deviceId: %s, sendEvent: %s]", tenantId,
                    deviceId, sendEvent);
        }
        return stringRep;
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || !(obj instanceof CommandRouterDeviceInfo)) {
            return false;
        }

        final CommandRouterDeviceInfo other = (CommandRouterDeviceInfo) obj;
        if (!tenantId.equals(other.tenantId)) {
            return false;
        }
        if (!deviceId.equals(other.deviceId)) {
            return false;
        }

        return sendEvent == other.sendEvent;
    }

    @Override
    public int hashCode() {
        int result = tenantId.hashCode();
        result = 31 * result + deviceId.hashCode();
        result = 31 * result + Boolean.valueOf(sendEvent).hashCode();
        return result;
    }
}
