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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

/**
 * Device Information.
 */
@JsonInclude(value = Include.NON_NULL)
public class Device {

    private Boolean enabled;

    @JsonProperty("ext")
    @JsonInclude(value = Include.NON_EMPTY)
    private Map<String, Object> extensions = new HashMap<>();

    @JsonInclude(value = Include.NON_EMPTY)
    private Map<String, Object> defaults = new HashMap<>();

    @JsonInclude(value = Include.NON_EMPTY)
    private List<String> via = new LinkedList<>();

    /**
     * Create new instance.
     */
    public Device() {
    }

    /**
     * Create new instance cloned from existing instance.
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
    }

    public void setEnabled(final Boolean enabled) {
        this.enabled = enabled;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setExtensions(final Map<String, Object> extensions) {
        this.extensions = extensions;
    }

    /**
     * Add a new extension entry to the device.
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

    public Map<String, Object> getExtensions() {
        return this.extensions;
    }

    public void setDefaults(final Map<String, Object> defaults) {
        this.defaults = defaults;
    }

    public Map<String, Object> getDefaults() {
        return defaults;
    }

    public List<String> getVia() {
        return via;
    }

    public void setVia(final List<String> via) {
        this.via = via;
    }
}
