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

package org.eclipse.hono.authentication.file;

import java.util.List;

import org.eclipse.hono.service.auth.SignatureSupportingOptions;
import org.eclipse.hono.util.AuthenticationConstants;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.ConfigMapping.NamingStrategy;
import io.smallrye.config.WithDefault;

/**
 * Options for configuring the {@code FileBasedAuthenticationService}.
 *
 */
@ConfigMapping(prefix = "hono.auth.svc", namingStrategy = NamingStrategy.VERBATIM)
public interface FileBasedAuthenticationServiceOptions {

    /**
     * Gets the properties for determining key material for creating tokens.
     *
     * @return The properties.
     */
    SignatureSupportingOptions signing();

    /**
     * Gets the path to the file that the authorization rules are loaded from.
     *
     * @return The path.
     */
    String permissionsPath();

    /**
     * Gets the SASL mechanisms supported by the configured service.
     *
     * @return The supported SASL mechanisms.
     */
    @WithDefault(AuthenticationConstants.MECHANISM_EXTERNAL + "," + AuthenticationConstants.MECHANISM_PLAIN)
    List<String> supportedSaslMechanisms();
}
