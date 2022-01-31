/*******************************************************************************
 * Copyright (c) 2020, 2022 Contributors to the Eclipse Foundation
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

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * A status used in Device value object.
 */
@RegisterForReflection
@JsonInclude(value = JsonInclude.Include.NON_DEFAULT)
public final class DeviceStatus extends Status<DeviceStatus> {

    @JsonProperty(RegistryManagementConstants.FIELD_AUTO_PROVISIONED)
    private boolean autoProvisioned = false;

    @JsonProperty(RegistryManagementConstants.FIELD_AUTO_PROVISIONING_NOTIFICATION_SENT)
    private boolean autoProvisioningNotificationSent = false;

    /**
     * Checks if this device was created by Hono's auto-provisioning capability.
     * <p>
     * The default value of this property is {@code false}.
     *
     * @return {@code true} if this device was auto-provisioned.
     */
    public boolean isAutoProvisioned() {
        return autoProvisioned;
    }

    /**
     * Sets whether this device has been provisioned automatically.
     * <p>
     * The default value of this property is {@code false}.
     *
     * @param autoProvisioned {@code true} if this device has been provisioned automatically.
     *
     * @return a reference to this for fluent use.
     */
    public DeviceStatus setAutoProvisioned(final Boolean autoProvisioned) {
        this.autoProvisioned = Optional.ofNullable(autoProvisioned).orElse(false);
        return this;
    }

    /**
     * Checks if a notification has been published, indicating that this device has been provisioned automatically.
     * <p>
     * The default value of this property is {@code false}.
     *
     * @return {@code true} if the notification has already been sent.
     */
    public boolean isAutoProvisioningNotificationSent() {
        return autoProvisioningNotificationSent;
    }

    /**
     * Sets whether a notification has been published, indicating that this device has been provisioned automatically.
     * <p>
     * The default value of this property is {@code false}.
     *
     * @param notificationSent {@code true} if the notification has already been sent.
     *
     * @return a reference to this for fluent use.
     */
    public DeviceStatus setAutoProvisioningNotificationSent(final Boolean notificationSent) {
        this.autoProvisioningNotificationSent = Optional.ofNullable(notificationSent).orElse(false);
        return this;
    }
}
