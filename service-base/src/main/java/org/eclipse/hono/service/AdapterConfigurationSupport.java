/**
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.service;

import org.eclipse.hono.config.AuthenticatingClientConfigProperties;
import org.eclipse.hono.config.ClientConfigProperties;

/**
 * A base class that provides helper methods for configuring protocol adapters.
 *
 */
public abstract class AdapterConfigurationSupport {

    /**
     * Sets a client configuration's server role name.
     * <p>
     * Does nothing if the configuration's <em>serverRole</em> property has
     * a value other than {@value AuthenticatingClientConfigProperties#SERVER_ROLE_UNKNOWN}.
     *
     * @param config The client configuration.
     * @param serverRole The role name.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    protected static void setConfigServerRoleIfUnknown(
            final AuthenticatingClientConfigProperties config,
            final String serverRole) {

        if (config.getServerRole().equals(AuthenticatingClientConfigProperties.SERVER_ROLE_UNKNOWN)) {
            config.setServerRole(serverRole);
        }
    }

    /**
     * Gets the name of the protocol adapter to configure.
     * <p>
     * This name will be used as part of the <em>container-id</em> in the AMQP <em>Open</em> frame sent by the
     * clients configured by this class.
     *
     * @return The protocol adapter name.
     */
    protected abstract String getAdapterName();

    /**
     * Sets the default name property of a client configuration.
     * <p>
     * Sets the <em>name</em> property to the value returned by {@link #getAdapterName()}
     * if not set.
     *
     * @param config The configuration properties.
     * @throws NullPointerException if config is {@code null}.
     */
    protected void setDefaultConfigNameIfNotSet(final ClientConfigProperties config) {
        if (config.getName() == null && getAdapterName() != null) {
            config.setName(getAdapterName());
        }
    }
}
