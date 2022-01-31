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

import java.util.Objects;

import org.eclipse.hono.util.RegistryManagementConstants;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * Device information which also includes device identifier.
 */
@RegisterForReflection(ignoreNested = false)
@JsonInclude(value = JsonInclude.Include.NON_NULL)
public final class DeviceWithId extends Device {

    @JsonProperty(RegistryManagementConstants.FIELD_ID)
    private String id;

    /**
     * Empty default constructor.
     * <p>
     * Mainly useful for mapping from JSON.
     */
    private DeviceWithId() {
    }

    private DeviceWithId(final String id, final Device device) {
        super(device);
        this.id = id;
    }

    /**
     * Creates an instance of {@link DeviceWithId} from the given device identifier and
     * {@link Device} instance.
     *
     * @param id The device identifier.
     * @param device The device information.
     *
     * @return an instance of {@link DeviceWithId}.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    public static DeviceWithId from(final String id, final Device device) {
        Objects.requireNonNull(id);
        Objects.requireNonNull(device);

        return new DeviceWithId(id, device);
    }

    /**
     * Returns the device identifier.
     *
     * @return the device identifier.
     */
    public String getId() {
        return id;
    }
}
