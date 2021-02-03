/**
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
 */


package org.eclipse.hono.authentication.quarkus;

import java.util.Objects;

import io.quarkus.arc.config.ConfigProperties;

/**
 * Standard {@link FileBasedAuthenticationServiceConfigProperties} which can be bound to environment variables by Quarkus.
 *
 */
@ConfigProperties(prefix = "hono.auth.svc", namingStrategy = ConfigProperties.NamingStrategy.VERBATIM, failOnMismatchingMember = false)
public class FileBasedAuthenticationServiceConfigProperties extends org.eclipse.hono.authentication.file.FileBasedAuthenticationServiceConfigProperties {

    private SignatureSupportingConfigProperties signing;

    /**
     * Sets the properties configuring key material for signing tokens.
     *
     * @param props The properties.
     * @throws NullPointerException if props is {@code null}.
     */
    public void setSigning(final SignatureSupportingConfigProperties props) {
        signing = Objects.requireNonNull(props);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SignatureSupportingConfigProperties getSigning() {
        return signing;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SignatureSupportingConfigProperties getValidation() {
        return signing;
    }

    /**
     * Standard {@link SignatureSupportingConfigProperties} which can be bound to configuration properties by Quarkus.
     *
     */
    public static class SignatureSupportingConfigProperties extends org.eclipse.hono.config.SignatureSupportingConfigProperties {
    }
}
