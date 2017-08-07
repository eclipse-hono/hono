/**
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 */

package org.eclipse.hono.adapter.mqtt;

import org.eclipse.hono.config.ServiceConfigProperties;

/**
 * Configuration properties for the MQTT protocol adapter of Hono.
 *
 */
public class MqttProtocolAdapterProperties extends ServiceConfigProperties {

    private boolean authenticateDevices = true;

    /**
     * Checks whether the MQTT protocol adapter always authenticates devices using their provided credentials as defined
     * in the <a href="https://www.eclipse.org/hono/api/Credentials-API/">Credentials API</a>.
     * <p>
     * If this property is {@code false} then devices are always allowed to publish data without providing
     * credentials. This should only be set to false in test setups.
     * <p>
     * The default value of this property is {@code true}.
     *
     * @return {@code true} if the MQTT protocol adapter demands the authentication of devices to allow the publishing of data.
     */

    public final boolean isAuthenticateDevices() {
        return authenticateDevices;
    }

    /**
     * Sets whether the MQTT protocol adapter always authenticates devices using their provided credentials as defined
     * in the <a href="https://www.eclipse.org/hono/api/Credentials-API/">Credentials API</a>.
     * <p>
     * If this property is set to {@code false} then devices are always allowed to publish data without providing
     * credentials. This should only be set to false in test setups.
     * <p>
     * The default value of this property is {@code true}.
     *
     * @param authenticateDevices {@code true} if the server should wait for downstream connections to be established during startup.
     * @return {@code true} if the MQTT protocol adapter demands the authentication of devices to allow the publishing of data.
     */
    public final MqttProtocolAdapterProperties setAuthenticateDevices(final boolean authenticateDevices) {
        this.authenticateDevices = authenticateDevices;
        return this;
    }
}
