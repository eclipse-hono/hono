/*******************************************************************************
 * Copyright (c) 2019, 2022 Contributors to the Eclipse Foundation
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
import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * This class encapsulates secrets information for a generic credential type.
 */
@RegisterForReflection
public class GenericSecret extends CommonSecret {

    @JsonAnySetter
    private Map<String, Object> additionalProperties = new HashMap<>();

    public void setAdditionalProperties(final Map<String, Object> additionalProperties) {
        this.additionalProperties = additionalProperties;
    }

    @JsonAnyGetter
    public Map<String, Object> getAdditionalProperties() {
        return this.additionalProperties;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Sets this secret's additional properties to the value of the other secret's corresponding
     * properties if this secret's additionalProperties map is empty.
     */
    @Override
    protected void mergeProperties(final CommonSecret otherSecret) {

        Objects.requireNonNull(otherSecret);

        final GenericSecret otherGenericSecret = (GenericSecret) otherSecret;
        if (this.additionalProperties.isEmpty()) {
            this.additionalProperties.putAll(otherGenericSecret.additionalProperties);
        }
    }
}
