/**
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 */
package org.eclipse.hono.util;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Encapsulates the credentials information for a device that was found by the get operation of the
 * <a href="https://www.eclipse.org/hono/api/Credentials-API/">Credentials API</a>.
 * <p>
 * Is mapped internally from json representation by jackson-databind.
 */
public final class CredentialsObject {

    @JsonProperty(CredentialsConstants.FIELD_DEVICE_ID)
    private String deviceId;
    @JsonProperty(CredentialsConstants.FIELD_TYPE)
    private String type;
    @JsonProperty(CredentialsConstants.FIELD_AUTH_ID)
    private String authId;
    @JsonProperty(CredentialsConstants.FIELD_ENABLED)
    private Boolean enabled;
    /*
     * Since the format of the secrets field is not determined by the Credentials API, they are best represented as
     * key-value maps with key and value both of type String.
     * The further processing of secrets is part of the validator for the specific type.
     */
    @JsonProperty(CredentialsConstants.FIELD_SECRETS)
    private List<Map<String, String>> secrets;

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(final String deviceId) {
        this.deviceId = deviceId;
    }

    public String getType() {
        return type;
    }

    public void setType(final String type) {
        this.type = type;
    }

    public String getAuthId() {
        return authId;
    }

    public void setAuthId(final String authId) {
        this.authId = authId;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(final Boolean enabled) {
        this.enabled = enabled;
    }

    public List<Map<String, String>> getSecrets() {
        return Collections.unmodifiableList(secrets);
    }

    public void setSecrets(final List<Map<String, String>> secrets) {
        this.secrets = new LinkedList<>(secrets);
    }

    public void addSecret(final Map<String, String> secret) {
        if (secrets == null) {
            secrets = new LinkedList<>();
        }
        secrets.add(secret);
    }
}
