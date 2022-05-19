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

package org.eclipse.hono.adapter.mqtt;

import org.eclipse.hono.adapter.ProtocolAdapterOptions;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.ConfigMapping.NamingStrategy;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithParentName;

/**
 * Options for configuring an MQTT based protocol adapter.
 *
 */
@ConfigMapping(prefix = "hono.mqtt", namingStrategy = NamingStrategy.VERBATIM)
public interface MqttProtocolAdapterOptions {

    /**
     * Gets the adapter options.
     *
     * @return The options.
     */
    @WithParentName
    ProtocolAdapterOptions adapterOptions();

    /**
     * Gets the waiting for acknowledgement timeout in milliseconds for commands published with QoS 1.
     * <p>
     * This timeout is used by the MQTT adapter for commands published with QoS 1. If there is no acknowledgement
     * within this time limit, then the command is settled with the <em>released</em> outcome.
     *
     * @return The timeout in milliseconds.
     */
    @WithDefault("1000")
    long sendMessageToDeviceTimeout();
}
