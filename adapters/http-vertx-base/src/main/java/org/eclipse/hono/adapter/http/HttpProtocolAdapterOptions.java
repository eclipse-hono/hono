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

package org.eclipse.hono.adapter.http;

import org.eclipse.hono.config.ProtocolAdapterOptions;

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
}
