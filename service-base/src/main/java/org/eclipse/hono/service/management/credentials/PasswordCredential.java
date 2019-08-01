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
 * Credential Information.
 */
@JsonInclude(value = JsonInclude.Include.NON_NULL)
public class PasswordCredential extends CommonCredential {

    @JsonProperty
    private List<PasswordSecret> secrets = new LinkedList<>();

    @Override
    public List<PasswordSecret> getSecrets() {
        return secrets;
    }

    /**
     * Set the secrets for this credential.
     *
     * @param secrets  The secrets to set.
     * @return         a reference to this for fluent use.
     */
    public PasswordCredential setSecrets(final List<PasswordSecret> secrets) {
        this.secrets = secrets;
        return this;
    }
}
