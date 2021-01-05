/*******************************************************************************
 * Copyright (c) 2016, 2021 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.service.auth.impl;

import java.util.List;
import java.util.Objects;

import org.eclipse.hono.config.SignatureSupportingConfigProperties;
import org.eclipse.hono.service.auth.AbstractHonoAuthenticationService;


/**
 * Configuration properties for the {@code FileBasedAuthenticationService}.
 *
 */
public class FileBasedAuthenticationServiceConfigProperties {

    private final SignatureSupportingConfigProperties signing = new SignatureSupportingConfigProperties();
    private String permissionsPath;
    private List<String> supportedSaslMechanisms = List.of(AbstractHonoAuthenticationService.DEFAULT_SASL_MECHANISMS);

    /**
     * Gets the properties for determining key material for creating tokens.
     *
     * @return The properties.
     */
    public final SignatureSupportingConfigProperties getSigning() {
        return signing;
    }

    /**
     * Gets the properties for determining key material for validating tokens issued by this service.
     *
     * @return The properties.
     */
    public final SignatureSupportingConfigProperties getValidation() {
        return signing;
    }

    /**
     * Gets the path to the file that the authorization rules are loaded from.
     *
     * @return The path.
     */
    public final String getPermissionsPath() {
        return permissionsPath;
    }

    /**
     * Sets the path to the file that the authorization rules should be loaded from.
     *
     * @param permissionsPath The path.
     * @throws NullPointerException if the path is {@code null}.
     */
    public final void setPermissionsPath(final String permissionsPath) {
        this.permissionsPath = Objects.requireNonNull(permissionsPath);
    }

    /**
     * Gets the SASL mechanisms supported by the configured service.
     *
     * @return The supported SASL mechanisms.
     */
    public final List<String> getSupportedSaslMechanisms() {
        return supportedSaslMechanisms;
    }

    /**
     * Sets the SASL mechanisms supported by the configured service.
     *
     * @param supportedSaslMechanisms The supported SASL mechanisms.
     * @throws NullPointerException if supportedSaslMechanisms is {@code null}.
     * @throws IllegalArgumentException if supportedSaslMechanisms is empty or contains mechanisms other than PLAIN and
     *             EXTERNAL.
     */
    public final void setSupportedSaslMechanisms(final List<String> supportedSaslMechanisms) {
        if (Objects.requireNonNull(supportedSaslMechanisms).stream()
                .noneMatch(AbstractHonoAuthenticationService::isCompatibleSaslMechanism)) {
            throw new IllegalArgumentException("invalid list of SASL mechanisms");
        }
        this.supportedSaslMechanisms = supportedSaslMechanisms;
    }
}
