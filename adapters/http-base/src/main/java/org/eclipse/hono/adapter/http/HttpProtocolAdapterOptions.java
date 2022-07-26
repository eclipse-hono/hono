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

package org.eclipse.hono.adapter.http;

import org.eclipse.hono.adapter.ProtocolAdapterOptions;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.ConfigMapping.NamingStrategy;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithParentName;

/**
 * Options for configuring an HTTP based protocol adapter.
 *
 */
@ConfigMapping(prefix = "hono.http", namingStrategy = NamingStrategy.VERBATIM)
public interface HttpProtocolAdapterOptions {

    /**
     * Gets the adapter options.
     *
     * @return The options.
     */
    @WithParentName
    ProtocolAdapterOptions adapterOptions();

    /**
     * Gets the name of the realm that unauthenticated devices are prompted to provide credentials for.
     * <p>
     * The realm is used in the <em>WWW-Authenticate</em> header returned to devices
     * in response to unauthenticated requests.
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
     * The default value is {@code 75} seconds.
     *
     * @return The idle timeout in seconds.
     */
    @WithDefault("75")
    int idleTimeout();
}
