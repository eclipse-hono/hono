/*******************************************************************************
 * Copyright (c) 2019, 2019 Contributors to the Eclipse Foundation
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

import java.util.LinkedList;
import java.util.List;

/**
 * Properties for LoRaWAN commands.
 */
public class LoraCommandProperties {

    private final List<String> commandEnabledTenants = new LinkedList<>();

    /**
     * Returns all tenants who are allowed to send commands. These have to be set explicitly in the properties file.
     *
     * @return all command enabled tenants
     */
    public List<String> getCommandEnabledTenants() {
        return commandEnabledTenants;
    }
}
