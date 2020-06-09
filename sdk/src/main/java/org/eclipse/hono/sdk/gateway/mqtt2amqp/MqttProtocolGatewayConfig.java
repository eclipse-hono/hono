/*******************************************************************************
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
 *******************************************************************************/

package org.eclipse.hono.sdk.gateway.mqtt2amqp;

import java.util.Objects;

import org.eclipse.hono.config.AbstractConfig;
import org.eclipse.hono.util.Constants;

/**
 * Configuration of server properties for {@link AbstractMqttProtocolGateway}.
 */
public class MqttProtocolGatewayConfig extends AbstractConfig {

    /**
     * The default number of milliseconds to wait for PUBACK.
     */
    protected static final int DEFAULT_COMMAND_ACK_TIMEOUT = 100;

    private int commandAckTimeout = DEFAULT_COMMAND_ACK_TIMEOUT;
    private int port = 0;
    private String bindAddress = Constants.LOOPBACK_DEVICE_ADDRESS;
    private boolean sni;

    /**
     * Gets the host name or literal IP address of the network interface that this server's secure port is configured to
     * be bound to.
     *
     * @return The host name.
     */
    public final String getBindAddress() {
        return bindAddress;
    }

    /**
     * Sets the host name or literal IP address of the network interface that this server's secure port should be bound
     * to.
     * <p>
     * The default value of this property is {@link Constants#LOOPBACK_DEVICE_ADDRESS} on IPv4 stacks.
     *
     * @param address The host name or IP address.
     * @throws NullPointerException if host is {@code null}.
     */
    public final void setBindAddress(final String address) {
        this.bindAddress = Objects.requireNonNull(address);
    }

    /**
     * Gets the port this server is configured to listen on.
     *
     * @return The port number.
     */
    public final int getPort() {
        return port;
    }

    /**
     * Sets the port that this server should listen on.
     * <p>
     * If the port is set to 0 (the default value), then this server will bind to an arbitrary free port chosen by the
     * operating system during startup.
     *
     * @param port The port number.
     * @throws IllegalArgumentException if port &lt; 0 or port &gt; 65535.
     */
    public final void setPort(final int port) {
        if (isValidPort(port)) {
            this.port = port;
        } else {
            throw new IllegalArgumentException("invalid port number");
        }
    }

    /**
     * Sets whether the server should support Server Name Indication for TLS connections.
     *
     * @param sni {@code true} if the server should support SNI.
     */
    public final void setSni(final boolean sni) {
        this.sni = sni;
    }

    /**
     * Checks if the server supports Server Name Indication for TLS connections.
     *
     * @return {@code true} if the server supports SNI.
     */
    public final boolean isSni() {
        return this.sni;
    }

    /**
     * Gets the waiting for acknowledgement time out in milliseconds for commands published with QoS 1.
     * <p>
     * This time out is used by the MQTT protocol gateway for commands published with QoS 1. If there is no
     * acknowledgement within this time limit, then the command is settled with the <em>released</em> outcome.
     * <p>
     * The default value is {@link #DEFAULT_COMMAND_ACK_TIMEOUT}.
     *
     * @return The time out in milliseconds.
     */
    public final int getCommandAckTimeout() {
        return commandAckTimeout;
    }

    /**
     * Sets the waiting for acknowledgement time out in milliseconds for commands published with QoS 1.
     * <p>
     * This time out is used by the MQTT protocol gateway for commands published with QoS 1. If there is no
     * acknowledgement within this time limit, then the command is settled with the <em>released</em> outcome.
     * <p>
     * The default value is {@link #DEFAULT_COMMAND_ACK_TIMEOUT}.
     *
     * @param timeout The time out in milliseconds.
     * @throws IllegalArgumentException if the timeout is negative.
     */
    public final void setCommandAckTimeout(final int timeout) {
        if (timeout < 0) {
            throw new IllegalArgumentException("timeout must not be negative");
        }
        this.commandAckTimeout = timeout;
    }
}
