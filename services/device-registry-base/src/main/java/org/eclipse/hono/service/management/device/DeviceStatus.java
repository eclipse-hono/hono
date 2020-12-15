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

package org.eclipse.hono.service.management.device;

import java.util.Optional;

import org.eclipse.hono.util.RegistryManagementConstants;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A status used in Device value object.
 */
@JsonInclude(value = JsonInclude.Include.NON_NULL)
public final class DeviceStatus extends Status<DeviceStatus> {

    private Boolean autoProvisioned;

    private Boolean autoProvisioningNotificationSent;

    /**
     * Checks if this device was created by Hono's auto-provisioning capability.
     * <p>
     * The default value of this property is {@code false}.
     *
     * @return {@code true} if this device was auto-provisioned.
     */
    @JsonProperty(RegistryManagementConstants.FIELD_AUTO_PROVISIONED)
    public boolean isAutoProvisioned() {
        return Optional.ofNullable(autoProvisioned).orElse(false);
    }

    /**
     * Marks this device as auto-provisioned/manually provisioned.
     *
     * @param autoProvisioned {@code true}, if auto-provisioned. May be {@code null} which then defaults to false.
     *
     * @return a reference to this for fluent use.
     */
    public DeviceStatus setAutoProvisioned(final Boolean autoProvisioned) {
        this.autoProvisioned = autoProvisioned;
        return this;
    }

    /**
     * Checks if a notification of this device having been auto-provisioned has been sent to the northbound application.
     * <p>
     * The default value of this property is {@code false}.
     *
     * @return {@code true} if the notification has been sent.
     */
    @JsonProperty(RegistryManagementConstants.FIELD_AUTO_PROVISIONING_NOTIFICATION_SENT)
    public boolean isAutoProvisioningNotificationSent() {
        return Optional.ofNullable(autoProvisioningNotificationSent).orElse(false);
    }

    /**
     * Marks this device's auto-provisioning as having been sent.
     *
     * @param autoProvisioningNotificationSent {@code true}, if the notification has been sent.
     *                                                     May be {@code null} which then defaults to false.
     *
     * @return a reference to this for fluent use.
     */
    public DeviceStatus setAutoProvisioningNotificationSent(final Boolean autoProvisioningNotificationSent) {
        this.autoProvisioningNotificationSent = autoProvisioningNotificationSent;
        return this;
    }
}
