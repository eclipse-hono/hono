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

package org.eclipse.hono.service.management.tenant;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;
import org.eclipse.hono.util.TenantConstants;

/**
 * Adapters Information.
 */
@JsonInclude(value = JsonInclude.Include.NON_NULL)
public class Adapter {

    private Boolean enabled;

    @JsonProperty(required = true)
    private String type;

    @JsonProperty(TenantConstants.FIELD_ADAPTERS_DEVICE_AUTHENTICATION_REQUIRED)
    private Boolean deviceAuthenticationRequired;

    @JsonProperty("ext")
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private Map<String, Object> extensions;

    /**
     * Set the enabled property.
     *
     * @param enabled the value to assign
     * @return a reference to this for fluent use.
     */
    public Adapter setEnabled(final Boolean enabled) {
        this.enabled = enabled;
        return this;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    /**
     * Set the type property.
     *
     * @param type the value to assign
     * @return a reference to this for fluent use.
     */
    public Adapter setType(final String type) {
        this.type = type;
        return this;
    }

    public String getType() {
        return type;
    }

    /**
     * Set the device-authentication-required property.
     *
     * @param enabled the value to assign
     * @return a reference to this for fluent use.
     */
    public Adapter setDeviceAuthenticationRequired(final Boolean enabled) {
        this.deviceAuthenticationRequired = enabled;
        return this;
    }

    public Boolean getDeviceAuthenticationRequired() {
        return deviceAuthenticationRequired;
    }

    /**
     * Set the extension field.
     *
     * @param extensions the value to assign
     * @return a reference to this for fluent use.
     */
    public Adapter setExtensions(final Map<String, Object> extensions) {
        this.extensions = extensions;
        return this;
    }

    public Map<String, Object> getExtensions() {
        return this.extensions;
    }
}
