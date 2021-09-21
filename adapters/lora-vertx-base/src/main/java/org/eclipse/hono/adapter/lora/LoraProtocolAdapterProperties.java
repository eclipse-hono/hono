/*******************************************************************************
 * Copyright (c) 2019, 2021 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.adapter.lora;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.eclipse.hono.adapter.http.HttpProtocolAdapterProperties;

/**
 * Properties for LoRaWAN commands.
 */
public class LoraProtocolAdapterProperties extends HttpProtocolAdapterProperties {

    private List<String> commandEnabledTenants;

    /**
     * Creates properties using default values.
     */
    public LoraProtocolAdapterProperties() {
        super();
    }

    /**
     * Creates properties using existing options.
     *
     * @param options The options to copy.
     */
    public LoraProtocolAdapterProperties(final LoraProtocolAdapterOptions options) {
        super(options.adapterOptions());
        this.commandEnabledTenants = List.copyOf(options.commandEnabledTenants());
    }

    /**
     * Gets all tenants which are allowed to send commands.
     *
     * @return The tenant identifiers.
     */
    public final List<String> getCommandEnabledTenants() {
        return Optional.ofNullable(commandEnabledTenants).orElse(List.of());
    }

    /**
     * Gets all tenants which are allowed to send commands.
     *
     * @param tenants The tenant identifiers.
     * @throws NullPointerException if tenants is {@code null}.
     */
    public final void setCommandEnabledTenants(final List<String> tenants) {
        Objects.requireNonNull(tenants);
        this.commandEnabledTenants = List.copyOf(tenants);
    }
}
