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

package org.eclipse.hono.adapter.lora;

import java.util.List;

import org.eclipse.hono.adapter.http.HttpProtocolAdapterOptions;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.ConfigMapping.NamingStrategy;
import io.smallrye.config.WithParentName;

/**
 * Options for configuring the LoRaWAN protocol adapter.
 *
 */
@ConfigMapping(prefix = "hono.lora", namingStrategy = NamingStrategy.VERBATIM)
public interface LoraProtocolAdapterOptions {

    /**
     * Gets the adapter options.
     *
     * @return The options.
     */
    @WithParentName
    HttpProtocolAdapterOptions adapterOptions();

    /**
     * Returns all tenants who are allowed to send commands. These have to be set explicitly in the properties file.
     *
     * @return all command enabled tenants
     */
    List<String> commandEnabledTenants();
}
