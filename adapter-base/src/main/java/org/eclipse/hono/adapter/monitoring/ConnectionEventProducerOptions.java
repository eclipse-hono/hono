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

package org.eclipse.hono.adapter.monitoring;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.ConfigMapping.NamingStrategy;
import io.smallrye.config.WithDefault;

/**
 * Options for selecting and configuring a {@code ConnectionEventProducer}.
 *
 */
@ConfigMapping(prefix = "hono.connectionEvents", namingStrategy = NamingStrategy.VERBATIM)
public interface ConnectionEventProducerOptions {

    /**
     * Gets the type of producer of connection events.
     *
     * @return The producer type.
     */
    @WithDefault("logging")
    String producer();

    /**
     * Gets the level to log information at if the <em>type</em> is {@code logging}.
     *
     * @return The level to log at.
     */
    @WithDefault("info")
    String logLevel();
}
