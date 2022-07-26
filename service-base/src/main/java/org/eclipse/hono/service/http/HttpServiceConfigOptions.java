/*******************************************************************************
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.service.http;

import org.eclipse.hono.config.ServiceOptions;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithParentName;

/**
 * Common configuration properties for the HTTP endpoint.
 *
 */
@ConfigMapping(prefix = "hono.http", namingStrategy = ConfigMapping.NamingStrategy.VERBATIM)
public interface HttpServiceConfigOptions {

    /**
     * Gets the common service configuration options.
     *
     * @return the options.
     */
    @WithParentName
    ServiceOptions commonOptions();

    /**
     * Checks whether the HTTP endpoint requires clients to authenticate.
     * <p>
     * If this property is {@code false} then clients are always allowed to access the HTTP endpoint
     * without authentication.
     *
     * @return {@code true} if the HTTP endpoint should require clients to authenticate.
     */
    @WithDefault("true")
    boolean authenticationRequired();

    /**
     * Gets the name of the realm to be used in the <em>WWW-Authenticate</em> header.
     * <p>
     * The realm is used in the <em>WWW-Authenticate</em> header.
     *
     * @return The realm name.
     */
    @WithDefault("Hono")
    String realm();

    /**
     * Gets the idle timeout.
     * <p>
     * A connection will timeout and be closed if no data is received or sent within the idle timeout period.
     * A zero value means no timeout is used.
     * <p>
     * The default value is {@code 60} seconds.
     *
     * @return The idle timeout in seconds.
     */
    @WithDefault("60")
    int idleTimeout();
}
