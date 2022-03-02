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

package org.eclipse.hono.service.auth.delegating;

import java.util.List;
import java.util.Objects;

import org.eclipse.hono.client.amqp.config.ClientConfigProperties;
import org.eclipse.hono.config.SignatureSupportingConfigProperties;
import org.eclipse.hono.service.auth.AbstractHonoAuthenticationService;
import org.eclipse.hono.util.AuthenticationConstants;


/**
 * Configuration properties for the {@code DelegatingAuthenticationService}.
 *
 */
public class AuthenticationServerClientConfigProperties extends ClientConfigProperties {

    private SignatureSupportingConfigProperties validation = new SignatureSupportingConfigProperties();
    private List<String> supportedSaslMechanisms = List.of(
            AuthenticationConstants.MECHANISM_EXTERNAL,
            AuthenticationConstants.MECHANISM_PLAIN);

    /**
     * Creates new properties using default values.
     */
    public AuthenticationServerClientConfigProperties() {
        super();
    }

    /**
     * Creates new properties from existing options.
     *
     * @param options The options to copy.
     */
    public AuthenticationServerClientConfigProperties(final AuthenticationServerClientOptions options) {
        super(options.clientOptions());
        setSupportedSaslMechanisms(List.copyOf(options.supportedSaslMechanisms()));
        this.validation = new SignatureSupportingConfigProperties(options.validation());
    }

    /**
     * Sets the properties configuring key material for validating tokens.
     *
     * @param props The properties.
     * @throws NullPointerException if props is {@code null}.
     */
    public final void setValidation(final SignatureSupportingConfigProperties props) {
        validation = Objects.requireNonNull(props);
    }

    /**
     * Gets the properties for determining key material for validating user tokens.
     *
     * @return The properties.
     */
    public final SignatureSupportingConfigProperties getValidation() {
        return validation;
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
