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

package org.eclipse.hono.adapter.sigfox.impl;

import org.eclipse.hono.adapter.http.HttpProtocolAdapterOptions;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.ConfigMapping.NamingStrategy;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithParentName;

/**
 * Enhanced configuration properties for {@link SigfoxProtocolAdapter}.
 */
@ConfigMapping(prefix = "hono.sigfox", namingStrategy = NamingStrategy.VERBATIM)
public interface SigfoxProtocolAdapterOptions {

    /**
     * Gets the generic HTTP protocol adapter options.
     *
     * @return The options.
     */
    @WithParentName
    HttpProtocolAdapterOptions httpAdapterOptions();

    /**
     * Gets the custom TTD value which is being used when an {@code ack} is required.
     * <p>
     * This defaults to the Sigfox default of 20 seconds. Only change this when you know what you are doing.
     *
     * @return The TTD value.
     */
    @WithDefault("20")
    int ttdWhenAckRequired();
}
