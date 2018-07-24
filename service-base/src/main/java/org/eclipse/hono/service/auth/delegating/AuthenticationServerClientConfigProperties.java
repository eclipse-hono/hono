/*******************************************************************************
 * Copyright (c) 2016, 2017 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.service.auth.delegating;

import org.eclipse.hono.config.ClientConfigProperties;
import org.eclipse.hono.config.SignatureSupportingConfigProperties;


/**
 * Configuration properties for the {@code DelegatingAuthenticationService}.
 *
 */
public class AuthenticationServerClientConfigProperties extends ClientConfigProperties {

    private final SignatureSupportingConfigProperties validation = new SignatureSupportingConfigProperties();

    /**
     * Gets the properties for determining key material for validating user tokens.
     * 
     * @return The properties.
     */
    public final SignatureSupportingConfigProperties getValidation() {
        return validation;
    }
}
