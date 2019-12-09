/*******************************************************************************
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.eclipse.hono.util.RegistryManagementConstants;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

/**
 * Device Information.
 */
@JsonInclude(value = Include.NON_NULL)
public class Device {

    private Boolean enabled;

    @JsonProperty(RegistryManagementConstants.FIELD_EXT)
    @JsonInclude(value = Include.NON_EMPTY)
    private Map<String, Object> extensions = new HashMap<>();

    @JsonInclude(value = Include.NON_EMPTY)
    private Map<String, Object> defaults = new HashMap<>();

    @JsonInclude(value = Include.NON_EMPTY)
    @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
    private List<String> via = new LinkedList<>();

    @JsonInclude(value = Include.NON_EMPTY)
    @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
    private List<String> memberOf = new LinkedList<>();

    /**
     * Creates a new Device instance.
     */
    public Device() {
    }

    /**
     * Creates a new instance cloned from an existing instance.
     * 
     * @param other The device to copy from.
     */
    public Device(final Device other) {
        Objects.requireNonNull(other);
        this.enabled = other.enabled;
        if (other.extensions != null) {
            this.extensions = new HashMap<>(other.extensions);
        }
        if (other.defaults != null) {
            this.defaults = new HashMap<>(other.defaults);
        }
        if (other.via != null) {
            this.via = new ArrayList<>(other.via);
        }
        if (other.memberOf != null) {
            this.memberOf = new ArrayList<>(other.memberOf);
        }
    }

    /**
     * Sets the enabled property for this device.
     *
     * @param enabled The enabled property to set.
     * @return        a reference to this for fluent use.
     */
    public Device setEnabled(final Boolean enabled) {
        this.enabled = enabled;
        return this;
    }

    /**
     * Checks if this device is enabled.
     * 
     * @return {@code true} if this device is enabled.
     */
    public Boolean getEnabled() {
        return enabled;
    }

    /**
     * Sets the extension properties for this device.
     * 
     * @param extensions The extension properties to set.
     * @return           a reference to this for fluent use.
     */
    public Device setExtensions(final Map<String, Object> extensions) {
        this.extensions = extensions;
        return this;
    }

    /**
     * Adds a new extension entry to the device.
     * 
     * @param key The key of the entry.
     * @param value The value of the entry.
     * @return This instance, to allowed chained invocations.
     */
    public Device putExtension(final String key, final Object value) {
        if (this.extensions == null) {
            this.extensions = new HashMap<>();
        }
        this.extensions.put(key, value);
        return this;
    }

    /**
     * Gets the extension properties for this device.
     * 
     * @return The extension properties.
     */
    public Map<String, Object> getExtensions() {
        return this.extensions;
    }

    /**
     * Sets the defaults for this device.
     *
     * @param defaults  The defaults to set for this device.
     * @return          a reference to this for fluent use.
     */
    public Device setDefaults(final Map<String, Object> defaults) {
        this.defaults = defaults;
        return this;
    }

    /**
     * Gets the default properties for this device.
     *
     * @return The default properties.
     */
    public Map<String, Object> getDefaults() {
        return defaults;
    }

    /**
     * Gets the identifiers of the gateway devices that this device may connect via.
     * 
     * @return The identifiers.
     */
    public List<String> getVia() {
        return via;
    }

    /**
     * Sets the identifiers of the gateway devices that this device may connect via.
     * 
     * @param via The via property to set.
     * @return    a reference to this for fluent use.
     */
    public Device setVia(final List<String> via) {
        if (memberOf != null && memberOf.size() > 0) {
            throw new IllegalArgumentException("A device must not have via and memberOf property set at the same time.");
        }
        this.via = via;
        return this;
    }

    /**
     * Gets the identifiers of the gateway groups in which the device is a member.
     *
     * @return The identifiers.
     */
    public List<String> getMemberOf() {
        return memberOf;
    }

    /**
     * Sets the identifiers of the gateway groups in which the device is a member.
     *
     * @param memberOf The memberOf property to set.
     * @return    a reference to this for fluent use.
     */
    public Device setMemberOf(final List<String> memberOf) {
        if (via != null && via.size() > 0) {
            throw new IllegalArgumentException("A device must not have via and memberOf property set at the same time.");
        }
        this.memberOf = memberOf;
        return this;
    }

}
