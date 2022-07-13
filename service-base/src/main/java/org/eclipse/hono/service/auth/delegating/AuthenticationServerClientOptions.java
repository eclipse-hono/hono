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

package org.eclipse.hono.service.auth.delegating;

import java.util.List;

import org.eclipse.hono.client.amqp.config.ClientOptions;
import org.eclipse.hono.service.auth.SignatureSupportingOptions;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.ConfigMapping.NamingStrategy;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithParentName;

/**
 * Options for configuring clients for accessing Hono's Authentication service.
 *
 */
@ConfigMapping(prefix = "hono.auth.client", namingStrategy = NamingStrategy.VERBATIM)
public interface AuthenticationServerClientOptions {

    /**
     * Gets the client options.
     *
     * @return The options.
     */
    @WithParentName
    ClientOptions clientOptions();

    /**
     * Gets the options for determining key material for validating user tokens.
     *
     * @return The options.
     */
    SignatureSupportingOptions validation();

    /**
     * Gets the SASL mechanisms supported by the configured service.
     *
     * @return The supported SASL mechanisms.
     */
    @WithDefault("EXTERNAL,PLAIN")
    List<String> supportedSaslMechanisms();
}
