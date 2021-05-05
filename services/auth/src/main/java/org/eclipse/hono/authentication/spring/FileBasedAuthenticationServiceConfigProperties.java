/*******************************************************************************
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.authentication.spring;

import org.eclipse.hono.config.SignatureSupportingConfigProperties;


/**
 * Configuration properties for the {@code FileBasedAuthenticationService}.
 *
 */
public class FileBasedAuthenticationServiceConfigProperties extends org.eclipse.hono.authentication.file.FileBasedAuthenticationServiceConfigProperties {

    private final SignatureSupportingConfigProperties signing = new SignatureSupportingConfigProperties();

    /**
     * Gets the properties for determining key material for creating tokens.
     *
     * @return The properties.
     */
    @Override
    public final SignatureSupportingConfigProperties getSigning() {
        return signing;
    }

    /**
     * Gets the properties for determining key material for validating tokens issued by this service.
     *
     * @return The properties.
     */
    @Override
    public final SignatureSupportingConfigProperties getValidation() {
        return signing;
    }
}
