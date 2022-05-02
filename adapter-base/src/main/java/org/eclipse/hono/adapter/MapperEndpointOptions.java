/**
 * Copyright (c) 2021, 2022 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.adapter;

import java.util.Optional;

import io.smallrye.config.WithDefault;

/**
 * Options for configuring custom mappers.
 *
 */
public interface MapperEndpointOptions {

    /**
     * Gets the host name or IP address of this mapper.
     *
     * @return The host name.
     */
    Optional<String> host();

    /**
     * Gets the port of this mapper.
     *
     * @return the port.
     */
    Optional<Integer> port();

    /**
     * Gets the uri of this mapper.
     *
     * @return the uri.
     */
    Optional<String> uri();

    /**
     * Checks whether the connection to the message mapping service is secured
     * using TLS.
     *
     * @return {@code true} if the connection to the mapper is secured using TLS.
     */
    @WithDefault("true")
    boolean tlsEnabled();
}
