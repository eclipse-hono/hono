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
 * A generic credential.
 */
public class GenericCredential extends CommonCredential {

    private String type;

    @JsonAnySetter
    private Map<String, Object> additionalProperties = new HashMap<>();

    private List<GenericSecret> secrets = new LinkedList<>();

    public void setType(final String type) {
        this.type = type;
    }

    public String getType() {
        return type;
    }

    @Override
    public List<GenericSecret> getSecrets() {
        return this.secrets;
    }

    public void setSecrets(final List<GenericSecret> secrets) {
        this.secrets = secrets;
    }

    public void setAdditionalProperties(final Map<String, Object> additionalProperties) {
        this.additionalProperties = additionalProperties;
    }

    @JsonAnyGetter
    public Map<String, Object> getAdditionalProperties() {
        return this.additionalProperties;
    }

}
