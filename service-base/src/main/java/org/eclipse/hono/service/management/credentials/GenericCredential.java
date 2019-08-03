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
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;

/**
 * A generic credentials implementation.
 */
public class GenericCredential extends CommonCredential {

    private String type;

    @JsonAnySetter
    private Map<String, Object> additionalProperties = new HashMap<>();

    private List<GenericSecret> secrets = new LinkedList<>();

    /**
     * Sets the credentials type name reflecting the type of authentication mechanism a device will use
     * when authenticating to protocol adapters.
     * 
     * @param type  The credential name type to set.
     * @return      a reference to this for fluent use.
     * 
     * @see <a href="https://www.eclipse.org/hono/docs/api/credentials-api/#standard-credential-types">Standard Credential Types</a>
     */
    public GenericCredential setType(final String type) {
        this.type = type;
        return this;
    }

    public String getType() {
        return type;
    }

    @Override
    public List<GenericSecret> getSecrets() {
        return this.secrets;
    }

    /**
     * Sets the list of secrets to use for authenticating a device to protocol adapters.
     * <p>
     * The list cannot be empty and each secret is scoped to its validity period.
     *
     * @param secrets The secret to set.
     * @return        a reference to this for fluent use.
     */
    public GenericCredential setSecrets(final List<GenericSecret> secrets) {
        this.secrets = secrets;
        return this;
    }

    /**
     * Sets the additional properties for this credential.
     * 
     * @param additionalProperties  The additional properties for this credential.
     * @return                      a reference to this for fluent use.
     */
    public GenericCredential setAdditionalProperties(final Map<String, Object> additionalProperties) {
        this.additionalProperties = additionalProperties;
        return this;
    }

    @JsonAnyGetter
    public Map<String, Object> getAdditionalProperties() {
        return this.additionalProperties;
    }

}
