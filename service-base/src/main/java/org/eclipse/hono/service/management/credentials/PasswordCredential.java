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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.LinkedList;
import java.util.List;

/**
 * A credential type for storing a password for a device.
 * <p>
 * See <a href="https://www.eclipse.org/hono/docs/api/credentials/#hashed-password">Hashed Password</a> for an example
 * of the configuration properties for this credential type.
 */
@JsonInclude(value = JsonInclude.Include.NON_NULL)
public class PasswordCredential extends CommonCredential {

    @JsonProperty
    @JsonInclude(value = JsonInclude.Include.NON_EMPTY)
    private List<PasswordSecret> secrets = new LinkedList<>();

    @Override
    public List<PasswordSecret> getSecrets() {
        return secrets;
    }

    /**
     * Sets the list of password secrets to use for authenticating a device to protocol adapters.
     * <p>
     * The list cannot be empty and each secret is scoped to its validity period.
     *
     * @param secrets The list of password secrets to set.
     * @return        a reference to this for fluent use.
     * @throws IllegalArgumentException if the list of secrets is empty.
     */
    public PasswordCredential setSecrets(final List<PasswordSecret> secrets) {
        if (secrets != null && secrets.isEmpty()) {
            throw new IllegalArgumentException("secrets cannot be empty");
        }
        this.secrets = secrets;
        return this;
    }
}
