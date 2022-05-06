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

package org.eclipse.hono.adapter.amqp;

import org.eclipse.hono.adapter.ProtocolAdapterOptions;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.ConfigMapping.NamingStrategy;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithParentName;

/**
 * Options for configuring the AMQP protocol adapter.
 *
 */
@ConfigMapping(prefix = "hono.amqp", namingStrategy = NamingStrategy.VERBATIM)
public interface AmqpAdapterOptions {

    /**
     * Gets the adapter options.
     *
     * @return The options.
     */
    @WithParentName
    ProtocolAdapterOptions adapterOptions();

    /**
     * Gets the maximum number of bytes that can be sent in an AMQP message delivery
     * over the connection with a device.
     *
     * @return The frame size.
     */
    @WithDefault("16384")
    int maxFrameSize();

    /**
     * Gets the maximum number of AMQP transfer frames for sessions created on this connection.
     * This is the number of transfer frames that may simultaneously be in flight for all links
     * in the session.
     *
     * @return The number of frames.
     */
    @WithDefault("30")
    int maxSessionFrames();

    /**
     * Gets the time to wait for incoming traffic from a device
     * before the connection is considered stale and thus be closed.
     *
     * @return The time interval in milliseconds.
     */
    @WithDefault("60000")
    int idleTimeout();

    /**
     * Gets the time to wait for a delivery update from a device before the AMQP sender link to the
     * device is closed.
     *
     * @return The wait time in milliseconds.
     */
    @WithDefault("1000")
    long sendMessageToDeviceTimeout();
}
