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

package org.eclipse.hono.authentication.file;

import java.util.List;
import java.util.Objects;

import org.eclipse.hono.service.auth.AbstractHonoAuthenticationService;
import org.eclipse.hono.service.auth.SignatureSupportingConfigProperties;
import org.eclipse.hono.util.AuthenticationConstants;


/**
 * Configuration properties for the {@code FileBasedAuthenticationService}.
 *
 */
public class FileBasedAuthenticationServiceConfigProperties {

    // explicitly initialized with null so that Quarkus doesn't complain about missing configuration property
    private String permissionsPath = null;
    private SignatureSupportingConfigProperties signingProps = new SignatureSupportingConfigProperties();
    private List<String> supportedSaslMechanisms = List.of(
            AuthenticationConstants.MECHANISM_EXTERNAL,
            AuthenticationConstants.MECHANISM_PLAIN);

    /**
     * Creates properties from default values.
     */
    public FileBasedAuthenticationServiceConfigProperties() {
        super();
    }

    /**
     * Creates properties from existing options.
     *
     * @param options The options to copy.
     */
    public FileBasedAuthenticationServiceConfigProperties(final FileBasedAuthenticationServiceOptions options) {
        super();
        signingProps = new SignatureSupportingConfigProperties(options.signing());
        this.permissionsPath = options.permissionsPath();
        this.supportedSaslMechanisms = options.supportedSaslMechanisms();
    }

    /**
     * Gets the properties for determining key material for creating tokens.
     *
     * @return The properties.
     */
    public final SignatureSupportingConfigProperties getSigning() {
        return signingProps;
    }

    /**
     * Gets the properties for determining key material for validating tokens issued by this service.
     *
     * @return The properties.
     */
    public final SignatureSupportingConfigProperties getValidation() {
        return getSigning();
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
