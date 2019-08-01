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
package org.eclipse.hono.service.management.credentials;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.hono.util.RegistryManagementConstants;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.annotation.JsonTypeIdResolver;

/**
 * Common Information.
 */
@JsonInclude(value = JsonInclude.Include.NON_NULL)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type", visible = true)
@JsonTypeIdResolver(CredentialTypeResolver.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public abstract class CommonCredential {

    @JsonProperty(RegistryManagementConstants.FIELD_AUTH_ID)
    private String authId;
    @JsonProperty
    private Boolean enabled;
    @JsonProperty
    private String comment;

    @JsonProperty(RegistryManagementConstants.FIELD_EXT)
    @JsonInclude(value = JsonInclude.Include.NON_EMPTY)
    private Map<String, Object> extensions = new HashMap<>();

    /**
     * Get a list of secrets for this credential.
     * 
     * @return The list of credentials, must not be {@code null}.
     */
    protected abstract List<? extends CommonSecret> getSecrets();

    public String getAuthId() {
        return authId;
    }

    /**
     * Set the authId for this credential.
     * 
     * @param authId  The authId to set for this credential.
     * @return        a reference to this for fluent use.
     */
    public CommonCredential setAuthId(final String authId) {
        this.authId = authId;
        return this;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    /**
     * Set the enabled property for this credential.
     *
     * @param enabled  The enabled property.
     * @return         a reference to this for fluent use.
     */
    public CommonCredential setEnabled(final Boolean enabled) {
        this.enabled = enabled;
        return this;
    }

    public String getComment() {
        return comment;
    }

    /**
     * Set the comment property for this credential.
     *
     * @param comment   The comment to set for this credential.
     * @return          a reference to this for fluent use.
     */
    public CommonCredential setComment(final String comment) {
        this.comment = comment;
        return this;
    }

    public Map<String, Object> getExtensions() {
        return extensions;
    }

    /**
     * Set the extension properties for this credential.
     *
     * @param extensions    The extension properties to set.
     * @return      a reference to this for fluent use.
     */
    public CommonCredential setExtensions(final Map<String, Object> extensions) {
        this.extensions = extensions;
        return this;
    }

    /**
     * Add a new extension entry to the device.
     * <p>
     * If an extension property already exist for the specified key, the old value is replaced by the specified value.
     * 
     * @param key The key of the entry.
     * @param value The value of the entry.
     * @return This instance, to allowed chained invocations.
     */
    public CommonCredential putExtension(final String key, final Object value) {
        if (this.extensions == null) {
            this.extensions = new HashMap<>();
        }
        this.extensions.put(key, value);
        return this;
    }

    /**
     * Check if credential is valid.
     * 
     * @throws IllegalStateException if the credential is not valid.
     */
    public void checkValidity() {
        if (this.authId == null || this.authId.isEmpty()) {
            throw new IllegalStateException("missing auth ID");
        }
    }
}
