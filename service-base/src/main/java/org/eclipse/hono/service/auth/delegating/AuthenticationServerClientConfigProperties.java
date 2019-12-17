/*******************************************************************************
 * Copyright (c) 2016, 2019 Contributors to the Eclipse Foundation
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

import org.eclipse.hono.config.ClientConfigProperties;
import org.eclipse.hono.config.SignatureSupportingConfigProperties;
import org.eclipse.hono.service.auth.AbstractHonoAuthenticationService;


/**
 * Configuration properties for the {@code DelegatingAuthenticationService}.
 *
 */
public class AuthenticationServerClientConfigProperties extends ClientConfigProperties {

    private final SignatureSupportingConfigProperties validation = new SignatureSupportingConfigProperties();
    private List<String> supportedSaslMechanisms = List.of(AbstractHonoAuthenticationService.DEFAULT_SASL_MECHANISMS);

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
