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

package org.eclipse.hono.example.protocolgateway;

/**
 * A collection of properties to configure a static device for demonstration purposes.
 */
public class DemoDeviceConfiguration {

    private String tenantId;
    private String deviceId;
    private String username;
    private String password;

    /**
     * Gets the tenant to which the device belongs.
     *
     * @return The tenant id.
     */
    public String getTenantId() {
        return tenantId;
    }

    /**
     * Sets the tenant to which the device belongs.
     *
     * @param tenantId The tenant id.
     */
    public void setTenantId(final String tenantId) {
        this.tenantId = tenantId;
    }

    /**
     * Sets the device id.
     *
     * @return The device id.
     */
    public String getDeviceId() {
        return deviceId;
    }

    /**
     * Sets the device id.
     *
     * @param deviceId The device id.
     */
    public void setDeviceId(final String deviceId) {
        this.deviceId = deviceId;
    }

    /**
     * Gets the allowed username for the device.
     *
     * @return The username.
     */
    public String getUsername() {
        return username;
    }

    /**
     * Sets the allowed username for the device.
     *
     * @param username The username.
     */
    public void setUsername(final String username) {
        this.username = username;
    }

    /**
     * Gets the allowed password for the device.
     *
     * @return Sets the allowed username for the device..
     */
    public String getPassword() {
        return password;
    }

    /**
     * Sets the allowed password for the device.
     *
     * @param password The password.
     */
    public void setPassword(final String password) {
        this.password = password;
    }
}
